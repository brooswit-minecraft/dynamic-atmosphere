package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DustTransformationsTest {
    @Test
    void mudChanceIsLinearFromHalfToFullAndClamped() {
        assertEquals(0, DustTransformations.mudChance(499, 1000));
        assertEquals(0, DustTransformations.mudChance(500, 1000));
        assertEquals(0.5, DustTransformations.mudChance(750, 1000));
        assertEquals(1, DustTransformations.mudChance(1000, 1000));
        assertEquals(1, DustTransformations.mudChance(2000, 1000));
        assertEquals(0, DustTransformations.mudChance(1000, 0));
    }

    @Test
    void successfulMudConversionCostsHalfCurrentDustRoundedUp() {
        assertEquals(0, DustTransformations.mudCost(0));
        assertEquals(50, DustTransformations.mudCost(100));
        assertEquals(51, DustTransformations.mudCost(101));
    }

    @Test
    void gravelRequiresOverfullDustAndConservativeRoll() {
        assertFalse(DustTransformations.createsGravel(1000, 1000, 0));
        assertTrue(DustTransformations.createsGravel(1001, 1000, 0));
        assertFalse(DustTransformations.createsGravel(1001, 1000, DustTransformations.GRAVEL_CHANCE));
    }

    @Test
    void successfulGravelCostsOneQuarterCurrentDustLikeVapor() {
        assertEquals(0, DustTransformations.gravelCost(0));
        assertEquals(1, DustTransformations.gravelCost(1));
        assertEquals(1, DustTransformations.gravelCost(7));
        assertEquals(2, DustTransformations.gravelCost(8));
        assertEquals(250, DustTransformations.gravelCost(1001));
    }
}
