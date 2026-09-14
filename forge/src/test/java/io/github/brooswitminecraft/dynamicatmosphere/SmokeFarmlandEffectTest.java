package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeFarmlandEffectTest {
    @Test
    void insufficientMaterialDoesNotRollOrInspectBlocks() {
        assertEquals(0, SmokeFarmlandEffect.attempt(39,
            () -> { fail("No roll without full cost"); return 0; },
            index -> { fail("No scan without full cost"); return true; },
            index -> { fail("No conversion without full cost"); return true; }));
    }

    @Test
    void oneIn128GatePrecedesScanning() {
        for (double roll : new double[]{1.0 / 128, 0.1, 1, Double.NaN, -0.1}) {
            assertEquals(0, SmokeFarmlandEffect.attempt(40, () -> roll,
                index -> { fail("Failed roll must not scan"); return true; },
                index -> { fail("Failed roll must not convert"); return true; }));
        }
        assertEquals(40, SmokeFarmlandEffect.attempt(40, () -> Math.nextDown(1.0 / 128),
            index -> true, index -> true));
    }

    @Test
    void convertsOneBlockAndChargesOnlyOnSuccess() {
        var mutations = new AtomicInteger();
        assertEquals(40, SmokeFarmlandEffect.attempt(1000, () -> 0, index -> index >= 5,
            index -> { assertEquals(5, index); mutations.incrementAndGet(); return true; }));
        assertEquals(1, mutations.get());
        assertEquals(0, SmokeFarmlandEffect.attempt(40, () -> 0, index -> true,
            index -> { mutations.incrementAndGet(); return false; }));
        assertEquals(2, mutations.get());
    }

    @Test
    void noFarmlandScansAtMost512BlocksAndConsumesNothing() {
        var scans = new AtomicInteger();
        assertEquals(0, SmokeFarmlandEffect.attempt(40, () -> 0,
            index -> { assertEquals(scans.getAndIncrement(), index); return false; },
            index -> { fail("No farmland"); return true; }));
        assertEquals(512, scans.get());
    }

    @Test
    void independentLeafAndFarmlandRollsShareOnlyRemainingMaterial() {
        int available = 80;
        available -= SmokeLeafEffect.attempt(available, () -> 0, index -> true, index -> true);
        available -= SmokeFarmlandEffect.attempt(available, () -> 0, index -> true, index -> true);
        assertEquals(0, available);

        int afterFailedLeaf = 40 - SmokeLeafEffect.attempt(40, () -> 0.5, index -> true, index -> true);
        assertEquals(40, SmokeFarmlandEffect.attempt(afterFailedLeaf, () -> 0, index -> true, index -> true));

        int afterSuccessfulLeaf = 40 - SmokeLeafEffect.attempt(40, () -> 0, index -> true, index -> true);
        assertEquals(0, SmokeFarmlandEffect.attempt(afterSuccessfulLeaf,
            () -> { fail("Cannot spend the same material twice"); return 0; }, index -> true, index -> true));
    }
}
