package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlimeGameplayTest {
    @Test
    void undergroundProductionRequiresSlimeChunkAndFiresUnconditionallyOtherwise() {
        assertEquals(1, SlimeGameplay.UNDERGROUND_CHANCE_DENOMINATOR);
        assertTrue(SlimeGameplay.undergroundEmission(true, 0));
        assertFalse(SlimeGameplay.undergroundEmission(false, 0));
    }

    @Test
    void dissipationSpawnRollIsOneOverDenominator() {
        assertTrue(SlimeGameplay.dissipationSpawnRoll(0));
        assertFalse(SlimeGameplay.dissipationSpawnRoll(1));
    }

    @Test
    void sizeForDensityPicksSmallMediumBigByThreshold() {
        // MINECRAFT-18: consumption-driven spawning is removed; a dissipation-triggered spawn's
        // size is picked by local amount/capacity density at the point of dissipation.
        assertEquals(SlimeGameplay.SMALL_SIZE, SlimeGameplay.sizeForDensity(100, 1_000, 0.25, 0.75));
        assertEquals(SlimeGameplay.MEDIUM_SIZE, SlimeGameplay.sizeForDensity(250, 1_000, 0.25, 0.75));
        assertEquals(SlimeGameplay.MEDIUM_SIZE, SlimeGameplay.sizeForDensity(749, 1_000, 0.25, 0.75));
        assertEquals(SlimeGameplay.BIG_SIZE, SlimeGameplay.sizeForDensity(750, 1_000, 0.25, 0.75));
        assertEquals(SlimeGameplay.BIG_SIZE, SlimeGameplay.sizeForDensity(1_000, 1_000, 0.25, 0.75));
    }

    @Test
    void sizeForDensityTreatsZeroCapacityAsSmall() {
        assertEquals(SlimeGameplay.SMALL_SIZE, SlimeGameplay.sizeForDensity(1, 0, 0.25, 0.75));
    }

    @Test
    void consumptionDrivenSpawnPathIsRemoved() {
        // MINECRAFT-18: onProcessedCell and SpawnDecision no longer exist on SlimeGameplay;
        // this just documents the removal for readers coming from the old test file.
        for (var method : SlimeGameplay.class.getDeclaredMethods()) {
            assertFalse("onProcessedCell".equals(method.getName()),
                "onProcessedCell (consumption-driven spawn) must no longer exist");
        }
    }
}
