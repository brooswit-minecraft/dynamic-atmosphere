package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DimensionAtmosphereConfigsTest {
    private static final DynamicAtmosphereServerConfig.DimensionAtmosphereConfig END_OVERRIDE =
        new DynamicAtmosphereServerConfig.DimensionAtmosphereConfig(false, 100.0);

    @Test
    void theEndUsesItsOwnOverride() {
        assertSame(END_OVERRIDE, DimensionAtmosphereConfigs.forDimension(Level.END, END_OVERRIDE));
    }

    @Test
    void everyOtherDimensionUsesTheDefault() {
        assertEquals(DynamicAtmosphereServerConfig.DimensionAtmosphereConfig.DEFAULT,
            DimensionAtmosphereConfigs.forDimension(Level.OVERWORLD, END_OVERRIDE));
        assertEquals(DynamicAtmosphereServerConfig.DimensionAtmosphereConfig.DEFAULT,
            DimensionAtmosphereConfigs.forDimension(Level.NETHER, END_OVERRIDE));
    }

    @Test
    void defaultHonorsPreferredAltitudeAtUnitMultiplier() {
        assertEquals(true, DynamicAtmosphereServerConfig.DimensionAtmosphereConfig.DEFAULT.honorPreferredAltitude());
        assertEquals(1.0, DynamicAtmosphereServerConfig.DimensionAtmosphereConfig.DEFAULT.globalDissipationMultiplier());
    }

    @Test
    void rejectsNonPositiveOrNonFiniteMultiplier() {
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DynamicAtmosphereServerConfig.DimensionAtmosphereConfig(true, bad));
        }
    }
}
