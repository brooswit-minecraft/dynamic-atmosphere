package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.IntUnaryOperator;
import java.util.function.ToDoubleFunction;

/** Disposable client lighting: render requests values; only client ticks sample terrain. */
final class AtmosphereLightCache {
    static final int SAMPLES_PER_TICK = 32;
    static final int MAX_CELLS = 8192;
    static final int REFRESH_TICKS = 20;
    static final float DEFAULT_GRAY = 0.5f;
    private final LinkedHashMap<AtmosphereClientCache.Cell, Entry> cells = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashSet<AtmosphereClientCache.Cell> pending = new LinkedHashSet<>();
    private long tick;

    private static final class Entry {
        float gray = DEFAULT_GRAY;
        long sampled = -REFRESH_TICKS;
        long seen;
    }

    float value(AtmosphereClientCache.Cell cell) {
        Entry entry = cells.computeIfAbsent(cell, ignored -> new Entry());
        entry.seen = tick;
        if (tick - entry.sampled >= REFRESH_TICKS) pending.add(cell);
        if (cells.size() > MAX_CELLS) {
            var oldest = cells.keySet().iterator();
            pending.remove(oldest.next());
            oldest.remove();
        }
        return entry.gray;
    }

    void advance(ToDoubleFunction<AtmosphereClientCache.Cell> sample) {
        tick++;
        var iterator = pending.iterator();
        for (int work = 0; work < SAMPLES_PER_TICK && iterator.hasNext(); work++) {
            var cell = iterator.next();
            iterator.remove();
            Entry entry = cells.get(cell);
            if (entry == null || tick - entry.seen > REFRESH_TICKS * 2) continue;
            double gray = sample.applyAsDouble(cell);
            if (Double.isFinite(gray)) entry.gray = (float) Math.clamp(gray, 0, 1);
            entry.sampled = tick;
        }
    }

    /** Negative samples are non-air; zero-light air still contributes to the denominator. */
    static float meanAirLight(IntUnaryOperator lightAt) {
        int size = AtmosphereVolumeGeometry.CELL_SIZE;
        int sum = 0, air = 0;
        for (int index = 0; index < size * size * size; index++) {
            int light = lightAt.applyAsInt(index);
            if (light < 0) continue;
            sum += Math.clamp(light, 0, 15);
            air++;
        }
        return air == 0 ? 0 : sum / (air * 15.0f);
    }

    void clear() { cells.clear(); pending.clear(); tick = 0; }
    int size() { return cells.size(); }
    int pendingSize() { return pending.size(); }
}
