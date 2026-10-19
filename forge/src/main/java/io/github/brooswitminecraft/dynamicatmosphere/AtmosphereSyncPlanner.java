package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AtmosphereSyncPlanner<P, D> {

    record Chunk(int x, int z) {
    }

    record Cell(int x, int y, int z, int amount, int capacity) {
    }

    record Update<D>(
        D dimension,
        boolean reset,
        boolean snapshotEnd,
        List<Chunk> authoritativeChunks,
        List<Cell> cells
    ) {
        Update {
            authoritativeChunks = List.copyOf(authoritativeChunks);
            cells = List.copyOf(cells);
        }
    }

    /** Used by callers that don't track view-distance changes; never matches a real change. */
    static final int UNKNOWN_VIEW_RADIUS = -1;

    private record PlayerView<D>(D dimension, int viewRadius, Set<Chunk> authoritativeChunks, Map<CellCoordinate, Cell> cells) {
    }

    private record CellCoordinate(int x, int y, int z) {
    }

    /**
     * MINECRAFT-436 review: one chunk's input to {@link #planChunks}, carrying the SAME
     * (amountRevision, terrainGeneration) pair a caller's own cross-tick cell cache already
     * computed (see {@code ForgeAtmospherePrototype.cachedChunkCells}) -- so the planner can
     * decide, per chunk, whether anything in it needs to be looked at at all, instead of
     * {@link #plan} re-indexing and re-diffing every visible cell in the player's entire view on
     * every call regardless of how much (if any) of it actually changed.
     */
    record ChunkCells(Chunk chunk, long amountRevision, long terrainGeneration, List<Cell> cells) { }

    private record ChunkEntry(long amountRevision, long terrainGeneration, Map<CellCoordinate, Cell> cells) { }

    private record ChunkedPlayerView<D>(D dimension, int viewRadius, Map<Chunk, ChunkEntry> chunkEntries) { }

    private final int batchSize;
    private final Map<P, PlayerView<D>> views = new HashMap<>();
    /** See {@link #planChunks}'s doc; independent state, used only by the chunk-revision-aware API. */
    private final Map<P, ChunkedPlayerView<D>> chunkedViews = new HashMap<>();

    AtmosphereSyncPlanner(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    List<Update<D>> plan(P player, D dimension, List<Chunk> authoritativeChunks, List<Cell> visibleCells) {
        return plan(player, dimension, UNKNOWN_VIEW_RADIUS, authoritativeChunks, visibleCells);
    }

    /**
     * MINECRAFT-436: a pure peek, mutating no state, at whether {@link #plan} would produce a
     * forced reset for this player right now -- i.e. a genuine dimension or view-distance change,
     * never inferred from chunk/cell set size. Lets a caller detect a genuine change cheaply
     * (using only the dimension/viewRadius it already has in hand, with no chunk/cell gather)
     * even while it is deliberately not calling {@code plan} this tick for an unrelated reason
     * (e.g. still draining a prior generation through a paced send queue), so it can still react
     * immediately to a real change instead of only noticing it once that unrelated reason clears.
     */
    boolean wouldForceReset(P player, D dimension, int viewRadius) {
        PlayerView<D> previous = views.get(player);
        return previous == null || !previous.dimension().equals(dimension) || previous.viewRadius() != viewRadius;
    }

    /** See {@link #wouldForceReset}'s doc; the same peek against {@link #planChunks}'s independent state. */
    boolean wouldForceResetChunked(P player, D dimension, int viewRadius) {
        ChunkedPlayerView<D> previous = chunkedViews.get(player);
        return previous == null || !previous.dimension().equals(dimension) || previous.viewRadius() != viewRadius;
    }

    /**
     * MINECRAFT-436 review: the original intended implementation required ordinary sync to
     * "process changes plus subscription-entry/exit information rather than rebuilding every
     * visible material map" per player per tick. This is the chunk-revision-aware planner engine
     * that does that: a chunk whose (amountRevision, terrainGeneration) is unchanged since the
     * last call, and which is still subscribed, is skipped entirely -- its cells are never
     * indexed, diffed, or even looked at -- rather than {@link #plan}'s flat model, which
     * re-indexes and re-diffs the player's ENTIRE visible cell set on every call regardless of
     * how much (if any) of it actually changed. When nothing is entered/changed/exited at all
     * (the common stationary-player, nothing-nearby-changed steady state), this returns {@code
     * List.of()} after only an O(subscribed chunk count) scan -- no cell-level work whatsoever.
     *
     * <p>Independent state from {@link #plan} ({@link #chunkedViews}, not {@link #views}): the
     * two APIs are never mixed for the same player in production (this one is), but both remain
     * fully functional and separately tested so {@link #plan}'s own existing, heavily-covered
     * flat-diff behavior (used directly by {@code AtmosphereSyncPlannerTest}) is untouched by this
     * addition.
     *
     * <p>Preserves every existing observable semantic of {@link #plan}: a departed chunk's cells
     * are zeroed (tombstoned), a departed cell within a still-subscribed changed chunk is
     * tombstoned, a capacity-only change (same amount, different capacity) is still emitted
     * because it reaches here only as part of a changed chunk's full cell-by-cell diff, batching
     * via {@link #addBatches} is unchanged, and dimension/view-distance changes still force a full
     * reset snapshot of every currently visible cell.
     */
    List<Update<D>> planChunks(P player, D dimension, int viewRadius, List<ChunkCells> chunks) {
        ChunkedPlayerView<D> previous = chunkedViews.get(player);
        boolean dimensionChanged = previous == null || !previous.dimension().equals(dimension);
        boolean viewChanged = previous != null && !dimensionChanged && previous.viewRadius() != viewRadius;
        var updates = new ArrayList<Update<D>>();

        if (dimensionChanged || viewChanged) {
            List<Chunk> chunkScope = List.copyOf(new LinkedHashSet<>(chunks.stream().map(ChunkCells::chunk).toList()));
            List<Cell> allCells = chunks.stream().flatMap(c -> c.cells().stream()).toList();
            Map<Chunk, ChunkEntry> entries = new LinkedHashMap<>();
            for (ChunkCells input : chunks) {
                entries.put(input.chunk(),
                    new ChunkEntry(input.amountRevision(), input.terrainGeneration(), index(input.cells())));
            }
            if (dimensionChanged && previous != null) {
                updates.add(new Update<>(previous.dimension(), true, true, List.of(), List.of()));
            }
            chunkedViews.put(player, new ChunkedPlayerView<>(dimension, viewRadius, entries));
            addBatches(updates, dimension, true, chunkScope, allCells);
            return updates;
        }

        Map<Chunk, ChunkEntry> previousEntries = previous.chunkEntries();
        Map<Chunk, ChunkEntry> nextEntries = new LinkedHashMap<>(previousEntries);
        Set<Chunk> currentChunkSet = new LinkedHashSet<>(chunks.stream().map(ChunkCells::chunk).toList());
        List<Cell> delta = new ArrayList<>();
        boolean anyChange = false;

        for (var entry : previousEntries.entrySet()) {
            if (!currentChunkSet.contains(entry.getKey())) {
                for (CellCoordinate coordinate : entry.getValue().cells().keySet()) {
                    delta.add(new Cell(coordinate.x(), coordinate.y(), coordinate.z(), 0, 0));
                }
                nextEntries.remove(entry.getKey());
                anyChange = true;
            }
        }

        for (ChunkCells input : chunks) {
            ChunkEntry oldEntry = previousEntries.get(input.chunk());
            boolean changed = oldEntry == null
                || oldEntry.amountRevision() != input.amountRevision()
                || oldEntry.terrainGeneration() != input.terrainGeneration();
            if (!changed) continue;
            anyChange = true;
            Map<CellCoordinate, Cell> oldCells = oldEntry == null ? Map.of() : oldEntry.cells();
            Map<CellCoordinate, Cell> newCells = index(input.cells());
            for (CellCoordinate coordinate : oldCells.keySet()) {
                if (!newCells.containsKey(coordinate)) {
                    delta.add(new Cell(coordinate.x(), coordinate.y(), coordinate.z(), 0, 0));
                }
            }
            for (Cell cell : input.cells()) {
                Cell old = oldCells.get(new CellCoordinate(cell.x(), cell.y(), cell.z()));
                if (!cell.equals(old)) delta.add(cell);
            }
            nextEntries.put(input.chunk(),
                new ChunkEntry(input.amountRevision(), input.terrainGeneration(), newCells));
        }

        chunkedViews.put(player, new ChunkedPlayerView<>(dimension, viewRadius, nextEntries));
        if (!anyChange) return List.of();
        addBatches(updates, dimension, false, List.of(), delta);
        return updates;
    }

    /**
     * {@code viewRadius} is the chunk-tracking view's own radius (e.g. {@code ChunkTrackingView.Positioned
     * .viewDistance()}), used ONLY to tell a real view-distance change apart from the tracked-chunk set merely
     * sliding as the player crosses a chunk boundary — never inferred from the chunk/cell set size.
     */
    List<Update<D>> plan(P player, D dimension, int viewRadius, List<Chunk> authoritativeChunks, List<Cell> visibleCells) {
        List<Chunk> chunkScope = List.copyOf(new LinkedHashSet<>(authoritativeChunks));
        Set<Chunk> currentChunks = Set.copyOf(chunkScope);
        Map<CellCoordinate, Cell> current = index(visibleCells);
        PlayerView<D> previous = views.put(player, new PlayerView<>(dimension, viewRadius, currentChunks, current));
        List<Update<D>> updates = new ArrayList<>();

        if (previous == null || !previous.dimension().equals(dimension)) {
            if (previous != null) {
                updates.add(new Update<>(previous.dimension(), true, true, List.of(), List.of()));
            }
            addBatches(updates, dimension, true, chunkScope, visibleCells);
            return updates;
        }

        if (previous.viewRadius() != viewRadius) {
            addBatches(updates, dimension, true, chunkScope, visibleCells);
            return updates;
        }

        List<Cell> delta = new ArrayList<>();
        for (CellCoordinate old : previous.cells().keySet()) {
            if (!current.containsKey(old)) {
                delta.add(new Cell(old.x(), old.y(), old.z(), 0, 0));
            }
        }
        for (Cell cell : visibleCells) {
            Cell old = previous.cells().get(new CellCoordinate(cell.x(), cell.y(), cell.z()));
            if (!cell.equals(old)) {
                delta.add(cell);
            }
        }
        addBatches(updates, dimension, false, List.of(), delta);
        return updates;
    }

    List<Update<D>> plan(P player, D dimension, List<Cell> visibleCells) {
        List<Chunk> inferredChunks = visibleCells.stream()
            .map(cell -> chunkOf(new CellCoordinate(cell.x(), cell.y(), cell.z())))
            .distinct()
            .toList();
        return plan(player, dimension, inferredChunks, visibleCells);
    }

    void disconnect(P player) {
        views.remove(player);
        chunkedViews.remove(player);
    }

    void reset(P player) {
        views.remove(player);
        chunkedViews.remove(player);
    }

    void retainPlayers(Iterable<P> connectedPlayers) {
        Map<P, Boolean> connected = new HashMap<>();
        connectedPlayers.forEach(player -> connected.put(player, Boolean.TRUE));
        views.keySet().removeIf(player -> !connected.containsKey(player));
        chunkedViews.keySet().removeIf(player -> !connected.containsKey(player));
    }

    void clear() {
        views.clear();
        chunkedViews.clear();
    }

    private Map<CellCoordinate, Cell> index(List<Cell> cells) {
        Map<CellCoordinate, Cell> indexed = new LinkedHashMap<>();
        for (Cell cell : cells) {
            indexed.put(new CellCoordinate(cell.x(), cell.y(), cell.z()), cell);
        }
        return Map.copyOf(indexed);
    }

    private void addBatches(
        List<Update<D>> updates,
        D dimension,
        boolean reset,
        List<Chunk> chunks,
        List<Cell> cells
    ) {
        int batchCount = Math.max(batchCount(chunks.size()), batchCount(cells.size()));
        if (batchCount == 0) {
            if (reset) {
                updates.add(new Update<>(dimension, true, true, List.of(), List.of()));
            }
            return;
        }
        for (int batch = 0; batch < batchCount; batch++) {
            int chunkStart = Math.min(chunks.size(), batch * batchSize);
            int cellStart = Math.min(cells.size(), batch * batchSize);
            int chunkEnd = Math.min(chunks.size(), chunkStart + batchSize);
            int cellEnd = Math.min(cells.size(), cellStart + batchSize);
            updates.add(new Update<>(
                dimension,
                reset && batch == 0,
                reset && batch == batchCount - 1,
                chunks.subList(chunkStart, chunkEnd),
                cells.subList(cellStart, cellEnd)
            ));
        }
    }

    private int batchCount(int size) {
        return (size + batchSize - 1) / batchSize;
    }

    private static Chunk chunkOf(CellCoordinate cell) {
        return new Chunk(
            AtmosphereGridLayout.chunkCoordinate(cell.x()),
            AtmosphereGridLayout.chunkCoordinate(cell.z())
        );
    }
}
