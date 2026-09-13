package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AtmospherePressureTest {

    private record Block(int x, int y, int z) {
    }

    private static final Comparator<Block> XYZ = Comparator.comparingInt(Block::x)
        .thenComparingInt(Block::y).thenComparingInt(Block::z);
    private static final AtmosphereGrid.CellKey<String> SOURCE = cell(0, 0, 0);
    private static final AtmospherePressure.CellScan<Block> AIR = scan(true);

    private static AtmosphereGrid.CellKey<String> cell(int x, int y, int z) {
        return new AtmosphereGrid.CellKey<>("test", x, y, z);
    }

    private static AtmospherePressure.Candidate<Block> block(int x, int y, int z, float hardness) {
        return new AtmospherePressure.Candidate<>(new Block(x, y, z), hardness);
    }

    @SafeVarargs
    private static AtmospherePressure.CellScan<Block> scan(
        boolean hasAir, AtmospherePressure.Candidate<Block>... candidates
    ) {
        return new AtmospherePressure.CellScan<>(hasAir, List.of(candidates));
    }

    private static AtmospherePressure.SearchResult<String, Block> select(
        Map<AtmosphereGrid.CellKey<String>, AtmospherePressure.CellScan<Block>> world
    ) {
        return AtmospherePressure.select(
            SOURCE, world::containsKey, world::get, XYZ, AtmospherePressure.MAX_VISITED_CELLS);
    }

    @Test
    void selectsWeakestInSourceEvenWhenSolidAndNeighborIsSofter() {
        var result = select(Map.of(
            SOURCE, scan(false, block(0, 0, 0, 5), block(1, 0, 0, 2)),
            cell(1, 0, 0), scan(true, block(4, 0, 0, 0))));

        assertEquals(SOURCE, result.selection().orElseThrow().cell());
        assertEquals(new Block(1, 0, 0), result.selection().orElseThrow().block());
        assertFalse(result.searchLimited());
    }

    @Test
    void skipsUnbreakableAndInvalidHardnessButAcceptsZero() {
        var result = select(Map.of(SOURCE, scan(false,
            block(0, 0, 0, -1), block(1, 0, 0, Float.NaN),
            block(2, 0, 0, Float.POSITIVE_INFINITY), block(3, 0, 0, 0))));

        assertEquals(new Block(3, 0, 0), result.selection().orElseThrow().block());
    }

    @Test
    void tiesUseAscendingXThenYThenZRegardlessOfCandidateOrder() {
        var candidates = new ArrayList<>(List.of(
            block(1, 0, 0, 2), block(0, 1, 0, 2), block(0, 0, 1, 2), block(0, 0, 0, 2)));
        for (int i = 0; i < candidates.size(); i++) {
            var result = select(Map.of(SOURCE, new AtmospherePressure.CellScan<>(true, candidates)));
            assertEquals(new Block(0, 0, 0), result.selection().orElseThrow().block());
            candidates.add(candidates.removeFirst());
        }
    }

    @Test
    void solidUnbreakableSourceDoesNotTunnelToNeighbor() {
        var visited = new ArrayList<AtmosphereGrid.CellKey<String>>();
        var result = AtmospherePressure.select(SOURCE, key -> true, key -> {
            visited.add(key);
            return key.equals(SOURCE) ? scan(false, block(0, 0, 0, -1)) : scan(true, block(4, 0, 0, 1));
        }, XYZ, AtmospherePressure.MAX_VISITED_CELLS);

        assertTrue(result.selection().isEmpty());
        assertFalse(result.searchLimited());
        assertEquals(List.of(SOURCE), visited);
    }

    @Test
    void sourceWithAirCanReachAndBreakSolidNeighbor() {
        var neighbor = cell(1, 0, 0);
        var result = select(Map.of(
            SOURCE, scan(true, block(0, 0, 0, -1)),
            neighbor, scan(false, block(4, 0, 0, 3))));

        assertEquals(neighbor, result.selection().orElseThrow().cell());
    }

    @Test
    void cannotCrossSolidUnbreakableNeighborToReachFartherBlock() {
        var result = select(Map.of(
            SOURCE, AIR,
            cell(1, 0, 0), scan(false, block(4, 0, 0, -1)),
            cell(2, 0, 0), scan(true, block(8, 0, 0, 1))));

        assertTrue(result.selection().isEmpty());
        assertFalse(result.searchLimited());
    }

    @Test
    void followsOnlyFaceNeighborsThroughAirAndReturnsActualTargetCell() {
        var target = cell(1, 1, 1);
        var world = new HashMap<AtmosphereGrid.CellKey<String>, AtmospherePressure.CellScan<Block>>();
        world.put(SOURCE, AIR);
        world.put(target, scan(false, block(4, 4, 4, 1)));
        assertTrue(select(world).selection().isEmpty(), "must not jump diagonally");

        world.put(cell(1, 0, 0), AIR);
        world.put(cell(1, 1, 0), AIR);
        assertEquals(target, select(world).selection().orElseThrow().cell());
    }

    @Test
    void breadthFirstSearchPrefersNearCellBeforeSofterFarCell() {
        var near = cell(0, 0, -1);
        var result = select(Map.of(
            SOURCE, AIR,
            cell(1, 0, 0), AIR,
            cell(2, 0, 0), scan(false, block(8, 0, 0, 0)),
            near, scan(false, block(0, 0, -4, 10))));

        assertEquals(near, result.selection().orElseThrow().cell());
    }

    @Test
    void inactiveCellsAreNeverScannedOrTraversed() {
        var world = Map.of(
            SOURCE, AIR,
            cell(1, 0, 0), AIR,
            cell(2, 0, 0), scan(false, block(8, 0, 0, 0)));
        var visited = new ArrayList<AtmosphereGrid.CellKey<String>>();
        var result = AtmospherePressure.select(SOURCE, key -> key.equals(SOURCE), key -> {
            visited.add(key);
            return world.get(key);
        }, XYZ, AtmospherePressure.MAX_VISITED_CELLS);

        assertTrue(result.selection().isEmpty());
        assertFalse(result.searchLimited());
        assertEquals(List.of(SOURCE), visited);
        assertTrue(AtmospherePressure.select(SOURCE, key -> false,
            key -> { throw new AssertionError("inactive source scanned"); }, XYZ, 1).selection().isEmpty());
    }

    @Test
    void finiteEmptyWorldReportsNoCandidateWithoutBudgetExhaustion() {
        var result = select(Map.of(SOURCE, AIR));
        assertTrue(result.selection().isEmpty());
        assertFalse(result.searchLimited());
    }

    @Test
    void unboundedAirSearchVisitsEachCellOnceAndReportsBudgetExhaustion() {
        var visited = new HashSet<AtmosphereGrid.CellKey<String>>();
        AtomicInteger eligibilityChecks = new AtomicInteger();
        var result = AtmospherePressure.select(SOURCE, key -> {
            eligibilityChecks.incrementAndGet();
            return true;
        }, key -> {
            assertTrue(visited.add(key), "duplicate scan");
            assertEquals("test", key.dimension());
            return AIR;
        }, XYZ, AtmospherePressure.MAX_VISITED_CELLS);

        assertTrue(result.selection().isEmpty());
        assertTrue(result.searchLimited());
        assertEquals(1024, visited.size());
        assertEquals(1024, eligibilityChecks.get());
    }

    @Test
    void budgetDoesNotInspectOrSelectBeyondItsFrontier() {
        var result = AtmospherePressure.select(SOURCE, key -> true,
            key -> key.equals(SOURCE) ? AIR : scan(false, block(4, 0, 0, 0)), XYZ, 1);
        assertTrue(result.selection().isEmpty());
        assertTrue(result.searchLimited());
    }

    @Test
    void rejectsInvalidBudgets() {
        for (int budget : new int[] {0, -1, 1025}) {
            assertThrows(IllegalArgumentException.class, () ->
                AtmospherePressure.select(SOURCE, key -> true, key -> AIR, XYZ, budget));
        }
    }

    @Test
    void extremeCoordinatesDoNotWrapToAnUnrelatedCell() {
        var source = cell(Integer.MAX_VALUE, 0, 0);
        var result = AtmospherePressure.select(source, key -> true, key -> {
            assertTrue(key.x() >= Integer.MAX_VALUE - 7);
            return AIR;
        }, XYZ, 7);
        assertTrue(result.selection().isEmpty());
        assertTrue(result.searchLimited());
    }
}
