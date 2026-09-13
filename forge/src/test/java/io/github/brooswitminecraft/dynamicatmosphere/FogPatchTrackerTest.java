package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FogPatchTrackerTest {

    @Test
    void scalesDepthTargetWithinBounds() {
        assertEquals(1, FogPatchTracker.strengthForDepth(-4));
        assertEquals(4, FogPatchTracker.strengthForDepth(4));
        assertEquals(8, FogPatchTracker.strengthForDepth(99));
    }

    @Test
    void suppliedPatchBuildsOneStepPerPassTowardDepthTarget() {
        FogPatchTracker<String> tracker = new FogPatchTracker<>(4, 1000, 100);

        assertTrue(tracker.observe("lake", 4, 0));
        assertEquals(1, tracker.patches().getFirst().strength());
        tracker.observe("lake", 4, 100);
        assertEquals(2, tracker.patches().getFirst().strength());
        tracker.observe("lake", 4, 200);
        tracker.observe("lake", 4, 300);
        tracker.observe("lake", 4, 400);

        assertEquals(4, tracker.patches().getFirst().strength());
        assertEquals(4, tracker.patches().getFirst().waterDepth());
        assertEquals(400, tracker.patches().getFirst().lastSeenTick());
    }

    @Test
    void duplicateObservationInOnePassIsIdempotent() {
        FogPatchTracker<String> tracker = new FogPatchTracker<>(4, 1000, 100);
        tracker.observe("lake", 8, 100);
        tracker.observe("lake", 8, 100);
        tracker.observe("lake", 8, 100);

        assertEquals(1, tracker.patches().getFirst().strength());
        assertEquals(1, tracker.size());
    }

    @Test
    void unsuppliedPatchFadesOneStepPerPassAndThenExpires() {
        FogPatchTracker<String> tracker = new FogPatchTracker<>(4, 1000, 100);
        tracker.observe("lake", 3, 0);
        tracker.observe("lake", 3, 100);
        tracker.observe("lake", 3, 200);
        assertEquals(3, tracker.patches().getFirst().strength());

        assertEquals(0, tracker.advance(300));
        assertEquals(2, tracker.patches().getFirst().strength());
        assertEquals(0, tracker.advance(400));
        assertEquals(1, tracker.patches().getFirst().strength());
        assertEquals(1, tracker.advance(500));
        assertEquals(0, tracker.size());
    }

    @Test
    void hardExpiryRemovesStateEvenWhenStrengthWouldRemain() {
        FogPatchTracker<String> tracker = new FogPatchTracker<>(4, 250, 100);
        tracker.observe("lake", 8, 0);
        tracker.observe("lake", 8, 100);

        assertEquals(1, tracker.advance(350));
        assertEquals(0, tracker.size());
    }

    @Test
    void capacityReusesOnlyStateOlderThanTheCurrentPass() {
        FogPatchTracker<String> tracker = new FogPatchTracker<>(2, 1000, 100);
        assertTrue(tracker.observe("a", 1, 0));
        assertTrue(tracker.observe("b", 2, 0));
        assertFalse(tracker.observe("c", 3, 0));

        assertTrue(tracker.observe("c", 3, 100));
        assertTrue(tracker.observe("d", 4, 100));
        assertFalse(tracker.observe("e", 5, 100));
        assertEquals(2, tracker.size());
        assertEquals("c", tracker.patches().get(0).key());
        assertEquals("d", tracker.patches().get(1).key());

        tracker.clear();
        assertEquals(0, tracker.size());
    }
}
