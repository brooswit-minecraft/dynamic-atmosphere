package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereCapacityCacheTest {
    @Test
    void repeatedViewsReuseCapacityUntilAirOccupancyChanges() {
        var cache = new AtmosphereCapacityCache(-64, 320);
        var reads = new AtomicInteger();
        assertEquals(1000, read(cache, 0, 0, 0, 1000, reads));
        assertEquals(1000, read(cache, 0, 0, 0, 500, reads));
        assertEquals(1, reads.get());
        cache.blockChanged(3, 2, 1, true, false);
        assertEquals(500, read(cache, 0, 0, 0, 500, reads));
        assertEquals(2, reads.get());
        cache.blockChanged(3, 2, 1, false, true);
        assertEquals(1000, read(cache, 0, 0, 0, 1000, reads));
        assertEquals(3, reads.get());
    }

    @Test
    void sameOccupancyAndOtherCellsDoNotInvalidate() {
        var cache = new AtmosphereCapacityCache(-64, 320);
        cache.put(0, 0, 0, 0);
        cache.put(1, 0, 0, 500);
        cache.blockChanged(0, 0, 0, false, false);
        assertEquals(0, cache.get(0, 0, 0));
        cache.blockChanged(4, 0, 0, true, false);
        assertEquals(0, cache.get(0, 0, 0));
        assertEquals(-1, cache.get(1, 0, 0));
    }

    @Test
    void bedrockChangeInvalidatesEvenWhenBothBlocksAreSolid() {
        var cache = new AtmosphereCapacityCache(-64, 320);
        cache.put(0, 0, 0, 500, false);

        cache.blockChanged(1, 1, 1, false, false, false, true);

        assertEquals(-1, cache.get(0, 0, 0));
        assertEquals(-1, cache.bedrock(0, 0, 0));
    }

    @Test
    void negativeCoordinatesAndVerticalEdgesStayInTheirOwnSlots() {
        var cache = new AtmosphereCapacityCache(-64, 320);
        assertEquals(1536, cache.size());
        cache.put(-1, -16, -1, 250);
        cache.put(-4, 79, -4, 750);
        cache.blockChanged(-1, -64, -1, false, true);
        assertEquals(-1, cache.get(-1, -16, -1));
        assertEquals(750, cache.get(-4, 79, -4));
        cache.blockChanged(-16, 320, -16, true, false);
        assertEquals(750, cache.get(-4, 79, -4));
        assertEquals(-1, cache.get(-4, 80, -4));
        assertEquals(-1, cache.get(-4, -17, -4));
    }

    @Test
    void transportSuppressionDoesNotSuppressInvalidation() {
        var cache = new AtmosphereCapacityCache(-64, 320);
        cache.put(0, 0, 0, 500);
        AtmosphereFluidTransport.enter();
        try {
            cache.blockChanged(0, 0, 0, false, true);
            assertEquals(-1, cache.get(0, 0, 0));
        } finally {
            AtmosphereFluidTransport.exit();
        }
    }

    @Test
    void unloadAndNewChunkIdentityStartUnknown() {
        var old = new AtmosphereCapacityCache(-64, 320);
        old.put(0, 0, 0, 1000);
        assertEquals(-1, new AtmosphereCapacityCache(-64, 320).get(0, 0, 0));
        old.clear();
        assertEquals(-1, old.get(0, 0, 0));
    }

    private static int read(AtmosphereCapacityCache cache, int x, int y, int z,
                            int terrainCapacity, AtomicInteger reads) {
        int known = cache.get(x, y, z);
        if (known >= 0) return known;
        reads.incrementAndGet();
        cache.put(x, y, z, terrainCapacity);
        return terrainCapacity;
    }
}
