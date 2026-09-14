package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-thread-only, coalesced event backlog. Rejection is explicit; existing entries are never evicted. */
public final class SmokeEmissionQueue<K> {
    public static final int DEFAULT_MAX_KEYS = 4096;
    public static final int DEFAULT_DRAIN_LIMIT = 128;
    public record Emission<K>(K key, int amount) { }

    private final int maxKeys;
    private final int maxAmount;
    private final Map<K, Integer> pending = new LinkedHashMap<>();
    private long rejectedAmount;

    public SmokeEmissionQueue() { this(DEFAULT_MAX_KEYS, AtmosphereGrid.MAX_STORED_AMOUNT); }

    public SmokeEmissionQueue(int maxKeys, int maxAmount) {
        if (maxKeys < 1 || maxAmount < 1) throw new IllegalArgumentException("Positive queue limits required");
        this.maxKeys = maxKeys;
        this.maxAmount = maxAmount;
    }

    /** Returns the accepted amount; callers can report the rejected remainder without recursive emissions. */
    public int offer(K key, int amount) {
        Objects.requireNonNull(key, "key");
        if (amount < 0) throw new IllegalArgumentException("Negative emission");
        if (amount == 0) return 0;
        Integer previous = pending.get(key);
        int accepted = previous == null && pending.size() >= maxKeys ? 0
            : Math.min(amount, maxAmount - (previous == null ? 0 : previous));
        if (accepted > 0) pending.put(key, (previous == null ? 0 : previous) + accepted);
        rejectedAmount += amount - accepted;
        return accepted;
    }

    /** Drain into a detached list before invoking grid/world callbacks; newly queued events wait for a later drain. */
    public List<Emission<K>> drain(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative drain limit");
        var result = new ArrayList<Emission<K>>(Math.min(limit, pending.size()));
        var entries = pending.entrySet().iterator();
        while (result.size() < limit && entries.hasNext()) {
            var entry = entries.next();
            result.add(new Emission<>(entry.getKey(), entry.getValue()));
            entries.remove();
        }
        return List.copyOf(result);
    }

    public int size() { return pending.size(); }
    public long rejectedAmount() { return rejectedAmount; }
    public void clear() { pending.clear(); rejectedAmount = 0; }
}
