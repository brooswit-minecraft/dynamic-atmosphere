package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Arrays;

/**
 * Fixed-size transient terrain facts for one loaded chunk: empty-block count and
 * downward-barrier presence per structural cell. Shared by Vapor, Smoke and every
 * {@link AtmosphereMaterial} grid, all of which use the same structural cell layout
 * ({@link AtmosphereGridLayout#CELL_SIZE}); each grid derives its own capacity from
 * these facts with its own formula instead of keeping a separate identical scan/cache.
 */
final class TerrainFactsCache {
    private static final int SIDE = 16 / AtmosphereGridLayout.CELL_SIZE;
    private static final int VOLUME =
        AtmosphereGridLayout.CELL_SIZE * AtmosphereGridLayout.CELL_SIZE * AtmosphereGridLayout.CELL_SIZE;
    private final int minCellY;
    private final byte[] emptyBlockCount;
    private final byte[] downwardBarrier;

    TerrainFactsCache(int minBlockY, int maxBlockY) {
        if (maxBlockY <= minBlockY) throw new IllegalArgumentException("Empty chunk height");
        minCellY = AtmosphereGridLayout.cellCoordinate(minBlockY);
        int maxCellY = AtmosphereGridLayout.cellCoordinate(maxBlockY - 1);
        emptyBlockCount = new byte[Math.multiplyExact(maxCellY - minCellY + 1, SIDE * SIDE)];
        downwardBarrier = new byte[emptyBlockCount.length];
        clear();
    }

    /** -1 means unknown and needs a terrain scan. */
    int emptyBlockCount(int x, int y, int z) {
        int index = index(x, y, z);
        return index < 0 ? -1 : emptyBlockCount[index];
    }

    /** -1 means unknown; otherwise 0/1. */
    int downwardBarrier(int x, int y, int z) {
        int index = index(x, y, z);
        return index < 0 || emptyBlockCount[index] < 0 ? -1 : downwardBarrier[index];
    }

    void put(int x, int y, int z, int emptyBlocks, boolean containsDownwardBarrier) {
        if (emptyBlocks < 0 || emptyBlocks > VOLUME) {
            throw new IllegalArgumentException("Invalid empty-block count");
        }
        int index = index(x, y, z);
        if (index >= 0) {
            emptyBlockCount[index] = (byte) emptyBlocks;
            downwardBarrier[index] = (byte) (containsDownwardBarrier ? 1 : 0);
        }
    }

    void blockChanged(int blockX, int blockY, int blockZ, boolean previousEmptySpace, boolean nextEmptySpace,
                      boolean previousDownwardBarrier, boolean nextDownwardBarrier) {
        if (previousEmptySpace == nextEmptySpace && previousDownwardBarrier == nextDownwardBarrier) return;
        int index = index(AtmosphereGridLayout.cellCoordinate(blockX), AtmosphereGridLayout.cellCoordinate(blockY),
            AtmosphereGridLayout.cellCoordinate(blockZ));
        if (index >= 0) emptyBlockCount[index] = -1;
    }

    void blockChanged(int blockX, int blockY, int blockZ, boolean previousEmptySpace, boolean nextEmptySpace) {
        blockChanged(blockX, blockY, blockZ, previousEmptySpace, nextEmptySpace, false, false);
    }

    void clear() {
        Arrays.fill(emptyBlockCount, (byte) -1);
        Arrays.fill(downwardBarrier, (byte) 0);
    }

    private int index(int x, int y, int z) {
        long row = (long) y - minCellY;
        if (row < 0 || row >= emptyBlockCount.length / (SIDE * SIDE)) return -1;
        return (int) row * SIDE * SIDE + Math.floorMod(z, SIDE) * SIDE + Math.floorMod(x, SIDE);
    }
}
