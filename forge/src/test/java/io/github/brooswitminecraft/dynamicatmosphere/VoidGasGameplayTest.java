package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-439 review fix: covers the production bounded-villager entry point through
 * {@link VoidGasGameplay#readyFirstEligible}, the ServerLevel-independent seam
 * {@code readyOneVillager} delegates to -- there is no in-repo harness for a real
 * ServerLevel/Villager (see {@code ForgeAtmospherePrototypeFreezeGateTest}), so this proves the
 * real first-candidate-wins/charge-once logic one layer below the ServerLevel-taking query,
 * which is a thin, untested pass-through.
 */
class VoidGasGameplayTest {
    private static final class FakeCandidate implements VoidGasGameplay.VillagerCandidate {
        int breadReceived;

        @Override
        public void receiveBread() { breadReceived++; }
    }

    @Test
    void noCandidatesNeverConsumesAndReportsFailure() {
        var consumeCalls = new int[1];
        boolean readied = VoidGasGameplay.readyFirstEligible(5, cost -> { consumeCalls[0]++; return true; }, List.of());
        assertFalse(readied);
        assertEquals(0, consumeCalls[0]);
    }

    @Test
    void onlyTheFirstCandidateIsEverTriedEvenWithMultipleEligibleOnes() {
        var first = new FakeCandidate();
        var second = new FakeCandidate();
        var consumeCalls = new ArrayList<Integer>();
        boolean readied = VoidGasGameplay.readyFirstEligible(5,
            cost -> { consumeCalls.add(cost); return true; }, List.of(first, second));

        assertTrue(readied);
        assertEquals(List.of(5), consumeCalls, "exactly one charge, for the first candidate only");
        assertEquals(1, first.breadReceived, "the first candidate receives bread exactly once");
        assertEquals(0, second.breadReceived, "a second eligible candidate must never be reached");
    }

    @Test
    void aRefusedConsumptionGrantsNoBreadAndTriesNoFurtherCandidate() {
        var first = new FakeCandidate();
        var second = new FakeCandidate();
        boolean readied = VoidGasGameplay.readyFirstEligible(5, cost -> false, List.of(first, second));

        assertFalse(readied);
        assertEquals(0, first.breadReceived, "a failed consumption must not grant bread");
        assertEquals(0, second.breadReceived, "a refused first candidate must not fall through to the next one");
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
