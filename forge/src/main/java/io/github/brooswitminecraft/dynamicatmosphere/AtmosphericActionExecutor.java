package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.logging.LogUtils;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * The Forge half of the generic event/action system (spec section 23): interprets a
 * data-defined {@link AtmosphericAction} against an actual {@code ServerLevel}.
 *
 * <p>This is the ONLY place any of the seven generic action types touches Minecraft.
 * {@link io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericEventEngine}
 * never calls this class directly -- callers resolve which action(s) fire via the
 * engine, then hand each one to {@link #execute} here. Deliberately boring: there is no
 * branch anywhere in this class for "lightning" or "ghast" or "zombie fog"; it only
 * knows about the seven action TYPES, and every built-in event is just data naming one
 * of them.
 *
 * <p>All id-parsing, unknown-id rejection, and default-parameter logic lives in this
 * class and runs against {@link ActionSink}, a seam over the handful of raw Minecraft
 * calls that actually mutate the world. Production always goes through
 * {@link #execute(ServerLevel, BlockPos, AtmosphericAction)}, which wraps the real level
 * in {@link ServerLevelActionSink}; tests exercise the exact same dispatch/validation
 * code via {@link #execute(ActionSink, BlockPos, AtmosphericAction)} with a fake sink, no
 * {@code ServerLevel} required.
 */
final class AtmosphericActionExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AtmosphericActionExecutor() { }

    /**
     * Returns whether the action actually took effect -- callers must only debit their
     * material cost on {@code true}; a cost charged against a no-op action destroys gas
     * for nothing (e.g. a cancelled entity spawn, or a block placement rejected because
     * the target was no longer empty space).
     */
    static boolean execute(ServerLevel level, BlockPos target, AtmosphericAction action) {
        return execute(new ServerLevelActionSink(level), target, action);
    }

    /** Same dispatch as {@link #execute(ServerLevel, BlockPos, AtmosphericAction)}, seamed for tests. */
    static boolean execute(ActionSink sink, BlockPos target, AtmosphericAction action) {
        return switch (action.type()) {
            case SPAWN_ENTITY -> spawnEntity(sink, target, action);
            case SPAWN_ITEM -> spawnItem(sink, target, action);
            case PLACE_BLOCK -> placeBlock(sink, target, action, false);
            case REPLACE_BLOCK -> placeBlock(sink, target, action, true);
            case APPLY_EFFECT -> applyEffect(sink, target, action);
            case EMIT_MATERIAL, TRANSFER_MATERIAL -> emitMaterial(sink, target, action);
        };
    }

    private static boolean spawnEntity(ActionSink sink, BlockPos target, AtmosphericAction action) {
        ResourceLocation id = parse(action.param("entity"));
        if (id == null || !sink.entityTypeExists(id)) {
            LOGGER.warn("[dynamicatmosphere] spawn_entity: unknown entity id {}", action.param("entity"));
            return false;
        }
        return sink.spawnEntity(id, target);
    }

    private static boolean spawnItem(ActionSink sink, BlockPos target, AtmosphericAction action) {
        ResourceLocation id = parse(action.param("item"));
        if (id == null || !sink.itemExists(id)) {
            LOGGER.warn("[dynamicatmosphere] spawn_item: unknown item id {}", action.param("item"));
            return false;
        }
        int count = Math.max(1, parseInt(action.param("count", "1"), 1));
        return sink.spawnItem(id, target, count);
    }

    private static boolean placeBlock(ActionSink sink, BlockPos target, AtmosphericAction action, boolean replace) {
        ResourceLocation id = parse(action.param("block"));
        if (id == null || !sink.blockExists(id)) {
            LOGGER.warn("[dynamicatmosphere] {}: unknown block id {}", action.type(), action.param("block"));
            return false;
        }
        if (!replace && !sink.isEmptySpace(target)) return false;
        return sink.placeBlock(id, target);
    }

    private static boolean applyEffect(ActionSink sink, BlockPos target, AtmosphericAction action) {
        ResourceLocation id = parse(action.param("effect"));
        if (id == null || !sink.effectExists(id)) {
            LOGGER.warn("[dynamicatmosphere] apply_effect: unknown effect id {}", action.param("effect"));
            return false;
        }
        int durationTicks = Math.max(1, parseInt(action.param("durationTicks", "100"), 100));
        int amplifier = Math.max(0, parseInt(action.param("amplifier", "0"), 0));
        double radius = Math.max(0.5, parseDouble(action.param("radius", "2.0"), 2.0));
        return sink.applyEffect(id, target, durationTicks, amplifier, radius);
    }

    /**
     * Local, same-dimension only: cross-dimension transfer (the portal Transfer Sink
     * from spec section 32) is Phase 4 work and explicitly out of scope here.
     */
    private static boolean emitMaterial(ActionSink sink, BlockPos target, AtmosphericAction action) {
        String materialId = action.param("material");
        int amount = Math.max(1, parseInt(action.param("amount", "1"), 1));
        if (!sink.materialExists(materialId)) {
            LOGGER.warn("[dynamicatmosphere] {}: unknown material id {}", action.type(), materialId);
            return false;
        }
        // Propagate real acceptance: an unloaded/unreadable/full destination makes the
        // underlying emit return false, and a matched material id must not be treated as
        // success on its own -- the caller must not debit its source cost for an emission
        // the destination never actually accepted.
        return sink.emitMaterial(materialId, target, amount);
    }

    private static ResourceLocation parse(String id) {
        try {
            return ResourceLocation.parse(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * The raw-Minecraft seam: every method here is a single real mutation or lookup, with
     * no id-parsing or default-handling of its own (that stays in the switch above, where
     * it's exercised identically whether the sink is real or fake). {@link
     * ServerLevelActionSink} is the only production implementation; tests supply a fake.
     */
    interface ActionSink {
        boolean entityTypeExists(ResourceLocation id);
        boolean spawnEntity(ResourceLocation id, BlockPos target);
        boolean itemExists(ResourceLocation id);
        boolean spawnItem(ResourceLocation id, BlockPos target, int count);
        boolean blockExists(ResourceLocation id);
        boolean isEmptySpace(BlockPos target);
        boolean placeBlock(ResourceLocation id, BlockPos target);
        boolean effectExists(ResourceLocation id);
        boolean applyEffect(ResourceLocation id, BlockPos target, int durationTicks, int amplifier, double radius);
        boolean materialExists(String materialId);
        boolean emitMaterial(String materialId, BlockPos target, int amount);
    }

    private record ServerLevelActionSink(ServerLevel level) implements ActionSink {
        @Override
        public boolean entityTypeExists(ResourceLocation id) {
            return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
        }

        @Override
        public boolean spawnEntity(ResourceLocation id, BlockPos target) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            return type.spawn(level, target, MobSpawnType.EVENT) != null;
        }

        @Override
        public boolean itemExists(ResourceLocation id) {
            return BuiltInRegistries.ITEM.containsKey(id);
        }

        @Override
        public boolean spawnItem(ResourceLocation id, BlockPos target, int count) {
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemEntity entity = new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.2, target.getZ() + 0.5,
                new ItemStack(item, count));
            return level.addFreshEntity(entity);
        }

        @Override
        public boolean blockExists(ResourceLocation id) {
            return BuiltInRegistries.BLOCK.containsKey(id);
        }

        @Override
        public boolean isEmptySpace(BlockPos target) {
            return ForgeCapacityBlockClassifier.isEmptySpace(level.getBlockState(target));
        }

        @Override
        public boolean placeBlock(ResourceLocation id, BlockPos target) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            return level.setBlockAndUpdate(target, block.defaultBlockState());
        }

        @Override
        public boolean effectExists(ResourceLocation id) {
            return BuiltInRegistries.MOB_EFFECT.containsKey(id);
        }

        @Override
        public boolean applyEffect(ResourceLocation id, BlockPos target, int durationTicks, int amplifier, double radius) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(BuiltInRegistries.MOB_EFFECT.get(id));
            AABB bounds = new AABB(target).inflate(radius);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, LivingEntity::isAlive)) {
                entity.addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
            }
            return true;
        }

        @Override
        public boolean materialExists(String materialId) {
            for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
                if (material.id().equals(materialId)) return true;
            }
            return false;
        }

        @Override
        public boolean emitMaterial(String materialId, BlockPos target, int amount) {
            for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
                if (material.id().equals(materialId)) {
                    return DynamicAtmosphereMod.emitMaterial(level, material, target, amount);
                }
            }
            return false;
        }
    }
}
