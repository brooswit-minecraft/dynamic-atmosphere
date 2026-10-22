package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.BooleanSupplier;

/** One paid attempt. The action is never called without funds; failure never charges. */
final class MoldGrowth {
    static int attempt(int available, int capacity, int cost, double threshold,
                       double probability, double roll, BooleanSupplier action) {
        if (cost <= 0 || available < cost || capacity <= 0
            || available / (double) capacity < threshold || !(roll < probability)) return 0;
        return action.getAsBoolean() ? cost : 0;
    }

    private MoldGrowth() { }
}
