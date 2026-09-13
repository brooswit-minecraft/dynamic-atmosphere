package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereLightCacheTest {
    private static AtmosphereClientCache.Cell cell(int x) { return new AtmosphereClientCache.Cell(x, 0, 0); }

    @Test
    void meanIncludesDarkAirButExcludesSolidBlocks() {
        assertEquals(0.5f, AtmosphereLightCache.meanAirLight(index -> index == 0 ? 15 : index == 1 ? 0 : -1));
        assertEquals(0, AtmosphereLightCache.meanAirLight(index -> 0));
        assertEquals(1, AtmosphereLightCache.meanAirLight(index -> 15));
        assertEquals(0, AtmosphereLightCache.meanAirLight(index -> -1));
        var blocks = new AtomicInteger();
        AtmosphereLightCache.meanAirLight(index -> { blocks.incrementAndGet(); return 15; });
        assertEquals(64, blocks.get());
    }

    @Test
    void renderRequestsDoNotSampleAndTicksHaveAFixedBudget() {
        var cache = new AtmosphereLightCache();
        for (int x = 0; x < 100; x++) {
            assertEquals(0.5f, cache.value(cell(x)));
            cache.value(cell(x));
        }
        assertEquals(100, cache.pendingSize());
        var calls = new AtomicInteger();
        cache.advance(key -> { calls.incrementAndGet(); return 0.2; });
        assertEquals(32, calls.get());
        assertEquals(68, cache.pendingSize());
        assertEquals(0.2f, cache.value(cell(0)));
        cache.advance(key -> { calls.incrementAndGet(); return 0.8; });
        assertEquals(64, calls.get());
    }

    @Test
    void lightingRefreshesAfterTimeWithoutPerFrameTerrainReads() {
        var cache = new AtmosphereLightCache();
        cache.value(cell(0));
        cache.advance(key -> 1);
        for (int tick = 0; tick < AtmosphereLightCache.REFRESH_TICKS; tick++) {
            cache.advance(key -> { fail("No new request should sample"); return 0; });
        }
        assertEquals(1, cache.value(cell(0)));
        cache.advance(key -> 0);
        assertEquals(0, cache.value(cell(0)));
    }

    @Test
    void unloadedSamplesKeepFallbackAndOldViewRequestsExpire() {
        var cache = new AtmosphereLightCache();
        cache.value(cell(0));
        cache.advance(key -> Double.NaN);
        assertEquals(0.5f, cache.value(cell(0)));
        cache.clear();
        for (int x = 0; x < 2000; x++) cache.value(cell(x));
        var calls = new AtomicInteger();
        for (int tick = 0; tick < 70; tick++) cache.advance(key -> { calls.incrementAndGet(); return 1; });
        assertEquals(32 * 40, calls.get());
        assertEquals(0, cache.pendingSize());
    }

    @Test
    void cacheAndQueueAreBoundedAndWorldClearDropsLighting() {
        var cache = new AtmosphereLightCache();
        for (int x = 0; x < AtmosphereLightCache.MAX_CELLS + 100; x++) cache.value(cell(x));
        assertEquals(AtmosphereLightCache.MAX_CELLS, cache.size());
        assertEquals(AtmosphereLightCache.MAX_CELLS, cache.pendingSize());
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.pendingSize());
        assertEquals(0.5f, cache.value(cell(0)));
    }
}
