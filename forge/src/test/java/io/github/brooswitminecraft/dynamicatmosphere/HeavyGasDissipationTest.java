package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HeavyGasDissipationTest {
    private static final double SMOKE_CHANCE = SmokeDissipation.CHANCE;
    private static final double DEFAULT_FACTOR = 3.0;

    @Test
    void effectiveChanceIsSmokesChanceDividedByFactor() {
        assertEquals(SMOKE_CHANCE / 3.0, HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, 3.0));
        assertEquals(SMOKE_CHANCE / 1.0, HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, 1.0));
        assertEquals(SMOKE_CHANCE / 6.0, HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, 6.0));
    }

    @Test
    void effectiveChanceIsClampedIntoZeroOneForAnyValidFactor() {
        // A tiny factor would otherwise boost chance far past 1; the effective chance must still land in [0, 1].
        assertEquals(1.0, HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, 0.001));
        assertEquals(0.0, HeavyGasDissipation.effectiveChance(0.0, DEFAULT_FACTOR));
        assertTrue(HeavyGasDissipation.effectiveChance(1.0, 1.0) <= 1.0);
    }

    @Test
    void factorMustBeFiniteAndPositive() {
        assertThrows(IllegalArgumentException.class, () -> HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, 0));
        assertThrows(IllegalArgumentException.class, () -> HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, -1));
        assertThrows(IllegalArgumentException.class,
            () -> HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> HeavyGasDissipation.amount(40, 0, () -> 0));
    }

    @Test
    void rollBoundaryHasExactEffectiveChanceForVoidGas() { assertRollBoundary(); }

    @Test
    void rollBoundaryHasExactEffectiveChanceForObsidianPowder() { assertRollBoundary(); }

    @Test
    void rollBoundaryHasExactEffectiveChanceForSlime() { assertRollBoundary(); }

    private static void assertRollBoundary() {
        double effectiveChance = SMOKE_CHANCE / DEFAULT_FACTOR;
        assertEquals(40, HeavyGasDissipation.amount(40, DEFAULT_FACTOR, () -> Math.nextDown(effectiveChance)));
        for (double roll : new double[]{effectiveChance, 0.5, 1, Double.NaN, -1}) {
            assertEquals(0, HeavyGasDissipation.amount(40, DEFAULT_FACTOR, () -> roll));
        }
    }

    @Test
    void expectedLossPerTurnIsOneThirdOfSmokesForVoidGas() { assertExpectedLossIsOneOverFactorOfSmokes(); }

    @Test
    void expectedLossPerTurnIsOneThirdOfSmokesForObsidianPowder() { assertExpectedLossIsOneOverFactorOfSmokes(); }

    @Test
    void expectedLossPerTurnIsOneThirdOfSmokesForSlime() { assertExpectedLossIsOneOverFactorOfSmokes(); }

    private static void assertExpectedLossIsOneOverFactorOfSmokes() {
        int remaining = 25;
        int smokeMaxLoss = SmokeDissipation.MAX_LOSS;
        double smokeExpectedLoss = SMOKE_CHANCE * Math.min(remaining, smokeMaxLoss);
        double heavyGasExpectedLoss =
            HeavyGasDissipation.effectiveChance(SMOKE_CHANCE, DEFAULT_FACTOR) * Math.min(remaining, smokeMaxLoss);
        assertEquals(smokeExpectedLoss / DEFAULT_FACTOR, heavyGasExpectedLoss, 1e-12);
    }

    @Test
    void voidGasFadesToZeroOverRepeatedDeterministicTurns() { assertFadesToZero(); }

    @Test
    void obsidianPowderFadesToZeroOverRepeatedDeterministicTurns() { assertFadesToZero(); }

    @Test
    void slimeFadesToZeroOverRepeatedDeterministicTurns() { assertFadesToZero(); }

    private static void assertFadesToZero() {
        int remaining = 130;
        // A fixed, deterministic alternating hit/miss roll sequence; no wall-clock timers or sampling.
        boolean hit = true;
        int turns = 0;
        while (remaining > 0 && turns < 1000) {
            double roll = hit ? 0.0 : 1.0;
            hit = !hit;
            remaining -= HeavyGasDissipation.amount(remaining, DEFAULT_FACTOR, () -> roll);
            turns++;
        }
        assertEquals(0, remaining);
    }

    @Test
    void absentMaterialDoesNotRoll() {
        for (int amount : new int[]{0, -1}) {
            assertEquals(0, HeavyGasDissipation.amount(amount, DEFAULT_FACTOR,
                () -> { fail("No material to dissipate"); return 0; }));
        }
    }

    @Test
    void zeroResultUsesExistingDirtyRemovalAndOtherMaterialIsUntouched() {
        var voidGas = new AtmosphereGrid<String>();
        var slime = new AtmosphereGrid<String>();
        var key = new AtmosphereGrid.CellKey<>("world", 0, 0, 0);
        voidGas.set(key, 7, 0, 1000);
        slime.set(key, 7, 0, 1000);
        voidGas.drainDirtyKeys();

        int remaining = voidGas.get(key).amount();
        voidGas.set(key, remaining - HeavyGasDissipation.amount(remaining, DEFAULT_FACTOR, () -> 0), 1, 1000);

        assertNull(voidGas.get(key));
        assertTrue(voidGas.drainDirtyKeys().contains(key));
        assertEquals(7, slime.get(key).amount());
    }

    @Test
    void dustAndSmokeAreUntouchedBySharedHelper() {
        // Smoke and Dust keep their own chance/maxLoss, unaffected by the heavy-gas factor.
        assertEquals(10.0 / 64, SmokeDissipation.CHANCE);
        assertEquals(40, SmokeDissipation.amount(40, () -> Math.nextDown(SMOKE_CHANCE)));
        assertEquals(0, SmokeDissipation.amount(40, () -> SMOKE_CHANCE));
        assertEquals(1.0 / 64, DustDissipation.CHANCE);
        assertEquals(40, DustDissipation.amount(40, () -> Math.nextDown(1.0 / 64)));
        assertEquals(0, DustDissipation.amount(40, () -> 1.0 / 64));
    }
}
