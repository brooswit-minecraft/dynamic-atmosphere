package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereVolumeGeometryTest {
    private static final AtmosphereClientCache.Cell CELL = new AtmosphereClientCache.Cell(0, 0, 0);
    private static final AtmosphereVolumeGeometry.Point FORWARD = new AtmosphereVolumeGeometry.Point(0, 0, 1);

    @Test
    void outsideViewHasInteriorSlicesRatherThanCubeShell() {
        var slices = AtmosphereVolumeGeometry.slices(CELL, 1000,
            new AtmosphereVolumeGeometry.Point(8, 8, -8), FORWARD);
        assertEquals(8, slices.size());
        for (var slice : slices) {
            assertEquals(4, slice.vertices().size());
            assertTrue(slice.depth() > 8 && slice.depth() < 24);
            assertTrue(slice.alpha() > 0 && slice.alpha() < 0.1);
        }
        double transmission = 1;
        for (var slice : slices) transmission *= 1 - slice.alpha();
        assertEquals(Math.exp(-0.6), transmission, 1.0e-6);
    }

    @Test
    void insideViewKeepsSlicesAheadAndOmitsEverythingBehind() {
        var slices = AtmosphereVolumeGeometry.slices(CELL, 1000,
            new AtmosphereVolumeGeometry.Point(8, 8, 8), FORWARD);
        assertFalse(slices.isEmpty());
        assertTrue(slices.stream().allMatch(slice -> slice.depth() > 0 && slice.depth() < 8));
        assertTrue(AtmosphereVolumeGeometry.slices(CELL, 1000,
            new AtmosphereVolumeGeometry.Point(8, 8, 24), FORWARD).isEmpty());
        assertTrue(AtmosphereVolumeGeometry.slices(CELL, 0,
            new AtmosphereVolumeGeometry.Point(8, 8, -8), FORWARD).isEmpty());
    }

    @Test
    void negativeAndFarCoordinatesKeepIdenticalCameraRelativeGeometry() {
        var expected = AtmosphereVolumeGeometry.slices(CELL, 500,
            new AtmosphereVolumeGeometry.Point(8, 8, -8), FORWARD);
        var negative = AtmosphereVolumeGeometry.slices(new AtmosphereClientCache.Cell(-1, -2, -3), 500,
            new AtmosphereVolumeGeometry.Point(-8, -24, -56), FORWARD);
        var far = AtmosphereVolumeGeometry.slices(new AtmosphereClientCache.Cell(1800000, 0, 1800000), 500,
            new AtmosphereVolumeGeometry.Point(28800008, 8, 28799992), FORWARD);
        assertEquals(expected, negative);
        assertEquals(expected, far);
    }

    @Test
    void obliqueAndVerticalSlicesStayInsideCellAndHaveBoundedPolygonCounts() {
        var camera = new AtmosphereVolumeGeometry.Point(8, 8, 8);
        for (var look : List.of(new AtmosphereVolumeGeometry.Point(1, 1, 1),
            new AtmosphereVolumeGeometry.Point(0, 1, 0), new AtmosphereVolumeGeometry.Point(0, -1, 0))) {
            var slices = AtmosphereVolumeGeometry.slices(CELL, 1000, camera, look);
            assertFalse(slices.isEmpty());
            assertTrue(slices.size() <= 15);
            for (var slice : slices) {
                assertTrue(slice.vertices().size() >= 3 && slice.vertices().size() <= 6);
                for (var p : slice.vertices()) {
                    assertTrue(Math.abs(p.x()) <= 8.000001 && Math.abs(p.y()) <= 8.000001 && Math.abs(p.z()) <= 8.000001);
                    assertEquals(slice.depth(), p.dot(look.normalized()), 1.0e-6);
                }
            }
        }
    }

    @Test
    void opacityIsMonotonicAndClamped() {
        assertEquals(0, AtmosphereVolumeGeometry.sliceAlpha(-1, 2));
        assertTrue(AtmosphereVolumeGeometry.sliceAlpha(100, 2) < AtmosphereVolumeGeometry.sliceAlpha(500, 2));
        assertEquals(AtmosphereVolumeGeometry.sliceAlpha(1000, 2), AtmosphereVolumeGeometry.sliceAlpha(9000, 2));
    }
}
