package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExhaustGameplayTest {
    @Test
    void creepersProduceFrequentlyAndOtherLivingMobsRandomly() {
        assertEquals(1, ExhaustGameplay.passiveEmission(false, 0));
        assertEquals(0, ExhaustGameplay.passiveEmission(false, 1.0 / 128.0));
        assertEquals(1, ExhaustGameplay.passiveEmission(true, 1.0 / 16.0 - 0.000001));
        assertEquals(0, ExhaustGameplay.passiveEmission(true, 1.0 / 16.0));
        assertEquals(0, ExhaustGameplay.passiveEmission(true, Double.NaN));
    }

    @Test
    void successfulDamageProducesBoundedExhaustButSelfDamageDoesNot() {
        assertEquals(0, ExhaustGameplay.damageEmission(0, false));
        assertEquals(4, ExhaustGameplay.damageEmission(1, false));
        assertEquals(5, ExhaustGameplay.damageEmission(1.01f, false));
        assertEquals(64, ExhaustGameplay.damageEmission(100, false));
        assertEquals(0, ExhaustGameplay.damageEmission(4, true));
        assertEquals(0, ExhaustGameplay.damageEmission(Float.NaN, false));
    }

    @Test
    void suffocationScalesFromHalfFullAndClampsBeyondCapacity() {
        assertFalse(ExhaustGameplay.suffocation(499, 1000).active());
        assertEquals(new ExhaustGameplay.Suffocation(1, 125),
            ExhaustGameplay.suffocation(500, 1000));
        assertEquals(new ExhaustGameplay.Suffocation(2.5f, 282),
            ExhaustGameplay.suffocation(750, 1000));
        assertEquals(new ExhaustGameplay.Suffocation(4, 500),
            ExhaustGameplay.suffocation(1000, 1000));
        assertEquals(new ExhaustGameplay.Suffocation(4, 1000),
            ExhaustGameplay.suffocation(2000, 1000));
        assertFalse(ExhaustGameplay.suffocation(1000, 0).active());
    }

    @Test
    void exposureUsesOnlyTheTwoBlockExhaustCell() {
        assertTrue(ExhaustGameplay.sameExhaustCell(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1)));
        assertFalse(ExhaustGameplay.sameExhaustCell(new BlockPos(0, 0, 0), new BlockPos(2, 1, 1)));
        assertTrue(ExhaustGameplay.sameExhaustCell(new BlockPos(-1, -1, -1), new BlockPos(-2, -2, -2)));
        assertFalse(ExhaustGameplay.sameExhaustCell(new BlockPos(-1, -1, -1), new BlockPos(-3, -2, -2)));
    }
}
