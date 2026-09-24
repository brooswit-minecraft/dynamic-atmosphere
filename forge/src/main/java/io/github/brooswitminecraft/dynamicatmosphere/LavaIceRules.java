package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure rung mapping for the ice-ladder -&gt; obsidian route (ATMO-34 cases 4-6):
 * blue ice -&gt; packed ice -&gt; ice -&gt; water, paying out obsidian, obsidian, magma
 * at the lava's own position. Kept free of {@code Level}/{@code BlockPos} so it
 * can be unit tested without a Minecraft-runtime fixture.
 */
public final class LavaIceRules {
    public enum Rung { ICE, PACKED_ICE, BLUE_ICE }

    private LavaIceRules() {
    }

    /** Returns the rung a neighbouring block occupies, or {@code null} if it isn't one of the three ice blocks. */
    public static Rung rungOf(BlockState neighborState) {
        if (neighborState.is(Blocks.ICE)) return Rung.ICE;
        if (neighborState.is(Blocks.PACKED_ICE)) return Rung.PACKED_ICE;
        if (neighborState.is(Blocks.BLUE_ICE)) return Rung.BLUE_ICE;
        return null;
    }

    /** What the neighbour degrades into: one rung down the ladder, or water at the bottom. */
    public static BlockState neighborResult(Rung rung) {
        return switch (rung) {
            case ICE -> Blocks.WATER.defaultBlockState();
            case PACKED_ICE -> Blocks.ICE.defaultBlockState();
            case BLUE_ICE -> Blocks.PACKED_ICE.defaultBlockState();
        };
    }

    /** What the lava's own position becomes: magma for the water rung, obsidian for the other two. */
    public static BlockState lavaResult(Rung rung) {
        return switch (rung) {
            case ICE -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case PACKED_ICE, BLUE_ICE -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }
}
