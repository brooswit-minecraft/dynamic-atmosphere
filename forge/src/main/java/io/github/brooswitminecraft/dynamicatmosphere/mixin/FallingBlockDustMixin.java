package io.github.brooswitminecraft.dynamicatmosphere.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brooswitminecraft.dynamicatmosphere.FallingBlockDustProducer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallingBlockEntity.class)
abstract class FallingBlockDustMixin {

    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")
    )
    private boolean dynamicAtmosphere$emitDustAfterLanding(boolean placed, @Local BlockPos blockpos) {
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        if (placed && entity.level() instanceof ServerLevel level) {
            FallingBlockDustProducer.landed(level, blockpos);
        }
        return placed;
    }
}
