package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;
import io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig;

/**
 * Client layout and palette. The RGB/opticalDensityMultiplier constructor args are the
 * CATALOG defaults only — both {@link #channel} and {@link #tuning} read the live client
 * config value at runtime, not these literals. MINECRAFT-21 made colour a client config
 * value (it was a hard-coded enum literal before); keep this literal and the matching
 * client config default (DynamicAtmosphereClientConfig) in sync for every value touched —
 * changing only one reproduces the "0.20.0 smokeOpticalDensity trap" where the catalog and
 * the config disagreed and the renderer, which reads config, never showed the change.
 * Smoke 0x0D0A08 is deliberately NOT pure black (0x000000): it's off-black specifically so
 * Smoke and Void Gas — the two most common heavy materials, both optical density 4x — stay
 * visually distinguishable in play. Obsidian Powder's 0x1A1025 is a PROPOSAL (ATMO-22
 * comment 23234); pending in-game visual confirmation, not a locked value.
 */
enum AtmosphereRenderMaterial {
    VAPOR(2, 0, 1.2), SMOKE(2, 0x0D0A08, 4),
    DUST(0, 0x8B4513, 1), OBSIDIAN_POWDER(0, 0x1A1025, 0.3),
    VOID_GAS(1, 0x000000, 4), EXHAUST(0, 0xFFFF00, 1),
    SLIME(1, 0x00FF00, 4), ZOMBIE_FOG(1, 0x808080, 4), NETHER_GAS(1, 0x6B8E23, 4),
    /** MINECRAFT-1 (B2): visual only, rendered unlit -- no real block light. */
    GLOW_GAS(0, 0xFFC83C, 2),
    /** MINECRAFT-1 (B3): very dark red; vapor-like root level, matching its vapor-like LOD. */
    NETHER_FOG(2, 0x400000, 1);

    final int cellSize = AtmosphereGridLayout.CELL_SIZE;
    final int rootLevel;
    final double opticalDensityMultiplier;
    private final int rgb;

    AtmosphereRenderMaterial(int rootLevel, int rgb, double opticalDensityMultiplier) {
        this.rootLevel = rootLevel;
        this.rgb = rgb;
        this.opticalDensityMultiplier = opticalDensityMultiplier;
    }

    AtmosphereClientCache newCache() {
        return new AtmosphereClientCache(DynamicAtmosphereClientConfig.snapshot().allocation().cellBudget(),
            cellSize, rootLevel, DynamicAtmosphereClientConfig.snapshot().maxDistanceMultiplier());
    }

    DynamicAtmosphereClientConfig.Material tuning() {
        var config = DynamicAtmosphereClientConfig.snapshot();
        return switch (this) {
            case VAPOR -> config.vapor();
            case SMOKE -> config.smoke();
            case DUST -> config.dust();
            case VOID_GAS -> config.voidGas();
            case EXHAUST -> config.exhaust();
            case SLIME -> config.slime();
            case OBSIDIAN_POWDER -> config.obsidianPowder();
            case ZOMBIE_FOG -> config.zombieFog();
            case NETHER_GAS -> config.netherGas();
            case GLOW_GAS -> config.glowGas();
            case NETHER_FOG -> config.netherFog();
        };
    }

    float channel(int channel, float[] fog) {
        // Reads the CLIENT CONFIG colour, not this enum's own rgb literal: the literal is
        // the catalog default only (see class javadoc re: the smokeOpticalDensity trap).
        if (this == VAPOR) return fog[channel];
        int colour = tuning().color();
        return ((colour >> (16 - 8 * channel)) & 255) / 255f;
    }
}
