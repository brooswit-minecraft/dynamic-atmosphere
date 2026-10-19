package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-439 review fix: covers the production bounded-villager entry point through
 * {@link VoidGasGameplay#readyOneVillagerVia} -- the exact shared orchestration
 * {@code readyOneVillager} calls in production -- with
 * {@link VoidGasGameplay#boundedMatchesForTesting} wired in as the injected bounded-query
 * implementation. There is no in-repo harness for a real ServerLevel/Villager (see
 * {@code ForgeAtmospherePrototypeFreezeGateTest}), so this proves the real
 * bounded-query/first-candidate-wins/charge-once logic through the one necessarily-injected
 * seam (how to fetch candidates from the world), not a disconnected parallel implementation.
 */
class VoidGasGameplayTest {
    private static final class FakeCandidate {
        final boolean eligible;
        int breadReceived;

        FakeCandidate(boolean eligible) {
            this.eligible = eligible;
        }
    }

    private static boolean ready(int cost, IntPredicate consume, List<FakeCandidate> candidates) {
        return VoidGasGameplay.readyFirstEligible(cost, consume, candidates, c -> c.breadReceived++);
    }

    @Test
    void noCandidatesNeverConsumesAndReportsFailure() {
        var consumeCalls = new int[1];
        boolean readied = ready(5, cost -> { consumeCalls[0]++; return true; }, List.of());
        assertFalse(readied);
        assertEquals(0, consumeCalls[0]);
    }

    @Test
    void onlyTheFirstCandidateIsEverTriedEvenWithMultipleEligibleOnes() {
        var first = new FakeCandidate(true);
        var second = new FakeCandidate(true);
        var consumeCalls = new ArrayList<Integer>();
        boolean readied = ready(5, cost -> { consumeCalls.add(cost); return true; }, List.of(first, second));

        assertTrue(readied);
        assertEquals(List.of(5), consumeCalls, "exactly one charge, for the first candidate only");
        assertEquals(1, first.breadReceived, "the first candidate receives bread exactly once");
        assertEquals(0, second.breadReceived, "a second candidate must never be reached");
    }

    @Test
    void aRefusedConsumptionGrantsNoBreadAndTriesNoFurtherCandidate() {
        var first = new FakeCandidate(true);
        var second = new FakeCandidate(true);
        boolean readied = ready(5, cost -> false, List.of(first, second));

        assertFalse(readied);
        assertEquals(0, first.breadReceived, "a failed consumption must not grant bread");
        assertEquals(0, second.breadReceived, "a refused first candidate must not fall through to the next one");
    }

    /**
     * Review fix: exercises {@link VoidGasGameplay#readyOneVillagerVia} directly -- the exact
     * same orchestration {@code readyOneVillager} calls in production -- with
     * {@link VoidGasGameplay#boundedMatchesForTesting} wired as the injected bounded-query
     * implementation. The limit it bounds to is never chosen by this test: it comes from
     * {@code readyOneVillagerVia} passing {@link VoidGasGameplay#VILLAGER_SELECTION_LIMIT}
     * itself, so a regression there (or in the real eligibility predicate production wires in)
     * would surface here, not just in a disconnected mirror. More than
     * {@code VILLAGER_SELECTION_LIMIT} worth of ineligible candidates precede one eligible
     * candidate; the eligible one must still be found, charged once, and rewarded.
     */
    @Test
    void anEligibleCandidateBeyondTheSelectionLimitsWorthOfIneligibleOnesIsStillFound() {
        var candidates = new ArrayList<FakeCandidate>();
        for (int i = 0; i < 200; i++) {
            candidates.add(new FakeCandidate(false));
        }
        var eligible = new FakeCandidate(true);
        candidates.add(eligible);
        assertTrue(200 > VoidGasGameplay.VILLAGER_SELECTION_LIMIT,
            "the regression only means something if more ineligible candidates precede the eligible one than the limit");

        var consumeCalls = new ArrayList<Integer>();
        boolean readied = VoidGasGameplay.<FakeCandidate>readyOneVillagerVia(5,
            cost -> { consumeCalls.add(cost); return true; },
            c -> c.eligible,
            c -> c.breadReceived++,
            (eligible2, limit) -> VoidGasGameplay.boundedMatchesForTesting(candidates, eligible2, limit));

        assertTrue(readied);
        assertEquals(List.of(5), consumeCalls, "exactly one charge");
        assertEquals(1, eligible.breadReceived);
    }

    @Test
    void aRefusedConsumptionThroughTheSharedOrchestrationGrantsNoReward() {
        var eligible = new FakeCandidate(true);
        boolean readied = VoidGasGameplay.<FakeCandidate>readyOneVillagerVia(5,
            cost -> false, c -> c.eligible, c -> c.breadReceived++,
            (eligible2, limit) -> VoidGasGameplay.boundedMatchesForTesting(List.of(eligible), eligible2, limit));

        assertFalse(readied);
        assertEquals(0, eligible.breadReceived, "a failed consumption must not grant bread");
    }

    @Test
    void theLimitStopsCollectingOnceItHasEnoughMatches() {
        var first = new FakeCandidate(true);
        var second = new FakeCandidate(true);
        var neverReached = new FakeCandidate(true);
        List<FakeCandidate> bounded = VoidGasGameplay.boundedMatchesForTesting(
            List.of(first, second, neverReached), c -> c.eligible, 2);
        assertEquals(List.of(first, second), bounded);
    }

    @Test
    void onlyMonsterCategoryDeathsAreHostileProduction() {
        assertTrue(VoidGasGameplay.isHostileCategory(MobCategory.MONSTER));
        assertFalse(VoidGasGameplay.isHostileCategory(MobCategory.CREATURE));
        assertFalse(VoidGasGameplay.isHostileCategory(MobCategory.MISC));
    }

    @Test
    void bottomProducerFiresUnconditionally() {
        assertEquals(1, VoidGasGameplay.BOTTOM_CHANCE_DENOMINATOR);
        assertTrue(VoidGasGameplay.bottomEmission(0));
    }

    @Test
    void tenThroughTwentyFivePercentReadiesVillagerForFivePercentCurrentCost() {
        var atTen = VoidGasGameplay.effectFor(100, 1_000);
        var atTwentyFive = VoidGasGameplay.effectFor(250, 1_000);
        assertTrue(atTen.villagerBand());
        assertEquals(5, atTen.cost());
        assertTrue(atTwentyFive.villagerBand());
        assertEquals(13, atTwentyFive.cost());
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(99, 1_000));
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(251, 1_000));
    }

    @Test
    void highFullnessNoLongerProducesAnEffect() {
        // MINECRAFT-17: the consumption-driven zombie spawn above 75% fullness is removed.
        // A full cell now only affects VaporHostileSpawnGate surface-spawn eligibility.
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(750, 1_000));
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(1_000, 1_000));
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(100, 0));
    }
}
