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

import java.util.function.BooleanSupplier;

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
        BlockState aboveState = level.getBlockState(above);
        Outcome outcome = attemptRise(
            above.getY() >= level.getMaxBuildHeight(),
            () -> level.removeBlock(pos, false),
            aboveState.isAir() || aboveState.canBeReplaced(),
            () -> level.setBlock(above, state, 3),
            () -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3),
            () -> level.removeBlock(above, false));
        switch (outcome) {
            case DELETED -> {}
            case BLOCKED -> level.scheduleTick(pos, this, riseIntervalTicks());
            case MOVED -> level.scheduleTick(above, this, riseIntervalTicks());
        }
    }

    enum Outcome { DELETED, BLOCKED, MOVED }

    /**
     * Regression for a review finding on MINECRAFT-1 (B4): the move used to ignore the result of
     * both {@code setBlock} calls, so a failed destination write could delete the source with
     * nothing produced, and a failed source clear after a successful destination write could
     * duplicate the block. The move is now checked and conservative: a failed destination write
     * leaves the source untouched (treated as blocked), and a failed source clear after a
     * successful destination write is undone (the destination placement is rolled back) rather
     * than left as a duplicate. Package-private and pure so review's "test the production path"
     * requirement can be met with injected failures instead of a full ServerLevel bootstrap.
     */
    static Outcome attemptRise(
            boolean atCeiling,
            BooleanSupplier removeAtCeiling,
            boolean destinationOpen,
            BooleanSupplier placeAbove,
            BooleanSupplier clearSource,
            Runnable undoAbove) {
        if (atCeiling) {
            removeAtCeiling.getAsBoolean();
            return Outcome.DELETED;
        }
        if (!destinationOpen) {
            return Outcome.BLOCKED;
        }
        if (!placeAbove.getAsBoolean()) {
            return Outcome.BLOCKED;
        }
        if (!clearSource.getAsBoolean()) {
            undoAbove.run();
            return Outcome.BLOCKED;
        }
        return Outcome.MOVED;
    }

    private static int riseIntervalTicks() {
        return DynamicAtmosphereServerConfig.snapshot().endSand().riseIntervalTicks();
    }
}
