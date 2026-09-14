package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;

/** Shared bounded crop/sapling growth policy for processed Vapor and Exhaust cells. */
public final class AtmospherePlantGrowth {
    public static final int COST = 40;
    public static final double CHANCE = 0.10;

    /** Runtime preflight value for callers that gate before invoking {@link #attempt}. */
    public static int minimumCost() {
        return DynamicAtmosphereServerConfig.snapshot().plantGrowth().cost();
    }

    /**
     * Tries every crop or sapling in one processed cell independently.
     *
     * <p>The caller maps indices to its own cell with x changing fastest,
     * followed by z and y. {@code eligiblePlantAt} must accept only non-mature
     * crops and saplings that pass vanilla bonemeal eligibility.
     * {@code applyBonemeal} must use vanilla's success rule, perform the effect,
     * and return true only when growth actually succeeded. The returned debit
     * belongs to the caller's source grid: the dedicated Vapor access for Vapor,
     * or {@code consumeMaterial(..., EXHAUST, ...)} for Exhaust.</p>
     */
    public static int attempt(
        int available,
        int cellSize,
        DoubleSupplier roll,
        IntPredicate eligiblePlantAt,
        IntPredicate applyBonemeal
    ) {
        DynamicAtmosphereServerConfig.PlantGrowth config =
            DynamicAtmosphereServerConfig.snapshot().plantGrowth();
        return attempt(available, cellSize, config.chance(), config.cost(), roll, eligiblePlantAt, applyBonemeal);
    }

    public static int attempt(
        int available, int cellSize, double chance, int cost, DoubleSupplier roll,
        IntPredicate eligiblePlantAt, IntPredicate applyBonemeal
    ) {
        if (cellSize <= 0 || cellSize > 16) throw new IllegalArgumentException("invalid cell size");
        if (available < cost) return 0;
        int consumed = 0;
        int checks = Math.multiplyExact(Math.multiplyExact(cellSize, cellSize), cellSize);
        for (int index = 0; index < checks && available - consumed >= cost; index++) {
            if (!eligiblePlantAt.test(index)) continue;
            double sample = roll.getAsDouble();
            if (!(sample >= 0 && sample < chance)) continue;
            if (applyBonemeal.test(index)) consumed += cost;
        }
        return consumed;
    }

    private AtmospherePlantGrowth() { }
}
