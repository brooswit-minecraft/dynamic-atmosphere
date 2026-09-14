package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Arrays;

/** Fixed-size transient capacity slots for one chunk; -1 means it needs a terrain read. */
final class AtmosphereCapacityCache {
    private static final int SIDE = 16 / AtmosphereGridLayout.CELL_SIZE;
    private final int minCellY;
    private final short[] values;
    private final byte[] downwardBarrier;

    AtmosphereCapacityCache(int minBlockY, int maxBlockY) {
        if (maxBlockY <= minBlockY) throw new IllegalArgumentException("Empty chunk height");
        minCellY = AtmosphereGridLayout.cellCoordinate(minBlockY);
        int maxCellY = AtmosphereGridLayout.cellCoordinate(maxBlockY - 1);
        values = new short[Math.multiplyExact(maxCellY - minCellY + 1, SIDE * SIDE)];
        downwardBarrier = new byte[values.length];
        clear();
    }

    int get(int cellX, int cellY, int cellZ) {
        int index = index(cellX, cellY, cellZ);
        return index < 0 ? -1 : values[index];
    }

    void put(int cellX, int cellY, int cellZ, int capacity) {
        put(cellX, cellY, cellZ, capacity, false);
    }

    void put(int cellX, int cellY, int cellZ, int capacity, boolean containsDownwardBarrier) {
        if (capacity < 0 || capacity > 1000) throw new IllegalArgumentException("Invalid capacity");
        int index = index(cellX, cellY, cellZ);
        if (index >= 0) {
            values[index] = (short) capacity;
            downwardBarrier[index] = (byte) (containsDownwardBarrier ? 1 : 0);
        }
    }

    int downwardBarrier(int cellX, int cellY, int cellZ) {
        int index = index(cellX, cellY, cellZ);
        return index < 0 || values[index] < 0 ? -1 : downwardBarrier[index];
    }

    void blockChanged(int x, int y, int z, boolean previousEmptySpace, boolean nextEmptySpace,
                      boolean previousDownwardBarrier, boolean nextDownwardBarrier) {
        if (previousEmptySpace == nextEmptySpace && previousDownwardBarrier == nextDownwardBarrier) return;
        int index = index(AtmosphereGridLayout.cellCoordinate(x),
            AtmosphereGridLayout.cellCoordinate(y), AtmosphereGridLayout.cellCoordinate(z));
        if (index >= 0) values[index] = -1;
    }

    void blockChanged(int x, int y, int z, boolean previousEmptySpace, boolean nextEmptySpace) {
        blockChanged(x, y, z, previousEmptySpace, nextEmptySpace, false, false);
    }

    void clear() {
        Arrays.fill(values, (short) -1);
        Arrays.fill(downwardBarrier, (byte) 0);
    }

    int size() { return values.length; }

    private int index(int x, int y, int z) {
        long row = (long) y - minCellY;
        if (row < 0 || row >= values.length / (SIDE * SIDE)) return -1;
        return (int) row * SIDE * SIDE + Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE);
    }
}
