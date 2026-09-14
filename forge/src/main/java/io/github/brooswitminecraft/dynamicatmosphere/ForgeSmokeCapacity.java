package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Non-persistent smoke-capacity cache attached to loaded chunk identity. */
public final class ForgeSmokeCapacity {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DynamicAtmosphereMod.MODID);
    private static final Supplier<AttachmentType<SmokeCapacityCache>> CACHE = TYPES.register("smoke_capacity_cache", () ->
        AttachmentType.builder(holder -> {
            LevelChunk chunk = (LevelChunk) holder;
            return new SmokeCapacityCache(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        }).build());

    public static void register(IEventBus bus) { TYPES.register(bus); }

    static SmokeCapacityCache get(LevelChunk chunk) { return chunk.getData(CACHE.get()); }

    public static void blockChanged(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState next) {
        if (previous == null || chunk.getLevel().isClientSide) return;
        var cache = chunk.getExistingDataOrNull(CACHE.get());
        if (cache != null) cache.blockChanged(pos.getX(), pos.getY(), pos.getZ(),
            ForgeCapacityBlockClassifier.isEmptySpace(previous), ForgeCapacityBlockClassifier.isEmptySpace(next),
            ForgeCapacityBlockClassifier.blocksDownwardTransfer(previous),
            ForgeCapacityBlockClassifier.blocksDownwardTransfer(next));
    }

    static void clear(LevelChunk chunk) {
        var cache = chunk.getExistingDataOrNull(CACHE.get());
        if (cache != null) cache.clear();
    }

    private ForgeSmokeCapacity() { }
}
