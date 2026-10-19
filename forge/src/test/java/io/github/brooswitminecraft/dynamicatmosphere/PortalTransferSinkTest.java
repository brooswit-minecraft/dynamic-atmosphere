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
    void anyReturnsEmptyOnlyWhenNoCandidateHasEverBeenObserved() {
        assertTrue(PortalTransferSink.any(List.of()).isEmpty());
        var observed = new BlockPos(100, 50, 0);
        assertEquals(Optional.of(observed), PortalTransferSink.any(List.of(observed)));
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
    void anyNeverPicksAnUnloadedEndCandidateOverALoadedValidExit() {
        var unloaded = new BlockPos(100, 50, 0);
        var loadedAndValid = new BlockPos(200, 50, 0);
        var eligible = PortalTransferSink.filterStale(
            List.of(unloaded, loadedAndValid), pos -> pos.equals(loadedAndValid));
        assertEquals(Optional.of(loadedAndValid), PortalTransferSink.any(eligible));
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

    private static Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> registryOf(
        ResourceKey<Level> dimension, Map<ChunkPos, Set<BlockPos>> byChunk
    ) {
        var inner = new HashMap<Long, Set<BlockPos>>();
        byChunk.forEach((chunkPos, positions) -> inner.put(chunkPos.toLong(), positions));
        var outer = new HashMap<ResourceKey<Level>, Map<Long, Set<BlockPos>>>();
        outer.put(dimension, inner);
        return outer;
    }

    @Test
    void knownPositionsNearOnlyTouchesChunksInsideTheGivenBox() {
        var inBox = new BlockPos(10, 64, 10); // chunk (0, 0)
        var outsideBox = new BlockPos(2000, 64, 2000); // chunk (125, 125)
        var registry = registryOf(Level.NETHER, Map.of(
            new ChunkPos(0, 0), Set.of(inBox),
            new ChunkPos(125, 125), Set.of(outsideBox)));
        var found = PortalTransferSink.knownPositionsNear(registry, Level.NETHER, -1, 1, -1, 1);
        assertEquals(Set.of(inBox), Set.copyOf(found));
    }

    @Test
    void knownPositionsNearReturnsEmptyForAnUnknownDimensionOrEmptyRegistry() {
        var registry = registryOf(Level.NETHER, Map.of(new ChunkPos(0, 0), Set.of(new BlockPos(1, 64, 1))));
        assertTrue(PortalTransferSink.knownPositionsNear(registry, Level.END, -1, 1, -1, 1).isEmpty());
        assertTrue(PortalTransferSink.knownPositionsNear(Map.of(), Level.NETHER, -1, 1, -1, 1).isEmpty());
    }

    @Test
    void knownPositionsNearCombinesEveryChunkInsideTheBoxButNoneOutsideIt() {
        var west = new BlockPos(-10, 64, 0); // chunk (-1, 0)
        var east = new BlockPos(10, 64, 0); // chunk (0, 0)
        var farEast = new BlockPos(2000, 64, 0); // chunk (125, 0)
        var registry = registryOf(Level.OVERWORLD, Map.of(
            new ChunkPos(-1, 0), Set.of(west),
            new ChunkPos(0, 0), Set.of(east),
            new ChunkPos(125, 0), Set.of(farEast)));
        var found = Set.copyOf(PortalTransferSink.knownPositionsNear(registry, Level.OVERWORLD, -1, 0, 0, 0));
        assertEquals(Set.of(west, east), found);
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
