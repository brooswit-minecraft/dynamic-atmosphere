package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/** Sparse, aligned octree. Amounts are sums of base-cell fullness, never raw material. */
final class AtmosphereLodHierarchy {
    static final int SELECTION_WORK_PER_TICK = 4096;
    private final int baseCellSize;
    private final int rootLevel;
    private final double reachMultiplier;
    private static final int TRANSITION_TICKS = 10;
    private static final int VIEW_REGION_SIZE = 16;

    private record Key(int x, int y, int z) { }

    private record Signal(float from, float target, long since) {
        long end() { return since + TRANSITION_TICKS; }
        double correction() { return (from - target) / (double) TRANSITION_TICKS; }
    }

    static final class Volume {
        final int x, y, z, level;
        final int baseCellSize;
        private final Volume[] children;
        private Signal signal;
        private int count;
        private double target;
        private TreeMap<Long, Double> corrections;

        private Volume(int x, int y, int z, int level, int baseCellSize) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
            this.baseCellSize = baseCellSize;
            children = level == 0 ? null : new Volume[8];
        }

        int size() { return baseCellSize << level; }
        double blockX() { return (double) x * baseCellSize; }
        double blockY() { return (double) y * baseCellSize; }
        double blockZ() { return (double) z * baseCellSize; }

        float amount(double tick) {
            if (count == 0) return 0;
            double sum = target;
            if (corrections != null) {
                for (var entry : corrections.entrySet()) {
                    sum += entry.getValue() * Math.max(0, entry.getKey() - tick);
                }
            }
            return (float) Math.clamp(sum / (1 << (3 * level)), 0, 1000);
        }

