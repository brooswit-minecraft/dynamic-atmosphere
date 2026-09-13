package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereClientViewTest {
    @Test
    void coarseGroupsAverageWholeSixteenBlockVolumeIncludingEmptySpace() {
        var cells = new ArrayList<AtmosphereClientCache.VisibleCell>();
        for (int x = -4; x < 0; x++) {
            for (int y = -4; y < 0; y++) {
                for (int z = -4; z < 0; z++) {
                    cells.add(new AtmosphereClientCache.VisibleCell(new AtmosphereClientCache.Cell(x, y, z), 500));
                }
            }
        }
        var coarse = AtmosphereClientView.aggregate(cells);
        assertEquals(1, coarse.size());
        assertEquals(new AtmosphereClientView.CoarseCell(-1, -1, -1, 500), coarse.getFirst());
        assertEquals(500 / 64f, AtmosphereClientView.aggregate(cells.subList(0, 1)).getFirst().amount());
    }

    @Test
    void coarseLayerUsesFourTimesViewAndCannotOverlapLoadedDetailChunks() {
        var near = new AtmosphereClientView.CoarseCell(3, 0, 0, 500);
        assertFalse(AtmosphereClientView.coarseVisible(near, 0, 0, 8, true));
        assertTrue(AtmosphereClientView.coarseVisible(near, 0, 0, 8, false));
        var far = new AtmosphereClientView.CoarseCell(32, 0, 0, 500);
        assertTrue(AtmosphereClientView.coarseVisible(far, 0, 0, 8, false));
        assertTrue(AtmosphereClientView.coarseVisible(far, 0, 0, 8, true));
        assertFalse(AtmosphereClientView.coarseVisible(new AtmosphereClientView.CoarseCell(33, 0, 0, 500), 0, 0, 8, false));
    }
    @Test
    void renderDistanceChangesViewWithoutFixedBlockRadius() {
        var cell = new AtmosphereClientCache.Cell(80, 100, 0); // Chunk 20, beyond 128 blocks.
        assertTrue(AtmosphereClientView.contains(cell, 0, 0, 24));
        assertFalse(AtmosphereClientView.contains(cell, 0, 0, 16));
        assertTrue(AtmosphereClientView.contains(new AtmosphereClientCache.Cell(80, -100, 80), 0, 0, 24));
    }

    @Test
    void negativeChunksUseFloorAndSubChunkCellsShareVisibility() {
        assertTrue(AtmosphereClientView.contains(new AtmosphereClientCache.Cell(-1, 0, -4), -0.01, -0.01, 0));
        assertFalse(AtmosphereClientView.contains(new AtmosphereClientCache.Cell(0, 0, -4), -0.01, -0.01, 0));
        assertTrue(AtmosphereClientView.contains(new AtmosphereClientCache.Cell(-5, 0, -4), -0.01, -0.01, 1));
    }
}
