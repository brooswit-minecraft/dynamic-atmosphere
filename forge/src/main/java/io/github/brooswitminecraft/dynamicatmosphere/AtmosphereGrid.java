package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class AtmosphereGrid<D> {

    static final int CELL_SIZE = AtmosphereGridLayout.CELL_SIZE;
    static final int MAX_AMOUNT = 1000;

    record CellKey<D>(D dimension, int x, int y, int z) {
    }

    record Cell<D>(CellKey<D> key, int amount, long lastEmissionTick, long lastUpdateTick) {
    }

    private final int capacity;
    private final int decayPerPass;
    private final Map<CellKey<D>, Cell<D>> cells = new LinkedHashMap<>();

    AtmosphereGrid(int capacity, int decayPerPass) {
        if (capacity <= 0 || decayPerPass <= 0) {
            throw new IllegalArgumentException("capacity and decayPerPass must be positive");
        }
        this.capacity = capacity;
        this.decayPerPass = decayPerPass;
    }

    static int cellCoordinate(int blockCoordinate) {
        return AtmosphereGridLayout.cellCoordinate(blockCoordinate);
    }

    static <S, D> boolean emitSourceOnce(
        Set<S> emittedSources,
        S source,
        AtmosphereGrid<D> grid,
        CellKey<D> cell,
        int amount,
        long tick
    ) {
        return emittedSources.add(source) && grid.emit(cell, amount, tick);
    }

    boolean emit(CellKey<D> key, int amount, long tick) {
        if (amount <= 0) {
            return false;
        }

        Cell<D> existing = cells.get(key);
        if (existing == null && cells.size() >= capacity && !evictOlderThan(tick)) {
            return false;
        }

        int current = existing == null ? 0 : existing.amount();
        cells.put(key, new Cell<>(key, Math.min(MAX_AMOUNT, current + amount), tick, tick));
        return true;
    }

    boolean set(CellKey<D> key, int amount, long tick) {
        int boundedAmount = Math.max(0, Math.min(MAX_AMOUNT, amount));
        if (boundedAmount == 0) {
            return cells.remove(key) != null;
        }
        if (!cells.containsKey(key) && cells.size() >= capacity && !evictOlderThan(tick)) {
            return false;
        }
        cells.put(key, new Cell<>(key, boundedAmount, tick, tick));
        return true;
    }

    int decay(long tick) {
        int removed = 0;
        for (Map.Entry<CellKey<D>, Cell<D>> entry : List.copyOf(cells.entrySet())) {
            Cell<D> cell = entry.getValue();
            int amount = Math.max(0, cell.amount() - decayPerPass);
            if (amount == 0) {
                cells.remove(entry.getKey());
                removed++;
            } else {
                cells.put(entry.getKey(), new Cell<>(entry.getKey(), amount, cell.lastEmissionTick(), tick));
            }
        }
        return removed;
    }

    int retain(Predicate<CellKey<D>> keep) {
        int before = cells.size();
        cells.keySet().removeIf(key -> !keep.test(key));
        return before - cells.size();
    }

    List<Cell<D>> cells() {
        return List.copyOf(cells.values());
    }

    int size() {
        return cells.size();
    }

    void clear() {
        cells.clear();
    }

    private boolean evictOlderThan(long tick) {
        Cell<D> oldest = cells.values().stream()
            .filter(cell -> cell.lastEmissionTick() < tick)
            .min(Comparator.comparingLong(Cell::lastEmissionTick))
            .orElse(null);
        if (oldest == null) {
            return false;
        }
        cells.remove(oldest.key());
        return true;
    }
}
