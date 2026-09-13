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
        assertTrue(AtmosphereProducerSchedule.passesChance(0.25, 0.0));
        assertTrue(AtmosphereProducerSchedule.passesChance(0.25, Math.nextDown(0.25)));
        assertFalse(AtmosphereProducerSchedule.passesChance(0.25, 0.25));
        assertFalse(AtmosphereProducerSchedule.passesChance(0.25, Math.nextDown(1.0)));
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereProducerSchedule.passesChance(0.25, Double.NaN));
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
}
