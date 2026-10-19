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
 * MINECRAFT-398: Dust's catalog literal moved from 0x8B4513 (saddle brown) to 0xD2B48C (tan,
 * a sandier hue); kept in sync with {@code DynamicAtmosphereClientConfig}'s {@code dustColor}
 * default per the smokeOpticalDensity-trap warning above.
 */
enum AtmosphereRenderMaterial {
    VAPOR(2, 0, 1.2), SMOKE(2, 0x0D0A08, 4),
    DUST(0, 0xD2B48C, 1), OBSIDIAN_POWDER(0, 0x1A1025, 0.3),
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
        return tuning(DynamicAtmosphereClientConfig.snapshot());
    }

    /** Same lookup, with the config snapshot given explicitly so a per-frame caller reads the
     *  one snapshot it already captured for the whole frame, rather than each material call
     *  taking its own independent (and potentially differently-timed) snapshot. */
    DynamicAtmosphereClientConfig.Material tuning(DynamicAtmosphereClientConfig.Snapshot config) {
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
        return channel(channel, fog, DynamicAtmosphereClientConfig.snapshot());
    }

    /** Same channel lookup, with the config snapshot given explicitly; see {@link #tuning(
     *  DynamicAtmosphereClientConfig.Snapshot)}. */
    float channel(int channel, float[] fog, DynamicAtmosphereClientConfig.Snapshot config) {
        // Reads the CLIENT CONFIG colour, not this enum's own rgb literal: the literal is
        // the catalog default only (see class javadoc re: the smokeOpticalDensity trap).
        if (this == VAPOR) return fog[channel];
        int colour = tuning(config).color();
        return ((colour >> (16 - 8 * channel)) & 255) / 255f;
    }

    /**
     * MINECRAFT-387: Vapor keeps its fog-derived RGB untouched (see {@link #channel}); only its
     * alpha changes, and only as a function of that same fog colour's luminance. Modelled as
     * extra optical density rather than a direct alpha multiply: boosting the transmittance
     * EXPONENT is exactly equivalent to multiplying {@code opticalDensityMultiplier} by the same
     * factor (compare {@link AtmosphereVolumeGeometry#sliceAlpha}), so it composes with the
     * existing amount/thickness math instead of fighting it, preserves alpha=0 -> 0 (empty or
     * fully distance-faded Vapor stays invisible), and stays in [0,1] for any finite exponent
     * >= 1 without a separate clamp needing to rescue it.
     * <p>Luminance uses the Rec. 709 weights on the fog triplet already composited by the
     * shader (no extra gamma step introduced). The darkness term is squared so the curve is flat
     * near full daylight (dL/dluminance == 0 at luminance 1): ordinary day/dusk/biome fog drift
     * produces no visible opacity jump, only true near-black fog pushes the exponent toward its
     * configured cap.
     * <p>A non-finite luminance (NaN fog channel from some future codec/mod conflict) folds to
     * luminance 1, i.e. boost disabled -- the safe direction, since it leaves Vapor exactly as it
     * rendered before this change rather than guessing a dark value that saturates opacity.
     * A non-finite input alpha folds to 0 (fully transparent): {@code Math.clamp} does NOT
     * sanitize {@code NaN} (a NaN compares false against both bounds and passes through
     * unchanged), so that case is handled explicitly, before any clamp, rather than relying on
     * clamp to rescue it.
     */
    static float vaporOpacity(float alpha, float[] fog) {
        return vaporOpacity(alpha, fog, DynamicAtmosphereClientConfig.snapshot().vaporDarknessOpacityBoost());
    }

    /** Same curve as {@link #vaporOpacity(float, float[])}, with the boost cap given explicitly
     *  (instead of read from the live config) so tests can exercise every limit (1, the
     *  configured default, and the 6 ceiling) without depending on a mutable global default. */
    static float vaporOpacity(float alpha, float[] fog, double boost) {
        if (!Float.isFinite(alpha)) return 0f;
        double luminance = 0.2126 * fog[0] + 0.7152 * fog[1] + 0.0722 * fog[2];
        if (!Double.isFinite(luminance)) luminance = 1;
        luminance = Math.clamp(luminance, 0, 1);
        double darkness = 1 - luminance;
        double exponent = 1 + (boost - 1) * darkness * darkness;
        double transmittance = Math.pow(1 - Math.clamp((double) alpha, 0, 1), exponent);
        return (float) Math.clamp(1 - transmittance, 0, 1);
    }

    /**
     * The exact combination {@link AtmosphereVolumeRenderer}'s {@code visit()} uses for every
     * VAPOR slice: the fog-darkness boost (extra optical density) applied to the RAW
     * density-derived alpha, THEN distance fade (a plain post-hoc alpha multiply) on the
     * boosted result -- never the other order. Boosting an already-faded alpha would make the
     * fade band disproportionately more opaque than the unfaded volume, since the boost is
     * nonlinear in alpha. Kept as one method precisely so a renderer edit that reorders or
     * drops either step breaks here instead of only being reachable by eye in a live client.
     */
    static float vaporSliceAlpha(float rawAlpha, float fade, float[] fog) {
        return vaporSliceAlpha(rawAlpha, fade, fog, DynamicAtmosphereClientConfig.snapshot().vaporDarknessOpacityBoost());
    }

    /** Same combination as {@link #vaporSliceAlpha(float, float, float[])}, with the boost cap
     *  given explicitly; see that overload's javadoc for why the ordering matters. */
    static float vaporSliceAlpha(float rawAlpha, float fade, float[] fog, double boost) {
        if (!Float.isFinite(fade)) return 0f;
        return (float) Math.clamp((double) vaporOpacity(rawAlpha, fog, boost) * fade, 0, 1);
    }
}
