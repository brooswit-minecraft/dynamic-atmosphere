package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;

/** Main-thread state; no Minecraft dependencies so packet/lifecycle behavior is testable. */
public final class AtmosphereClientCache {
    public static final int DEFAULT_CELL_BUDGET = 200_000;
    private static final int TRANSITION_TICKS = 10;

    public record Cell(int x, int y, int z) { }
    public record Chunk(int x, int z) { }
    public record Update(Cell cell, int amount, int capacity) { }
    /** Render amount is interpolated fullness scaled to 0..1000, not stored material. */
    public record VisibleCell(Cell cell, float amount) { }
    private record Amount(float from, float target, long since, int material, int capacity, int duration) {
        Amount(float from, float target, long since, int material, int capacity) {
            this(from, target, since, material, capacity,
                io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().transitionTicks());
        }
        float at(double tick) {
            if (duration == 0) return target;
            double progress = Math.clamp((tick - since) / duration, 0.0, 1.0);
            return (float) (from + (target - from) * progress);
        }
    }

    private final Map<Cell, Amount> cells = new LinkedHashMap<>(16, 0.75f, true);
    private final AtmosphereLodHierarchy lod;
    private final int baseCellSize;
    private final int cellBudget;
    private Predicate<Cell> detailedView = cell -> true;
    private Map<Cell, Update> snapshot;
    private Set<Chunk> snapshotChunks;
    private boolean wholeViewSnapshot;
    private String dimension;
    private boolean hasSnapshot;
    private long tick;
    private long revision;
    private long detailedRevision = -1;
    private List<Cell> detailedCells = List.of();
    private List<AtmosphereClientView.CoarseCell> coarseCells = List.of();
    private long coarseTick = Long.MIN_VALUE;
    private long viewX = Long.MIN_VALUE;
    private long viewZ = Long.MIN_VALUE;
    private int viewDistance = -1;

    public AtmosphereClientCache() {
        this(io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().allocation().cellBudget());
    }

    AtmosphereClientCache(int cellBudget) {
        this(cellBudget, AtmosphereGridLayout.CELL_SIZE, 2, 2);
    }

    AtmosphereClientCache(int cellBudget, int baseCellSize, int rootLevel, double reachMultiplier) {
        if (cellBudget < 1) throw new IllegalArgumentException("cell budget must be positive");
        this.cellBudget = cellBudget;
        this.baseCellSize = baseCellSize;
        this.lod = new AtmosphereLodHierarchy(baseCellSize, rootLevel, reachMultiplier);
    }

    void setReachMultiplier(double reach) { lod.setReachMultiplier(reach); }

    public void changeDimension(String nextDimension) {
        if (!Objects.equals(dimension, nextDimension)) {
            clear();
            dimension = nextDimension;
        }
    }

    public void clear() {
        cells.clear();
        lod.clear();
        snapshot = null;
        snapshotChunks = null;
        detailedView = cell -> true;
        dimension = null;
        hasSnapshot = false;
        tick = 0;
        revision++;
        detailedRevision = -1;
        detailedCells = List.of();
        coarseCells = List.of();
        coarseTick = Long.MIN_VALUE;
        viewDistance = -1;
    }

    public void apply(String packetDimension, boolean reset, List<Update> updates) {
        apply(packetDimension, reset, reset, updates);
    }

    public void apply(String packetDimension, boolean reset, boolean snapshotEnd, List<Update> updates) {
        apply(packetDimension, reset, snapshotEnd, List.of(), updates, true);
    }

    public void apply(String packetDimension, boolean reset, boolean snapshotEnd, List<Chunk> authoritativeChunks, List<Update> updates) {
        apply(packetDimension, reset, snapshotEnd, authoritativeChunks, updates, false);
    }

    private void apply(String packetDimension, boolean reset, boolean snapshotEnd, List<Chunk> authoritativeChunks,
                       List<Update> updates, boolean legacyWholeView) {
        if (dimension == null || !dimension.equals(packetDimension)) {
            return;
        }
        if (reset) {
            snapshot = new LinkedHashMap<>();
            snapshotChunks = new HashSet<>();
            wholeViewSnapshot = legacyWholeView;
        }
        if (snapshot != null) {
            snapshotChunks.addAll(authoritativeChunks);
            for (Update update : updates) {
                snapshot.put(update.cell(), update);
            }
            if (!snapshotEnd) {
                return;
            }
            if (wholeViewSnapshot) {
                Map<Cell, Amount> previous = new LinkedHashMap<>(cells);
                cells.clear();
                lod.clear();
                reconcile(previous, snapshot.values());
            } else {
                var absent = new ArrayList<Update>();
                for (var entry : cells.entrySet()) {
                    if (snapshotChunks.contains(chunk(entry.getKey())) && !snapshot.containsKey(entry.getKey())) {
                        absent.add(new Update(entry.getKey(), 0, entry.getValue().capacity()));
                    }
                }
                reconcile(cells, absent);
                reconcile(cells, snapshot.values().stream()
                    .filter(update -> snapshotChunks.contains(chunk(update.cell()))).toList());
            }
            snapshot = null;
            snapshotChunks = null;
            hasSnapshot = true;
        } else if (hasSnapshot && !snapshotEnd) {
            reconcile(cells, updates);
        }
        trim();
    }

