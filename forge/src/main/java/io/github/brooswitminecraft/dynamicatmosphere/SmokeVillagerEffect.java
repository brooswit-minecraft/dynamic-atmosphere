package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Iterator;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Independent cell-local villager effect; no query is requested before the cost/chance gates. */
public final class SmokeVillagerEffect {
    public static final int COST = 40;
    public static final double CHANCE = 10.0 / 256;
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
        var smoke = DynamicAtmosphereServerConfig.snapshot().smoke();
        return attempt(available, smoke.villagerConversionChance(), smoke.villagerConversionCost(),
            roll, query, eligible, convert);
    }

    /** Effective chance overload for the runtime configuration boundary. */
    public static <T> int attempt(int available, double chance, DoubleSupplier roll,
                                 Supplier<? extends Iterator<T>> query,
                                 Predicate<T> eligible, Predicate<T> convert) {
        return attempt(available, chance, COST, roll, query, eligible, convert);
    }

    /** Effective chance and cost overload used by reloadable server configuration. */
    public static <T> int attempt(int available, double chance, int cost, DoubleSupplier roll,
                                 Supplier<? extends Iterator<T>> query,
                                 Predicate<T> eligible, Predicate<T> convert) {
        requireChance(chance);
        if (cost < 0) throw new IllegalArgumentException("cost must be non-negative");
        if (available < cost) return 0;
        double result = roll.getAsDouble();
        if (!(result >= 0 && result < chance)) return 0;
        Iterator<T> candidates = query.get();
        for (int checked = 0; checked < MAX_CANDIDATES && candidates.hasNext(); checked++) {
            T candidate = candidates.next();
            if (eligible.test(candidate)) return convert.test(candidate) ? cost : 0;
        }
        return 0;
    }

    private static void requireChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("chance must be finite and within [0, 1]");
        }
    }

    private SmokeVillagerEffect() { }
}
