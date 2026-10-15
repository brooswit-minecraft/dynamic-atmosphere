package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainCloudSamplingTest {
    @Test
    void configuredCloudHeightDrivesTheRainGateAndAirborneRange() {
        var defaultHeight = RainCloudSampling.forLanding(80, 256);
        var configuredHeight = RainCloudSampling.forLanding(80, 320);

        assertEquals(256, defaultHeight.cloudY());
        assertEquals(256, defaultHeight.airborneUpperY());
        assertEquals(320, configuredHeight.cloudY());
        assertEquals(320, configuredHeight.airborneUpperY());
    }

    @Test
    void terrainAboveTheCloudHeightRemainsTheAirborneUpperBound() {
        var sampling = RainCloudSampling.forLanding(384, 256);

        assertEquals(256, sampling.cloudY());
        assertEquals(384, sampling.airborneUpperY());
    }

    @Test
    void netherAndCeilingDimensionsRemainExcludedFromRainSampling() {
        assertTrue(RainCloudSampling.isEligible(false, false, true, true, true));
        assertFalse(RainCloudSampling.isEligible(true, false, true, true, true));
        assertFalse(RainCloudSampling.isEligible(false, true, true, true, true));
    }
}
