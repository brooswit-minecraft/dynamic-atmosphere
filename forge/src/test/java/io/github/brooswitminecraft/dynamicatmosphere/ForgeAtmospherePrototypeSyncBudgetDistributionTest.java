package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-436 review (first round): "production gives the full allowance independently to
 * atmosphere, smoke and each of nine materials, allowing 11 times the named per-player cap in one
 * sync tick. ... Split/plan continuation at the available operation allowance and account for a
 * shared per-player budget with fair channel scheduling." Coverage for {@link
 * ForgeAtmospherePrototype#distributeBudget}, the extracted, player-independent allocator that
 * now shares one total per-player budget across every sync channel each tick, so these channels
 * can be exercised with simple fake channels at the minimum budget and with several active
 * channels -- no {@code ServerPlayer}/{@code MinecraftServer} harness needed.
 */
class ForgeAtmospherePrototypeSyncBudgetDistributionTest {

    /** A fake channel that reports wanting (and consuming) exactly {@code demand} ops, capped by what it's offered. */
    private static ForgeAtmospherePrototype.BudgetedChannel wanting(int demand, List<Integer> budgetsOffered) {
        return budget -> {
            budgetsOffered.add(budget);
            return Math.min(demand, budget);
        };
    }

    @Test
    void totalConsumptionAcrossManyHungryChannelsNeverExceedsTheConfiguredCap() {
        var offered = new ArrayList<Integer>();
        var channels = List.of(
            wanting(1_000, offered), wanting(1_000, offered), wanting(1_000, offered),
            wanting(1_000, offered), wanting(1_000, offered), wanting(1_000, offered),
            wanting(1_000, offered), wanting(1_000, offered), wanting(1_000, offered),
            wanting(1_000, offered), wanting(1_000, offered)); // 11 channels, each wants far more than the cap

        int totalBudget = 4_096;
        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, totalBudget, 0);

