package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;

/** One bounded leaf-removal attempt per processed Smoke turn; runtime owns loaded-only block access. */
public final class SmokeLeafEffect {
    public static final int COST = 40;
    public static final double CHANCE = 1.0;
    public static final int CELL_SIZE = SmokeGridLayout.CELL_SIZE;
    public static final int MAX_CHECKS = CELL_SIZE * CELL_SIZE * CELL_SIZE;

    /**
     * Returns material to consume, never consumes on behalf of the grid. Invoke once per selected turn.
     * Indices are x-fastest, then z, then y within the Smoke cell. The runtime must
     * use BlockTags.LEAVES, reject unavailable chunks, recheck before removal, and
     * supply the actual mutation result. No drop policy is imposed by this helper.
     */
    public static int attempt(int available, DoubleSupplier roll, IntPredicate leafAt, IntPredicate removeLeaf) {
        var smoke = DynamicAtmosphereServerConfig.snapshot().smoke();
        return attempt(available, smoke.leafRemovalChance(), smoke.leafRemovalCost(), roll, leafAt, removeLeaf);
    }

    /** Effective chance overload for the runtime configuration boundary. */
    public static int attempt(int available, double chance, DoubleSupplier roll,
                              IntPredicate leafAt, IntPredicate removeLeaf) {
        return attempt(available, chance, COST, roll, leafAt, removeLeaf);
    }

    /** Effective chance and cost overload used by reloadable server configuration. */
    public static int attempt(int available, double chance, int cost, DoubleSupplier roll,
                              IntPredicate leafAt, IntPredicate removeLeaf) {
        requireChance(chance);
        if (cost < 0) throw new IllegalArgumentException("cost must be non-negative");
        if (available < cost) return 0;
        double result = roll.getAsDouble();
        if (!(result >= 0 && result < chance)) return 0;
        for (int index = 0; index < MAX_CHECKS; index++) {
            if (leafAt.test(index)) return removeLeaf.test(index) ? cost : 0;
        }
        return 0;
    }

    private static void requireChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("chance must be finite and within [0, 1]");
        }
    }

    private SmokeLeafEffect() { }
}
