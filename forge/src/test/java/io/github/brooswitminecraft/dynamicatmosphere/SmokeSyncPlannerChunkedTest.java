package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-436 review (third round): the {@link SmokeSyncPlanner} equivalent of {@code
 * AtmosphereSyncPlannerChunkedTest} -- see that class's doc for the full rationale. This planner
 * is shared by the smoke grid and every per-material grid, so this coverage applies to all of
 * them. A lighter suite than the Atmosphere one (the underlying algorithm is identical, already
 * fully exercised there); this focuses on the smoke-specific wiring plus the same
 * skip-unchanged-chunk proof technique (deliberately wrong cell data on an unchanged chunk).
 */
class SmokeSyncPlannerChunkedTest {

    private static SmokeSyncPlanner.ChunkCells chunkCells(
        int cx, int cz, long revision, long generation, SmokeSyncPlanner.Cell... cells
    ) {
        return new SmokeSyncPlanner.ChunkCells(new SmokeSyncPlanner.Chunk(cx, cz), revision, generation, List.of(cells));
    }

    @Test
    void firstCallIsAFullResetSnapshot() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var cell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);

        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().reset());
        assertTrue(updates.getFirst().snapshotEnd());
        assertEquals(List.of(cell), updates.getFirst().cells());
    }

    @Test
    void unchangedStationaryViewProducesNoUpdates() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var cell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(List.of(), updates);
    }

    @Test
    void unchangedRevisionAndGenerationSkipsTheChunkEvenIfSuppliedCellsDisagree() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var chunk = new SmokeSyncPlanner.Chunk(0, 0);
        var originalCell = new SmokeSyncPlanner.Cell(0, 0, 0, 100, 1000);
        planner.planChunks("player", "overworld", 8,
            List.of(new SmokeSyncPlanner.ChunkCells(chunk, 1, 1, List.of(originalCell))));

        var wrongCell = new SmokeSyncPlanner.Cell(0, 0, 0, 999, 1000);
        var updates = planner.planChunks("player", "overworld", 8,
            List.of(new SmokeSyncPlanner.ChunkCells(chunk, 1, 1, List.of(wrongCell))));

        assertEquals(List.of(), updates, "an unchanged (revision, generation) pair must skip the chunk "
            + "entirely, never noticing the supplied cell data disagrees with what's stored");
    }

    @Test
    void oneChangedChunkAmongManyUnchangedOnesOnlyEmitsThatChunksCells() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var cellA = new SmokeSyncPlanner.Cell(0, 0, 0, 100, 1000);
        var cellB = new SmokeSyncPlanner.Cell(16, 0, 0, 200, 1000);
        planner.planChunks("player", "overworld", 8,
            List.of(chunkCells(0, 0, 1, 1, cellA), chunkCells(1, 0, 1, 1, cellB)));

        var wrongA = new SmokeSyncPlanner.Cell(0, 0, 0, 1, 1);
        var changedB = new SmokeSyncPlanner.Cell(16, 0, 0, 999, 1000);
        var updates = planner.planChunks("player", "overworld", 8,
            List.of(chunkCells(0, 0, 1, 1, wrongA), chunkCells(1, 0, 2, 1, changedB)));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(changedB), updates.getFirst().cells());
    }

    @Test
    void exitingAChunkTombstonesExactlyItsOwnCells() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var cellA = new SmokeSyncPlanner.Cell(0, 0, 0, 100, 1000);
        var cellB1 = new SmokeSyncPlanner.Cell(16, 0, 0, 200, 1000);
        var cellB2 = new SmokeSyncPlanner.Cell(16, 1, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(
            chunkCells(0, 0, 1, 1, cellA), chunkCells(1, 0, 1, 1, cellB1, cellB2)));

        var wrongA = new SmokeSyncPlanner.Cell(0, 0, 0, 1, 1);
        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, wrongA)));

        assertEquals(1, updates.size());
        assertEquals(Set.copyOf(List.of(
                new SmokeSyncPlanner.Cell(16, 0, 0, 0, 0),
                new SmokeSyncPlanner.Cell(16, 1, 0, 0, 0))),
            Set.copyOf(updates.getFirst().cells()));
    }

    @Test
    void capacityOnlyChangeWithinAChangedChunkIsStillEmitted() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var original = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, original)));

        var capacityChanged = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 500);
        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 2, capacityChanged)));

        assertEquals(1, updates.size());
        assertEquals(List.of(capacityChanged), updates.getFirst().cells());
    }

    @Test
    void dimensionChangeForcesAFullReset() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var cell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        var updates = planner.planChunks("player", "nether", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(2, updates.size());
        assertTrue(updates.get(0).reset());
        assertTrue(updates.get(1).reset());
        assertEquals("nether", updates.get(1).dimension());
    }

    @Test
    void viewDistanceChangeForcesAFullResetEvenWithIdenticalRevisions() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var cell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        var updates = planner.planChunks("player", "overworld", 16, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().reset());
    }

    @Test
    void wouldForceResetChunkedIsAPureNonMutatingPeek() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        assertTrue(planner.wouldForceResetChunked("player", "overworld", 8));

        var cell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));

        assertFalse(planner.wouldForceResetChunked("player", "overworld", 8));
        assertTrue(planner.wouldForceResetChunked("player", "nether", 8));

        var updates = planner.planChunks("player", "overworld", 8, List.of(chunkCells(0, 0, 1, 1, cell)));
        assertEquals(List.of(), updates, "the peek must not have mutated state");
    }
}
