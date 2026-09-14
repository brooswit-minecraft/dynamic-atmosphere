package io.github.brooswitminecraft.dynamicatmosphere.mixin;

import io.github.brooswitminecraft.dynamicatmosphere.ForgeSmokeGameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
abstract class ExplosionSmokeMixin {
    @Shadow @Final private Level level;
    @Unique private boolean dynamicAtmosphere$smokeEmitted;

    // Finalization is reached only for accepted explosions, including explosions that affect no blocks.
    @Inject(method = "finalizeExplosion(Z)V", at = @At("HEAD"))
    private void dynamicAtmosphere$burst(boolean particles, CallbackInfo callback) {
        if (dynamicAtmosphere$smokeEmitted) return;
        dynamicAtmosphere$smokeEmitted = true;
        ForgeSmokeGameplay.explosionBurst(level, BlockPos.containing(((Explosion) (Object) this).center()));
    }
}
