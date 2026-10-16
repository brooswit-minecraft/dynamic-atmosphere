package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericAction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.brooswitminecraft.dynamicatmosphere.AtmosphericActionExecutor.allActionsSucceeded;
import static io.github.brooswitminecraft.dynamicatmosphere.AtmosphericActionExecutor.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review fix (PR #65): callers must only debit an event's material cost when its
 * action(s) actually took effect -- previously every evaluate* method in
 * ForgeAtmospherePrototype charged the cost unconditionally, so a cancelled ghast spawn
 * or a rejected slime-block placement destroyed gas for nothing.
 *
 * <p>{@link AtmosphericActionExecutor#allActionsSucceeded} is the pure fold this fix
 * hinges on and is fully testable without a {@code ServerLevel}. {@link #execute} itself
 * needs a real {@code ServerLevel} to exercise a successful spawn/placement -- no such
 * harness exists anywhere else in this test suite (the project's own convention is to
 * extract pure logic instead, e.g. {@code VaporHostileSpawnGate.evaluate}) -- but every
 * action type's "unknown id" rejection path returns {@code false} before ever touching
 * the level, so those are covered directly by passing a {@code null} level.
 */
class AtmosphericActionExecutorTest {

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void zeroActionsIsVacuouslySuccessful() {
        // Spec section 26's actionless heat sink: no action can fail, so the cost is
        // always committed. This is the exact case the fix must not break.
        assertTrue(allActionsSucceeded(List.of()));
    }

    @Test
    void allSucceedingActionsCommitTheCost() {
        assertTrue(allActionsSucceeded(List.of(true)));
        assertTrue(allActionsSucceeded(List.of(true, true, true)));
    }

    @Test
    void anySingleFailureDeniesTheWholeEvent() {
        assertFalse(allActionsSucceeded(List.of(false)));
        assertFalse(allActionsSucceeded(List.of(true, false)));
        assertFalse(allActionsSucceeded(List.of(false, true, true)));
    }

    @Test
    void unknownEntityIdFailsWithoutTouchingTheLevel() {
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "not:an_entity"));
        assertFalse(execute(null, ORIGIN, action));
    }

    @Test
    void unknownItemIdFailsWithoutTouchingTheLevel() {
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ITEM, Map.of("item", "not:an_item"));
        assertFalse(execute(null, ORIGIN, action));
    }

    @Test
    void unknownBlockIdFailsWithoutTouchingTheLevelForBothPlaceAndReplace() {
        var place = new AtmosphericAction(AtmosphericAction.Type.PLACE_BLOCK, Map.of("block", "not:a_block"));
        var replace = new AtmosphericAction(AtmosphericAction.Type.REPLACE_BLOCK, Map.of("block", "not:a_block"));
        assertFalse(execute(null, ORIGIN, place));
        assertFalse(execute(null, ORIGIN, replace));
    }

    @Test
    void unknownEffectIdFailsWithoutTouchingTheLevel() {
        var action = new AtmosphericAction(AtmosphericAction.Type.APPLY_EFFECT, Map.of("effect", "not:an_effect"));
        assertFalse(execute(null, ORIGIN, action));
    }

    @Test
    void unknownMaterialIdFailsWithoutTouchingTheLevelForBothEmitAndTransfer() {
        var emit = new AtmosphericAction(AtmosphericAction.Type.EMIT_MATERIAL, Map.of("material", "not_a_material"));
        var transfer = new AtmosphericAction(AtmosphericAction.Type.TRANSFER_MATERIAL, Map.of("material", "not_a_material"));
        assertFalse(execute(null, ORIGIN, emit));
        assertFalse(execute(null, ORIGIN, transfer));
    }

    private static final net.minecraft.core.BlockPos ORIGIN = net.minecraft.core.BlockPos.ZERO;
}
