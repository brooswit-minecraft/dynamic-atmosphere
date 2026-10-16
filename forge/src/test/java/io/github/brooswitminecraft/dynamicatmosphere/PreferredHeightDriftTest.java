package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void splitConservesAmountAndRemainderStaysUnmoved() {
        // amount=0 fill fraction, home far above y: thin, so p_up=0.90. amount=100 units stored
        // (capacity is only used for the fill fraction, not as an upper bound on amount here),
        // driftFraction=0.10 -> drift=10 units moved total, 90 stay in the source cell.
        var split = PreferredHeightDrift.split(100, 0, 100, Integer.MAX_VALUE, W, DEAD_BAND, 0.10);
        assertEquals(10, split.up() + split.down());
        assertEquals(9, split.up()); // round(10 * 0.90)
        assertEquals(1, split.down());
    }

    @Test
    void splitMovesNothingWhenDriftRoundsToZero() {
        var split = PreferredHeightDrift.split(100, 0, 4, CAPACITY, W, DEAD_BAND, 0.10);
        assertEquals(0, split.up());
        assertEquals(0, split.down());
    }

    private static double up(int home, int y, int amount) {
        return PreferredHeightDrift.upProbability(home, y, amount, CAPACITY, W, DEAD_BAND);
    }
}
