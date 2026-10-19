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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MINECRAFT-437 round-2 review follow-up: the round-1 same-coordinate-incarnation test in
 * {@link ForgeMaterialStorageProductionChunkTest} writes directly to two separate
 * {@code ProtoChunk}s without ever going through {@code ForgeAtmospherePrototype}'s real
 * lifecycle-boundary coordinator -- {@code ensureImported}, which, when a chunk unloads and a new
 * chunk object loads at the same coordinates, flushes the old incarnation's dirty state through
 * the real batched write path, evicts its grid entries, and only then imports the new
 * incarnation's own stored cells.
 *
 * <p>{@code ensureImported} itself needs a real {@code ServerLevel} (via
 * {@code level.getChunkSource().getChunkNow(...)}) purely to locate the chunk object -- this
 * repo's test suite has no {@code ServerLevel} stand-in anywhere (confirmed across the whole
 * {@code forge/src/test} tree; see {@code ForgeAtmospherePrototypeFreezeGateTest}'s class
 * Javadoc), so driving the full method end to end is not feasible here. What IS feasible, and
 * what this suite exercises directly, is {@link ForgeAtmospherePrototype#reimportMaterialCells},
 * the material-persistence half of that swap sequence extracted specifically so it needs no
 * {@code ChunkState}/{@code LevelChunk}/{@code ServerLevel} -- {@code ensureImported} calls it,
 * unchanged, for every material on every real-production chunk import. This suite drives it with
 * two genuine {@link ProtoChunk}-backed {@link ChunkAccess} incarnations at the same coordinates,
 * combined with the real {@link ForgeMaterialStorage#writeBatch}/{@link AtmosphereGrid} dirty-key
 * machinery for the preceding flush, proving both review-flagged properties: (a) the old
 * incarnation's dirty cells are actually persisted (readable back from real storage) before the
 * swap, and (b) the new incarnation's import pulls only its own chunk's stored cells, never
 * anything carried over from the old incarnation's in-memory grid state.
 */
class MaterialIncarnationSwapSeamTest {
    private static final AtmosphereMaterial MATERIAL = AtmosphereMaterial.DUST;

    private static Registry<Biome> biomeRegistry;
    private static LevelHeightAccessor height;
    private static ResourceKey<Level> dimension;

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // Same minimal standalone Registry<Biome> pattern as ForgeMaterialStorageProductionChunkTest;
        // ProtoChunk's constructor requires one and biomes are otherwise only available behind a
        // full server/RegistryAccess.
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
        dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

        ForgeMaterialStorage.registerForTesting();
    }

    private static ChunkAccess newChunk(int x, int z) {
        return new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, height, biomeRegistry, null);
    }

    private static MaterialChunkData.Cell cell(int chunkX, int chunkZ, int amount) {
        return new MaterialChunkData.Cell(
            MATERIAL.cellCoordinate(chunkX * 16), MATERIAL.cellCoordinate(0), MATERIAL.cellCoordinate(chunkZ * 16),
            amount);
    }

    /** A cell anchored inside chunk ({@code chunkX}, {@code chunkZ}) at a chosen block Y, so two cells in the
     * same chunk column can land at distinct {@code AtmosphereGrid.CellKey}s (one chunk column spans several
     * vertical cells, since the material grid's cell height is smaller than the ~448-block world height). */
    private static MaterialChunkData.Cell cellAtY(AtmosphereMaterial material, int chunkX, int blockY, int chunkZ, int amount) {
        return new MaterialChunkData.Cell(
            material.cellCoordinate(chunkX * 16), material.cellCoordinate(blockY), material.cellCoordinate(chunkZ * 16),
            amount);
    }

    private static AtmosphereGrid.CellKey<ResourceKey<Level>> cellKey(MaterialChunkData.Cell c) {
        return new AtmosphereGrid.CellKey<>(dimension, c.x(), c.y(), c.z());
    }

    /**
     * Drives the real swap sequence a chunk-unload-then-reload-at-the-same-coordinates event
     * produces: (1) the old incarnation has a dirty live-grid cell that was never flushed yet --
     * flush it through the real {@code ForgeMaterialStorage.writeBatch} path (exactly what
     * {@code ensureImported}'s preceding {@code flushMaterialDirtyChunks} call does for a dirty
     * old incarnation), confirming via a real storage read-back that it actually persisted; then
     * (2) call the real {@link ForgeAtmospherePrototype#reimportMaterialCells} to evict the old
     * incarnation and import the new one -- the new incarnation's own stored cell lives at a
     * DIFFERENT block Y than the old incarnation's cell (same chunk column, different vertical
     * cell), so the two never share a single {@code CellKey} and a genuine leak (the old cell
     * surviving into the new incarnation's live state) is directly observable, not masked by one
     * cell overwriting the other at an identical key.
     */
    @Test
    void oldIncarnationFlushesBeforeSwapAndNewIncarnationImportsOnlyItsOwnCells() {
        var grid = new AtmosphereGrid<ResourceKey<Level>>();
        ChunkAccess oldChunk = newChunk(7, 7);
        ChunkAccess newChunk = newChunk(7, 7); // a brand new object at the identical coordinates

        // The old incarnation has an in-memory cell that is dirty (set, never yet flushed).
        MaterialChunkData.Cell oldCell = cellAtY(MATERIAL, 7, 0, 7, 99);
        var oldCellKey = cellKey(oldCell);
        grid.set(oldCellKey, oldCell.amount(), 0L);
        Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, MaterialChunkData.Cell> oldPersistedCells =
            new LinkedHashMap<>();

        // Step 1: the real flush -- collect dirty keys for the old incarnation's own chunk and
        // publish via the real production writeBatch entry point, exactly as
        // ForgeAtmospherePrototype#flushMaterialDirtyChunks does per material.
        ForgeAtmospherePrototype.ChunkKey oldChunkKey = new ForgeAtmospherePrototype.ChunkKey(dimension, 7, 7);
        Map<ForgeAtmospherePrototype.ChunkKey, List<MaterialChunkData.Cell>> updates =
            ForgeAtmospherePrototype.collectMaterialDirtyChunkUpdates(
                grid.drainDirtyKeys(),
                key -> oldChunkKey,
                candidate -> true,
                key -> {
                    var liveCell = grid.get(key);
                    return liveCell == null ? null : liveCell.amount();
                },
                candidate -> oldPersistedCells,
                List::copyOf);
        assertEquals(1, updates.size(), "sanity: exactly the old incarnation's own chunk was touched");
        ForgeMaterialStorage.writeBatch(oldChunk, Map.of(MATERIAL, updates.get(oldChunkKey)));

        // Confirm, via a real storage read-back, that the old incarnation's dirty state was
        // actually persisted BEFORE anything about the swap happens.
        assertEquals(List.of(oldCell), ForgeMaterialStorage.read(oldChunk, MATERIAL),
            "the old incarnation's dirty cell must be flushed through the real write path before the swap");

        // The new incarnation separately has its own, different, already-stored cell at a
        // different vertical cell within the same chunk column -- it shares no CellKey with the
        // old incarnation's cell, and it is never written to oldChunk.
        MaterialChunkData.Cell newCell = cellAtY(MATERIAL, 7, AtmosphereGrid.CELL_SIZE, 7, 5);
        var newCellKey = cellKey(newCell);
        assertFalse(newCellKey.equals(oldCellKey), "sanity: the two cells must land at distinct CellKeys");
        ForgeMaterialStorage.writeBatch(newChunk, Map.of(MATERIAL, List.of(newCell)));

        // Step 2: the real production swap seam -- evict the old incarnation, import the new one.
        Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, MaterialChunkData.Cell> newPersistedCells =
            new LinkedHashMap<>();
        ForgeAtmospherePrototype.reimportMaterialCells(grid, dimension, newChunk, MATERIAL,
            oldPersistedCells, newPersistedCells, 0L);

        // Step 3: the new incarnation's grid/persisted-cells state reflects ONLY its own cell --
        // the old incarnation's cell is gone from the live grid (a leak would leave it present,
        // falsely attributed to the new incarnation, since import never explicitly deletes keys
        // absent from the new chunk's own storage -- only eviction does).
        assertNull(grid.get(oldCellKey), "the old incarnation's cell must be evicted from the live grid");
        assertEquals(newCell.amount(), grid.get(newCellKey).amount(),
            "the new incarnation's own stored cell must be restored into the live grid");
        assertEquals(Map.of(newCellKey, newCell), newPersistedCells,
            "the new incarnation's persisted-cells map must contain only its own cell, nothing from the old one");
        assertFalse(newPersistedCells.containsKey(oldCellKey),
            "nothing from the old incarnation must bleed into the new incarnation's persisted cells");
    }

    /** No old incarnation (a first import, previousMaterialCells == null) must skip eviction and just import. */
    @Test
    void firstImportWithNoPreviousIncarnationSkipsEvictionAndImportsCleanly() {
        var grid = new AtmosphereGrid<ResourceKey<Level>>();
        ChunkAccess chunk = newChunk(8, 8);
        MaterialChunkData.Cell storedCell = cell(8, 8, 17);
        ForgeMaterialStorage.writeBatch(chunk, Map.of(MATERIAL, List.of(storedCell)));

        Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, MaterialChunkData.Cell> newPersistedCells =
            new LinkedHashMap<>();
        ForgeAtmospherePrototype.reimportMaterialCells(grid, dimension, chunk, MATERIAL,
            null, newPersistedCells, 0L);

        var key = cellKey(storedCell);
        assertEquals(storedCell.amount(), grid.get(key).amount());
        assertEquals(Map.of(key, storedCell), newPersistedCells);
    }
}
