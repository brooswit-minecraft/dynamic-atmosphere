package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void simulationIntervalScalesExactlyWithCellSize() {
        assertEquals(15.625, AtmosphereGridLayout.simulationIntervalTicks(1));
        assertEquals(62.5, AtmosphereGridLayout.simulationIntervalTicks(4));
        assertEquals(250.0, AtmosphereGridLayout.simulationIntervalTicks(16));
        assertEquals(500.0, AtmosphereGridLayout.simulationIntervalTicks(32));
        assertThrows(IllegalArgumentException.class, () -> AtmosphereGridLayout.simulationIntervalTicks(0));
    }

    @Test
    void fractionalCadenceUsesStrictFutureGlobalBoundariesWithoutDrift() {
        assertEquals(63, AtmosphereGridLayout.nextSimulationTick(0, 4));
        assertEquals(125, AtmosphereGridLayout.nextSimulationTick(63, 4));
        assertEquals(188, AtmosphereGridLayout.nextSimulationTick(125, 4));
        assertEquals(250, AtmosphereGridLayout.nextSimulationTick(188, 4));
        assertEquals(313, AtmosphereGridLayout.nextSimulationTick(250, 4));

        assertEquals(16, AtmosphereGridLayout.nextSimulationTick(0, 1));
        assertEquals(32, AtmosphereGridLayout.nextSimulationTick(16, 1));
        assertEquals(47, AtmosphereGridLayout.nextSimulationTick(32, 1));
        assertEquals(250, AtmosphereGridLayout.nextSimulationTick(0, 16));
        assertEquals(500, AtmosphereGridLayout.nextSimulationTick(250, 16));
        assertEquals(500, AtmosphereGridLayout.nextSimulationTick(0, 32));
        assertEquals(1000, AtmosphereGridLayout.nextSimulationTick(500, 32));
    }

    @Test
    void currentCellsRunSixteenChecksPerThousandTicks() {
        int checks = 0;
        for (int tick = 1; tick <= 1000; tick++) {
            if (AtmosphereGridLayout.isSimulationTick(tick)) checks++;
        }
        assertEquals(16, checks);
    }

    @Test
    void sourceTickCheckUsesTheSameCadenceAsGridScheduling() {
        assertFalse(AtmosphereGridLayout.isSimulationTick(62));
        assertTrue(AtmosphereGridLayout.isSimulationTick(63));
        assertTrue(AtmosphereGridLayout.isSimulationTick(125));
        assertFalse(AtmosphereGridLayout.isSimulationTick(126));
        assertTrue(AtmosphereGridLayout.isSimulationTick(250));
        assertTrue(AtmosphereGridLayout.isSimulationTick(313));
    }
}
