package io.github.brooswitminecraft.dynamicatmosphere.engine;

/**
 * The whole of this story's configuration surface (cell size, rebuild
 * debounce interval, and the per-step rebuild bound), deliberately kept in
 * one small value type. Spec section 37: exact numeric values are
 * placeholders pending profiling against a real world; nothing here is a
 * tuned constant.
 *
 * @param cellSize           the edge length, in blocks, of one cubic Weather
 *                            Block. X, Y and Z share this one value — see
 *                            {@link io.github.brooswitminecraft.dynamicatmosphere.engine.grid.WeatherGrid}.
 * @param debounceInterval   how much SIMULATION time (as advanced by the
 *                            caller, never wall-clock time) a dirtied cell
 *                            must sit untouched before it becomes eligible
 *                            for rebuild. Trailing-edge: a cell dirtied again
 *                            before this elapses restarts the wait.
 * @param maxRebuildsPerStep the maximum number of cell rebuilds performed by
 *                            one {@code advanceTo} call, regardless of how
 *                            many cells are eligible.
 */
public record EngineConfig(int cellSize, long debounceInterval, int maxRebuildsPerStep) {

    public static final int DEFAULT_CELL_SIZE = 16;

    public static final long DEFAULT_DEBOUNCE_INTERVAL = 10;

    /**
     * Honest placeholder. The spec requires rebuilds-per-step to be bounded
     * by a configurable constant, but the real value has to come from
     * profiling against a real world — which does not exist yet at this
     * story. This number was not measured; it exists so the bound is
     * present and configurable, not so it is correct.
     */
    public static final int DEFAULT_MAX_REBUILDS_PER_STEP = 8;

    public EngineConfig {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive, was " + cellSize);
        }
        if (debounceInterval < 0) {
            throw new IllegalArgumentException("debounceInterval must not be negative, was " + debounceInterval);
        }
        if (maxRebuildsPerStep <= 0) {
            throw new IllegalArgumentException("maxRebuildsPerStep must be positive, was " + maxRebuildsPerStep);
        }
    }

    public static EngineConfig defaults() {
        return new EngineConfig(DEFAULT_CELL_SIZE, DEFAULT_DEBOUNCE_INTERVAL, DEFAULT_MAX_REBUILDS_PER_STEP);
    }
}
