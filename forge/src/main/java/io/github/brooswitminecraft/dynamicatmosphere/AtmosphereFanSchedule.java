package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
import java.util.function.Predicate;

/** Independent fan cadence with bounded loaded-chunk work and no probability gate. */
final class AtmosphereFanSchedule<K> {
    private final AtmosphereProducerSchedule<K> queue = new AtmosphereProducerSchedule<>();
    private long lastBoundary = -1;
    private long cycles;

    List<K> poll(long tick, int interval, int budget, Iterable<K> loaded, Predicate<K> active) {
        if (tick < 0 || interval <= 0) throw new IllegalArgumentException("invalid fan cadence");
        if (tick > 0 && tick % interval == 0 && tick != lastBoundary) {
            lastBoundary = tick;
            if (queue.beginCycle(loaded)) cycles++;
        }
        return queue.poll(budget, active);
    }

    long cycles() { return cycles; }
    int pending() { return queue.pending(); }
    void clear() { queue.clear(); lastBoundary = -1; cycles = 0; }
}
