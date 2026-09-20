package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
