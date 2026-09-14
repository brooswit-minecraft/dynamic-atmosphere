package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeLeafEffectTest {
    @Test
    void insufficientSmokeDoesNotRollScanOrMutate() {
        assertEquals(0, SmokeLeafEffect.attempt(39,
            () -> { fail("No roll without full cost"); return 0; },
            index -> { fail("No scan without full cost"); return true; },
            index -> { fail("No removal without full cost"); return true; }));
    }

    @Test
    void chanceGatePrecedesAllBlockAccess() {
        for (double roll : new double[]{0.1, 0.9, 1, Double.NaN, -0.1}) {
            assertEquals(0, SmokeLeafEffect.attempt(40, () -> roll,
                index -> { fail("Failed roll must not scan"); return true; },
                index -> { fail("Failed roll must not mutate"); return true; }));
        }
    }

    @Test
    void successfulRemovalConsumesExactlyFortyAndStopsAtOneLeaf() {
        var scans = new AtomicInteger();
        var mutations = new AtomicInteger();
        assertEquals(40, SmokeLeafEffect.attempt(1000, () -> 0.099999,
            index -> { scans.incrementAndGet(); return index >= 7; },
            index -> { assertEquals(7, index); mutations.incrementAndGet(); return true; }));
        assertEquals(8, scans.get());
        assertEquals(1, mutations.get());
    }

    @Test
    void failedOrNoLongerLeafMutationConsumesNothingAndDoesNotTryAnother() {
        var mutations = new AtomicInteger();
        assertEquals(0, SmokeLeafEffect.attempt(40, () -> 0, index -> true,
            index -> { mutations.incrementAndGet(); return false; }));
        assertEquals(1, mutations.get());
    }

    @Test
    void emptyOrUnavailableCellNeverConsumesAndScanCannotEscapeEightCube() {
        var scans = new AtomicInteger();
        assertEquals(0, SmokeLeafEffect.attempt(40, () -> 0,
            index -> {
                assertEquals(scans.getAndIncrement(), index);
                assertTrue(index >= 0 && index < 512);
                return false;
            }, index -> { fail("No leaf available"); return true; }));
        assertEquals(512, scans.get());
    }
}
