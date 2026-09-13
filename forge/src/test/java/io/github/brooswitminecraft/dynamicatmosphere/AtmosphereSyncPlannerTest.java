package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereSyncPlannerTest {

    @Test
    void firstPlanIsSnapshotAndUnchangedPlanIsSilent() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        List<AtmosphereSyncPlanner.Cell> cells = List.of(new AtmosphereSyncPlanner.Cell(-1, 2, 3, 50));

        List<AtmosphereSyncPlanner.Update<String>> snapshot = planner.plan("player", "overworld", cells);
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.getFirst().reset());
        assertEquals(cells, snapshot.getFirst().cells());
        assertTrue(planner.plan("player", "overworld", cells).isEmpty());
    }

    @Test
    void deltaSendsRemovalsBeforeChangedAndAddedCells() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        planner.plan("player", "overworld", List.of(
            new AtmosphereSyncPlanner.Cell(0, 0, 0, 10),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 20)
        ));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan("player", "overworld", List.of(
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 30),
            new AtmosphereSyncPlanner.Cell(2, 0, 0, 40)
        ));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(
            new AtmosphereSyncPlanner.Cell(0, 0, 0, 0),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 30),
            new AtmosphereSyncPlanner.Cell(2, 0, 0, 40)
        ), updates.getFirst().cells());
    }

    @Test
    void dimensionChangeClearsOldDimensionAndSnapshotsNewOne() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        planner.plan("player", "overworld", List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 10)));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "nether", List.of(new AtmosphereSyncPlanner.Cell(4, 5, 6, 20)));

        assertEquals(2, updates.size());
        assertEquals("overworld", updates.get(0).dimension());
        assertTrue(updates.get(0).reset());
        assertTrue(updates.get(0).cells().isEmpty());
        assertEquals("nether", updates.get(1).dimension());
        assertTrue(updates.get(1).reset());
    }

    @Test
    void resetAndDisconnectBothRequireFreshSnapshots() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        List<AtmosphereSyncPlanner.Cell> cells = List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 10));
        planner.plan("player", "overworld", cells);

        planner.reset("player");
        assertTrue(planner.plan("player", "overworld", cells).getFirst().reset());
        planner.disconnect("player");
        assertTrue(planner.plan("player", "overworld", cells).getFirst().reset());
    }
}
