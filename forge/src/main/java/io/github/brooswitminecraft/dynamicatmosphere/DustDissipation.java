package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;

/** Dust-only loss once per processed Dust simulation turn. */
public final class DustDissipation {
    public static final int MAX_LOSS = 40;
    public static final double CHANCE = 1.0 / 64;

    public static int amount(int remaining, DoubleSupplier roll) {
        DynamicAtmosphereServerConfig.Dust config = DynamicAtmosphereServerConfig.snapshot().dust();
        return amount(remaining, roll, config.dissipationChance(), config.dissipationAmount());
    }

    public static int amount(int remaining, DoubleSupplier roll, double chance, int maximumLoss) {
        if (remaining <= 0) return 0;
        double sample = roll.getAsDouble();
        return sample >= 0 && sample < chance ? Math.min(remaining, maximumLoss) : 0;
    }

    private DustDissipation() { }
}
