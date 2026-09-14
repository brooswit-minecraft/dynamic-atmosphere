package io.github.brooswitminecraft.dynamicatmosphere;

/** MVP removal default: five Vapor per layer-equivalent, forty per full snow or ice block. */
public final class SnowRemovalRules {
    public static final int FULL_BLOCK_LAYERS = 8;
    public static final int VAPOR_PER_LAYER = 5;
    public static final int VAPOR_PER_ICE_BLOCK = 40;

    /** Only a successful mutation can emit; transitions within the snow family emit the net loss. */
    public static int material(boolean succeeded, int previousLayers, int nextLayers) {
        if (previousLayers < 0 || previousLayers > FULL_BLOCK_LAYERS
            || nextLayers < 0 || nextLayers > FULL_BLOCK_LAYERS) {
            throw new IllegalArgumentException("Snow layer equivalents must be in 0..8");
        }
        return succeeded ? Math.max(0, previousLayers - nextLayers) * VAPOR_PER_LAYER : 0;
    }

    /** Includes ice-to-water; any later water loss is a distinct mutation handled by the water hook. */
    public static int iceMaterial(boolean succeeded, boolean previousIce, boolean nextIce) {
        return succeeded && previousIce && !nextIce ? VAPOR_PER_ICE_BLOCK : 0;
    }

    private SnowRemovalRules() { }
}
