package io.github.brooswitminecraft.dynamicatmosphere.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereFluidTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FlowingFluid.class)
abstract class FlowingFluidTransportMixin {
    // WrapMethod includes Flowing Fluids 1.0.6's cancellable HEAD injection into tick.
    @WrapMethod(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)V")
    private void dynamicAtmosphere$transport(Level level, BlockPos pos, FluidState state, Operation<Void> original) {
        AtmosphereFluidTransport.enter();
        try {
            original.call(level, pos, state);
        } finally {
            AtmosphereFluidTransport.exit();
        }
    }
}
