package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;

/** One bounded leaf-removal attempt per processed Smoke turn; runtime owns loaded-only block access. */
public final class SmokeLeafEffect {
    public static final int COST = 40;
    public static final double CHANCE = 0.1;
    public static final int CELL_SIZE = 8;
    public static final int MAX_CHECKS = CELL_SIZE * CELL_SIZE * CELL_SIZE;

    /**
     * Returns material to consume, never consumes on behalf of the grid. Invoke once per selected turn.
     * Indices map to local x=index%8, z=(index/8)%8, y=index/64. The runtime must
     * use BlockTags.LEAVES, reject unavailable chunks, recheck before removal, and
     * supply the actual mutation result. No drop policy is imposed by this helper.
     */
    public static int attempt(int available, DoubleSupplier roll, IntPredicate leafAt, IntPredicate removeLeaf) {
        if (available < COST) return 0;
        double chance = roll.getAsDouble();
        if (!(chance >= 0 && chance < CHANCE)) return 0;
        for (int index = 0; index < MAX_CHECKS; index++) {
            if (leafAt.test(index)) return removeLeaf.test(index) ? COST : 0;
        }
        return 0;
    }

    private SmokeLeafEffect() { }
}
