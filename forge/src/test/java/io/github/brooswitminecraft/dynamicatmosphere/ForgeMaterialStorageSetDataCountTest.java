package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-437 round-2 review follow-up: the existing
 * {@code ForgeMaterialStorageProductionChunkTest#unchangedBatchDoesNotPublishWhileARealChangeProducesExactlyOnePublication}
 * test only asserts {@code chunk.isUnsaved()}, a boolean -- it cannot distinguish "published once"
 * from "published N times," so it could not have caught a regression that republished per
 * material inside one {@code writeBatch} call instead of once per batch.
 *
 * <p>{@code ChunkAccess}/{@code ProtoChunk} expose no built-in call counter for
 * {@code IAttachmentHolder.setData}, but {@code ChunkAccess.setData} (see
 * {@code net.minecraft.world.level.chunk.ChunkAccess}) is a plain, non-final, public instance
 * method -- not a default method dispatched through a hidden internal holder -- so a thin
 * pass-through {@code ProtoChunk} subclass can override it, increment a counter, and delegate to
 * {@code super.setData(...)} for the real behavior. This counts the exact, real {@code setData}
 * invocations {@link ForgeMaterialStorage#writeBatch} makes -- through the genuine production
 * entry point and the genuine {@code IAttachmentHolder} machinery -- with no reimplementation of
 * the counting logic itself.
 */
class ForgeMaterialStorageSetDataCountTest {
    private static final AtmosphereMaterial A = AtmosphereMaterial.DUST;
    private static final AtmosphereMaterial B = AtmosphereMaterial.OBSIDIAN_POWDER;
    private static final AtmosphereMaterial C = AtmosphereMaterial.VOID_GAS;

    private static Registry<Biome> biomeRegistry;
    private static LevelHeightAccessor height;

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Biome plains = new Biome.BiomeBuilder()
            .hasPrecipitation(false)
            .temperature(0.5f)
            .downfall(0.5f)
            .specialEffects(new BiomeSpecialEffects.Builder()
                .fogColor(0xC0D8FF)
                .waterColor(0x3F76E4)
                .waterFogColor(0x050533)
                .skyColor(0x78A7FF)
                .build())
            .mobSpawnSettings(MobSpawnSettings.EMPTY)
            .generationSettings(BiomeGenerationSettings.EMPTY)
            .build();
        registry.register(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("plains")),
            plains, RegistrationInfo.BUILT_IN);
        registry.freeze();
        biomeRegistry = registry;
        height = LevelHeightAccessor.create(-64, 384);

        ForgeMaterialStorage.registerForTesting();
    }

    /** A real {@link ProtoChunk} whose only change from production behavior is counting {@code setData} calls. */
    private static final class CountingChunk extends ProtoChunk {
        private final AtomicInteger setDataCalls = new AtomicInteger();

        CountingChunk(ChunkPos pos, LevelHeightAccessor height, Registry<Biome> biomeRegistry) {
            super(pos, UpgradeData.EMPTY, height, biomeRegistry, (BlendingData) null);
        }

        @Override
        @Nullable
        public <T> T setData(AttachmentType<T> type, T data) {
            setDataCalls.incrementAndGet();
            return super.setData(type, data);
        }

        int setDataCallCount() {
            return setDataCalls.get();
        }
    }

    private static MaterialChunkData.Cell cell(AtmosphereMaterial material, int chunkX, int chunkZ, int amount) {
        return new MaterialChunkData.Cell(
            material.cellCoordinate(chunkX * 16), material.cellCoordinate(0), material.cellCoordinate(chunkZ * 16),
            amount);
    }

    /**
     * Two materials changed in the same {@code writeBatch} call must result in exactly ONE real
     * {@code setData} invocation -- a single coherent snapshot publish -- not two (one per
     * material), which is precisely the batching regression this ticket exists to prevent.
     */
    @Test
    void writeBatchWithTwoChangedMaterialsCallsSetDataExactlyOnce() {
        CountingChunk chunk = new CountingChunk(new ChunkPos(9, 9), height, biomeRegistry);

        Map<AtmosphereMaterial, IllegalStateException> failures = ForgeMaterialStorage.writeBatch(chunk, Map.of(
            A, List.of(cell(A, 9, 9, 10)),
            B, List.of(cell(B, 9, 9, 20))));

        assertTrue(failures.isEmpty());
        assertEquals(1, chunk.setDataCallCount(),
            "two materials changed in one batch must publish as exactly one setData call, not two");
        assertEquals(List.of(cell(A, 9, 9, 10)), ForgeMaterialStorage.read(chunk, A));
        assertEquals(List.of(cell(B, 9, 9, 20)), ForgeMaterialStorage.read(chunk, B));
    }

    /** An unchanged re-submission of the same batch must call setData zero additional times. */
    @Test
    void unchangedBatchCallsSetDataZeroAdditionalTimes() {
        CountingChunk chunk = new CountingChunk(new ChunkPos(10, 9), height, biomeRegistry);
        ForgeMaterialStorage.writeBatch(chunk, Map.of(
            A, List.of(cell(A, 10, 9, 10)),
            B, List.of(cell(B, 10, 9, 20))));
        assertEquals(1, chunk.setDataCallCount(), "sanity: the first, real write did call setData once");

        ForgeMaterialStorage.writeBatch(chunk, Map.of(
            A, List.of(cell(A, 10, 9, 10)),
            B, List.of(cell(B, 10, 9, 20))));

        assertEquals(1, chunk.setDataCallCount(),
            "re-submitting byte-for-byte the same batch must not call setData again");
    }

    /** Three materials all changing in one batch still produce exactly one setData call, not three. */
    @Test
    void writeBatchWithThreeChangedMaterialsStillCallsSetDataExactlyOnce() {
        CountingChunk chunk = new CountingChunk(new ChunkPos(11, 9), height, biomeRegistry);

        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = Map.of(
            A, List.of(cell(A, 11, 9, 1)),
            B, List.of(cell(B, 11, 9, 2)),
            C, List.of(cell(C, 11, 9, 3)));

        Map<AtmosphereMaterial, IllegalStateException> failures = ForgeMaterialStorage.writeBatch(chunk, updates);

        assertTrue(failures.isEmpty());
        assertEquals(1, chunk.setDataCallCount(),
            "three materials changed in one batch must still publish as exactly one setData call");
    }
}
