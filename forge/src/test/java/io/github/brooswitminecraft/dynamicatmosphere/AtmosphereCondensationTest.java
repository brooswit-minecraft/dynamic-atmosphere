package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereCondensationTest {

    @Test
    void probabilityBeginsAboveHalfFullAndCapsAtTenPercent() {
        assertEquals(0.0, AtmosphereCondensation.probability(500, 1000));
        assertEquals(0.05, AtmosphereCondensation.probability(750, 1000), 1.0e-12);
        assertEquals(0.1, AtmosphereCondensation.probability(1000, 1000), 1.0e-12);
        assertEquals(0.1, AtmosphereCondensation.probability(2000, 1000), 1.0e-12);
    }

    @Test
    void cellsWithoutKnownAirCapacityCannotCondense() {
        assertEquals(0.0, AtmosphereCondensation.probability(1000, 0));
        assertEquals(0.0, AtmosphereCondensation.probability(1000, -1));
    }

    @Test
    void consumptionRoundsQuarterDownWithPositiveMinimum() {
        assertEquals(0, AtmosphereCondensation.consumedAmount(0));
        assertEquals(0, AtmosphereCondensation.consumedAmount(-1));
        assertEquals(1, AtmosphereCondensation.consumedAmount(1));
        assertEquals(1, AtmosphereCondensation.consumedAmount(7));
        assertEquals(2, AtmosphereCondensation.consumedAmount(8));
        assertEquals(2, AtmosphereCondensation.consumedAmount(11));
    }

    @Test
    void rollUsesHalfOpenProbabilityBoundary() {
        assertTrue(AtmosphereCondensation.shouldCondense(750, 1000, 0.0));
        assertTrue(AtmosphereCondensation.shouldCondense(750, 1000, Math.nextDown(0.05)));
        assertFalse(AtmosphereCondensation.shouldCondense(750, 1000, 0.05));
        assertFalse(AtmosphereCondensation.shouldCondense(500, 1000, 0.0));
        assertFalse(AtmosphereCondensation.shouldCondense(1000, 1000, Math.nextDown(1.0)));
    }

    @Test
    void rollMustBeInBetweenZeroInclusiveAndOneExclusive() {
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereCondensation.shouldCondense(1000, 1000, -Double.MIN_VALUE));
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereCondensation.shouldCondense(1000, 1000, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereCondensation.shouldCondense(1000, 1000, Double.NaN));
    }
}
