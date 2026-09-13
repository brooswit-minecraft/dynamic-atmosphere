package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereGridTest {

    @Test
    void mapsNegativeBlockCoordinatesToWorldAlignedCells() {
        assertEquals(0, AtmosphereGrid.cellCoordinate(3));
        assertEquals(1, AtmosphereGrid.cellCoordinate(4));
        assertEquals(3, AtmosphereGrid.cellCoordinate(15));
        assertEquals(-1, AtmosphereGrid.cellCoordinate(-1));
        assertEquals(-1, AtmosphereGrid.cellCoordinate(-4));
        assertEquals(-2, AtmosphereGrid.cellCoordinate(-5));
    }

    @Test
    void distinctSourcesCanAccumulateInOneCellBeforeDecay() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>(4, 10);
        AtmosphereGrid.CellKey<String> key = new AtmosphereGrid.CellKey<>("overworld", 1, 2, 3);

        assertTrue(grid.emit(key, 40, 100));
        assertTrue(grid.emit(key, 20, 100));
        grid.decay(100);

        assertEquals(50, grid.cells().getFirst().amount());
    }

    @Test
    void materialDecaysWithoutFlowAndIsRemovedAtZero() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>(4, 10);
        AtmosphereGrid.CellKey<String> first = new AtmosphereGrid.CellKey<>("overworld", 0, 0, 0);
        AtmosphereGrid.CellKey<String> neighbor = new AtmosphereGrid.CellKey<>("overworld", 1, 0, 0);
        grid.emit(first, 20, 0);

        grid.decay(1);
        assertEquals(10, grid.cells().getFirst().amount());
        assertFalse(grid.cells().stream().anyMatch(cell -> cell.key().equals(neighbor)));

        assertEquals(1, grid.decay(2));
        assertEquals(0, grid.size());
    }

    @Test
    void capacityDoesNotEvictCellsEmittedInCurrentPass() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>(2, 1);
        assertTrue(grid.emit(new AtmosphereGrid.CellKey<>("overworld", 0, 0, 0), 10, 5));
        assertTrue(grid.emit(new AtmosphereGrid.CellKey<>("overworld", 1, 0, 0), 10, 5));
        assertFalse(grid.emit(new AtmosphereGrid.CellKey<>("overworld", 2, 0, 0), 10, 5));
        assertEquals(2, grid.size());
    }

    @Test
    void capacityReusesOlderCellOnLaterPass() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>(1, 1);
        AtmosphereGrid.CellKey<String> old = new AtmosphereGrid.CellKey<>("overworld", 0, 0, 0);
        AtmosphereGrid.CellKey<String> replacement = new AtmosphereGrid.CellKey<>("overworld", 1, 0, 0);
        grid.emit(old, 10, 1);

        assertTrue(grid.emit(replacement, 10, 2));
        assertEquals(replacement, grid.cells().getFirst().key());
    }

    @Test
    void rainSourceDedupesWhileAnotherSourceAddsAndDecayStillRuns() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>(4, 10);
        AtmosphereGrid.CellKey<String> cell = new AtmosphereGrid.CellKey<>("overworld", 0, 4, 0);
        Set<String> emittedSources = new HashSet<>();

        assertTrue(AtmosphereGrid.emitSourceOnce(
            emittedSources, "rain:0,64,0", grid, cell, 40, 100));
        assertFalse(AtmosphereGrid.emitSourceOnce(
            emittedSources, "rain:0,64,0", grid, cell, 40, 100));
        assertTrue(AtmosphereGrid.emitSourceOnce(
            emittedSources, "water:0,63,0", grid, cell, 20, 100));

        grid.decay(100);
        assertEquals(50, grid.cells().getFirst().amount());
    }
}
