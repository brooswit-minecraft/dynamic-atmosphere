package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereVolumeGeometryTest {
    @Test
    void constantFogColorAlphaComposesIndependentlyOfVolumeOrder() {
        double nearAlpha = AtmosphereVolumeGeometry.sliceAlpha(800, 0.5);
        double farAlpha = AtmosphereVolumeGeometry.sliceAlpha(125, 16);
        for (double fogChannel : new double[] {0, 0.2, 0.75, 1}) {
            for (double background : new double[] {0, 0.5, 1}) {
                double nearThenFar = fogChannel * farAlpha
                    + (fogChannel * nearAlpha + background * (1 - nearAlpha)) * (1 - farAlpha);
                double farThenNear = fogChannel * nearAlpha
                    + (fogChannel * farAlpha + background * (1 - farAlpha)) * (1 - nearAlpha);
                assertEquals(nearThenFar, farThenNear, 1e-12);
            }
        }
    }

    @Test
    void everyLodUsesItsOwnBoundsAndDensityIntegratedOverWorldThickness() {
        for (int cellX : new int[] {1, 10, 20}) {
            var tree = new AtmosphereLodHierarchy();
            tree.put(new AtmosphereClientCache.Cell(cellX, 0, 0), 1000, 1000, 0, 0);
            var volume = tree.select(0, 0, 0, 4, 0).volumes().getFirst();
            var camera = new AtmosphereVolumeGeometry.Point(volume.blockX() + volume.size() / 2.0,
                volume.size() / 2.0, -2);
            var slices = AtmosphereVolumeGeometry.lodSlices(volume, 500, camera,
                new AtmosphereVolumeGeometry.Point(0, 0, 1));
            assertFalse(slices.isEmpty());
            assertEquals(volume.size(), slices.size(), "All LODs retain one-block slice spacing for Vapor");
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

    /**
     * MINECRAFT-432: pins {@code coarseSlices}' pre-existing (if asymmetric) alpha formula --
     * {@code baseCellSize} is {@link AtmosphereVolumeGeometry#CELL_SIZE} (4), NOT the 16-block
     * box size/spacing this method otherwise uses for its geometry. A refactor regression that
     * passed 16 as baseCellSize instead (caught in review) would quarter the computed optical
     * depth below for identical amount/thickness inputs, since {@code sliceAlpha}'s exponent is
     * proportional to {@code thickness / baseCellSize}.
     */
    @Test
    void coarseGeometryAlphaUsesCellSizeNotTheSixteenBlockBoxSizeAsBaseCellSize() {
        // Camera placed so the box's near/far depths (16, 32) land exactly on 16-block grid
        // planes: one full-thickness (16) slice, with no partial end-slab fraction to account for.
        var camera = new AtmosphereVolumeGeometry.Point(8, 8, -16);
        var slices = AtmosphereVolumeGeometry.coarseSlices(0, 0, 0, 500, camera,
            new AtmosphereVolumeGeometry.Point(0, 0, 1));
        assertEquals(1, slices.size(),
            "a camera-aligned 16-block box with 16-block spacing must yield exactly one full-thickness slice");
        assertEquals(AtmosphereVolumeGeometry.sliceAlpha(500, 16, AtmosphereVolumeGeometry.CELL_SIZE),
            slices.getFirst().alpha(), 1.0e-6f,
            "coarse alpha must integrate against CELL_SIZE, not the 16-block box size, as baseCellSize");
    }
    private static final AtmosphereClientCache.Cell CELL = new AtmosphereClientCache.Cell(0, 0, 0);
    private static final AtmosphereVolumeGeometry.Point FORWARD = new AtmosphereVolumeGeometry.Point(0, 0, 1);

    @Test
    void outsideViewHasInteriorSlicesRatherThanCubeShell() {
        var slices = AtmosphereVolumeGeometry.slices(CELL, 1000,
            new AtmosphereVolumeGeometry.Point(2, 2, -2), FORWARD);
        assertEquals(4, AtmosphereVolumeGeometry.CELL_SIZE);
        assertEquals(1.0, AtmosphereVolumeGeometry.SLICE_SPACING);
        assertEquals(4, slices.size());
        for (var slice : slices) {
            assertEquals(4, slice.vertices().size());
            assertTrue(slice.depth() > 2 && slice.depth() < 6);
            for (var point : slice.vertices()) {
                assertTrue(Math.abs(point.x()) <= 2 && Math.abs(point.y()) <= 2);
                assertTrue(point.z() > 2 && point.z() < 6);
            }
            assertTrue(slice.alpha() > 0 && slice.alpha() < 0.2);
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

    @Test
    void fadeIsFullBelowStartZeroAtAndBeyondMaxAndStrictlyDecreasingBetween() {
        double fadeStart = 75, maxDistance = 100;
        assertEquals(1, AtmosphereVolumeGeometry.fadeMultiplier(0, fadeStart, maxDistance));
        assertEquals(1, AtmosphereVolumeGeometry.fadeMultiplier(fadeStart, fadeStart, maxDistance));
        assertEquals(0, AtmosphereVolumeGeometry.fadeMultiplier(maxDistance, fadeStart, maxDistance));
        assertEquals(0, AtmosphereVolumeGeometry.fadeMultiplier(maxDistance + 50, fadeStart, maxDistance));
        assertEquals(0.5, AtmosphereVolumeGeometry.fadeMultiplier(87.5, fadeStart, maxDistance), 1e-9);
        double previous = 1;
        for (double d = fadeStart; d <= maxDistance; d += 1) {
            double fade = AtmosphereVolumeGeometry.fadeMultiplier(d, fadeStart, maxDistance);
            assertTrue(fade <= previous, "fade must be monotone non-increasing with distance");
            previous = fade;
        }
    }

    @Test
    void fadeFallsBackToAHardCutoffWhenTheBandIsDegenerate() {
        assertEquals(1, AtmosphereVolumeGeometry.fadeMultiplier(50, 100, 100));
        assertEquals(0, AtmosphereVolumeGeometry.fadeMultiplier(100, 100, 100));
        assertEquals(0, AtmosphereVolumeGeometry.fadeMultiplier(150, 100, 100));
    }

    // --- MINECRAFT-432: a single per-frame basis passed into geometry generation must produce
    // --- exactly the geometry the old per-call "normalize look, rebuild right/up" path did.

    @Test
    void precomputedBasisProducesIdenticalGeometryToRecomputingItPerCall() {
        var tree = new AtmosphereLodHierarchy();
        tree.put(new AtmosphereClientCache.Cell(3, 0, -2), 1000, 1000, 0, 0);
        var volume = tree.select(0, 0, 0, 4, 0).volumes().getFirst();
        var camera = new AtmosphereVolumeGeometry.Point(2, 2, -2);
        for (var look : List.of(new AtmosphereVolumeGeometry.Point(0, 0, 1),
            new AtmosphereVolumeGeometry.Point(1, 1, 1), new AtmosphereVolumeGeometry.Point(0, 1, 0),
            new AtmosphereVolumeGeometry.Point(0, -1, 0))) {
            var viaLook = AtmosphereVolumeGeometry.lodSlices(volume, 500, camera, look, 1);
            var basis = AtmosphereVolumeGeometry.Basis.of(look);
            var viaBasis = AtmosphereVolumeGeometry.lodSlices(volume, 500, camera, basis, 1,
                io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().slicesPerBaseCell(),
                raw -> raw);
            assertEquals(viaLook, viaBasis,
                "a precomputed Basis must generate byte-for-byte the same slices as the look-vector path");
        }
    }

    // --- MINECRAFT-432: folding the final opacity/fade adjustment into slice generation itself
    // --- (an AlphaAdjust hook) must leave every slice's alpha exactly as a post-hoc multiply
    // --- over the unadjusted slices would have, while depth/vertices stay untouched.

    @Test
    void alphaAdjustHookMatchesApplyingTheSameAdjustmentAfterGeneration() {
        var tree = new AtmosphereLodHierarchy();
        tree.put(new AtmosphereClientCache.Cell(5, 0, 5), 1000, 1000, 0, 0);
        var volume = tree.select(0, 0, 0, 4, 0).volumes().getFirst();
        var camera = new AtmosphereVolumeGeometry.Point(2, 2, -2);
        var basis = AtmosphereVolumeGeometry.Basis.of(new AtmosphereVolumeGeometry.Point(0, 0, 1));
        int slicesPerBaseCell =
            io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().slicesPerBaseCell();
        var unadjusted = AtmosphereVolumeGeometry.lodSlices(volume, 500, camera, basis, 1, slicesPerBaseCell, raw -> raw);
        assertFalse(unadjusted.isEmpty());
        float fade = 0.4f;
        var foldedIn = AtmosphereVolumeGeometry.lodSlices(volume, 500, camera, basis, 1, slicesPerBaseCell,
            raw -> raw * fade);
        assertEquals(unadjusted.size(), foldedIn.size());
        for (int i = 0; i < unadjusted.size(); i++) {
            var before = unadjusted.get(i);
            var after = foldedIn.get(i);
            assertEquals(before.depth(), after.depth());
            assertEquals(before.vertices(), after.vertices());
            assertEquals(before.alpha() * fade, after.alpha(), 1e-6f);
        }
    }
}
