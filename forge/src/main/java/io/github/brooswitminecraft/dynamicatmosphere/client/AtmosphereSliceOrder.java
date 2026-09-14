package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Merge per-volume depth-sorted slices across materials, preserving order across GPU batches. */
final class AtmosphereSliceOrder {
    record ColoredSlice(AtmosphereVolumeGeometry.Slice slice, boolean smoke) { }
    private static final class Cursor {
        final List<AtmosphereVolumeGeometry.Slice> slices;
        final boolean smoke;
        final long order;
        int index;
        Cursor(List<AtmosphereVolumeGeometry.Slice> slices, boolean smoke, long order) {
            this.slices = slices;
            this.smoke = smoke;
            this.order = order;
            index = slices.size() - 1;
        }
        double depth() { return slices.get(index).depth(); }
    }

    private final PriorityQueue<Cursor> queue = new PriorityQueue<>(
        Comparator.comparingDouble(Cursor::depth).reversed().thenComparingLong(cursor -> cursor.order));
    private long sequence;

    void add(List<AtmosphereVolumeGeometry.Slice> slices, boolean smoke) {
        if (!slices.isEmpty()) queue.add(new Cursor(slices, smoke, sequence++));
    }

    boolean isEmpty() { return queue.isEmpty(); }

    ColoredSlice next() {
        Cursor cursor = queue.remove();
        var result = new ColoredSlice(cursor.slices.get(cursor.index), cursor.smoke);
        if (--cursor.index >= 0) queue.add(cursor);
        return result;
    }
}
