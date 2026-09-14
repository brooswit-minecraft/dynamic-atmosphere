package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeCapacityCacheTest {
    @Test
    void invalidatesOnlyChangedEightBlockCell() {
        var cache = new SmokeCapacityCache(-64, 320);
        cache.put(0, 8, 0, 900);
        cache.put(1, 8, 0, 800);

        cache.blockChanged(7, 64, 7, true, false);

        assertEquals(-1, cache.get(0, 8, 0));
        assertEquals(800, cache.get(1, 8, 0));
    }

    @Test
    void bedrockChangeInvalidatesSolidSmokeCell() {
        var cache = new SmokeCapacityCache(-64, 320);
        cache.put(0, 8, 0, 900, false);

        cache.blockChanged(7, 64, 7, false, false, false, true);

        assertEquals(-1, cache.get(0, 8, 0));
        assertEquals(-1, cache.bedrock(0, 8, 0));
    }
}
