package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-439 review fix: covers the production entry point's entity-query boundary and
 * effect application through {@link NetherGasGameplay#runEligible}, the ServerLevel-independent
 * seam {@code processTurn} delegates to -- there is no in-repo harness for a real
 * ServerLevel/LivingEntity (see {@code ForgeAtmospherePrototypeFreezeGateTest}), so this proves
 * the real decision/entity logic one layer below the ServerLevel-taking wrapper, which is a thin,
 * untested pass-through.
 */
class NetherGasGameplayTest {
    private static final double MILD = 0.10;
    private static final double DANGEROUS = 0.50;

    private static final class FakeTarget implements NetherGasGameplay.EffectTarget {
        private final boolean alive;
        private boolean appliedMild;
        private boolean appliedDangerous;

        FakeTarget(boolean alive) {
            this.alive = alive;
        }

        @Override
        public boolean isAlive() { return alive; }

        @Override
        public void applyMild() { appliedMild = true; }

        @Override
        public void applyDangerous() { appliedDangerous = true; }
    }

    @Test
    void inactiveCellNeverInvokesTheTargetsSupplier() {
        var calls = new int[1];
        NetherGasGameplay.runEligible(Optional.of(new AtmosphereMaterialState(0, 1000)), MILD, DANGEROUS,
            () -> { calls[0]++; return List.of(); });
        NetherGasGameplay.runEligible(Optional.empty(), MILD, DANGEROUS,
            () -> { calls[0]++; return List.of(); });
        assertEquals(0, calls[0], "an ineligible or absent cell must never issue the entity query");
    }

    @Test
    void activeCellInvokesTheTargetsSupplierExactlyOnce() {
        var calls = new int[1];
        NetherGasGameplay.runEligible(Optional.of(new AtmosphereMaterialState(500, 1000)), MILD, DANGEROUS,
            () -> { calls[0]++; return List.of(); });
        assertEquals(1, calls[0]);
    }

    @Test
    void activeEffectAppliesToEveryAliveTargetAndSkipsDeadOnes() {
        var alive1 = new FakeTarget(true);
        var alive2 = new FakeTarget(true);
        var dead = new FakeTarget(false);
        NetherGasGameplay.runEligible(Optional.of(new AtmosphereMaterialState(500, 1000)), MILD, DANGEROUS,
            () -> List.of(alive1, dead, alive2));

        assertTrue(alive1.appliedMild);
        assertTrue(alive1.appliedDangerous);
        assertTrue(alive2.appliedMild);
        assertTrue(alive2.appliedDangerous);
        assertFalse(dead.appliedMild, "a dead target must never receive the effect");
        assertFalse(dead.appliedDangerous);
    }

    @Test
    void mildOnlyFullnessNeverAppliesTheDangerousEffect() {
        var target = new FakeTarget(true);
        NetherGasGameplay.runEligible(Optional.of(new AtmosphereMaterialState(100, 1000)), MILD, DANGEROUS,
            () -> List.of(target));
        assertTrue(target.appliedMild);
        assertFalse(target.appliedDangerous);
    }

    @Test
    void emptyOrZeroCapacityCellsHaveNoEffect() {
        assertFalse(NetherGasGameplay.effectFor(0, 1000, MILD, DANGEROUS).active());
        assertFalse(NetherGasGameplay.effectFor(500, 0, MILD, DANGEROUS).active());
    }

    @Test
    void belowMildFullnessHasNoEffect() {
        var effect = NetherGasGameplay.effectFor(99, 1000, MILD, DANGEROUS);
        assertFalse(effect.active());
        assertFalse(effect.mild());
        assertFalse(effect.dangerous());
    }

    @Test
    void atOrAboveMildFullnessAndBelowDangerousIsMildOnly() {
        var effect = NetherGasGameplay.effectFor(100, 1000, MILD, DANGEROUS);
        assertTrue(effect.active());
        assertTrue(effect.mild());
        assertFalse(effect.dangerous());
    }

    @Test
    void atOrAboveDangerousFullnessIsBothMildAndDangerous() {
        var effect = NetherGasGameplay.effectFor(500, 1000, MILD, DANGEROUS);
        assertTrue(effect.active());
        assertTrue(effect.mild());
        assertTrue(effect.dangerous());
    }
}
