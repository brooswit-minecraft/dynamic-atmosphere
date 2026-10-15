package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;

/**
 * Smoke's own loss once per processed Smoke simulation turn; never apply to Vapor.
 * Also reused by {@link HeavyGasDissipation} for Void Gas, Obsidian Powder and Slime at a
 * reduced chance.
 */
public final class SmokeDissipation {
    public static final int MAX_LOSS = 40;
    public static final double CHANCE = 10.0 / 64;

    /**
     * Independent roll, using the material remaining after other effects. Runtime must
     * subtract through the normal grid mutation path, including persisted/synced zero removal.
     */
    public static int amount(int remaining, DoubleSupplier roll) {
        var smoke = DynamicAtmosphereServerConfig.snapshot().smoke();
        return amount(remaining, smoke.dissipationChance(), smoke.dissipationAmount(), roll);
    }

    /** Effective chance overload for the runtime configuration boundary. */
    public static int amount(int remaining, double chance, DoubleSupplier roll) {
        return amount(remaining, chance, MAX_LOSS, roll);
    }

    /** Effective chance and maximum loss overload used by reloadable server configuration. */
    public static int amount(int remaining, double chance, int maxLoss, DoubleSupplier roll) {
        requireChance(chance);
        if (maxLoss < 0) throw new IllegalArgumentException("maximum loss must be non-negative");
        if (remaining <= 0) return 0;
        double result = roll.getAsDouble();
        return result >= 0 && result < chance ? Math.min(remaining, maxLoss) : 0;
    }

    private static void requireChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("chance must be finite and within [0, 1]");
        }
    }

    private SmokeDissipation() { }
}
