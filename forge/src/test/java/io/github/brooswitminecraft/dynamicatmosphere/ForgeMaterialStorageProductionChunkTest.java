package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-437 review follow-up: {@code MaterialChunkDataBatchMergeTest} only exercises the pure
 * {@link MaterialChunkData#mergeBatch} function with hand-built maps -- it never goes through the
 * real {@link ForgeMaterialStorage#writeBatch}/{@link ForgeMaterialStorage#read} entry points
 * against an actual chunk attachment, so it never exercises the real
 * {@code setData}/{@code getExistingDataOrNull} {@code IAttachmentHolder} machinery, or the NBT
 * {@code IAttachmentSerializer.write()}/{@code .read()} round trip the attachment system performs
 * when a chunk is actually saved and reloaded.
 *
 * <p>This suite closes that gap using {@link ProtoChunk} -- a genuine {@link ChunkAccess}
 * implementation that NeoForge's generation pipeline constructs with just a {@link ChunkPos} and
 * {@link UpgradeData}, with no {@code Level}/{@code ServerLevel}/{@code MinecraftServer} required.
 * Because {@code ChunkAccess} itself (not {@code LevelChunk}) implements {@code IAttachmentHolder}
 * via a backing {@code AttachmentHolder.AsField}, a {@code ProtoChunk} goes through the exact same
 * production attachment code as a real loaded chunk would: {@code setData}, {@code
 * getExistingDataOrNull}, {@code setUnsaved}, and -- via {@link ChunkAccess#writeAttachmentsToNBT}
 * / {@link ChunkAccess#readAttachmentsFromNBT} -- the real {@code AttachmentType}'s
 * {@code IAttachmentSerializer.write()}/{@code .read()} pair that {@code ForgeMaterialStorage}'s
 * {@code AttachmentType.builder(...).serialize(...)} registers. {@code ForgeMaterialStorage.write}/
 * {@code writeBatch}/{@code read} were widened from {@code LevelChunk} to {@code ChunkAccess} to
 * make this possible; every existing production call site already passes a {@code LevelChunk},
 * which still satisfies {@code ChunkAccess} unchanged.
 *
 * <p>What this suite does NOT cover: an actual loaded, ticking {@code ServerLevel} chunk, or disk
 * region-file I/O. The registry-and-bootstrap setup below is the same pattern
 * {@code ForgeCapacityBlockClassifierTest} already uses; the one extra step is constructing and
 * freezing a minimal {@code Registry<Biome>} (required by {@code ProtoChunk}'s constructor) and
 * directly registering {@code ForgeMaterialStorage}'s real {@code AttachmentType} into {@code
 * NeoForgeRegistries.ATTACHMENT_TYPES} ourselves, since no mod-loading {@code RegisterEvent} ever
 * fires in a plain unit test to do that for us -- without an entry there, NeoForge's dev-only
 * {@code validateAttachmentType} check throws before any attachment operation can run.
 */
class ForgeMaterialStorageProductionChunkTest {
    private static final AtmosphereMaterial A = AtmosphereMaterial.DUST;
    private static final AtmosphereMaterial B = AtmosphereMaterial.OBSIDIAN_POWDER;

    private static Registry<Biome> biomeRegistry;
    private static LevelHeightAccessor height;

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // ProtoChunk's constructor needs a Registry<Biome>; biomes are normally a dynamic,
        // datapack-driven registry only available behind a full server/RegistryAccess, so build
        // and freeze the smallest standalone one ourselves.
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

        // See ForgeMaterialStorage.registerForTesting()'s Javadoc: a plain unit test never fires
        // the mod-loading RegisterEvent that production's register(bus) hooks, so this directly
        // (and idempotently) seeds the same real attachment type into the registry under the
        // same key, letting DATA.get() resolve exactly as it would in production.
        ForgeMaterialStorage.registerForTesting();
    }

    private static ChunkAccess newChunk(int x, int z) {
        return new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, height, biomeRegistry, null);
    }

    /** A valid cell for the material's own cellSize grid, anchored inside chunk ({@code chunkX}, {@code chunkZ}). */
    private static MaterialChunkData.Cell cell(AtmosphereMaterial material, int chunkX, int chunkZ, int amount) {
        return new MaterialChunkData.Cell(
            material.cellCoordinate(chunkX * 16), material.cellCoordinate(0), material.cellCoordinate(chunkZ * 16),
            amount);
    }

    /** Round trip: writeBatch, then read -- confirming exact stored amounts for every material in the batch. */
    @Test
    void writeBatchThenReadReturnsExactStoredAmountsForEveryMaterial() {
        ChunkAccess chunk = newChunk(0, 0);
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = Map.of(
            A, List.of(cell(A, 0, 0, 10)),
            B, List.of(cell(B, 0, 0, 20)));

        Map<AtmosphereMaterial, IllegalStateException> failures = ForgeMaterialStorage.writeBatch(chunk, updates);

        assertTrue(failures.isEmpty());
        assertEquals(List.of(cell(A, 0, 0, 10)), ForgeMaterialStorage.read(chunk, A));
        assertEquals(List.of(cell(B, 0, 0, 20)), ForgeMaterialStorage.read(chunk, B));
    }

    /** Deletion: a batch that empties a previously-stored material actually removes it on read-back. */
    @Test
    void batchThatEmptiesAPreviouslyStoredMaterialRemovesItOnReadBack() {
        ChunkAccess chunk = newChunk(1, 0);
        ForgeMaterialStorage.writeBatch(chunk, Map.of(A, List.of(cell(A, 1, 0, 10))));
        assertEquals(List.of(cell(A, 1, 0, 10)), ForgeMaterialStorage.read(chunk, A));

        ForgeMaterialStorage.writeBatch(chunk, Map.of(A, List.of()));

        assertEquals(List.of(), ForgeMaterialStorage.read(chunk, A));
    }

    /**
     * Same-coordinate replacement: two separate chunk incarnations at the same coordinates (as
     * happens when a chunk unloads and a new one loads at the same position) must never leak into
     * each other -- mirrors what {@code ForgeAtmospherePrototype#ensureImported} relies on.
     */
    @Test
    void twoIncarnationsAtTheSameCoordinatesDoNotLeakIntoEachOther() {
        ChunkAccess firstIncarnation = newChunk(5, 5);
        ForgeMaterialStorage.writeBatch(firstIncarnation, Map.of(A, List.of(cell(A, 5, 5, 42))));
        assertEquals(List.of(cell(A, 5, 5, 42)), ForgeMaterialStorage.read(firstIncarnation, A));

        // A brand new object at the identical chunk coordinates -- nothing is shared with it.
        ChunkAccess secondIncarnation = newChunk(5, 5);

        assertEquals(List.of(), ForgeMaterialStorage.read(secondIncarnation, A),
            "a new incarnation at the same coordinates must not see the old incarnation's data");
        ForgeMaterialStorage.writeBatch(secondIncarnation, Map.of(A, List.of(cell(A, 5, 5, 7))));
        assertEquals(List.of(cell(A, 5, 5, 7)), ForgeMaterialStorage.read(secondIncarnation, A));
        assertEquals(List.of(cell(A, 5, 5, 42)), ForgeMaterialStorage.read(firstIncarnation, A),
            "writing to the new incarnation must not retroactively change the old one");
    }

    /**
     * Corrupt-plus-valid partial failure: a material already stored as unreadable, batched
     * alongside a valid update to a different material -- the valid one publishes, the corrupt one
     * is reported as a failure without being overwritten, exercised through the real
     * {@code setData}/{@code getExistingDataOrNull} path (not just pure {@code mergeBatch}).
     */
    @Test
    void corruptMaterialIsReportedAsFailureWithoutBlockingAValidUpdateToAnotherMaterial() {
        ChunkAccess chunk = newChunk(2, 0);
        CompoundTag corruptTag = new CompoundTag();
        corruptTag.putString("bogus", "data");
        corruptTag.putInt("version", MaterialChunkData.VERSION);
        corruptTag.putInt("cell_size", A.cellSize());
        // No "cells" array under the current version -- MaterialChunkData.decodeStored treats
        // this as corrupt/unreadable, not stale, exactly like ForgeMaterialStorageStaleVersionTest
        // pins for the pure decoder.
        chunk.setUnsaved(true);
        writeRawAttachmentNbt(chunk, A, corruptTag);
        assertThrowsOnReadingTheCorruptMaterial(chunk); // sanity: corrupt before the batch, too

        Map<AtmosphereMaterial, IllegalStateException> failures =
            ForgeMaterialStorage.writeBatch(chunk, Map.of(A, List.of(cell(A, 2, 0, 99)), B, List.of(cell(B, 2, 0, 55))));

        assertEquals(1, failures.size());
        assertTrue(failures.containsKey(A));
        assertEquals(List.of(cell(B, 2, 0, 55)), ForgeMaterialStorage.read(chunk, B),
            "the valid material in the same batch must still publish");
        assertThrowsOnReadingTheCorruptMaterial(chunk);
    }

    private static void assertThrowsOnReadingTheCorruptMaterial(ChunkAccess chunk) {
        try {
            ForgeMaterialStorage.read(chunk, A);
            throw new AssertionError("expected the corrupt material to still be reported unreadable");
        } catch (IllegalStateException expected) {
            // Matches the single-material write/read path's contract for retained-corrupt data.
        }
    }

    /** Publish/no-op counts: an unchanged batch publishes nothing; a batch with one real change publishes exactly once. */
    @Test
    void unchangedBatchDoesNotPublishWhileARealChangeProducesExactlyOnePublication() {
        ChunkAccess chunk = newChunk(3, 0);
        ForgeMaterialStorage.writeBatch(chunk, Map.of(A, List.of(cell(A, 3, 0, 10)), B, List.of(cell(B, 3, 0, 20))));
        boolean unsavedBeforeNoOp = chunk.isUnsaved();
        chunk.setUnsaved(false);

        // Re-submitting byte-for-byte the same cells for both materials must not republish.
        Map<AtmosphereMaterial, IllegalStateException> noOpFailures =
            ForgeMaterialStorage.writeBatch(chunk, Map.of(A, List.of(cell(A, 3, 0, 10)), B, List.of(cell(B, 3, 0, 20))));

        assertTrue(noOpFailures.isEmpty());
        assertFalse(chunk.isUnsaved(), "an unchanged batch must not call setData/mark the chunk unsaved");
        assertTrue(unsavedBeforeNoOp, "sanity: the first, real write above did mark the chunk unsaved");

        // One real change across the whole chunk -- still exactly one publication, not one per
        // material, even though both materials are present in the batch.
        Map<AtmosphereMaterial, IllegalStateException> realChangeFailures =
            ForgeMaterialStorage.writeBatch(chunk, Map.of(A, List.of(cell(A, 3, 0, 11)), B, List.of(cell(B, 3, 0, 20))));

        assertTrue(realChangeFailures.isEmpty());
        assertTrue(chunk.isUnsaved());
        assertEquals(List.of(cell(A, 3, 0, 11)), ForgeMaterialStorage.read(chunk, A));
        assertEquals(List.of(cell(B, 3, 0, 20)), ForgeMaterialStorage.read(chunk, B));
    }

    /** Publish/serialize/reload: the NBT IAttachmentSerializer write()/read() round trip through a reloaded incarnation. */
    @Test
    void writeBatchSurvivesAFullNbtSerializeAndReloadIntoANewIncarnation() {
        ChunkAccess chunk = newChunk(4, 0);
        ForgeMaterialStorage.writeBatch(chunk, Map.of(A, List.of(cell(A, 4, 0, 33)), B, List.of(cell(B, 4, 0, 44))));

        CompoundTag serialized = chunk.writeAttachmentsToNBT(RegistryAccess.EMPTY);

        ChunkAccess reloaded = newChunk(4, 0);
        reloaded.readAttachmentsFromNBT(RegistryAccess.EMPTY, serialized);

        assertEquals(List.of(cell(A, 4, 0, 33)), ForgeMaterialStorage.read(reloaded, A));
        assertEquals(List.of(cell(B, 4, 0, 44)), ForgeMaterialStorage.read(reloaded, B));
    }

    private static void writeRawAttachmentNbt(ChunkAccess chunk, AtmosphereMaterial material, CompoundTag materialTag) {
        // Round-trips through the real IAttachmentSerializer.read() so the corrupt tag is
        // retained exactly the way MaterialChunkData.decodeStored would see it after a genuine
        // reload, rather than poking chunk.setData with a hand-built Snapshot directly.
        CompoundTag root = new CompoundTag();
        root.put(material.id(), materialTag);
        chunk.readAttachmentsFromNBT(RegistryAccess.EMPTY, wrapAsAttachmentsTag(root));
    }

    private static CompoundTag wrapAsAttachmentsTag(CompoundTag materialsRoot) {
        CompoundTag attachments = new CompoundTag();
        attachments.put(DynamicAtmosphereMod.MODID + ":materials", materialsRoot);
        return attachments;
    }
}
