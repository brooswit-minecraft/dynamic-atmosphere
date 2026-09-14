package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/** Server-thread cache, attached to chunk identity and deliberately not serialized. */
public final class ForgeAtmosphereCapacity {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DynamicAtmosphereMod.MODID);
    private static final Supplier<AttachmentType<AtmosphereCapacityCache>> CACHE = TYPES.register("capacity_cache", () ->
        AttachmentType.builder(holder -> {
            LevelChunk chunk = (LevelChunk) holder;
            return new AtmosphereCapacityCache(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        }).build());

    public static void register(IEventBus bus) { TYPES.register(bus); }

    static AtmosphereCapacityCache get(LevelChunk chunk) { return chunk.getData(CACHE.get()); }

    public static void blockChanged(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState next) {
        if (previous == null || chunk.getLevel().isClientSide || previous.isAir() == next.isAir()) return;
        var cache = chunk.getExistingDataOrNull(CACHE.get());
        if (cache != null) cache.blockChanged(pos.getX(), pos.getY(), pos.getZ(), previous.isAir(), next.isAir());
    }

    static void clear(LevelChunk chunk) {
        var cache = chunk.getExistingDataOrNull(CACHE.get());
        if (cache != null) cache.clear();
    }

    private ForgeAtmosphereCapacity() { }
}
