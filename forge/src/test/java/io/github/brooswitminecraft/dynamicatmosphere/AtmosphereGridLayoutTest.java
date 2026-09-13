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
        assertEquals(1.5625, AtmosphereGridLayout.simulationIntervalTicks(1));
        assertEquals(6.25, AtmosphereGridLayout.simulationIntervalTicks(4));
        assertEquals(25.0, AtmosphereGridLayout.simulationIntervalTicks(16));
        assertEquals(50.0, AtmosphereGridLayout.simulationIntervalTicks(32));
        assertThrows(IllegalArgumentException.class, () -> AtmosphereGridLayout.simulationIntervalTicks(0));
    }

    @Test
    void fractionalCadenceUsesStrictFutureGlobalBoundariesWithoutDrift() {
        assertEquals(7, AtmosphereGridLayout.nextSimulationTick(0, 4));
        assertEquals(13, AtmosphereGridLayout.nextSimulationTick(7, 4));
        assertEquals(19, AtmosphereGridLayout.nextSimulationTick(13, 4));
        assertEquals(25, AtmosphereGridLayout.nextSimulationTick(19, 4));
        assertEquals(32, AtmosphereGridLayout.nextSimulationTick(25, 4));

        assertEquals(2, AtmosphereGridLayout.nextSimulationTick(0, 1));
        assertEquals(4, AtmosphereGridLayout.nextSimulationTick(2, 1));
        assertEquals(5, AtmosphereGridLayout.nextSimulationTick(4, 1));
        assertEquals(25, AtmosphereGridLayout.nextSimulationTick(0, 16));
        assertEquals(50, AtmosphereGridLayout.nextSimulationTick(25, 16));
        assertEquals(50, AtmosphereGridLayout.nextSimulationTick(0, 32));
        assertEquals(100, AtmosphereGridLayout.nextSimulationTick(50, 32));
    }

    @Test
    void sourceTickCheckUsesTheSameCadenceAsGridScheduling() {
        assertFalse(AtmosphereGridLayout.isSimulationTick(6));
        assertTrue(AtmosphereGridLayout.isSimulationTick(7));
        assertTrue(AtmosphereGridLayout.isSimulationTick(13));
        assertFalse(AtmosphereGridLayout.isSimulationTick(14));
        assertTrue(AtmosphereGridLayout.isSimulationTick(25));
        assertTrue(AtmosphereGridLayout.isSimulationTick(32));
    }
}
