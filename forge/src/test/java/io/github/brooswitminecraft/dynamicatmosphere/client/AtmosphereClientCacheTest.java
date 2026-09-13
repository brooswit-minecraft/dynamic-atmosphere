package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereClientCacheTest {
    private static final AtmosphereClientCache.Cell A = new AtmosphereClientCache.Cell(-1, 4, 0);
    private static final AtmosphereClientCache.Cell B = new AtmosphereClientCache.Cell(0, 4, 0);

    private static AtmosphereClientCache.Update update(AtmosphereClientCache.Cell cell, int amount) {
        return new AtmosphereClientCache.Update(cell, amount, 1000);
    }

    private static void settle(AtmosphereClientCache cache) {
        for (int i = 0; i < 10; i++) cache.advance();
    }

    @Test
    void scopedSnapshotReplacesOnlyNamedChunksAndKeepsCachedOutside() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.restore(List.of(update(A, 400), update(B, 600)));
        cache.apply("overworld", false, false, List.of(), List.of(update(A, 900)));
        assertEquals(400, cache.storedAmount(A)); // Restore is not an authoritative snapshot.
        cache.apply("overworld", true, false, List.of(new AtmosphereClientCache.Chunk(-1, 0)), List.of());
        assertEquals(400, cache.storedAmount(A));
        cache.apply("overworld", false, true, List.of(), List.of());
        assertEquals(0, cache.storedAmount(A));
        assertEquals(600, cache.storedAmount(B));
        settle(cache);
        assertEquals(List.of(update(B, 600)), cache.exportUpdates());
        cache.apply("overworld", true, true, List.of(), List.of()); // Unsubscribe is not erasure.
        assertEquals(600, cache.storedAmount(B));
        cache.apply("overworld", false, false, List.of(), List.of(update(B, 0)));
        settle(cache);
        assertTrue(cache.exportUpdates().isEmpty());
    }

    @Test
    void authoritativeChunkUnionAndRawExportRetainExcessAndCapacity() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.restore(List.of(new AtmosphereClientCache.Update(A, 5000, 250), update(B, 600)));
        assertEquals(5000, cache.exportUpdates().getFirst().amount());
        assertEquals(250, cache.exportUpdates().getFirst().capacity());
        cache.apply("overworld", true, false, List.of(new AtmosphereClientCache.Chunk(-1, 0)), List.of(update(A, 700)));
        cache.apply("overworld", false, true, List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(B, 800)));
        assertEquals(700, cache.storedAmount(A));
        assertEquals(800, cache.storedAmount(B));
        cache.restore(List.of(update(A, 100))); // Late disk load cannot overwrite authority.
        assertEquals(700, cache.storedAmount(A));
    }

    @Test
    void memoryBudgetEvictsOutsideViewLruButNeverTruncatesDetailedView() {
        var cache = new AtmosphereClientCache(2);
        cache.changeDimension("overworld");
        cache.setDetailedView(cell -> cell.x() == 0);
        var far = new AtmosphereClientCache.Cell(100, 0, 0);
        cache.restore(List.of(update(A, 100), update(B, 200)));
        cache.apply("overworld", true, true, List.of(), List.of());
        cache.apply("overworld", false, false, List.of(), List.of(update(far, 300)));
        assertEquals(0, cache.storedAmount(A));
        assertEquals(200, cache.storedAmount(B));
        assertEquals(300, cache.storedAmount(far));
        cache.setDetailedView(cell -> true);
        cache.apply("overworld", false, false, List.of(), List.of(update(A, 100)));
        assertEquals(3, cache.size());
    }

    @Test
    void coarseCacheIsReusedBetweenTenTickRebuilds() {
        var cache = new AtmosphereClientCache();
        cache.restore(List.of(update(B, 500)));
        var first = cache.coarseCells();
        for (int i = 0; i < 9; i++) cache.advance();
        assertSame(first, cache.coarseCells());
        cache.advance();
        assertNotSame(first, cache.coarseCells());
        assertEquals(first, cache.coarseCells());
    }

    @Test
    void storedPressureUsesMillionUnitBoundWhileOpacityStaysNormalized() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(new AtmosphereClientCache.Update(A, 1_000_000, 500)));
        settle(cache);
        assertEquals(1_000_000, cache.storedAmount(A));
        assertEquals(1000, cache.visible(0).getFirst().amount());
        cache.apply("overworld", false, List.of(new AtmosphereClientCache.Update(A, Integer.MAX_VALUE, 500)));
        assertEquals(1_000_000, cache.storedAmount(A));
        cache.apply("overworld", false, List.of(new AtmosphereClientCache.Update(A, 5000, 0)));
        settle(cache);
        assertEquals(5000, cache.storedAmount(A));
        assertTrue(cache.visible(0).isEmpty());
        cache.apply("overworld", false, List.of(new AtmosphereClientCache.Update(A, -1, 500)));
        settle(cache);
        assertEquals(0, cache.storedAmount(A));
        assertEquals(0, cache.size());
    }

    @Test
    void capacityOnlyChangesInterpolateFullnessAndSnapshotsPreserveIt() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(new AtmosphereClientCache.Update(A, 250, 1000)));
        settle(cache);
        cache.apply("overworld", false, List.of(new AtmosphereClientCache.Update(A, 250, 500)));
        assertEquals(250, cache.visible(0).getFirst().amount());
        settle(cache);
        assertEquals(500, cache.visible(0).getFirst().amount());
        cache.apply("overworld", true, List.of(new AtmosphereClientCache.Update(A, 250, 500)));
        assertEquals(500, cache.visible(0).getFirst().amount());
    }

    @Test
    void zeroCapacityIsInvisibleAndOverfullOpacityIsClamped() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(new AtmosphereClientCache.Update(A, 500, 100)));
        settle(cache);
        assertEquals(1000, cache.visible(0).getFirst().amount());
        cache.apply("overworld", false, List.of(new AtmosphereClientCache.Update(A, 500, 0)));
        settle(cache);
        assertTrue(cache.visible(0).isEmpty());
        cache.apply("overworld", false, List.of(new AtmosphereClientCache.Update(A, 500, 1000)));
        settle(cache);
        assertEquals(500, cache.visible(0).getFirst().amount());
        cache.apply("overworld", false, List.of(new AtmosphereClientCache.Update(A, 0, 0)));
        settle(cache);
        assertEquals(0, cache.size());
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
    void retainsEntireSubscribedViewAndReleasesRemovalsWithoutFixedCap() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        var updates = new ArrayList<AtmosphereClientCache.Update>();
        for (int i = 0; i < 9000; i++) {
            updates.add(update(new AtmosphereClientCache.Cell(i, 0, 0), 9000));
        }
        cache.apply("overworld", true, updates);
        settle(cache);
        assertEquals(9000, cache.size());
        assertTrue(cache.visible(0).stream().allMatch(cell -> cell.amount() == 1000));
        cache.apply("overworld", false, List.of(update(new AtmosphereClientCache.Cell(0, 0, 0), -1), update(A, 100)));
        assertEquals(9001, cache.size());
        settle(cache);
        assertEquals(9000, cache.size());
        assertTrue(cache.visible(0).stream().anyMatch(cell -> cell.cell().equals(A)));
        cache.apply("overworld", true, List.of());
        assertEquals(0, cache.size());
    }

    @Test
    void multiPacketSnapshotIsAtomicAndPreservesLaterBatchOpacity() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 800), update(B, 500)));
        settle(cache);
        cache.apply("overworld", true, false, List.of(update(A, 800)));
        cache.advance();
        assertEquals(2, cache.size());
        assertEquals(1, cache.pendingSize());
        cache.apply("overworld", false, true, List.of(update(B, 500)));
        assertEquals(0, cache.pendingSize());
        assertEquals(List.of(new AtmosphereClientCache.VisibleCell(A, 800),
            new AtmosphereClientCache.VisibleCell(B, 500)), cache.visible(0));
    }

    @Test
    void incompleteSnapshotIsReplacedAndDisconnectDropsBothViews() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.apply("overworld", true, List.of(update(A, 800)));
        cache.apply("overworld", true, false, List.of(update(B, 500)));
        cache.apply("overworld", true, true, List.of(update(A, 800)));
        assertEquals(1, cache.size());
        assertEquals(0, cache.pendingSize());
        cache.apply("overworld", true, false, List.of(update(B, 500)));
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.pendingSize());
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
