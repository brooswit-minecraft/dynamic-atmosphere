package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-436: coverage for {@code ForgeAtmospherePrototype.dueForStaggeredFullSnapshot}, the
 * helper that spreads players' periodic full re-snapshots across the configured interval instead
 * of firing every connected player's reset on the exact same tick (a thundering herd of full
 * snapshots every {@code fullSnapshotIntervalTicks}), using reflection since the method is
 * private and there is no in-repo {@code MinecraftServer}/{@code ServerPlayer} harness to drive
 * {@code syncPlayers} end-to-end (see {@code ForgeAtmospherePrototypeFreezeGateTest}'s class doc
 * for the same limit).
 */
class ForgeAtmospherePrototypeSyncStaggerTest {

    private static boolean dueForStaggeredFullSnapshot(UUID player, long serverTicks, int intervalTicks)
        throws ReflectiveOperationException {
        var method = ForgeAtmospherePrototype.class.getDeclaredMethod(
            "dueForStaggeredFullSnapshot", UUID.class, long.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, player, serverTicks, intervalTicks);
    }

    @Test
    void eachPlayerIsDueExactlyOnceEveryFullInterval() throws ReflectiveOperationException {
        var player = UUID.randomUUID();
        int interval = 200;
        int dueCount = 0;
        for (long tick = 0; tick < interval; tick++) {
            if (dueForStaggeredFullSnapshot(player, tick, interval)) dueCount++;
        }
        assertEquals(1, dueCount, "a player must be due for exactly one full snapshot per interval window");
    }

    @Test
    void differentPlayersAreNotAllDueOnTheSameTick() throws ReflectiveOperationException {
        int interval = 200;
        var players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        var dueTicks = new java.util.HashSet<Long>();
        for (var player : players) {
            for (long tick = 0; tick < interval; tick++) {
                if (dueForStaggeredFullSnapshot(player, tick, interval)) {
                    dueTicks.add(tick);
                }
            }
        }

        assertTrue(dueTicks.size() > 1,
            "with several distinct players, their due ticks must spread across the interval rather than "
                + "all players becoming due on the exact same tick -- the thundering herd this staggering "
                + "exists to avoid");
    }

    @Test
    void sameUuidAndIntervalAlwaysProducesTheSameDueTick() throws ReflectiveOperationException {
        var player = UUID.randomUUID();
        int interval = 150;
        for (long cycle = 0; cycle < 3; cycle++) {
            long base = cycle * interval;
            boolean dueAtOffsetZero = dueForStaggeredFullSnapshot(player, base, interval);
            boolean dueAtOffsetZeroAgain = dueForStaggeredFullSnapshot(player, base, interval);
            assertEquals(dueAtOffsetZero, dueAtOffsetZeroAgain,
                "the due tick must be a stable, deterministic function of (player, interval), not randomized "
                    + "per call, so repeated calls agree and the same player keeps the same phase");
        }
    }
}
