package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToDoubleFunction;

/** Disposable client lighting: render requests values; only client ticks sample terrain. */
final class AtmosphereLightCache {
    static final int SAMPLES_PER_TICK = 32;
    static final int MAX_CELLS = 8192;
    static final int REFRESH_TICKS = 20;
    static final float DEFAULT_GRAY = 0.5f;
    private record Volume(AtmosphereClientCache.Cell origin, int level) { }
    record Sample(float gray, int airBlocks) { }
    private final LinkedHashMap<Volume, Entry> cells = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashSet<Volume> pending = new LinkedHashSet<>();
    private long tick;

    private static final class Entry {
        float gray = DEFAULT_GRAY;
        long sampled = -REFRESH_TICKS;
        long seen;
        int nextCell;
        int airBlocks;
        double graySum;
    }

    float value(AtmosphereClientCache.Cell cell) {
        return value(cell, 0);
    }

    float value(AtmosphereClientCache.Cell origin, int level) {
        if (level < 0 || level > 3) throw new IllegalArgumentException("invalid light volume level");
        var volume = new Volume(origin, level);
        Entry entry = cells.computeIfAbsent(volume, ignored -> new Entry());
        entry.seen = tick;
        if (tick - entry.sampled >= REFRESH_TICKS && pending.add(volume)) {
            entry.nextCell = 0;
            entry.airBlocks = 0;
            entry.graySum = 0;
        }
        if (cells.size() > MAX_CELLS) {
            var oldest = cells.keySet().iterator();
            pending.remove(oldest.next());
            oldest.remove();
        }
        return entry.gray;
    }

    void advance(ToDoubleFunction<AtmosphereClientCache.Cell> sample) {
        advanceSamples(cell -> {
            double gray = sample.applyAsDouble(cell);
            return Double.isFinite(gray) ? new Sample((float) Math.clamp(gray, 0, 1), 64) : null;
        });
    }

    void advanceSamples(Function<AtmosphereClientCache.Cell, Sample> sample) {
        tick++;
        for (int work = 0; work < SAMPLES_PER_TICK && !pending.isEmpty(); work++) {
            var iterator = pending.iterator();
            var volume = iterator.next();
            iterator.remove();
            Entry entry = cells.get(volume);
            if (entry == null || tick - entry.seen > REFRESH_TICKS * 2) continue;
            int edge = 1 << volume.level();
            int index = entry.nextCell++;
            var origin = volume.origin();
            Sample result = sample.apply(new AtmosphereClientCache.Cell(origin.x() + index % edge,
                origin.y() + index / (edge * edge), origin.z() + (index / edge) % edge));
            // An unloaded child leaves the last complete value intact, never a partial mean.
            if (result != null && Float.isFinite(result.gray())) {
                entry.graySum += Math.clamp(result.gray(), 0, 1) * Math.max(0, result.airBlocks());
                entry.airBlocks += Math.max(0, result.airBlocks());
                if (entry.nextCell < edge * edge * edge) {
                    pending.add(volume);
                    continue;
                }
                entry.gray = entry.airBlocks == 0 ? 0 : (float) (entry.graySum / entry.airBlocks);
            }
            entry.sampled = tick;
        }
    }

    /** Negative samples are non-air; zero-light air still contributes to the denominator. */
    static float meanAirLight(IntUnaryOperator lightAt) {
        return airLight(lightAt).gray();
    }

    static Sample airLight(IntUnaryOperator lightAt) {
        int size = AtmosphereVolumeGeometry.CELL_SIZE;
        int sum = 0, air = 0;
        for (int index = 0; index < size * size * size; index++) {
            int light = lightAt.applyAsInt(index);
            if (light < 0) continue;
            sum += Math.clamp(light, 0, 15);
            air++;
        }
        return new Sample(air == 0 ? 0 : sum / (air * 15.0f), air);
    }

    void clear() { cells.clear(); pending.clear(); tick = 0; }
    int size() { return cells.size(); }
    int pendingSize() { return pending.size(); }
}
