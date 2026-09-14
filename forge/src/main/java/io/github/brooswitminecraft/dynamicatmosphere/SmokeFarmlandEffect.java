package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;

/** Independent farmland roll per processed Smoke turn, with bounded cell-local inspection. */
public final class SmokeFarmlandEffect {
    public static final int COST = 40;
    public static final double CHANCE = 1.0 / 128;
    public static final int CELL_SIZE = 8;
    public static final int MAX_CHECKS = CELL_SIZE * CELL_SIZE * CELL_SIZE;

    /**
     * Returns material to consume only after successful conversion. Pass the amount remaining
     * after other effects, and a fresh roll independent of the leaf effect. Indices map to
     * local x=index%8, z=(index/8)%8, y=index/64. Runtime callbacks must use loaded-only
     * access, recheck FARMLAND, and set DIRT with normal block updates for crop behavior.
     * If a required neighboring chunk is unavailable, defer rather than force loading it.
     */
    public static int attempt(int available, DoubleSupplier roll, IntPredicate farmlandAt,
                              IntPredicate convertToDirt) {
        if (available < COST) return 0;
        double chance = roll.getAsDouble();
        if (!(chance >= 0 && chance < CHANCE)) return 0;
        for (int index = 0; index < MAX_CHECKS; index++) {
            if (farmlandAt.test(index)) return convertToDirt.test(index) ? COST : 0;
        }
        return 0;
    }

    private SmokeFarmlandEffect() { }
}
