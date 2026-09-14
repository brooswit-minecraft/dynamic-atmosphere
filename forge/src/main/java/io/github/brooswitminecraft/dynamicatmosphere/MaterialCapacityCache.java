package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Arrays;

/** Fixed-size transient terrain cache for one material in one loaded chunk. */
final class MaterialCapacityCache {
    private final AtmosphereMaterial material;
    private final int side;
    private final int minCellY;
    private final short[] capacities;
    private final byte[] downwardBarrier;

    MaterialCapacityCache(AtmosphereMaterial material, int minBlockY, int maxBlockY) {
        if (maxBlockY <= minBlockY) throw new IllegalArgumentException("Empty chunk height");
        this.material = material;
        side = 16 / material.cellSize();
        minCellY = material.cellCoordinate(minBlockY);
        int maxCellY = material.cellCoordinate(maxBlockY - 1);
        capacities = new short[Math.multiplyExact(maxCellY - minCellY + 1, side * side)];
        downwardBarrier = new byte[capacities.length];
        clear();
    }

    int get(int x, int y, int z) {
        int index = index(x, y, z);
        return index < 0 ? -1 : capacities[index];
    }

    int downwardBarrier(int x, int y, int z) {
        int index = index(x, y, z);
        return index < 0 || capacities[index] < 0 ? -1 : downwardBarrier[index];
    }

    void put(int x, int y, int z, int capacity, boolean containsDownwardBarrier) {
        if (capacity < 0 || capacity > AtmosphereGrid.MAX_AMOUNT) {
            throw new IllegalArgumentException("Invalid material capacity");
        }
        int index = index(x, y, z);
        if (index >= 0) {
            capacities[index] = (short) capacity;
            downwardBarrier[index] = (byte) (containsDownwardBarrier ? 1 : 0);
        }
    }

    void blockChanged(int blockX, int blockY, int blockZ, boolean previousEmptySpace, boolean nextEmptySpace,
                      boolean previousDownwardBarrier, boolean nextDownwardBarrier) {
        if (previousEmptySpace == nextEmptySpace && previousDownwardBarrier == nextDownwardBarrier) return;
        int index = index(material.cellCoordinate(blockX), material.cellCoordinate(blockY),
            material.cellCoordinate(blockZ));
        if (index >= 0) capacities[index] = -1;
    }

    void clear() {
        Arrays.fill(capacities, (short) -1);
        Arrays.fill(downwardBarrier, (byte) 0);
    }

    private int index(int x, int y, int z) {
        long row = (long) y - minCellY;
        if (row < 0 || row >= capacities.length / (side * side)) return -1;
        return (int) row * side * side + Math.floorMod(z, side) * side + Math.floorMod(x, side);
    }
}
