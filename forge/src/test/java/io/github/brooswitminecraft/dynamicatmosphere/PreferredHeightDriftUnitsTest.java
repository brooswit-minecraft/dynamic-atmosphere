package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for a review finding on MINECRAFT-1: home, deadBand and w are all specified in
 * blocks, but a cell's own position in the grid is in cell coordinates. These tests exercise
 * {@link ForgeAtmospherePrototype#cellOriginY}, the actual production conversion the adapter
 * uses, together with {@link PreferredHeightDrift}'s formula — not a hand-rolled block/cell
 * conversion fed arbitrary units — so a regression back to comparing in mismatched units fails.
 */
class PreferredHeightDriftUnitsTest {
    @Test
    void cellOriginIsExpressedInBlocksNotCells() {
        assertEquals(0, ForgeAtmospherePrototype.cellOriginY(0, 4));
        assertEquals(8, ForgeAtmospherePrototype.cellOriginY(2, 4));
        assertEquals(-16, ForgeAtmospherePrototype.cellOriginY(-4, 4));
        assertEquals(20, ForgeAtmospherePrototype.cellOriginY(20, 1));
    }

    @Test
    void aConfiguredDeadBandOfTwoBlocksStaysTwoBlocksRegardlessOfCellSize() {
        int cellSize = AtmosphereGrid.CELL_SIZE; // the production grid's actual cell size (4).
        int deadBand = 2; // the production default, in blocks.
        int home = 15;

        // Cell at block-y=8 (cellY=2): 7 blocks below home, well outside a 2-block dead band.
        // Converting home down to cell units instead (the bug) would have compared
        // floorDiv(15,4)=3 against cellY=2 -- a delta of 1, wrongly INSIDE a dead band of 2.
        int cellY = 2;
        assertEquals(1, PreferredHeightDrift.sign(home, ForgeAtmospherePrototype.cellOriginY(cellY, cellSize), deadBand),
            "home 7 blocks above should be clearly outside a 2-block dead band, not inside it");

        // Cell at block-y=14 (cellY=3, origin=12): 3 blocks below home -- still outside the
        // dead band once compared in blocks (3 > 2), even though both home and this origin
        // floor to the same cell coordinate (3) and would look identical if compared as cells.
        int nearbyCellY = 3;
        assertEquals(1, PreferredHeightDrift.sign(home, ForgeAtmospherePrototype.cellOriginY(nearbyCellY, cellSize), deadBand),
            "3 blocks away must stay outside a 2-block dead band even though both floor to the same cell");

        // Cell at block-y=13 (one cell below the one above, hypothetically): 2 blocks below home,
        // exactly at the dead band boundary -- inside.
        assertEquals(0, PreferredHeightDrift.sign(home, 13, deadBand));
    }
}
