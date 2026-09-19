package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialCapacityCacheTest {

    @Test
    void fluidTransitionsInvalidateCapacityAndBarrierTogether() {
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            var cache = new MaterialCapacityCache(material, -64, 320);
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
    }

    @Test
    void dustAndEnderKeepSeparateCachesDespiteSharingCellCoordinates() {
        var dust = new MaterialCapacityCache(AtmosphereMaterial.DUST, -64, 320);
        var ender = new MaterialCapacityCache(AtmosphereMaterial.ENDER_GAS, -64, 320);
        dust.put(7, 32, 7, 500, false);
        ender.put(7, 32, 7, 1000, false);

        dust.blockChanged(31, 128, 31, true, false, false, false);
        ender.blockChanged(31, 128, 31, true, false, false, false);

        assertEquals(-1, dust.get(7, 32, 7));
        assertEquals(-1, ender.get(7, 32, 7));
    }

    @Test
    void bedrockChangeInvalidatesWithoutAirCapacityChange() {
        var cache = new MaterialCapacityCache(AtmosphereMaterial.ENDER_GAS, -64, 320);
        cache.put(0, 0, 0, 0, false);

        cache.blockChanged(0, 0, 0, false, false, false, true);

        assertEquals(-1, cache.get(0, 0, 0));
        assertEquals(-1, cache.downwardBarrier(0, 0, 0));
    }
}
