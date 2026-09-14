package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Snow/ice removal policy. One shared mutation hook; no world mutation or duplicate ongoing producer. */
public final class ForgeSnowSources {
    /** Pass the successful setBlockState return value as previous; null denotes failure/no change. */
    public static int transition(BlockState previous, BlockState next) {
        if (previous == null) return 0;
        return SnowRemovalRules.material(true, layers(previous), layers(next))
            + SnowRemovalRules.iceMaterial(true, previous.is(BlockTags.ICE), next.is(BlockTags.ICE));
    }

    private static int layers(BlockState state) {
        if (state.is(Blocks.SNOW)) return state.getValue(SnowLayerBlock.LAYERS);
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return SnowRemovalRules.FULL_BLOCK_LAYERS;
        }
        return 0;
    }

    private ForgeSnowSources() { }
}
