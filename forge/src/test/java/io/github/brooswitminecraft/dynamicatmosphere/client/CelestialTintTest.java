package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MINECRAFT-399: "tint atmospheric materials toward visible sun and moon by viewing angle".
 * Exercises the pure angular/blend math {@code AtmosphereVolumeRenderer} applies once per
 * frame to every material's colour -- direction model, toward/oblique/away weighting, visibility
 * gating, and the bounded-combination guarantee that keeps every channel in {@code [0, 1]} and
 * never lets a material's own colour vanish entirely.
 */
class CelestialTintTest {
    private static final CelestialTint.Vector UP = new CelestialTint.Vector(0, 1, 0);
    private static final CelestialTint.Vector DOWN = new CelestialTint.Vector(0, -1, 0);
    private static final CelestialTint.Vector NORTH = new CelestialTint.Vector(0, 0, 1);
    private static final float[] BASE = {0.5f, 0.3f, 0.2f};
    private static final float[] SUN = {1f, 0.8f, 0.5f};
    private static final float[] MOON = {0.6f, 0.75f, 0.9f};

    @Test
    void sunAndMoonDirectionsAreAntipodalAtEveryAngle() {
        for (double angle : new double[] {0, 0.3, 1.1, Math.PI, 4.2, 2 * Math.PI - 0.01}) {
            var sun = CelestialTint.sunDirection(angle);
            var moon = CelestialTint.moonDirection(angle);
            assertEquals(-sun.x(), moon.x(), 1e-9);
            assertEquals(-sun.y(), moon.y(), 1e-9);
            assertEquals(-sun.z(), moon.z(), 1e-9);
        }
    }

    @Test
    void sunAngleZeroIsOverheadAndHalfTurnLaterIsBelowTheHorizon() {
        // Pure geometry only -- (-sin(angle), cos(angle), 0): angle 0 gives straight up,
        // angle pi (half the cycle later) gives straight down. Whichever real dayTime values
        // vanilla's own getSunAngle maps to those two radians is outside this pure-math class.
        var overhead = CelestialTint.sunDirection(0);
        assertEquals(0, overhead.x(), 1e-9);
        assertEquals(1, overhead.y(), 1e-9);
        var opposite = CelestialTint.sunDirection(Math.PI);
        assertEquals(0, opposite.x(), 1e-9);
        assertEquals(-1, opposite.y(), 1e-9, "half a cycle later the sun direction is below the horizon");
    }

    @Test
    void lookingDirectlyAtTheBodyIsMaximalWeight() {
        double w = CelestialTint.weight(UP, UP, 1, 0.35);
        assertEquals(0.35, w, 1e-9);
    }

    @Test
    void lookingDirectlyAwayIsZeroWeight() {
        assertEquals(0, CelestialTint.weight(DOWN, UP, 1, 0.35));
    }

    @Test
    void obliqueViewingIsStrictlyBetweenZeroAndMax() {
        var oblique = new CelestialTint.Vector(0, Math.sqrt(0.5), Math.sqrt(0.5));
        double w = CelestialTint.weight(oblique, UP, 1, 0.35);
        assertTrue(w > 0 && w < 0.35, "expected a strictly intermediate weight, got " + w);
    }

    @Test
    void weightIsMonotonicFromAwayThroughObliqueToToward() {
        double away = CelestialTint.weight(DOWN, UP, 1, 0.35);
        double perpendicular = CelestialTint.weight(NORTH, UP, 1, 0.35);
        double oblique = CelestialTint.weight(new CelestialTint.Vector(0, 0.5, Math.sqrt(0.75)), UP, 1, 0.35);
        double toward = CelestialTint.weight(UP, UP, 1, 0.35);
        assertTrue(away <= perpendicular, away + " should be <= " + perpendicular);
        assertTrue(perpendicular <= oblique, perpendicular + " should be <= " + oblique);
        assertTrue(oblique <= toward, oblique + " should be <= " + toward);
    }

    @Test
    void zeroVisibilityCollapsesWeightRegardlessOfAngle() {
        assertEquals(0, CelestialTint.weight(UP, UP, 0, 0.35));
    }

    @Test
    void nonFiniteInputsFoldToZeroWeight() {
        assertEquals(0, CelestialTint.weight(new CelestialTint.Vector(Double.NaN, 0, 0), UP, 1, 0.35));
        assertEquals(0, CelestialTint.weight(UP, UP, Double.NaN, 0.35));
        assertEquals(0, CelestialTint.weight(UP, UP, 1, Double.NaN));
    }

