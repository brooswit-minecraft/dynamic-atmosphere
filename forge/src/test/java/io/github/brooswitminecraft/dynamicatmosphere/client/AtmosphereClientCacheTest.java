package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereClientCacheTest {
    private static final AtmosphereClientCache.Cell A = new AtmosphereClientCache.Cell(-1, 4, 0);
    private static final AtmosphereClientCache.Cell B = new AtmosphereClientCache.Cell(0, 4, 0);

    private static AtmosphereClientCache.Update update(AtmosphereClientCache.Cell cell, int amount) {
        return new AtmosphereClientCache.Update(cell, amount);
    }

    private static void settle(AtmosphereClientCache cache) {
        for (int i = 0; i < 10; i++) cache.advance();
    }

    @Test
    void periodicSnapshotReconcilesMembershipWithoutRestartingOpacity() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 800), update(B, 500)));
        settle(cache);
        cache.apply("overworld", true, List.of(update(A, 800)));
        assertEquals(List.of(new AtmosphereClientCache.VisibleCell(A, 800)), cache.visible(0));
        cache.apply("overworld", true, List.of(update(A, 400)));
        assertEquals(800, cache.visible(0).getFirst().amount());
        settle(cache);
        assertEquals(400, cache.visible(0).getFirst().amount());
    }

    @Test
    void snapshotDuringFadePreservesProgress() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 800)));
        for (int i = 0; i < 5; i++) cache.advance();
        cache.apply("overworld", true, List.of(update(A, 800)));
        assertEquals(400, cache.visible(0).getFirst().amount());
        for (int i = 0; i < 5; i++) cache.advance();
        assertEquals(800, cache.visible(0).getFirst().amount());
    }

    @Test
    void requiresSnapshotForCurrentDimensionAndIgnoresOldDimensionPackets() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", false, List.of(update(A, 400)));
        assertEquals(0, cache.size());
        cache.apply("overworld", true, List.of(update(A, 400)));
        cache.changeDimension("nether");
        cache.apply("overworld", true, List.of(update(A, 900)));
        assertEquals(0, cache.size());
        cache.apply("nether", false, List.of(update(A, 400)));
        assertEquals(0, cache.size());
        cache.apply("nether", true, List.of(update(B, 500)));
        assertEquals(1, cache.size());
    }

    @Test
    void resetReplacesButDeltasRetainOtherCells() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 400)));
        cache.apply("overworld", false, List.of(update(B, 800)));
        assertEquals(2, cache.size());
        cache.apply("overworld", true, List.of(update(B, 800)));
        settle(cache);
        assertEquals(List.of(new AtmosphereClientCache.VisibleCell(B, 800)), cache.visible(0));
    }

    @Test
    void interpolatesRetargetingAndRemovalWithoutDiscontinuousOpacity() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 1000)));
        assertTrue(cache.visible(0).isEmpty());
        for (int i = 0; i < 5; i++) cache.advance();
        assertEquals(550, cache.visible(0.5f).getFirst().amount(), 0.001);
        cache.apply("overworld", false, List.of(update(A, 200)));
        assertEquals(500, cache.visible(0).getFirst().amount(), 0.001);
        settle(cache);
        assertEquals(200, cache.visible(0).getFirst().amount(), 0.001);
        cache.apply("overworld", false, List.of(update(A, 0)));
        assertEquals(200, cache.visible(0).getFirst().amount(), 0.001);
        settle(cache);
        assertEquals(0, cache.size());
    }

    @Test
    void duplicateDeltaDoesNotRestartTransition() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 1000)));
        for (int i = 0; i < 10; i++) {
            cache.advance();
            cache.apply("overworld", false, List.of(update(A, 1000)));
        }
        assertEquals(1000, cache.visible(0).getFirst().amount());
    }

    @Test
    void boundsCacheAndAmountsAndMakesRoomForRemovals() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        var updates = new ArrayList<AtmosphereClientCache.Update>();
        for (int i = 0; i < AtmosphereClientCache.MAX_CELLS + 10; i++) {
            updates.add(update(new AtmosphereClientCache.Cell(i, 0, 0), 9000));
        }
        cache.apply("overworld", true, updates);
        settle(cache);
        assertEquals(AtmosphereClientCache.MAX_CELLS, cache.size());
        assertTrue(cache.visible(0).stream().allMatch(cell -> cell.amount() == 1000));
        cache.apply("overworld", false, List.of(update(new AtmosphereClientCache.Cell(0, 0, 0), -1), update(A, 100)));
        assertEquals(AtmosphereClientCache.MAX_CELLS, cache.size());
        settle(cache);
        assertTrue(cache.visible(0).stream().anyMatch(cell -> cell.cell().equals(A)));
    }

    @Test
    void disconnectAndSameDimensionWorldReplacementRequireFreshSnapshot() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 100)));
        cache.clear();
        cache.changeDimension("overworld");
        cache.apply("overworld", false, List.of(update(A, 100)));
        assertEquals(0, cache.size());
        assertTrue(cache.visible(0).isEmpty());
    }
}
