package io.github.brooswitminecraft.dynamicatmosphere.mixin;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereWaterTransitions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class LevelChunkWaterTransitionMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void dynamicAtmosphere$waterTransition(
        BlockPos pos,
        BlockState next,
        boolean moved,
        CallbackInfoReturnable<BlockState> callback
    ) {
        AtmosphereWaterTransitions.observe((LevelChunk) (Object) this, pos, callback.getReturnValue(), next);
    }
}
