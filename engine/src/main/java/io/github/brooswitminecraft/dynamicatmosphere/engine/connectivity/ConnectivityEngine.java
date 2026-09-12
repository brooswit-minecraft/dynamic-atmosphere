package io.github.brooswitminecraft.dynamicatmosphere.engine.connectivity;

import io.github.brooswitminecraft.dynamicatmosphere.engine.EngineConfig;
import io.github.brooswitminecraft.dynamicatmosphere.engine.adapter.EnvironmentalAdapter;
import io.github.brooswitminecraft.dynamicatmosphere.engine.adapter.Passability;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.BlockPos;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.CellPos;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.Direction;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.WeatherGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the cached, per-Weather-Block internal connectivity graph (spec
 * section 7) and its dirty/queued/debounced rebuild lifecycle (spec sections
 * 8 and 50).
 *
 * <p>Flood fills happen ONLY inside {@link #advanceTo(long)}, and only for
 * cells whose debounce interval has elapsed — never as a side effect of a
 * read, never on every step. Spec section 50: "Never perform terrain flood
 * fills on normal atmospheric ticks."
 *
 * <p>REQUIRED DECISION carried by the ticket: a position the adapter reports
 * {@link Passability#UNKNOWN} for is treated as NOT PASSABLE by the flood
 * fill — conservative, because atmosphere should not be assumed to flow into
 * terrain this engine cannot see. This is the one place that collapses
 * UNKNOWN toward impassable; {@link EnvironmentalAdapter} itself still
 * reports it as a distinct value.
 *
 * <p>Time here is simulation time supplied by the caller through
 * {@link #advanceTo(long)} — never wall-clock time, never
 * {@code Thread.sleep}. There is no background thread.
 */
public final class ConnectivityEngine {

    private final WeatherGrid grid;
    private final EnvironmentalAdapter adapter;
    private final EngineConfig config;

    private final Map<CellPos, CellState> cells = new HashMap<>();

    private long currentTime = 0;
    private long floodFillCount = 0;
    private long boundaryRecomputeCount = 0;

    public ConnectivityEngine(WeatherGrid grid, EnvironmentalAdapter adapter, EngineConfig config) {
        if (grid.cellSize() != config.cellSize()) {
            throw new IllegalArgumentException(
                "grid cell size " + grid.cellSize() + " does not match config cell size " + config.cellSize());
        }
        this.grid = grid;
        this.adapter = adapter;
        this.config = config;
    }

    public WeatherGrid grid() {
        return grid;
    }

    public long currentTime() {
        return currentTime;
    }

    /** Total number of flood fills performed across this engine's lifetime. Kept separate from {@link #boundaryRecomputeCount()}. */
    public long floodFillCount() {
        return floodFillCount;
    }

    /** Total number of face boundary-adjacency recomputations performed, independent of whether they found a built neighbour. */
    public long boundaryRecomputeCount() {
        return boundaryRecomputeCount;
    }

    /** Number of cells currently marked dirty (eligible for rebuild or not). */
    public int queueDepth() {
        int count = 0;
        for (CellState state : cells.values()) {
            if (state.dirty) {
                count++;
            }
        }
        return count;
    }

    /** The cell's current regions, in the deterministic order described on {@link Region}. Empty if the cell has never been built. */
    public List<Region> regionsAt(CellPos cell) {
        CellState state = cells.get(cell);
        return state == null ? List.of() : state.regions;
    }

    /**
     * Marks the Weather Block containing {@code changedWorldBlock} dirty and
     * resets its debounce timer (trailing-edge debounce, spec section 8: a
     * cell dirtied again before its interval elapses restarts the wait, so a
     * burst of changes to one cell costs exactly one rebuild). A cell under
     * continuous change could in principle be starved forever under this
     * rule — a known property of trailing-edge debounce, not a bug.
     */
    public void markDirty(BlockPos changedWorldBlock) {
        markDirtyCell(grid.cellOf(changedWorldBlock));
    }

    /** As {@link #markDirty(BlockPos)}, but naming the cell directly. */
    public void markDirtyCell(CellPos cell) {
        CellState state = cells.computeIfAbsent(cell, c -> new CellState());
        state.dirty = true;
        state.dirtySince = currentTime;
    }

    /**
     * Advances simulation time to {@code simulationTime} and processes the
     * debounce queue: a dirty cell becomes eligible once
     * {@code simulationTime - dirtySince >= debounceInterval}. Up to
     * {@link EngineConfig#maxRebuildsPerStep()} of the eligible cells are
     * rebuilt, oldest-dirtied first (ties broken by {@link CellPos}'s
     * natural order) so behaviour is deterministic regardless of
     * {@link java.util.HashMap} iteration order. A cell that is eligible but
     * not reached this call stays dirty, at its original dirty-since time,
     * for a later call to pick up.
     *
     * @return one {@link RemapReport} per cell actually rebuilt, in rebuild order.
     */
    public List<RemapReport> advanceTo(long simulationTime) {
        if (simulationTime < currentTime) {
            throw new IllegalArgumentException(
                "simulation time must not move backwards: was " + currentTime + ", got " + simulationTime);
        }
        currentTime = simulationTime;

        List<CellPos> eligible = new ArrayList<>();
        for (Map.Entry<CellPos, CellState> entry : cells.entrySet()) {
            CellState state = entry.getValue();
            if (state.dirty && (currentTime - state.dirtySince) >= config.debounceInterval()) {
                eligible.add(entry.getKey());
            }
        }
        Comparator<CellPos> byDirtySince = Comparator.comparingLong(p -> cells.get(p).dirtySince);
        eligible.sort(byDirtySince.thenComparing(Comparator.naturalOrder()));

        List<RemapReport> results = new ArrayList<>();
        int limit = Math.min(eligible.size(), config.maxRebuildsPerStep());
        for (int i = 0; i < limit; i++) {
            results.add(rebuildCell(eligible.get(i)));
        }
        return results;
    }

    private RemapReport rebuildCell(CellPos cellPos) {
        CellState state = cells.computeIfAbsent(cellPos, c -> new CellState());
        List<Region> oldRegions = state.regions;

        FloodFillResult flood = floodFill(cellPos);
        floodFillCount++;

        state.regions = flood.regions();
        state.blockOwner = flood.blockOwner();
        state.dirty = false;

        // REQUIRED HANDLING for boundary changes: recompute this cell's
        // boundary adjacency against all six neighbours and update their
        // STORED VIEW directly, without flood-filling any of them.
        for (Direction face : Direction.values()) {
            recomputeBoundaryAdjacency(cellPos, face);
        }

        return buildRemapReport(cellPos, oldRegions, state.regions);
    }

    private FloodFillResult floodFill(CellPos cellPos) {
        int size = config.cellSize();
        BlockPos origin = grid.originOf(cellPos);
        boolean[] visited = new boolean[size * size * size];
        List<Region> regions = new ArrayList<>();
        Map<BlockPos, Region> owner = new HashMap<>();

        // Scanning in ascending linearIndex order and always starting a new
        // flood fill at the first unvisited passable cell guarantees that
        // start cell's index is the MINIMUM linear index among the whole
        // component it discovers: any smaller-index member would already
        // have been visited by the scan (either as this same region's start,
        // or, being connected, pulled in by an earlier BFS) before we get
        // here. That is what makes the start index a valid, deterministic
        // sort key with no extra bookkeeping during the BFS itself.
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    int idx = linearIndex(x, y, z, size);
                    if (visited[idx]) {
                        continue;
                    }
                    BlockPos start = new BlockPos(x, y, z);
                    if (!isPassable(origin, start)) {
                        visited[idx] = true;
                        continue;
                    }

                    List<BlockPos> members = new ArrayList<>();
                    EnumSet<Direction> touched = EnumSet.noneOf(Direction.class);
                    Deque<BlockPos> queue = new ArrayDeque<>();
                    queue.add(start);
                    visited[idx] = true;

                    while (!queue.isEmpty()) {
                        BlockPos cur = queue.poll();
                        members.add(cur);
                        markTouchedFaces(cur, size, touched);

                        for (Direction d : Direction.values()) {
                            int nx = cur.x() + d.dx();
                            int ny = cur.y() + d.dy();
                            int nz = cur.z() + d.dz();
                            if (nx < 0 || nx >= size || ny < 0 || ny >= size || nz < 0 || nz >= size) {
                                continue;
                            }
                            int nIdx = linearIndex(nx, ny, nz, size);
                            if (visited[nIdx]) {
                                continue;
                            }
                            BlockPos next = new BlockPos(nx, ny, nz);
                            if (!isPassable(origin, next)) {
                                visited[nIdx] = true;
                                continue;
                            }
                            visited[nIdx] = true;
                            queue.add(next);
                        }
                    }

                    Region region = new Region(new HashSet<>(members), touched, idx);
                    regions.add(region);
                    for (BlockPos m : members) {
                        owner.put(m, region);
                    }
                }
            }
        }

        regions.sort(Comparator.comparingInt(Region::orderKey));
        return new FloodFillResult(List.copyOf(regions), Map.copyOf(owner));
    }

    private static void markTouchedFaces(BlockPos local, int size, EnumSet<Direction> touched) {
        if (local.x() == 0) {
            touched.add(Direction.WEST);
        }
        if (local.x() == size - 1) {
            touched.add(Direction.EAST);
        }
        if (local.y() == 0) {
            touched.add(Direction.DOWN);
        }
        if (local.y() == size - 1) {
            touched.add(Direction.UP);
        }
        if (local.z() == 0) {
            touched.add(Direction.NORTH);
        }
        if (local.z() == size - 1) {
            touched.add(Direction.SOUTH);
        }
    }

    private boolean isPassable(BlockPos origin, BlockPos local) {
        BlockPos world = new BlockPos(origin.x() + local.x(), origin.y() + local.y(), origin.z() + local.z());
        return adapter.passability(world) == Passability.PASSABLE;
    }

    private static int linearIndex(int x, int y, int z, int size) {
        return (x * size + y) * size + z;
    }

    /**
     * Recomputes region-to-region adjacency between {@code cellPos} and its
     * neighbour across {@code face}, from {@code cellPos}'s freshly rebuilt
     * regions and the neighbour's CURRENT regions — without flood-filling the
     * neighbour. Mutates the stored neighbour lists on regions in BOTH
     * cells; this is how rebuilding one cell updates the neighbour's stored
     * view of the shared boundary without marking the neighbour itself
     * dirty or touching its region set.
     */
    private void recomputeBoundaryAdjacency(CellPos cellPos, Direction face) {
        CellState here = cells.get(cellPos);
        CellPos neighborPos = grid.neighborOf(cellPos, face);
        CellState there = cells.get(neighborPos);

        for (Region r : here.regions) {
            r.clearNeighbors(face);
        }
        if (there != null) {
            for (Region r : there.regions) {
                r.clearNeighbors(face.opposite());
            }

            int size = config.cellSize();
            Map<Region, Set<Region>> linked = new HashMap<>();
            for (BlockPos local : boundaryLayer(face, size)) {
                Region rHere = here.blockOwner.get(local);
                if (rHere == null) {
                    continue;
                }
                BlockPos correspondingLocal = correspondingLocal(local, face, size);
                Region rThere = there.blockOwner.get(correspondingLocal);
                if (rThere == null) {
                    continue;
                }
                if (linked.computeIfAbsent(rHere, k -> new HashSet<>()).add(rThere)) {
                    rHere.addNeighbor(face, rThere);
                    rThere.addNeighbor(face.opposite(), rHere);
                }
            }
        }

        boundaryRecomputeCount++;
    }

    private static List<BlockPos> boundaryLayer(Direction face, int size) {
        List<BlockPos> layer = new ArrayList<>(size * size);
        switch (face) {
            case EAST -> {
                for (int y = 0; y < size; y++) {
                    for (int z = 0; z < size; z++) {
                        layer.add(new BlockPos(size - 1, y, z));
                    }
                }
            }
            case WEST -> {
                for (int y = 0; y < size; y++) {
                    for (int z = 0; z < size; z++) {
                        layer.add(new BlockPos(0, y, z));
                    }
                }
            }
            case UP -> {
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        layer.add(new BlockPos(x, size - 1, z));
                    }
                }
            }
            case DOWN -> {
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        layer.add(new BlockPos(x, 0, z));
                    }
                }
            }
            case SOUTH -> {
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        layer.add(new BlockPos(x, y, size - 1));
                    }
                }
            }
            case NORTH -> {
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        layer.add(new BlockPos(x, y, 0));
                    }
                }
            }
        }
        return layer;
    }

    private static BlockPos correspondingLocal(BlockPos local, Direction face, int size) {
        return switch (face) {
            case EAST -> new BlockPos(0, local.y(), local.z());
            case WEST -> new BlockPos(size - 1, local.y(), local.z());
            case UP -> new BlockPos(local.x(), 0, local.z());
            case DOWN -> new BlockPos(local.x(), size - 1, local.z());
            case SOUTH -> new BlockPos(local.x(), local.y(), 0);
            case NORTH -> new BlockPos(local.x(), local.y(), size - 1);
        };
    }

    private RemapReport buildRemapReport(CellPos cellPos, List<Region> oldRegions, List<Region> newRegions) {
        List<RegionOverlap> overlaps = new ArrayList<>();
        for (Region oldRegion : oldRegions) {
            for (Region newRegion : newRegions) {
                int overlap = countOverlap(oldRegion.blocks(), newRegion.blocks());
                if (overlap > 0) {
                    overlaps.add(new RegionOverlap(oldRegion, newRegion, overlap));
                }
            }
        }
        return new RemapReport(cellPos, List.copyOf(overlaps));
    }

    private static int countOverlap(Set<BlockPos> a, Set<BlockPos> b) {
        Set<BlockPos> smaller = a.size() <= b.size() ? a : b;
        Set<BlockPos> larger = a.size() <= b.size() ? b : a;
        int count = 0;
        for (BlockPos p : smaller) {
            if (larger.contains(p)) {
                count++;
            }
        }
        return count;
    }

    private static final class CellState {
        List<Region> regions = List.of();
        Map<BlockPos, Region> blockOwner = Map.of();
        boolean dirty = false;
        long dirtySince = 0;
    }

    private record FloodFillResult(List<Region> regions, Map<BlockPos, Region> blockOwner) {
    }
}
