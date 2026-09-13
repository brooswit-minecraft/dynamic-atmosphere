package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereGridPayloadTest {
    @Test
    void snapshotDataPreservesCapacityAndSignedCoordinates() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cell = new AtmosphereSyncPlanner.Cell(-5, -2, 7, 250, 500);
        var snapshot = planner.plan("player", "overworld", List.of(cell));
        assertEquals(List.of(cell), snapshot.getFirst().cells());
    }

    @Test
    void snapshotDataDoesNotClampOverfullOrTrappedMaterial() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cells = List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 1_000_000, 100),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 5000, 0));
        var snapshot = planner.plan("player", "overworld", cells);
        assertEquals(cells, snapshot.getFirst().cells());
    }
}
