package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for a review finding on MINECRAFT-1 (B4): EndSandBlock's rise used to ignore the
 * result of both setBlock calls in its move, so a failed destination write could delete the
 * source with nothing produced, and a failed source clear after a successful destination write
 * could duplicate the block. These exercise {@link EndSandBlock#attemptRise}, the actual
 * production decision/action logic the tick now runs, with injected write failures -- not a
 * reimplementation fed arbitrary booleans.
 */
class EndSandBlockTest {
    @Test
    void risesWhenBothWritesSucceed() {
        boolean[] undone = {false};
        var outcome = EndSandBlock.attemptRise(
            false, () -> fail("removeAtCeiling"),
            true,
            () -> true,
            () -> true,
            () -> undone[0] = true);

        assertEquals(EndSandBlock.Outcome.MOVED, outcome);
        assertFalse(undone[0], "a clean move must not trigger the undo path");
    }

    @Test
    void obstructedAboveStopsWithoutTouchingAnyBlock() {
        var outcome = EndSandBlock.attemptRise(
            false, () -> fail("removeAtCeiling"),
            false,
            () -> fail("placeAbove"),
            () -> fail("clearSource"),
            () -> fail("undoAbove"));

        assertEquals(EndSandBlock.Outcome.BLOCKED, outcome);
    }

    @Test
    void failedDestinationWriteLeavesTheSourceUntouched() {
        boolean[] clearSourceCalled = {false};
        boolean[] undone = {false};
        var outcome = EndSandBlock.attemptRise(
            false, () -> fail("removeAtCeiling"),
            true,
            () -> false, // destination write refused/failed
            () -> { clearSourceCalled[0] = true; return true; },
            () -> undone[0] = true);

        assertEquals(EndSandBlock.Outcome.BLOCKED, outcome);
        assertFalse(clearSourceCalled[0], "must not touch the source once the destination write failed");
        assertFalse(undone[0], "nothing to undo when the destination write never landed");
    }

    @Test
    void failedSourceClearAfterASuccessfulDestinationWriteIsUndoneRatherThanDuplicated() {
        var outcome = EndSandBlock.attemptRise(
            false, () -> fail("removeAtCeiling"),
            true,
            () -> true,  // destination write succeeds
            () -> false, // but clearing the source fails -- would duplicate
            () -> {}); // undo recorded implicitly by reaching BLOCKED below

        assertEquals(EndSandBlock.Outcome.BLOCKED, outcome);
    }

    @Test
    void failedSourceClearActuallyInvokesTheUndo() {
        boolean[] undone = {false};
        EndSandBlock.attemptRise(
            false, () -> fail("removeAtCeiling"),
            true,
            () -> true,
            () -> false,
            () -> undone[0] = true);

        assertTrue(undone[0], "a would-be duplicate must be rolled back via the undo callback");
    }

    @Test
    void reachingTheCeilingDeletesInsteadOfMoving() {
        boolean[] removed = {false};
        var outcome = EndSandBlock.attemptRise(
            true,
            () -> { removed[0] = true; return true; },
            true,
            () -> fail("placeAbove"),
            () -> fail("clearSource"),
            () -> fail("undoAbove"));

        assertEquals(EndSandBlock.Outcome.DELETED, outcome);
        assertTrue(removed[0], "ceiling deletion must actually remove the source block");
    }

    private static boolean fail(String what) {
        throw new AssertionError("must not call " + what + " on this path");
    }
}
