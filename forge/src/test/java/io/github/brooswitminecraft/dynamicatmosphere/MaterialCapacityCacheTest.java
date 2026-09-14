package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialCapacityCacheTest {
    @Test
    void dustAndEnderUseTheirOwnCellCoordinates() {
        var dust = new MaterialCapacityCache(AtmosphereMaterial.DUST, -64, 320);
        var ender = new MaterialCapacityCache(AtmosphereMaterial.ENDER_GAS, -64, 320);
        dust.put(7, 32, 7, 500, false);
        ender.put(15, 64, 15, 1000, false);

        dust.blockChanged(15, 64, 15, true, false, false, false);
        ender.blockChanged(15, 64, 15, true, false, false, false);

        assertEquals(-1, dust.get(7, 32, 7));
        assertEquals(-1, ender.get(15, 64, 15));
    }

    @Test
    void bedrockChangeInvalidatesWithoutAirCapacityChange() {
        var cache = new MaterialCapacityCache(AtmosphereMaterial.ENDER_GAS, -64, 320);
        cache.put(0, 0, 0, 0, false);

        cache.blockChanged(0, 0, 0, false, false, false, true);

        assertEquals(-1, cache.get(0, 0, 0));
        assertEquals(-1, cache.bedrock(0, 0, 0));
    }
}
