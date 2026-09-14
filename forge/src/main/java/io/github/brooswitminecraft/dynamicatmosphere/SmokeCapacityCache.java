package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Arrays;

/** Fixed-size transient capacity slots for one smoke chunk. */
final class SmokeCapacityCache {
    private static final int SIDE = 16 / SmokeGridLayout.CELL_SIZE;
    private final int minCellY;
    private final short[] values;
    private final byte[] downwardBarrier;

    SmokeCapacityCache(int minBlockY, int maxBlockY) {
        if (maxBlockY <= minBlockY) throw new IllegalArgumentException("Empty chunk height");
        minCellY = SmokeGridLayout.cellCoordinate(minBlockY);
        int maxCellY = SmokeGridLayout.cellCoordinate(maxBlockY - 1);
        values = new short[Math.multiplyExact(maxCellY - minCellY + 1, SIDE * SIDE)];
        downwardBarrier = new byte[values.length];
        clear();
    }

    int get(int x, int y, int z) {
        int index = index(x, y, z);
        return index < 0 ? -1 : values[index];
    }

    void put(int x, int y, int z, int capacity) {
        put(x, y, z, capacity, false);
    }

    void put(int x, int y, int z, int capacity, boolean containsDownwardBarrier) {
        if (capacity < 0 || capacity > AtmosphereGrid.MAX_AMOUNT) {
            throw new IllegalArgumentException("Invalid smoke capacity");
        }
        int index = index(x, y, z);
        if (index >= 0) {
            values[index] = (short) capacity;
            downwardBarrier[index] = (byte) (containsDownwardBarrier ? 1 : 0);
        }
    }

    int downwardBarrier(int x, int y, int z) {
        int index = index(x, y, z);
        return index < 0 || values[index] < 0 ? -1 : downwardBarrier[index];
    }

    void blockChanged(int x, int y, int z, boolean previousEmptySpace, boolean nextEmptySpace,
                      boolean previousDownwardBarrier, boolean nextDownwardBarrier) {
        if (previousEmptySpace == nextEmptySpace && previousDownwardBarrier == nextDownwardBarrier) return;
        int index = index(SmokeGridLayout.cellCoordinate(x), SmokeGridLayout.cellCoordinate(y),
            SmokeGridLayout.cellCoordinate(z));
        if (index >= 0) values[index] = -1;
    }

    void blockChanged(int x, int y, int z, boolean previousEmptySpace, boolean nextEmptySpace) {
        blockChanged(x, y, z, previousEmptySpace, nextEmptySpace, false, false);
    }

    void clear() {
        Arrays.fill(values, (short) -1);
        Arrays.fill(downwardBarrier, (byte) 0);
    }

    private int index(int x, int y, int z) {
        long row = (long) y - minCellY;
        if (row < 0 || row >= values.length / (SIDE * SIDE)) return -1;
        return (int) row * SIDE * SIDE + Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE);
    }
}