    @Test
    void zeroWeightsLeaveTheBaseColourUnchanged() {
        float[] result = CelestialTint.blend(BASE, SUN, 0, MOON, 0);
        assertArrayEquals(BASE, result, 1e-9f);
    }

    @Test
    void fullSunWeightGoesEntirelyToSunColour() {
        float[] result = CelestialTint.blend(BASE, SUN, 1, MOON, 0);
        assertArrayEquals(SUN, result, 1e-6f);
    }

    @Test
    void fullMoonWeightGoesEntirelyToMoonColour() {
        float[] result = CelestialTint.blend(BASE, SUN, 0, MOON, 1);
        assertArrayEquals(MOON, result, 1e-6f);
    }

    @Test
    void overflowingCombinedWeightIsNormalizedNotClampedPerChannel() {
        // sunWeight + moonWeight == 1.6 must scale both down proportionally (to 0.625/0.375 of
        // their original ratio) rather than saturating each channel independently, or the
        // result would silently stop being a convex combination of the three colours.
        float[] normalized = CelestialTint.blend(BASE, SUN, 0.8, MOON, 0.8);
        float[] expected = CelestialTint.blend(BASE, SUN, 0.5, MOON, 0.5);
        assertArrayEquals(expected, normalized, 1e-6f);
    }

    @Test
    void resultIsAlwaysWithinValidColourRange() {
        for (double sunWeight : new double[] {0, 0.2, 0.5, 0.9, 1, 1.5}) {
            for (double moonWeight : new double[] {0, 0.3, 0.7, 1, 2}) {
                float[] result = CelestialTint.blend(BASE, SUN, sunWeight, MOON, moonWeight);
                for (float channel : result) {
                    assertTrue(channel >= 0 && channel <= 1, "channel out of [0,1]: " + channel);
                }
            }
        }
    }

    @Test
    void horizonWeightIsZeroBelowAndOneAboveTheSoftenedBand() {
        assertEquals(0, CelestialTint.horizonWeight(-1, 0.06));
        assertEquals(0, CelestialTint.horizonWeight(-0.06, 0.06), 1e-9);
        assertEquals(1, CelestialTint.horizonWeight(0.06, 0.06), 1e-9);
        assertEquals(1, CelestialTint.horizonWeight(1, 0.06));
    }

    @Test
    void horizonWeightIsStrictlyBetweenZeroAndOneInsideTheBand() {
        double w = CelestialTint.horizonWeight(0, 0.06);
        assertTrue(w > 0 && w < 1, "expected a strictly intermediate horizon weight, got " + w);
    }

    @Test
    void horizonWeightFallsBackToABareStepOnInvalidHalfWidth() {
        assertEquals(1, CelestialTint.horizonWeight(0.5, 0));
        assertEquals(0, CelestialTint.horizonWeight(-0.5, -1));
    }

    @Test
    void jitterRingCentreRayIsExactlyTheRequestedDirection() {
        var rays = CelestialTint.jitterRing(UP, 0.08, 4);
        assertEquals(UP, rays[0]);
    }

    @Test
    void jitterRingProducesUnitVectorsCloseToTheCentreDirection() {
        var direction = new CelestialTint.Vector(0, Math.sqrt(0.5), Math.sqrt(0.5));
        var rays = CelestialTint.jitterRing(direction, 0.08, 4);
        for (var ray : rays) {
            double length = Math.sqrt(ray.dot(ray));
            assertEquals(1, length, 1e-9, "every ray must be a unit vector");
            assertTrue(ray.dot(direction) > 0.99, "jittered ray strayed too far from the centre direction");
        }
    }

    @Test
    void jitterRingWorksForAStraightOverheadDirection() {
        // The basis construction must fall back to a different reference axis when direction is
        // itself nearly parallel to the default +Y reference, or the cross product degenerates.
        var rays = CelestialTint.jitterRing(UP, 0.08, 4);
        for (var ray : rays) {
            assertTrue(Double.isFinite(ray.x()) && Double.isFinite(ray.y()) && Double.isFinite(ray.z()),
                "degenerate basis produced a non-finite ray: " + ray);
        }
    }
}
