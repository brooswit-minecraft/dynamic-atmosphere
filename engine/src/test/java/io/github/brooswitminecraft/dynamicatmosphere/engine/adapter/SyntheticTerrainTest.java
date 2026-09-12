package io.github.brooswitminecraft.dynamicatmosphere.engine.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.BlockPos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyntheticTerrainTest {

    @Test
    void unsetPositionDefaultsToImpassable() {
        SyntheticTerrain terrain = new SyntheticTerrain();
        assertEquals(Passability.IMPASSABLE, terrain.passability(new BlockPos(3, 4, 5)));
    }

    @Test
    void unknownIsDistinguishableFromImpassableAtTheInterface() {
        SyntheticTerrain terrain = new SyntheticTerrain();
        BlockPos solid = new BlockPos(0, 0, 0);
        BlockPos unknown = new BlockPos(1, 0, 0);
        terrain.set(unknown, Passability.UNKNOWN);
        // solid is left at its default (IMPASSABLE), never explicitly set.

        assertEquals(Passability.IMPASSABLE, terrain.passability(solid));
        assertEquals(Passability.UNKNOWN, terrain.passability(unknown));
        assertNotEquals(terrain.passability(solid), terrain.passability(unknown));
    }

    @Test
    void everyMutatorNotifiesTheChangeListenerOfEveryTouchedPosition() {
        SyntheticTerrain terrain = new SyntheticTerrain();
        List<BlockPos> changed = new ArrayList<>();
        terrain.onChange(changed::add);

        terrain.set(new BlockPos(0, 0, 0), Passability.PASSABLE);
        terrain.fill(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), Passability.PASSABLE);

        // one set() + a 2-block fill() = 3 notifications total
        assertEquals(3, changed.size());
        assertEquals(new BlockPos(0, 0, 0), changed.get(0));
    }

    @Test
    void namedWorldBuildingHelpersAreFluentAndReportTopologyChanges() {
        SyntheticTerrain terrain = new SyntheticTerrain();
        List<BlockPos> changed = new ArrayList<>();
        terrain.onChange(changed::add)
            .fillSolid(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0))
            .carveAir(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0))
            .placeWall(new BlockPos(0, 0, 0), new BlockPos(0, 1, 0))
            .punchHole(new BlockPos(0, 0, 0))
            .markUnknown(new BlockPos(1, 0, 0), new BlockPos(1, 1, 0));

        assertEquals(Passability.PASSABLE, terrain.passability(new BlockPos(0, 0, 0)));
        assertEquals(Passability.IMPASSABLE, terrain.passability(new BlockPos(0, 1, 0)));
        assertEquals(Passability.UNKNOWN, terrain.passability(new BlockPos(1, 0, 0)));
        assertEquals(9, changed.size(), "every helper must report every topology position it changes");
    }
}
