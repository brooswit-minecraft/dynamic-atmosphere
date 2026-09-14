package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;

/** Smoke-only loss once per processed Smoke simulation turn; never apply to Vapor. */
public final class SmokeDissipation {
    public static final int MAX_LOSS = 40;
    public static final double CHANCE = 1.0 / 64;

    /**
     * Independent roll, using the material remaining after other effects. Runtime must
     * subtract through the normal grid mutation path, including persisted/synced zero removal.
     */
    public static int amount(int remaining, DoubleSupplier roll) {
        if (remaining <= 0) return 0;
        double chance = roll.getAsDouble();
        return chance >= 0 && chance < CHANCE ? Math.min(remaining, MAX_LOSS) : 0;
    }

    private SmokeDissipation() { }
}
