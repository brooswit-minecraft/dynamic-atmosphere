package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A bounded hook invoked only for chunks selected by the shared producer scheduler.
 *
 * <p>MINECRAFT-435: returns {@code true} once this call has fully handled the chunk, or {@code
 * false} if bounded work remains and the chunk must be revisited (the scheduler requeues it,
 * fairly, behind any other already-pending chunks) before this visit is considered complete. A
 * hook whose work is always O(1) per visit should simply always return {@code true}.</p>
 */
@FunctionalInterface
public interface AtmosphereMaterialProducer {
    boolean sampleLoadedChunk(ServerLevel level, LevelChunk chunk);
}
