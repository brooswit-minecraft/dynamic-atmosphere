package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MINECRAFT-436: pacing/budget/splitting/supersede/fairness behavior for {@link PacedSyncQueue}. */
class PacedSyncQueueTest {

    private record Batch(int weight, boolean reset) { }

    /** No real sub-structure to split -- mirrors a caller whose batches are atomic units. */
    private static PacedSyncQueue<String, Batch> indivisibleQueue() {
        return new PacedSyncQueue<>(Batch::weight, Batch::reset, (batch, maxWeight) -> List.of(batch));
    }

    /** One "unit" of weight per op; splits a batch into single-weight pieces, each weight <= maxWeight. */
    private record Unit(boolean reset, boolean snapshotEnd, int id) { }

    private static PacedSyncQueue<String, Unit> unitQueue() {
        return new PacedSyncQueue<>(ignored -> 1, Unit::reset, (unit, maxWeight) -> List.of(unit));
    }

    /** A batch made of individually-weighted, splittable "ops", like a real Update's chunk+cell lists. */
    private record Composite(boolean reset, boolean snapshotEnd, List<Integer> ops) { }

    private static List<Composite> splitComposite(Composite composite, int maxWeight) {
        if (composite.ops().size() <= maxWeight) return List.of(composite);
        var pieces = new ArrayList<Composite>();
        int index = 0;
        while (index < composite.ops().size()) {
            int end = Math.min(composite.ops().size(), index + maxWeight);
            boolean first = index == 0;
            boolean last = end == composite.ops().size();
            pieces.add(new Composite(first && composite.reset(), last && composite.snapshotEnd(),
                composite.ops().subList(index, end)));
            index = end;
        }
        return pieces;
    }

    private static PacedSyncQueue<String, Composite> compositeQueue() {
        return new PacedSyncQueue<>(c -> c.ops().size(), Composite::reset, PacedSyncQueueTest::splitComposite);
    }

    @Test
    void drainReleasesOnlyUpToTheSuppliedBudgetAndLeavesTheRestQueued() {
        var queue = indivisibleQueue();
        var batches = List.of(new Batch(3, true), new Batch(3, false), new Batch(3, false));
        queue.enqueue("p", batches);

        var first = queue.drain("p", 5);
        assertEquals(List.of(batches.get(0)), first, "first batch (weight 3) fits the budget of 5; a second "
            + "(cumulative weight 6) would not, so only one batch should be released");
        assertTrue(queue.hasPending("p"));

        var second = queue.drain("p", 5);
        assertEquals(List.of(batches.get(1)), second);
        assertTrue(queue.hasPending("p"));

        var third = queue.drain("p", 100);
        assertEquals(List.of(batches.get(2)), third);
        assertFalse(queue.hasPending("p"), "queue must be fully drained and removed once empty");
    }

    @Test
    void drainAlwaysReleasesAtLeastOneBatchWhenASingleIndivisibleUnitExceedsTheBudget() {
        var queue = indivisibleQueue();
        queue.enqueue("p", List.of(new Batch(1_000_000, true)));

        var released = queue.drain("p", 1);

        assertEquals(1, released.size(),
            "an oversized single indivisible batch must still make progress instead of starving the player "
                + "forever, since the supplied splitter has no way to shrink it below the cap");
        assertFalse(queue.hasPending("p"));
    }

