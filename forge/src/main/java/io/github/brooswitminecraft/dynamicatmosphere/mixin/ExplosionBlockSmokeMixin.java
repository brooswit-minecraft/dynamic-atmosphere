package io.github.brooswitminecraft.dynamicatmosphere.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brooswitminecraft.dynamicatmosphere.ForgeSmokeGameplay;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class ExplosionBlockSmokeMixin {
    @WrapMethod(method = "onExplosionHit")
    private void dynamicAtmosphere$destroyedBlock(Level level, BlockPos pos, Explosion explosion,
        BiConsumer<ItemStack, BlockPos> drops, Operation<Void> original) {
        BlockState previous = null;
        if (level instanceof ServerLevel server && server.getServer().isSameThread()) {
            var chunk = server.getChunkSource().getChunkNow(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
            if (chunk != null) previous = chunk.getBlockState(pos);
        }
        original.call(level, pos, explosion, drops);
        ForgeSmokeGameplay.explosionBlock(level, pos, previous);
    }
}
