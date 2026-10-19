package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-439 review fix: covers the production entry point's entity-query boundary, target
 * filtering/damage behavior, material charge, and feedback suppression through
 * {@link ExhaustGameplay#runTurn}/{@link ExhaustGameplay#processEligible}, the
 * ServerLevel-independent seams {@code processTurn} delegates to -- there is no in-repo harness
 * for a real ServerLevel/LivingEntity (see {@code ForgeAtmospherePrototypeFreezeGateTest}), so
 * this proves the real decision/damage logic one layer below the ServerLevel-taking wrapper,
 * which is a thin, untested pass-through.
 */
class ExhaustGameplayTest {
    private static final DynamicAtmosphereServerConfig.Exhaust CONFIG =
        new DynamicAtmosphereServerConfig.Exhaust(1, 128, 16, 4, 64, 0.5, 1.0, 1.0, 4.0, 0.25, 0.5, 0);
    private static final BlockPos SOURCE = new BlockPos(0, 0, 0);

    private static final class FakeTarget {
        final UUID uuid;
        final boolean alive;
        final boolean sameLevel;
        final BlockPos eyeBlockPos;
        final boolean hurtResult;
        boolean hurtCalled;
        Boolean guardedDuringHurt;

        FakeTarget(UUID uuid, boolean alive, boolean sameLevel, BlockPos eyeBlockPos, boolean hurtResult) {
            this.uuid = uuid;
            this.alive = alive;
            this.sameLevel = sameLevel;
            this.eyeBlockPos = eyeBlockPos;
            this.hurtResult = hurtResult;
        }
    }

    private static int process(float damage, Iterable<? extends FakeTarget> targets) {
        return ExhaustGameplay.processEligible(damage, SOURCE, targets,
            t -> t.uuid, t -> t.alive, t -> t.sameLevel, t -> t.eyeBlockPos,
            (t, dmg) -> {
                t.hurtCalled = true;
                // Observes the REAL ExhaustGameplay.EXHAUST_DAMAGE guard while hurt() is in
                // flight -- this must be true here regardless of what the fake does, proving the
                // guard itself (not a hard-coded stand-in) is what's exercised.
                t.guardedDuringHurt = ExhaustGameplay.isGuardedForTesting(t.uuid);
                return t.hurtResult;
            });
    }

    @Test
    void inactiveCellNeverInvokesTheTargetsSupplierOrConsume() {
        var supplierCalls = new int[1];
        var consumeCalls = new int[1];
        int damaged = ExhaustGameplay.runTurn(Optional.of(new AtmosphereMaterialState(0, 1000)), CONFIG, SOURCE,
            () -> { supplierCalls[0]++; return List.<FakeTarget>of(); },
            t -> t.uuid, t -> t.alive, t -> t.sameLevel, t -> t.eyeBlockPos, (t, dmg) -> t.hurtResult,
            cost -> { consumeCalls[0]++; return true; });
        assertEquals(0, damaged);
        assertEquals(0, supplierCalls[0], "an ineligible cell must never issue the entity query");
        assertEquals(0, consumeCalls[0]);

        damaged = ExhaustGameplay.runTurn(Optional.empty(), CONFIG, SOURCE,
            () -> { supplierCalls[0]++; return List.<FakeTarget>of(); },
            t -> t.uuid, t -> t.alive, t -> t.sameLevel, t -> t.eyeBlockPos, (t, dmg) -> t.hurtResult,
            cost -> { consumeCalls[0]++; return true; });
        assertEquals(0, damaged);
        assertEquals(0, supplierCalls[0]);
    }

    @Test
    void activeCellInvokesTheTargetsSupplierExactlyOnce() {
        var supplierCalls = new int[1];
        ExhaustGameplay.runTurn(Optional.of(new AtmosphereMaterialState(1000, 1000)), CONFIG, SOURCE,
            () -> { supplierCalls[0]++; return List.<FakeTarget>of(); },
            t -> t.uuid, t -> t.alive, t -> t.sameLevel, t -> t.eyeBlockPos, (t, dmg) -> t.hurtResult,
            cost -> true);
        assertEquals(1, supplierCalls[0]);
    }

    @Test
    void retainsAliveSameLevelSameCellFilteringAndDedupesByUuid() {
        var sameUuid = UUID.randomUUID();
        var duplicate = new FakeTarget(sameUuid, true, true, SOURCE, true);
        var sameUuidAgain = new FakeTarget(sameUuid, true, true, SOURCE, true);
        var dead = new FakeTarget(UUID.randomUUID(), false, true, SOURCE, true);
        var crossLevel = new FakeTarget(UUID.randomUUID(), true, false, SOURCE, true);
        var wrongCell = new FakeTarget(UUID.randomUUID(), true, true, new BlockPos(100, 0, 0), true);
        var eligible = new FakeTarget(UUID.randomUUID(), true, true, SOURCE, true);

        int damaged = process(2.5f, List.of(duplicate, sameUuidAgain, dead, crossLevel, wrongCell, eligible));

        assertEquals(2, damaged, "the duplicate UUID and the one genuinely eligible target are each damaged once");
        assertTrue(duplicate.hurtCalled);
        assertFalse(sameUuidAgain.hurtCalled, "a repeated UUID must not be damaged twice");
        assertFalse(dead.hurtCalled);
        assertFalse(crossLevel.hurtCalled);
        assertFalse(wrongCell.hurtCalled);
        assertTrue(eligible.hurtCalled);
    }

    @Test
    void rejectedDamageIsNotCountedButAcceptedDamageIs() {
        var rejected = new FakeTarget(UUID.randomUUID(), true, true, SOURCE, false);
        var accepted = new FakeTarget(UUID.randomUUID(), true, true, SOURCE, true);
        int damaged = process(1f, List.of(rejected, accepted));
        assertEquals(1, damaged, "only the accepted hurt() result is counted as damaged");
    }

    @Test
    void exhaustDamageFeedbackGuardIsSetDuringHurtAndClearedAfter() {
        var target = new FakeTarget(UUID.randomUUID(), true, true, SOURCE, true);
        assertFalse(ExhaustGameplay.isGuardedForTesting(target.uuid), "nothing is guarded before processing starts");

        process(1f, List.of(target));

        assertTrue(target.hurtCalled);
        assertEquals(Boolean.TRUE, target.guardedDuringHurt,
            "the real EXHAUST_DAMAGE guard must be set for this UUID while hurt() is in flight");
        assertFalse(ExhaustGameplay.isGuardedForTesting(target.uuid), "the guard must be cleared once hurt() returns");
    }

    @Test
    void consumeIsCalledOnceWithTheComputedCostOnlyWhenSomethingWasDamagedAndARefusalGrantsNoDamage() {
        var target = new FakeTarget(UUID.randomUUID(), true, true, SOURCE, true);
        var consumeCalls = new ArrayList<Integer>();
        int damaged = ExhaustGameplay.runTurn(Optional.of(new AtmosphereMaterialState(1000, 1000)), CONFIG, SOURCE,
            () -> List.of(target), t -> t.uuid, t -> t.alive, t -> t.sameLevel, t -> t.eyeBlockPos,
            (t, dmg) -> t.hurtResult, cost -> { consumeCalls.add(cost); return true; });
        assertEquals(1, damaged);
        assertEquals(List.of(500), consumeCalls, "exactly one charge, for the computed suffocation cost");

        var secondTarget = new FakeTarget(UUID.randomUUID(), true, true, SOURCE, true);
        int refused = ExhaustGameplay.runTurn(Optional.of(new AtmosphereMaterialState(1000, 1000)), CONFIG, SOURCE,
            () -> List.of(secondTarget), t -> t.uuid, t -> t.alive, t -> t.sameLevel, t -> t.eyeBlockPos,
            (t, dmg) -> t.hurtResult, cost -> false);
        assertEquals(0, refused, "a refused charge must not report any damage despite a successful hurt()");
    }

    @Test
    void noDamageMeansConsumeIsNeverCalled() {
        var consumeCalls = new int[1];
        var deadOnly = new FakeTarget(UUID.randomUUID(), false, true, SOURCE, true);
        int damaged = ExhaustGameplay.runTurn(Optional.of(new AtmosphereMaterialState(1000, 1000)), CONFIG, SOURCE,
            () -> List.of(deadOnly), t -> t.uuid, t -> t.alive, t -> t.sameLevel, t -> t.eyeBlockPos,
            (t, dmg) -> t.hurtResult, cost -> { consumeCalls[0]++; return true; });
        assertEquals(0, damaged);
        assertEquals(0, consumeCalls[0]);
    }

    @Test
    void creepersProduceFrequentlyAndOtherLivingMobsRandomly() {
        assertEquals(1, ExhaustGameplay.passiveEmission(false, 0));
        assertEquals(0, ExhaustGameplay.passiveEmission(false, 1.0 / 128.0));
        assertEquals(1, ExhaustGameplay.passiveEmission(true, 1.0 / 16.0 - 0.000001));
        assertEquals(0, ExhaustGameplay.passiveEmission(true, 1.0 / 16.0));
        assertEquals(0, ExhaustGameplay.passiveEmission(true, Double.NaN));
    }

    @Test
    void successfulDamageProducesBoundedExhaustButSelfDamageDoesNot() {
        assertEquals(0, ExhaustGameplay.damageEmission(0, false));
        assertEquals(4, ExhaustGameplay.damageEmission(1, false));
        assertEquals(5, ExhaustGameplay.damageEmission(1.01f, false));
        assertEquals(64, ExhaustGameplay.damageEmission(100, false));
        assertEquals(0, ExhaustGameplay.damageEmission(4, true));
        assertEquals(0, ExhaustGameplay.damageEmission(Float.NaN, false));
    }

    @Test
    void suffocationScalesFromHalfFullAndClampsBeyondCapacity() {
        assertFalse(ExhaustGameplay.suffocation(499, 1000).active());
        assertEquals(new ExhaustGameplay.Suffocation(1, 125),
            ExhaustGameplay.suffocation(500, 1000));
        assertEquals(new ExhaustGameplay.Suffocation(2.5f, 282),
            ExhaustGameplay.suffocation(750, 1000));
        assertEquals(new ExhaustGameplay.Suffocation(4, 500),
            ExhaustGameplay.suffocation(1000, 1000));
        assertEquals(new ExhaustGameplay.Suffocation(4, 1000),
            ExhaustGameplay.suffocation(2000, 1000));
        assertFalse(ExhaustGameplay.suffocation(1000, 0).active());
    }

    @Test
    void exposureUsesOnlyTheSharedFourBlockExhaustCell() {
        assertTrue(ExhaustGameplay.sameExhaustCell(new BlockPos(0, 0, 0), new BlockPos(3, 3, 3)));
        assertFalse(ExhaustGameplay.sameExhaustCell(new BlockPos(0, 0, 0), new BlockPos(4, 1, 1)));
        assertTrue(ExhaustGameplay.sameExhaustCell(new BlockPos(-1, -1, -1), new BlockPos(-4, -4, -4)));
        assertFalse(ExhaustGameplay.sameExhaustCell(new BlockPos(-1, -1, -1), new BlockPos(-5, -2, -2)));
    }
}
