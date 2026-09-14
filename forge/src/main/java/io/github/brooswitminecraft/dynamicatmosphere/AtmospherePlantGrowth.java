package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;

/** Shared bounded crop/sapling growth policy for processed Vapor and Exhaust cells. */
public final class AtmospherePlantGrowth {
    public static final int COST = 40;
    public static final double CHANCE = 0.10;

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
        if (cellSize <= 0 || cellSize > 16) throw new IllegalArgumentException("invalid cell size");
        if (available < COST) return 0;
        int consumed = 0;
        int checks = Math.multiplyExact(Math.multiplyExact(cellSize, cellSize), cellSize);
        for (int index = 0; index < checks && available - consumed >= COST; index++) {
            if (!eligiblePlantAt.test(index)) continue;
            double chance = roll.getAsDouble();
            if (!(chance >= 0 && chance < CHANCE)) continue;
            if (applyBonemeal.test(index)) consumed += COST;
        }
        return consumed;
    }

    private AtmospherePlantGrowth() { }
}
