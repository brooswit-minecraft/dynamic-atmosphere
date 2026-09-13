package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosphereGridLayoutTest {

    @Test
    void capacityScalesWithVacantBlockCount() {
        assertEquals(0, AtmosphereGridLayout.capacityForAirBlocks(0));
        assertEquals(250, AtmosphereGridLayout.capacityForAirBlocks(16));
        assertEquals(500, AtmosphereGridLayout.capacityForAirBlocks(32));
        assertEquals(1000, AtmosphereGridLayout.capacityForAirBlocks(64));
        assertEquals(0, AtmosphereGridLayout.capacityForAirBlocks(-1));
        assertEquals(1000, AtmosphereGridLayout.capacityForAirBlocks(65));
    }

    @Test
    void mapsCellCoordinatesToContainingMinecraftChunk() {
        assertEquals(0, AtmosphereGridLayout.chunkCoordinate(0));
        assertEquals(0, AtmosphereGridLayout.chunkCoordinate(1));
        assertEquals(0, AtmosphereGridLayout.chunkCoordinate(2));
        assertEquals(0, AtmosphereGridLayout.chunkCoordinate(3));
        assertEquals(1, AtmosphereGridLayout.chunkCoordinate(4));
        assertEquals(-1, AtmosphereGridLayout.chunkCoordinate(-1));
        assertEquals(-1, AtmosphereGridLayout.chunkCoordinate(-2));
        assertEquals(-1, AtmosphereGridLayout.chunkCoordinate(-3));
        assertEquals(-1, AtmosphereGridLayout.chunkCoordinate(-4));
        assertEquals(-2, AtmosphereGridLayout.chunkCoordinate(-5));
    }
}
