package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetherGasGameplayTest {
    private static final double MILD = 0.10;
    private static final double DANGEROUS = 0.50;

    @Test
    void emptyOrZeroCapacityCellsHaveNoEffect() {
        assertFalse(NetherGasGameplay.effectFor(0, 1000, MILD, DANGEROUS).active());
        assertFalse(NetherGasGameplay.effectFor(500, 0, MILD, DANGEROUS).active());
    }

    @Test
    void belowMildFullnessHasNoEffect() {
        var effect = NetherGasGameplay.effectFor(99, 1000, MILD, DANGEROUS);
        assertFalse(effect.active());
        assertFalse(effect.mild());
        assertFalse(effect.dangerous());
    }

    @Test
    void atOrAboveMildFullnessAndBelowDangerousIsMildOnly() {
        var effect = NetherGasGameplay.effectFor(100, 1000, MILD, DANGEROUS);
        assertTrue(effect.active());
        assertTrue(effect.mild());
        assertFalse(effect.dangerous());
    }

    @Test
    void atOrAboveDangerousFullnessIsBothMildAndDangerous() {
        var effect = NetherGasGameplay.effectFor(500, 1000, MILD, DANGEROUS);
        assertTrue(effect.active());
        assertTrue(effect.mild());
        assertTrue(effect.dangerous());
    }
}
