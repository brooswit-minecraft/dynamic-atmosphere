package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoldGrowthTest {
    @Test void insufficientFundsThresholdOrFailedRollNeverRunAction() {
        java.util.function.BooleanSupplier forbidden = () -> { fail("must not mutate world"); return true; };
        assertEquals(0, MoldGrowth.attempt(127, 1000, 128, 0, 1, 0, forbidden));
        assertEquals(0, MoldGrowth.attempt(749, 1000, 128, .75, 1, 0, forbidden));
        assertEquals(0, MoldGrowth.attempt(1000, 1000, 128, .75, .02, .02, forbidden));
        assertEquals(0, MoldGrowth.attempt(1000, 0, 128, 0, 1, 0, forbidden));
    }
    @Test void failedPlacementOrConversionDoesNotChargeAndSuccessChargesExactlyOnce() {
        assertEquals(0, MoldGrowth.attempt(1000, 1000, 128, .75, 1, 0, () -> false));
        int[] calls = {0};
        int paid = MoldGrowth.attempt(256, 256, 256, .75, 1, 0, () -> { calls[0]++; return true; });
        assertEquals(256, paid);
        assertEquals(1, calls[0]);
        assertEquals(0, MoldGrowth.attempt(256 - paid, 256, 256, .75, 1, 0,
            () -> { fail("no double charge"); return true; }));
    }
}
