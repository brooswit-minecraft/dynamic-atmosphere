package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void producerAndSimulationCadencesAreIndependent() {
        assertEquals(50, AtmosphereGridLayout.PRODUCER_INTERVAL_TICKS);
        assertEquals(200, AtmosphereGridLayout.simulationIntervalTicks());
    }

    @Test
    void simulationUsesStrictFutureTwoHundredTickBoundaries() {
        assertEquals(200, AtmosphereGridLayout.nextSimulationTick(0));
        assertEquals(200, AtmosphereGridLayout.nextSimulationTick(199));
        assertEquals(400, AtmosphereGridLayout.nextSimulationTick(200));
        assertEquals(600, AtmosphereGridLayout.nextSimulationTick(401));
    }

    @Test
    void currentCellsRunFiveChecksPerThousandTicks() {
        int checks = 0;
        for (int tick = 1; tick <= 1000; tick++) {
            if (AtmosphereGridLayout.isSimulationTick(tick)) checks++;
        }
        assertEquals(5, checks);
    }

    @Test
    void sourceTickCheckUsesTheSameCadenceAsGridScheduling() {
        assertFalse(AtmosphereGridLayout.isSimulationTick(0));
        assertFalse(AtmosphereGridLayout.isSimulationTick(199));
        assertTrue(AtmosphereGridLayout.isSimulationTick(200));
        assertFalse(AtmosphereGridLayout.isSimulationTick(201));
        assertTrue(AtmosphereGridLayout.isSimulationTick(400));
        assertTrue(AtmosphereGridLayout.isSimulationTick(1000));
    }
}
