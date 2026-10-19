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
 * method taking the incarnation/amountRevision/terrain generation as plain {@code long}s (rather
 * than deriving them from a {@code ChunkState}/{@code LevelChunk} itself) specifically so it is
 * directly testable here with no Minecraft runtime involved at all -- unlike most of {@code
 * ForgeAtmospherePrototype}, which has no such harness (see {@code
 * ForgeAtmospherePrototypeFreezeGateTest}'s class doc).
 *
 * <p>MINECRAFT-436 review (sixth round): production no longer tracks a persistent,
 * never-shrinking {@code Map<ChunkKey, Long>} of revisions per coordinate -- that bookkeeping is
 * now carried directly on the bounded, already-unload-removed {@code ChunkState} object, keyed by
 * a process-wide monotonic incarnation token assigned once per {@code ChunkState} at creation.
 * This test therefore drives {@code cachedChunkCells} with the three freshness values directly,
 * exactly as a caller holding a live {@code ChunkState} would, rather than through an
 * accompanying revision map.
 */
class ForgeAtmospherePrototypeCachedChunkCellsTest {

    private static List<String> build(AtomicInteger builds, String value) {
        builds.incrementAndGet();
        return List.of(value);
    }

    @Test
    void sameIncarnationRevisionAndGenerationReusesTheCachedListWithoutRebuilding() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        var builds = new AtomicInteger();

        var first = ForgeAtmospherePrototype.cachedChunkCells(
            cache, "chunk-0-0", 1L, 0L, 5L, () -> build(builds, "a"));
        var second = ForgeAtmospherePrototype.cachedChunkCells(
            cache, "chunk-0-0", 1L, 0L, 5L, () -> build(builds, "a"));

        assertEquals(1, builds.get(), "the builder must not run again when neither the incarnation, "
            + "the revision, nor the terrain generation changed -- this is the whole point of the cache");
        assertSame(first, second, "the exact same cached List instance must be returned, not merely an "
            + "equal copy -- this is what lets multiple overlapping players share one built list");
    }

    @Test
    void changedAmountRevisionForcesARebuild() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        var builds = new AtomicInteger();

        ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-0-0", 1L, 1L, 5L, () -> build(builds, "a"));
        // simulates flushDirtyChunks finding a genuine amount change: state.amountRevision++
        var result = ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-0-0", 1L, 2L, 5L, () -> build(builds, "b"));

        assertEquals(2, builds.get(), "a changed amount revision alone must force a rebuild");
        assertEquals(List.of("b"), result);
    }

    @Test
    void changedTerrainGenerationForcesARebuildEvenWithTheSameAmountRevision() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        var builds = new AtomicInteger();

        ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-0-0", 1L, 0L, 5L, () -> build(builds, "a"));
        var result = ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-0-0", 1L, 0L, 6L, () -> build(builds, "b"));

        assertEquals(2, builds.get(), "a changed terrain generation alone (a capacity-only world change, "
            + "with no amount change at all) must still force a rebuild");
        assertEquals(List.of("b"), result);
    }

    @Test
    void aDifferentKeyIsCachedIndependently() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        var builds = new AtomicInteger();

        var chunkA = ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-A", 1L, 0L, 1L, () -> build(builds, "a"));
        var chunkB = ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-B", 1L, 0L, 1L, () -> build(builds, "b"));
        var chunkAAgain = ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-A", 1L, 0L, 1L, () -> build(builds, "a"));

        assertEquals(2, builds.get(), "two distinct chunk keys must each build once, independently");
        assertEquals(List.of("a"), chunkA);
        assertEquals(List.of("b"), chunkB);
        assertSame(chunkA, chunkAAgain, "re-querying the first chunk key must still hit its own cache entry");
    }

    @Test
    void anAbsentCacheEntryBuildsConsistentlyOnFirstUse() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>(); // no entry yet
        var builds = new AtomicInteger();

        ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-0-0", 1L, 0L, 1L, () -> build(builds, "a"));
        ForgeAtmospherePrototype.cachedChunkCells(cache, "chunk-0-0", 1L, 0L, 1L, () -> build(builds, "a"));

        assertEquals(1, builds.get(),
            "a chunk never found dirty (amountRevision 0) must still cache-hit on repeated calls with the "
                + "same incarnation/revision/generation triple, rather than rebuilding every time");
    }

    /**
     * MINECRAFT-436 review: models the exact bug a chunk-replacement incarnation can produce --
     * a stale cached entry remains reachable under an (amountRevision, generation) pair that
     * coincidentally matches the NEW incarnation's own starting values, even though the
     * underlying data is for a completely different chunk instance with different cells. The
     * production fix is to give every {@code ChunkState} its own unique, process-wide incarnation
     * token at creation and feed THAT (not just the revision/generation pair) into this cache, so
     * a coincidental revision/generation match can never cause a stale hit across incarnations --
     * no explicit eviction call is even needed. See {@code ChunkIncarnationInvalidationTest} for
     * the full shared-cache-plus-planner story.
     */
    @Test
    void aDifferentIncarnationForcesARebuildEvenWithIdenticalRevisionAndGenerationAsBefore() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<String>> cache = new HashMap<>();
        var builds = new AtomicInteger();

        // Old chunk incarnation: token 1, revision 0 (never found dirty), generation 1 (fresh attachment).
        var oldChunkCells = ForgeAtmospherePrototype.cachedChunkCells(
            cache, "chunk-0-0", 1L, 0L, 1L, () -> build(builds, "old-incarnation-cell"));

        // A replacement LevelChunk at the SAME coordinate is imported, getting a brand-new,
        // guaranteed-unique incarnation token (2) from the process-wide allocator. Its own fresh
        // TerrainFactsCache also starts at generation 1, and its persisted cells are restored
        // directly into the new ChunkState, so the very next flushDirtyChunks sees persisted ==
        // live and never bumps the revision either: both signals coincidentally match the old
        // incarnation's -- only the incarnation token itself tells them apart.
        var newChunkCells = ForgeAtmospherePrototype.cachedChunkCells(
            cache, "chunk-0-0", 2L, 0L, 1L, () -> build(builds, "new-incarnation-cell"));

        assertEquals(2, builds.get(), "a different incarnation token must force a rebuild even though "
            + "revision (0) and generation (1) are identical to what the old incarnation's entry was "
            + "stamped with");
        assertEquals(List.of("old-incarnation-cell"), oldChunkCells);
        assertEquals(List.of("new-incarnation-cell"), newChunkCells,
            "the new incarnation's actual data must be served, not the stale entry");
    }
}
