package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;

/** Dust-only loss once per processed Dust simulation turn. */
public final class DustDissipation {
    public static final int MAX_LOSS = 40;
    public static final double CHANCE = 1.0 / 64;

    public static int amount(int remaining, DoubleSupplier roll) {
        if (remaining <= 0) return 0;
        double chance = roll.getAsDouble();
        return chance >= 0 && chance < CHANCE ? Math.min(remaining, MAX_LOSS) : 0;
    }

    private DustDissipation() { }
}
