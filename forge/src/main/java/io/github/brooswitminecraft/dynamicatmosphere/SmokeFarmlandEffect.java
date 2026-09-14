package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;

/** Independent farmland roll per processed Smoke turn, with bounded cell-local inspection. */
public final class SmokeFarmlandEffect {
    public static final int COST = 40;
    public static final double CHANCE = 10.0 / 128;
    public static final int CELL_SIZE = SmokeGridLayout.CELL_SIZE;
    public static final int MAX_CHECKS = CELL_SIZE * CELL_SIZE * CELL_SIZE;

    /**
     * Returns material to consume only after successful conversion. Pass the amount remaining
     * after other effects, and a fresh roll independent of the leaf effect. Indices map to
     * local x first, then z, then y within the cell. Runtime callbacks must use loaded-only
     * access, recheck FARMLAND, and set DIRT with normal block updates for crop behavior.
     * If a required neighboring chunk is unavailable, defer rather than force loading it.
     */
    public static int attempt(int available, DoubleSupplier roll, IntPredicate farmlandAt,
                              IntPredicate convertToDirt) {
        var smoke = DynamicAtmosphereServerConfig.snapshot().smoke();
        return attempt(available, smoke.farmlandConversionChance(), smoke.farmlandConversionCost(),
            roll, farmlandAt, convertToDirt);
    }

    /** Effective chance overload for the runtime configuration boundary. */
    public static int attempt(int available, double chance, DoubleSupplier roll,
                              IntPredicate farmlandAt, IntPredicate convertToDirt) {
        return attempt(available, chance, COST, roll, farmlandAt, convertToDirt);
    }

    /** Effective chance and cost overload used by reloadable server configuration. */
    public static int attempt(int available, double chance, int cost, DoubleSupplier roll,
                              IntPredicate farmlandAt, IntPredicate convertToDirt) {
        requireChance(chance);
        if (cost < 0) throw new IllegalArgumentException("cost must be non-negative");
        if (available < cost) return 0;
        double result = roll.getAsDouble();
        if (!(result >= 0 && result < chance)) return 0;
        for (int index = 0; index < MAX_CHECKS; index++) {
            if (farmlandAt.test(index)) return convertToDirt.test(index) ? cost : 0;
        }
        return 0;
    }

    private static void requireChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("chance must be finite and within [0, 1]");
        }
    }

    private SmokeFarmlandEffect() { }
}
