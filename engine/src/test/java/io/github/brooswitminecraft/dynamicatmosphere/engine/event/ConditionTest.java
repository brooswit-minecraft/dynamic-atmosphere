package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionTest {

    @Test
    void concentrationAtLeastMatchesAtAndAboveThreshold() {
        var condition = new Condition.ConcentrationAtLeast(0.85);
        assertFalse(condition.matches(new EventContext(0.84, true, 0.5)));
        assertTrue(condition.matches(new EventContext(0.85, true, 0.5)));
        assertTrue(condition.matches(new EventContext(1.0, true, 0.5)));
    }

    @Test
    void concentrationAtMostMatchesAtAndBelowThreshold() {
        var condition = new Condition.ConcentrationAtMost(0.4);
        assertTrue(condition.matches(new EventContext(0.4, false, 0)));
        assertFalse(condition.matches(new EventContext(0.41, false, 0)));
    }

    @Test
    void precipitatingMatchesExactFlag() {
        assertTrue(new Condition.Precipitating(true).matches(new EventContext(0, true, 0)));
        assertFalse(new Condition.Precipitating(true).matches(new EventContext(0, false, 0)));
        assertTrue(new Condition.Precipitating(false).matches(new EventContext(0, false, 0)));
    }

    @Test
    void biomeTemperatureBoundsMatchInclusively() {
        assertTrue(new Condition.BiomeTemperatureAtLeast(0.15).matches(new EventContext(0, false, 0.15)));
        assertFalse(new Condition.BiomeTemperatureAtLeast(0.15).matches(new EventContext(0, false, 0.1)));
        assertTrue(new Condition.BiomeTemperatureAtMost(0.15).matches(new EventContext(0, false, 0.15)));
        assertFalse(new Condition.BiomeTemperatureAtMost(0.15).matches(new EventContext(0, false, 0.2)));
    }

    @Test
    void allMatchRequiresEveryCondition() {
        var context = new EventContext(0.9, true, 0.8);
        assertTrue(Condition.allMatch(List.of(
            new Condition.ConcentrationAtLeast(0.85), new Condition.Precipitating(true)), context));
        assertFalse(Condition.allMatch(List.of(
            new Condition.ConcentrationAtLeast(0.95), new Condition.Precipitating(true)), context));
        assertTrue(Condition.allMatch(List.of(), context));
    }

    @Test
    void rejectsOutOfRangeOrNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> new Condition.ConcentrationAtLeast(-0.01));
        assertThrows(IllegalArgumentException.class, () -> new Condition.ConcentrationAtLeast(1.01));
        assertThrows(IllegalArgumentException.class, () -> new Condition.ConcentrationAtMost(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Condition.BiomeTemperatureAtLeast(Double.POSITIVE_INFINITY));
    }

    @Test
    void eventContextRejectsOutOfRangeConcentrationOrNonFiniteTemperature() {
        assertThrows(IllegalArgumentException.class, () -> new EventContext(-0.1, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventContext(1.1, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventContext(0.5, true, Double.NaN));
    }

    @Test
    void eventContextOfDerivesConcentrationFromAmountAndCapacity() {
        assertEquals(0.5, EventContext.of(50, 100, true, 0.5).concentration());
        assertEquals(0, EventContext.of(50, 0, true, 0.5).concentration());
        assertEquals(1.0, EventContext.of(150, 100, true, 0.5).concentration());
    }
}
