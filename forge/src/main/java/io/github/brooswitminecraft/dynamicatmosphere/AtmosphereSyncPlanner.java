package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AtmosphereSyncPlanner<P, D> {

    record Cell(int x, int y, int z, int amount, int capacity) {
    }

    record Update<D>(D dimension, boolean reset, boolean snapshotEnd, List<Cell> cells) {
        Update {
            cells = List.copyOf(cells);
        }
    }

    private record PlayerView<D>(D dimension, Map<CellCoordinate, Cell> cells) {
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

    List<Update<D>> plan(P player, D dimension, List<Cell> visibleCells) {
        Map<CellCoordinate, Cell> current = index(visibleCells);
        PlayerView<D> previous = views.put(player, new PlayerView<>(dimension, current));
        List<Update<D>> updates = new ArrayList<>();

        if (previous == null || !previous.dimension().equals(dimension)) {
            if (previous != null) {
                updates.add(new Update<>(previous.dimension(), true, true, List.of()));
            }
            addBatches(updates, dimension, true, visibleCells);
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
        addBatches(updates, dimension, false, delta);
        return updates;
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

    private void addBatches(List<Update<D>> updates, D dimension, boolean reset, List<Cell> cells) {
        if (cells.isEmpty()) {
            if (reset) {
                updates.add(new Update<>(dimension, true, true, List.of()));
            }
            return;
        }
        for (int start = 0; start < cells.size(); start += batchSize) {
            int end = Math.min(cells.size(), start + batchSize);
            updates.add(new Update<>(dimension, reset && start == 0, reset && end == cells.size(), cells.subList(start, end)));
        }
    }
}
