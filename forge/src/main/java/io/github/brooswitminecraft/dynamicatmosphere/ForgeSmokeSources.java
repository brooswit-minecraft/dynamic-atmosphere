package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** State-only queries: reuse the existing bounded section scan, never register a second fire producer. */
public final class ForgeSmokeSources {
    public static SmokeProducerRules.Source source(BlockState state) {
        if (state.is(BlockTags.FIRE)) return SmokeProducerRules.Source.FIRE;
        if (state.getFluidState().is(FluidTags.LAVA)) return SmokeProducerRules.Source.LAVA;
        if (state.getBlock() instanceof AbstractFurnaceBlock) return SmokeProducerRules.Source.FURNACE;
        if (state.getBlock() instanceof CampfireBlock) return SmokeProducerRules.Source.CAMPFIRE;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
            || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
            || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) {
            return SmokeProducerRules.Source.TORCH;
        }
        return SmokeProducerRules.Source.NONE;
    }

    public static int ongoing(BlockState state) {
        return SmokeProducerRules.ongoing(source(state),
            !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT));
    }

    /** Supply the successful setBlockState return value as previous; null denotes failure/no change. */
    public static int transition(BlockState previous, BlockState next) {
        if (previous == null || AtmosphereFluidTransport.active()) return 0;
        return SmokeProducerRules.transition(true, previous.is(BlockTags.FIRE), next.is(BlockTags.FIRE),
            previous.getFluidState().is(FluidTags.LAVA), next.getFluidState().is(FluidTags.LAVA), false);
    }

    private ForgeSmokeSources() { }
}