    private Chunk chunk(Cell cell) {
        return new Chunk(Math.floorDiv(cell.x(), 16 / baseCellSize), Math.floorDiv(cell.z(), 16 / baseCellSize));
    }

    /** Raw last-known values, never interpolated opacity. Disk integration owns world/dimension identity. */
    public List<Update> exportUpdates() {
        return cells.entrySet().stream()
            .map(entry -> new Update(entry.getKey(), entry.getValue().material(), entry.getValue().capacity())).toList();
    }

    /** Seed before receiving authority; cached visuals appear immediately and do not authorize deltas. */
    public void restore(List<Update> updates) {
        if (hasSnapshot) return;
        for (Update update : updates) {
            int material = Math.clamp(update.amount(), 0, 1_000_000);
            int capacity = Math.clamp(update.capacity(), 0, 1000);
            float fullness = capacity == 0 ? 0 : Math.min(1000, material * 1000.0f / capacity);
            if (material > 0 && !cells.containsKey(update.cell())) {
                put(update.cell(), new Amount(fullness, fullness, tick, material, capacity));
            }
        }
        trim();
        revision++;
    }

    public void setDetailedView(Predicate<Cell> contains) {
        detailedView = Objects.requireNonNull(contains);
        detailedRevision = -1;
        trim();
    }

    public void setView(double cameraX, double cameraZ, int viewChunks) {
        long x = (long) Math.floor(cameraX / 16.0);
        long z = (long) Math.floor(cameraZ / 16.0);
        if (viewX == x && viewZ == z && viewDistance == viewChunks) return;
        viewX = x;
        viewZ = z;
        viewDistance = viewChunks;
        setDetailedView(cell -> {
            Chunk chunk = chunk(cell);
            return AtmosphereClientView.containsChunk(chunk.x(), chunk.z(), cameraX, cameraZ, viewChunks);
        });
    }

    private void trim() {
        if (cells.size() <= cellBudget) return;
        var iterator = cells.entrySet().iterator();
        while (cells.size() > cellBudget && iterator.hasNext()) {
            var entry = iterator.next();
            if (!detailedView.test(entry.getKey())) {
                lod.remove(entry.getKey(), tick);
                iterator.remove();
                revision++;
            }
        }
    }

    private void reconcile(Map<Cell, Amount> previous, Iterable<Update> updates) {
        revision++;
        for (Update update : updates) {
            int material = Math.clamp(update.amount(), 0, 1_000_000);
            int capacity = Math.clamp(update.capacity(), 0, 1000);
            float target = capacity == 0 ? 0 : Math.min(1000, material * 1000.0f / capacity);
            Amount old = previous.get(update.cell());
            if (old == null && material == 0) {
                continue;
            }
            if (old != null && old.material() == material && old.capacity() == capacity) {
                put(update.cell(), old);
                continue;
            }
            put(update.cell(), new Amount(old == null ? 0 : old.at(tick), target, tick, material, capacity));
        }
    }

    private void put(Cell cell, Amount amount) {
        cells.put(cell, amount);
        lod.put(cell, amount.from(), amount.target(), amount.since(), tick);
    }

    AtmosphereLodHierarchy.Selection lodSelection(double x, double y, double z, int viewChunks) {
        return lod.select(x, y, z, viewChunks, tick);
    }

    double renderTick(float partialTick) { return tick + Math.clamp(partialTick, 0, 1); }

    public void advance() {
        tick++;
        if (cells.entrySet().removeIf(entry -> {
            Amount amount = entry.getValue();
            if (amount.material() != 0 || tick - amount.since() < amount.duration()) return false;
            lod.remove(entry.getKey(), tick);
            return true;
        })) revision++;
    }

    public List<VisibleCell> visibleDetailed(float partialTick) {
        if (detailedRevision != revision) {
            detailedCells = cells.keySet().stream().filter(detailedView).toList();
            detailedRevision = revision;
        }
        var result = new ArrayList<VisibleCell>(detailedCells.size());
        for (Cell key : detailedCells) {
            Amount value = cells.get(key);
            if (value == null) continue;
            float amount = value.at(tick + Math.clamp(partialTick, 0, 1));
            if (amount > 0) result.add(new VisibleCell(key, amount));
        }
        return result;
    }

    /** Rebuild at most twice per second, never aggregate the entire cache each frame. */
    public List<AtmosphereClientView.CoarseCell> coarseCells() {
        if (coarseTick == Long.MIN_VALUE || tick - coarseTick >= 10) {
            coarseCells = AtmosphereClientView.aggregate(visible(0), baseCellSize);
            coarseTick = tick;
        }
        return coarseCells;
    }

    public List<VisibleCell> visible(float partialTick) {
        List<VisibleCell> result = new ArrayList<>(cells.size());
        for (var entry : cells.entrySet()) {
            float amount = entry.getValue().at(tick + Math.clamp(partialTick, 0, 1));
            if (amount > 0) {
                result.add(new VisibleCell(entry.getKey(), amount));
            }
        }
        return result;
    }

    public int size() {
        return cells.size();
    }

    int storedAmount(Cell cell) {
        Amount amount = cells.get(cell);
        return amount == null ? 0 : amount.material();
    }

    int pendingSize() {
        return snapshot == null ? 0 : snapshot.size();
    }
}
