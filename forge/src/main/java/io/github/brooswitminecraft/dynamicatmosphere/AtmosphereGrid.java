package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** A bounded-work, server-owned atmospheric material grid. */
public final class AtmosphereGrid<D> {
    public static final int CELL_SIZE = AtmosphereGridLayout.CELL_SIZE;
    public static final int MAX_AMOUNT = 1_000;
    public static final int MAX_STORED_AMOUNT = 1_000_000;
    public static final int MAX_SOURCES_PER_SPREAD = 128;
    public static final int MAX_OVERFLOW_VISITS = 512;
    public static final int TINY_CELL_AMOUNT = 10;

    private static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][] TINY_CELL_DESTINATION_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final Map<CellKey<D>, Cell<D>> cells = new LinkedHashMap<>();
    private final PriorityQueue<WorkItem<D>> workQueue = new PriorityQueue<>(
        Comparator.comparingLong((WorkItem<D> item) -> item.dueTick)
            .thenComparingLong(item -> item.sequence)
    );
    private final Map<CellKey<D>, Long> dueByKey = new HashMap<>();
    private final Set<CellKey<D>> dirtyKeys = new LinkedHashSet<>();
    private final Map<CellKey<D>, OverflowSearch<D>> overflowSearches = new HashMap<>();
    private long nextSequence;

    public static int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CELL_SIZE);
    }

    public static <S, D> boolean emitSourceOnce(
        Set<S> emittedSources,
        S source,
        AtmosphereGrid<D> grid,
        CellKey<D> key,
        int amount,
        long tick,
        int capacity
    ) {
        return emittedSources.add(source) && grid.emit(key, amount, tick, capacity);
    }

    public boolean emit(CellKey<D> key, int amount, long tick) {
        return emit(key, amount, tick, MAX_AMOUNT);
    }

    public boolean emit(CellKey<D> key, int amount, long tick, int capacity) {
        if (amount <= 0) {
            return false;
        }
        Cell<D> existing = cells.get(key);
        int boundedCapacity = boundedCapacity(capacity);
        if (existing == null && boundedCapacity <= 0) {
            return false;
        }
        int current = existing == null ? 0 : existing.amount;
        int nextAmount = (int) Math.min(MAX_STORED_AMOUNT, (long) current + amount);
        if (nextAmount == current) {
            return false;
        }
        putCell(key, nextAmount, boundedCapacity, tick, tick);
        scheduleIfAbsent(key, AtmosphereGridLayout.nextSimulationTick(tick));
        return true;
    }

    public boolean set(CellKey<D> key, int amount, long tick) {
        return set(key, amount, tick, MAX_AMOUNT);
    }

    public boolean set(CellKey<D> key, int amount, long tick, int capacity) {
        if (amount <= 0) {
            return remove(key) != null;
        }
        Cell<D> existing = cells.get(key);
        int boundedCapacity = boundedCapacity(capacity);
        if (existing == null && boundedCapacity <= 0) {
            return false;
        }
        int boundedAmount = Math.min(amount, MAX_STORED_AMOUNT);
        putCell(key, boundedAmount, boundedCapacity, tick, tick);
        scheduleIfAbsent(key, AtmosphereGridLayout.nextSimulationTick(tick));
        return true;
    }

    public Cell<D> get(CellKey<D> key) {
        return cells.get(key);
    }

    public boolean restore(Cell<D> cell, long tick) {
        if (cell.amount <= 0) {
            return false;
        }
        int capacity = boundedCapacity(cell.capacity);
        putCell(
            cell.key,
            Math.min(cell.amount, MAX_STORED_AMOUNT),
            capacity,
            cell.lastEmissionTick,
            cell.lastUpdateTick
        );
        scheduleIfAbsent(cell.key, AtmosphereGridLayout.nextSimulationTick(tick));
        return true;
    }

    public Cell<D> remove(CellKey<D> key) {
        Cell<D> removed = cells.remove(key);
        if (removed != null) {
            dueByKey.remove(key);
            overflowSearches.remove(key);
            dirtyKeys.add(key);
        }
        return removed;
    }

    public SpreadResult<D> spread(long tick, ToIntFunction<CellKey<D>> capacityAt) {
        return spread(tick, capacityAt, ignored -> { });
    }

    public SpreadResult<D> spread(long tick, ToIntFunction<CellKey<D>> capacityAt, Consumer<CellKey<D>> beforeSpread) {
        return spread(tick, capacityAt, beforeSpread, key -> true);
    }

    public SpreadResult<D> spread(long tick, ToIntFunction<CellKey<D>> capacityAt,
        Consumer<CellKey<D>> beforeSpread, Predicate<CellKey<D>> shouldSimulate) {
        List<CellKey<D>> dueSources = pollDueSources(tick, MAX_SOURCES_PER_SPREAD);
        if (dueSources.isEmpty()) {
            return SpreadResult.empty(hasDueWork(tick));
        }
        List<CellKey<D>> sources = new ArrayList<>();
        for (CellKey<D> source : dueSources) {
            if (shouldSimulate.test(source)) sources.add(source);
        }

        for (CellKey<D> source : sources) beforeSpread.accept(source);
        int moved = spreadOneHop(tick, capacityAt, sources);
        SpreadResult<D> overflow = redistributeOverflowInternal(tick, capacityAt, sources);
        int consolidated = consolidateTinySources(tick, capacityAt, sources);
        for (CellKey<D> source : dueSources) {
            if (cells.containsKey(source)) {
                scheduleIfAbsent(source, AtmosphereGridLayout.nextSimulationTick(tick));
            }
        }
        return new SpreadResult<>(
            moved + overflow.overflowMoved + consolidated,
            overflow.overflowMoved,
            overflow.blockedCells,
            sources.size(),
            hasDueWork(tick),
            overflow.searchLimited
        );
    }

    /** Consolidates tiny selected sources without scanning the full grid or creating cells. */
    private int consolidateTinySources(
        long tick,
        ToIntFunction<CellKey<D>> capacityAt,
        List<CellKey<D>> sources
    ) {
        int moved = 0;
        Comparator<CellKey<D>> coordinateOrder = Comparator
            .comparingInt((CellKey<D> key) -> key.x)
            .thenComparingInt(key -> key.y)
            .thenComparingInt(key -> key.z);

        for (CellKey<D> sourceKey : sources) {
            Cell<D> source = cells.get(sourceKey);
            if (source == null || source.amount <= 0 || source.amount > TINY_CELL_AMOUNT) {
                continue;
            }

            Cell<D> destination = null;
            int destinationCapacity = 0;
            for (CellKey<D> neighborKey : tinyCellDestinations(sourceKey)) {
                Cell<D> neighbor = cells.get(neighborKey);
                if (neighbor == null || neighbor.amount <= source.amount) {
                    continue;
                }
                int rawCapacity = capacityAt.applyAsInt(neighborKey);
                if (rawCapacity < 0) {
                    continue;
                }
                int capacity = boundedCapacity(rawCapacity);
                if ((long) neighbor.amount + source.amount > capacity) {
                    continue;
                }
                if (destination == null
                    || neighbor.amount > destination.amount
                    || (neighbor.amount == destination.amount
                        && coordinateOrder.compare(neighbor.key, destination.key) < 0)) {
                    destination = neighbor;
                    destinationCapacity = capacity;
                }
            }
            if (destination == null) {
                continue;
            }

            putCell(destination.key, destination.amount + source.amount, destinationCapacity,
                destination.lastEmissionTick, tick);
            moved += source.amount;
            remove(sourceKey);
        }
        return moved;
    }

    public SpreadResult<D> redistributeOverflow(
        long tick,
        ToIntFunction<CellKey<D>> capacityAt,
        Iterable<CellKey<D>> sourceKeys
    ) {
        List<CellKey<D>> sources = new ArrayList<>();
        for (CellKey<D> key : sourceKeys) {
            if (sources.size() == MAX_SOURCES_PER_SPREAD) {
                break;
            }
            if (cells.containsKey(key)) {
                sources.add(key);
            }
        }
        return redistributeOverflowInternal(tick, capacityAt, sources);
    }

    public Set<CellKey<D>> drainDirtyKeys() {
        Set<CellKey<D>> drained = Set.copyOf(dirtyKeys);
        dirtyKeys.clear();
        return drained;
    }

    public int retain(Predicate<Cell<D>> keep) {
        List<CellKey<D>> removed = cells.values().stream()
            .filter(cell -> !keep.test(cell))
            .map(Cell::key)
            .toList();
        removed.forEach(this::remove);
        return removed.size();
    }

    public List<Cell<D>> cells() {
        return List.copyOf(cells.values());
    }

    public int size() {
        return cells.size();
    }

    public void clear() {
        for (CellKey<D> key : cells.keySet()) {
            dirtyKeys.add(key);
        }
        cells.clear();
        dueByKey.clear();
        workQueue.clear();
        overflowSearches.clear();
    }

    private int spreadOneHop(
        long tick,
        ToIntFunction<CellKey<D>> capacityAt,
        List<CellKey<D>> sources
    ) {
        Set<CellKey<D>> localKeys = new LinkedHashSet<>();
        for (CellKey<D> source : sources) {
            localKeys.add(source);
            localKeys.addAll(neighbors(source));
        }
        Map<CellKey<D>, Integer> capacitySnapshot = new HashMap<>();
        for (CellKey<D> key : localKeys) {
            capacitySnapshot.put(key, capacityAt.applyAsInt(key));
        }
        Map<CellKey<D>, Integer> amountSnapshot = new HashMap<>();
        for (CellKey<D> key : localKeys) {
            Cell<D> cell = cells.get(key);
            amountSnapshot.put(key, cell == null ? 0 : cell.amount);
        }
        Map<CellKey<D>, Integer> deltas = new LinkedHashMap<>();

        for (CellKey<D> sourceKey : sources) {
            int sourceAmount = amountSnapshot.getOrDefault(sourceKey, 0);
            int sourceCapacity = capacitySnapshot.get(sourceKey);
            if (sourceAmount <= 0 || sourceCapacity < 0) {
                continue;
            }

            List<CellKey<D>> neighborhood = new ArrayList<>();
            List<Integer> capacities = new ArrayList<>();
            neighborhood.add(sourceKey);
            capacities.add(boundedCapacity(sourceCapacity));
            long totalAmount = sourceAmount;
            int totalCapacity = boundedCapacity(sourceCapacity);

            for (CellKey<D> neighbor : neighbors(sourceKey)) {
                int capacity = capacitySnapshot.get(neighbor);
                if (capacity <= 0) {
                    continue;
                }
                int bounded = boundedCapacity(capacity);
                neighborhood.add(neighbor);
                capacities.add(bounded);
                totalCapacity += bounded;
                totalAmount += amountSnapshot.getOrDefault(neighbor, 0);
            }
            if (totalCapacity <= 0) {
                continue;
            }

            int sourceTarget = weightedTarget(totalAmount, capacities.getFirst(), totalCapacity);
            int alreadyOutgoing = Math.min(0, deltas.getOrDefault(sourceKey, 0));
            int available = Math.max(0, sourceAmount - sourceTarget + alreadyOutgoing);
            for (int index = 1; index < neighborhood.size() && available > 0; index++) {
                CellKey<D> neighbor = neighborhood.get(index);
                int neighborAmount = amountSnapshot.getOrDefault(neighbor, 0) + deltas.getOrDefault(neighbor, 0);
                int target = weightedTarget(totalAmount, capacities.get(index), totalCapacity);
                int transfer = Math.min(available, Math.max(0, target - neighborAmount));
                if (transfer > 0) {
                    deltas.merge(sourceKey, -transfer, Integer::sum);
                    deltas.merge(neighbor, transfer, Integer::sum);
                    available -= transfer;
                }
            }
        }

        int moved = 0;
        for (Map.Entry<CellKey<D>, Integer> entry : deltas.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            moved += entry.getValue();
        }
        applyDeltas(deltas, tick, capacityAt);
        return moved;
    }

    private SpreadResult<D> redistributeOverflowInternal(
        long tick,
        ToIntFunction<CellKey<D>> capacityAt,
        List<CellKey<D>> sources
    ) {
        int visitsRemaining = MAX_OVERFLOW_VISITS;
        int moved = 0;
        boolean searchLimited = false;
        List<BlockedCell<D>> blocked = new ArrayList<>();

        for (CellKey<D> sourceKey : sources) {
            Cell<D> source = cells.get(sourceKey);
            if (source == null) {
                continue;
            }
            int rawSourceCapacity = capacityAt.applyAsInt(sourceKey);
            if (rawSourceCapacity < 0) {
                searchLimited = true;
                continue;
            }
            int remaining = Math.max(0, source.amount - boundedCapacity(rawSourceCapacity));
            if (remaining == 0) {
                overflowSearches.remove(sourceKey);
                refreshCapacity(sourceKey, rawSourceCapacity, tick);
                continue;
            }

            OverflowSearch<D> search = overflowSearches.get(sourceKey);
            boolean resumedSearch = search != null;
            if (search == null) {
                search = new OverflowSearch<>(sourceKey, neighbors(sourceKey));
                overflowSearches.put(sourceKey, search);
            }
            boolean budgetExhausted = false;

            while (remaining > 0 && !search.frontier.isEmpty()) {
                if (visitsRemaining == 0) {
                    budgetExhausted = true;
                    break;
                }
                CellKey<D> candidate = search.frontier.remove();
                if (search.visited.contains(candidate)) {
                    continue;
                }
                visitsRemaining--;
                int rawCapacity = capacityAt.applyAsInt(candidate);
                if (rawCapacity < 0) {
                    search.unknownFrontier.add(candidate);
                    continue;
                }
                search.visited.add(candidate);
                int capacity = boundedCapacity(rawCapacity);
                if (capacity == 0) {
                    continue;
                }

                Cell<D> existing = cells.get(candidate);
                int candidateAmount = existing == null ? 0 : existing.amount;
                int transfer = Math.min(remaining, Math.max(0, capacity - candidateAmount));
                if (transfer > 0) {
                    putCell(candidate, candidateAmount + transfer, capacity, tick, tick);
                    scheduleIfAbsent(candidate, AtmosphereGridLayout.nextSimulationTick(tick));
                    remaining -= transfer;
                    moved += transfer;
                }
                if (remaining > 0) {
                    search.frontier.addAll(neighbors(candidate));
                }
            }

            int transferred = Math.max(0, source.amount - boundedCapacity(rawSourceCapacity) - remaining);
            if (transferred > 0) {
                int newAmount = source.amount - transferred;
                if (newAmount == 0) {
                    remove(sourceKey);
                } else {
                    putCell(sourceKey, newAmount, boundedCapacity(rawSourceCapacity), source.lastEmissionTick, tick);
                }
            } else {
                refreshCapacity(sourceKey, rawSourceCapacity, tick);
            }

            if (remaining == 0) {
                overflowSearches.remove(sourceKey);
            } else if (!search.unknownFrontier.isEmpty()) {
                search.frontier.addAll(search.unknownFrontier);
                search.unknownFrontier.clear();
                searchLimited = true;
                blocked.add(new BlockedCell<>(sourceKey, remaining, false));
            } else if (budgetExhausted || !search.frontier.isEmpty()) {
                searchLimited = true;
                blocked.add(new BlockedCell<>(sourceKey, remaining, false));
            } else {
                overflowSearches.remove(sourceKey);
                blocked.add(new BlockedCell<>(sourceKey, remaining, !resumedSearch));
                searchLimited |= resumedSearch;
            }
            if (remaining > 0) {
                boolean limited = !blocked.getLast().pressureEligible;
                searchLimited |= limited;
            }
        }

        return new SpreadResult<>(moved, moved, blocked, sources.size(), false, searchLimited);
    }

    private void applyDeltas(
        Map<CellKey<D>, Integer> deltas,
        long tick,
        ToIntFunction<CellKey<D>> capacityAt
    ) {
        for (Map.Entry<CellKey<D>, Integer> entry : deltas.entrySet()) {
            CellKey<D> key = entry.getKey();
            Cell<D> existing = cells.get(key);
            int current = existing == null ? 0 : existing.amount;
            int next = current + entry.getValue();
            if (next <= 0) {
                remove(key);
                continue;
            }
            int rawCapacity = capacityAt.applyAsInt(key);
            int capacity = rawCapacity < 0
                ? (existing == null ? 0 : existing.capacity)
                : boundedCapacity(rawCapacity);
            long emissionTick = existing == null ? tick : existing.lastEmissionTick;
            putCell(key, next, capacity, emissionTick, tick);
            if (existing == null) {
                scheduleIfAbsent(key, AtmosphereGridLayout.nextSimulationTick(tick));
            }
        }
    }

    private List<CellKey<D>> pollDueSources(long tick, int limit) {
        List<CellKey<D>> result = new ArrayList<>(limit);
        discardStaleWork();
        while (result.size() < limit && !workQueue.isEmpty() && workQueue.peek().dueTick <= tick) {
            WorkItem<D> item = workQueue.remove();
            Long due = dueByKey.get(item.key);
            if (due == null || due != item.dueTick || !cells.containsKey(item.key)) {
                discardStaleWork();
                continue;
            }
            dueByKey.remove(item.key);
            result.add(item.key);
            discardStaleWork();
        }
        return result;
    }

    private boolean hasDueWork(long tick) {
        discardStaleWork();
        return !workQueue.isEmpty() && workQueue.peek().dueTick <= tick;
    }

    private void discardStaleWork() {
        while (!workQueue.isEmpty()) {
            WorkItem<D> item = workQueue.peek();
            Long due = dueByKey.get(item.key);
            if (due != null && due == item.dueTick && cells.containsKey(item.key)) {
                return;
            }
            workQueue.remove();
        }
    }

    private void scheduleIfAbsent(CellKey<D> key, long dueTick) {
        if (dueByKey.putIfAbsent(key, dueTick) == null) {
            workQueue.add(new WorkItem<>(key, dueTick, nextSequence++));
        }
    }

    private void refreshCapacity(CellKey<D> key, int capacity, long tick) {
        Cell<D> existing = cells.get(key);
        int bounded = boundedCapacity(capacity);
        if (existing != null && existing.capacity != bounded) {
            putCell(key, existing.amount, bounded, existing.lastEmissionTick, tick);
        }
    }

    private void putCell(CellKey<D> key, int amount, int capacity, long emissionTick, long updateTick) {
        cells.put(key, new Cell<>(key, amount, capacity, emissionTick, updateTick));
        dirtyKeys.add(key);
    }

    private static int boundedCapacity(int capacity) {
        return Math.max(0, Math.min(MAX_AMOUNT, capacity));
    }

    private static int weightedTarget(long totalAmount, int capacity, int totalCapacity) {
        return (int) Math.min(capacity, totalAmount * capacity / totalCapacity);
    }

    private static <D> List<CellKey<D>> neighbors(CellKey<D> key) {
        return offsetNeighbors(key, NEIGHBOR_OFFSETS);
    }

    private static <D> List<CellKey<D>> tinyCellDestinations(CellKey<D> key) {
        return offsetNeighbors(key, TINY_CELL_DESTINATION_OFFSETS);
    }

    private static <D> List<CellKey<D>> offsetNeighbors(CellKey<D> key, int[][] offsets) {
        List<CellKey<D>> neighbors = new ArrayList<>(offsets.length);
        for (int[] offset : offsets) {
            neighbors.add(new CellKey<>(
                key.dimension,
                key.x + offset[0],
                key.y + offset[1],
                key.z + offset[2]
            ));
        }
        return neighbors;
    }

    public record CellKey<D>(D dimension, int x, int y, int z) {}

    public record Cell<D>(
        CellKey<D> key,
        int amount,
        int capacity,
        long lastEmissionTick,
        long lastUpdateTick
    ) {}

    public record BlockedCell<D>(CellKey<D> key, int amount, boolean pressureEligible) {}

    public record SpreadResult<D>(
        int moved,
        int overflowMoved,
        List<BlockedCell<D>> blockedCells,
        int sourcesProcessed,
        boolean workRemaining,
        boolean searchLimited
    ) {
        public SpreadResult {
            blockedCells = List.copyOf(blockedCells);
        }

        private static <D> SpreadResult<D> empty(boolean workRemaining) {
            return new SpreadResult<>(0, 0, List.of(), 0, workRemaining, false);
        }

        public int blockedOverflow() {
            return blockedCells.stream().mapToInt(BlockedCell::amount).sum();
        }

        public boolean mayBreakForPressure() {
            return blockedCells.stream().anyMatch(BlockedCell::pressureEligible);
        }
    }

    private record WorkItem<D>(CellKey<D> key, long dueTick, long sequence) {}

    private static final class OverflowSearch<D> {
        private final Queue<CellKey<D>> frontier;
        private final Set<CellKey<D>> visited = new HashSet<>();
        private final List<CellKey<D>> unknownFrontier = new ArrayList<>();

        private OverflowSearch(CellKey<D> source, List<CellKey<D>> initialFrontier) {
            frontier = new ArrayDeque<>(initialFrontier);
            visited.add(source);
        }
    }
}
