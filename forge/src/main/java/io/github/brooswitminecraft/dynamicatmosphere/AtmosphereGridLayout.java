package io.github.brooswitminecraft.dynamicatmosphere;

/** Shared world-to-atmosphere-grid coordinate layout. */
public final class AtmosphereGridLayout {

    public static final int CELL_SIZE = 4;
    private static final int MINECRAFT_CHUNK_SIZE = 16;
    private static final int CELLS_PER_CHUNK = MINECRAFT_CHUNK_SIZE / CELL_SIZE;

    private AtmosphereGridLayout() {
    }

    public static int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CELL_SIZE);
    }

    public static int chunkCoordinate(int cellCoordinate) {
        return Math.floorDiv(cellCoordinate, CELLS_PER_CHUNK);
    }
}
