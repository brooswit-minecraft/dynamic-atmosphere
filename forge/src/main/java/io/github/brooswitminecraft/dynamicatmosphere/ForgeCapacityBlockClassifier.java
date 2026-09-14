package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Shared terrain semantics for capacity scans and mutation invalidation. */
public final class ForgeCapacityBlockClassifier {
    /** Liquid blocks leave the full volume available; waterlogged hosts stay occupied. */
    public static boolean isEmptySpace(BlockState state) {
        return state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(Blocks.BUBBLE_COLUMN);
    }

    /** Any fluid amount, including flowing fluid and waterlogging, blocks downward transfer. */
    public static boolean blocksDownwardTransfer(BlockState state) {
        return state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty();
    }

    private ForgeCapacityBlockClassifier() { }
}
