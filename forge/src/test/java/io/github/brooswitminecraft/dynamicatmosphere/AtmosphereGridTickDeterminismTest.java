package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-384: regression coverage proving atmosphere physics advance strictly
 * with the simulated tick argument, never with elapsed real time, server lag, or
 * pause/resume gaps. Every production entry point exercised here (emit/set/spread,
 * cadence helpers, dissipation rolls) takes only a {@code long tick} parameter --
 * there is no wall-clock input anywhere in this API for a real-time dependency to
 * hide behind. These tests inject real {@link Thread#sleep} delays of varying and
 * sometimes large duration between identical tick sequences and assert byte-for-byte
 * identical resulting state, so a future change that smuggles in
 * {@code System.currentTimeMillis()}/{@code nanoTime()}-based pacing would fail here.
 */
class AtmosphereGridTickDeterminismTest {

    private static AtmosphereGrid.CellKey<String> key(int x, int y, int z) {
        return new AtmosphereGrid.CellKey<>("overworld", x, y, z);
    }

    private static Map<AtmosphereGrid.CellKey<String>, Integer> amounts(AtmosphereGrid<String> grid) {
        return grid.cells().stream().collect(java.util.stream.Collectors.toMap(
            AtmosphereGrid.Cell::key, AtmosphereGrid.Cell::amount));
    }

    /** Runs the same emission + multi-tick spread sequence, sleeping real wall time between steps. */
    private static AtmosphereGrid<String> runSequence(long sleepMillisPerStep) throws InterruptedException {
        var grid = new AtmosphereGrid<String>();
        var source = key(0, 0, 0);
        var neighbors = List.of(key(1, 0, 0), key(-1, 0, 0), key(0, 1, 0), key(0, -1, 0), key(0, 0, 1), key(0, 0, -1));
        ToIntFunction<AtmosphereGrid.CellKey<String>> capacity = k -> 1000;

        long tick = 1;
        grid.set(source, 900, tick, 1000);
        if (sleepMillisPerStep > 0) Thread.sleep(sleepMillisPerStep);

        // Drive several simulation cycles purely by tick number, each preceded by a
        // real-time delay of a different, caller-chosen length (simulated lag/pause).
        for (int i = 0; i < 5; i++) {
            tick = AtmosphereGridLayout.nextSimulationTick(tick);
            grid.spread(tick, capacity);
            if (sleepMillisPerStep > 0) Thread.sleep(sleepMillisPerStep);
        }
        return grid;
    }

    @Test
    void equalTickSequenceProducesIdenticalStateRegardlessOfWallClockPacing() throws InterruptedException {
        var noDelay = runSequence(0);
        var shortDelay = runSequence(5);
        // A deliberately large, uneven gap simulating server lag / a pause-resume stall
        // between ticks; if anything measured real elapsed time this would diverge.
        var longDelay = runSequence(120);

        assertEquals(amounts(noDelay), amounts(shortDelay),
            "spreading at the same tick numbers must be identical regardless of real time elapsed between calls");
        assertEquals(amounts(noDelay), amounts(longDelay),
            "a long real-time stall between ticks (simulated lag/pause) must not change physics state");
    }

    @Test
    void cadenceHelpersDependOnlyOnTickArgumentNotWallClock() throws InterruptedException {
        long tick = 7;
        long expectedNext = AtmosphereGridLayout.nextSimulationTick(tick);
        Thread.sleep(50);
        assertEquals(expectedNext, AtmosphereGridLayout.nextSimulationTick(tick),
            "cadence scheduling is a pure function of the tick argument");

        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            long before = material.nextSimulationTick(tick);
            Thread.sleep(5);
            assertEquals(before, material.nextSimulationTick(tick),
                "per-material cadence must not drift with wall-clock time for " + material);
        }
    }

    @Test
    void dissipationRollIsDrivenByInvocationCountNotRealTime() throws InterruptedException {
        // A deterministic fixed roll sequence: dissipation must fire purely based on
        // how many times it is invoked (once per due simulation turn), never on how
        // much real time separated those invocations.
        double[] rolls = {0.001, 0.5, 0.001, 0.9, 0.001};
        int[] fastResults = new int[rolls.length];
        int[] slowResults = new int[rolls.length];

        for (int i = 0; i < rolls.length; i++) {
            final double roll = rolls[i];
            fastResults[i] = DustDissipation.amount(1000, () -> roll, 1.0 / 64, 40);
        }
        for (int i = 0; i < rolls.length; i++) {
            final double roll = rolls[i];
            Thread.sleep(30);
            slowResults[i] = DustDissipation.amount(1000, () -> roll, 1.0 / 64, 40);
        }

        assertEquals(List.of(fastResults[0], fastResults[1], fastResults[2], fastResults[3], fastResults[4]),
            List.of(slowResults[0], slowResults[1], slowResults[2], slowResults[3], slowResults[4]));
        assertTrue(fastResults[0] > 0 && fastResults[2] > 0, "sanity: the chosen low rolls should trigger dissipation");
    }

    @Test
    void pauseResumeGapBetweenServerTicksDoesNotAdvancePhysics() throws InterruptedException {
        // Simulated "pause": no onServerTick call (ticks are not incremented) happens
        // for a real-world stretch of time. Because this API has no wall-clock input,
        // state is necessarily unchanged -- but if real-time pacing were ever added to
        // the grid's own internals, this is the regression that would catch it.
        var grid = new AtmosphereGrid<String>();
        var source = key(0, 0, 0);
        long tick = 1;
        grid.set(source, 500, tick, 1000);
        var before = amounts(grid);

        Thread.sleep(150); // wall-clock time passes; no tick advanced, no spread() called

        assertEquals(before, amounts(grid), "state must not change while simulated ticks are stopped, no matter how long real time passes");
    }
}
