package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Main-thread state; no Minecraft dependencies so packet/lifecycle behavior is testable. */
public final class AtmosphereClientCache {
    public static final int MAX_CELLS = 4096;
    private static final int TRANSITION_TICKS = 10;

    public record Cell(int x, int y, int z) { }
    public record Update(Cell cell, int amount, int capacity) { }
    /** Render amount is interpolated fullness scaled to 0..1000, not stored material. */
    public record VisibleCell(Cell cell, float amount) { }
    private record Amount(float from, float target, long since, int material, int capacity) {
        float at(double tick) {
            double progress = Math.clamp((tick - since) / TRANSITION_TICKS, 0.0, 1.0);
            return (float) (from + (target - from) * progress);
        }
    }

    private final Map<Cell, Amount> cells = new LinkedHashMap<>();
    private String dimension;
    private boolean hasSnapshot;
    private long tick;

    public void changeDimension(String nextDimension) {
        if (!Objects.equals(dimension, nextDimension)) {
            clear();
            dimension = nextDimension;
        }
    }

    public void clear() {
        cells.clear();
        dimension = null;
        hasSnapshot = false;
        tick = 0;
    }

    public void apply(String packetDimension, boolean reset, List<Update> updates) {
        if (dimension == null || !dimension.equals(packetDimension) || (!reset && !hasSnapshot)) {
            return;
        }
        Map<Cell, Amount> previous = reset ? new LinkedHashMap<>(cells) : cells;
        if (reset) {
            cells.clear();
            hasSnapshot = true;
        }
        // Bound even an unexpectedly large decoded update list.
        for (int i = 0; i < Math.min(updates.size(), MAX_CELLS); i++) {
            Update update = updates.get(i);
            int material = Math.clamp(update.amount(), 0, 1_000_000);
            int capacity = Math.clamp(update.capacity(), 0, 1000);
            float target = capacity == 0 ? 0 : Math.min(1000, material * 1000.0f / capacity);
            Amount old = previous.get(update.cell());
            if (reset && material == 0) {
                continue;
            }
            if (old == null && material == 0) {
                continue;
            }
            if (old != null && old.material() == material && old.capacity() == capacity) {
                if (reset) {
                    cells.put(update.cell(), old);
                }
                continue;
            }
            if (old == null && cells.size() >= MAX_CELLS) {
                // Fading removals must not displace newly subscribed authoritative cells.
                var fading = cells.entrySet().iterator();
                while (fading.hasNext()) {
                    if (fading.next().getValue().material() == 0) {
                        fading.remove();
                        break;
                    }
                }
                if (cells.size() >= MAX_CELLS) {
                    continue;
                }
            }
            cells.put(update.cell(), new Amount(old == null ? 0 : old.at(tick), target, tick, material, capacity));
        }
    }

    public void advance() {
        tick++;
        cells.values().removeIf(amount -> amount.material() == 0 && tick - amount.since() >= TRANSITION_TICKS);
    }

    public List<VisibleCell> visible(float partialTick) {
        List<VisibleCell> result = new ArrayList<>(cells.size());
        for (var entry : cells.entrySet()) {
            float amount = entry.getValue().at(tick + Math.clamp(partialTick, 0, 1));
            if (amount > 0) {
                result.add(new VisibleCell(entry.getKey(), amount));
            }
        }
        return result;
    }

    public int size() {
        return cells.size();
    }

    int storedAmount(Cell cell) {
        Amount amount = cells.get(cell);
        return amount == null ? 0 : amount.material();
    }
}
