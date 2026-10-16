package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferredHeightDriftTest {
    private static final double W = 0.40;
    private static final int DEAD_BAND = 2;
    private static final int CAPACITY = 100;

    @Test
    void thinCellAtEmptyFillFavorsHomeAboveSplitsEvenlyInDeadBandAndAvoidsHomeBelow() {
        // home well above y: thin (f=0) should drift toward it.
        assertEquals(0.90, up(100, 0, 0), 1e-9);
        // home well below y: thin should drift away from it (down).
        assertEquals(0.10, up(0, 100, 0), 1e-9);
        // y within the dead band of home: thin splits evenly.
        assertEquals(0.50, up(50, 50, 0), 1e-9);
        assertEquals(0.50, up(50, 49, 0), 1e-9);
        assertEquals(0.50, up(50, 51, 0), 1e-9);
    }

    @Test
    void denseCellAtFullFillAlwaysFavorsDownRegardlessOfHome() {
        assertEquals(0.10, up(100, 0, 100), 1e-9);
        assertEquals(0.10, up(0, 100, 100), 1e-9);
        assertEquals(0.10, up(50, 50, 100), 1e-9);
    }

    @Test
    void belowHomeProbabilityIsLinearInFill() {
        int home = 100;
        int y = 0; // strictly below home - dead band
        assertEquals(0.90, up(home, y, 0), 1e-9);
        assertEquals(0.70, up(home, y, 25), 1e-9);
        assertEquals(0.50, up(home, y, 50), 1e-9);
        assertEquals(0.30, up(home, y, 75), 1e-9);
        assertEquals(0.10, up(home, y, 100), 1e-9);
    }

    @Test
    void aboveHomeProbabilityIsConstantAtAnyFill() {
        int home = 0;
        int y = 100; // strictly above home + dead band
        assertEquals(0.10, up(home, y, 0), 1e-9);
        assertEquals(0.10, up(home, y, 25), 1e-9);
        assertEquals(0.10, up(home, y, 50), 1e-9);
        assertEquals(0.10, up(home, y, 75), 1e-9);
        assertEquals(0.10, up(home, y, 100), 1e-9);
    }

    @Test
    void denseMaterialBelowHomeStillFavorsDownWhileThinMaterialThereFavorsUp() {
        // "dense Smoke below home -> 0.10 vs thin Smoke there -> 0.90"
        int smokeHome = 144;
        int y = 50;
        assertEquals(0.90, up(smokeHome, y, 0), 1e-9);
        assertEquals(0.10, up(smokeHome, y, 100), 1e-9);
    }

    @Test
    void thinMaterialHighAboveItsBottomHomeFavorsDown() {
        // "thin Void Gas high up -> 0.10": Void Gas's home is the dimension bottom.
        int voidGasHome = -64;
        int y = 200;
        assertEquals(0.10, up(voidGasHome, y, 0), 1e-9);
    }

    @Test
    void vaporCentersOnItsDimensionsCloudlineRegardlessOfWhichOneItIs() {
        assertEquals(0.50, up(256, 256, 0), 1e-9); // Overworld-style cloudline
        assertEquals(0.50, up(120, 120, 0), 1e-9); // Nether cloudline
        assertEquals(0.50, up(304, 304, 0), 1e-9); // End cloudline (max build height - 16)
    }

    @Test
    void upAndDownProbabilitiesAlwaysSumToOne() {
        for (int y = -10; y <= 10; y++) {
            for (int amount = 0; amount <= CAPACITY; amount += 10) {
                double up = up(0, y, amount);
                double down = PreferredHeightDrift.downProbability(0, y, amount, CAPACITY, W, DEAD_BAND);
                assertEquals(1.0, up + down, 1e-9, "y=" + y + " amount=" + amount);
            }
        }
    }

    @Test
    void clampsAtTheWeightExtremes() {
        // w=0: direction is irrelevant, always 50/50.
        assertEquals(0.50, PreferredHeightDrift.upProbability(100, 0, 0, CAPACITY, 0, DEAD_BAND), 1e-9);
        assertEquals(0.50, PreferredHeightDrift.upProbability(100, 0, 100, CAPACITY, 0, DEAD_BAND), 1e-9);
        // w=0.5: thin below home reaches the p_up=1.0 boundary exactly, clamped not exceeded.
        assertEquals(1.0, PreferredHeightDrift.upProbability(100, 0, 0, CAPACITY, 0.5, DEAD_BAND), 1e-9);
        // dense above home reaches the p_up=0.0 boundary exactly, clamped not exceeded.
        assertEquals(0.0, PreferredHeightDrift.upProbability(0, 100, CAPACITY, CAPACITY, 0.5, DEAD_BAND), 1e-9);
    }

    private static final int LARGE_CAPACITY = 1000;

    @Test
    void splitConservesAmountAndRemainderStaysUnmoved() {
        // amount=100 of capacity=1000 (f=0.1), home far above y: h=+1. p_up=0.5+0.4*(0.9-0.1)=0.82.
        // driftFraction=0.10 -> driftExact=10.0: up=floor(8.2)=8, down=floor(1.8)=1, 1 unit of the
        // 10-unit drift budget stays as a genuine remainder (on top of the other 900 untouched).
        var split = PreferredHeightDrift.split(100, 0, 100, LARGE_CAPACITY, W, DEAD_BAND, 0.10);
        assertEquals(8, split.up());
        assertEquals(1, split.down());
    }

    @Test
    void splitMovesNothingWhenDriftRoundsToZero() {
        var split = PreferredHeightDrift.split(100, 0, 4, CAPACITY, W, DEAD_BAND, 0.10);
        assertEquals(0, split.up());
        assertEquals(0, split.down());
    }

    @Test
    void splitNeverExceedsTheExactPerLegBudgetEvenWhenTheTotalBudgetIsAFraction() {
        // amount=5 of capacity=1000, driftFraction=0.1 -> driftExact=0.5. Neither leg's exact
        // share reaches 1 unit, so NEITHER leg may move anything: moving a whole unit here would
        // exceed the exact budget of either leg. This is the regression for a previous bug where
        // rounding the combined total first (to 1) and handing the whole unit to whichever leg
        // won let a leg move more than its own exact share, with the "remainder" silently
        // vanishing into the other leg instead of staying at the source.
        var split = PreferredHeightDrift.split(100, 0, 5, LARGE_CAPACITY, W, DEAD_BAND, 0.10);
        assertEquals(0, split.up());
        assertEquals(0, split.down());
    }

    @Test
    void splitLeavesAGenuineRemainderInTheSourceRatherThanRoundingItIntoALeg() {
        // amount=20 of capacity=1000 (f=0.02), home far above: h=+1, p_up=0.5+0.4*(0.98-0.02)=0.884.
        // driftFraction=0.10 -> driftExact=2.0. Exact shares: up=1.768, down=0.232. Flooring each
        // independently gives up=1, down=0 -- moving only 1 of the 2.0-unit budget, with the rest
        // staying as a genuine remainder, never swept into the down leg to fill out the budget.
        var split = PreferredHeightDrift.split(100, 0, 20, LARGE_CAPACITY, W, DEAD_BAND, 0.10);
        assertEquals(1, split.up());
        assertEquals(0, split.down());
        assertTrue(split.up() + split.down() < 20 * 0.10, "moved amount must not reach the full drift budget");
    }

    @Test
    void neitherLegEverExceedsItsOwnExactShareAcrossASweepOfSmallAmountsAndFractions() {
        for (int amount = 0; amount <= 50; amount++) {
            for (double driftFraction : new double[] {0.01, 0.05, 0.1, 0.33, 0.5, 1.0}) {
                var split = PreferredHeightDrift.split(100, 0, amount, LARGE_CAPACITY, W, DEAD_BAND, driftFraction);
                double driftExact = amount * driftFraction;
                double pUp = PreferredHeightDrift.upProbability(100, 0, amount, LARGE_CAPACITY, W, DEAD_BAND);
                String context = "amount=" + amount + " driftFraction=" + driftFraction;
                assertTrue(split.up() <= driftExact * pUp + 1e-9, "up leg exceeded its exact share: " + context);
                assertTrue(split.down() <= driftExact * (1 - pUp) + 1e-9, "down leg exceeded its exact share: " + context);
                assertTrue(split.up() + split.down() <= amount, "moved more than the cell holds: " + context);
            }
        }
    }

    private static double up(int home, int y, int amount) {
        return PreferredHeightDrift.upProbability(home, y, amount, CAPACITY, W, DEAD_BAND);
    }
}
