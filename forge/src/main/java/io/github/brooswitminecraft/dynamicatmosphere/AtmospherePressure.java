package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.DoubleSupplier;

/** Pure selection for pressure that could not discharge into existing space. */
final class AtmospherePressure {

    static final int MAX_VISITED_CELLS = 1024;
    private static final int[][] FACES = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    record Candidate<B>(B block, float hardness) {
        Candidate {
            Objects.requireNonNull(block, "block");
        }
    }

    /** Candidates must be blocks whose destruction can create air. */
    record CellScan<B>(int emptyBlocks, int totalBlocks, List<Candidate<B>> candidates) {
        CellScan {
            if (totalBlocks <= 0 || emptyBlocks < 0 || emptyBlocks > totalBlocks) {
                throw new IllegalArgumentException("invalid cell occupancy");
            }
            candidates = List.copyOf(candidates);
        }
        CellScan(boolean hasAir, List<Candidate<B>> candidates) {
            this(hasAir ? 1 : 0, 1, candidates);
        }
        boolean hasAir() { return emptyBlocks > 0; }
    }

    record Selection<D, B>(AtmosphereGrid.CellKey<D> cell, B block) {
    }

    record SearchResult<D, B>(Optional<Selection<D, B>> selection, boolean searchLimited) {
    }

    /** One scope roll per authorized attempt, including the zero/all-air endpoints. */
    static <D, B> SearchResult<D, B> selectForAttempt(
        AtmosphereGrid.CellKey<D> source,
        Predicate<AtmosphereGrid.CellKey<D>> activeLoaded,
        Function<AtmosphereGrid.CellKey<D>, CellScan<B>> scan,
        Comparator<? super B> blockOrder,
        int maxVisitedCells,
        BiPredicate<AtmosphereGrid.CellKey<D>, AtmosphereGrid.CellKey<D>> canTransfer,
        DoubleSupplier roll
    ) {
        if (maxVisitedCells <= 0 || maxVisitedCells > MAX_VISITED_CELLS) {
            throw new IllegalArgumentException("invalid visit budget");
        }
        if (!activeLoaded.test(source)) return new SearchResult<>(Optional.empty(), true);
        CellScan<B> contents = Objects.requireNonNull(scan.apply(source));
        double draw = roll.getAsDouble();
        if (!(draw >= 0 && draw < 1)) throw new IllegalArgumentException("roll must be in [0,1)");
        boolean neighbors = draw < (double) contents.emptyBlocks() / contents.totalBlocks();
        if (!neighbors) {
            return select(source, source::equals, ignored -> contents, blockOrder, 1, (from, to) -> false);
        }
        // Strip source candidates without rescanning it; only neighbor scope may be selected now.
        CellScan<B> transit = new CellScan<>(contents.emptyBlocks(), contents.totalBlocks(), List.of());
        return select(source, activeLoaded, cell -> cell.equals(source) ? transit : scan.apply(cell),
            blockOrder, maxVisitedCells, canTransfer);
    }

    /**
     * Prefers the weakest eligible block in the source cell, then the first
     * nearest breakable face-neighbor layer. Select its weakest block;
     * hardness ties use blockOrder.
     * A cell with no air may be broken into but is never traversed. This is
     * cell-level adjacency, not a claim of exact voxel paths within a cell.
     *
     * <p>The visit budget includes rejected/inactive cells. Each discovered
     * cell is queued once, and neither the queue nor scans exceed the budget.
     */
    static <D, B> SearchResult<D, B> select(
        AtmosphereGrid.CellKey<D> source,
        Predicate<AtmosphereGrid.CellKey<D>> activeLoaded,
        Function<AtmosphereGrid.CellKey<D>, CellScan<B>> scan,
        Comparator<? super B> blockOrder,
        int maxVisitedCells
    ) {
        return select(source, activeLoaded, scan, blockOrder, maxVisitedCells,
            (from, to) -> true);
    }

    static <D, B> SearchResult<D, B> select(
        AtmosphereGrid.CellKey<D> source,
        Predicate<AtmosphereGrid.CellKey<D>> activeLoaded,
        Function<AtmosphereGrid.CellKey<D>, CellScan<B>> scan,
        Comparator<? super B> blockOrder,
        int maxVisitedCells,
        BiPredicate<AtmosphereGrid.CellKey<D>, AtmosphereGrid.CellKey<D>> canTransfer
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activeLoaded, "activeLoaded");
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(blockOrder, "blockOrder");
        Objects.requireNonNull(canTransfer, "canTransfer");
        if (maxVisitedCells <= 0 || maxVisitedCells > MAX_VISITED_CELLS) {
            throw new IllegalArgumentException("visit budget must be between 1 and " + MAX_VISITED_CELLS);
        }

        var queue = new ArrayDeque<AtmosphereGrid.CellKey<D>>();
        var discovered = new HashSet<AtmosphereGrid.CellKey<D>>();
        queue.add(source);
        discovered.add(source);
        boolean searchLimited = false;
        while (!queue.isEmpty()) {
            int layerSize = queue.size();
            Candidate<B> layerWeakest = null;
            AtmosphereGrid.CellKey<D> weakestCell = null;
            for (int layerIndex = 0; layerIndex < layerSize; layerIndex++) {
            AtmosphereGrid.CellKey<D> cell = queue.removeFirst();
            if (!activeLoaded.test(cell)) {
                continue;
            }
            CellScan<B> contents = Objects.requireNonNull(scan.apply(cell), "cell scan");
            Candidate<B> weakest = null;
            for (Candidate<B> candidate : contents.candidates()) {
                if (Float.isFinite(candidate.hardness()) && candidate.hardness() >= 0
                    && (weakest == null || candidate.hardness() < weakest.hardness()
                        || (candidate.hardness() == weakest.hardness()
                            && blockOrder.compare(candidate.block(), weakest.block()) < 0))) {
                    weakest = candidate;
                }
            }
            if (weakest != null) {
                if (layerWeakest == null || weakest.hardness() < layerWeakest.hardness()
                    || (weakest.hardness() == layerWeakest.hardness()
                        && blockOrder.compare(weakest.block(), layerWeakest.block()) < 0)) {
                    layerWeakest = weakest;
                    weakestCell = cell;
                }
            }
            if (!contents.hasAir()) {
                continue;
            }
            for (int[] face : FACES) {
                long x = (long) cell.x() + face[0];
                long y = (long) cell.y() + face[1];
                long z = (long) cell.z() + face[2];
                if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                    || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                    || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
                    continue;
                }
                var next = new AtmosphereGrid.CellKey<>(cell.dimension(), (int) x, (int) y, (int) z);
                if (discovered.contains(next) || !canTransfer.test(cell, next)) {
                    continue;
                }
                if (discovered.size() >= maxVisitedCells) {
                    searchLimited = true;
                    continue;
                }
                discovered.add(next);
                queue.addLast(next);
            }
            }
            if (layerWeakest != null) {
                return new SearchResult<>(Optional.of(new Selection<>(weakestCell, layerWeakest.block())), false);
            }
        }
        return new SearchResult<>(Optional.empty(), searchLimited);
    }

    private AtmospherePressure() {
    }
}
