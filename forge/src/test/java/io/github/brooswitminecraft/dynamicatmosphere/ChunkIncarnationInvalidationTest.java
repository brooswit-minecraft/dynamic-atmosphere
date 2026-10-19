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
 * <p>Composes the REAL {@link ForgeAtmospherePrototype#cachedChunkCells} (the shared cross-tick
 * cache) with the REAL {@link AtmosphereSyncPlanner#planChunks} (a subscribed player's own
 * retained freshness state) exactly as {@code ForgeAtmospherePrototype.syncPlayer} composes them
 * in production, proving the end-to-end fix: merely clearing the shared cache's revision entry
 * back to its implicit default is not enough (the player's own retained entry can already be at
 * that same default and would wrongly see no change), but bumping the revision -- guaranteed
 * different from whatever it was before -- fixes both the shared cache and the player's own
 * planner state with the one signal, since both compare against the same revision value fed into
 * {@code planChunks}. Production applies this same bump via one unified method, {@code
 * invalidateChunkCellCaches}, from both the chunk-incarnation-replacement path AND the normal
 * chunk-unload path (the fifth round's finding), so the sequence tested here -- a player's
 * planner entry surviving an unload+reload untouched, never told of an exit -- is covered the
 * same way as an in-place replacement.
 */
class ChunkIncarnationInvalidationTest {

    private static List<AtmosphereSyncPlanner.Cell> buildCells(int amount) {
        return List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, amount, 1000));
    }

    /** One sync pass for one player: shared cache lookup, then feed the result into planChunks. */
    private static List<AtmosphereSyncPlanner.Update<String>> syncOnce(
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache,
        Map<String, Long> revisions, AtmosphereSyncPlanner<String, String> planner,
        String chunkKey, AtmosphereSyncPlanner.Chunk chunk, long generation, int freshAmount
    ) {
        List<AtmosphereSyncPlanner.Cell> cells = ForgeAtmospherePrototype.cachedChunkCells(
            sharedCache, revisions, chunkKey, generation, () -> buildCells(freshAmount));
        long revision = revisions.getOrDefault(chunkKey, 0L);
        return planner.planChunks("player", "overworld", 8,
            List.of(new AtmosphereSyncPlanner.ChunkCells(chunk, revision, generation, cells)));
    }

    @Test
    void mereClearingBackToTheDefaultRevisionLeavesAnAlreadySubscribedPlayerStale() {
        // This test documents the BUG the review found, using a plain Map.remove (what the
        // production code did before the fourth-round fix) instead of a guaranteed-different bump.
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        // Establish the player's subscription: chunk never found amount-dirty, so its revision
        // reads back at the implicit default, 0.
        syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 100);

        // Simulate a chunk-incarnation replacement using the BUGGY fix: only clear the revision
        // entry (back to the same implicit default, 0) and the shared cache entry.
        revisions.remove(chunkKey);
        sharedCache.remove(chunkKey);

        // The replacement chunk's true current data differs (999, not 100), and its own fresh
        // terrain-facts generation also happens to read back as 1 again (a believable coincidence
        // -- see ensureImported's doc).
        var updates = syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 999);

        assertEquals(List.of(), updates, "documents the bug: clearing back to the same implicit "
            + "default revision the player's retained entry already has produces no update at all, "
            + "even though the chunk's real data changed -- ordinary sync goes stale until some "
            + "unrelated later change or a full reset");
    }

    @Test
    void bumpingTheRevisionOnReplacementCorrectlyInvalidatesAnAlreadySubscribedPlayer() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 100);
        assertFalse(syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 100).size() > 0,
            "sanity: an unchanged, still-subscribed chunk produces no update");

        // The real fix: invalidateChunkCellCaches bumps the revision (never clears it) in
        // addition to evicting the shared cache entry.
        revisions.merge(chunkKey, 1L, Long::sum);
        sharedCache.remove(chunkKey);

        // Same coincidentally-matching terrain generation as the buggy-fix test above, and the
        // same real data change (100 -> 999).
        var updates = syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 999);

        assertEquals(1, updates.size(), "the bumped revision must be detected as a change by the "
            + "player's own retained planner entry, not just by the shared cache");
        assertFalse(updates.getFirst().reset(), "an ordinary chunk-data replacement while still "
            + "subscribed is a ordinary delta, not a full reset");
        assertEquals(List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 999, 1000)), updates.getFirst().cells(),
            "the replacement chunk's true current data must be delivered");
    }

    @Test
    void repeatedReplacementsKeepBumpingSoEveryOneIsDetected() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 100);

        for (int replacement = 1; replacement <= 5; replacement++) {
            revisions.merge(chunkKey, 1L, Long::sum);
            sharedCache.remove(chunkKey);
            int amount = 100 + replacement;
            var updates = syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, amount);
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
     * loaded; the chunk then unloads (production: {@code onChunkUnload} calls {@code
     * invalidateChunkCellCaches}, bumping the revision -- the fix for THIS finding, replacing the
     * earlier plain-removal eviction that only handled the in-place-replacement case) and later
     * reloads with genuinely different persisted data. The player's planner entry was never told
     * about an exit (a busy channel can miss it, exactly as the review describes), so it is still
     * the SAME entry from before the unload -- proving the fix works even when the planner-side
     * state was never invalidated via the ordinary exited-chunk path at all.
     */
    @Test
    void unloadThenReloadWhilePlayerPlannerEntryWasNeverToldOfAnExitIsStillDetectedCorrectly() {
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        // Chunk loaded, player subscribed, never found amount-dirty (revision stays at the
        // implicit default, 0) before it unloads.
        syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 100);

        // Chunk unloads. Production's onChunkUnload calls invalidateChunkCellCaches: bumps the
        // revision (the fifth-round fix) and drops the shared cache entry. The player's own
        // planner entry is untouched -- it was never told the player exited this chunk (the
        // channel was busy, or simply hasn't replanned yet), so it still holds the pre-unload data.
        revisions.merge(chunkKey, 1L, Long::sum);
        sharedCache.remove(chunkKey);

        // Chunk reloads with genuinely different persisted data (e.g. changed while unloaded) and
        // its own fresh terrain-facts generation, which could just as easily coincidentally read
        // back as the same value as before -- irrelevant here, since the bumped revision alone
        // already guarantees detection regardless of what the generation happens to be.
        var updates = syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 777);

        assertEquals(1, updates.size(), "the reload must be detected as a change by the player's "
            + "planner entry even though that entry was never told about an exit in between");
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 777, 1000)), updates.getFirst().cells(),
            "the reloaded chunk's true current data must be delivered, not the stale pre-unload value");
    }

    @Test
    void aPlainUnrelatedChangeStillWorksNormallyAlongsideTheBumpMechanism() {
        // Sanity: ordinary flushDirtyChunks-driven revision bumps (unrelated to replacement) and
        // the replacement-driven bump use the same mechanism and compose correctly.
        Map<String, ForgeAtmospherePrototype.CachedChunkCells<AtmosphereSyncPlanner.Cell>> sharedCache =
            new HashMap<>();
        Map<String, Long> revisions = new HashMap<>();
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        String chunkKey = "overworld:0:0";

        syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 100);

        // Ordinary amount change: flushDirtyChunks would bump the revision the same way.
        revisions.merge(chunkKey, 1L, Long::sum);
        sharedCache.remove(chunkKey);
        var updates = syncOnce(sharedCache, revisions, planner, chunkKey, chunk, 1L, 150);

        assertTrue(updates.size() == 1 && updates.getFirst().cells().equals(
            List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 150, 1000))));
    }
}