    @Test
    void drainSplitsAnOversizedSplittableBatchInsteadOfExceedingTheBudget() {
        var queue = compositeQueue();
        var batch = new Composite(true, true, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        queue.enqueue("p", List.of(batch));

        var allReleased = new ArrayList<Composite>();
        while (queue.hasPending("p")) {
            var released = queue.drain("p", 3);
            for (Composite piece : released) {
                assertTrue(piece.ops().size() <= 3,
                    "the explicit per-tick budget must never be exceeded once the batch is splittable");
            }
            allReleased.addAll(released);
        }

        assertEquals(4, allReleased.size(), "10 ops at budget 3 must split into ceil(10/3) = 4 pieces");
        assertTrue(allReleased.get(0).reset(), "only the first piece keeps the original reset flag");
        for (int i = 1; i < allReleased.size(); i++) {
            assertFalse(allReleased.get(i).reset(), "no piece after the first may claim reset");
        }
        assertTrue(allReleased.get(allReleased.size() - 1).snapshotEnd(),
            "only the last piece keeps the original snapshotEnd flag");
        for (int i = 0; i < allReleased.size() - 1; i++) {
            assertFalse(allReleased.get(i).snapshotEnd(), "no piece before the last may claim snapshotEnd");
        }
        var reassembled = allReleased.stream().flatMap(piece -> piece.ops().stream()).toList();
        assertEquals(batch.ops(), reassembled, "splitting must neither drop nor duplicate any op, and must "
            + "preserve their original order");
    }

    @Test
    void splittingNeverExceedsBudgetAcrossManyDrainCallsEvenWhenQueuedWithOtherBatches() {
        var queue = compositeQueue();
        var big = new Composite(true, false, List.of(1, 2, 3, 4, 5, 6, 7));
        var small = new Composite(false, true, List.of(8, 9));
        queue.enqueue("p", List.of(big, small));

        var allReleased = new ArrayList<Composite>();
        int totalWeightSeen = 0;
        while (queue.hasPending("p")) {
            var released = queue.drain("p", 2);
            int weight = released.stream().mapToInt(c -> c.ops().size()).sum();
            assertTrue(weight <= 2, "each drain call's released weight must respect the supplied budget");
            totalWeightSeen += weight;
            allReleased.addAll(released);
        }
        assertEquals(9, totalWeightSeen, "all 7 + 2 ops must eventually be delivered exactly once");
    }

    @Test
    void newResetBatchSupersedesStillUndeliveredBatchesFromAnEarlierIncompleteSnapshot() {
        var queue = unitQueue();
        var staleBatch1 = new Unit(true, false, 1);
        var staleBatch2 = new Unit(false, false, 2);
        queue.enqueue("p", List.of(staleBatch1, staleBatch2));
        assertTrue(queue.hasPending("p"));

        var freshReset = new Unit(true, false, 3);
        var freshContinuation = new Unit(false, true, 4);
        queue.enqueue("p", List.of(freshReset, freshContinuation));

        var released = queue.drain("p", 100);
        assertEquals(List.of(freshReset, freshContinuation), released,
            "the stale, still-undelivered batches from the earlier incomplete snapshot must never be "
                + "delivered once a newer snapshot has started -- the client must not see a mix of two "
                + "generations' data");
    }

    @Test
    void ordinaryDeltaBatchesAppendAfterAnUnfinishedSnapshotInsteadOfInterleaving() {
        var queue = unitQueue();
        var snapshotStart = new Unit(true, false, 1);
        var snapshotRemainder = new Unit(false, true, 2);
        queue.enqueue("p", List.of(snapshotStart, snapshotRemainder));

        var delta = new Unit(false, false, 3);
        queue.enqueue("p", List.of(delta));

        var released = queue.drain("p", 100);
        assertEquals(List.of(snapshotStart, snapshotRemainder, delta), released,
            "a delta planned while a snapshot is still queued must be delivered strictly after the "
                + "snapshot finishes, in order -- never ahead of it or mixed into it");
    }

    @Test
    void releaseDropsAllQueuedWorkForThatPlayerOnly() {
        var queue = indivisibleQueue();
        queue.enqueue("a", List.of(new Batch(1, true)));
        queue.enqueue("b", List.of(new Batch(1, true)));

        queue.release("a");

        assertFalse(queue.hasPending("a"), "disconnect must release queued work rather than leaking it");
        assertTrue(queue.hasPending("b"), "releasing one player must not affect another player's queue");
    }

    @Test
    void retainPlayersDropsQueuedWorkForPlayersNoLongerConnected() {
        var queue = indivisibleQueue();
        queue.enqueue("a", List.of(new Batch(1, true)));
        queue.enqueue("b", List.of(new Batch(1, true)));

        queue.retainPlayers(List.of("b"));

        assertFalse(queue.hasPending("a"));
        assertTrue(queue.hasPending("b"));
    }

    @Test
    void clearDropsAllPlayersQueuedWork() {
        var queue = indivisibleQueue();
        queue.enqueue("a", List.of(new Batch(1, true)));
        queue.enqueue("b", List.of(new Batch(1, true)));

        queue.clear();

        assertFalse(queue.hasPending("a"));
        assertFalse(queue.hasPending("b"));
    }

    @Test
    void drainOfAnUnknownOrEmptyPlayerReturnsEmptyRatherThanThrowing() {
        var queue = indivisibleQueue();
        assertEquals(List.of(), queue.drain("never-enqueued", 10));
    }

    @Test
    void nonPositiveBudgetIsRejected() {
        var queue = indivisibleQueue();
        queue.enqueue("p", List.of(new Batch(1, true)));
        assertThrows(IllegalArgumentException.class, () -> queue.drain("p", 0));
    }
}
