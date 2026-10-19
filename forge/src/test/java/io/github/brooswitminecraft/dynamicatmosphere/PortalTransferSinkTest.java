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

    @Test
    void advanceEndCandidateScanOnlyTouchesTheBoundedNumberOfChunkEntriesWhenNothingIsEligible() {
        var registry = navigableRegistryOf(Map.of(
            new ChunkPos(0, 0), Set.of(new BlockPos(1, 64, 1)),
            new ChunkPos(1, 0), Set.of(new BlockPos(2, 64, 2)),
            new ChunkPos(2, 0), Set.of(new BlockPos(3, 64, 3))));
        var scan = new PortalTransferSink.EndCandidateScan();
        var budget = new PortalTransferSink.Budget(100);
        PortalTransferSink.advanceEndCandidateScan(registry, 2, scan, budget, pos -> false);
        assertTrue(scan.done, "done once the bounded 2-entry window is exhausted, even though a third entry exists");
        assertEquals(null, scan.found);
    }

    @Test
    void selectLiveNearestPicksTheNearestEligibleCandidate() {
        var anchor = BlockPos.ZERO;
        var near = new BlockPos(10, 64, 0);
        var far = new BlockPos(100, 64, 0);
        var pool = new java.util.HashSet<>(Set.of(near, far));
        assertEquals(Optional.of(near), PortalTransferSink.selectLiveNearest(anchor, pool, 128, pos -> true));
    }

    /**
     * MINECRAFT-435 (fourth review): a candidate added to the pool while live during an earlier
     * advance may have been destroyed/unloaded by the time the scan completes and a winner is
     * selected -- the nearer one must be revalidated and, if stale, discarded in favor of the
     * next-nearest, never silently accepted.
     */
    @Test
    void selectLiveNearestRevalidatesAndFallsBackWhenTheNearestCandidateHasGoneStale() {
        var anchor = BlockPos.ZERO;
        var nearerButNowStale = new BlockPos(10, 64, 0);
        var fartherButStillValid = new BlockPos(100, 64, 0);
        var pool = new java.util.HashSet<>(Set.of(nearerButNowStale, fartherButStillValid));
        var result = PortalTransferSink.selectLiveNearest(anchor, pool, 128, pos -> !pos.equals(nearerButNowStale));
        assertEquals(Optional.of(fartherButStillValid), result);
        assertEquals(Set.of(fartherButStillValid), pool, "the stale candidate is discarded from the pool");
    }

    @Test
    void selectLiveNearestReturnsEmptyWhenEveryCandidateIsStale() {
        var anchor = BlockPos.ZERO;
        var pool = new java.util.HashSet<>(Set.of(new BlockPos(10, 64, 0)));
        assertTrue(PortalTransferSink.selectLiveNearest(anchor, pool, 128, pos -> false).isEmpty());
        assertTrue(pool.isEmpty());
    }

    @Test
    void selectLiveNearestDependsOnTheAnchorEvenOverAnIdenticalSharedPool() {
        // MINECRAFT-435 (fourth review): two different anchors sharing the same discovered pool
        // (e.g. the same destination chunk box) must each get their OWN nearest answer.
        var a = new BlockPos(0, 64, 0);
        var b = new BlockPos(100, 64, 0);
        var pool = Set.of(a, b);
        assertEquals(Optional.of(a), PortalTransferSink.selectLiveNearest(new BlockPos(-10, 64, 0), new java.util.HashSet<>(pool), 200, pos -> true));
        assertEquals(Optional.of(b), PortalTransferSink.selectLiveNearest(new BlockPos(110, 64, 0), new java.util.HashSet<>(pool), 200, pos -> true));
    }

    @Test
    void ensureQueueNeverStompsAnAlreadyInProgressQueue() {
        var queues = new HashMap<ResourceKey<Level>, Map<Long, java.util.Deque<BlockPos>>>();
        var inProgress = new java.util.ArrayDeque<>(List.of(new BlockPos(9, 0, 0)));
        queues.computeIfAbsent(Level.NETHER, ignored -> new HashMap<>()).put(1L, inProgress);
        PortalTransferSink.ensureQueue(queues, Level.NETHER, 1L, Set.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));
        assertEquals(inProgress, PortalTransferSink.queueFor(queues, Level.NETHER, 1L),
            "a fresh discovery must never replace a pass still being drained");
    }

    @Test
    void ensureQueueDoesNothingForAnEmptyDiscovery() {
        var queues = new HashMap<ResourceKey<Level>, Map<Long, java.util.Deque<BlockPos>>>();
        PortalTransferSink.ensureQueue(queues, Level.NETHER, 1L, Set.of());
        org.junit.jupiter.api.Assertions.assertNull(PortalTransferSink.queueFor(queues, Level.NETHER, 1L));
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
     * PortalTransferSink#selectLiveNearest} (anchor-specific selection, applied once discovery is
     * done) -- the true nearest candidate is found regardless of which call examined it.
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
        assertEquals(Optional.of(nearToAnchor),
            PortalTransferSink.selectLiveNearest(anchor, scan.eligibleCandidates, 2000, pos -> true),
            "the globally nearest candidate is found even though a farther one was examined in an earlier call");
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
