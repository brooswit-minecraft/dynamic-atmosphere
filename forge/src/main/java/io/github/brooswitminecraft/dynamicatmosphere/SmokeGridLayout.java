package io.github.brooswitminecraft.dynamicatmosphere;

/** World-aligned layout for the independent sparse smoke grid. */
public final class SmokeGridLayout {
    public static final int CELL_SIZE = 8;
    public static final int SIMULATION_INTERVAL_TICKS = 200;
    public static final int PRODUCER_INTERVAL_TICKS = 300;
    private static final int CELLS_PER_CHUNK = 16 / CELL_SIZE;

    private SmokeGridLayout() { }

    public static int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CELL_SIZE);
    }

    public static int chunkCoordinate(int cellCoordinate) {
        return Math.floorDiv(cellCoordinate, CELLS_PER_CHUNK);
    }

    public static int capacityForAirBlocks(int airBlocks) {
        int volume = CELL_SIZE * CELL_SIZE * CELL_SIZE;
        return Math.clamp(airBlocks, 0, volume) * AtmosphereGrid.MAX_AMOUNT / volume;
    }

    public static long nextSimulationTick(long tick) {
        return Math.multiplyExact(Math.floorDiv(tick, SIMULATION_INTERVAL_TICKS) + 1,
            (long) SIMULATION_INTERVAL_TICKS);
    }
}
