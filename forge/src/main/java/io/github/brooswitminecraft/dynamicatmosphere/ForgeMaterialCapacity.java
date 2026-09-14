package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** Lazily allocated terrain caches for additional materials on loaded chunks. */
public final class ForgeMaterialCapacity {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DynamicAtmosphereMod.MODID);
    private static final Supplier<AttachmentType<Map<AtmosphereMaterial, MaterialCapacityCache>>> CACHE =
        TYPES.register("material_capacity_cache", () -> AttachmentType
            .<Map<AtmosphereMaterial, MaterialCapacityCache>>builder(holder ->
            new EnumMap<AtmosphereMaterial, MaterialCapacityCache>(AtmosphereMaterial.class)).build());

    public static void register(IEventBus bus) { TYPES.register(bus); }

    static MaterialCapacityCache get(LevelChunk chunk, AtmosphereMaterial material) {
        return chunk.getData(CACHE.get()).computeIfAbsent(material,
            ignored -> new MaterialCapacityCache(material, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight()));
    }

    public static void blockChanged(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState next) {
        if (previous == null || chunk.getLevel().isClientSide) return;
        Map<AtmosphereMaterial, MaterialCapacityCache> caches = chunk.getExistingDataOrNull(CACHE.get());
        if (caches == null) return;
        for (MaterialCapacityCache cache : caches.values()) {
            cache.blockChanged(pos.getX(), pos.getY(), pos.getZ(), previous.isAir(), next.isAir(),
                previous.is(Blocks.BEDROCK), next.is(Blocks.BEDROCK));
        }
    }

    static void clear(LevelChunk chunk) {
        Map<AtmosphereMaterial, MaterialCapacityCache> caches = chunk.getExistingDataOrNull(CACHE.get());
        if (caches != null) caches.values().forEach(MaterialCapacityCache::clear);
    }

    private ForgeMaterialCapacity() { }
}
