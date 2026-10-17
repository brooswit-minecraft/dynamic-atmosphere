package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericAction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.brooswitminecraft.dynamicatmosphere.AtmosphericActionExecutor.execute;
import static io.github.brooswitminecraft.dynamicatmosphere.AtmosphericActionExecutor.executeAtMostOneAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review fix (PR #65, comments 47565/47625): the production action-to-debit path must
 * only report success when the world actually accepted the action, so a caller never
 * charges its material cost for a no-op. This exercises the REAL dispatch/validation code
 * in {@link AtmosphericActionExecutor} -- unknown-id rejection, parameter defaults, and
 * success propagation -- through {@link AtmosphericActionExecutor.ActionSink}, the seam
 * production wraps around a real {@code ServerLevel} ({@code ServerLevelActionSink}) and
 * this test replaces with {@link FakeSink}. No {@code ServerLevel} construction, no
 * Minecraft bootstrap, and no parallel reimplementation of the logic under test.
 */
class AtmosphericActionExecutorTest {

    private static final BlockPos ORIGIN = BlockPos.ZERO;

    @Test
    void acceptedSpawnEntityReportsSuccessExactlyOnce() {
        var sink = new FakeSink();
        sink.knownEntities.add("minecraft:ghast");
        sink.spawnEntityResult = true;
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "minecraft:ghast"));

        assertTrue(execute(sink, ORIGIN, action));
        assertEquals(1, sink.spawnEntityCalls.size(), "the world must be asked to spawn exactly once");
    }

    @Test
    void rejectedSpawnEntityReportsFailureAndIsNotTreatedAsSuccess() {
        // The exact case the bug fix targets: a valid, known entity id whose actual spawn
        // attempt is cancelled by the world (e.g. EntityType.spawn returning null).
        var sink = new FakeSink();
        sink.knownEntities.add("minecraft:ghast");
        sink.spawnEntityResult = false;
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "minecraft:ghast"));

        assertFalse(execute(sink, ORIGIN, action), "a cancelled spawn must not be reported as success");
    }

    @Test
    void unknownEntityIdFailsWithoutEverAskingTheWorldToSpawn() {
        var sink = new FakeSink();
        // Deliberately NOT added to knownEntities.
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "not:an_entity"));

        assertFalse(execute(sink, ORIGIN, action));
        assertTrue(sink.spawnEntityCalls.isEmpty(), "an unknown id must never reach the real spawn call");
    }

    @Test
    void acceptedAndRejectedItemInsertionPropagateRealAcceptance() {
        var accepted = new FakeSink();
        accepted.knownItems.add("minecraft:slime_ball");
        accepted.spawnItemResult = true;
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ITEM, Map.of("item", "minecraft:slime_ball"));
        assertTrue(execute(accepted, ORIGIN, action));

        var rejected = new FakeSink();
        rejected.knownItems.add("minecraft:slime_ball");
        rejected.spawnItemResult = false;
        assertFalse(execute(rejected, ORIGIN, action), "a rejected insertion (addFreshEntity false) must not count as success");
    }

    @Test
    void placeBlockRefusesAnOccupiedTargetWithoutWritingTheBlock() {
        var sink = new FakeSink();
        sink.knownBlocks.add("minecraft:lightning_rod");
        sink.emptySpace = false;
        var action = new AtmosphericAction(AtmosphericAction.Type.PLACE_BLOCK, Map.of("block", "minecraft:lightning_rod"));

        assertFalse(execute(sink, ORIGIN, action));
        assertTrue(sink.placeBlockCalls.isEmpty(), "an occupied target must never reach the real block write");
    }

    @Test
    void replaceBlockWritesEvenWhenOccupiedAndPropagatesTheWriteResult() {
        var accepted = new FakeSink();
        accepted.knownBlocks.add("minecraft:slime_block");
        accepted.emptySpace = false;
        accepted.placeBlockResult = true;
        var action = new AtmosphericAction(AtmosphericAction.Type.REPLACE_BLOCK, Map.of("block", "minecraft:slime_block"));
        assertTrue(execute(accepted, ORIGIN, action));
        assertEquals(1, accepted.placeBlockCalls.size());

        var rejected = new FakeSink();
        rejected.knownBlocks.add("minecraft:slime_block");
        rejected.emptySpace = false;
        rejected.placeBlockResult = false;
        assertFalse(execute(rejected, ORIGIN, action), "setBlockAndUpdate returning false must not count as success");
    }

    @Test
    void emitMaterialPropagatesRealDestinationAcceptanceNotJustIdMatch() {
        // The exact finding from comment 47625: a matched material id alone used to be
        // treated as success even when the destination (unloaded/unreadable/full) rejected
        // the emission.
        var accepted = new FakeSink();
        accepted.knownMaterials.add("zombie_fog");
        accepted.emitMaterialResult = true;
        var emit = new AtmosphericAction(AtmosphericAction.Type.EMIT_MATERIAL, Map.of("material", "zombie_fog"));
        assertTrue(execute(accepted, ORIGIN, emit));

        var rejected = new FakeSink();
        rejected.knownMaterials.add("zombie_fog");
        rejected.emitMaterialResult = false;
        assertFalse(execute(rejected, ORIGIN, emit), "an id match must not stand in for the destination actually accepting it");

        var transfer = new AtmosphericAction(AtmosphericAction.Type.TRANSFER_MATERIAL, Map.of("material", "zombie_fog"));
        assertFalse(execute(rejected, ORIGIN, transfer), "TRANSFER_MATERIAL must propagate acceptance the same way");
    }

    @Test
    void unknownMaterialIdFailsWithoutEverCallingEmit() {
        var sink = new FakeSink();
        var action = new AtmosphericAction(AtmosphericAction.Type.EMIT_MATERIAL, Map.of("material", "not_a_material"));

        assertFalse(execute(sink, ORIGIN, action));
        assertTrue(sink.emitMaterialCalls.isEmpty());
    }

    @Test
    void unknownBlockEffectAndItemIdsAllFailWithoutTouchingTheWorld() {
        var sink = new FakeSink();
        assertFalse(execute(sink, ORIGIN,
            new AtmosphericAction(AtmosphericAction.Type.PLACE_BLOCK, Map.of("block", "not:a_block"))));
        assertFalse(execute(sink, ORIGIN,
            new AtmosphericAction(AtmosphericAction.Type.APPLY_EFFECT, Map.of("effect", "not:an_effect"))));
        assertFalse(execute(sink, ORIGIN,
            new AtmosphericAction(AtmosphericAction.Type.SPAWN_ITEM, Map.of("item", "not:an_item"))));
        assertTrue(sink.placeBlockCalls.isEmpty());
        assertTrue(sink.applyEffectCalls.isEmpty());
        assertTrue(sink.spawnItemCalls.isEmpty());
    }

    @Test
    void zeroActionsSucceedsWithoutCallingTheSink() {
        // Spec section 26's actionless heat sink: nothing can fail, so the cost stays
        // eligible to be committed.
        var sink = new FakeSink();
        assertTrue(executeAtMostOneAction(sink, ORIGIN, List.of(), "heat_sink"));
        assertTrue(sink.spawnEntityCalls.isEmpty());
    }

    @Test
    void oneActionExecutesExactlyOnceAndPropagatesSuccess() {
        var sink = new FakeSink();
        sink.knownEntities.add("minecraft:ghast");
        sink.spawnEntityResult = true;
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "minecraft:ghast"));

        assertTrue(executeAtMostOneAction(sink, ORIGIN, List.of(action), "ghast_spawn"));
        assertEquals(1, sink.spawnEntityCalls.size());
    }

    @Test
    void oneActionExecutesExactlyOnceAndPropagatesFailure() {
        var sink = new FakeSink();
        sink.knownEntities.add("minecraft:ghast");
        sink.spawnEntityResult = false;
        var action = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "minecraft:ghast"));

        assertFalse(executeAtMostOneAction(sink, ORIGIN, List.of(action), "ghast_spawn"));
        assertEquals(1, sink.spawnEntityCalls.size(), "a failed action must still be attempted exactly once");
    }

    @Test
    void twoOrMoreActionsFailWithZeroExecutorCallsAndNoPartialOutput() {
        // The exact conservation defect comment 47631 asks this to prove: the size check
        // must run BEFORE any side effect, not after the first one, or a 2-action event
        // could leave the first action's real output in place while refusing the second.
        var sink = new FakeSink();
        sink.knownEntities.add("minecraft:ghast");
        sink.spawnEntityResult = true;
        var first = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "minecraft:ghast"));
        var second = new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY, Map.of("entity", "minecraft:ghast"));

        assertFalse(executeAtMostOneAction(sink, ORIGIN, List.of(first, second), "two_actions"));
        assertTrue(sink.spawnEntityCalls.isEmpty(), "neither action may reach the sink once there are 2+");
    }

    /** Records every call it receives so tests can assert what production actually did. */
    private static final class FakeSink implements AtmosphericActionExecutor.ActionSink {
        final List<String> knownEntities = new ArrayList<>();
        final List<String> knownItems = new ArrayList<>();
        final List<String> knownBlocks = new ArrayList<>();
        final List<String> knownEffects = new ArrayList<>();
        final List<String> knownMaterials = new ArrayList<>();
        boolean emptySpace = true;
        boolean spawnEntityResult = true;
        boolean spawnItemResult = true;
        boolean placeBlockResult = true;
        boolean emitMaterialResult = true;
        final List<ResourceLocation> spawnEntityCalls = new ArrayList<>();
        final List<ResourceLocation> spawnItemCalls = new ArrayList<>();
        final List<ResourceLocation> placeBlockCalls = new ArrayList<>();
        final List<ResourceLocation> applyEffectCalls = new ArrayList<>();
        final List<String> emitMaterialCalls = new ArrayList<>();

        @Override
        public boolean entityTypeExists(ResourceLocation id) { return knownEntities.contains(id.toString()); }

        @Override
        public boolean spawnEntity(ResourceLocation id, BlockPos target) {
            spawnEntityCalls.add(id);
            return spawnEntityResult;
        }

        @Override
        public boolean itemExists(ResourceLocation id) { return knownItems.contains(id.toString()); }

        @Override
        public boolean spawnItem(ResourceLocation id, BlockPos target, int count) {
            spawnItemCalls.add(id);
            return spawnItemResult;
        }

        @Override
        public boolean blockExists(ResourceLocation id) { return knownBlocks.contains(id.toString()); }

        @Override
        public boolean isEmptySpace(BlockPos target) { return emptySpace; }

        @Override
        public boolean placeBlock(ResourceLocation id, BlockPos target) {
            placeBlockCalls.add(id);
            return placeBlockResult;
        }

        @Override
        public boolean effectExists(ResourceLocation id) { return knownEffects.contains(id.toString()); }

        @Override
        public boolean applyEffect(ResourceLocation id, BlockPos target, int durationTicks, int amplifier, double radius) {
            applyEffectCalls.add(id);
            return true;
        }

        @Override
        public boolean materialExists(String materialId) { return knownMaterials.contains(materialId); }

        @Override
        public boolean emitMaterial(String materialId, BlockPos target, int amount) {
            emitMaterialCalls.add(materialId);
            return emitMaterialResult;
        }
    }
}
