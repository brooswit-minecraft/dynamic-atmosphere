package io.github.brooswitminecraft.dynamicatmosphere.engine.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Test (f) from the ticket: negative-coordinate grid mapping (both
 * directions, including a truncation-vs-floor divergence case), a
 * non-default single scalar cell size, and neighbour lookup across all six
 * faces.
 */
class WeatherGridTest {

    @Test
    void rejectsNonPositiveCellSize() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherGrid(0));
        assertThrows(IllegalArgumentException.class, () -> new WeatherGrid(-1));
    }

    @Test
    void nonDefaultScalarSizeIsHonoredAndCellsStayCubic() {
        // Non-default size (8, not the spec's default of 16). There is only
        // one scalar to configure; cells are always cubes as a result.
        WeatherGrid grid = new WeatherGrid(8);
        assertEquals(8, grid.cellSize());

        CellPos cell = grid.cellOf(new BlockPos(10, 10, 10));
        assertEquals(new CellPos(1, 1, 1), cell);
        // The cell's own block span is exactly cellSize on every axis.
        BlockPos origin = grid.originOf(cell);
        assertEquals(new BlockPos(8, 8, 8), origin);
    }

    @Test
    void negativeCoordinatesUseFloorDivisionNotTruncation() {
        WeatherGrid grid = new WeatherGrid(8);

        // x = -1: floorDiv(-1, 8) = -1 (since -1 = -1*8 + 7), but the naive
        // truncating "/" in Java gives -1 / 8 == 0. If cellOf ever regressed
        // to truncating division, this would wrongly report cell x = 0.
        BlockPos pos = new BlockPos(-1, -1, -1);
        assertNotEquals(-1, -1 / 8, "sanity check: truncating division actually differs from floor here");

        CellPos cell = grid.cellOf(pos);
        assertEquals(new CellPos(-1, -1, -1), cell);

        // The local coordinate must use floorMod, landing in [0, cellSize),
        // not the naive "%" which would give -1 for a negative dividend.
        BlockPos local = grid.localOf(pos);
        assertEquals(new BlockPos(7, 7, 7), local);
    }

    @Test
    void negativeCoordinateRoundTripsThroughWorldOf() {
        WeatherGrid grid = new WeatherGrid(8);
        BlockPos original = new BlockPos(-17, -1, -8);

        CellPos cell = grid.cellOf(original);
        BlockPos local = grid.localOf(original);

        assertEquals(original, grid.worldOf(cell, local));
    }

    @Test
    void neighborLookupCoversAllSixFaces() {
        WeatherGrid grid = new WeatherGrid(8);
        CellPos center = new CellPos(0, 0, 0);

        assertEquals(new CellPos(1, 0, 0), grid.neighborOf(center, Direction.EAST));
        assertEquals(new CellPos(-1, 0, 0), grid.neighborOf(center, Direction.WEST));
        assertEquals(new CellPos(0, 1, 0), grid.neighborOf(center, Direction.UP));
        assertEquals(new CellPos(0, -1, 0), grid.neighborOf(center, Direction.DOWN));
        assertEquals(new CellPos(0, 0, -1), grid.neighborOf(center, Direction.NORTH));
        assertEquals(new CellPos(0, 0, 1), grid.neighborOf(center, Direction.SOUTH));

        // Every neighbor step is reversible via the opposite direction.
        for (Direction d : Direction.values()) {
            CellPos stepped = grid.neighborOf(center, d);
            assertEquals(center, grid.neighborOf(stepped, d.opposite()));
        }
    }

    @Test
    void directionOppositeAndOrientationAreConsistent() {
        assertEquals(Direction.DOWN, Direction.UP.opposite());
        assertEquals(Direction.UP, Direction.DOWN.opposite());
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite());
        assertEquals(Direction.NORTH, Direction.SOUTH.opposite());
        assertEquals(Direction.WEST, Direction.EAST.opposite());
        assertEquals(Direction.EAST, Direction.WEST.opposite());

        Set<Direction> sideways = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) {
                assertEquals(true, d.isUp());
                assertEquals(false, d.isDown());
                assertEquals(false, d.isSideways());
            } else if (d == Direction.DOWN) {
                assertEquals(false, d.isUp());
                assertEquals(true, d.isDown());
                assertEquals(false, d.isSideways());
            } else {
                assertEquals(true, sideways.contains(d));
                assertEquals(false, d.isUp());
                assertEquals(false, d.isDown());
                assertEquals(true, d.isSideways());
            }
        }
    }
}
