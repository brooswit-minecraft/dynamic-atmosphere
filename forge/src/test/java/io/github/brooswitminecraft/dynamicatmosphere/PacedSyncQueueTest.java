package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MINECRAFT-436: pacing/budget/supersede/fairness behavior for {@link PacedSyncQueue}. */
class PacedSyncQueueTest {

    private record Batch(int weight, boolean reset) { }

    private static PacedSyncQueue<String, Batch> queue() {
        return new PacedSyncQueue<>(Batch::weight, Batch::reset);
    }

    @Test
    void drainReleasesOnlyUpToTheSuppliedBudgetAndLeavesTheRestQueued() {
        var queue = queue();
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
    void drainAlwaysReleasesAtLeastOneBatchEvenIfItAloneExceedsTheBudget() {
        var queue = queue();
        queue.enqueue("p", List.of(new Batch(1_000_000, true)));

        var released = queue.drain("p", 1);

        assertEquals(1, released.size(),
            "an oversized single batch must still make progress instead of starving the player forever");
        assertFalse(queue.hasPending("p"));
    }

    @Test
    void newResetBatchSupersedesStillUndeliveredBatchesFromAnEarlierIncompleteSnapshot() {
        var queue = queue();
        var staleBatch1 = new Batch(1, true);
        var staleBatch2 = new Batch(1, false);
        queue.enqueue("p", List.of(staleBatch1, staleBatch2));
        assertTrue(queue.hasPending("p"));

        var freshReset = new Batch(1, true);
        var freshContinuation = new Batch(1, false);
        queue.enqueue("p", List.of(freshReset, freshContinuation));

        var released = queue.drain("p", 100);
        assertEquals(List.of(freshReset, freshContinuation), released,
            "the stale, still-undelivered batches from the earlier incomplete snapshot must never be "
                + "delivered once a newer snapshot has started -- the client must not see a mix of two "
                + "generations' data");
    }

    @Test
    void ordinaryDeltaBatchesAppendAfterAnUnfinishedSnapshotInsteadOfInterleaving() {
        var queue = queue();
        var snapshotStart = new Batch(1, true);
        var snapshotRemainder = new Batch(1, false);
        queue.enqueue("p", List.of(snapshotStart, snapshotRemainder));

        var delta = new Batch(1, false);
        queue.enqueue("p", List.of(delta));

        var released = queue.drain("p", 100);
        assertEquals(List.of(snapshotStart, snapshotRemainder, delta), released,
            "a delta planned while a snapshot is still queued must be delivered strictly after the "
                + "snapshot finishes, in order -- never ahead of it or mixed into it");
    }

    @Test
    void releaseDropsAllQueuedWorkForThatPlayerOnly() {
        var queue = queue();
        queue.enqueue("a", List.of(new Batch(1, true)));
        queue.enqueue("b", List.of(new Batch(1, true)));

        queue.release("a");

        assertFalse(queue.hasPending("a"), "disconnect must release queued work rather than leaking it");
        assertTrue(queue.hasPending("b"), "releasing one player must not affect another player's queue");
    }

    @Test
    void retainPlayersDropsQueuedWorkForPlayersNoLongerConnected() {
        var queue = queue();
        queue.enqueue("a", List.of(new Batch(1, true)));
        queue.enqueue("b", List.of(new Batch(1, true)));

        queue.retainPlayers(List.of("b"));

        assertFalse(queue.hasPending("a"));
        assertTrue(queue.hasPending("b"));
    }

    @Test
    void clearDropsAllPlayersQueuedWork() {
        var queue = queue();
        queue.enqueue("a", List.of(new Batch(1, true)));
        queue.enqueue("b", List.of(new Batch(1, true)));

        queue.clear();

        assertFalse(queue.hasPending("a"));
        assertFalse(queue.hasPending("b"));
    }

    @Test
    void drainOfAnUnknownOrEmptyPlayerReturnsEmptyRatherThanThrowing() {
        var queue = queue();
        assertEquals(List.of(), queue.drain("never-enqueued", 10));
    }

    @Test
    void nonPositiveBudgetIsRejected() {
        var queue = queue();
        queue.enqueue("p", List.of(new Batch(1, true)));
        assertThrows(IllegalArgumentException.class, () -> queue.drain("p", 0));
    }
}
