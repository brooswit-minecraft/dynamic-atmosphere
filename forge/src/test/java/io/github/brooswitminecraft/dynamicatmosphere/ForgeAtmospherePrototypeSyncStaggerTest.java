package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-436: coverage for {@link ForgeAtmospherePrototype#dueForStaggeredFullSnapshot} and
 * {@link ForgeAtmospherePrototype#rotationFor}, the helpers that spread players' periodic full
 * re-snapshots across the configured interval (instead of firing every connected player's reset
 * on the exact same tick, a thundering herd of full snapshots every {@code
 * fullSnapshotIntervalTicks}) and rotate which sync channel is served first each tick (so a
 * channel is never permanently first and therefore never permanently starved by channels ahead of
 * it). Both are package-visible specifically so they can be driven directly here, the same
 * convention {@code ForgeAtmospherePrototypeHeadroomTest} uses for {@code hasExactHeadroom} --
 * there is no in-repo {@code MinecraftServer}/{@code ServerPlayer} harness to drive {@code
 * syncAllPlayers} end-to-end (see {@code ForgeAtmospherePrototypeFreezeGateTest}'s class doc for
 * the same limit).
 *
 * <p>MINECRAFT-436 review (first round): staggering was previously computed against raw {@code
 * serverTicks} modulo {@code fullSnapshotIntervalTicks}, but {@code tick()} only ever calls sync
 * when {@code serverTicks % syncIntervalTicks == 0} -- so with the real defaults (sync 20, full
 * snapshot 200) only 10 of the 200 possible phases were ever reachable, permanently starving
 * periodic resync for most players (e.g. {@code new UUID(0, 1)}, phase 1, never due). Staggering
 * is now computed against {@code callIndex} (the count of sync calls so far) and {@code
 * slotsPerInterval} (sync calls per full-snapshot window), through which every phase is reachable
 * by construction. {@link #eachRealConfiguredDefaultLeavesNoPlayerPermanentlyUnreachable} drives
 * that exact previously-failing case through the actual production sync-tick gate.
 */
class ForgeAtmospherePrototypeSyncStaggerTest {

    @Test
    void eachPlayerIsDueExactlyOnceEverySlotsPerInterval() {
        var player = UUID.randomUUID();
        int slotsPerInterval = 10;
        int dueCount = 0;
        for (long callIndex = 0; callIndex < slotsPerInterval; callIndex++) {
            if (ForgeAtmospherePrototype.dueForStaggeredFullSnapshot(player, callIndex, slotsPerInterval)) dueCount++;
        }
        assertEquals(1, dueCount,
            "a player must be due for exactly one full snapshot per slotsPerInterval consecutive sync calls");
    }

    @Test
    void differentPlayersAreNotAllDueOnTheSameCallIndex() {
        int slotsPerInterval = 10;
        var players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        var dueCallIndices = new HashSet<Long>();
        for (var player : players) {
            for (long callIndex = 0; callIndex < slotsPerInterval; callIndex++) {
                if (ForgeAtmospherePrototype.dueForStaggeredFullSnapshot(player, callIndex, slotsPerInterval)) {
                    dueCallIndices.add(callIndex);
                }
            }
        }

        assertTrue(dueCallIndices.size() > 1,
            "with several distinct players, their due call indices must spread across the window rather "
                + "than every player becoming due on the exact same sync call -- the thundering herd this "
                + "staggering exists to avoid");
    }

    @Test
    void everyPhaseInTheWindowIsReachableRegardlessOfSlotsPerInterval() {
        // Every possible phase must correspond to SOME real UUID, and every phase must actually
        // fire at some callIndex -- i.e. no phase is a dead zone, unlike the raw-tick scheme this
        // replaced where most phases (any not a multiple of syncIntervalTicks) were unreachable.
        for (int slotsPerInterval : new int[] {1, 7, 10, 13}) {
            for (int phase = 0; phase < slotsPerInterval; phase++) {
                boolean reachable = false;
                for (long callIndex = 0; callIndex < slotsPerInterval; callIndex++) {
                    if (Math.floorMod(callIndex, slotsPerInterval) == phase) {
                        reachable = true;
                        break;
                    }
                }
                assertTrue(reachable, "phase " + phase + " of " + slotsPerInterval + " must be reachable");
            }
        }
    }

    /**
     * MINECRAFT-436 review (first round), exact repro: syncIntervalTicks=20,
     * fullSnapshotIntervalTicks=200 (the real defaults) gives slotsPerInterval=10. {@code new
     * UUID(0, 1)} has {@code hashCode() == 1}, so phase 1 -- under the OLD (serverTicks mod
     * fullSnapshotIntervalTicks) scheme this was never reachable because serverTicks only ever
     * takes multiples of 20 at a sync call, and 1 is not a multiple of 20. This test drives the
     * actual production sync-tick gate (serverTicks stepping by syncIntervalTicks, exactly as
     * {@code ForgeAtmospherePrototype.tick} does before calling {@code syncAllPlayers}) and
     * asserts that player is, in fact, eventually due.
     */
    @Test
    void eachRealConfiguredDefaultLeavesNoPlayerPermanentlyUnreachable() {
        var previouslyUnreachablePlayer = new UUID(0, 1);
        int syncIntervalTicks = 20;
        int fullSnapshotIntervalTicks = 200;
        int slotsPerInterval = Math.max(1, fullSnapshotIntervalTicks / syncIntervalTicks);

        boolean everDue = false;
        for (long serverTicks = syncIntervalTicks; serverTicks <= fullSnapshotIntervalTicks * 3L;
                serverTicks += syncIntervalTicks) {
            long callIndex = serverTicks / syncIntervalTicks;
            if (ForgeAtmospherePrototype.dueForStaggeredFullSnapshot(
                    previouslyUnreachablePlayer, callIndex, slotsPerInterval)) {
                everDue = true;
                break;
            }
        }
        assertTrue(everDue, "a player whose phase was previously a dead zone under real configured defaults "
            + "must now become due within a few full-snapshot windows, driven through the actual "
            + "production sync-tick stepping");
    }

    @Test
    void sameUuidAndSlotsPerIntervalAlwaysProducesTheSameDuePhase() {
        var player = UUID.randomUUID();
        int slotsPerInterval = 15;
        for (long cycle = 0; cycle < 3; cycle++) {
            long base = cycle * slotsPerInterval;
            boolean dueAtOffsetZero = ForgeAtmospherePrototype.dueForStaggeredFullSnapshot(player, base, slotsPerInterval);
            boolean dueAtOffsetZeroAgain =
                ForgeAtmospherePrototype.dueForStaggeredFullSnapshot(player, base, slotsPerInterval);
            assertEquals(dueAtOffsetZero, dueAtOffsetZeroAgain,
                "the due phase must be a stable, deterministic function of (player, slotsPerInterval), not "
                    + "randomized per call, so repeated calls agree and the same player keeps the same phase");
        }
    }

    @Test
    void rotationCyclesThroughEveryChannelExactlyOnceEveryChannelCountCalls() {
        int channelCount = 11; // atmosphere + smoke + 9 materials, matching production today
        var seen = new HashSet<Integer>();
        for (long callIndex = 0; callIndex < channelCount; callIndex++) {
            seen.add(ForgeAtmospherePrototype.rotationFor(callIndex, channelCount));
        }
        assertEquals(channelCount, seen.size(),
            "every channel index must be the first-served channel exactly once per channelCount "
                + "consecutive sync ticks, so no channel is permanently first (or permanently starved "
                + "by channels ahead of it)");
    }
}
