package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereProducerScheduleTest {

    @Test
    void chanceGateUsesHalfOpenRollBoundary() {
        assertTrue(AtmosphereProducerSchedule.passesChance(0.10, 0.0));
        assertTrue(AtmosphereProducerSchedule.passesChance(0.10, Math.nextDown(0.10)));
        assertFalse(AtmosphereProducerSchedule.passesChance(0.10, 0.10));
        assertFalse(AtmosphereProducerSchedule.passesChance(0.10, Math.nextDown(1.0)));
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereProducerSchedule.passesChance(0.10, Double.NaN));
    }

    @Test
    void cycleIsUniqueBoundedAndCannotBeReplacedWhileBacklogged() {
        var schedule = new AtmosphereProducerSchedule<String>();
        assertTrue(schedule.beginCycle(List.of("a", "b", "a", "c")));
        assertFalse(schedule.beginCycle(List.of("replacement")));
        assertEquals(List.of("a", "b"), schedule.poll(2, ignored -> true));
        assertEquals(1, schedule.pending());
        assertEquals(List.of("c"), schedule.poll(2, ignored -> true));
        assertEquals(0, schedule.pending());
        assertTrue(schedule.beginCycle(List.of("replacement")));
    }

    @Test
    void unloadedEntriesAreSkippedWithoutDroppingRemainingWork() {
        var schedule = new AtmosphereProducerSchedule<String>();
        schedule.beginCycle(List.of("loaded", "unloaded", "later"));

        assertEquals(List.of("loaded"), schedule.poll(2, Set.of("loaded", "later")::contains));
        assertEquals(1, schedule.pending());
        assertEquals(List.of("later"), schedule.poll(1, ignored -> true));
    }

    @Test
    void requeuePutsAnUnfinishedKeyBehindAlreadyPendingWorkForFairRoundRobinContinuation() {
        var schedule = new AtmosphereProducerSchedule<String>();
        schedule.beginCycle(List.of("a", "b"));
        assertEquals(List.of("a"), schedule.poll(1, ignored -> true));
        // "a" needed another pass -- it goes to the BACK, behind "b", which is still pending.
        schedule.requeue("a");
        assertEquals(2, schedule.pending());
        assertEquals(List.of("b"), schedule.poll(1, ignored -> true));
        assertEquals(List.of("a"), schedule.poll(1, ignored -> true));
        assertEquals(0, schedule.pending());
    }

    @Test
    void requeuedKeyKeepsTheCurrentCycleFromBeingReplaced() {
        var schedule = new AtmosphereProducerSchedule<String>();
        schedule.beginCycle(List.of("a"));
        schedule.poll(1, ignored -> true);
        schedule.requeue("a");
        // Still backlogged: a new cycle must not silently replace the unfinished one.
        assertFalse(schedule.beginCycle(List.of("replacement")));
        assertEquals(List.of("a"), schedule.poll(1, ignored -> true));
        assertTrue(schedule.beginCycle(List.of("replacement")));
    }
}
