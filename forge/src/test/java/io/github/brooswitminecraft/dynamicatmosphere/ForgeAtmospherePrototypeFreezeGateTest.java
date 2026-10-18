package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MINECRAFT-384: regression coverage for a real production defect a reviewer established --
 * {@code ForgeAtmospherePrototype.onServerTick} previously advanced the atmosphere simulation on
 * every {@code ServerTickEvent.Post}, which NeoForge's {@code MinecraftServer.tickServer} fires
 * unconditionally every real server tick, including while
 * {@code server.tickRateManager().runsNormally()} is {@code false} (platform {@code /tick
 * freeze}, which still runs the server loop for control/network work). That let atmosphere
 * transport/decay/reaction keep advancing purely because wall-clock ticks kept arriving even
 * though the simulated world was frozen.
 *
 * <p>These tests drive {@link ForgeAtmospherePrototype#tick}, the exact method
 * {@code onServerTick} delegates to in production, with an explicit {@code runsNormally} flag --
 * the one thing a live {@code ServerTickEvent.Post} cannot be constructed with here, since it
 * requires a fully bootstrapped {@code MinecraftServer}. There is no in-repo harness for a real
 * {@code MinecraftServer}/{@code ServerLevel}, so a non-frozen call is only proven to engage the
 * real dispatch body by observing that it reaches the point of dereferencing the (here, null)
 * server and throws {@code NullPointerException} -- a disclosed limit, not end-to-end gameplay
 * coverage. What IS newly proven here, directly, is the thing the prior revision's reviewer
 * flagged as unobserved: the production-used {@code serverTicks} counter itself, driven through
 * one instance across a realistic normal/frozen/step/frozen/resume sequence, via
 * {@link ForgeAtmospherePrototype#serverTicksForTesting()}.
 */
class ForgeAtmospherePrototypeFreezeGateTest {

    @Test
    void frozenCallsNeverAdvanceTheCounterOrTouchTheServer() {
        var prototype = new ForgeAtmospherePrototype();
        assertEquals(0, prototype.serverTicksForTesting());

        // Repeated "frozen ticks" with real wall-clock delay between them, exactly like many
        // real ServerTickEvent.Post firings while /tick freeze is active: each must short-circuit
        // before touching the server (here null; a regression would NPE) or the counter.
        for (int i = 0; i < 3; i++) {
            prototype.tick(null, false);
            sleepQuietly(20);
        }
        assertEquals(0, prototype.serverTicksForTesting(),
            "three frozen calls, however much real time elapsed between them, must advance the counter by zero");
    }

    @Test
    void acceptedCallAdvancesTheCounterByExactlyOneAndEngagesRealDispatch() {
        var prototype = new ForgeAtmospherePrototype();

        // runsNormally() is true for a normal tick, a sprinting tick, and a single stepped tick
        // while otherwise frozen -- production code (and this test) treats all three identically.
        assertThrows(NullPointerException.class, () -> prototype.tick(null, true),
            "an accepted tick must engage the real dispatch body, which dereferences the server");
        assertEquals(1, prototype.serverTicksForTesting(),
            "the counter must have advanced before the dispatch body (and its NPE) was reached");
    }

    @Test
    void normalThenRepeatedFrozenThenStepThenFrozenThenResumeAdvancesOnlyByAcceptedCallsNoCatchUp() {
        var prototype = new ForgeAtmospherePrototype();
        assertEquals(0, prototype.serverTicksForTesting());

        // 1. A normal, accepted tick: +1.
        assertThrows(NullPointerException.class, () -> prototype.tick(null, true));
        assertEquals(1, prototype.serverTicksForTesting(), "one accepted tick must read back as exactly one");

        // 2. Repeated frozen ticks, with real wall-clock delay between them: +0 each, no matter
        // how long real time elapses or how many frozen calls arrive.
        for (int i = 0; i < 3; i++) {
            prototype.tick(null, false);
            sleepQuietly(15);
        }
        assertEquals(1, prototype.serverTicksForTesting(),
            "repeated frozen calls across real wall-clock delay must leave the counter unchanged");

        // 3. A single stepped tick while otherwise frozen is represented identically to any other
        // accepted tick in production (runsNormally() alone gates dispatch, with no separate
        // "step" signal): +1.
        assertThrows(NullPointerException.class, () -> prototype.tick(null, true));
        assertEquals(2, prototype.serverTicksForTesting(), "the single stepped tick must advance the counter by one");

        // 4. Frozen again, with a longer real-time gap simulating another pause: +0.
        for (int i = 0; i < 2; i++) {
            prototype.tick(null, false);
            sleepQuietly(50);
        }
        assertEquals(2, prototype.serverTicksForTesting(), "the second frozen stretch must also advance the counter by zero");

        // 5. Resume: exactly one more accepted tick must advance the counter by exactly one --
        // never by the elapsed real time or by the number of frozen calls that were skipped
        // (a wall-time-driven catch-up burst), which is precisely the regression this test
        // exists to catch (see class doc and the manual mutation-testing verification in the
        // accompanying PR description/commit message).
        assertThrows(NullPointerException.class, () -> prototype.tick(null, true));
        assertEquals(3, prototype.serverTicksForTesting(),
            "resuming after frozen stretches must advance the counter by exactly one accepted tick, no catch-up");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
