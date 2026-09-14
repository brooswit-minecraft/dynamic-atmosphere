package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeGridLayoutTest {
    @Test
    void mapsWorldCoordinatesAcrossNegativeBoundaries() {
        assertEquals(0, SmokeGridLayout.cellCoordinate(3));
        assertEquals(1, SmokeGridLayout.cellCoordinate(4));
        assertEquals(-1, SmokeGridLayout.cellCoordinate(-1));
        assertEquals(-2, SmokeGridLayout.cellCoordinate(-5));
        assertEquals(0, SmokeGridLayout.chunkCoordinate(1));
        assertEquals(1, SmokeGridLayout.chunkCoordinate(4));
        assertEquals(-1, SmokeGridLayout.chunkCoordinate(-1));
        assertEquals(-2, SmokeGridLayout.chunkCoordinate(-5));
    }

    @Test
    void airCapacityUsesFourCubedVolume() {
        assertEquals(0, SmokeGridLayout.capacityForAirBlocks(0));
        assertEquals(500, SmokeGridLayout.capacityForAirBlocks(32));
        assertEquals(1000, SmokeGridLayout.capacityForAirBlocks(64));
        assertEquals(200, SmokeGridLayout.nextSimulationTick(0));
        assertEquals(400, SmokeGridLayout.nextSimulationTick(200));
    }
}
