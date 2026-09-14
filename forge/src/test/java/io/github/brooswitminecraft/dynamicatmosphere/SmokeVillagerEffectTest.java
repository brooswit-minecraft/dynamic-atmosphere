package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeVillagerEffectTest {
    @Test
    void costAndChanceGatesPrecedeEntityQuery() {
        assertEquals(0, SmokeVillagerEffect.<String>attempt(39,
            () -> { fail("Insufficient Smoke must not roll"); return 0; },
            () -> { fail("Must not query"); return List.<String>of().iterator(); }, v -> true, v -> true));
        for (double roll : new double[]{1.0 / 256, 0.5, Double.NaN, -1}) {
            assertEquals(0, SmokeVillagerEffect.<String>attempt(40, () -> roll,
                () -> { fail("Failed gate must not query"); return List.<String>of().iterator(); },
                v -> true, v -> true));
        }
    }

    @Test
    void skipsNitwitsAndConvertsOnlyOneCandidate() {
        var calls = new AtomicInteger();
        assertEquals(40, SmokeVillagerEffect.attempt(80, () -> Math.nextDown(1.0 / 256),
            () -> List.of("nitwit", "farmer", "librarian").iterator(),
            v -> !v.equals("nitwit"), v -> {
                assertEquals("farmer", v);
                calls.incrementAndGet();
                return true;
            }));
        assertEquals(1, calls.get());
    }

    @Test
    void emptyAlreadyNitwitAndFailedConversionDoNotConsume() {
        assertEquals(0, SmokeVillagerEffect.<String>attempt(40, () -> 0,
            () -> Collections.emptyIterator(), v -> true, v -> true));
        assertEquals(0, SmokeVillagerEffect.attempt(40, () -> 0,
            () -> List.of("nitwit").iterator(), v -> false,
            v -> { fail("Nitwit must not convert"); return true; }));
        assertEquals(0, SmokeVillagerEffect.attempt(40, () -> 0,
            () -> List.of("farmer").iterator(), v -> true, v -> false));
    }

    @Test
    void candidateInspectionIsBoundedEvenForAnOversizedQuery() {
        var checked = new AtomicInteger();
        assertEquals(0, SmokeVillagerEffect.attempt(40, () -> 0,
            () -> Collections.nCopies(1000, "nitwit").iterator(),
            v -> { checked.incrementAndGet(); return false; }, v -> true));
        assertEquals(128, checked.get());
    }
}
