package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeSyncPlannerTest {
    @Test
    void materialSpecificChunkMappingScopesOneBlockCellsCorrectly() {
        var planner = new SmokeSyncPlanner<String, String>(2,
            AtmosphereMaterial.OBSIDIAN_POWDER::chunkCoordinate);

        planner.plan("player", "overworld", List.of(new SmokeSyncPlanner.Chunk(-1, 0)),
            List.of(new SmokeSyncPlanner.Cell(-1, 64, 0, 40, 1000)));
        var update = planner.plan("player", "overworld", List.of(new SmokeSyncPlanner.Chunk(-1, 0)),
            List.of());

        assertEquals(1, update.size());
        assertEquals(0, update.getFirst().cells().getFirst().amount());
    }
    @Test
    void snapshotsEmptyTrackedChunksAndEmitsScopedRemoval() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var chunk = new SmokeSyncPlanner.Chunk(0, 0);
        var cell = new SmokeSyncPlanner.Cell(1, 8, 1, 40, 1000);

        var snapshot = planner.plan("player", "overworld", List.of(chunk), List.of(cell));
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.getFirst().reset());
        assertTrue(snapshot.getFirst().snapshotEnd());
        assertEquals(List.of(chunk), snapshot.getFirst().authoritativeChunks());

        var removal = planner.plan("player", "overworld", List.of(chunk), List.of());
        assertEquals(List.of(new SmokeSyncPlanner.Cell(1, 8, 1, 0, 0)), removal.getFirst().cells());
    }

    @Test
    void batchesLargeSnapshotsWithoutDroppingCellsOrScope() {
        var planner = new SmokeSyncPlanner<String, String>(2);
        var chunks = List.of(new SmokeSyncPlanner.Chunk(0, 0), new SmokeSyncPlanner.Chunk(1, 0),
            new SmokeSyncPlanner.Chunk(2, 0));
        var cells = List.of(new SmokeSyncPlanner.Cell(0, 0, 0, 1, 1000),
            new SmokeSyncPlanner.Cell(2, 0, 0, 2, 1000),
            new SmokeSyncPlanner.Cell(4, 0, 0, 3, 1000));

        var updates = planner.plan("player", "overworld", chunks, cells);

        assertEquals(2, updates.size());
        assertTrue(updates.getFirst().reset());
        assertTrue(updates.getLast().snapshotEnd());
        assertEquals(3, updates.stream().mapToInt(update -> update.authoritativeChunks().size()).sum());
        assertEquals(3, updates.stream().mapToInt(update -> update.cells().size()).sum());
    }

    @Test
    void oneRingStepBoundaryCrossingAtSameViewRadiusIsDeltaWithOnlyGainedAndLostChunkCells() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var retainedChunk = new SmokeSyncPlanner.Chunk(0, 0);
        var lostChunk = new SmokeSyncPlanner.Chunk(-1, 0);
        var gainedChunk = new SmokeSyncPlanner.Chunk(1, 0);
        var retainedCell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        var lostCell = new SmokeSyncPlanner.Cell(-16, 0, 0, 300, 1000);
        var gainedCell = new SmokeSyncPlanner.Cell(16, 0, 0, 400, 1000);
        planner.plan("player", "overworld", 8, List.of(retainedChunk, lostChunk),
            List.of(retainedCell, lostCell));

        var updates = planner.plan(
            "player", "overworld", 8, List.of(retainedChunk, gainedChunk), List.of(retainedCell, gainedCell));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(
            new SmokeSyncPlanner.Cell(lostCell.x(), lostCell.y(), lostCell.z(), 0, 0),
            gainedCell
        ), updates.getFirst().cells());
    }

    @Test
    void viewDistanceChangeAtSameChunkSetForcesFullResetSnapshot() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var chunk = new SmokeSyncPlanner.Chunk(0, 0);
        var cell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.plan("player", "overworld", 8, List.of(chunk), List.of(cell));

        var updates = planner.plan("player", "overworld", 16, List.of(chunk), List.of(cell));

        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().reset());
        assertTrue(updates.getFirst().snapshotEnd());
        assertEquals(List.of(chunk), updates.getFirst().authoritativeChunks());
        assertEquals(List.of(cell), updates.getFirst().cells());
    }

    @Test
    void dimensionChangeStillForcesFullResetSnapshotRegardlessOfViewRadius() {
        var planner = new SmokeSyncPlanner<String, String>(512);
        var cell = new SmokeSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.plan("player", "overworld", 8, List.of(new SmokeSyncPlanner.Chunk(0, 0)), List.of(cell));

        var updates = planner.plan(
            "player", "nether", 8, List.of(new SmokeSyncPlanner.Chunk(0, 0)), List.of(cell));

        assertEquals(2, updates.size());
        assertTrue(updates.get(0).reset());
        assertTrue(updates.get(1).reset());
        assertEquals("nether", updates.get(1).dimension());
    }
}