        assertEquals(totalBudget, consumed,
            "with channels each hungry for more than the whole budget, total consumption must exactly "
                + "reach -- and never exceed -- the configured per-player cap, not some multiple of it");
    }

    @Test
    void atTheMinimumBudgetOnlyOneOperationIsEverConsumedAcrossAllChannels() {
        var offered = new ArrayList<Integer>();
        var channels = List.of(
            wanting(512, offered), wanting(512, offered), wanting(512, offered), wanting(512, offered),
            wanting(512, offered), wanting(512, offered), wanting(512, offered), wanting(512, offered),
            wanting(512, offered), wanting(512, offered), wanting(512, offered));

        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, 1, 0);

        assertEquals(1, consumed, "the minimum supported budget (1) must still cap total consumption at 1, "
            + "even with 11 channels each individually capable of consuming hundreds of operations");
    }

    @Test
    void eachChannelIsOfferedOnlyWhatRemainsAfterEarlierChannelsInRotationOrder() {
        var offered = new ArrayList<Integer>();
        var channels = List.of(wanting(3, offered), wanting(3, offered), wanting(3, offered));

        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, 7, 0);

        assertEquals(List.of(7, 4, 1), offered,
            "each channel must be offered exactly what remains of the shared pool after earlier "
                + "channels (in rotation order) already consumed their share: 7, then 7-3=4, then 4-3=1");
        assertEquals(7, consumed, "3+3+1 (the third channel is capped by only 1 remaining) must total 7");
    }

    @Test
    void rotationChangesWhichChannelIsOfferedTheFullBudgetFirst() {
        var offeredAtRotationZero = new ArrayList<Integer>();
        var channelsAtRotationZero = List.of(
            wanting(2, offeredAtRotationZero), wanting(2, offeredAtRotationZero), wanting(2, offeredAtRotationZero));
        ForgeAtmospherePrototype.distributeBudget(channelsAtRotationZero, 5, 0);

        var offeredAtRotationOne = new ArrayList<Integer>();
        var channelsAtRotationOne = List.of(
            wanting(2, offeredAtRotationOne), wanting(2, offeredAtRotationOne), wanting(2, offeredAtRotationOne));
        ForgeAtmospherePrototype.distributeBudget(channelsAtRotationOne, 5, 1);

        assertTrue(!offeredAtRotationZero.isEmpty() && !offeredAtRotationOne.isEmpty());
        assertEquals(5, offeredAtRotationZero.get(0), "at rotation 0, channel 0 is served first with the full pool");
        assertEquals(5, offeredAtRotationOne.get(0), "at rotation 1, a DIFFERENT channel (index 1) is instead "
            + "served first with the full pool -- proving which channel goes first actually rotates");
    }

    @Test
    void aChannelThatNeedsNothingLeavesTheFullBudgetForTheNextChannel() {
        var offered = new ArrayList<Integer>();
        ForgeAtmospherePrototype.BudgetedChannel idleChannel = budget -> {
            offered.add(budget);
            return 0;
        };
        var channels = List.of(idleChannel, wanting(2, offered));

        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, 5, 0);

        assertEquals(2, consumed, "an idle channel consuming 0 must not reduce what the next channel is offered");
    }

    /**
     * MINECRAFT-436 review (second round): "periodic resync requests are lost when the channel is
     * busy or skipped because earlier channels exhaust the budget. ... Retain a pending due
     * request/deadline until that channel can plan the resync after its current generation
     * finishes." These tests reproduce the exact composition {@code ForgeAtmospherePrototype}
     * uses -- a per-channel {@code Set<UUID>} latch, set unconditionally when a player is due
     * (mirroring {@code syncAllPlayers}' unconditional marking BEFORE budget allocation) and
     * consumed only once that specific channel actually applies the deferred reset -- proving a
     * due-ness signal survives both a busy channel and a channel {@code distributeBudget} never
     * even reaches this tick.
     */
    @Test
    void aPendingDueLatchSurvivesABusyChannelAndFiresOnceItGoesIdle() {
        var player = UUID.randomUUID();
        var pending = new HashSet<UUID>();
        int[] busyTicksRemaining = {3};
        var appliedOnTick = new ArrayList<Boolean>();

        ForgeAtmospherePrototype.BudgetedChannel channel = budget -> {
            boolean idle = busyTicksRemaining[0] <= 0;
            if (idle) {
                appliedOnTick.add(pending.remove(player));
            } else {
                busyTicksRemaining[0]--;
                appliedOnTick.add(false);
            }
            return 1;
        };

        // The due pulse is latched unconditionally, exactly once, exactly like syncAllPlayers
        // marking every channel's pending set before any channel is even invoked this tick.
        pending.add(player);

        ForgeAtmospherePrototype.distributeBudget(List.of(channel), 10, 0);
        assertTrue(pending.contains(player), "still busy on tick 1 -- the latch must not be consumed yet");

        for (int i = 0; i < 2; i++) {
            ForgeAtmospherePrototype.distributeBudget(List.of(channel), 10, 0);
        }
        assertTrue(pending.contains(player),
            "the latch must survive across every busy tick, not just the first one after it was set");

        ForgeAtmospherePrototype.distributeBudget(List.of(channel), 10, 0);
        assertFalse(pending.contains(player), "once idle, the channel must consume the latch and apply the reset");
        assertEquals(List.of(false, false, false, true), appliedOnTick,
            "the reset must be applied on exactly the tick the channel first goes idle, not before");
    }

    @Test
    void aPendingDueLatchSurvivesNeverBeingReachedWhenEarlierChannelsExhaustTheWholeBudget() {
        var player = UUID.randomUUID();
        var pending = new HashSet<UUID>();
        pending.add(player);

        ForgeAtmospherePrototype.BudgetedChannel hungryChannel = budget -> budget; // always consumes everything offered
        var secondChannelInvocations = new ArrayList<Boolean>();
        ForgeAtmospherePrototype.BudgetedChannel secondChannel = budget -> {
            secondChannelInvocations.add(true);
            pending.remove(player);
            return 0;
        };

        // Tick where the hungry first channel exhausts the whole shared budget: distributeBudget's
        // own loop guard (remaining > 0) means the second channel is never even invoked this tick.
        ForgeAtmospherePrototype.distributeBudget(List.of(hungryChannel, secondChannel), 10, 0);
        assertTrue(secondChannelInvocations.isEmpty(),
            "the second channel must never be invoked at all when the first exhausts the whole budget");
        assertTrue(pending.contains(player),
            "the pending latch must survive a tick where this channel was skipped entirely, not just a busy one");

        // A later tick where the first channel has nothing left to consume (e.g. it caught up),
        // finally reaching the second channel, which must still apply the deferred reset.
        ForgeAtmospherePrototype.BudgetedChannel caughtUpChannel = budget -> 0;
        ForgeAtmospherePrototype.distributeBudget(List.of(caughtUpChannel, secondChannel), 10, 0);
        assertFalse(pending.contains(player),
            "once finally reached, the channel must still apply the periodic reset that was deferred earlier");
    }
}
