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
        assertEquals(1, AtmosphereMaterial.ENDER_GAS.cellSize());
        assertEquals(8, AtmosphereMaterial.VIOLENCE.cellSize());
        assertEquals(2, AtmosphereMaterial.EXHAUST.cellSize());
        assertEquals(16, AtmosphereMaterial.SLIME.cellSize());
        assertEquals(-1, AtmosphereMaterial.DUST.cellCoordinate(-1));
        assertEquals(-1, AtmosphereMaterial.DUST.chunkCoordinate(-1));
        assertEquals(-2, AtmosphereMaterial.DUST.chunkCoordinate(-9));
        assertEquals(-1, AtmosphereMaterial.ENDER_GAS.chunkCoordinate(-1));
        assertEquals(-2, AtmosphereMaterial.ENDER_GAS.chunkCoordinate(-17));
    }

    @Test
    void simulationSpeedsAreFourAndEightTimesBase() {
        assertEquals(50, AtmosphereMaterial.DUST.simulationIntervalTicks());
        assertEquals(25, AtmosphereMaterial.ENDER_GAS.simulationIntervalTicks());
        assertEquals(50, AtmosphereMaterial.DUST.nextSimulationTick(1));
        assertEquals(25, AtmosphereMaterial.ENDER_GAS.nextSimulationTick(1));
        assertEquals(100, AtmosphereMaterial.DUST.nextSimulationTick(50));
        assertEquals(50, AtmosphereMaterial.ENDER_GAS.nextSimulationTick(25));
        assertEquals(200, AtmosphereMaterial.VIOLENCE.simulationIntervalTicks());
        assertEquals(50, AtmosphereMaterial.EXHAUST.simulationIntervalTicks());
        assertEquals(400, AtmosphereMaterial.SLIME.simulationIntervalTicks());
        assertEquals(400, AtmosphereMaterial.SLIME.nextSimulationTick(1));
        assertEquals(800, AtmosphereMaterial.SLIME.nextSimulationTick(400));
    }

    @Test
    void producerCadenceScalesWithoutAccumulatingRoundingDrift() {
        assertEquals(75, AtmosphereMaterial.DUST.nextProducerTick(0));
        assertEquals(38, AtmosphereMaterial.ENDER_GAS.nextProducerTick(0));
        assertEquals(75, AtmosphereMaterial.ENDER_GAS.nextProducerTick(38));
        assertEquals(113, AtmosphereMaterial.ENDER_GAS.nextProducerTick(75));
        assertEquals(300, AtmosphereMaterial.VIOLENCE.nextProducerTick(0));
        assertEquals(300, AtmosphereMaterial.VIOLENCE.nextProducerTick(299));
        assertEquals(600, AtmosphereMaterial.VIOLENCE.nextProducerTick(300));
        assertEquals(75, AtmosphereMaterial.EXHAUST.nextProducerTick(0));
        assertEquals(600, AtmosphereMaterial.SLIME.nextProducerTick(0));
        assertEquals(600, AtmosphereMaterial.SLIME.nextProducerTick(599));
        assertEquals(1_200, AtmosphereMaterial.SLIME.nextProducerTick(600));
        assertThrows(IllegalArgumentException.class, () -> AtmosphereMaterial.SLIME.nextProducerTick(-1));
    }

    @Test
    void capacityScalesToEachCellVolume() {
        assertEquals(500, AtmosphereMaterial.DUST.capacityForAirBlocks(4));
        assertEquals(1000, AtmosphereMaterial.ENDER_GAS.capacityForAirBlocks(1));
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
