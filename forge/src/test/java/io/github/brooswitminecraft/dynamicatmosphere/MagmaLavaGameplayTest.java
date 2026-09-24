package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagmaLavaGameplayTest {

    @Test
    void probabilityOneAlwaysRollsAndProbabilityZeroNeverDoes() {
        assertTrue(MagmaLavaGameplay.rollsLava(1.0, 0.0));
        assertTrue(MagmaLavaGameplay.rollsLava(1.0, Math.nextDown(1.0)));
        assertFalse(MagmaLavaGameplay.rollsLava(0.0, 0.0));
        assertFalse(MagmaLavaGameplay.rollsLava(0.0, Math.nextDown(1.0)));
    }

    @Test
    void seededRollIsDeterministicAtTheDefaultChance() {
        var random = new Random(42);
        boolean[] expected = new boolean[20];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = MagmaLavaGameplay.rollsLava(0.1, random.nextDouble());
        }

        var repeat = new Random(42);
        for (boolean value : expected) {
            assertEquals(value, MagmaLavaGameplay.rollsLava(0.1, repeat.nextDouble()));
        }
    }

    @Test
    void statisticalRateAtDefaultChanceStaysWithinToleranceOverManySeededTrials() {
        var random = new Random(1234);
        int trials = 100_000;
        int successes = 0;
        for (int i = 0; i < trials; i++) {
            if (MagmaLavaGameplay.rollsLava(0.1, random.nextDouble())) successes++;
        }

        double rate = successes / (double) trials;
        assertTrue(rate > 0.09 && rate < 0.11, "observed rate " + rate + " outside [0.09, 0.11]");
    }
}
