package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereSourceStrengthTest {

    @Test
    void lightToEmissionHasClampedMonotonicEndpoints() {
        assertEquals(40, AtmosphereSourceStrength.lightToEmission(-1));
        assertEquals(40, AtmosphereSourceStrength.lightToEmission(0));
        assertEquals(0, AtmosphereSourceStrength.lightToEmission(15));
        assertEquals(0, AtmosphereSourceStrength.lightToEmission(16));

        for (int light = 0; light < 15; light++) {
            assertTrue(
                AtmosphereSourceStrength.lightToEmission(light)
                    >= AtmosphereSourceStrength.lightToEmission(light + 1));
        }
    }

    @Test
    void materialDecaysWhenDarkGroundSourceTurnsOff() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>(4, 10);
        AtmosphereGrid.CellKey<String> cell = new AtmosphereGrid.CellKey<>("overworld", 0, 16, 0);
        grid.emit(cell, AtmosphereSourceStrength.lightToEmission(0), 100);

        grid.decay(100);
        assertEquals(30, grid.cells().getFirst().amount());
        assertEquals(0, AtmosphereSourceStrength.lightToEmission(15));
        grid.decay(200);
        assertEquals(20, grid.cells().getFirst().amount());
    }
}
