package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Emits one conservative Dust burst after a falling block is successfully placed. */
public final class FallingBlockDustProducer {
    public static final int LANDING_AMOUNT = 24;

    public static void landed(ServerLevel level, BlockPos pos) {
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.DUST, pos,
            DynamicAtmosphereServerConfig.snapshot().dust().fallingBlockEmission());
    }

    private FallingBlockDustProducer() { }
}
