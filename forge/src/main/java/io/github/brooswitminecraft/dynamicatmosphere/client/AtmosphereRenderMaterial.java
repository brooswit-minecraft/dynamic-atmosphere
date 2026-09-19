package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;
import io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig;

/** Client layout and palette. Brown/purple RGB values are presentation defaults. */
enum AtmosphereRenderMaterial {
    VAPOR(2, 2, 0, 1), SMOKE(2, 2, 0x000000, 4),
    DUST(0, 0.25, 0x8B4513, 1), ENDER_GAS(0, 0.25, 0x800080, 40),
    VIOLENCE(1, 2, 0xFF0000, 4), EXHAUST(0, 0.25, 0xFFFF00, 1),
    SLIME(1, 2, 0x00FF00, 4);

    final int cellSize = AtmosphereGridLayout.CELL_SIZE;
    final int rootLevel;
    final double reach;
    final double opticalDensityMultiplier;
    private final int rgb;

    AtmosphereRenderMaterial(int rootLevel, double reach, int rgb, double opticalDensityMultiplier) {
        this.rootLevel = rootLevel;
        this.reach = reach;
        this.rgb = rgb;
        this.opticalDensityMultiplier = opticalDensityMultiplier;
    }

    AtmosphereClientCache newCache() {
        return new AtmosphereClientCache(DynamicAtmosphereClientConfig.snapshot().allocation().cellBudget(),
            cellSize, rootLevel, tuning().reachMultiplier());
    }

    DynamicAtmosphereClientConfig.Material tuning() {
        var config = DynamicAtmosphereClientConfig.snapshot();
        return switch (this) {
            case VAPOR -> config.vapor();
            case SMOKE -> config.smoke();
            case DUST -> config.dust();
            case VIOLENCE -> config.violence();
            case EXHAUST -> config.exhaust();
            case SLIME -> config.slime();
            case ENDER_GAS -> config.enderGas();
        };
    }

    float channel(int channel, float[] fog) {
        return this == VAPOR ? fog[channel] : ((rgb >> (16 - 8 * channel)) & 255) / 255f;
    }
}
