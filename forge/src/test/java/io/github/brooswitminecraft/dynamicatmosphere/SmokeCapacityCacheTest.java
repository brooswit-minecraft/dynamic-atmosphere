package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeCapacityCacheTest {

    @Test
    void fluidTransitionsInvalidateCapacityAndBarrierTogether() {
        var cache = new SmokeCapacityCache(-64, 320);
        // Air to liquid changes only the downward barrier.
        cache.put(0, 0, 0, 1000, false);
        AtmosphereFluidTransport.enter();
        try {
            cache.blockChanged(0, 0, 0, true, true, false, true);
        } finally {
            AtmosphereFluidTransport.exit();
        }
        assertEquals(-1, cache.get(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));

        // Source/flowing level changes retain the same two classifications.
        cache.put(0, 0, 0, 1000, true);
        cache.blockChanged(0, 0, 0, true, true, true, true);
        assertEquals(1000, cache.get(0, 0, 0));
        assertEquals(1, cache.downwardBarrier(0, 0, 0));

        cache.blockChanged(0, 0, 0, true, true, true, false);
        assertEquals(-1, cache.get(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));

        // Waterlogging and draining never change the host's occupancy.
        cache.put(0, 0, 0, 0, false);
        cache.blockChanged(0, 0, 0, false, false, false, true);
        assertEquals(-1, cache.get(0, 0, 0));
        cache.put(0, 0, 0, 0, true);
        cache.blockChanged(0, 0, 0, false, false, true, false);
        assertEquals(-1, cache.get(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));

        // Replacing a liquid with a waterlogged host changes only capacity.
        cache.put(0, 0, 0, 1000, true);
        cache.blockChanged(0, 0, 0, true, false, true, true);
        assertEquals(-1, cache.get(0, 0, 0));
        cache.put(0, 0, 0, 0, false);
        assertEquals(0, cache.downwardBarrier(0, 0, 0));
        cache.clear();
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));
    }

    @Test
    void invalidatesOnlyChangedFourBlockCell() {
        var cache = new SmokeCapacityCache(-64, 320);
        cache.put(0, 16, 0, 900);
        cache.put(1, 16, 0, 800);

        cache.blockChanged(3, 64, 3, true, false);

        assertEquals(-1, cache.get(0, 16, 0));
        assertEquals(800, cache.get(1, 16, 0));
    }

    @Test
    void bedrockChangeInvalidatesSolidSmokeCell() {
        var cache = new SmokeCapacityCache(-64, 320);
        cache.put(0, 16, 0, 900, false);

        cache.blockChanged(3, 64, 3, false, false, false, true);

        assertEquals(-1, cache.get(0, 16, 0));
        assertEquals(-1, cache.downwardBarrier(0, 16, 0));
    }
}
