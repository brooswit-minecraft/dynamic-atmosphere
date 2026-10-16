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
}
