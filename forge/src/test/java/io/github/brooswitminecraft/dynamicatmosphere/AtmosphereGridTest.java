package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereGridTest {
    @Test
    void beforeSpreadCallbackRunsOnlyWhenSourceIsDueAndBeforeMaterialIsRead() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        var source = key(0, 0, 0);
        grid.set(source, 500, 1, 1000);
        long due = AtmosphereGridLayout.nextSimulationTick(1);
        var called = new ArrayList<AtmosphereGrid.CellKey<String>>();
        java.util.function.Consumer<AtmosphereGrid.CellKey<String>> callback = cell -> {
            called.add(cell);
            grid.set(cell, grid.get(cell).amount() - 100, due, 1000);
        };
        var capacityAt = capacities(Map.of(source, 1000));

        assertEquals(0, grid.spread(due - 1, capacityAt, callback).sourcesProcessed());
        assertTrue(called.isEmpty());
        assertEquals(1, grid.spread(due, capacityAt, callback).sourcesProcessed());
        assertEquals(List.of(source), called);
        assertEquals(400, amount(grid, source));
        assertEquals(0, grid.spread(due, capacityAt, callback).sourcesProcessed());
        assertEquals(List.of(source), called);
    }

    @Test
    void beforeSpreadCallbackSharesTheOneHundredTwentyEightSourceBudget() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        Set<AtmosphereGrid.CellKey<String>> expected = new HashSet<>();
        for (int x = 0; x < 130; x++) {
            var source = key(x * 3, 0, 0);
            expected.add(source);
            grid.set(source, 1, 1, 1000);
        }
        long due = AtmosphereGridLayout.nextSimulationTick(1);
        var called = new ArrayList<AtmosphereGrid.CellKey<String>>();
        ToIntFunction<AtmosphereGrid.CellKey<String>> capacityAt = cell -> cell.x() % 3 == 0 ? 1000 : 0;

        var first = grid.spread(due, capacityAt, called::add);
        assertEquals(128, first.sourcesProcessed());
        assertEquals(128, called.size());
        assertTrue(first.workRemaining());
        var second = grid.spread(due, capacityAt, called::add);
        assertEquals(2, second.sourcesProcessed());
        assertEquals(130, called.size());
        assertEquals(expected, new HashSet<>(called));
        assertFalse(second.workRemaining());
        grid.spread(due, capacityAt, called::add);
        assertEquals(130, called.size());
    }

    @Test
    void callbackCanConsumeEntireSourceWithoutResurrectionOrStaleCallback() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        var source = key(0, 0, 0);
        var removedBeforeDue = key(10, 0, 0);
        grid.set(source, 1, 1, 1000);
        grid.set(removedBeforeDue, 1, 1, 1000);
        grid.remove(removedBeforeDue);
        grid.drainDirtyKeys();
        long due = AtmosphereGridLayout.nextSimulationTick(1);
        var called = new ArrayList<AtmosphereGrid.CellKey<String>>();

        var result = grid.spread(due, ignored -> 1000, cell -> {
            called.add(cell);
            grid.set(cell, 0, due, 1000);
        });
        assertEquals(List.of(source), called);
        assertEquals(1, result.sourcesProcessed());
        assertEquals(0, result.moved());
        assertTrue(result.blockedCells().isEmpty());
        assertNull(grid.get(source));
        assertEquals(0, grid.size());
        assertEquals(Set.of(source), grid.drainDirtyKeys());
        assertEquals(0, grid.spread(AtmosphereGridLayout.nextSimulationTick(due), ignored -> 1000, called::add).sourcesProcessed());
        assertEquals(List.of(source), called);
    }

    @Test
    void mapsNegativeBlockCoordinatesToWorldAlignedCells() {
        assertEquals(0, AtmosphereGrid.cellCoordinate(3));
        assertEquals(1, AtmosphereGrid.cellCoordinate(4));
        assertEquals(-1, AtmosphereGrid.cellCoordinate(-1));
        assertEquals(-2, AtmosphereGrid.cellCoordinate(-5));
    }

    @Test
    void sourceDeduplicationAllowsIndependentSourcesAndPressureAboveCapacity() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> cell = key(0, 0, 0);
        Set<String> sources = new HashSet<>();

        assertTrue(AtmosphereGrid.emitSourceOnce(sources, "rain", grid, cell, 40, 1, 50));
        assertFalse(AtmosphereGrid.emitSourceOnce(sources, "rain", grid, cell, 40, 1, 50));
        assertTrue(AtmosphereGrid.emitSourceOnce(sources, "water", grid, cell, 20, 1, 50));
        assertEquals(60, amount(grid, cell));
    }

    @Test
    void spreadWaitsForCadenceThenEqualizesNormalizedFullness() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> full = key(0, 0, 0);
        AtmosphereGrid.CellKey<String> half = key(1, 0, 0);
        grid.set(full, 750, 1, 1000);

        assertEquals(0, grid.spread(62, ignored -> 1000).sourcesProcessed());
        AtmosphereGrid.SpreadResult<String> result = grid.spread(63, capacities(Map.of(full, 1000, half, 500)));

        assertEquals(250, result.moved());
        assertEquals(500, amount(grid, full));
        assertEquals(250, amount(grid, half));
        assertEquals(750, total(grid));
    }

    @Test
    void insertionOnCadenceBoundaryWaitsForNextGlobalBoundary() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        grid.set(key(0, 0, 0), 40, 63, 1000);

        assertEquals(0, grid.spread(63, ignored -> 0).sourcesProcessed());
        assertEquals(0, grid.spread(124, ignored -> 0).sourcesProcessed());
        assertEquals(1, grid.spread(125, ignored -> 0).sourcesProcessed());
    }

    @Test
    void ordinarySpreadIsOneHopAndCannotRespendIncomingMaterial() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> west = key(-1, 0, 0);
        AtmosphereGrid.CellKey<String> center = key(0, 0, 0);
        grid.set(west, 700, 1, 1000);
        grid.set(center, 1, 1, 1000);

        grid.spread(101, ignored -> 1000);

        assertFalse(amounts(grid).containsKey(key(2, 0, 0)));
        assertEquals(701, total(grid));
    }

    @Test
    void integerRemainderStaysAtSourceAndMaterialIsConserved() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> source = key(0, 0, 0);
        AtmosphereGrid.CellKey<String> east = key(1, 0, 0);
        AtmosphereGrid.CellKey<String> west = key(-1, 0, 0);
        grid.set(source, 10, 1, 1000);

        grid.spread(101, capacities(Map.of(source, 1000, east, 1000, west, 1000)));

        assertEquals(4, amount(grid, source));
        assertEquals(3, amount(grid, east));
        assertEquals(3, amount(grid, west));
        assertEquals(10, total(grid));
    }

    @Test
    void dueWorkIsLimitedToOneHundredTwentyEightSourcesPerCall() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        for (int x = 0; x < 130; x++) {
            grid.set(key(x * 3, 0, 0), 1, 1, 1000);
        }

        AtmosphereGrid.SpreadResult<String> first = grid.spread(101, key -> key.x() % 3 == 0 ? 1000 : 0);
        AtmosphereGrid.SpreadResult<String> second = grid.spread(101, key -> key.x() % 3 == 0 ? 1000 : 0);

        assertEquals(128, first.sourcesProcessed());
        assertTrue(first.workRemaining());
        assertEquals(2, second.sourcesProcessed());
        assertFalse(second.workRemaining());
        assertTrue(first.blockedCells().isEmpty());
    }

    @Test
    void loadedGridHasNoFixedGlobalCellCap() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        for (int x = 0; x < 1_100; x++) {
            assertTrue(grid.set(key(x, 0, 0), 1, 1, 1000));
        }
        assertEquals(1_100, grid.size());
    }

    @Test
    void overflowResumesAcrossBudgetsUntilItFindsDistantRoom() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> source = key(0, 0, 0);
        AtmosphereGrid.CellKey<String> room = key(600, 0, 0);
        grid.set(source, 1001, 1, 1000);
        ToIntFunction<AtmosphereGrid.CellKey<String>> corridor = key -> {
            if (key.y() != 0 || key.z() != 0 || key.x() < 0 || key.x() > 600) {
                return 0;
            }
            return key.equals(room) ? 1000 : 1;
        };

        AtmosphereGrid.SpreadResult<String> first = grid.spread(101, corridor);
        assertTrue(first.searchLimited());
        assertFalse(first.mayBreakForPressure());

        AtmosphereGrid.SpreadResult<String> result = first;
        for (int attempt = 0; attempt < 10 && result.blockedOverflow() > 0; attempt++) {
            result = grid.redistributeOverflow(102 + attempt, corridor, List.of(source));
            if (result.blockedOverflow() > 0) {
                assertTrue(result.searchLimited());
                assertFalse(result.mayBreakForPressure());
            }
        }
        assertEquals(0, result.blockedOverflow());
        assertEquals(401, amount(grid, room));
        assertEquals(1001, total(grid));
    }

    @Test
    void knownSolidEnclosureReportsPressureEligibleOverflow() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> source = key(0, 0, 0);
        grid.set(source, 100, 1, 1000);

        AtmosphereGrid.SpreadResult<String> result = grid.spread(101, ignored -> 0);

        assertEquals(100, result.blockedOverflow());
        assertEquals(List.of(new AtmosphereGrid.BlockedCell<>(source, 100, true)), result.blockedCells());
        assertTrue(result.mayBreakForPressure());
        assertFalse(result.searchLimited());
    }

    @Test
    void unknownBoundaryDefersPressureBreakingAndRemainsRetryable() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> source = key(0, 0, 0);
        AtmosphereGrid.CellKey<String> east = key(1, 0, 0);
        grid.set(source, 100, 1, 1000);

        AtmosphereGrid.SpreadResult<String> unknown = grid.spread(101, key -> key.equals(source) ? 0 : -1);
        assertTrue(unknown.searchLimited());
        assertFalse(unknown.mayBreakForPressure());

        AtmosphereGrid.SpreadResult<String> loaded = grid.redistributeOverflow(
            102, capacities(Map.of(source, 0, east, 100)), List.of(source));
        assertEquals(100, loaded.overflowMoved());
        assertEquals(100, amount(grid, east));
    }

    @Test
    void emissionsAccumulateToStoredMaximumWithoutDiscardingPressure() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> source = key(0, 0, 0);

        assertTrue(grid.emit(source, 800_000, 1, 1000));
        assertTrue(grid.emit(source, 800_000, 2, 1000));
        assertEquals(AtmosphereGrid.MAX_STORED_AMOUNT, amount(grid, source));
    }

    @Test
    void noPassiveDecayOccursWhenNothingCanSpread() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> source = key(0, 0, 0);
        grid.set(source, 40, 1, 1000);

        grid.spread(101, capacities(Map.of(source, 1000)));
        assertEquals(40, amount(grid, source));
    }

    @Test
    void dirtyDrainAndRestoreSupportChunkPersistenceWithoutFullScans() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> source = key(2, 3, 4);
        AtmosphereGrid.Cell<String> persisted = new AtmosphereGrid.Cell<>(source, 2_000, 500, 9, 10);

        assertTrue(grid.restore(persisted, 20));
        assertEquals(Set.of(source), grid.drainDirtyKeys());
        assertTrue(grid.drainDirtyKeys().isEmpty());
        assertEquals(2_000, grid.get(source).amount());
        assertEquals(9, grid.get(source).lastEmissionTick());
        assertEquals(10, grid.get(source).lastUpdateTick());

        grid.remove(source);
        assertNull(grid.get(source));
        assertEquals(Set.of(source), grid.drainDirtyKeys());
    }

    private static ToIntFunction<AtmosphereGrid.CellKey<String>> capacities(
        Map<AtmosphereGrid.CellKey<String>, Integer> capacities
    ) {
        return key -> capacities.getOrDefault(key, 0);
    }

    private static AtmosphereGrid.CellKey<String> key(int x, int y, int z) {
        return new AtmosphereGrid.CellKey<>("overworld", x, y, z);
    }

    private static int amount(AtmosphereGrid<String> grid, AtmosphereGrid.CellKey<String> key) {
        return amounts(grid).getOrDefault(key, 0);
    }

    private static Map<AtmosphereGrid.CellKey<String>, Integer> amounts(AtmosphereGrid<String> grid) {
        return grid.cells().stream().collect(Collectors.toMap(AtmosphereGrid.Cell::key, AtmosphereGrid.Cell::amount));
    }

    private static int total(AtmosphereGrid<String> grid) {
        return grid.cells().stream().mapToInt(AtmosphereGrid.Cell::amount).sum();
    }
}
