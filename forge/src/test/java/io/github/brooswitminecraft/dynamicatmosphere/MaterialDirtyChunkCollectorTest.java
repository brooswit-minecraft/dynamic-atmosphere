package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the review finding on MINECRAFT-437 PR #81: the first cut of
 * {@code ForgeAtmospherePrototype#flushMaterialDirtyChunks} called the per-(chunk, material)
 * {@code List.copyOf} materialization INSIDE the per-dirty-key loop -- once per dirty CELL key,
 * not once per changed (chunk, material) pair -- which made that copy MORE frequent than before
 * batching for a chunk/material with several dirty cells in one flush, the opposite of the
 * ticket's intent. The fix (see {@code ForgeAtmospherePrototype#collectMaterialDirtyChunkUpdates})
 * drains all dirty keys for a material first, recording only which chunk each touched, and only
 * then materializes each touched chunk's cells exactly once.
 *
 * <p>This test drives {@code collectMaterialDirtyChunkUpdates} directly -- no {@code ChunkState},
 * {@code AtmosphereGrid}, or {@code LevelChunk} needed, since the method is dimension-type- and
 * chunk-structure-agnostic -- and counts {@code materialize} invocations with a plain counter
 * instead of any timing/profiling, satisfying the ticket's "no profiling or benchmark gate"
 * constraint while still deterministically pinning the exact call count.
 */
class MaterialDirtyChunkCollectorTest {
    private static final String DIMENSION = "overworld";

    private static ForgeAtmospherePrototype.ChunkKey chunkKey(int x, int z) {
        // The real record requires a ResourceKey<Level>; since this test never touches an actual
        // Level, a null dimension value is fine -- ChunkKey's equals/hashCode only need it to be
        // consistent, and every key built here uses the same constant.
        return new ForgeAtmospherePrototype.ChunkKey(fakeDimension(), x, z);
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> fakeDimension() {
        // A single shared sentinel so every ChunkKey in this test compares equal/hashes the same
        // way a real dimension key would; its identity, not its contents, is what matters here.
        return (ResourceKey<Level>) (ResourceKey<?>) DIMENSION_KEY;
    }

    private static final ResourceKey<?> DIMENSION_KEY = ResourceKey.create(
        net.minecraft.core.registries.Registries.DIMENSION,
        net.minecraft.resources.ResourceLocation.withDefaultNamespace(DIMENSION));

    /**
     * The regression itself: three dirty keys land in the SAME chunk for one material. Before the
     * fix, {@code materialize} (production: {@code List.copyOf}) ran three times (once per dirty
     * key); after the fix, it must run exactly once for that chunk, with the final result
     * reflecting all three keys' net effect.
     */
    @Test
    void threeDirtyKeysInOneChunkMaterializeExactlyOnce() {
        ForgeAtmospherePrototype.ChunkKey chunk = chunkKey(0, 0);
        var key1 = new AtmosphereGrid.CellKey<String>("d", 0, 0, 0);
        var key2 = new AtmosphereGrid.CellKey<String>("d", 1, 0, 0);
        var key3 = new AtmosphereGrid.CellKey<String>("d", 2, 0, 0);
        Map<AtmosphereGrid.CellKey<String>, Integer> liveAmounts = new HashMap<>();
        liveAmounts.put(key1, 10);
        liveAmounts.put(key2, 20);
        liveAmounts.put(key3, 30);

        Map<ForgeAtmospherePrototype.ChunkKey, Map<AtmosphereGrid.CellKey<String>, MaterialChunkData.Cell>>
            persistedCellsByChunk = new HashMap<>();
        AtomicInteger materializeCalls = new AtomicInteger();

        var result = ForgeAtmospherePrototype.collectMaterialDirtyChunkUpdates(
            List.of(key1, key2, key3),
            key -> chunk,
            candidate -> true,
            liveAmounts::get,
            candidate -> persistedCellsByChunk.computeIfAbsent(candidate, unused -> new LinkedHashMap<>()),
            cells -> {
                materializeCalls.incrementAndGet();
                return List.copyOf(cells);
            });

        assertEquals(1, materializeCalls.get(),
            "three dirty keys in the same chunk/material must materialize exactly once, not once per key");
        assertEquals(1, result.size());
        assertEquals(3, result.get(chunk).size(),
            "the single materialization must still reflect the net effect of all three dirty keys");
    }

    /**
     * Two chunks are touched by the same material's drained keys: materialize must run exactly
     * once per touched chunk (two total), never once per key (which would be four).
     */
    @Test
    void dirtyKeysAcrossTwoChunksMaterializeOncePerChunk() {
        ForgeAtmospherePrototype.ChunkKey chunkA = chunkKey(0, 0);
        ForgeAtmospherePrototype.ChunkKey chunkB = chunkKey(1, 0);
        var a1 = new AtmosphereGrid.CellKey<String>("d", 0, 0, 0);
        var a2 = new AtmosphereGrid.CellKey<String>("d", 1, 0, 0);
        var b1 = new AtmosphereGrid.CellKey<String>("d", 16, 0, 0);
        var b2 = new AtmosphereGrid.CellKey<String>("d", 17, 0, 0);
        Map<AtmosphereGrid.CellKey<String>, ForgeAtmospherePrototype.ChunkKey> owner =
            Map.of(a1, chunkA, a2, chunkA, b1, chunkB, b2, chunkB);
        Map<AtmosphereGrid.CellKey<String>, Integer> liveAmounts = Map.of(a1, 1, a2, 2, b1, 3, b2, 4);
        Map<ForgeAtmospherePrototype.ChunkKey, Map<AtmosphereGrid.CellKey<String>, MaterialChunkData.Cell>>
            persistedCellsByChunk = new HashMap<>();
        AtomicInteger materializeCalls = new AtomicInteger();

        var result = ForgeAtmospherePrototype.collectMaterialDirtyChunkUpdates(
            List.of(a1, a2, b1, b2),
            owner::get,
            candidate -> true,
            liveAmounts::get,
            candidate -> persistedCellsByChunk.computeIfAbsent(candidate, unused -> new LinkedHashMap<>()),
            cells -> {
                materializeCalls.incrementAndGet();
                return List.copyOf(cells);
            });

        assertEquals(2, materializeCalls.get(), "exactly one materialization per distinct touched chunk");
        assertEquals(Set.of(chunkA, chunkB), result.keySet());
        assertEquals(2, result.get(chunkA).size());
        assertEquals(2, result.get(chunkB).size());
    }

    /** A key whose chunk is rejected by isTrackedAndReadable never touches persistedCells or materialize. */
    @Test
    void untrackedOrUnreadableChunkIsSkippedEntirely() {
        ForgeAtmospherePrototype.ChunkKey trackedChunk = chunkKey(0, 0);
        ForgeAtmospherePrototype.ChunkKey untrackedChunk = chunkKey(99, 99);
        var trackedKey = new AtmosphereGrid.CellKey<String>("d", 0, 0, 0);
        var untrackedKey = new AtmosphereGrid.CellKey<String>("d", 99 * 16, 0, 99 * 16);
        Map<AtmosphereGrid.CellKey<String>, ForgeAtmospherePrototype.ChunkKey> owner =
            Map.of(trackedKey, trackedChunk, untrackedKey, untrackedChunk);
        Map<AtmosphereGrid.CellKey<String>, Integer> liveAmounts = Map.of(trackedKey, 5, untrackedKey, 7);
        AtomicInteger materializeCalls = new AtomicInteger();

        var result = ForgeAtmospherePrototype.collectMaterialDirtyChunkUpdates(
            List.of(trackedKey, untrackedKey),
            owner::get,
            candidate -> candidate.equals(trackedChunk),
            liveAmounts::get,
            candidate -> {
                if (candidate.equals(untrackedChunk)) {
                    throw new AssertionError("persistedCellsFor must never be called for a rejected chunk");
                }
                return new LinkedHashMap<>();
            },
            cells -> {
                materializeCalls.incrementAndGet();
                return List.copyOf(cells);
            });

        assertEquals(1, materializeCalls.get());
        assertEquals(Set.of(trackedChunk), result.keySet());
    }

    /** A key whose live amount is null (removed) deletes the persisted entry rather than inserting a Cell for it. */
    @Test
    void nullCurrentAmountRemovesThePersistedEntry() {
        ForgeAtmospherePrototype.ChunkKey chunk = chunkKey(0, 0);
        var key = new AtmosphereGrid.CellKey<String>("d", 0, 0, 0);
        Map<ForgeAtmospherePrototype.ChunkKey, Map<AtmosphereGrid.CellKey<String>, MaterialChunkData.Cell>>
            persistedCellsByChunk = new HashMap<>();
        persistedCellsByChunk.put(chunk, new LinkedHashMap<>(Map.of(key, new MaterialChunkData.Cell(0, 0, 0, 9))));
        AtomicInteger materializeCalls = new AtomicInteger();

        var result = ForgeAtmospherePrototype.collectMaterialDirtyChunkUpdates(
            List.of(key),
            k -> chunk,
            candidate -> true,
            k -> null,
            candidate -> persistedCellsByChunk.computeIfAbsent(candidate, unused -> new LinkedHashMap<>()),
            cells -> {
                materializeCalls.incrementAndGet();
                return List.copyOf(cells);
            });

        assertEquals(1, materializeCalls.get());
        assertEquals(List.of(), result.get(chunk));
        assertEquals(Map.of(), persistedCellsByChunk.get(chunk));
    }

    /** A key whose live amount exactly matches what's already persisted is not touched -- no chunk is recorded. */
    @Test
    void unchangedAmountTouchesNothing() {
        ForgeAtmospherePrototype.ChunkKey chunk = chunkKey(0, 0);
        var key = new AtmosphereGrid.CellKey<String>("d", 0, 0, 0);
        Map<ForgeAtmospherePrototype.ChunkKey, Map<AtmosphereGrid.CellKey<String>, MaterialChunkData.Cell>>
            persistedCellsByChunk = new HashMap<>();
        var existing = new LinkedHashMap<AtmosphereGrid.CellKey<String>, MaterialChunkData.Cell>();
        existing.put(key, new MaterialChunkData.Cell(0, 0, 0, 9));
        persistedCellsByChunk.put(chunk, existing);
        AtomicInteger materializeCalls = new AtomicInteger();

        var result = ForgeAtmospherePrototype.collectMaterialDirtyChunkUpdates(
            List.of(key),
            k -> chunk,
            candidate -> true,
            k -> 9, // identical to what's already persisted
            candidate -> persistedCellsByChunk.computeIfAbsent(candidate, unused -> new LinkedHashMap<>()),
            cells -> {
                materializeCalls.incrementAndGet();
                return List.copyOf(cells);
            });

        assertEquals(0, materializeCalls.get(), "no net change means the chunk must never be materialized");
        assertEquals(Map.of(), result);
        assertSame(existing, persistedCellsByChunk.get(chunk), "the persisted map itself must be untouched, by reference");
    }
}
