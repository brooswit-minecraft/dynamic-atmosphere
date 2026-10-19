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

    private final int batchSize;
    private final Map<P, PlayerView<D>> views = new HashMap<>();

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
    }

    void reset(P player) {
        views.remove(player);
    }

    void retainPlayers(Iterable<P> connectedPlayers) {
        Map<P, Boolean> connected = new HashMap<>();
        connectedPlayers.forEach(player -> connected.put(player, Boolean.TRUE));
        views.keySet().removeIf(player -> !connected.containsKey(player));
    }

    void clear() {
        views.clear();
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
