package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Phase 4 (section 36): small per-dimension overrides layered onto the default atmosphere
 * behavior. Deliberately not a general registry: the design brief asks dimension
 * configuration to "remain relatively small," and today only the End has a documented
 * override (section 35). A pure lookup, so it is testable without a live server config.
 */
public final class DimensionAtmosphereConfigs {
    private DimensionAtmosphereConfigs() { }

    public static DynamicAtmosphereServerConfig.DimensionAtmosphereConfig forDimension(
        ResourceKey<Level> dimension
    ) {
        return forDimension(dimension, DynamicAtmosphereServerConfig.snapshot().dimensions().theEnd());
    }

    static DynamicAtmosphereServerConfig.DimensionAtmosphereConfig forDimension(
        ResourceKey<Level> dimension, DynamicAtmosphereServerConfig.DimensionAtmosphereConfig theEnd
    ) {
        return Level.END.equals(dimension) ? theEnd : DynamicAtmosphereServerConfig.DimensionAtmosphereConfig.DEFAULT;
    }
}
