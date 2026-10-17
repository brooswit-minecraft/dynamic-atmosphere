package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

/**
 * The generic, Minecraft-free snapshot a {@link Condition} is evaluated against.
 *
 * <p>Every field here is a plain measurement the Forge adapter already has to hand (a
 * cell's amount/capacity ratio, whether it is currently precipitating, and the biome
 * temperature at the cell's origin). Nothing here is specific to any one material:
 * Lightning (Cloud/Vapor), a Ghast spawn (Smoke), a Zombie Fog spawn modifier, and a
 * Slime Fog precipitation rule all read the exact same context shape.
 */
public record EventContext(double concentration, boolean precipitating, double biomeTemperature) {
    public EventContext {
        if (!Double.isFinite(concentration) || concentration < 0 || concentration > 1) {
            throw new IllegalArgumentException("concentration must be within [0, 1]");
        }
        if (!Double.isFinite(biomeTemperature)) {
            throw new IllegalArgumentException("biomeTemperature must be finite");
        }
    }

    /** Convenience for the common case of deriving concentration from amount/capacity. */
    public static EventContext of(int amount, int capacity, boolean precipitating, double biomeTemperature) {
        double concentration = capacity <= 0 ? 0 : Math.clamp((double) amount / capacity, 0, 1);
        return new EventContext(concentration, precipitating, biomeTemperature);
    }
}
