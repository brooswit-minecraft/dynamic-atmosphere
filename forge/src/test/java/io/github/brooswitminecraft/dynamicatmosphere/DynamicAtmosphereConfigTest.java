package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DynamicAtmosphereConfigTest {
    @Test
    void serverSnapshotFallsBackToCurrentDefaultsBeforeConfigLoads() {
        var first = DynamicAtmosphereServerConfig.snapshot();
        var second = DynamicAtmosphereServerConfig.snapshot();

        assertSame(first, second);
        assertEquals(4, first.smoke().lavaEmission());
        assertEquals(1.0, first.smoke().leafRemovalChance());
        assertEquals(10.0 / 128, first.smoke().farmlandConversionChance());
        assertEquals(10.0 / 256, first.smoke().villagerConversionChance());
        assertEquals(10.0 / 64, first.smoke().dissipationChance());
        assertEquals(0.75, first.runtime().simulationSkipChance());
        assertEquals(100, first.enderGas().portalBlockEmission());
        assertEquals(192, first.vapor().rainCloudHeight());
        assertEquals(24, first.vapor().highTerrainBlocksAboveSeaLevel());
        assertEquals(200, first.runtime().simulationIntervalTicks());
        assertEquals(300, first.runtime().producerIntervalTicks());
        assertEquals(1.0, first.integrations().createFanTransportPerRpm());
        assertEquals(100, first.integrations().createFanIntervalTicks());
        assertEquals(32, first.integrations().maxFanChunksPerTick());
        assertEquals(3.0, first.heavyGas().dissipationFactor());
    }

    @Test
    void clientSnapshotFallsBackToCurrentDefaultsBeforeConfigLoads() {
        var first = DynamicAtmosphereClientConfig.snapshot();
        var second = DynamicAtmosphereClientConfig.snapshot();

        assertSame(first, second);
        assertEquals(4, first.slicesPerBaseCell());
        assertEquals(4096, first.selectionWorkPerTick());
        assertEquals(2, first.smoke().reachMultiplier());
        assertEquals(2, first.smoke().opticalDensity());
        assertEquals(40, first.enderGas().opticalDensity());
        assertEquals(200_000, first.allocation().cellBudget());
    }

    @Test
    void invalidServerValuesFallBackInsteadOfEscaping() {
        assertEquals(4, DynamicAtmosphereServerConfig.validInt(null, 4, 0, 10));
        assertEquals(4, DynamicAtmosphereServerConfig.validInt(-1, 4, 0, 10));
        assertEquals(4, DynamicAtmosphereServerConfig.validInt(11, 4, 0, 10));
        assertEquals(0.5, DynamicAtmosphereServerConfig.validDouble(Double.NaN, 0.5, 0, 1));
        assertEquals(0.5, DynamicAtmosphereServerConfig.validDouble(2.0, 0.5, 0, 1));
    }

    @Test
    void invalidClientValuesFallBackInsteadOfEscaping() {
        assertEquals(4096, DynamicAtmosphereClientConfig.validInt(null, 4096, 1, 10_000));
        assertEquals(4096, DynamicAtmosphereClientConfig.validInt(0, 4096, 1, 10_000));
        assertEquals(2.0, DynamicAtmosphereClientConfig.validDouble(
            Double.POSITIVE_INFINITY, 2.0, 0.25, 4));
        assertEquals(2.0, DynamicAtmosphereClientConfig.validDouble(0.1, 2.0, 0.25, 4));
    }
}
