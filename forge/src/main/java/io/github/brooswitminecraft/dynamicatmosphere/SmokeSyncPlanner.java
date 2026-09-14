package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Per-player snapshot/delta planner for a grid with an integral number of cells per chunk. */
final class SmokeSyncPlanner<P, D> {
    record Chunk(int x, int z) { }
    record Cell(int x, int y, int z, int amount, int capacity) { }
    record Update<D>(D dimension, boolean reset, boolean snapshotEnd, List<Chunk> authoritativeChunks,
                     List<Cell> cells) {
        Update {
            authoritativeChunks = List.copyOf(authoritativeChunks);
            cells = List.copyOf(cells);
        }
    }

    private record Coordinate(int x, int y, int z) { }
    private record PlayerView<D>(D dimension, Set<Chunk> chunks, Map<Coordinate, Cell> cells) { }

    private final int batchSize;
    private final IntUnaryOperator chunkCoordinate;
    private final Map<P, PlayerView<D>> views = new HashMap<>();

    SmokeSyncPlanner(int batchSize) {
        this(batchSize, SmokeGridLayout::chunkCoordinate);
    }

    SmokeSyncPlanner(int batchSize, IntUnaryOperator chunkCoordinate) {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        this.batchSize = batchSize;
        this.chunkCoordinate = chunkCoordinate;
    }

    List<Update<D>> plan(P player, D dimension, List<Chunk> authoritativeChunks, List<Cell> visibleCells) {
        List<Chunk> scope = List.copyOf(new LinkedHashSet<>(authoritativeChunks));
        Set<Chunk> currentChunks = Set.copyOf(scope);
        Map<Coordinate, Cell> current = index(visibleCells);
        PlayerView<D> previous = views.put(player, new PlayerView<>(dimension, currentChunks, current));
        var updates = new ArrayList<Update<D>>();

        if (previous == null || !previous.dimension().equals(dimension)) {
            if (previous != null) updates.add(new Update<>(previous.dimension(), true, true, List.of(), List.of()));
            addBatches(updates, dimension, true, scope, visibleCells);
            return updates;
        }
        if (!previous.chunks().equals(currentChunks)) {
            addBatches(updates, dimension, true, scope, visibleCells);
            return updates;
        }

        var delta = new ArrayList<Cell>();
        for (Coordinate old : previous.cells().keySet()) {
            if (!current.containsKey(old) && currentChunks.contains(chunkOf(old))) {
                delta.add(new Cell(old.x(), old.y(), old.z(), 0, 0));
            }
        }
        for (Cell cell : visibleCells) {
            Cell old = previous.cells().get(new Coordinate(cell.x(), cell.y(), cell.z()));
            if (!cell.equals(old)) delta.add(cell);
        }
        addBatches(updates, dimension, false, List.of(), delta);
        return updates;
    }

    void disconnect(P player) { views.remove(player); }
    void reset(P player) { views.remove(player); }
    void retainPlayers(Iterable<P> players) {
        var connected = new LinkedHashSet<P>();
        players.forEach(connected::add);
        views.keySet().removeIf(player -> !connected.contains(player));
    }
    void clear() { views.clear(); }

    private Map<Coordinate, Cell> index(List<Cell> cells) {
        var indexed = new LinkedHashMap<Coordinate, Cell>();
        for (Cell cell : cells) indexed.put(new Coordinate(cell.x(), cell.y(), cell.z()), cell);
        return Map.copyOf(indexed);
    }

    private void addBatches(List<Update<D>> updates, D dimension, boolean reset, List<Chunk> chunks,
                            List<Cell> cells) {
        int batches = Math.max(batchCount(chunks.size()), batchCount(cells.size()));
        if (batches == 0) {
            if (reset) updates.add(new Update<>(dimension, true, true, List.of(), List.of()));
            return;
        }
        for (int batch = 0; batch < batches; batch++) {
            int chunkStart = Math.min(chunks.size(), batch * batchSize);
            int cellStart = Math.min(cells.size(), batch * batchSize);
            updates.add(new Update<>(dimension, reset && batch == 0, reset && batch == batches - 1,
                chunks.subList(chunkStart, Math.min(chunks.size(), chunkStart + batchSize)),
                cells.subList(cellStart, Math.min(cells.size(), cellStart + batchSize))));
        }
    }

    private int batchCount(int size) { return (size + batchSize - 1) / batchSize; }

    private Chunk chunkOf(Coordinate cell) {
        return new Chunk(chunkCoordinate.applyAsInt(cell.x()), chunkCoordinate.applyAsInt(cell.z()));
    }
}
