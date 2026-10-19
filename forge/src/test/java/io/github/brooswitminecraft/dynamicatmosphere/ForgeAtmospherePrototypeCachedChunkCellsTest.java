package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * MINECRAFT-436: coverage for {@link ForgeAtmospherePrototype#cachedChunkCells}, the cross-tick
 * cache-or-rebuild algorithm behind the ticket's "reusable immutable chunk snapshots shared
 * across overlapping player views" requirement. Deliberately a pure, static, key-type-generic
 * method taking the terrain generation as a plain {@code long} (rather than deriving it from a
 * {@code ChunkState}/{@code LevelChunk} itself) specifically so it is directly testable here with
 * no Minecraft runtime involved at all -- unlike most of {@code ForgeAtmospherePrototype}, which
 * has no such harness (see {@code ForgeAtmospherePrototypeFreezeGateTest}'s class doc).
 */
class ForgeAtmospherePrototypeCachedChunkCellsTest {

    private static List<String> build(AtomicInteger builds, String value) {
        builds.incrementAndGet();
        return List.of(value);
    }

    @Test
    void sameRevisionAndGenerationReusesTheCachedListWithoutRebuilding() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var builds = new AtomicInteger();

        var first = ForgeAtmospherePrototype.cachedChunkCells(
            cache, revisions, "chunk-0-0", 5L, () -> build(builds, "a"));
        var second = ForgeAtmospherePrototype.cachedChunkCells(
            cache, revisions, "chunk-0-0", 5L, () -> build(builds, "a"));

        assertEquals(1, builds.get(), "the builder must not run again when neither the revision nor the "
            + "terrain generation changed -- this is the whole point of the cache");
        assertSame(first, second, "the exact same cached List instance must be returned, not merely an "
            + "equal copy -- this is what lets multiple overlapping players share one built list");
    }

    @Test
    void changedAmountRevisionForcesARebuild() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var builds = new AtomicInteger();

        revisions.put("chunk-0-0", 1L);
        ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-0-0", 5L, () -> build(builds, "a"));
        revisions.put("chunk-0-0", 2L); // simulates flushDirtyChunks finding a genuine amount change
        var result = ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-0-0", 5L, () -> build(builds, "b"));

        assertEquals(2, builds.get(), "a changed amount revision alone must force a rebuild");
        assertEquals(List.of("b"), result);
    }

    @Test
    void changedTerrainGenerationForcesARebuildEvenWithTheSameAmountRevision() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var builds = new AtomicInteger();

        ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-0-0", 5L, () -> build(builds, "a"));
        var result = ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-0-0", 6L, () -> build(builds, "b"));

        assertEquals(2, builds.get(), "a changed terrain generation alone (a capacity-only world change, "
            + "with no amount change at all) must still force a rebuild");
        assertEquals(List.of("b"), result);
    }

    @Test
    void aDifferentKeyIsCachedIndependently() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var builds = new AtomicInteger();

        var chunkA = ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-A", 1L, () -> build(builds, "a"));
        var chunkB = ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-B", 1L, () -> build(builds, "b"));
        var chunkAAgain = ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-A", 1L, () -> build(builds, "a"));

        assertEquals(2, builds.get(), "two distinct chunk keys must each build once, independently");
        assertEquals(List.of("a"), chunkA);
        assertEquals(List.of("b"), chunkB);
        assertSame(chunkA, chunkAAgain, "re-querying the first chunk key must still hit its own cache entry");
    }

    @Test
    void anAbsentRevisionEntryDefaultsToZeroConsistently() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        Map<String, Long> revisions = new HashMap<>(); // no entry for "chunk-0-0" at all
        var builds = new AtomicInteger();

        ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-0-0", 1L, () -> build(builds, "a"));
        ForgeAtmospherePrototype.cachedChunkCells(cache, revisions, "chunk-0-0", 1L, () -> build(builds, "a"));

        assertEquals(1, builds.get(),
            "a chunk never found dirty (no revisions entry) must still cache-hit on repeated calls, "
                + "defaulting consistently to the same implied revision rather than rebuilding every time");
    }

    /**
     * MINECRAFT-436 review: models the exact bug a chunk-replacement incarnation can produce --
     * a stale cached entry remains reachable under a (revision, generation) pair that
     * coincidentally matches the NEW incarnation's own starting values, even though the
     * underlying data is for a completely different chunk instance with different cells. This is
     * exactly why {@code ensureImported}'s replacement branch unconditionally evicts the cache
     * entry (via {@code evictChunkCellCaches}) instead of trusting revision/generation alone to
     * detect the swap: removal forces a rebuild regardless of what the (possibly coincidentally
     * matching) revision and generation values are.
     */
    @Test
    void evictingAnEntryForcesARebuildEvenWithIdenticalRevisionAndGenerationAsBefore() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var builds = new AtomicInteger();

        // Old chunk incarnation: revision 0 (never found dirty), generation 1 (fresh attachment).
        var oldChunkCells = ForgeAtmospherePrototype.cachedChunkCells(
            cache, revisions, "chunk-0-0", 1L, () -> build(builds, "old-incarnation-cell"));

        // A replacement LevelChunk at the SAME coordinate is imported. Its own fresh
        // TerrainFactsCache also starts at generation 1, and its persisted cells are restored
        // directly into the new ChunkState, so the very next flushDirtyChunks sees persisted ==
        // live and never bumps the revision either: both signals coincidentally match the old
        // incarnation's. Without eviction, the stale "old-incarnation-cell" would be reused here.
        cache.remove("chunk-0-0"); // what evictChunkCellCaches does on replacement
        var newChunkCells = ForgeAtmospherePrototype.cachedChunkCells(
            cache, revisions, "chunk-0-0", 1L, () -> build(builds, "new-incarnation-cell"));

        assertEquals(2, builds.get(), "eviction must force a rebuild even though revision (0) and "
            + "generation (1) are identical to what the old incarnation's entry was stamped with");
        assertEquals(List.of("old-incarnation-cell"), oldChunkCells);
        assertEquals(List.of("new-incarnation-cell"), newChunkCells,
            "the new incarnation's actual data must be served, not the evicted stale entry");
    }
}
