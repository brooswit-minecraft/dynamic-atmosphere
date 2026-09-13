package io.github.brooswitminecraft.dynamicatmosphere;

/** Shared world-to-atmosphere-grid coordinate layout. */
public final class AtmosphereGridLayout {

    public static final int CELL_SIZE = 4;
    public static final int SIMULATION_INTERVAL_TICKS = 200;
    public static final int PRODUCER_INTERVAL_TICKS = 50;
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

    public static int capacityForAirBlocks(int airBlocks) {
        int volume = CELL_SIZE * CELL_SIZE * CELL_SIZE;
        return Math.clamp(airBlocks, 0, volume) * 1000 / volume;
    }

    public static int simulationIntervalTicks() {
        return SIMULATION_INTERVAL_TICKS;
    }

    public static boolean isSimulationTick(long tick) {
        return tick > 0 && tick % SIMULATION_INTERVAL_TICKS == 0;
    }

    public static long nextSimulationTick(long tick) {
        return Math.multiplyExact(Math.floorDiv(tick, SIMULATION_INTERVAL_TICKS) + 1,
            (long) SIMULATION_INTERVAL_TICKS);
    }
}
