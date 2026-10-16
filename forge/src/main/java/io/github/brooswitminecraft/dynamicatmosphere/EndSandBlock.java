package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MINECRAFT-1 (B4): rises 1 block every {@code endSand.riseIntervalTicks}. When blocked above it
 * simply stops rescheduling itself a tick and resumes once that tick finds the way clear again;
 * deleted outright rather than placed when a rise would cross the dimension's max build height.
 * Player-placed End Sand rises exactly the same way as condensed End Sand -- {@link #onPlace}
 * schedules the first tick for any fresh placement, not just the mod's own.
 */
public final class EndSandBlock extends Block {
    public static final MapCodec<EndSandBlock> CODEC = simpleCodec(EndSandBlock::new);

    public EndSandBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) && !level.isClientSide()) {
            level.scheduleTick(pos, this, riseIntervalTicks());
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        if (above.getY() >= level.getMaxBuildHeight()) {
            level.removeBlock(pos, false);
            return;
        }
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.isAir() || aboveState.canBeReplaced()) {
            level.setBlock(above, state, 3);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.scheduleTick(above, this, riseIntervalTicks());
        } else {
            // Blocked: stop here, but reschedule so this resumes as soon as it's freed.
            level.scheduleTick(pos, this, riseIntervalTicks());
        }
    }

    private static int riseIntervalTicks() {
        return DynamicAtmosphereServerConfig.snapshot().endSand().riseIntervalTicks();
    }
}
