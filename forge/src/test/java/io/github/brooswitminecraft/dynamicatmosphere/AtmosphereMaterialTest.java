package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereMaterialTest {
    @Test
    void layoutsUseIndependentCellSizesAndNegativeCoordinates() {
        assertEquals(2, AtmosphereMaterial.DUST.cellSize());
        assertEquals(2, AtmosphereMaterial.ENDER_GAS.cellSize());
        assertEquals(8, AtmosphereMaterial.VIOLENCE.cellSize());
        assertEquals(2, AtmosphereMaterial.EXHAUST.cellSize());
        assertEquals(16, AtmosphereMaterial.SLIME.cellSize());
        assertEquals(-1, AtmosphereMaterial.DUST.cellCoordinate(-1));
        assertEquals(-1, AtmosphereMaterial.DUST.chunkCoordinate(-1));
        assertEquals(-2, AtmosphereMaterial.DUST.chunkCoordinate(-9));
        assertEquals(-1, AtmosphereMaterial.ENDER_GAS.chunkCoordinate(-1));
        assertEquals(-2, AtmosphereMaterial.ENDER_GAS.chunkCoordinate(-9));
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
    void capacityScalesToEachCellVolume() {
        assertEquals(500, AtmosphereMaterial.DUST.capacityForAirBlocks(4));
        assertEquals(125, AtmosphereMaterial.ENDER_GAS.capacityForAirBlocks(1));
        assertEquals(1000, AtmosphereMaterial.ENDER_GAS.capacityForAirBlocks(8));
        assertEquals(0, AtmosphereMaterial.ENDER_GAS.capacityForAirBlocks(0));
        assertEquals(500, AtmosphereMaterial.VIOLENCE.capacityForAirBlocks(256));
        assertEquals(500, AtmosphereMaterial.EXHAUST.capacityForAirBlocks(4));
        assertEquals(500, AtmosphereMaterial.SLIME.capacityForAirBlocks(2048));
    }

    @Test
    void immutableStateUsesStrictHalfFullRule() {
        assertFalse(new AtmosphereMaterialState(500, 1000).moreThanHalfFull());
        assertTrue(new AtmosphereMaterialState(501, 1000).moreThanHalfFull());
        assertFalse(new AtmosphereMaterialState(1, 0).moreThanHalfFull());
    }
}
