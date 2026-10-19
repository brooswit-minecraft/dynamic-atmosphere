package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class TerrainFactsCacheTest {

    @Test
    void fluidTransitionsInvalidateFactsAndBarrierTogether() {
        var cache = new TerrainFactsCache(-64, 320);
        // Air to liquid changes only the downward barrier.
        cache.put(0, 0, 0, 64, false);
        AtmosphereFluidTransport.enter();
        try {
            cache.blockChanged(0, 0, 0, true, true, false, true);
        } finally {
            AtmosphereFluidTransport.exit();
        }
        assertEquals(-1, cache.emptyBlockCount(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));

        // Source/flowing level changes retain the same two classifications.
        cache.put(0, 0, 0, 64, true);
        cache.blockChanged(0, 0, 0, true, true, true, true);
        assertEquals(64, cache.emptyBlockCount(0, 0, 0));
        assertEquals(1, cache.downwardBarrier(0, 0, 0));

        cache.blockChanged(0, 0, 0, true, true, true, false);
        assertEquals(-1, cache.emptyBlockCount(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));

        // Waterlogging and draining never change the host's occupancy.
        cache.put(0, 0, 0, 0, false);
        cache.blockChanged(0, 0, 0, false, false, false, true);
        assertEquals(-1, cache.emptyBlockCount(0, 0, 0));
        cache.put(0, 0, 0, 0, true);
        cache.blockChanged(0, 0, 0, false, false, true, false);
        assertEquals(-1, cache.emptyBlockCount(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));

        // Replacing a liquid with a waterlogged host changes only occupancy.
        cache.put(0, 0, 0, 64, true);
        cache.blockChanged(0, 0, 0, true, false, true, true);
        assertEquals(-1, cache.emptyBlockCount(0, 0, 0));
        cache.put(0, 0, 0, 0, false);
        assertEquals(0, cache.downwardBarrier(0, 0, 0));
        cache.clear();
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));
    }

    @Test
    void repeatedViewsReuseFactsUntilAirOccupancyChanges() {
        var cache = new TerrainFactsCache(-64, 320);
        var reads = new AtomicInteger();
        assertEquals(64, read(cache, 0, 0, 0, 64, reads));
        assertEquals(64, read(cache, 0, 0, 0, 32, reads));
        assertEquals(1, reads.get());
        cache.blockChanged(3, 2, 1, true, false);
        assertEquals(32, read(cache, 0, 0, 0, 32, reads));
        assertEquals(2, reads.get());
        cache.blockChanged(3, 2, 1, false, true);
        assertEquals(64, read(cache, 0, 0, 0, 64, reads));
        assertEquals(3, reads.get());
    }

    @Test
    void emptyBlockCountWithScanSupplierScansOnlyOnce() {
        var cache = new TerrainFactsCache(-64, 320);
        var scans = new AtomicInteger();
        Supplier<TerrainFactsCache.Scan> scan = () -> {
            scans.incrementAndGet();
            return new TerrainFactsCache.Scan(48, false);
        };
        assertEquals(48, cache.emptyBlockCount(5, 10, 5, scan));
        assertEquals(48, cache.emptyBlockCount(5, 10, 5, scan));
        assertEquals(1, scans.get(), "a cached cell must not be rescanned");

        cache.blockChanged(5 * 4, 10 * 4, 5 * 4, true, false);
        assertEquals(48, cache.emptyBlockCount(5, 10, 5, scan));
        assertEquals(2, scans.get(), "invalidation must force exactly one rescan");
    }

    @Test
    void sameOccupancyAndOtherCellsDoNotInvalidate() {
        var cache = new TerrainFactsCache(-64, 320);
        cache.put(0, 0, 0, 0, false);
        cache.put(1, 0, 0, 32, false);
        cache.blockChanged(0, 0, 0, false, false);
        assertEquals(0, cache.emptyBlockCount(0, 0, 0));
        cache.blockChanged(4, 0, 0, true, false);
        assertEquals(0, cache.emptyBlockCount(0, 0, 0));
        assertEquals(-1, cache.emptyBlockCount(1, 0, 0));
    }

    @Test
    void bedrockChangeInvalidatesEvenWhenBothBlocksAreSolid() {
        var cache = new TerrainFactsCache(-64, 320);
        cache.put(0, 0, 0, 32, false);

        cache.blockChanged(1, 1, 1, false, false, false, true);

        assertEquals(-1, cache.emptyBlockCount(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));
    }

    @Test
    void negativeCoordinatesAndBuildHeightBoundariesStayInTheirOwnSlots() {
        var cache = new TerrainFactsCache(-64, 320);
        cache.put(-1, -16, -1, 16, false);
        cache.put(-4, 79, -4, 48, false);
        cache.blockChanged(-1, -64, -1, false, true);
        assertEquals(-1, cache.emptyBlockCount(-1, -16, -1));
        assertEquals(48, cache.emptyBlockCount(-4, 79, -4));
        cache.blockChanged(-16, 320, -16, true, false);
        assertEquals(48, cache.emptyBlockCount(-4, 79, -4));
        assertEquals(-1, cache.emptyBlockCount(-4, 80, -4));
        assertEquals(-1, cache.emptyBlockCount(-4, -17, -4));
    }

    @Test
    void transportSuppressionDoesNotSuppressInvalidation() {
        var cache = new TerrainFactsCache(-64, 320);
        cache.put(0, 0, 0, 32, false);
        AtmosphereFluidTransport.enter();
        try {
            cache.blockChanged(0, 0, 0, false, true);
            assertEquals(-1, cache.emptyBlockCount(0, 0, 0));
        } finally {
            AtmosphereFluidTransport.exit();
        }
    }

    @Test
    void unloadAndNewChunkIdentityStartUnknown() {
        var old = new TerrainFactsCache(-64, 320);
        old.put(0, 0, 0, 64, false);
        assertEquals(-1, new TerrainFactsCache(-64, 320).emptyBlockCount(0, 0, 0));
        old.clear();
        assertEquals(-1, old.emptyBlockCount(0, 0, 0));
    }

    @Test
    void invalidOrOutOfRangeEmptyBlockCountIsRejected() {
        var cache = new TerrainFactsCache(-64, 320);
        assertThrows(IllegalArgumentException.class, () -> cache.put(0, 0, 0, -1, false));
        assertThrows(IllegalArgumentException.class, () -> cache.put(0, 0, 0, 65, false));
    }

    /**
     * MINECRAFT-436: {@code generation()} is the cheap, chunk-wide "did capacity possibly
     * change" signal a cross-tick cell-snapshot cache (outside this class) relies on instead of
     * re-deriving capacity for every cell to find out. It must bump on every invalidating block
     * change and on {@code clear()}, and must NOT bump on a `put` (a scan result being recorded,
     * not a change) or on a block change that doesn't actually alter classification -- otherwise
     * that outside cache would invalidate far more often than genuinely needed.
     */
    @Test
    void generationBumpsOnlyOnActualInvalidationOrClear() {
        var cache = new TerrainFactsCache(-64, 320);
        long initial = cache.generation();

        // Recording a scan result is not itself a change; must not bump.
        cache.put(0, 0, 0, 64, false);
        assertEquals(initial, cache.generation(), "put() records a scan result, not an invalidation");

        // A block change that doesn't alter either classification must not bump.
        cache.blockChanged(0, 0, 0, true, true, false, false);
        assertEquals(initial, cache.generation(), "a no-op block change must not bump generation");

        // A real invalidating change must bump exactly once.
        cache.blockChanged(0, 0, 0, true, false, false, false);
        long afterFirstChange = cache.generation();
        assertTrue(afterFirstChange > initial, "a real invalidating block change must bump generation");

        // Another real change bumps again.
        cache.put(0, 0, 0, 64, false);
        cache.blockChanged(0, 0, 0, true, false, false, true);
        assertTrue(cache.generation() > afterFirstChange,
            "each distinct invalidating change must bump generation again, not just the first one");

        // clear() is itself treated as an invalidation (e.g. unload/reload, delete-material-data).
        long beforeClear = cache.generation();
        cache.clear();
        assertTrue(cache.generation() > beforeClear, "clear() must also bump generation");
    }

    @Test
    void generationNeverBumpsForAnOutOfRangeBlockChange() {
        var cache = new TerrainFactsCache(-64, 320);
        long initial = cache.generation();
        // Far below the chunk's height range: index() returns -1, so there is nothing to
        // invalidate and nothing for the shared cross-tick cache to react to.
        cache.blockChanged(0, -1000, 0, true, false);
        assertEquals(initial, cache.generation(), "a block change outside this chunk's tracked range must not bump");
    }

    private static int read(TerrainFactsCache cache, int x, int y, int z, int terrainAir, AtomicInteger scans) {
        int known = cache.emptyBlockCount(x, y, z);
        if (known >= 0) return known;
        scans.incrementAndGet();
        cache.put(x, y, z, terrainAir, false);
        return terrainAir;
    }
}
