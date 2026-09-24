package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * NeoForge-facing adapter for the water+lava -&gt; magma override (ATMO-35, cases 1-3 of ATMO-23). Both the
 * obsidian/cobblestone path ({@code FluidInteractionRegistry}) and {@code LavaFluid.spreadTo}'s stone path
 * place their block through this single event, so one listener covers all three cases. Decision logic lives in
 * {@link LavaWaterMagmaRules}; this class only translates real BlockState/BlockPos/LevelAccessor values to and
 * from it, and is deliberately left untested here (see {@code ForgeSmokeSources} for the same split elsewhere
 * in this codebase) — the required in-pack observation is what exercises this adapter against the real event.
 */
public final class LavaWaterMagmaGameplay {

    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        LavaWaterMagmaRules.Decision decision = LavaWaterMagmaRules.classify(proposedBlock(event.getNewState()));
        if (!decision.applies()) return;

        LevelAccessor level = event.getLevel();
        BlockPos pos = event.getPos();

        if (decision.clearsNeighborWater()) {
            for (Direction direction : LiquidBlock.POSSIBLE_FLOW_DIRECTIONS) {
                BlockPos neighbor = pos.relative(direction.getOpposite());
                if (level.getFluidState(neighbor).is(FluidTags.WATER)) {
                    level.setBlock(neighbor, Blocks.AIR.defaultBlockState(), 3);
                    break;
                }
            }
        }

        event.setNewState(Blocks.MAGMA_BLOCK.defaultBlockState());

        if (decision.emitsLavaSmoke() && level instanceof ServerLevel serverLevel) {
            ForgeSmokeGameplay.lavaRemoved(serverLevel, pos);
        }
    }

    static LavaWaterMagmaRules.ProposedBlock proposedBlock(BlockState state) {
        if (state.is(Blocks.OBSIDIAN)) return LavaWaterMagmaRules.ProposedBlock.OBSIDIAN;
        if (state.is(Blocks.COBBLESTONE)) return LavaWaterMagmaRules.ProposedBlock.COBBLESTONE;
        if (state.is(Blocks.STONE)) return LavaWaterMagmaRules.ProposedBlock.STONE;
        return LavaWaterMagmaRules.ProposedBlock.OTHER;
    }

    private LavaWaterMagmaGameplay() { }
}
