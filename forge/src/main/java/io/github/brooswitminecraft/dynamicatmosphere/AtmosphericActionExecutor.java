package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.logging.LogUtils;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericAction;
import net.minecraft.core.BlockPos;
import java.util.List;
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
import net.minecraft.world.level.block.state.BlockState;
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
        return switch (action.type()) {
            case SPAWN_ENTITY -> spawnEntity(level, target, action);
            case SPAWN_ITEM -> spawnItem(level, target, action);
            case PLACE_BLOCK -> placeBlock(level, target, action, false);
            case REPLACE_BLOCK -> placeBlock(level, target, action, true);
            case APPLY_EFFECT -> applyEffect(level, target, action);
            case EMIT_MATERIAL, TRANSFER_MATERIAL -> emitMaterial(level, target, action);
        };
    }

    private static boolean spawnEntity(ServerLevel level, BlockPos target, AtmosphericAction action) {
        ResourceLocation id = parse(action.param("entity"));
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            LOGGER.warn("[dynamicatmosphere] spawn_entity: unknown entity id {}", action.param("entity"));
            return false;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return type.spawn(level, target, MobSpawnType.EVENT) != null;
    }

    private static boolean spawnItem(ServerLevel level, BlockPos target, AtmosphericAction action) {
        ResourceLocation id = parse(action.param("item"));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            LOGGER.warn("[dynamicatmosphere] spawn_item: unknown item id {}", action.param("item"));
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        int count = Math.max(1, parseInt(action.param("count", "1"), 1));
        ItemEntity entity = new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.2, target.getZ() + 0.5,
            new ItemStack(item, count));
        return level.addFreshEntity(entity);
    }

    private static boolean placeBlock(ServerLevel level, BlockPos target, AtmosphericAction action, boolean replace) {
        ResourceLocation id = parse(action.param("block"));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            LOGGER.warn("[dynamicatmosphere] {}: unknown block id {}", action.type(), action.param("block"));
            return false;
        }
        BlockState current = level.getBlockState(target);
        if (!replace && !ForgeCapacityBlockClassifier.isEmptySpace(current)) return false;
        Block block = BuiltInRegistries.BLOCK.get(id);
        return level.setBlockAndUpdate(target, block.defaultBlockState());
    }

    private static boolean applyEffect(ServerLevel level, BlockPos target, AtmosphericAction action) {
        ResourceLocation id = parse(action.param("effect"));
        if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            LOGGER.warn("[dynamicatmosphere] apply_effect: unknown effect id {}", action.param("effect"));
            return false;
        }
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(BuiltInRegistries.MOB_EFFECT.get(id));
        int durationTicks = Math.max(1, parseInt(action.param("durationTicks", "100"), 100));
        int amplifier = Math.max(0, parseInt(action.param("amplifier", "0"), 0));
        double radius = Math.max(0.5, parseDouble(action.param("radius", "2.0"), 2.0));
        AABB bounds = new AABB(target).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, LivingEntity::isAlive)) {
            entity.addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
        }
        return true;
    }

    /**
     * Local, same-dimension only: cross-dimension transfer (the portal Transfer Sink
     * from spec section 32) is Phase 4 work and explicitly out of scope here.
     */
    private static boolean emitMaterial(ServerLevel level, BlockPos target, AtmosphericAction action) {
        String materialId = action.param("material");
        int amount = Math.max(1, parseInt(action.param("amount", "1"), 1));
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            if (material.id().equals(materialId)) {
                DynamicAtmosphereMod.emitMaterial(level, material, target, amount);
                return true;
            }
        }
        LOGGER.warn("[dynamicatmosphere] {}: unknown material id {}", action.type(), materialId);
        return false;
    }

    /**
     * Folds an event's per-action outcomes into one verdict for whether the caller may
     * commit its material cost. Vacuously {@code true} for an empty list -- the spec
     * section 26 actionless heat sink has nothing to fail, so it always consumes; any
     * single failed action denies the whole event, since a partially-applied event (e.g.
     * one of two actions landing) has no well-defined "half the cost" to charge.
     */
    static boolean allActionsSucceeded(List<Boolean> outcomes) {
        for (boolean outcome : outcomes) {
            if (!outcome) return false;
        }
        return true;
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
}
