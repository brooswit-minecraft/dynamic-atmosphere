package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalTransferSinkTest {
    @Test
    void netherToOverworldScalesHorizontallyByEightAndPreservesY() {
        assertEquals(new BlockPos(8, 64, -16), PortalTransferSink.netherToOverworld(new BlockPos(1, 64, -2)));
        assertEquals(new BlockPos(0, 0, 0), PortalTransferSink.netherToOverworld(new BlockPos(0, 0, 0)));
    }

    @Test
    void overworldToNetherScalesHorizontallyByOneEighthAndPreservesY() {
        assertEquals(new BlockPos(1, 64, -2), PortalTransferSink.overworldToNether(new BlockPos(8, 64, -16)));
        // Integer division floors toward zero per Java/BlockPos semantics, matching vanilla portal linking.
        assertEquals(new BlockPos(1, 0, 0), PortalTransferSink.overworldToNether(new BlockPos(15, 0, 0)));
    }

    @Test
    void theTwoScalesAreInverseForExactMultiplesOfEight() {
        var original = new BlockPos(24, 70, -40);
        assertEquals(original, PortalTransferSink.netherToOverworld(PortalTransferSink.overworldToNether(original)));
    }

    @Test
    void nearestWithinRadiusFindsADisplacedExitNotJustTheRawAnchor() {
        var anchor = new BlockPos(800, 64, 0);
        // Vanilla's real exit can land away from the naive scaled anchor; this one is 20 blocks off.
        var displacedExit = new BlockPos(820, 70, 5);
        var farAway = new BlockPos(5000, 64, 5000);
        var found = PortalTransferSink.nearestWithinRadius(anchor, List.of(farAway, displacedExit), 128);
        assertEquals(Optional.of(displacedExit), found);
    }

    @Test
    void nearestWithinRadiusDeclinesWhenNothingVerifiedIsNearby() {
        var anchor = new BlockPos(800, 64, 0);
        var farAway = new BlockPos(5000, 64, 5000);
        assertTrue(PortalTransferSink.nearestWithinRadius(anchor, List.of(farAway), 128).isEmpty());
        assertTrue(PortalTransferSink.nearestWithinRadius(anchor, List.of(), 128).isEmpty());
    }

    @Test
    void nearestWithinRadiusPicksTheCloserOfTwoCandidates() {
        var anchor = BlockPos.ZERO;
        var near = new BlockPos(10, 64, 0);
        var far = new BlockPos(100, 64, 0);
        assertEquals(Optional.of(near), PortalTransferSink.nearestWithinRadius(anchor, List.of(far, near), 128));
    }

    @Test
    void planDeclinesOnEmptySourceNonPositiveThroughputOrUnresolvedDestination() {
        assertFalse(PortalTransferSink.plan(0, 64, true).active());
        assertFalse(PortalTransferSink.plan(100, 0, true).active());
        assertFalse(PortalTransferSink.plan(100, 64, false).active());
    }

    @Test
    void planNeverMovesMoreThanTheSourceHasOrTheConfiguredThroughput() {
        assertEquals(10, PortalTransferSink.plan(10, 64, true).amount());
        assertEquals(64, PortalTransferSink.plan(1000, 64, true).amount());
    }

    @Test
    void filterStaleKeepsOnlyCandidatesThePredicateAcceptsInjectedAdapter() {
        var destroyedButLoaded = new BlockPos(1, 64, 1);
        var stillValidAndLoaded = new BlockPos(2, 64, 2);
        var unloaded = new BlockPos(3, 64, 3);
        // Production's real predicate requires loaded AND still-valid; unloaded is never kept,
        // just like a loaded-but-destroyed one -- an unverifiable candidate is not eligible this
        // pass, only trusted in the historical cache for a later one.
        var loadedAndValid = java.util.Set.of(stillValidAndLoaded);
        var loadedPositions = java.util.Set.of(destroyedButLoaded, stillValidAndLoaded);
        var kept = PortalTransferSink.filterStale(
            List.of(destroyedButLoaded, stillValidAndLoaded, unloaded),
            pos -> loadedPositions.contains(pos) && loadedAndValid.contains(pos));
        assertEquals(java.util.Set.of(stillValidAndLoaded), java.util.Set.copyOf(kept));
    }

    @Test
    void filterStaleDropsNothingWhenEveryCandidatePassesThePredicate() {
        var a = new BlockPos(1, 64, 1);
        var b = new BlockPos(2, 64, 2);
        assertEquals(java.util.Set.of(a, b), java.util.Set.copyOf(PortalTransferSink.filterStale(List.of(a, b), pos -> true)));
    }

    @Test
    void nearestWithinRadiusNeverPicksANearerUnloadedCandidateOverAFartherLoadedValidNetherExit() {
        var anchor = new BlockPos(800, 64, 0);
        var nearerButUnloaded = new BlockPos(805, 64, 0);
        var fartherButLoadedAndValid = new BlockPos(900, 64, 0);
        // Simulates production's eligibleCandidates: only the loaded+valid position is eligible
        // for selection this pass, even though the other one is numerically closer to the anchor.
        var eligible = PortalTransferSink.filterStale(
            List.of(nearerButUnloaded, fartherButLoadedAndValid),
            pos -> pos.equals(fartherButLoadedAndValid));
        assertEquals(Optional.of(fartherButLoadedAndValid), PortalTransferSink.nearestWithinRadius(anchor, eligible, 128));
    }

    @Test
    void chunkBoxAroundCoversEveryChunkThatCouldContainABlockWithinRadius() {
        // Anchor at the origin of chunk (0,0); a radius of 128 blocks reaches 8 chunks either way.
        var box = PortalTransferSink.chunkBoxAround(new BlockPos(5, 64, 5), 128);
        assertEquals(-8, box[0]);
        assertEquals(8, box[1]);
        assertEquals(-8, box[2]);
        assertEquals(8, box[3]);
    }

    /** {@link PortalTransferSink#advanceEndCandidateScan} requires a {@link java.util.NavigableMap}, matching production. */
    private static java.util.NavigableMap<Long, Set<BlockPos>> navigableRegistryOf(Map<ChunkPos, Set<BlockPos>> byChunk) {
        var inner = new java.util.TreeMap<Long, Set<BlockPos>>();
        byChunk.forEach((chunkPos, positions) -> inner.put(chunkPos.toLong(), positions));
        return inner;
    }

    @Test
    void advanceEndCandidateScanFindsTheFirstEligibleCandidateAndShortCircuits() {
        var a = new BlockPos(1, 64, 1);
        var b = new BlockPos(2, 64, 2);
        var registry = navigableRegistryOf(Map.of(new ChunkPos(0, 0), Set.of(a), new ChunkPos(1, 0), Set.of(b)));
        var scan = new PortalTransferSink.EndCandidateScan();
        var budget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceEndCandidateScan(registry, 10, scan, budget, pos -> true);
        assertTrue(scan.done);
        assertTrue(scan.found.equals(a) || scan.found.equals(b), "whichever chunk entry is visited first, its position is found");
    }

    @Test
    void advanceEndCandidateScanBoundsExaminationOfASingleDenseChunkEntryToTheBudget() {
        var dense = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < 10_000; i++) dense.add(new BlockPos(i, 64, 0));
        var registry = navigableRegistryOf(Map.of(new ChunkPos(0, 0), dense));
        var scan = new PortalTransferSink.EndCandidateScan();
        var budget = new PortalTransferSink.Budget(50);
        int[] callCount = {0};
        PortalTransferSink.advanceEndCandidateScan(registry, 10, scan, budget, pos -> { callCount[0]++; return false; });
        assertFalse(scan.done, "10,000 positions in one chunk entry cannot all be examined within a budget of 50");
        assertEquals(50, callCount[0], "exactly the budget's worth of positions were examined, never the whole dense set");
    }

    @Test
    void advanceEndCandidateScanResumesAcrossCallsAndExaminesEveryPositionInADenseChunkAtMostOnceUntilFound() {
        var dense = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < 237; i++) dense.add(new BlockPos(i, 64, 0));
        var registry = navigableRegistryOf(Map.of(new ChunkPos(0, 0), dense));
        var scan = new PortalTransferSink.EndCandidateScan();
        var seen = new java.util.HashSet<BlockPos>();
        int calls = 0;
        while (!scan.done) {
            var budget = new PortalTransferSink.Budget(40);
            PortalTransferSink.advanceEndCandidateScan(registry, 10, scan, budget,
                pos -> { assertTrue(seen.add(pos), "no position examined twice across the resumed scan"); return false; });
            calls++;
            assertTrue(calls < 100, "safety bound to catch a runaway loop on test failure");
        }
        assertEquals(dense, seen, "every position in the dense chunk examined exactly once across the resumed scan, none omitted");
        assertTrue(calls > 1, "a budget of 40 over 237 positions must genuinely take more than one call");
        assertEquals(null, scan.found, "nothing was ever eligible in this fixture");
    }

    @Test
    void advanceEndCandidateScanIsDoneWithNothingFoundWhenTheRegistryIsEmpty() {
        var scan = new PortalTransferSink.EndCandidateScan();
        var budget = new PortalTransferSink.Budget(10);
        PortalTransferSink.advanceEndCandidateScan(navigableRegistryOf(Map.of()), 10, scan, budget, pos -> true);
        assertTrue(scan.done);
        assertEquals(null, scan.found);
    }

    /**
     * MINECRAFT-435 (fifth review): the per-call chunk-entry limit is a YIELD point, never a
     * verdict -- a third, unexamined entry must leave the scan not-done, not conclude "nothing
     * exists" just because one call's window ran out.
     */
    @Test
    void advanceEndCandidateScanYieldsNotDoneWhenMoreEntriesExistBeyondThePerCallWindow() {
        var registry = navigableRegistryOf(Map.of(
            new ChunkPos(0, 0), Set.of(new BlockPos(1, 64, 1)),
            new ChunkPos(1, 0), Set.of(new BlockPos(2, 64, 2)),
            new ChunkPos(2, 0), Set.of(new BlockPos(3, 64, 3))));
        var scan = new PortalTransferSink.EndCandidateScan();
        var budget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceEndCandidateScan(registry, 2, scan, budget, pos -> false);
        assertFalse(scan.done, "a third entry still exists unexamined -- the 2-entry window is only a per-call yield");
        assertEquals(null, scan.found);
    }

    /**
     * MINECRAFT-435 (fifth review): "test a valid destination beyond the first window" -- the
     * eligible position sits in the third chunk entry, outside a 2-entry-per-call window; the
     * SAME source/scan attempt must still find it on a later call, not conclude "nothing exists"
     * after the first call's window.
     */
    @Test
    void advanceEndCandidateScanFindsAValidDestinationBeyondTheFirstWindowAcrossCalls() {
        var beyondFirstWindow = new BlockPos(3, 64, 3);
        var registry = navigableRegistryOf(Map.of(
            new ChunkPos(0, 0), Set.of(new BlockPos(1, 64, 1)),
            new ChunkPos(1, 0), Set.of(new BlockPos(2, 64, 2)),
            new ChunkPos(2, 0), Set.of(beyondFirstWindow)));
        var scan = new PortalTransferSink.EndCandidateScan();
        var firstCallBudget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceEndCandidateScan(registry, 2, scan, firstCallBudget, pos -> pos.equals(beyondFirstWindow));
        assertFalse(scan.done, "the eligible position is in the third entry, beyond the first call's 2-entry window");
        var secondCallBudget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceEndCandidateScan(registry, 2, scan, secondCallBudget, pos -> pos.equals(beyondFirstWindow));
        assertTrue(scan.done);
        assertEquals(beyondFirstWindow, scan.found, "the same (resumed) scan attempt finds it, not abandoned as a false negative");
    }

    @Test
    void advanceEndCandidateScanCompletesWithNothingFoundOnlyAfterWrappingAroundEveryEntry() {
        var registry = navigableRegistryOf(Map.of(
            new ChunkPos(0, 0), Set.of(new BlockPos(1, 64, 1)),
            new ChunkPos(1, 0), Set.of(new BlockPos(2, 64, 2)),
            new ChunkPos(2, 0), Set.of(new BlockPos(3, 64, 3))));
        var scan = new PortalTransferSink.EndCandidateScan();
        int calls = 0;
        while (!scan.done) {
            var budget = new PortalTransferSink.Budget(100);
            PortalTransferSink.advanceEndCandidateScan(registry, 2, scan, budget, pos -> false);
            calls++;
            assertTrue(calls < 100, "safety bound to catch a runaway loop on test failure");
        }
        assertEquals(null, scan.found);
        assertTrue(calls > 1, "a 2-entry-per-call window over 3 entries must genuinely take more than one call");
    }

    /**
     * MINECRAFT-435 (sixth review): wraparound detection must stay robust to the registry's own
     * starting entry being removed entirely between calls (e.g. that chunk's only portal
     * destroyed and rescanned as empty) -- a wraparound keyed on remembering that one specific
     * starting key would never fire again once it's gone, starving the scan forever. Tracking
     * every visited key (and completing once that count reaches the registry's current size, or
     * an already-visited key repeats) must still terminate and still examine every remaining
     * entry.
     */
    @Test
    void advanceEndCandidateScanTerminatesEvenWhenItsStartingEntryIsRemovedMidScan() {
        var registry = navigableRegistryOf(Map.of(
            new ChunkPos(0, 0), Set.of(new BlockPos(1, 64, 1)),
            new ChunkPos(1, 0), Set.of(new BlockPos(2, 64, 2)),
            new ChunkPos(2, 0), Set.of(new BlockPos(3, 64, 3))));
        var scan = new PortalTransferSink.EndCandidateScan();
        var budget = new PortalTransferSink.Budget(100);
        // First call visits exactly one entry (maxChunkEntriesPerCall = 1): the registry's first
        // key, via ceilingKey(Long.MIN_VALUE).
        PortalTransferSink.advanceEndCandidateScan(registry, 1, scan, budget, pos -> false);
        assertFalse(scan.done, "only one of three entries examined so far");
        long startingKey = new ChunkPos(0, 0).toLong();
        registry.remove(startingKey);
        int calls = 1;
        while (!scan.done) {
            PortalTransferSink.advanceEndCandidateScan(registry, 1, scan, new PortalTransferSink.Budget(100), pos -> false);
            calls++;
            assertTrue(calls < 100, "safety bound to catch a runaway loop on test failure -- "
                + "must not starve forever just because the starting entry vanished");
        }
        assertEquals(null, scan.found, "nothing eligible in this fixture");
    }

    @Test
    void advanceNetherSelectionPicksTheNearestEligibleCandidate() {
        var anchor = BlockPos.ZERO;
        var near = new BlockPos(10, 64, 0);
        var far = new BlockPos(100, 64, 0);
        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = Set.of(near, far).iterator();
        var budget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceNetherSelection(anchor, 128, selection, budget, pos -> true);
        assertTrue(selection.done);
        assertEquals(near, selection.best);
    }

    /**
     * MINECRAFT-435 (fourth review): a candidate examined while live during an earlier advance may
     * have been destroyed/unloaded by the time a later, nearer candidate turns out ineligible --
     * eligibility is revalidated inline (only when a candidate could improve on the current best),
     * so the farther-but-valid candidate is correctly kept instead of a nearer-but-stale one.
     */
    @Test
    void advanceNetherSelectionFallsBackToTheNextNearestWhenTheNearestCandidateIsIneligible() {
        var anchor = BlockPos.ZERO;
        var nearerButIneligible = new BlockPos(10, 64, 0);
        var fartherButEligible = new BlockPos(100, 64, 0);
        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = List.of(nearerButIneligible, fartherButEligible).iterator();
        var budget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceNetherSelection(anchor, 128, selection, budget, pos -> !pos.equals(nearerButIneligible));
        assertTrue(selection.done);
        assertEquals(fartherButEligible, selection.best);
    }

    @Test
    void advanceNetherSelectionIsDoneWithNoWinnerWhenEveryCandidateIsIneligible() {
        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = List.of(new BlockPos(10, 64, 0)).iterator();
        var budget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceNetherSelection(BlockPos.ZERO, 128, selection, budget, pos -> false);
        assertTrue(selection.done);
        assertEquals(null, selection.best);
    }

    @Test
    void advanceNetherSelectionDependsOnTheAnchorEvenOverAnIdenticalPool() {
        // MINECRAFT-435 (fourth review): two different anchors over the same discovered pool
        // (e.g. the same destination chunk box) must each get their OWN nearest answer.
        var a = new BlockPos(0, 64, 0);
        var b = new BlockPos(100, 64, 0);
        var selectionNearA = new PortalTransferSink.NetherSelectionScan();
        selectionNearA.remaining = List.of(a, b).iterator();
        PortalTransferSink.advanceNetherSelection(new BlockPos(-10, 64, 0), 200, selectionNearA, new PortalTransferSink.Budget(100), pos -> true);
        assertEquals(a, selectionNearA.best);

        var selectionNearB = new PortalTransferSink.NetherSelectionScan();
        selectionNearB.remaining = List.of(a, b).iterator();
        PortalTransferSink.advanceNetherSelection(new BlockPos(110, 64, 0), 200, selectionNearB, new PortalTransferSink.Budget(100), pos -> true);
        assertEquals(b, selectionNearB.best);
    }

    /**
     * MINECRAFT-435 (fifth review): selection must itself be budget-bounded and resumable -- a
     * dense pool cannot be fully examined (nor its eligibility rechecked) in a single call.
     */
    @Test
    void advanceNetherSelectionBoundsExaminationToTheBudgetAndResumesAcrossCalls() {
        var dense = new java.util.ArrayList<BlockPos>();
        for (int i = 0; i < 237; i++) dense.add(new BlockPos(i, 64, 0));
        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = dense.iterator();
        int[] eligibilityChecks = {0};
        int calls = 0;
        while (!selection.done) {
            var budget = new PortalTransferSink.Budget(40);
            PortalTransferSink.advanceNetherSelection(new BlockPos(1000, 64, 0), 2000, selection, budget,
                pos -> { eligibilityChecks[0]++; return true; });
            calls++;
            assertTrue(calls < 100, "safety bound to catch a runaway loop on test failure");
        }
        assertTrue(calls > 1, "a budget of 40 over 237 positions must genuinely take more than one call");
        // Monotonic nearest-so-far tracking means eligibility is only rechecked when a candidate
        // could improve on the current best, not once per candidate unconditionally -- bounded by
        // the pool size, never quadratic in it.
        assertTrue(eligibilityChecks[0] <= dense.size(), "eligibility checks must not exceed the pool size (no O(n^2) re-scanning)");
        assertEquals(new BlockPos(236, 64, 0), selection.best, "nearest to anchor (1000,64,0) among x in 0..236 is the largest x");
    }

    /**
     * MINECRAFT-435 (sixth review): a selection's retained winner may have been chosen several
     * calls ago and never revisited since -- {@link PortalTransferSink#finalizeNetherSelection}
     * must give it one final live recheck, and if it has gone stale in the meantime, restart over
     * the pool to find the next-nearest genuinely live candidate rather than transferring to a
     * destination that is no longer there (or silently finding none at all).
     */
    @Test
    void finalizeNetherSelectionRestartsAndFindsTheNextNearestWhenTheWinnerWentStale() {
        var anchor = BlockPos.ZERO;
        var winner = new BlockPos(10, 64, 0);
        var nextNearest = new BlockPos(50, 64, 0);
        var pool = List.of(winner, nextNearest);
        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = pool.iterator();
        PortalTransferSink.advanceNetherSelection(anchor, 128, selection, new PortalTransferSink.Budget(100), pos -> true);
        assertTrue(selection.done);
        assertEquals(winner, selection.best, "winner is initially selected as nearest");

        // Simulate the winner going stale between selection completing and final use: now
        // ineligible, everything else still eligible.
        boolean finalized = PortalTransferSink.finalizeNetherSelection(anchor, 128, selection, pool,
            new PortalTransferSink.Budget(100), pos -> !pos.equals(winner));
        assertTrue(finalized);
        assertEquals(nextNearest, selection.best, "restart must find the next-nearest candidate still genuinely live");
    }

    /**
     * MINECRAFT-435 (sixth review): if every candidate (including the prior winner) has gone
     * stale by the time of final revalidation, finalization must conclude with no winner at all,
     * never a stale one.
     */
    @Test
    void finalizeNetherSelectionEndsWithNoWinnerWhenEverythingHasGoneStale() {
        var anchor = BlockPos.ZERO;
        var winner = new BlockPos(10, 64, 0);
        var pool = List.of(winner);
        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = pool.iterator();
        PortalTransferSink.advanceNetherSelection(anchor, 128, selection, new PortalTransferSink.Budget(100), pos -> true);
        assertEquals(winner, selection.best);

        boolean finalized = PortalTransferSink.finalizeNetherSelection(anchor, 128, selection, pool,
            new PortalTransferSink.Budget(100), pos -> false);
        assertTrue(finalized);
        assertEquals(null, selection.best, "no genuinely live candidate remains");
    }

    /**
     * MINECRAFT-435 (sixth review): a restart triggered by a stale winner must itself stay
     * budget-bounded and resumable across calls for a dense pool, never re-scanning it unbounded
     * in one call just because this is "only" a revalidation restart.
     */
    @Test
    void finalizeNetherSelectionRestartIsBudgetBoundedAndResumesAcrossCalls() {
        var anchor = new BlockPos(1000, 64, 0);
        var dense = new java.util.ArrayList<BlockPos>();
        for (int i = 0; i < 237; i++) dense.add(new BlockPos(i, 64, 0));
        var stale = new BlockPos(236, 64, 0);
        java.util.function.Predicate<BlockPos> isEligible = pos -> !pos.equals(stale);

        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = dense.iterator();
        PortalTransferSink.advanceNetherSelection(anchor, 2000, selection, new PortalTransferSink.Budget(1000), pos -> true);
        assertEquals(stale, selection.best);

        // Winner (236) goes stale; every other candidate is genuinely eligible, so restarting
        // must eventually land on the next-nearest (235), but only after however many bounded
        // calls a dense pool of 237 positions actually requires -- mirroring how
        // transferNetherPortal actually drives this: resume an in-progress restart directly via
        // advanceNetherSelection (selection.done == false), only calling finalizeNetherSelection
        // again once that resumed restart itself completes.
        boolean finalized = PortalTransferSink.finalizeNetherSelection(anchor, 2000, selection, dense,
            new PortalTransferSink.Budget(40), isEligible);
        assertFalse(finalized, "a budget of 40 over 237 positions cannot finish the restart in one call");
        assertFalse(selection.done);
        int calls = 1;
        while (!finalized) {
            if (!selection.done) {
                PortalTransferSink.advanceNetherSelection(anchor, 2000, selection, new PortalTransferSink.Budget(40), isEligible);
            } else {
                finalized = PortalTransferSink.finalizeNetherSelection(anchor, 2000, selection, dense,
                    new PortalTransferSink.Budget(40), isEligible);
            }
            calls++;
            assertTrue(calls < 100, "safety bound to catch a runaway loop on test failure");
        }
        assertTrue(calls > 1, "the restart must genuinely take more than one call for a dense pool");
        assertEquals(new BlockPos(235, 64, 0), selection.best, "next-nearest to anchor once 236 is excluded as stale");
    }

    @Test
    void ensureQueueNeverStompsAnAlreadyInProgressQueue() {
        var queues = new HashMap<ResourceKey<Level>, Map<Long, PortalTransferSink.SourceQueue>>();
        var inProgress = new PortalTransferSink.SourceQueue(List.of(new BlockPos(9, 0, 0)).iterator());
        queues.computeIfAbsent(Level.NETHER, ignored -> new HashMap<>()).put(1L, inProgress);
        PortalTransferSink.ensureQueue(queues, Level.NETHER, 1L, Set.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));
        assertEquals(inProgress, PortalTransferSink.queueFor(queues, Level.NETHER, 1L),
            "a fresh discovery must never replace a pass still being drained");
    }

    @Test
    void ensureQueueDoesNothingForAnEmptyDiscovery() {
        var queues = new HashMap<ResourceKey<Level>, Map<Long, PortalTransferSink.SourceQueue>>();
        PortalTransferSink.ensureQueue(queues, Level.NETHER, 1L, Set.of());
        org.junit.jupiter.api.Assertions.assertNull(PortalTransferSink.queueFor(queues, Level.NETHER, 1L));
    }

    @Test
    void sourceQueuePeekPollDrainsExactlyOnceEachInOrder() {
        var queue = new PortalTransferSink.SourceQueue(
            List.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)).iterator());
        assertTrue(!queue.isEmpty());
        assertEquals(new BlockPos(1, 0, 0), queue.peek(), "peek must not consume");
        assertEquals(new BlockPos(1, 0, 0), queue.peek(), "repeated peek must return the same element");
        queue.poll();
        assertEquals(new BlockPos(2, 0, 0), queue.peek());
        queue.poll();
        assertTrue(queue.isEmpty());
    }

    @Test
    void advanceNetherCandidateScanFinishesInOneCallForASparseBox() {
        var only = new BlockPos(10, 64, 10); // chunk (0, 0)
        Map<Long, Set<BlockPos>> byChunk = Map.of(ChunkPos.asLong(0, 0), Set.of(only));
        var scan = new PortalTransferSink.CandidateScan();
        var budget = new PortalTransferSink.Budget(100);
        var examined = new java.util.ArrayList<BlockPos>();
        PortalTransferSink.advanceNetherCandidateScan(byChunk, 0, 0, 0, 0,
            scan, budget, pos -> { examined.add(pos); return true; });
        assertTrue(scan.done);
        assertEquals(Set.of(only), scan.eligibleCandidates);
        assertEquals(List.of(only), examined);
    }

    /**
     * MINECRAFT-435 (third review): "Add a counting dense-single-registry-chunk fixture" -- a
     * single chunk entry with far more candidates than the per-call budget must still cost
     * exactly the budget, not the whole dense set, proving one densely-populated destination
     * chunk can never force unbounded work in a single call.
     */
    @Test
    void advanceNetherCandidateScanBoundsExaminationOfASingleDenseChunkEntryToTheBudget() {
        var dense = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < 10_000; i++) dense.add(new BlockPos(i, 64, 0));
        Map<Long, Set<BlockPos>> byChunk = Map.of(ChunkPos.asLong(0, 0), dense);
        var scan = new PortalTransferSink.CandidateScan();
        var budget = new PortalTransferSink.Budget(50);
        int[] callCount = {0};
        PortalTransferSink.advanceNetherCandidateScan(byChunk, 0, 0, 0, 0,
            scan, budget, pos -> { callCount[0]++; return false; });
        assertFalse(scan.done, "10,000 positions in one chunk entry cannot all be examined within a budget of 50");
        assertEquals(50, callCount[0], "exactly the budget's worth of positions were examined, never the whole dense set");
        assertFalse(budget.hasRemaining());
    }

    @Test
    void advanceNetherCandidateScanResumesAcrossCallsAndExaminesEveryPositionInADenseChunkExactlyOnce() {
        var dense = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < 237; i++) dense.add(new BlockPos(i, 64, 0));
        Map<Long, Set<BlockPos>> byChunk = Map.of(ChunkPos.asLong(0, 0), dense);
        var scan = new PortalTransferSink.CandidateScan();
        var seen = new java.util.HashSet<BlockPos>();
        int calls = 0;
        while (!scan.done) {
            var budget = new PortalTransferSink.Budget(40);
            PortalTransferSink.advanceNetherCandidateScan(byChunk, 0, 0, 0, 0,
                scan, budget, pos -> { assertTrue(seen.add(pos), "no position examined twice across the resumed scan"); return true; });
            calls++;
            assertTrue(calls < 100, "safety bound to catch a runaway loop on test failure");
        }
        assertEquals(dense, seen, "every position in the dense chunk examined exactly once across the resumed scan, none omitted");
        assertEquals(dense, scan.eligibleCandidates);
        assertTrue(calls > 1, "a budget of 40 over 237 positions must genuinely take more than one call");
    }

    /**
     * Integration of {@link PortalTransferSink#advanceNetherCandidateScan} (anchor-independent
     * discovery, possibly spread over several calls) with {@link
     * PortalTransferSink#advanceNetherSelection} (anchor-specific selection, applied once
     * discovery is done) -- the true nearest candidate is found regardless of which call examined
     * it during discovery.
     */
    @Test
    void discoveredPoolYieldsTheTrueNearestRegardlessOfWhichCallExaminedEachCandidate() {
        var nearToAnchor = new BlockPos(5, 64, 0); // chunk (0, 0)
        var farFromAnchor = new BlockPos(1000, 64, 0); // chunk (62, 0)
        var byChunk = new java.util.HashMap<Long, Set<BlockPos>>();
        byChunk.put(ChunkPos.asLong(0, 0), Set.of(farFromAnchor));
        byChunk.put(ChunkPos.asLong(62, 0), Set.of(nearToAnchor));
        var scan = new PortalTransferSink.CandidateScan();
        int calls = 0;
        while (!scan.done) {
            var budget = new PortalTransferSink.Budget(1);
            PortalTransferSink.advanceNetherCandidateScan(byChunk, 0, 62, 0, 0, scan, budget, pos -> true);
            calls++;
            assertTrue(calls < 100, "safety bound to catch a runaway loop on test failure");
        }
        var anchor = new BlockPos(10, 64, 0);
        var selection = new PortalTransferSink.NetherSelectionScan();
        selection.remaining = scan.eligibleCandidates.iterator();
        PortalTransferSink.advanceNetherSelection(anchor, 2000, selection, new PortalTransferSink.Budget(100), pos -> true);
        assertTrue(selection.done);
        assertEquals(nearToAnchor, selection.best,
            "the globally nearest candidate is found even though a farther one was examined in an earlier discovery call");
    }

    @Test
    void acceptedAmountMeasuresTheDestinationsOwnBeforeAfterDeltaNotThePlannedAmount() {
        // A near-full destination can clamp an emit short of what was requested while
        // DynamicAtmosphereMod#emitMaterial still reports success for the partial addition.
        assertEquals(10, PortalTransferSink.acceptedAmount(990, 1000));
        assertEquals(0, PortalTransferSink.acceptedAmount(1000, 1000));
    }

    @Test
    void acceptedAmountNeverGoesNegativeEvenIfSomethingElseDrainedTheCellConcurrently() {
        assertEquals(0, PortalTransferSink.acceptedAmount(1000, 400));
    }

    /** In-memory fake standing in for the live grid, so {@code transfer} is testable end to end. */
    private static final class FakeGrid implements PortalTransferSink.MaterialPorts {
        private final java.util.Map<BlockPos, Integer> amounts = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, Integer> capacity = new java.util.HashMap<>();
        private final java.util.Set<BlockPos> unavailable = new java.util.HashSet<>();
        private final java.util.Set<BlockPos> consumeFails = new java.util.HashSet<>();
        private int emitCalls;
        private int consumeCalls;

        FakeGrid put(BlockPos pos, int amount, int capacity) {
            amounts.put(pos, amount);
            this.capacity.put(pos, capacity);
            return this;
        }

        FakeGrid markUnavailable(BlockPos pos) {
            unavailable.add(pos);
            return this;
        }

        FakeGrid failConsumeAt(BlockPos pos) {
            consumeFails.add(pos);
            return this;
        }

        int amountAt(BlockPos pos) { return amounts.getOrDefault(pos, 0); }

        @Override
        public Optional<AtmosphereMaterialState> state(net.minecraft.server.level.ServerLevel level,
                                                         AtmosphereMaterial material, BlockPos pos) {
            if (unavailable.contains(pos)) return Optional.empty();
            return Optional.of(new AtmosphereMaterialState(amountAt(pos), capacity.getOrDefault(pos, 1000)));
        }

        @Override
        public boolean emit(net.minecraft.server.level.ServerLevel level, AtmosphereMaterial material, BlockPos pos, int amount) {
            emitCalls++;
            int current = amountAt(pos);
            int headroom = Math.max(0, capacity.getOrDefault(pos, 1000) - current);
            amounts.put(pos, current + Math.min(amount, headroom));
            return true;
        }

        @Override
        public boolean consume(net.minecraft.server.level.ServerLevel level, AtmosphereMaterial material, BlockPos pos, int amount) {
            consumeCalls++;
            if (consumeFails.contains(pos)) return false;
            int current = amountAt(pos);
            if (current < amount) return false;
            amounts.put(pos, current - amount);
            return true;
        }
    }

    @Test
    void transferOnlyMovesWhatTheDestinationActuallyAcceptsOnAPartialClampedEmit() {
        var source = new BlockPos(0, 64, 0);
        var destination = new BlockPos(1000, 64, 0);
        var grid = new FakeGrid().put(source, 100, 1000).put(destination, 990, 1000);
        var sink = PortalTransferSink.createForTest(grid);
        sink.transfer(null, source, null, destination, AtmosphereMaterial.NETHER_GAS, 64);
        // Only 10 units of headroom existed at the destination; only that much is ever debited
        // from the source, even though 64 were planned -- conservation holds (100+990 == 90+1000).
        assertEquals(90, grid.amountAt(source));
        assertEquals(1000, grid.amountAt(destination));
    }

    @Test
    void transferRollsBackTheDestinationWhenTheSourceDebitFails() {
        var source = new BlockPos(0, 64, 0);
        var destination = new BlockPos(1000, 64, 0);
        var grid = new FakeGrid().put(source, 100, 1000).put(destination, 0, 1000).failConsumeAt(source);
        var sink = PortalTransferSink.createForTest(grid);
        sink.transfer(null, source, null, destination, AtmosphereMaterial.NETHER_GAS, 64);
        // The source debit was made to fail; the destination's accepted amount must be rolled
        // back so material is never duplicated -- both totals end exactly where they started.
        assertEquals(100, grid.amountAt(source));
        assertEquals(0, grid.amountAt(destination));
    }

    @Test
    void transferPreservesBothTotalsWhenTheDestinationIsUnavailable() {
        var source = new BlockPos(0, 64, 0);
        var destination = new BlockPos(1000, 64, 0);
        var grid = new FakeGrid().put(source, 100, 1000).markUnavailable(destination);
        var sink = PortalTransferSink.createForTest(grid);
        sink.transfer(null, source, null, destination, AtmosphereMaterial.NETHER_GAS, 64);
        assertEquals(100, grid.amountAt(source));
        assertEquals(0, grid.emitCalls, "an unavailable destination must never be emitted into");
        assertEquals(0, grid.consumeCalls, "nothing was ever accepted, so nothing may be debited");
    }
}
