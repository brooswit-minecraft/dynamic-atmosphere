package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.SpawnModifier;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.brooswitminecraft.dynamicatmosphere.AtmosphericSpawnModifierGate.appliesTo;
import static io.github.brooswitminecraft.dynamicatmosphere.AtmosphericSpawnModifierGate.applyModifier;
import static net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT;
import static net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;
import static net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review fixes (PR #65): the gate must not treat a placement check's starting {@code DEFAULT}
 * result as already-allowed (so a boost must still rescue the ordinary vanilla-fails case), but
 * it must never convert an explicit {@code FAIL} -- a deliberate denial, whether vanilla's own or
 * another gate's -- into a {@code SUCCEED}; and it must only apply to natural/chunk-generation
 * spawning, the same source guard {@link VaporHostileSpawnGate} already uses. Both
 * {@link AtmosphericSpawnModifierGate#applyModifier} and
 * {@link AtmosphericSpawnModifierGate#appliesTo} are pure decisions -- Result/boolean in,
 * Result/boolean out, no {@link MobSpawnEvent} construction required.
 */
class AtmosphericSpawnModifierGateTest {

    private static final SpawnModifier BOOST = new SpawnModifier("minecraft:zombie", List.of(), 2.0);
    private static final SpawnModifier SUPPRESS = new SpawnModifier("minecraft:zombie", List.of(), 0.5);
    private static final SpawnModifier NO_OP = new SpawnModifier("minecraft:zombie", List.of(), 1.0);

    @Test
    void boostOverridesAnOrdinaryVanillaDefaultThatWouldHaveFailed() {
        // A placement check that nothing has explicitly decided yet starts DEFAULT, not SUCCEED.
        // A >1 multiplier exists specifically to rescue the ordinary case where vanilla's own
        // predicate was going to fail -- it must not be skipped just because no earlier gate
        // happened to set FAIL explicitly.
        assertEquals(SUCCEED, applyModifier(DEFAULT, BOOST, 0.0));
        assertEquals(SUCCEED, applyModifier(DEFAULT, BOOST, 0.99));
    }

    @Test
    void boostNeverOverridesAnExplicitFail() {
        // An explicit FAIL is a deliberate denial -- vanilla's own predicate already evaluated
        // to no, or another gate (e.g. VaporHostileSpawnGate) already vetoed this placement. This
        // modifier has no business overriding somebody else's veto; it only rescues the
        // not-yet-decided DEFAULT case.
        assertEquals(FAIL, applyModifier(FAIL, BOOST, 0.0));
        assertEquals(FAIL, applyModifier(FAIL, BOOST, 0.99));
    }

    @Test
    void boostNeverTouchesAnAlreadySucceedingResult() {
        assertEquals(SUCCEED, applyModifier(SUCCEED, BOOST, 0.0));
    }

    @Test
    void suppressKnocksDownBothDefaultAndExplicitSuccess() {
        assertEquals(FAIL, applyModifier(DEFAULT, SUPPRESS, 0.0));
        assertEquals(FAIL, applyModifier(SUCCEED, SUPPRESS, 0.0));
    }

    @Test
    void suppressNeverTouchesAnAlreadyFailingResult() {
        assertEquals(FAIL, applyModifier(FAIL, SUPPRESS, 0.0));
    }

    @Test
    void multiplierOfOneNeverChangesAnyResultRegardlessOfRoll() {
        for (var result : List.of(SUCCEED, DEFAULT, FAIL)) {
            assertEquals(result, applyModifier(result, NO_OP, 0.0));
            assertEquals(result, applyModifier(result, NO_OP, 0.999999));
        }
    }

    @Test
    void rollsOutsideTheThresholdLeaveTheResultUnaffected() {
        // multiplier=2.0 -> overrideDenyProbability=1.0, suppressProbability=0.0: a roll of
        // exactly 1.0 is never "< 1.0", so nothing fires.
        assertEquals(DEFAULT, applyModifier(DEFAULT, BOOST, 1.0));
        // multiplier=0.5 -> suppressProbability=0.5: a roll of 0.5 is never "< 0.5".
        assertEquals(DEFAULT, applyModifier(DEFAULT, SUPPRESS, 0.5));
    }

    @Test
    void onlyNaturalAndChunkGenerationSpawningAreInScope() {
        assertTrue(appliesTo(MobSpawnType.NATURAL));
        assertTrue(appliesTo(MobSpawnType.CHUNK_GENERATION));
    }

    @Test
    void explicitAndNonWorldSpawnSourcesAreNeverGated() {
        // Commands, spawn eggs, spawners and other explicit sources must retain their normal
        // behavior, same as VaporHostileSpawnGate's own source guard -- this modifier gate has
        // no business touching a placement nobody asked the world to decide on its own.
        assertFalse(appliesTo(MobSpawnType.COMMAND));
        assertFalse(appliesTo(MobSpawnType.SPAWN_EGG));
        assertFalse(appliesTo(MobSpawnType.SPAWNER));
        assertFalse(appliesTo(MobSpawnType.TRIAL_SPAWNER));
        assertFalse(appliesTo(MobSpawnType.MOB_SUMMONED));
        assertFalse(appliesTo(MobSpawnType.BREEDING));
    }
}
