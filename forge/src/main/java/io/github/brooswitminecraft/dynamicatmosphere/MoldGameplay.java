package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MushroomBlock;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

final class MoldGameplay {
    private final ForgeAtmospherePrototype prototype;

    MoldGameplay(ForgeAtmospherePrototype prototype) { this.prototype = prototype; }

    /** A single candidate per processed cell, independent of world size or entity population. */
    static int processCell(ServerLevel level, BlockPos origin, int amount, int capacity) {
        var config = DynamicAtmosphereServerConfig.snapshot().mold();
        int size = AtmosphereMaterial.MOLD.cellSize();
        BlockPos pos = origin.offset(level.random.nextInt(size), level.random.nextInt(size),
            level.random.nextInt(size));
        var state = level.getBlockState(pos);
        if (state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM)) {
            return MoldGrowth.attempt(amount, capacity, config.giantMushroomCost(),
                config.highConcentration(), config.transformationChance(), level.random.nextDouble(), () -> {
                    // Vanilla giant features may cross chunk boundaries. Only run with the entire
                    // conservative feature footprint loaded; no chunk retrieval/generation here.
                    if (!level.hasChunksAt(pos.offset(-16, -1, -16), pos.offset(16, 16, 16))) return false;
                    return ((MushroomBlock) state.getBlock()).growMushroom(level, pos, state, level.random);
                });
        }
        return MoldGrowth.attempt(amount, capacity, config.smallMushroomCost(), 0,
            config.transformationChance(), level.random.nextDouble(), () -> {
                if (!state.isAir() || !level.isInWorldBounds(pos.below())) return false;
                var mushroom = (level.random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM)
                    .defaultBlockState();
                return mushroom.canSurvive(level, pos) && level.setBlockAndUpdate(pos, mushroom);
            });
    }

    void onEntityTick(EntityTickEvent.Post event) {
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        var config = DynamicAtmosphereServerConfig.snapshot().mold();
        if (Math.floorMod(level.getGameTime() + entity.getId(), config.witchIntervalTicks()) != 0) return;
        if (entity instanceof Witch) {
            prototype.emitMaterial(level, AtmosphereMaterial.MOLD, entity.blockPosition(), config.sourceEmission());
        } else if (entity.getType() == EntityType.COW && entity instanceof Cow cow) {
            BlockPos pos = entity.blockPosition();
            prototype.materialState(level, AtmosphereMaterial.MOLD, pos).ifPresent(cell -> {
                int cost = MoldGrowth.attempt(cell.amount(), cell.capacity(), config.mooshroomCost(),
                    config.highConcentration(), config.transformationChance(), level.random.nextDouble(),
                    () -> {
                        var converted = cow.convertTo(EntityType.MOOSHROOM, false);
                        return converted != null && level.getEntity(converted.getUUID()) == converted;
                    });
                if (cost > 0) prototype.consumeMaterial(level, AtmosphereMaterial.MOLD, pos, cost);
            });
        }
    }
}
