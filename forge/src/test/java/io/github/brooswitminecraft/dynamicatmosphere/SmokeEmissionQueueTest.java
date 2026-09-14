package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeEmissionQueueTest {
    @Test
    void coalescesAmountsAndDrainsInBoundedFifoOrder() {
        var queue = new SmokeEmissionQueue<String>(2, 1000);
        assertEquals(40, queue.offer("a", 40));
        assertEquals(80, queue.offer("a", 80));
        queue.offer("b", 10);
        assertEquals(List.of(new SmokeEmissionQueue.Emission<>("a", 120)), queue.drain(1));
        assertEquals(1, queue.size());
        assertEquals(List.of(new SmokeEmissionQueue.Emission<>("b", 10)), queue.drain(1));
        assertTrue(queue.drain(1).isEmpty());
    }

    @Test
    void capacityNeverEvictsAndReportsEveryUnacceptedUnit() {
        var queue = new SmokeEmissionQueue<String>(1, 100);
        queue.offer("a", 80);
        assertEquals(0, queue.offer("b", 40));
        assertEquals(20, queue.offer("a", Integer.MAX_VALUE));
        assertEquals(40L + Integer.MAX_VALUE - 20, queue.rejectedAmount());
        assertEquals(List.of(new SmokeEmissionQueue.Emission<>("a", 100)), queue.drain(10));
    }

    @Test
    void detachedDrainDoesNotRecursivelyConsumeNewEventsAndClearDropsOldWorld() {
        var queue = new SmokeEmissionQueue<String>();
        queue.offer("a", 40);
        var drained = queue.drain(SmokeEmissionQueue.DEFAULT_DRAIN_LIMIT);
        queue.offer("b", 20);
        assertEquals(List.of(new SmokeEmissionQueue.Emission<>("a", 40)), drained);
        assertEquals(1, queue.size());
        queue.clear();
        assertTrue(queue.drain(128).isEmpty());
        assertEquals(0, queue.rejectedAmount());
    }

    @Test
    void zeroAndInvalidLimitsCannotCreateWork() {
        var queue = new SmokeEmissionQueue<String>();
        assertEquals(0, queue.offer("a", 0));
        assertEquals(0, queue.size());
        assertTrue(queue.drain(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queue.offer("a", -1));
        assertThrows(IllegalArgumentException.class, () -> queue.drain(-1));
        assertThrows(IllegalArgumentException.class, () -> new SmokeEmissionQueue<>(0, 1));
    }
}
