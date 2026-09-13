package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereSyncPlannerTest {

    @Test
    void capacityOnlyChangeIsSentEvenWhenAmountIsUnchanged() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        planner.plan("player", "overworld", List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000)));
        var changed = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 500);
        var updates = planner.plan("player", "overworld", List.of(changed));
        assertEquals(List.of(changed), updates.getFirst().cells());
        assertFalse(updates.getFirst().reset());
        assertTrue(planner.plan("player", "overworld", List.of(changed)).isEmpty());
    }

    @Test
    void firstPlanIsSnapshotAndUnchangedPlanIsSilent() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        List<AtmosphereSyncPlanner.Cell> cells = List.of(new AtmosphereSyncPlanner.Cell(-1, 2, 3, 50, 1000));

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
            new AtmosphereSyncPlanner.Cell(0, 0, 0, 10, 1000),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 20, 1000)
        ));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan("player", "overworld", List.of(
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 30, 1000),
            new AtmosphereSyncPlanner.Cell(2, 0, 0, 40, 1000)
        ));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(
            new AtmosphereSyncPlanner.Cell(0, 0, 0, 0, 0),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 30, 1000),
            new AtmosphereSyncPlanner.Cell(2, 0, 0, 40, 1000)
        ), updates.getFirst().cells());
    }

    @Test
    void dimensionChangeClearsOldDimensionAndSnapshotsNewOne() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        planner.plan("player", "overworld", List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 10, 1000)));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "nether", List.of(new AtmosphereSyncPlanner.Cell(4, 5, 6, 20, 1000)));

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
        List<AtmosphereSyncPlanner.Cell> cells = List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 10, 1000));
        planner.plan("player", "overworld", cells);

        planner.reset("player");
        assertTrue(planner.plan("player", "overworld", cells).getFirst().reset());
        planner.disconnect("player");
        assertTrue(planner.plan("player", "overworld", cells).getFirst().reset());
    }
}
