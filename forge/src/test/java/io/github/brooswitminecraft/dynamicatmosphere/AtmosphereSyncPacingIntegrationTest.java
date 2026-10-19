package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.client.AtmosphereClientCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-436 review (first round): deterministic planner-to-paced-queue-to-client coverage,
 * driving the REAL {@link AtmosphereSyncPlanner}, {@link PacedSyncQueue}, and (where the scenario
 * calls for client-visible state) the real {@link AtmosphereClientCache} -- rather than only the
 * synthetic batch types {@code PacedSyncQueueTest} uses to isolate the queue's own mechanics.
 * {@link #syncOnce} mirrors {@code ForgeAtmospherePrototype.syncPlayer}'s idle/genuine-change
 * planning gate exactly (see that method's doc), so these tests exercise the production
 * composition, not just each piece in isolation.
 */
class AtmosphereSyncPacingIntegrationTest {

    private static int weight(AtmosphereSyncPlanner.Update<String> update) {
        return update.authoritativeChunks().size() + update.cells().size();
    }

    /** Mirrors {@code ForgeAtmospherePrototype.splitAtmosphereUpdate} for this test's Update type. */
    private static List<AtmosphereSyncPlanner.Update<String>> split(
        AtmosphereSyncPlanner.Update<String> update, int maxWeight
    ) {
        List<AtmosphereSyncPlanner.Chunk> chunks = update.authoritativeChunks();
        List<AtmosphereSyncPlanner.Cell> cells = update.cells();
        if (chunks.size() + cells.size() <= maxWeight) return List.of(update);
        var pieces = new ArrayList<AtmosphereSyncPlanner.Update<String>>();
        int chunkIndex = 0;
        int cellIndex = 0;
        while (chunkIndex < chunks.size() || cellIndex < cells.size()) {
            int allowance = maxWeight;
            int chunkEnd = Math.min(chunks.size(), chunkIndex + allowance);
            allowance -= chunkEnd - chunkIndex;
            int cellEnd = Math.min(cells.size(), cellIndex + allowance);
            boolean first = pieces.isEmpty();
            boolean last = chunkEnd == chunks.size() && cellEnd == cells.size();
            pieces.add(new AtmosphereSyncPlanner.Update<>(update.dimension(), first && update.reset(),
                last && update.snapshotEnd(), chunks.subList(chunkIndex, chunkEnd), cells.subList(cellIndex, cellEnd)));
            chunkIndex = chunkEnd;
            cellIndex = cellEnd;
        }
        return pieces;
    }

    private static PacedSyncQueue<String, AtmosphereSyncPlanner.Update<String>> newQueue() {
        return new PacedSyncQueue<>(
            AtmosphereSyncPacingIntegrationTest::weight, AtmosphereSyncPlanner.Update::reset,
            AtmosphereSyncPacingIntegrationTest::split);
    }

    /**
     * Mirrors {@code ForgeAtmospherePrototype.syncPlayer}'s gate exactly: a fresh plan is only
     * requested when the queue is idle, or -- regardless of queue state -- a genuine
     * dimension/view-distance change is detected via {@link AtmosphereSyncPlanner#wouldForceReset}.
     * The periodic trigger ({@code periodicDue}) is only ever honored while idle.
     */
    private static List<AtmosphereSyncPlanner.Update<String>> syncOnce(
        AtmosphereSyncPlanner<String, String> planner,
        PacedSyncQueue<String, AtmosphereSyncPlanner.Update<String>> queue,
        AtmosphereClientCache client,
        String player, String dimension, List<AtmosphereSyncPlanner.Chunk> chunks,
        List<AtmosphereSyncPlanner.Cell> cells, boolean periodicDue, int budget
    ) {
        boolean idle = !queue.hasPending(player);
        boolean genuineChange = !idle
            && planner.wouldForceReset(player, dimension, AtmosphereSyncPlanner.UNKNOWN_VIEW_RADIUS);
        if (idle || genuineChange) {
            if (idle && periodicDue) planner.reset(player);
            queue.enqueue(player, planner.plan(player, dimension, chunks, cells));
        }
        List<AtmosphereSyncPlanner.Update<String>> delivered = queue.drain(player, budget);
        if (client != null) {
            for (var update : delivered) {
                client.apply(update.dimension(), update.reset(), update.snapshotEnd(), update.cells().stream()
                    .map(cell -> new AtmosphereClientCache.Update(
                        new AtmosphereClientCache.Cell(cell.x(), cell.y(), cell.z()), cell.amount(), cell.capacity()))
                    .toList());
            }
        }
        return delivered;
    }

    @Test
    void periodicDueRepeatedlyWhileDrainingNeverRestartsAnUnfinishedSnapshotAndEventuallyCompletes() {
        var planner = new AtmosphereSyncPlanner<String, String>(1_000);
        var queue = newQueue();
        var client = new AtmosphereClientCache();
        client.changeDimension("overworld");
        List<AtmosphereSyncPlanner.Chunk> chunks = List.of();
        List<AtmosphereSyncPlanner.Cell> cells = IntStream.range(0, 11)
            .mapToObj(i -> new AtmosphereSyncPlanner.Cell(i, 0, 0, 500, 1000)).toList();
        int budget = 3;

        boolean sawUnfinishedReset = false;
        boolean sawSnapshotEnd = false;
        // Worst case: the periodic trigger fires on EVERY tick (far more aggressive than real
        // staggered production cadence, which fires at most once per slotsPerInterval ticks),
        // proving even this cannot restart an already-in-flight, not-yet-complete snapshot.
        for (int tick = 0; tick < 50 && !sawSnapshotEnd; tick++) {
            for (var update : syncOnce(planner, queue, client, "p", "overworld", chunks, cells, true, budget)) {
                if (update.reset()) {
                    assertFalse(sawUnfinishedReset,
                        "a new reset must never arrive before the previous reset's snapshotEnd -- that would "
                            + "mean the periodic trigger restarted and discarded an unfinished snapshot");
                    sawUnfinishedReset = true;
                }
                if (update.snapshotEnd()) {
                    sawUnfinishedReset = false;
                    sawSnapshotEnd = true;
                }
            }
        }

        assertTrue(sawSnapshotEnd, "the snapshot must eventually complete despite the aggressive periodic trigger");
        assertEquals(11, client.size(), "all 11 cells must have committed to the client once snapshotEnd arrived");
    }

    @Test
    void changesDuringAnUnfinishedSnapshotConvergeCorrectlyOnceItFinishes() {
        var planner = new AtmosphereSyncPlanner<String, String>(1_000);
        var queue = newQueue();
        var client = new AtmosphereClientCache();
        client.changeDimension("overworld");
        List<AtmosphereSyncPlanner.Chunk> chunks = List.of();
        List<AtmosphereSyncPlanner.Cell> initialCells = IntStream.range(0, 10)
            .mapToObj(i -> new AtmosphereSyncPlanner.Cell(i, 0, 0, 500, 1000)).toList();
        int budget = 2;

        syncOnce(planner, queue, client, "p", "overworld", chunks, initialCells, true, budget);
        assertTrue(queue.hasPending("p"), "budget 2 over 10 cells must not complete in a single tick");

        // A cell's authoritative value changes WHILE the snapshot is still draining. Since the
        // queue is busy and neither dimension nor view radius changed, the production gate does
        // not replan this tick -- the in-flight snapshot keeps delivering its original values.
        var changedCells = new ArrayList<>(initialCells);
        changedCells.set(3, new AtmosphereSyncPlanner.Cell(3, 0, 0, 999, 1000));

        while (queue.hasPending("p")) {
            syncOnce(planner, queue, client, "p", "overworld", chunks, changedCells, false, budget);
        }
        assertEquals(10, client.size(), "the in-flight snapshot must still fully complete, undisturbed");

        // Once idle, the very next sync call re-plans against the live (changed) cells and must
        // correctly deliver the change that arrived mid-snapshot -- no staleness, nothing lost.
        syncOnce(planner, queue, client, "p", "overworld", chunks, changedCells, false, budget);

        var committed = client.exportUpdates().stream()
            .collect(java.util.stream.Collectors.toMap(
                u -> u.cell(), AtmosphereClientCache.Update::amount));
        assertEquals(999, committed.get(new AtmosphereClientCache.Cell(3, 0, 0)),
            "the change that arrived during the unfinished snapshot must be reflected once that "
                + "snapshot finishes and a fresh diff runs -- correctly converged, not dropped or stale");
    }

    @Test
    void genuineDimensionChangeSupersedesAnUnfinishedSnapshotImmediatelyEvenMidDrain() {
        var planner = new AtmosphereSyncPlanner<String, String>(1_000);
        var queue = newQueue();
        List<AtmosphereSyncPlanner.Chunk> chunks = List.of();
        List<AtmosphereSyncPlanner.Cell> overworldCells = IntStream.range(0, 10)
            .mapToObj(i -> new AtmosphereSyncPlanner.Cell(i, 0, 0, 500, 1000)).toList();
        int budget = 2;

        syncOnce(planner, queue, null, "p", "overworld", chunks, overworldCells, true, budget);
        assertTrue(queue.hasPending("p"), "budget 2 over 10 cells must not complete in a single tick");

        // A genuine dimension change arrives mid-drain: must supersede immediately in THIS same
        // call, not deferred until the stale overworld snapshot finishes draining.
        List<AtmosphereSyncPlanner.Cell> netherCells = List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000));
        var delivered = syncOnce(planner, queue, null, "p", "nether", chunks, netherCells, false, budget);

        assertTrue(delivered.stream().anyMatch(AtmosphereSyncPlanner.Update::reset),
            "a genuine dimension change must start a fresh reset in the very tick it is detected, "
                + "even though the queue was still busy with an unrelated, unfinished snapshot");
        assertTrue(delivered.stream().anyMatch(u -> u.dimension().equals("nether")),
            "the new dimension's reset must be part of what is delivered immediately");

        var remainder = queue.drain("p", 1_000_000);
        var everDelivered = new ArrayList<>(delivered);
        everDelivered.addAll(remainder);
        assertFalse(everDelivered.stream().anyMatch(u -> u.dimension().equals("overworld") && !u.cells().isEmpty()),
            "no stale overworld cell data may remain queued or be delivered once the dimension change "
                + "has superseded it -- only the empty closing reset for the old dimension is allowed");
    }

    @Test
    void sustainedDeltaArrivalKeepsPendingWeightBoundedRegardlessOfElapsedTicksOrArrivalRate() {
        var planner = new AtmosphereSyncPlanner<String, String>(1_000);
        var queue = newQueue();
        var client = new AtmosphereClientCache();
        client.changeDimension("overworld");
        List<AtmosphereSyncPlanner.Chunk> chunks = List.of();
        int cellCount = 20;
        int budget = 1; // deliberately starved: far slower than the arrival rate below.

        List<AtmosphereSyncPlanner.Cell> cells = IntStream.range(0, cellCount)
            .mapToObj(i -> new AtmosphereSyncPlanner.Cell(i, 0, 0, 1, 1000)).toList();
        while (true) {
            syncOnce(planner, queue, client, "p", "overworld", chunks, cells, false, budget);
            if (!queue.hasPending("p")) break;
        }
        assertEquals(cellCount, client.size());

        int maxWeightObserved = 0;
        var random = new Random(42);
        for (int tick = 0; tick < 500; tick++) {
            // Every tick, every cell's amount changes -- far faster than budget=1 can drain.
            cells = IntStream.range(0, cellCount)
                .mapToObj(i -> new AtmosphereSyncPlanner.Cell(i, 0, 0, 1 + random.nextInt(900), 1000)).toList();
            syncOnce(planner, queue, client, "p", "overworld", chunks, cells, false, budget);
            maxWeightObserved = Math.max(maxWeightObserved, queue.pendingWeight("p"));
        }

        assertTrue(maxWeightObserved <= cellCount,
            "pending weight must stay bounded by the current scope size (at most " + cellCount + " ops) and "
                + "never grow with elapsed ticks or arrival rate (observed max " + maxWeightObserved
                + "), since a fresh plan is only ever queued once the previous generation has fully drained");
    }
}
