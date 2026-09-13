package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereVolumeGeometryTest {
    @Test
    void distanceBlendKeepsGrayNearAndReachesHorizonAtViewDistance() {
        assertEquals(0, AtmosphereVolumeGeometry.horizonBlend(0, 128));
        assertEquals(0, AtmosphereVolumeGeometry.horizonBlend(64, 128));
        assertEquals(0.5f, AtmosphereVolumeGeometry.horizonBlend(96, 128));
        assertEquals(1, AtmosphereVolumeGeometry.horizonBlend(128, 128));
        assertEquals(1, AtmosphereVolumeGeometry.horizonBlend(512, 128));
        float previous = 0;
        for (int distance = 64; distance <= 128; distance++) {
            float blend = AtmosphereVolumeGeometry.horizonBlend(distance, 128);
            assertTrue(blend >= previous);
            previous = blend;
        }
        for (float channel : new float[] {0, 0.15f, 0.7f, 1}) {
            assertEquals(0.25f, AtmosphereVolumeGeometry.colorChannel(0, channel, 0.25f));
            assertEquals(channel, AtmosphereVolumeGeometry.colorChannel(1, channel, 0.25f), 1e-7);
            assertEquals((channel + 0.25f) / 2, AtmosphereVolumeGeometry.colorChannel(0.5f, channel, 0.25f), 1e-7);
        }
        assertEquals(0, AtmosphereVolumeGeometry.colorChannel(1, -1, 0.25f));
        assertEquals(1, AtmosphereVolumeGeometry.colorChannel(1, 2, 0.25f));
    }

    @Test
    void blendIsContinuousAtBothBandEdgesAndIndependentOfLodLevel() {
        assertEquals(0, AtmosphereVolumeGeometry.horizonBlend(64.001, 128), 1e-7);
        assertEquals(1, AtmosphereVolumeGeometry.horizonBlend(127.999, 128), 1e-7);
        assertEquals(AtmosphereVolumeGeometry.horizonBlend(96, 128),
            AtmosphereVolumeGeometry.horizonBlend(192, 256));
    }

    @Test
    void nearColoredFallbackIsDrawnAfterGrayDetailBehindIt() {
        var tree = new AtmosphereLodHierarchy();
        tree.put(new AtmosphereClientCache.Cell(3, 0, 0), 500, 500, 0, 0);
        tree.put(new AtmosphereClientCache.Cell(6, 0, 0), 500, 500, 0, 0);
        var selected = tree.select(40, 2, 2, 4, 0);
        var gray = selected.volumes().stream().filter(v -> v.x == 3).findFirst().orElseThrow();
        var fallback = selected.unloadedFallbacks().stream().filter(v -> v.x == 4).findFirst().orElseThrow();
        assertTrue(AtmosphereVolumeGeometry.backToFront(gray, fallback,
            AtmosphereVolumeGeometry.cameraCell(new AtmosphereVolumeGeometry.Point(40, 2, 2))) < 0);
    }

    @Test
    void spatialPainterOrderIsBackToFrontAlongRaysAcrossMixedLodAndNegativeRoots() {
        var tree = new AtmosphereLodHierarchy();
        for (int x = -8; x < 16; x++) {
            for (int z = -8; z < 16; z++) tree.put(new AtmosphereClientCache.Cell(x, 0, z), 500, 500, 0, 0);
        }
        var selected = tree.select(2, 2, 2, 4, 0).volumes();
        assertTrue(selected.stream().anyMatch(v -> v.level == 0));
        assertTrue(selected.stream().anyMatch(v -> v.level > 0));
        for (var camera : List.of(new AtmosphereVolumeGeometry.Point(2, 2, 2),
            new AtmosphereVolumeGeometry.Point(-35, 2, 21), new AtmosphereVolumeGeometry.Point(70, 2, 70))) {
            var ordered = new java.util.ArrayList<>(selected);
            ordered.sort((a, b) -> AtmosphereVolumeGeometry.backToFront(a, b, AtmosphereVolumeGeometry.cameraCell(camera)));
            for (var ray : List.of(new AtmosphereVolumeGeometry.Point(1, 0, 0),
                new AtmosphereVolumeGeometry.Point(-1, 0, 0), new AtmosphereVolumeGeometry.Point(0, 0, 1),
                new AtmosphereVolumeGeometry.Point(0, 0, -1), new AtmosphereVolumeGeometry.Point(1, 0, 0.7),
                new AtmosphereVolumeGeometry.Point(-1, 0, -0.7))) {
                double previousNear = Double.POSITIVE_INFINITY;
                for (var volume : ordered) {
                    double[] interval = rayInterval(volume, camera, ray);
                    if (interval == null) continue;
                    assertTrue(interval[1] <= previousNear + 1e-7, "Far volume must precede nearer volume on the same ray");
                    previousNear = interval[0];
                }
            }
        }
    }

    private static double[] rayInterval(AtmosphereLodHierarchy.Volume volume, AtmosphereVolumeGeometry.Point camera,
                                         AtmosphereVolumeGeometry.Point ray) {
        double near = 0, far = Double.POSITIVE_INFINITY;
        double[] origin = {camera.x(), camera.y(), camera.z()};
        double[] direction = {ray.x(), ray.y(), ray.z()};
        double[] min = {volume.blockX(), volume.blockY(), volume.blockZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] == 0) {
                if (origin[axis] <= min[axis] || origin[axis] >= min[axis] + volume.size()) return null;
                continue;
            }
            double a = (min[axis] - origin[axis]) / direction[axis];
            double b = (min[axis] + volume.size() - origin[axis]) / direction[axis];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }
        return far > near ? new double[] {near, far} : null;
    }

    @Test
    void painterOrderCameraKeyUsesFloorAndDoesNotChangeWithinCell() {
        assertEquals(new AtmosphereClientCache.Cell(-1, 0, 0),
            AtmosphereVolumeGeometry.cameraCell(new AtmosphereVolumeGeometry.Point(-0.01, 1, 2)));
        assertEquals(AtmosphereVolumeGeometry.cameraCell(new AtmosphereVolumeGeometry.Point(0, 0, 0)),
            AtmosphereVolumeGeometry.cameraCell(new AtmosphereVolumeGeometry.Point(3.9, 3.9, 3.9)));
    }

    @Test
    void everyLodUsesItsOwnBoundsAndDensityIntegratedOverWorldThickness() {
        for (int cellX : new int[] {1, 10, 20, 40}) {
            var tree = new AtmosphereLodHierarchy();
            tree.put(new AtmosphereClientCache.Cell(cellX, 0, 0), 1000, 1000, 0, 0);
            var volume = tree.select(0, 0, 0, 4, 0).volumes().getFirst();
            var camera = new AtmosphereVolumeGeometry.Point(volume.blockX() + volume.size() / 2.0,
                volume.size() / 2.0, -2);
            var slices = AtmosphereVolumeGeometry.lodSlices(volume, 500, camera,
                new AtmosphereVolumeGeometry.Point(0, 0, 1));
            assertFalse(slices.isEmpty());
            assertTrue(slices.size() <= (volume.size() == 4 ? 9 : 3));
            double transmission = 1;
            for (var slice : slices) {
                transmission *= 1 - slice.alpha();
                for (var point : slice.vertices()) {
                    assertTrue(Math.abs(point.x()) <= volume.size() / 2.0 + 1e-6);
                    assertTrue(Math.abs(point.y()) <= volume.size() / 2.0 + 1e-6);
                    assertTrue(point.z() > 2 && point.z() < volume.size() + 2);
                }
            }
            assertEquals(Math.exp(-0.3 * volume.size() / 4), transmission, 1e-6);
            var inside = AtmosphereVolumeGeometry.lodSlices(volume, 500,
                new AtmosphereVolumeGeometry.Point(camera.x(), camera.y(), volume.size() / 2.0),
                new AtmosphereVolumeGeometry.Point(0, 0, 1));
            assertFalse(inside.isEmpty());
            assertTrue(inside.stream().allMatch(slice -> slice.depth() > 0 && slice.depth() < volume.size() / 2.0));
        }
    }

    @Test
    void coarseGeometryUsesSixteenBlockBoundsAndAtMostThreePlanes() {
        var camera = new AtmosphereVolumeGeometry.Point(-24, -8, -24);
        var slices = AtmosphereVolumeGeometry.coarseSlices(-1, -1, -1, 500, camera,
            new AtmosphereVolumeGeometry.Point(1, 0, 1));
        assertFalse(slices.isEmpty());
        assertTrue(slices.size() <= 3);
        for (var slice : slices) {
            for (var point : slice.vertices()) {
                assertTrue(point.x() >= 8 - 1e-6 && point.x() <= 24 + 1e-6);
                assertTrue(point.y() >= -8 - 1e-6 && point.y() <= 8 + 1e-6);
                assertTrue(point.z() >= 8 - 1e-6 && point.z() <= 24 + 1e-6);
            }
        }
    }
    private static final AtmosphereClientCache.Cell CELL = new AtmosphereClientCache.Cell(0, 0, 0);
    private static final AtmosphereVolumeGeometry.Point FORWARD = new AtmosphereVolumeGeometry.Point(0, 0, 1);

    @Test
    void outsideViewHasInteriorSlicesRatherThanCubeShell() {
        var slices = AtmosphereVolumeGeometry.slices(CELL, 1000,
            new AtmosphereVolumeGeometry.Point(2, 2, -2), FORWARD);
        assertEquals(4, AtmosphereVolumeGeometry.CELL_SIZE);
        assertEquals(0.5, AtmosphereVolumeGeometry.SLICE_SPACING);
        assertEquals(8, slices.size());
        for (var slice : slices) {
            assertEquals(4, slice.vertices().size());
            assertTrue(slice.depth() > 2 && slice.depth() < 6);
            for (var point : slice.vertices()) {
                assertTrue(Math.abs(point.x()) <= 2 && Math.abs(point.y()) <= 2);
                assertTrue(point.z() > 2 && point.z() < 6);
            }
            assertTrue(slice.alpha() > 0 && slice.alpha() < 0.1);
        }
        double transmission = 1;
        for (var slice : slices) transmission *= 1 - slice.alpha();
        assertEquals(Math.exp(-0.6), transmission, 1.0e-6);
    }

    @Test
    void insideViewKeepsSlicesAheadAndOmitsEverythingBehind() {
        var slices = AtmosphereVolumeGeometry.slices(CELL, 1000,
            new AtmosphereVolumeGeometry.Point(2, 2, 2), FORWARD);
        assertFalse(slices.isEmpty());
        assertTrue(slices.stream().allMatch(slice -> slice.depth() > 0 && slice.depth() < 2));
        assertTrue(AtmosphereVolumeGeometry.slices(CELL, 1000,
            new AtmosphereVolumeGeometry.Point(2, 2, 6), FORWARD).isEmpty());
        assertTrue(AtmosphereVolumeGeometry.slices(CELL, 0,
            new AtmosphereVolumeGeometry.Point(2, 2, -2), FORWARD).isEmpty());
    }

    @Test
    void negativeAndFarCoordinatesKeepIdenticalCameraRelativeGeometry() {
        var expected = AtmosphereVolumeGeometry.slices(CELL, 500,
            new AtmosphereVolumeGeometry.Point(2, 2, -2), FORWARD);
        var negative = AtmosphereVolumeGeometry.slices(new AtmosphereClientCache.Cell(-1, -2, -3), 500,
            new AtmosphereVolumeGeometry.Point(-2, -6, -14), FORWARD);
        var far = AtmosphereVolumeGeometry.slices(new AtmosphereClientCache.Cell(7200000, 0, 7200000), 500,
            new AtmosphereVolumeGeometry.Point(28800002, 2, 28799998), FORWARD);
        assertEquals(expected, negative);
        assertEquals(expected, far);
    }

    @Test
    void obliqueAndVerticalSlicesStayInsideCellAndHaveBoundedPolygonCounts() {
        var camera = new AtmosphereVolumeGeometry.Point(2, 2, 2);
        for (var look : List.of(new AtmosphereVolumeGeometry.Point(1, 1, 1),
            new AtmosphereVolumeGeometry.Point(0, 1, 0), new AtmosphereVolumeGeometry.Point(0, -1, 0))) {
            var slices = AtmosphereVolumeGeometry.slices(CELL, 1000, camera, look);
            assertFalse(slices.isEmpty());
            assertTrue(slices.size() <= 15);
            for (var slice : slices) {
                assertTrue(slice.vertices().size() >= 3 && slice.vertices().size() <= 6);
                for (var p : slice.vertices()) {
                    assertTrue(Math.abs(p.x()) <= 2.000001 && Math.abs(p.y()) <= 2.000001 && Math.abs(p.z()) <= 2.000001);
                    assertEquals(slice.depth(), p.dot(look.normalized()), 1.0e-6);
                }
            }
        }
    }

    @Test
    void opacityIsMonotonicAndClamped() {
        assertEquals(0, AtmosphereVolumeGeometry.sliceAlpha(-1, 0.5));
        assertTrue(AtmosphereVolumeGeometry.sliceAlpha(100, 0.5) < AtmosphereVolumeGeometry.sliceAlpha(500, 0.5));
        assertEquals(AtmosphereVolumeGeometry.sliceAlpha(1000, 0.5), AtmosphereVolumeGeometry.sliceAlpha(9000, 0.5));
        assertEquals(-Math.expm1(-0.6 / 8), AtmosphereVolumeGeometry.sliceAlpha(1000, 0.5), 1.0e-7);
        assertEquals(-Math.expm1(-0.6), AtmosphereVolumeGeometry.sliceAlpha(1000, 4), 1.0e-7);
    }
}
