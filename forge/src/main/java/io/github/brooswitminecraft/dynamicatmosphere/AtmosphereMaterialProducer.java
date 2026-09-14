package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** A bounded hook invoked only for chunks selected by the shared producer scheduler. */
@FunctionalInterface
public interface AtmosphereMaterialProducer {
    void sampleLoadedChunk(ServerLevel level, LevelChunk chunk);
}
