package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-436 review (fourth round): "the player's retained planner entry is not invalidated
 * when the shared cache is evicted. A replacement/reloaded chunk with the same revision and
 * terrain-generation pair is skipped even when its rebuilt cells differ, leaving ordinary sync
 * stale until another change or full reset." Review (fifth round), the same finding's remaining
 * continuation: "normal unload removes revision/cache entries but does not remove retained
 * player/planner entries... A player can leave, allow unload/reload, and return before the
 * channel next plans... The retained entry then suppresses the ordinary delta/tombstones."
 *
 * <p>MINECRAFT-436 review (sixth round): "unified invalidation now permanently retains each
 * unloaded coordinate in all 11 channel revision maps, even for empty channels and after all
 * player/queued state is gone... Preserve incarnation uniqueness with bounded bookkeeping, for
 * example a process-wide incarnation allocator whose token is stored only with live chunk/planner
 * state, rather than retaining every historical coordinate." Production's fix: a single
 * process-wide monotonic {@code nextIncarnation} counter (one plain {@code long}, never a map)
 * assigns a unique token to each {@code ChunkState} at creation; the token lives only on that
 * bounded, already-unload-removed object, never in a persistent per-coordinate map. This test
 * models that directly: {@code nextIncarnation} is a local counter standing in for {@code
 * ForgeAtmospherePrototype}'s own field, and each simulated chunk incarnation (initial load,
 * in-place replacement, or unload+reload) is handed the NEXT token from it, exactly as {@code
 * ensureImported} does with {@code new ChunkState(chunk, nextIncarnation++)} -- never a token
 * looked up or retained by coordinate.
 *
 * <p>Composes the REAL {@link ForgeAtmospherePrototype#cachedChunkCells} (the shared cross-tick
 * cache) with the REAL {@link AtmosphereSyncPlanner#planChunks} (a subscribed player's own
 * retained freshness state) exactly as {@code ForgeAtmospherePrototype.syncPlayer} composes them
 * in production, proving the end-to-end fix: a fresh incarnation token is guaranteed different
 * from whatever token the player's own retained entry already has, so it alone is sufficient to
 * invalidate both the shared cache and the player's retained planner state, even when the
 * accompanying amountRevision/terrainGeneration pair coincidentally matches the old incarnation's
 * -- all without any explicit eviction call, and without retaining any state keyed by coordinate
 * beyond the live chunk itself.
 */
class ChunkIncarnationInvalidationTest {

