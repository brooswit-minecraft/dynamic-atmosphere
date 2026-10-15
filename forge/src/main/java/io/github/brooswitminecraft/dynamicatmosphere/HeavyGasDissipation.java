package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;

/**
 * Void Gas, Obsidian Powder and Slime loss once per processed simulation turn. Reuses
 * {@link SmokeDissipation}'s roll and {@code maxLoss} semantics at 1/factor of
 * Smoke's chance, so fade rate stays linear in the configured factor.
 */
public final class HeavyGasDissipation {
    /**
     * Independent roll, using the material remaining after other effects. Runtime must
     * subtract through the normal grid mutation path, including persisted/synced zero removal.
     */
    public static int amount(int remaining, double factor, DoubleSupplier roll) {
        var smoke = DynamicAtmosphereServerConfig.snapshot().smoke();
        return SmokeDissipation.amount(remaining, effectiveChance(smoke.dissipationChance(), factor),
            smoke.dissipationAmount(), roll);
    }

    /** Smoke's chance divided by the factor, clamped to [0, 1] for any valid (finite, positive) factor. */
    public static double effectiveChance(double smokeChance, double factor) {
        if (!Double.isFinite(factor) || factor <= 0) {
            throw new IllegalArgumentException("factor must be finite and greater than zero");
        }
        return Math.min(1.0, Math.max(0.0, smokeChance / factor));
    }

    private HeavyGasDissipation() { }
}
