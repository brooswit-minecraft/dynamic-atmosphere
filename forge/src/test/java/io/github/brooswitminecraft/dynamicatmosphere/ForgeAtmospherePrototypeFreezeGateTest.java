package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * requires a fully bootstrapped {@code MinecraftServer}. Passing {@code null} as the server
 * proves the gate precisely: when frozen, the dispatch body (and the {@code serverTicks}
 * counter) must never be reached -- not "reached but a no-op" -- so it may never dereference the
 * server, and a regression that advances physics during freeze fails with an immediate
 * {@code NullPointerException} instead of silently passing. The inverse call proves the gate
 * does not also swallow real ticks: once not frozen, the real dispatch body must run and
 * therefore must dereference the (here, null) server.
 */
class ForgeAtmospherePrototypeFreezeGateTest {

    @Test
    void frozenTickTouchesNothingAndDoesNotAdvanceTheCounter() {
        var prototype = new ForgeAtmospherePrototype();

        // Repeated "frozen ticks" with real wall-clock delay between them, exactly like many
        // real ServerTickEvent.Post firings while /tick freeze is active, must never dereference
        // the server (here null) and must never advance serverTicks.
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> prototype.tick(null, false),
                "a frozen tick must short-circuit before touching the server");
            sleepQuietly(20);
        }
    }

    @Test
    void nonFrozenTickActuallyEngagesTheProductionDispatch() {
        var prototype = new ForgeAtmospherePrototype();

        // The inverse of the above: once runsNormally() is true, the real dispatch body must
        // run (and therefore dereference the server) -- proving the gate is an early-return on
        // "frozen", not a no-op that happens to also skip normal ticks.
        assertThrows(NullPointerException.class, () -> prototype.tick(null, true),
            "a non-frozen tick must engage the real dispatch body, which uses the server");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
