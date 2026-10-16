package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
        var unloadedUnverifiable = new BlockPos(3, 64, 3);
        // Fake adapter: "loaded" positions are verified directly; an unloaded one can't be
        // checked without forcing a load, so filterStale must trust it (keep it) regardless.
        var loadedAndValid = java.util.Set.of(stillValidAndLoaded);
        var loadedPositions = java.util.Set.of(destroyedButLoaded, stillValidAndLoaded);
        var kept = PortalTransferSink.filterStale(
            List.of(destroyedButLoaded, stillValidAndLoaded, unloadedUnverifiable),
            pos -> !loadedPositions.contains(pos) || loadedAndValid.contains(pos));
        assertEquals(java.util.Set.of(stillValidAndLoaded, unloadedUnverifiable), java.util.Set.copyOf(kept));
    }

    @Test
    void filterStaleDropsNothingWhenEveryCandidatePassesThePredicate() {
        var a = new BlockPos(1, 64, 1);
        var b = new BlockPos(2, 64, 2);
        assertEquals(java.util.Set.of(a, b), java.util.Set.copyOf(PortalTransferSink.filterStale(List.of(a, b), pos -> true)));
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
}
