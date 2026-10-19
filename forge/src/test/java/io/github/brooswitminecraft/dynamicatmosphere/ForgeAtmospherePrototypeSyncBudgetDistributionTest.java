package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        return (periodicDue, budget) -> {
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
        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, false, totalBudget, 0);

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

        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, false, 1, 0);

        assertEquals(1, consumed, "the minimum supported budget (1) must still cap total consumption at 1, "
            + "even with 11 channels each individually capable of consuming hundreds of operations");
    }

    @Test
    void eachChannelIsOfferedOnlyWhatRemainsAfterEarlierChannelsInRotationOrder() {
        var offered = new ArrayList<Integer>();
        var channels = List.of(wanting(3, offered), wanting(3, offered), wanting(3, offered));

        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, false, 7, 0);

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
        ForgeAtmospherePrototype.distributeBudget(channelsAtRotationZero, false, 5, 0);

        var offeredAtRotationOne = new ArrayList<Integer>();
        var channelsAtRotationOne = List.of(
            wanting(2, offeredAtRotationOne), wanting(2, offeredAtRotationOne), wanting(2, offeredAtRotationOne));
        ForgeAtmospherePrototype.distributeBudget(channelsAtRotationOne, false, 5, 1);

        assertTrue(!offeredAtRotationZero.isEmpty() && !offeredAtRotationOne.isEmpty());
        assertEquals(5, offeredAtRotationZero.get(0), "at rotation 0, channel 0 is served first with the full pool");
        assertEquals(5, offeredAtRotationOne.get(0), "at rotation 1, a DIFFERENT channel (index 1) is instead "
            + "served first with the full pool -- proving which channel goes first actually rotates");
    }

    @Test
    void aChannelThatNeedsNothingLeavesTheFullBudgetForTheNextChannel() {
        var offered = new ArrayList<Integer>();
        ForgeAtmospherePrototype.BudgetedChannel idleChannel = (due, budget) -> {
            offered.add(budget);
            return 0;
        };
        var channels = List.of(idleChannel, wanting(2, offered));

        int consumed = ForgeAtmospherePrototype.distributeBudget(channels, false, 5, 0);

        assertEquals(2, consumed, "an idle channel consuming 0 must not reduce what the next channel is offered");
    }
}
