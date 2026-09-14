package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereFanScheduleTest {
    @Test
    void runsEveryFiveSecondsWithoutRandomSkippingOrDuplicateCycles() {
        var schedule = new AtmosphereFanSchedule<String>();
        for (long tick : new long[] {1, 99}) assertTrue(schedule.poll(tick, 100, 32, List.of("fan"), k -> true).isEmpty());
        assertEquals(List.of("fan"), schedule.poll(100, 100, 32, List.of("fan", "fan"), k -> true));
        assertTrue(schedule.poll(100, 100, 32, List.of("fan"), k -> true).isEmpty());
        assertTrue(schedule.poll(199, 100, 32, List.of("fan"), k -> true).isEmpty());
        assertEquals(List.of("fan"), schedule.poll(200, 100, 32, List.of("fan"), k -> true));
        assertEquals(2, schedule.cycles());
    }

    @Test
    void drainsBoundedBacklogWithoutRestartingItAndDropsUnloadedChunks() {
        var schedule = new AtmosphereFanSchedule<String>();
        var keys = List.of("a", "b", "unloaded", "d");
        assertEquals(List.of("a"), schedule.poll(100, 100, 1, keys, k -> true));
        assertEquals(List.of("b"), schedule.poll(200, 100, 1, keys, k -> true));
        assertTrue(schedule.poll(201, 100, 1, keys, k -> !k.equals("unloaded")).isEmpty());
        assertEquals(List.of("d"), schedule.poll(202, 100, 1, keys, k -> true));
        assertEquals(1, schedule.cycles());
        schedule.clear();
        assertEquals(0, schedule.pending());
        assertEquals(0, schedule.cycles());
        assertEquals(keys, schedule.poll(100, 100, 32, keys, k -> true));
    }
}
