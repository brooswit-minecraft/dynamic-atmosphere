package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereClientViewTest {
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
