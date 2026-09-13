package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/** Fair, bounded producer-cycle queue with a pure probability gate. */
final class AtmosphereProducerSchedule<K> {

    private final ArrayDeque<K> pending = new ArrayDeque<>();

    static boolean passesChance(double chance, double roll) {
        if (!(chance >= 0.0 && chance <= 1.0)) {
            throw new IllegalArgumentException("chance must be in [0, 1]");
        }
        if (!(roll >= 0.0 && roll < 1.0)) {
            throw new IllegalArgumentException("roll must be in [0, 1)");
        }
        return roll < chance;
    }

    boolean beginCycle(Iterable<K> keys) {
        if (!pending.isEmpty()) {
            return false;
        }
        var unique = new LinkedHashSet<K>();
        keys.forEach(unique::add);
        pending.addAll(unique);
        return true;
    }

    List<K> poll(int budget, Predicate<K> active) {
        if (budget <= 0) {
            throw new IllegalArgumentException("budget must be positive");
        }
        var result = new ArrayList<K>(Math.min(budget, pending.size()));
        for (int examined = 0; examined < budget && !pending.isEmpty(); examined++) {
            K key = pending.removeFirst();
            if (active.test(key)) {
                result.add(key);
            }
        }
        return List.copyOf(result);
    }

    int pending() {
        return pending.size();
    }

    void clear() {
        pending.clear();
    }
}
