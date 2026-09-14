package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaporOverheadTerrainTest {

    @Test
    void onlyTerrainStrictlyAboveTheSpawnBypassesTheGate() {
        assertTrue(VaporOverheadTerrain.isAbove(80, 79));
        assertFalse(VaporOverheadTerrain.isAbove(80, 80));
        assertFalse(VaporOverheadTerrain.isAbove(80, 81));
        assertFalse(VaporOverheadTerrain.isAbove(
            VaporOverheadTerrain.ColumnCache.NONE, 0));
    }

    @Test
    void columnCacheStartsUnknownAndInvalidatesOnlyItsColumn() {
        VaporOverheadTerrain.ColumnCache cache = new VaporOverheadTerrain.ColumnCache();
        assertEquals(VaporOverheadTerrain.ColumnCache.UNKNOWN, cache.highest(17));
        cache.record(17, 92);
        cache.record(18, 71);

        cache.invalidate(17);

        assertEquals(VaporOverheadTerrain.ColumnCache.UNKNOWN, cache.highest(17));
        assertEquals(71, cache.highest(18));
    }
}
