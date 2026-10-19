package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-436 review (third round): "idle ordinary sync still flattens every visible cached
 * chunk and both planners rebuild/diff a complete per-player cell map... Feed revisions/
 * subscriptions into planning and reuse unchanged chunk state instead of reconstructing every
 * visible material map... verify this with deterministic work-count/state regressions."
 *
 * <p>Coverage for {@link AtmosphereSyncPlanner#planChunks}, the chunk-revision-aware planning
 * engine that does this -- independent of {@link AtmosphereSyncPlanner#plan}'s existing flat
 * model, which stays fully intact and covered by {@code AtmosphereSyncPlannerTest}.
 *
 * <p>Several tests prove a chunk was skipped entirely -- not merely "produced no visible
 * difference" -- by supplying deliberately WRONG cell data for a chunk whose (amountRevision,
 * terrainGeneration) is unchanged: if {@code planChunks} actually looked at that chunk's cells,
 * the wrong data would disagree with what's stored and produce an update; asserting it does NOT
 * is proof the chunk's cells were never indexed, diffed, or even read.
 */
class AtmosphereSyncPlannerChunkedTest {

    /** Fixed incarnation: these tests exercise the revision/generation comparison, not the
     *  incarnation-token mechanism (see {@code ChunkIncarnationInvalidationTest} for that). */
    private static AtmosphereSyncPlanner.ChunkCells chunkCells(
        int cx, int cz, long revision, long generation, AtmosphereSyncPlanner.Cell... cells
    ) {
        return new AtmosphereSyncPlanner.ChunkCells(
            new AtmosphereSyncPlanner.Chunk(cx, cz), 1L, revision, generation, List.of(cells));
    }

    @Test
    void firstCallIsAFullResetSnapshotOfEveryChunk() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);

        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().reset());
        assertTrue(updates.getFirst().snapshotEnd());
        assertEquals(List.of(new AtmosphereSyncPlanner.Chunk(0, 0)), updates.getFirst().authoritativeChunks());
        assertEquals(List.of(cell), updates.getFirst().cells());
    }

    @Test
    void unchangedStationaryViewProducesNoUpdatesAtAll() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(List.of(), updates, "identical (revision, generation) for every chunk, same chunk "
            + "set -- the stationary steady state -- must produce no updates at all");
    }

    @Test
    void unchangedRevisionAndGenerationSkipsTheChunkEvenIfSuppliedCellsDisagree() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        var originalCell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 100, 1000);
        planner.planChunks("player", "overworld", 8,
            List.of(new AtmosphereSyncPlanner.ChunkCells(chunk, 1L, 1, 1, List.of(originalCell))));

        // Same (revision, generation) as before, but deliberately WRONG cell data. If planChunks
        // actually looked at this chunk's cells, it would see a disagreement and emit an update.
        var wrongCell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 999, 1000);
        var updates = planner.planChunks("player", "overworld", 8,
            List.of(new AtmosphereSyncPlanner.ChunkCells(chunk, 1L, 1, 1, List.of(wrongCell))));

        assertEquals(List.of(), updates, "an unchanged (revision, generation) pair must skip the chunk "
            + "entirely -- the planner must trust the revision pairing rather than re-examining cells");
    }

    @Test
    void oneChangedChunkAmongManyUnchangedOnesOnlyEmitsThatChunksCells() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cellA = new AtmosphereSyncPlanner.Cell(0, 0, 0, 100, 1000);
        var cellB = new AtmosphereSyncPlanner.Cell(16, 0, 0, 200, 1000);
        var cellC = new AtmosphereSyncPlanner.Cell(32, 0, 0, 300, 1000);
        planner.planChunks("player", "overworld", 8, List.of(
            chunkCells(0, 0, 1, 1, cellA), chunkCells(1, 0, 1, 1, cellB), chunkCells(2, 0, 1, 1, cellC)));

        // Chunk A and C keep identical (revision, generation) but get WRONG data (must be ignored).
        // Only chunk B's revision actually changed, with real new data.
        var wrongA = new AtmosphereSyncPlanner.Cell(0, 0, 0, 1, 1);
        var wrongC = new AtmosphereSyncPlanner.Cell(32, 0, 0, 1, 1);
        var changedB = new AtmosphereSyncPlanner.Cell(16, 0, 0, 999, 1000);
        var updates = planner.planChunks("player", "overworld", 8, List.of(
            chunkCells(0, 0, 1, 1, wrongA), chunkCells(1, 0, 2, 1, changedB), chunkCells(2, 0, 1, 1, wrongC)));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(changedB), updates.getFirst().cells(),
            "only the one chunk whose revision actually changed may appear in the delta; the two "
                + "unchanged chunks' (wrong) data must never be looked at");
    }

    @Test
    void enteringANewChunkEmitsAllItsCellsAsAdditionsWithoutTouchingUnchangedChunks() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cellA = new AtmosphereSyncPlanner.Cell(0, 0, 0, 100, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cellA)));

        var wrongA = new AtmosphereSyncPlanner.Cell(0, 0, 0, 1, 1); // chunk A unchanged; must be ignored
        var newCellB = new AtmosphereSyncPlanner.Cell(16, 0, 0, 500, 1000);
        var updates = planner.planChunks("player", "overworld", 8, List.of(
            chunkCells(0, 0, 1, 1, wrongA), chunkCells(1, 0, 1, 1, newCellB)));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(newCellB), updates.getFirst().cells(),
            "a newly entered chunk's cells must all appear as additions; the unchanged chunk must "
                + "not be touched even though it was re-supplied");
    }

    @Test
    void exitingAChunkTombstonesExactlyItsOwnCellsWithoutTouchingOthers() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cellA = new AtmosphereSyncPlanner.Cell(0, 0, 0, 100, 1000);
        var cellB1 = new AtmosphereSyncPlanner.Cell(16, 0, 0, 200, 1000);
        var cellB2 = new AtmosphereSyncPlanner.Cell(16, 1, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(
            chunkCells(0, 0, 1, 1, cellA), chunkCells(1, 0, 1, 1, cellB1, cellB2)));

        var wrongA = new AtmosphereSyncPlanner.Cell(0, 0, 0, 1, 1); // chunk A unchanged; must be ignored
        // Chunk B (1,0) departs the subscription entirely.
        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, wrongA)));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(Set.copyOf(List.of(
                new AtmosphereSyncPlanner.Cell(16, 0, 0, 0, 0),
                new AtmosphereSyncPlanner.Cell(16, 1, 0, 0, 0))),
            Set.copyOf(updates.getFirst().cells()),
            "exactly the departed chunk's own two cells must be tombstoned; the still-subscribed, "
                + "unchanged chunk A must not be touched");
    }

    @Test
    void capacityOnlyChangeWithinAChangedChunkIsStillEmitted() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var original = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, original)));

        // Same amount, different capacity -- a capacity-only world change. Its terrainGeneration
        // bump (not its amountRevision) is what must still mark this chunk as changed.
        var capacityChanged = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 500);
        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 2, capacityChanged)));

        assertEquals(1, updates.size());
        assertEquals(List.of(capacityChanged), updates.getFirst().cells(),
            "a capacity-only change must still be detected and emitted via the terrain generation bump "
                + "alone, even though the amount (and therefore amountRevision) never changed");
    }

    @Test
    void dimensionChangeForcesAFullResetRegardlessOfUnchangedRevisions() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        var updates = planner.planChunks("player", "nether", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(2, updates.size(), "a closing update for the old dimension, then a fresh reset "
            + "snapshot for the new one");
        assertTrue(updates.get(0).reset());
        assertEquals("overworld", updates.get(0).dimension());
        assertTrue(updates.get(1).reset());
        assertTrue(updates.get(1).snapshotEnd());
        assertEquals("nether", updates.get(1).dimension());
        assertEquals(List.of(cell), updates.get(1).cells());
    }

    @Test
    void viewDistanceChangeForcesAFullResetEvenWithIdenticalRevisions() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        var updates = planner.planChunks("player", "overworld", 16, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().reset());
        assertTrue(updates.getFirst().snapshotEnd());
        assertEquals(List.of(cell), updates.getFirst().cells());
    }

    @Test
    void wouldForceResetChunkedMatchesPlanChunksDecisionWithoutMutatingState() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        assertTrue(planner.wouldForceResetChunked("player", "overworld", 8),
            "no prior view at all must be reported as a forced reset");

        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertFalse(planner.wouldForceResetChunked("player", "overworld", 8),
            "same dimension and view radius as the established view must not be a forced reset");
        assertTrue(planner.wouldForceResetChunked("player", "nether", 8),
            "a different dimension must be reported as a forced reset");
        assertTrue(planner.wouldForceResetChunked("player", "overworld", 16),
            "a different view radius must be reported as a forced reset");

        // The peek itself must not have mutated anything: the next real call still sees the
        // original established view and is still an ordinary (non-reset) call.
        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));
        assertEquals(List.of(), updates, "wouldForceResetChunked must be a pure peek with no side effects");
    }

    @Test
    void disconnectAndResetBothClearChunkedStateRequiringAFreshFullSnapshot() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        planner.reset("player");
        assertTrue(planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)))
            .getFirst().reset());

        planner.disconnect("player");
        assertTrue(planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)))
            .getFirst().reset());
    }
}
