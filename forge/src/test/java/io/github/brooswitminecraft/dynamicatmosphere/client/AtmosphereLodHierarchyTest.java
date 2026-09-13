package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereLodHierarchyTest {
    private static AtmosphereClientCache.Cell cell(int x, int y, int z) {
        return new AtmosphereClientCache.Cell(x, y, z);
    }

    private static void seed(AtmosphereLodHierarchy tree, int x, int y, int z, float amount) {
        tree.put(cell(x, y, z), amount, amount, 0, 0);
    }

    @Test
    void halfViewAndDoublingBandsUseAlignedFourEightSixteenAndThirtyTwoBlocks() {
        var tree = new AtmosphereLodHierarchy();
        for (int x : new int[] {1, 10, 20, 40, 80}) seed(tree, x, 0, 0, 1000);
        var selected = tree.select(0, 0, 0, 4, 0).volumes();
        assertEquals(List.of(4, 8, 16, 32), selected.stream().map(AtmosphereLodHierarchy.Volume::size).sorted().toList());
        for (var volume : selected) {
            assertEquals(0, Math.floorMod(volume.x, 1 << volume.level));
            assertEquals(0, Math.floorMod(volume.y, 1 << volume.level));
            assertEquals(0, Math.floorMod(volume.z, 1 << volume.level));
        }
    }

    @Test
    void averagesAllEightChildrenAndAllEmptySpaceAtEveryLevel() {
        var tree = new AtmosphereLodHierarchy();
        seed(tree, 10, 0, 0, 1000);
        seed(tree, 20, 0, 0, 1000);
        seed(tree, 40, 0, 0, 1000);
        var selected = tree.select(0, 0, 0, 4, 0).volumes();
        for (var volume : selected) assertEquals(1000f / (1 << (3 * volume.level)), volume.amount(0));
        for (int x = 10; x < 12; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) seed(tree, x, y, z, 1000);
            }
        }
        assertEquals(1000, selected.stream().filter(v -> v.size() == 8).findFirst().orElseThrow().amount(0));
    }

    @Test
    void parentSignalsExactlyPreserveStaggeredInterpolationWithoutRebuildingSelection() {
        var tree = new AtmosphereLodHierarchy();
        tree.put(cell(10, 0, 0), 0, 1000, 0, 0);
        tree.put(cell(11, 0, 0), 0, 0, 0, 0);
        var first = tree.select(0, 0, 0, 4, 0);
        var volume = first.volumes().getFirst();
        assertEquals(62.5, volume.amount(5), 1e-5);
        tree.put(cell(11, 0, 0), 0, 800, 5, 5);
        assertSame(first, tree.select(0, 0, 0, 4, 5));
        assertEquals(118.75, volume.amount(7.5), 1e-5);
        assertEquals(175, volume.amount(10), 1e-5);
        assertEquals(225, volume.amount(15), 1e-5);
        assertEquals(0, tree.lastSelectionWork());
    }

    @Test
    void replacingAnInFlightSignalCancelsItsOldEndpoint() {
        var tree = new AtmosphereLodHierarchy();
        tree.put(cell(10, 0, 0), 0, 1000, 0, 0);
        var volume = tree.select(0, 0, 0, 4, 0).volumes().getFirst();
        tree.put(cell(10, 0, 0), 500, 200, 5, 5);
        assertEquals(425 / 8.0, volume.amount(7.5), 1e-5);
        assertEquals(350 / 8.0, volume.amount(10), 1e-5);
        assertEquals(200 / 8.0, volume.amount(15), 1e-5);
        tree.remove(cell(10, 0, 0), 16);
        assertEquals(0, volume.amount(16));
        assertTrue(tree.select(0, 0, 0, 4, 16).volumes().isEmpty());
    }

    @Test
    void samplingNextTickDoesNotExpireAnEndpointBeforeSameTickPacketReplacement() {
        var tree = new AtmosphereLodHierarchy();
        tree.put(cell(10, 0, 0), 0, 1000, 0, 0);
        var volume = tree.select(0, 0, 0, 4, 0).volumes().getFirst();
        assertEquals(1000 / 8.0, volume.amount(10), 1e-5);
        assertEquals(900 / 8.0, volume.amount(9), 1e-5);
        tree.put(cell(10, 0, 0), 900, 0, 9, 9);
        assertEquals(900 / 8.0, volume.amount(9), 1e-5);
        assertEquals(810 / 8.0, volume.amount(10), 1e-5);
        assertEquals(0, volume.amount(19));
    }

    @Test
    void negativeCoordinatesFloorAndVerticalDistanceAlsoSelectsLod() {
        var tree = new AtmosphereLodHierarchy();
        seed(tree, -41, -1, -1, 1000);
        seed(tree, 0, 40, 0, 1000);
        var volumes = tree.select(0, 0, 0, 4, 0).volumes();
        assertEquals(2, volumes.size());
        assertTrue(volumes.stream().allMatch(v -> v.size() == 32));
        var negative = volumes.stream().filter(v -> v.x < 0).findFirst().orElseThrow();
        assertEquals(-192, negative.blockX());
        assertEquals(-32, negative.blockY());
        assertEquals(-32, negative.blockZ());
    }

    @Test
    void boundaryFrontierAndUnloadedFallbacksNeverDoubleCover() {
        var tree = new AtmosphereLodHierarchy();
        for (int x = -20; x < 80; x++) {
            for (int y = -2; y < 2; y++) seed(tree, x, y, 0, 500);
        }
        var selected = tree.select(3, 1, 1, 4, 0);
        assertFalse(tree.selectionPending());
        for (boolean loaded : new boolean[] {true, false}) {
            var visible = new ArrayList<AtmosphereLodHierarchy.Volume>();
            selected.volumes().stream().filter(v -> AtmosphereLodHierarchy.visibleWhenLoaded(v, false, loaded)).forEach(visible::add);
            selected.unloadedFallbacks().stream().filter(v -> AtmosphereLodHierarchy.visibleWhenLoaded(v, true, loaded)).forEach(visible::add);
            assertFalse(visible.isEmpty());
            for (int i = 0; i < visible.size(); i++) {
                for (int j = i + 1; j < visible.size(); j++) assertFalse(overlaps(visible.get(i), visible.get(j)));
            }
        }
        for (int x = -20; x < 64; x++) {
            final double center = x * 4 + 2;
            assertEquals(1, selected.volumes().stream().filter(v -> center >= v.blockX() && center < v.blockX() + v.size()
                && 2 >= v.blockY() && 2 < v.blockY() + v.size() && 2 >= v.blockZ() && 2 < v.blockZ() + v.size()).count());
        }
    }

    @Test
    void teleportDiscardsPendingOldCameraBuildAndPublishesOnlyTheNewView() {
        var tree = new AtmosphereLodHierarchy();
        seedBusyRegion(tree);
        seed(tree, 25000, 0, 0, 1000);
        var initial = tree.select(0, 0, 0, 4, 0);
        assertTrue(tree.selectionPending());
        assertEquals(AtmosphereLodHierarchy.SELECTION_WORK_PER_TICK, tree.lastSelectionWork());
        var preview = tree.select(100000, 0, 0, 4, 0);
        assertNotSame(initial, preview);
        assertEquals(1, preview.volumes().size());
        assertEquals(100000, preview.volumes().getFirst().blockX());
        assertEquals(32, preview.volumes().getFirst().size());
        var completed = tree.select(100000, 0, 0, 4, 1);
        assertFalse(tree.selectionPending());
        assertEquals(1, completed.volumes().size());
        assertEquals(100000, completed.volumes().getFirst().blockX());
        assertEquals(4, completed.volumes().getFirst().size());
        assertTrue(tree.lastSelectionWork() <= AtmosphereLodHierarchy.SELECTION_WORK_PER_TICK);
        var busyPreview = tree.select(0, 0, 0, 4, 2);
        assertTrue(tree.selectionPending());
        assertTrue(busyPreview.volumes().stream().allMatch(v -> v.size() == 32));
        assertTrue(busyPreview.volumes().stream().anyMatch(v -> v.x == 0 && v.y == 0 && v.z == 0));
    }

    private static void seedBusyRegion(AtmosphereLodHierarchy tree) {
        for (int x = -8; x < 8; x++) {
            for (int y = -8; y < 8; y++) {
                for (int z = -8; z < 8; z++) seed(tree, x * 8, y * 8, z * 8, 1000);
            }
        }
    }

    @Test
    void largeOffscreenCacheDoesNotAddGlobalRootWalksToViewSelection() {
        var tree = new AtmosphereLodHierarchy();
        seed(tree, 1, 0, 0, 1000);
        for (int i = 10000; i < 30000; i++) {
            seed(tree, i * 8, 0, 0, 1000);
            seed(tree, 0, i * 8, 0, 1000);
            seed(tree, 0, 0, i * 8, 1000);
        }
        var selected = tree.select(0, 0, 0, 4, 0);
        assertFalse(tree.selectionPending());
        assertEquals(1, selected.volumes().size());
        assertTrue(tree.lastSelectionWork() < 32);
        var moved = tree.select(16, 0, 0, 4, 1);
        assertFalse(tree.selectionPending());
        assertEquals(1, moved.volumes().size());
        assertTrue(tree.lastSelectionWork() < 32);
    }

    @Test
    void cameraRegionMovementReusesSelectionButViewAndWorldChangesInvalidateIt() {
        var tree = new AtmosphereLodHierarchy();
        seed(tree, 10, 0, 0, 1000);
        var first = tree.select(0, 0, 0, 4, 0);
        assertSame(first, tree.select(12, 12, 12, 4, 1));
        assertEquals(0, tree.lastSelectionWork());
        assertNotSame(first, tree.select(1, 1, 1, 8, 2));
        tree.clear();
        assertTrue(tree.select(1, 1, 1, 8, 3).volumes().isEmpty());
    }

    @Test
    void cacheRestoreUsesFullnessIncludingMissingCellsAndFreshZerosFade() {
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        cache.restore(List.of(new AtmosphereClientCache.Update(cell(10, 0, 0), 100, 200),
            new AtmosphereClientCache.Update(cell(11, 0, 0), 1000, 100)));
        var volume = cache.lodSelection(0, 0, 0, 4).volumes().getFirst();
        assertEquals(187.5, volume.amount(cache.renderTick(0)), 1e-5);
        cache.apply("overworld", true, true, List.of(new AtmosphereClientCache.Chunk(2, 0)), List.of());
        assertEquals(187.5, volume.amount(cache.renderTick(0)), 1e-5);
        for (int tick = 0; tick < 5; tick++) cache.advance();
        assertEquals(93.75, volume.amount(cache.renderTick(0)), 1e-5);
        for (int tick = 0; tick < 5; tick++) cache.advance();
        assertTrue(cache.lodSelection(0, 0, 0, 4).volumes().isEmpty());
        assertTrue(cache.exportUpdates().isEmpty());
    }

    @Test
    void cacheEvictionAndDimensionChangesAlsoRemoveHierarchyMembership() {
        var cache = new AtmosphereClientCache(1);
        cache.changeDimension("overworld");
        cache.restore(List.of(new AtmosphereClientCache.Update(cell(10, 0, 0), 100, 200),
            new AtmosphereClientCache.Update(cell(40, 0, 0), 1000, 100)));
        cache.setDetailedView(key -> key.x() == 40);
        var selected = cache.lodSelection(0, 0, 0, 4);
        assertEquals(1, selected.volumes().size());
        assertEquals(160, selected.volumes().getFirst().blockX());
        assertEquals(List.of(new AtmosphereClientCache.Update(cell(40, 0, 0), 1000, 100)), cache.exportUpdates());
        cache.changeDimension("nether");
        assertTrue(cache.lodSelection(0, 0, 0, 4).volumes().isEmpty());
        assertTrue(cache.exportUpdates().isEmpty());
    }

    private static boolean overlaps(AtmosphereLodHierarchy.Volume a, AtmosphereLodHierarchy.Volume b) {
        return a.blockX() < b.blockX() + b.size() && b.blockX() < a.blockX() + a.size()
            && a.blockY() < b.blockY() + b.size() && b.blockY() < a.blockY() + a.size()
            && a.blockZ() < b.blockZ() + b.size() && b.blockZ() < a.blockZ() + a.size();
    }
}