        private void change(Signal value, int sign, long tick) {
            if (value == null) return;
            target += sign * value.target();
            // Only the logical cache tick may expire endpoints. Rendering can sample
            // tick + 1 before a packet replaces a signal at the current tick.
            if (corrections != null) corrections.headMap(tick, true).clear();
            if (value.end() > tick && value.correction() != 0) {
                if (corrections == null) corrections = new TreeMap<>();
                corrections.merge(value.end(), sign * value.correction(), Double::sum);
            }
        }
    }

    record Selection(List<Volume> volumes, List<Volume> unloadedFallbacks) {
        static final Selection EMPTY = new Selection(List.of(), List.of());
    }

    private record View(long x, long y, long z, int chunks) {
        static View of(double x, double y, double z, int chunks) {
            int size = VIEW_REGION_SIZE;
            return new View((long) Math.floor(x / size), (long) Math.floor(y / size),
                (long) Math.floor(z / size), chunks);
        }
    }

    // X -> Z -> Y permits bounded spatial queries without walking unrelated cached roots.
    private final TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, Volume>>> roots = new TreeMap<>();
    private long revision;
    private long selectedRevision = -1;
    private View selectedView;
    private Selection selection = Selection.EMPTY;
    private Build build;
    private long workedTick = Long.MIN_VALUE;
    private int lastWork;

    AtmosphereLodHierarchy() { this(4, 2, 2); }

    AtmosphereLodHierarchy(int baseCellSize, int rootLevel, double reachMultiplier) {
        if ((baseCellSize != 1 && baseCellSize != 2 && baseCellSize != 4 && baseCellSize != 8 && baseCellSize != 16)
            || rootLevel < 0 || rootLevel > 3 || !Double.isFinite(reachMultiplier)
            || reachMultiplier < 0.25 || reachMultiplier > 4) throw new IllegalArgumentException("Unsupported LOD layout");
        this.baseCellSize = baseCellSize;
        this.rootLevel = rootLevel;
        this.reachMultiplier = reachMultiplier;
    }

    void put(AtmosphereClientCache.Cell cell, float from, float target, long since, long tick) {
        update(cell, new Signal(from, target, since), tick);
    }

    void remove(AtmosphereClientCache.Cell cell, long tick) { update(cell, null, tick); }

    private void update(AtmosphereClientCache.Cell cell, Signal signal, long tick) {
        int edge = 1 << rootLevel;
        var key = new Key(Math.floorDiv(cell.x(), edge), Math.floorDiv(cell.y(), edge), Math.floorDiv(cell.z(), edge));
        Volume root = root(key);
        if (root == null) {
            if (signal == null) return;
            root = new Volume(key.x() * edge, key.y() * edge, key.z() * edge, rootLevel, baseCellSize);
            roots.computeIfAbsent(key.x(), ignored -> new TreeMap<>())
                .computeIfAbsent(key.z(), ignored -> new TreeMap<>()).put(key.y(), root);
        }
        update(root, cell, signal, tick);
        if (root.count == 0) {
            var columns = roots.get(key.x());
            var column = columns.get(key.z());
            column.remove(key.y());
            if (column.isEmpty()) columns.remove(key.z());
            if (columns.isEmpty()) roots.remove(key.x());
        }
    }

    private Volume root(Key key) {
        var columns = roots.get(key.x());
        var column = columns == null ? null : columns.get(key.z());
        return column == null ? null : column.get(key.y());
    }

    private Signal update(Volume node, AtmosphereClientCache.Cell cell, Signal signal, long tick) {
        Signal previous;
        if (node.level == 0) {
            previous = node.signal;
            node.signal = signal;
            if ((previous == null) != (signal == null)) revision++;
        } else {
            int edge = 1 << (node.level - 1);
            int dx = (cell.x() - node.x) / edge;
            int dy = (cell.y() - node.y) / edge;
            int dz = (cell.z() - node.z) / edge;
            int index = dx | dy << 1 | dz << 2;
            Volume child = node.children[index];
            if (child == null) {
                if (signal == null) return null;
                child = new Volume(node.x + dx * edge, node.y + dy * edge, node.z + dz * edge, node.level - 1, baseCellSize);
                node.children[index] = child;
            }
            previous = update(child, cell, signal, tick);
            if (child.count == 0) node.children[index] = null;
        }
        node.change(previous, -1, tick);
        node.change(signal, 1, tick);
        node.count += (signal == null ? 0 : 1) - (previous == null ? 0 : 1);
        return previous;
    }

    void clear() {
        roots.clear();
        revision++;
        selectedRevision = -1;
        selectedView = null;
        selection = Selection.EMPTY;
        build = null;
        workedTick = Long.MIN_VALUE;
    }

    /** Rotation never invalidates selection. Publish a complete frontier, never half a parent. */
    Selection select(double x, double y, double z, int chunks, long tick) {
        View view = View.of(x, y, z, chunks);
        if (build != null && !view.equals(build.view)) build = null;
        if (build == null && (!view.equals(selectedView) || selectedRevision != revision)) {
            build = new Build(view, revision, x, y, z);
            double rootSize = baseCellSize << rootLevel;
            build.seed = root(new Key((int) Math.floor(x / rootSize), (int) Math.floor(y / rootSize),
                (int) Math.floor(z / rootSize)));
            if (build.seed != null) {
                build.previewRoots.add(build.seed);
                build.pending.push(build.seed);
            }
            // A changed/teleported view gets its own coarse roots immediately, never
            // the old camera's frontier. Same-view refinements retain existing detail.
            if (!view.equals(selectedView)) selection = build.preview;
        }
        if (workedTick == tick) return selection;
        workedTick = tick;
        lastWork = 0;
        if (build == null) return selection;
        while (lastWork < SELECTION_WORK_PER_TICK) {
            lastWork++;
            if (build.pending.isEmpty()) {
                Volume next = nextRoot(build);
                if (build.exhausted) {
                    selection = new Selection(Collections.unmodifiableList(build.volumes), Collections.unmodifiableList(build.fallbacks));
                    selectedView = build.view;
                    selectedRevision = build.revision;
                    build = null;
                    break;
                }
                if (next != null && next != build.seed) {
                    build.previewRoots.add(next);
                    build.pending.push(next);
                }
                continue;
            }
            Volume node = build.pending.pop();
            if (node.count == 0) continue;
            double distance = distanceSquared(node, build.x, build.y, build.z);
            double viewBlocks = Math.max(1, build.view.chunks()) * 16.0;
            // Query/render distance is padded for movement inside the cached view
            // region; the renderer applies the current camera's exact reach/frustum.
            if (distance > square(viewBlocks * reachMultiplier + VIEW_REGION_SIZE * Math.sqrt(3))) continue;
            double threshold = viewBlocks * reachMultiplier / (1 << (rootLevel - node.level + 1));
            if (node.level == 0 || distance > square(threshold)) {
                build.volumes.add(node);
            } else {
                // A subdivided 16-block node is exactly one chunk section. On unload,
                // use it instead of its 4/8-block descendants, never alongside them.
                if (node.size() == 16) build.fallbacks.add(node);
                for (Volume child : node.children) if (child != null) build.pending.push(child);
            }
        }
        return selection;
    }

    /** One bounded column lookup per work unit, including empty/out-of-height columns. */
    private Volume nextRoot(Build build) {
        var x = roots.ceilingEntry(build.cursorX);
        if (x == null || x.getKey() > build.maxX) {
            build.exhausted = true;
            return null;
        }
        if (x.getKey() != build.cursorX) {
            build.cursorX = x.getKey();
            build.cursorZ = build.minZ;
            build.cursorY = build.minY;
        }
        var z = x.getValue().ceilingEntry(build.cursorZ);
        if (z == null || z.getKey() > build.maxZ) {
            build.cursorX++;
            build.cursorZ = build.minZ;
            build.cursorY = build.minY;
            return null;
        }
        if (z.getKey() != build.cursorZ) {
            build.cursorZ = z.getKey();
            build.cursorY = build.minY;
        }
        var y = z.getValue().ceilingEntry(build.cursorY);
        if (y == null || y.getKey() > build.maxY) {
            build.cursorZ++;
            build.cursorY = build.minY;
            return null;
        }
        build.cursorY = y.getKey() + 1;
        return y.getValue();
    }

    private final class Build {
        final View view;
        final long revision;
        final double x, y, z;
        final ArrayDeque<Volume> pending = new ArrayDeque<>();
        final List<Volume> volumes = new ArrayList<>();
        final List<Volume> fallbacks = new ArrayList<>();
        final List<Volume> previewRoots = new ArrayList<>();
        final Selection preview = new Selection(Collections.unmodifiableList(previewRoots), List.of());
        final int minY, minZ, maxX, maxY, maxZ;
        int cursorX, cursorY, cursorZ;
        boolean exhausted;
        Volume seed;

        Build(View view, long revision, double x, double y, double z) {
            this.view = view;
            this.revision = revision;
            this.x = x;
            this.y = y;
            this.z = z;
            double rootSize = baseCellSize << rootLevel;
            double radius = Math.max(1, view.chunks()) * 16.0 * reachMultiplier;
            cursorX = (int) Math.floor((view.x() * VIEW_REGION_SIZE - radius) / rootSize) - 1;
            cursorY = minY = (int) Math.floor((view.y() * VIEW_REGION_SIZE - radius) / rootSize) - 1;
            cursorZ = minZ = (int) Math.floor((view.z() * VIEW_REGION_SIZE - radius) / rootSize) - 1;
            maxX = (int) Math.floor(((view.x() + 1) * VIEW_REGION_SIZE + radius) / rootSize);
            maxY = (int) Math.floor(((view.y() + 1) * VIEW_REGION_SIZE + radius) / rootSize);
            maxZ = (int) Math.floor(((view.z() + 1) * VIEW_REGION_SIZE + radius) / rootSize);
        }
    }

    static double distanceSquared(Volume volume, double x, double y, double z) {
        return square(gap(x, volume.blockX(), volume.size()))
            + square(gap(y, volume.blockY(), volume.size()))
            + square(gap(z, volume.blockZ(), volume.size()));
    }

    static boolean visibleWhenLoaded(Volume volume, boolean fallback, boolean loaded) {
        return fallback ? !loaded : volume.size() >= 16 || loaded;
    }

    static boolean withinReach(Volume volume, double x, double y, double z, int viewChunks) {
        return withinReach(volume, x, y, z, viewChunks, 2);
    }

    static boolean withinReach(Volume volume, double x, double y, double z, int viewChunks, double reach) {
        return distanceSquared(volume, x, y, z) <= square(Math.max(1, viewChunks) * 16.0 * reach);
    }

    private static double gap(double camera, double min, int size) { return Math.max(0, Math.max(min - camera, camera - min - size)); }
    private static double square(double value) { return value * value; }
    int lastSelectionWork() { return lastWork; }
    boolean selectionPending() { return build != null; }
}
