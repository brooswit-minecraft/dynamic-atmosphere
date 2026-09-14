package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeClientTest {
    private static final UUID WORLD = new UUID(1, 2);
    private static final String DIMENSION = "minecraft:overworld";

    private static AtmosphereClientCache.Update update(int x, int amount) {
        return new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(x, 0, 0), amount, 1000);
    }

    @Test
    void scopedSnapshotsAreAtomicAndUseEightBlockChunkCoordinates() {
        var smoke = new SmokeClientSession();
        smoke.world(DIMENSION);
        smoke.receive(WORLD, DIMENSION, true, false, List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 100)));
        assertEquals(0, smoke.cache().size());
        smoke.receive(WORLD, DIMENSION, false, true, List.of(new AtmosphereClientCache.Chunk(1, 0)), List.of(update(2, 200)));
        assertEquals(2, smoke.cache().size());
        smoke.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(1, 0)), List.of());
        assertEquals(100, smoke.cache().storedAmount(update(0, 0).cell()));
        assertEquals(0, smoke.cache().storedAmount(update(2, 0).cell()));
    }

    @Test
    void negativeCoordinatesAndDeltaRemovalsStayIndependentFromVapor() {
        var smoke = new SmokeClientSession();
        var vapor = new AtmosphereClientSession();
        smoke.world(DIMENSION);
        vapor.world(DIMENSION);
        vapor.receive(DIMENSION, true, List.of(update(-1, 99)));
        smoke.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(-1, 0)), List.of(update(-1, 500)));
        smoke.receive(WORLD, DIMENSION, false, false, List.of(), List.of(update(-1, 0)));
        for (int i = 0; i < 10; i++) smoke.cache().advance();
        assertEquals(0, smoke.cache().size());
        assertEquals(99, vapor.cache().storedAmount(update(-1, 0).cell()));
    }

    @Test
    void beforeWorldSnapshotsPromoteButWrongWorldDeltasAndDimensionsDoNot() {
        var smoke = new SmokeClientSession();
        smoke.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 100)));
        smoke.world(DIMENSION);
        assertEquals(100, smoke.cache().storedAmount(update(0, 0).cell()));
        smoke.receive(new UUID(3, 4), DIMENSION, false, false, List.of(), List.of(update(0, 900)));
        smoke.receive(WORLD, "minecraft:the_nether", true, true, List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 900)));
        assertEquals(100, smoke.cache().storedAmount(update(0, 0).cell()));
        smoke.receive(new UUID(3, 4), DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 200)));
        assertEquals(200, smoke.cache().storedAmount(update(0, 0).cell()));
        smoke.clear();
        smoke.world(DIMENSION);
        smoke.receive(WORLD, DIMENSION, false, false, List.of(), List.of(update(0, 900)));
        assertEquals(0, smoke.cache().size());
    }

    @Test
    void dimensionChangeDropsSmokeAndKeepsItsLayoutOnNextSnapshot() {
        var smoke = new SmokeClientSession();
        smoke.world(DIMENSION);
        smoke.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 1000)));
        smoke.world("minecraft:the_nether");
        assertEquals(0, smoke.cache().size());
        smoke.receive(WORLD, "minecraft:the_nether", true, true,
            List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 1000)));
        var selection = smoke.cache().lodSelection(0, 0, 0, 8);
        assertEquals(8, selection.volumes().getFirst().size());
    }

    @Test
    void smokeLodAveragesEmptyChildrenAndUsesEightSixteenThirtyTwoBands() {
        int[] positions = {0, 10, 20};
        int[] sizes = {8, 16, 32};
        float[] expected = {1000, 125, 15.625f};
        for (int i = 0; i < positions.length; i++) {
            var lod = new AtmosphereLodHierarchy(8, 2, 2);
            lod.put(new AtmosphereClientCache.Cell(positions[i], 0, 0), 1000, 1000, 0, 0);
            var selection = lod.select(0, 0, 0, 8, 0);
            assertEquals(1, selection.volumes().size());
            var volume = selection.volumes().getFirst();
            assertEquals(sizes[i], volume.size());
            assertEquals(expected[i], volume.amount(10));
        }
        var far = new AtmosphereLodHierarchy(8, 2, 2);
        far.put(new AtmosphereClientCache.Cell(1000, 0, 0), 1000, 1000, 0, 0);
        assertTrue(far.select(0, 0, 0, 8, 0).volumes().isEmpty());
    }

    @Test
    void sixteenBlockFallbackDoesNotOverlapLoadedSmokeDetail() {
        var lod = new AtmosphereLodHierarchy(8, 2, 2);
        lod.put(new AtmosphereClientCache.Cell(-1, 0, 0), 1000, 1000, 0, 0);
        var selection = lod.select(-1, 0, 0, 8, 0);
        var detail = selection.volumes().getFirst();
        var fallback = selection.unloadedFallbacks().getFirst();
        assertEquals(-8, detail.blockX());
        assertEquals(16, fallback.size());
        assertTrue(AtmosphereLodHierarchy.visibleWhenLoaded(detail, false, true));
        assertFalse(AtmosphereLodHierarchy.visibleWhenLoaded(fallback, true, true));
        assertFalse(AtmosphereLodHierarchy.visibleWhenLoaded(detail, false, false));
        assertTrue(AtmosphereLodHierarchy.visibleWhenLoaded(fallback, true, false));
    }

    @Test
    void bothMaterialsUseInclusiveBandEdgesAndTwoViewCutoff() {
        double[] distances = {63.999, 64, 64.001, 127.999, 128, 128.001, 256};
        int[] multipliers = {1, 1, 2, 2, 2, 4, 4};
        for (int size : new int[] {4, 8}) {
            for (int i = 0; i < distances.length; i++) {
                var lod = new AtmosphereLodHierarchy(size, 2, 2);
                lod.put(new AtmosphereClientCache.Cell(0, 0, 0), 1000, 1000, 0, 0);
                var selection = lod.select(-distances[i], 0, 0, 8, 0);
                var volume = selection.volumes().getFirst();
                assertEquals(size * multipliers[i], volume.size());
                assertTrue(AtmosphereLodHierarchy.withinReach(volume, -distances[i], 0, 0, 8));
                assertFalse(AtmosphereLodHierarchy.withinReach(volume, -256.001, 0, 0, 8));
            }
            var lod = new AtmosphereLodHierarchy(size, 2, 2);
            lod.put(new AtmosphereClientCache.Cell(0, 0, 0), 1000, 1000, 0, 0);
            var fallback = lod.select(0, 0, 0, 8, 0).unloadedFallbacks().getFirst();
            assertTrue(AtmosphereLodHierarchy.withinReach(fallback, -256, 0, 0, 8));
            assertFalse(AtmosphereLodHierarchy.withinReach(fallback, -256.001, 0, 0, 8));
        }
    }

    @Test
    void crossingFallbackPolygonsAreClippedNotDrawnBeyondTwoView() {
        var slice = new AtmosphereVolumeGeometry.Slice(250, 0.2f, List.of(
            new AtmosphereVolumeGeometry.Point(-100, -100, 250),
            new AtmosphereVolumeGeometry.Point(100, -100, 250),
            new AtmosphereVolumeGeometry.Point(100, 100, 250),
            new AtmosphereVolumeGeometry.Point(-100, 100, 250)));
        var forward = new AtmosphereVolumeGeometry.Point(0, 0, 1);
        var clipped = AtmosphereVolumeGeometry.clipToReach(List.of(slice), forward, 256);
        assertEquals(1, clipped.size());
        assertEquals(0.2f, clipped.getFirst().alpha());
        for (var point : clipped.getFirst().vertices()) assertTrue(point.dot(point) <= 256 * 256 + 1e-8);
        assertTrue(AtmosphereVolumeGeometry.clipToReach(List.of(slice), forward, 249).isEmpty());
        assertSame(slice, AtmosphereVolumeGeometry.clipToReach(List.of(slice), forward, 1000).getFirst());
    }

    @Test
    void shorterVaporRenderReachDoesNotDeleteCachedHistory() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension(DIMENSION);
        cache.restore(List.of(update(200, 1000)));
        assertTrue(cache.lodSelection(0, 0, 0, 8).volumes().isEmpty());
        assertEquals(List.of(update(200, 1000)), cache.exportUpdates());
    }

    @Test
    void sliceOpacityNormalizesToIndependentBaseSize() {
        assertEquals(AtmosphereVolumeGeometry.sliceAlpha(1000, 4),
            AtmosphereVolumeGeometry.sliceAlpha(1000, 8, 8));
    }

    @Test
    void mixedSlicesInterleaveByDepthRatherThanMaterialOrVolumeCenter() {
        var order = new AtmosphereSliceOrder();
        order.add(List.of(slice(1), slice(5), slice(9)), AtmosphereRenderMaterial.VAPOR);
        order.add(List.of(slice(3), slice(7)), AtmosphereRenderMaterial.SMOKE);
        for (int i = 0; i < 5; i++) {
            var next = order.next();
            assertEquals(9 - 2 * i, next.slice().depth());
            assertEquals(i % 2 == 1 ? AtmosphereRenderMaterial.SMOKE : AtmosphereRenderMaterial.VAPOR, next.material());
        }
        assertTrue(order.isEmpty());
    }

    private static AtmosphereVolumeGeometry.Slice slice(double depth) {
        return new AtmosphereVolumeGeometry.Slice(depth, 0.2f, List.of());
    }
}
