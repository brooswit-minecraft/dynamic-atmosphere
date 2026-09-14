package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeGridLayoutTest {
    @Test
    void mapsWorldCoordinatesAcrossNegativeBoundaries() {
        assertEquals(0, SmokeGridLayout.cellCoordinate(7));
        assertEquals(1, SmokeGridLayout.cellCoordinate(8));
        assertEquals(-1, SmokeGridLayout.cellCoordinate(-1));
        assertEquals(-2, SmokeGridLayout.cellCoordinate(-9));
        assertEquals(0, SmokeGridLayout.chunkCoordinate(1));
        assertEquals(1, SmokeGridLayout.chunkCoordinate(2));
        assertEquals(-1, SmokeGridLayout.chunkCoordinate(-1));
        assertEquals(-2, SmokeGridLayout.chunkCoordinate(-3));
    }

    @Test
    void airCapacityUsesEightCubedVolume() {
        assertEquals(0, SmokeGridLayout.capacityForAirBlocks(0));
        assertEquals(500, SmokeGridLayout.capacityForAirBlocks(256));
        assertEquals(1000, SmokeGridLayout.capacityForAirBlocks(512));
        assertEquals(200, SmokeGridLayout.nextSimulationTick(0));
        assertEquals(400, SmokeGridLayout.nextSimulationTick(200));
    }
}
