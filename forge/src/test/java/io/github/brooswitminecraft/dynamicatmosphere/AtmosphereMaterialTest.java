package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereMaterialTest {
    @Test
    void layoutsShareTheUniformCellSizeAndSupportNegativeCoordinates() {
        for (var material : AtmosphereMaterial.values()) {
            assertEquals(4, material.cellSize());
            assertEquals(-1, material.cellCoordinate(-1));
            assertEquals(-1, material.chunkCoordinate(-1));
            assertEquals(-3, material.chunkCoordinate(-9));
        }
    }

    @Test
    void allMaterialsUseSharedSimulationCadence() {
        for (var material : AtmosphereMaterial.values()) {
            assertEquals(200, material.simulationIntervalTicks());
            assertEquals(200, material.nextSimulationTick(1));
            assertEquals(400, material.nextSimulationTick(200));
            assertEquals(AtmosphereGridLayout.nextSimulationTick(1), material.nextSimulationTick(1));
            assertEquals(SmokeGridLayout.nextSimulationTick(1), material.nextSimulationTick(1));
        }
    }

    @Test
    void allMaterialsUseSharedProductionCadence() {
        for (var material : AtmosphereMaterial.values()) {
            assertEquals(300, material.nextProducerTick(0));
            assertEquals(300, material.nextProducerTick(299));
            assertEquals(600, material.nextProducerTick(300));
            assertEquals(900, material.nextProducerTick(600));
            assertThrows(IllegalArgumentException.class, () -> material.nextProducerTick(-1));
        }
    }

    @Test
    void capacityScalesToTheSharedSixtyFourBlockCellVolume() {
        for (var material : AtmosphereMaterial.values()) {
            assertEquals(0, material.capacityForAirBlocks(0));
            assertEquals(15, material.capacityForAirBlocks(1));
            assertEquals(500, material.capacityForAirBlocks(32));
            assertEquals(1000, material.capacityForAirBlocks(64));
        }
    }

    @Test
    void immutableStateUsesStrictHalfFullRule() {
        assertFalse(new AtmosphereMaterialState(500, 1000).moreThanHalfFull());
        assertTrue(new AtmosphereMaterialState(501, 1000).moreThanHalfFull());
        assertFalse(new AtmosphereMaterialState(1, 0).moreThanHalfFull());
    }
}
