package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

    private static final class FakeTarget {
        private final boolean alive;
        private boolean appliedMild;
        private boolean appliedDangerous;

        FakeTarget(boolean alive) {
            this.alive = alive;
        }
    }

    private static <T> void run(
        Optional<AtmosphereMaterialState> state, Supplier<? extends Iterable<? extends T>> targetsSupplier,
        Predicate<? super T> isAlive, Consumer<? super T> applyMild, Consumer<? super T> applyDangerous
    ) {
        NetherGasGameplay.runEligible(state, MILD, DANGEROUS, targetsSupplier, isAlive, applyMild, applyDangerous);
    }

    @Test
    void inactiveCellNeverInvokesTheTargetsSupplier() {
        var calls = new int[1];
        Supplier<Iterable<FakeTarget>> supplier = () -> { calls[0]++; return List.of(); };
        run(Optional.of(new AtmosphereMaterialState(0, 1000)), supplier,
            t -> t.alive, t -> { }, t -> { });
        run(Optional.empty(), supplier, t -> t.alive, t -> { }, t -> { });
        assertEquals(0, calls[0], "an ineligible or absent cell must never issue the entity query");
    }

    @Test
    void activeCellInvokesTheTargetsSupplierExactlyOnce() {
        var calls = new int[1];
        run(Optional.of(new AtmosphereMaterialState(500, 1000)), () -> { calls[0]++; return List.<FakeTarget>of(); },
            t -> t.alive, t -> { }, t -> { });
        assertEquals(1, calls[0]);
    }

    @Test
    void activeEffectAppliesToEveryAliveTargetAndSkipsDeadOnes() {
        var alive1 = new FakeTarget(true);
        var alive2 = new FakeTarget(true);
        var dead = new FakeTarget(false);
        run(Optional.of(new AtmosphereMaterialState(500, 1000)), () -> List.of(alive1, dead, alive2),
            t -> t.alive, t -> t.appliedMild = true, t -> t.appliedDangerous = true);

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
        run(Optional.of(new AtmosphereMaterialState(100, 1000)), () -> List.of(target),
            t -> t.alive, t -> t.appliedMild = true, t -> t.appliedDangerous = true);
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
