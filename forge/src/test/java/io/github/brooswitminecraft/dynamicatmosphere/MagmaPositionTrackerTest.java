package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagmaPositionTrackerTest {

    @Test
    void placementAddsAndNeverRollsOnItsOwn() {
        var tracker = new MagmaPositionTracker<Integer>();
        assertFalse(tracker.observe(1, true));
    }

    @Test
    void firstRemovalOfTrackedMagmaRollsExactlyOnce() {
        var tracker = new MagmaPositionTracker<Integer>();
        tracker.observe(1, true);

        assertTrue(tracker.observe(1, false));
        // The position is no longer tracked as magma, so a second consecutive
        // non-magma report (e.g. the lava write's own notify) does not re-roll.
        assertFalse(tracker.observe(1, false));
    }

    @Test
    void theLavaWriteItselfDoesNotRetrigger() {
        var tracker = new MagmaPositionTracker<Integer>();
        tracker.observe(1, true);
        assertTrue(tracker.observe(1, false));

        // Placing lava at the position is itself a non-magma report at an
        // already-untracked position.
        assertFalse(tracker.observe(1, false));
    }

    @Test
    void anUntrackedPositionNeverRolls() {
        var tracker = new MagmaPositionTracker<Integer>();
        assertFalse(tracker.observe(1, false));
    }

    @Test
    void forgettingAChunkDropsItsPositionsSoAFutureReappearanceStartsClean() {
        var tracker = new MagmaPositionTracker<Integer>();
        tracker.observe(1, true);
        tracker.observe(2, true);

        tracker.forgetAll(List.of(1, 2));

        // Forgotten while still (logically) magma: a later report of the same
        // key as magma is a fresh placement, and non-magma is not a removal.
        assertFalse(tracker.observe(1, false));
        assertFalse(tracker.observe(2, true));
    }

    @Test
    void pistonExemptMagmaMovingAwayDoesNotRoll() {
        var tracker = new MagmaPositionTracker<Integer>();
        tracker.observe(1, true);
        tracker.markPistonExempt(1);

        assertFalse(tracker.observe(1, false));
        // The exemption is consumed by the observe above; a later genuine
        // removal at the same key (after it becomes magma again) still rolls.
        tracker.observe(1, true);
        assertTrue(tracker.observe(1, false));
    }

    @Test
    void clearingAPistonExemptionThatWasNeverConsumedLeavesNoLeak() {
        var tracker = new MagmaPositionTracker<Integer>();
        tracker.observe(1, true);
        tracker.markPistonExempt(1);

        // The piston move never actually happened (e.g. blocked elsewhere);
        // Post-side cleanup clears the exemption without it ever being observed.
        tracker.clearPistonExempt(1);

        assertTrue(tracker.observe(1, false));
    }
}
