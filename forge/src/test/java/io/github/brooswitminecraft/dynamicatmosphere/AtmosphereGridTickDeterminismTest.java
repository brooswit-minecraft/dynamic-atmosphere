package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.LongUnaryOperator;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-384: regression coverage proving atmosphere physics advance strictly
 * with the simulated tick argument, never with elapsed real time, server lag, or
 * pause/resume gaps.
 *
 * <p>These tests drive the real production adapter -- {@link AtmosphereGrid#spread}
 * with its {@code beforeSpread}/{@code afterSpread} hooks, the exact seam
 * {@code ForgeAtmospherePrototype.advanceSimulation} uses for drift/growth/condense/
 * vapor-event callbacks -- rather than only exercising {@code emit}/{@code set} in
 * isolation. The hooks below stand in for a representative decay path ({@link
 * DustDissipation}, the real per-turn Dust loss policy) and a representative reaction
 * path ({@link AtmosphereCondensation}, the real condensation-trigger policy) so that a
 * future change that substitutes wall-clock/frame-delta pacing for the simulated tick
 * anywhere in that dispatch -- not just in {@code AtmosphereGrid} itself -- fails here.
 * Equality is checked over full {@link AtmosphereGrid.Cell} records (amount, capacity,
 * lastEmissionTick, lastUpdateTick), not just amounts, so scheduling/timing state
 * divergence is also caught, and cadence is proven on a per-grid configured tick-rate
 * (the exact per-material/per-grid interval functions production code injects), not an
 * assumed global constant.
 */
class AtmosphereGridTickDeterminismTest {

    private static AtmosphereGrid.CellKey<String> key(int x, int y, int z) {
        return new AtmosphereGrid.CellKey<>("overworld", x, y, z);
    }

    /** Full cell state (amount, capacity, lastEmissionTick, lastUpdateTick) -- not merely amounts(). */
    private static List<AtmosphereGrid.Cell<String>> state(AtmosphereGrid<String> grid) {
        return grid.cells().stream()
            .sorted(Comparator
                .comparingInt((AtmosphereGrid.Cell<String> c) -> c.key().x())
                .thenComparingInt(c -> c.key().y())
                .thenComparingInt(c -> c.key().z()))
            .toList();
    }

    /**
     * Representative decay: once per due turn, applies {@link DustDissipation}'s real
     * per-turn loss policy to the source cell, using a fixed, invocation-indexed roll
     * sequence (never wall-clock) so it is reproducible regardless of real pacing.
     */
    private static final class DecayHook {
        private final AtmosphereGrid<String> grid;
        private final double[] rolls;
        private int calls;
        private final ToIntFunction<AtmosphereGrid.CellKey<String>> capacity;

        DecayHook(AtmosphereGrid<String> grid, double[] rolls, ToIntFunction<AtmosphereGrid.CellKey<String>> capacity) {
            this.grid = grid;
            this.rolls = rolls;
            this.capacity = capacity;
        }

        void beforeSpread(AtmosphereGrid.CellKey<String> k) {
            var cell = grid.get(k);
            if (cell == null) return;
            double roll = rolls[calls % rolls.length];
            calls++;
            int loss = DustDissipation.amount(cell.amount(), () -> roll, 1.0 / 64, 40);
            if (loss > 0) {
                grid.set(k, cell.amount() - loss, cell.lastUpdateTick(), capacity.applyAsInt(k));
            }
        }
    }

    /**
     * Representative reaction: after spread, if a source cell is dense enough,
     * {@link AtmosphereCondensation}'s real trigger policy (fixed, invocation-indexed
     * roll) converts a deterministic fraction of it into a distinct destination cell --
     * standing in for condensation/vapor-event style production of new material.
     */
    private static final class ReactionHook {
        private final AtmosphereGrid<String> grid;
        private final AtmosphereGrid.CellKey<String> product;
        private final double[] rolls;
        private int calls;
        private final ToIntFunction<AtmosphereGrid.CellKey<String>> capacity;

        ReactionHook(AtmosphereGrid<String> grid, AtmosphereGrid.CellKey<String> product,
            double[] rolls, ToIntFunction<AtmosphereGrid.CellKey<String>> capacity) {
            this.grid = grid;
            this.product = product;
            this.rolls = rolls;
            this.capacity = capacity;
        }

        void afterSpread(AtmosphereGrid.CellKey<String> k) {
            var cell = grid.get(k);
            if (cell == null) return;
            int cap = capacity.applyAsInt(k);
            double roll = rolls[calls % rolls.length];
            calls++;
            if (AtmosphereCondensation.shouldCondense(cell.amount(), cap, roll, 1.0)) {
                int consumed = AtmosphereCondensation.consumedAmount(cell.amount());
                grid.set(k, cell.amount() - consumed, cell.lastUpdateTick(), cap);
                grid.emit(product, consumed, cell.lastUpdateTick(), capacity.applyAsInt(product));
            }
        }
    }

    /** Runs emission + several due-tick spreads (with decay/reaction hooks) under a given wall-clock pacing. */
    private static List<AtmosphereGrid.Cell<String>> runSequence(long sleepMillisPerStep, LongUnaryOperator cadence)
        throws InterruptedException {
        var grid = new AtmosphereGrid<String>(cadence);
        var source = key(0, 0, 0);
        var product = key(5, 5, 5);
        // Neighbors of the source have no capacity, so one-hop transport cannot dilute
        // the source cell: this test is isolating decay/reaction determinism, not
        // transport (already covered by the other tests in this file).
        ToIntFunction<AtmosphereGrid.CellKey<String>> capacity =
            k -> k.equals(source) || k.equals(product) ? 1000 : 0;
        double[] decayRolls = {0.001, 0.9, 0.001, 0.5, 0.001};
        double[] reactionRolls = {0.9, 0.01, 0.9, 0.01, 0.9};
        var decay = new DecayHook(grid, decayRolls, capacity);
        var reaction = new ReactionHook(grid, product, reactionRolls, capacity);

        long tick = 1;
        grid.set(source, 950, tick, 1000);
        if (sleepMillisPerStep > 0) Thread.sleep(sleepMillisPerStep);

        for (int i = 0; i < 5; i++) {
            tick = cadence.applyAsLong(tick);
            grid.spread(tick, capacity, decay::beforeSpread, ignored -> true,
                (sourceKey, destinationKey) -> true, reaction::afterSpread);
            if (sleepMillisPerStep > 0) Thread.sleep(sleepMillisPerStep);
        }
        return state(grid);
    }

    @Test
    void tickSequenceWithDecayAndReactionHooksIsIdenticalRegardlessOfWallClockPacing() throws InterruptedException {
        var noDelay = runSequence(0, AtmosphereGridLayout::nextSimulationTick);
        var shortDelay = runSequence(5, AtmosphereGridLayout::nextSimulationTick);
        // A deliberately large, uneven gap simulating server lag / a pause-resume stall.
        var longDelay = runSequence(120, AtmosphereGridLayout::nextSimulationTick);

        assertEquals(noDelay, shortDelay,
            "full cell state (amount/capacity/lastEmissionTick/lastUpdateTick) through decay+reaction "
                + "hooks must be identical regardless of real time elapsed between due-tick spreads");
        assertEquals(noDelay, longDelay,
            "a long real-time stall between ticks (simulated lag/pause) must not change physics state");
        assertTrue(noDelay.stream().anyMatch(c -> c.key().equals(key(5, 5, 5))),
            "sanity: the reaction hook's product cell should have been produced at least once");
    }

    @Test
    void equalPassCountsUnderDifferentConfiguredTickRatesAreEachInternallyWallClockInvariant() throws InterruptedException {
        // Two distinct, explicitly configured cadences -- the exact per-grid seam
        // ForgeAtmospherePrototype injects per material (e.g. AtmosphereMaterial::nextSimulationTick,
        // SmokeGridLayout::nextSimulationTick) rather than one assumed global interval.
        LongUnaryOperator fastCadence = tick -> Math.multiplyExact(Math.floorDiv(tick, 20) + 1, 20L);
        LongUnaryOperator slowCadence = tick -> Math.multiplyExact(Math.floorDiv(tick, 90) + 1, 90L);

        var fastNoDelay = runSequence(0, fastCadence);
        var fastWithDelay = runSequence(40, fastCadence);
        var slowNoDelay = runSequence(0, slowCadence);
        var slowWithDelay = runSequence(40, slowCadence);

        assertEquals(fastNoDelay, fastWithDelay,
            "a configured fast (20-tick) cadence must still be wall-clock invariant across equal pass counts");
        assertEquals(slowNoDelay, slowWithDelay,
            "a configured slow (90-tick) cadence must still be wall-clock invariant across equal pass counts");
    }

    @Test
    void cadenceHelpersDependOnlyOnTickArgumentNotWallClock() throws InterruptedException {
        long tick = 7;
        long expectedNext = AtmosphereGridLayout.nextSimulationTick(tick);
        long expectedSmokeNext = SmokeGridLayout.nextSimulationTick(tick);
        Thread.sleep(50);
        assertEquals(expectedNext, AtmosphereGridLayout.nextSimulationTick(tick),
            "vapor-grid cadence scheduling is a pure function of the tick argument");
        assertEquals(expectedSmokeNext, SmokeGridLayout.nextSimulationTick(tick),
            "smoke-grid cadence scheduling is a pure function of the tick argument");

        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            long before = material.nextSimulationTick(tick);
            Thread.sleep(5);
            assertEquals(before, material.nextSimulationTick(tick),
                "per-material cadence must not drift with wall-clock time for " + material);
        }
    }

    @Test
    void preDueTickDoesNothingAndDueTickProcessesExactlyOnce() throws InterruptedException {
        var grid = new AtmosphereGrid<String>(AtmosphereGridLayout::nextSimulationTick);
        var source = key(0, 0, 0);
        ToIntFunction<AtmosphereGrid.CellKey<String>> capacity = k -> 1000;
        int[] hookCalls = {0};

        long tick = 1;
        grid.set(source, 900, tick, 1000);
        long due = AtmosphereGridLayout.nextSimulationTick(tick);
        assertTrue(due > tick + 1, "sanity: due tick must be strictly after the emission tick for this scenario");

        var beforeAnyCall = state(grid);
        // A tick before the scheduled due tick: must not process the source or invoke any hook,
        // no matter how much real time has elapsed since emission.
        Thread.sleep(80);
        var preDueResult = grid.spread(due - 1, capacity, k -> hookCalls[0]++);
        assertEquals(0, preDueResult.sourcesProcessed(), "a not-yet-due tick must not process any source");
        assertEquals(0, hookCalls[0], "a not-yet-due tick must not invoke the beforeSpread hook");
        assertEquals(beforeAnyCall, state(grid), "state must be unchanged before the due tick, regardless of wall time waited");

        Thread.sleep(80);
        var dueResult = grid.spread(due, capacity, k -> hookCalls[0]++);
        assertEquals(1, dueResult.sourcesProcessed(), "the due tick must process the one scheduled source exactly once");
        assertEquals(1, hookCalls[0], "the due tick must invoke the beforeSpread hook exactly once");
    }

    @Test
    void resumingAfterAnArbitrarilyLongPauseAdvancesExactlyOnePendingStepNotAWallTimeCatchUp() throws InterruptedException {
        ToIntFunction<AtmosphereGrid.CellKey<String>> capacity = k -> 1000;
        var source = key(0, 0, 0);

        // Reference: no pause at all between emission and the first due-tick spread.
        var reference = new AtmosphereGrid<String>(AtmosphereGridLayout::nextSimulationTick);
        reference.set(source, 500, 1, 1000);
        long due = AtmosphereGridLayout.nextSimulationTick(1);
        reference.spread(due, capacity);
        var referenceAfterFirstStep = state(reference);
        long secondDue = AtmosphereGridLayout.nextSimulationTick(due);
        reference.spread(secondDue, capacity);
        var referenceAfterSecondStep = state(reference);

        // Same scenario, but a long real-time "pause" (no onServerTick/spread calls at all,
        // simulating a frozen/paused server) elapses before the first resumed call.
        var paused = new AtmosphereGrid<String>(AtmosphereGridLayout::nextSimulationTick);
        paused.set(source, 500, 1, 1000);
        Thread.sleep(150);
        paused.spread(due, capacity);
        assertEquals(referenceAfterFirstStep, state(paused),
            "resuming after an arbitrarily long real-time pause must produce exactly the same single "
                + "due-tick step as no pause at all -- never a wall-time-scaled catch-up");
        Thread.sleep(150);
        paused.spread(secondDue, capacity);
        assertEquals(referenceAfterSecondStep, state(paused),
            "the next resumed due tick after another long pause must still advance by exactly one step");
    }

    @Test
    void dissipationRollIsDrivenByInvocationCountNotRealTime() throws InterruptedException {
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
    void condensationTriggerIsDrivenByInvocationArgumentsNotRealTime() throws InterruptedException {
        // AtmosphereCondensation.shouldCondense is the real reaction-trigger policy used by
        // condenseNetherFog/condenseEndSand/vapor condensation; it must stay a pure function
        // of (amount, capacity, roll), never real elapsed time between calls.
        boolean fast = AtmosphereCondensation.shouldCondense(900, 1000, 0.01, 1.0);
        Thread.sleep(60);
        boolean slow = AtmosphereCondensation.shouldCondense(900, 1000, 0.01, 1.0);
        assertEquals(fast, slow, "condensation trigger must depend only on its arguments, not wall-clock timing");
        assertTrue(fast, "sanity: a low roll against a dense, high-fullness cell should trigger condensation");
    }

    @Test
    void pauseResumeGapWithNoDueTickDoesNotAdvancePhysics() throws InterruptedException {
        // Simulated "pause": no onServerTick call (ticks are not incremented) happens
        // for a real-world stretch of time, and the tick passed to spread never reaches
        // the due tick. Because this API has no wall-clock input, state is necessarily
        // unchanged -- but if real-time pacing were ever added to the grid's own
        // internals, this is the regression that would catch it.
        var grid = new AtmosphereGrid<String>();
        var source = key(0, 0, 0);
        long tick = 1;
        grid.set(source, 500, tick, 1000);
        var before = state(grid);

        Thread.sleep(150); // wall-clock time passes; no tick advanced, no spread() called

        assertEquals(before, state(grid),
            "state must not change while simulated ticks are stopped, no matter how long real time passes");
    }
}
