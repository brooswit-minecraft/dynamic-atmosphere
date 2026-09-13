package io.github.brooswitminecraft.dynamicatmosphere;

/** Shared world-to-atmosphere-grid coordinate layout. */
public final class AtmosphereGridLayout {

    public static final int CELL_SIZE = 4;
    private static final int MINECRAFT_CHUNK_SIZE = 16;
    private static final int CELLS_PER_CHUNK = MINECRAFT_CHUNK_SIZE / CELL_SIZE;
    private static final int BASE_INTERVAL_TICKS = 250;
    private static final int CADENCE_DENOMINATOR = 16;

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

    public static double simulationIntervalTicks(int cellSize) {
        requirePositiveCellSize(cellSize);
        return (double) BASE_INTERVAL_TICKS * cellSize / CADENCE_DENOMINATOR;
    }

    public static boolean isSimulationTick(long tick) {
        return tick > 0 && nextSimulationTick(tick - 1) == tick;
    }

    public static long nextSimulationTick(long tick) {
        return nextSimulationTick(tick, CELL_SIZE);
    }

    static long nextSimulationTick(long tick, int cellSize) {
        requirePositiveCellSize(cellSize);
        long cadenceNumerator = Math.multiplyExact((long) BASE_INTERVAL_TICKS, cellSize);
        long cycle = Math.floorDiv(tick, cadenceNumerator);
        long withinCycle = Math.floorMod(tick, cadenceNumerator);
        long event = withinCycle * CADENCE_DENOMINATOR / cadenceNumerator + 1;
        if (event > CADENCE_DENOMINATOR) {
            cycle++;
            event = 1;
        }
        long boundaryWithinCycle = Math.ceilDiv(event * cadenceNumerator, CADENCE_DENOMINATOR);
        return Math.addExact(Math.multiplyExact(cycle, cadenceNumerator), boundaryWithinCycle);
    }

    private static void requirePositiveCellSize(int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cell size must be positive");
        }
    }
}
