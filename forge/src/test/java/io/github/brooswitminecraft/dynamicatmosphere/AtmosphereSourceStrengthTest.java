package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

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
    void materialDoesNotPassivelyDecayWhenDarkGroundSourceTurnsOff() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> cell = new AtmosphereGrid.CellKey<>("overworld", 0, 16, 0);
        grid.emit(cell, AtmosphereSourceStrength.lightToEmission(0), 100, 1000);

        assertEquals(0, AtmosphereSourceStrength.lightToEmission(15));
        grid.spread(AtmosphereGridLayout.nextSimulationTick(100), ignored -> 0);
        assertEquals(40, grid.cells().getFirst().amount());
    }

    @Test
    void rainSplitIsInclusiveAndAlwaysConservesItsTotal() {
        assertEquals(new AtmosphereSourceStrength.RainSplit(0, 320),
            AtmosphereSourceStrength.splitRain(320, 0));
        assertEquals(new AtmosphereSourceStrength.RainSplit(137, 183),
            AtmosphereSourceStrength.splitRain(320, 137));
        assertEquals(new AtmosphereSourceStrength.RainSplit(320, 0),
            AtmosphereSourceStrength.splitRain(320, 320));
    }

    @Test
    void independentRainSourcesConserveTotalWhenTheyShareOneCell() {
        AtmosphereGrid<String> grid = new AtmosphereGrid<>();
        AtmosphereGrid.CellKey<String> cell = new AtmosphereGrid.CellKey<>("overworld", 1, 2, 3);
        var sources = new HashSet<String>();
        var rain = AtmosphereSourceStrength.splitRain(320, 137);

        assertTrue(AtmosphereGrid.emitSourceOnce(sources, "rain-ground", grid,
            cell, rain.ground(), 1, 1000));
        assertTrue(AtmosphereGrid.emitSourceOnce(sources, "rain-cloud", grid,
            cell, rain.cloud(), 1, 1000));

        assertEquals(320, grid.get(cell).amount());
    }
}