    private static List<AtmosphereSyncPlanner.Cell> buildCells(int amount) {
        return List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, amount, 1000));
    }

    /** One sync pass for one player: shared cache lookup, then feed the result into planChunks. */
    private static List<AtmosphereSyncPlanner.Update<String>> syncOnce(
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache,
        AtmosphereSyncPlanner<String, String> planner, String chunkKey, AtmosphereSyncPlanner.Chunk chunk,
        long incarnation, long amountRevision, long generation, int freshAmount
    ) {
        List<AtmosphereSyncPlanner.Cell> cells = ForgeAtmospherePrototype.cachedChunkCells(
            sharedCache, chunkKey, incarnation, amountRevision, generation, () -> buildCells(freshAmount));
        return planner.planChunks("player", "overworld", 8,
            List.of(new AtmosphereSyncPlanner.ChunkCells(chunk, incarnation, amountRevision, generation, cells)));
    }

    @Test
    void anUnchangedIncarnationWithUnchangedRevisionAndGenerationProducesNoUpdate() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        syncOnce(sharedCache, planner, chunkKey, chunk, 1L, 0L, 1L, 100);
        var updates = syncOnce(sharedCache, planner, chunkKey, chunk, 1L, 0L, 1L, 100);

        assertEquals(List.of(), updates, "sanity: an unchanged, still-subscribed chunk produces no update");
    }

    @Test
    void aFreshIncarnationTokenAloneInvalidatesAnAlreadySubscribedPlayer() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";
        long nextIncarnation = 1;

        // Initial load: token 1, never found amount-dirty (revision stays at 0).
        syncOnce(sharedCache, planner, chunkKey, chunk, nextIncarnation++, 0L, 1L, 100);
        assertFalse(syncOnce(sharedCache, planner, chunkKey, chunk, nextIncarnation - 1, 0L, 1L, 100).size() > 0,
            "sanity: replaying the same incarnation with unchanged data produces no update");

        // A chunk-incarnation replacement at the SAME coordinate: a brand-new token (2) from the
        // process-wide allocator, even though its own fresh terrain-facts generation happens to
        // also read back as 1 again (a believable coincidence -- see ensureImported's doc), and
        // its restored amount revision also happens to start back at 0.
        var updates = syncOnce(sharedCache, planner, chunkKey, chunk, nextIncarnation++, 0L, 1L, 999);

        assertEquals(1, updates.size(), "the fresh incarnation token must be detected as a change by the "
            + "player's own retained planner entry, not just by the shared cache, even though the "
            + "accompanying revision and generation coincidentally matched the old incarnation's");
        assertFalse(updates.getFirst().reset(), "an ordinary chunk-data replacement while still "
            + "subscribed is an ordinary delta, not a full reset");
        assertEquals(List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 999, 1000)), updates.getFirst().cells(),
            "the replacement chunk's true current data must be delivered");
    }

    @Test
    void repeatedReplacementsEachGetAFreshTokenSoEveryOneIsDetected() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";
        long nextIncarnation = 1;

        syncOnce(sharedCache, planner, chunkKey, chunk, nextIncarnation++, 0L, 1L, 100);

        for (int replacement = 1; replacement <= 5; replacement++) {
            int amount = 100 + replacement;
            var updates = syncOnce(sharedCache, planner, chunkKey, chunk, nextIncarnation++, 0L, 1L, amount);
            assertEquals(1, updates.size(), "replacement #" + replacement + " must be detected");
            assertEquals(List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, amount, 1000)), updates.getFirst().cells());
        }
    }

    /**
     * MINECRAFT-436 review (fifth round): "normal unload removes revision/cache entries but does
     * not remove retained player/planner entries. Busy paced channels can miss the subscription-
     * exit interval. A player can leave, allow unload/reload, and return before the channel next
     * plans; reload takes previous == null and may supply old revision/generation values despite
     * persisted amount changes made during the earlier drain. The retained entry then suppresses
     * the ordinary delta/tombstones."
     *
     * <p>Models the full sequence: the player's planner entry is established while the chunk is
     * loaded (incarnation token 1); the chunk then unloads and later reloads as an entirely new
     * {@code ChunkState} (production: {@code ensureImported} assigns it the next token, 2, from
     * the same process-wide allocator -- no bookkeeping survives per coordinate across the unload
     * at all) with genuinely different persisted data. The player's planner entry was never told
     * about an exit (a busy channel can miss it, exactly as the review describes), so it is still
     * the SAME entry from before the unload -- proving the fresh token alone is detected correctly
     * even when the planner-side state was never invalidated via the ordinary exited-chunk path.
     */
    @Test
    void unloadThenReloadWhilePlayerPlannerEntryWasNeverToldOfAnExitIsStillDetectedCorrectly() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        // Chunk loaded (token 1), player subscribed, never found amount-dirty before it unloads.
        syncOnce(sharedCache, planner, chunkKey, chunk, 1L, 0L, 1L, 100);

        // Chunk unloads: production's onChunkUnload only drops the shared cache entry (no revision
        // map to bump any more -- ChunkState itself is simply discarded). The player's own planner
        // entry is untouched -- it was never told the player exited this chunk (the channel was
        // busy, or simply hasn't replanned yet), so it still holds the pre-unload data.
        sharedCache.remove(chunkKey);

        // Chunk reloads as a brand-new ChunkState (token 2, from the same allocator -- no relation
        // to coordinate) with genuinely different persisted data (e.g. changed while unloaded) and
        // its own fresh terrain-facts generation, which could just as easily coincidentally read
        // back as the same value as before -- irrelevant here, since the fresh token alone already
        // guarantees detection regardless of what the generation or revision happen to be.
        var updates = syncOnce(sharedCache, planner, chunkKey, chunk, 2L, 0L, 1L, 777);

        assertEquals(1, updates.size(), "the reload must be detected as a change by the player's "
            + "planner entry even though that entry was never told about an exit in between");
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 777, 1000)), updates.getFirst().cells(),
            "the reloaded chunk's true current data must be delivered, not the stale pre-unload value");
    }

    @Test
    void aPlainUnrelatedAmountChangeStillWorksNormallyAlongsideTheIncarnationMechanism() {
        // Sanity: ordinary flushDirtyChunks-driven revision bumps on the SAME incarnation (unrelated
        // to any replacement/reload) and the fresh-token mechanism compose correctly.
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        syncOnce(sharedCache, planner, chunkKey, chunk, 1L, 0L, 1L, 100);

        // Ordinary amount change on the SAME chunk incarnation: state.amountRevision++.
        sharedCache.remove(chunkKey);
        var updates = syncOnce(sharedCache, planner, chunkKey, chunk, 1L, 1L, 1L, 150);

        assertTrue(updates.size() == 1 && updates.getFirst().cells().equals(
            List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 150, 1000))));
    }
}
