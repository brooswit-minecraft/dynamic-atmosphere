package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/** Small deterministic store for bounded, expiring fog observations. */
final class FogPatchTracker<K> {

    record Patch<K>(K key, int waterDepth, int strength, long lastSeenTick, long lastUpdateTick) {
    }

    private static final int MAX_WATER_DEPTH = 8;

    private final int capacity;
    private final long expiryTicks;
    private final long decayIntervalTicks;
    private final Map<K, Patch<K>> patches = new LinkedHashMap<>();

    FogPatchTracker(int capacity, long expiryTicks, long decayIntervalTicks) {
        if (capacity <= 0 || expiryTicks <= 0 || decayIntervalTicks <= 0) {
            throw new IllegalArgumentException("capacity, expiryTicks, and decayIntervalTicks must be positive");
        }
        this.capacity = capacity;
        this.expiryTicks = expiryTicks;
        this.decayIntervalTicks = decayIntervalTicks;
    }

    boolean observe(K key, int waterDepth, long tick) {
        int boundedDepth = Math.max(1, Math.min(MAX_WATER_DEPTH, waterDepth));
        Patch<K> current = patches.get(key);
        if (current != null && current.lastSeenTick() == tick) {
            patches.put(key, new Patch<>(
                key, boundedDepth, current.strength(), current.lastSeenTick(), current.lastUpdateTick()));
            return true;
        }
        if (current == null && patches.size() >= capacity) {
            Map.Entry<K, Patch<K>> oldest = patches.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().lastSeenTick()))
                .orElseThrow();
            if (oldest.getValue().lastSeenTick() >= tick) {
                return false;
            }
            patches.remove(oldest.getKey());
        }
        int strength = current == null ? 1 : stepToward(current.strength(), boundedDepth);
        patches.put(key, new Patch<>(key, boundedDepth, strength, tick, tick));
        return true;
    }

    int advance(long tick) {
        int removed = 0;
        for (Map.Entry<K, Patch<K>> entry : List.copyOf(patches.entrySet())) {
            Patch<K> patch = entry.getValue();
            if (tick - patch.lastSeenTick() >= expiryTicks) {
                patches.remove(entry.getKey());
                removed++;
                continue;
            }
            if (patch.lastSeenTick() == tick) {
                continue;
            }

            long steps = Math.max(0, (tick - patch.lastUpdateTick()) / decayIntervalTicks);
            if (steps == 0) {
                continue;
            }
            int strength = (int) Math.max(0, patch.strength() - steps);
            if (strength == 0) {
                patches.remove(entry.getKey());
                removed++;
            } else {
                long updateTick = patch.lastUpdateTick() + steps * decayIntervalTicks;
                patches.put(entry.getKey(), new Patch<>(
                    patch.key(), patch.waterDepth(), strength, patch.lastSeenTick(), updateTick));
            }
        }
        return removed;
    }

    List<Patch<K>> patches() {
        return List.copyOf(patches.values());
    }

    int size() {
        return patches.size();
    }

    void clear() {
        patches.clear();
    }

    static int strengthForDepth(int waterDepth) {
        return Math.max(1, Math.min(MAX_WATER_DEPTH, waterDepth));
    }

    private static int stepToward(int current, int target) {
        if (current < target) {
            return current + 1;
        }
        if (current > target) {
            return current - 1;
        }
        return current;
    }
}
