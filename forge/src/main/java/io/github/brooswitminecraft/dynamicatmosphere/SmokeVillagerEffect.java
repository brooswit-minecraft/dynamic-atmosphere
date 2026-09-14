package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Iterator;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Independent cell-local villager effect; no query is requested before the cost/chance gates. */
public final class SmokeVillagerEffect {
    public static final int COST = 40;
    public static final double CHANCE = 1.0 / 256;
    public static final int MAX_CANDIDATES = 128;

    /**
     * Runtime must provide a loaded-only, bounded cell query (not a global entity list),
     * and check each villager's position belongs to this cell, not just AABB overlap.
     * Conversion mutates the existing entity. Pass current remaining Smoke and an
     * independent roll once per processed turn. Returns the cost only on success.
     */
    public static <T> int attempt(int available, DoubleSupplier roll,
                                 Supplier<? extends Iterator<T>> query,
                                 Predicate<T> eligible, Predicate<T> convert) {
        if (available < COST) return 0;
        double chance = roll.getAsDouble();
        if (!(chance >= 0 && chance < CHANCE)) return 0;
        Iterator<T> candidates = query.get();
        for (int checked = 0; checked < MAX_CANDIDATES && candidates.hasNext(); checked++) {
            T candidate = candidates.next();
            if (eligible.test(candidate)) return convert.test(candidate) ? COST : 0;
        }
        return 0;
    }

    private SmokeVillagerEffect() { }
}
