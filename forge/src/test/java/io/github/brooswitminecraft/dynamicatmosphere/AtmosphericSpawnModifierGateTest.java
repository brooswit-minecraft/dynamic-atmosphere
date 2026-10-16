package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.SpawnModifier;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.brooswitminecraft.dynamicatmosphere.AtmosphericSpawnModifierGate.applyModifier;
import static net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT;
import static net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;
import static net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review fix (PR #65): the gate must not treat a placement check's starting {@code DEFAULT}
 * result as already-allowed, and overriding a deny must force {@code SUCCEED}, not reset to
 * {@code DEFAULT}. {@link AtmosphericSpawnModifierGate#applyModifier} is the pure decision this
 * regresses -- Result in, Result out, no {@link MobSpawnEvent} construction required.
 */
class AtmosphericSpawnModifierGateTest {

    private static final SpawnModifier BOOST = new SpawnModifier("minecraft:zombie", List.of(), 2.0);
    private static final SpawnModifier SUPPRESS = new SpawnModifier("minecraft:zombie", List.of(), 0.5);
    private static final SpawnModifier NO_OP = new SpawnModifier("minecraft:zombie", List.of(), 1.0);

    @Test
    void boostOverridesAnOrdinaryVanillaDefaultThatWouldHaveFailed() {
        // This is the bug: a placement check that nothing has explicitly decided yet starts
        // DEFAULT, not SUCCEED. A >1 multiplier exists specifically to rescue the ordinary case
        // where vanilla's own predicate was going to fail -- it must not be skipped just because
        // no earlier gate happened to set FAIL explicitly.
        assertEquals(SUCCEED, applyModifier(DEFAULT, BOOST, 0.0));
        assertEquals(SUCCEED, applyModifier(DEFAULT, BOOST, 0.99));
    }

    @Test
    void boostOverridesAnExplicitFailWithRealSuccessNotDefault() {
        // Resetting an explicit FAIL to DEFAULT only asks vanilla to decide again, which can
        // discard another gate's deliberate denial without actually forcing the spawn through.
        assertEquals(SUCCEED, applyModifier(FAIL, BOOST, 0.0));
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
}
