package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialClientTest {
    /**
     * Cross-checks all three per-material cell-size catalogs by NAME (the way
     * {@code AtmosphereClient} matches materials with {@code valueOf(name())}):
     * client {@code AtmosphereRenderMaterial}, server {@code AtmosphereMaterial},
     * and engine {@code AtmosphericMaterials}. Fails both on a size mismatch and
     * on a material present in one catalog with no counterpart in another.
     */
    @Test
    void allThreeCellSizeCatalogsAgreeByNameForEveryMaterial() {
        var engineByName = io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.ALL.stream()
            .collect(java.util.stream.Collectors.toMap(
                m -> m.id().toUpperCase(java.util.Locale.ROOT), m -> m.settings().cellSize()));
        var clientNames = java.util.Arrays.stream(AtmosphereRenderMaterial.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        assertEquals(engineByName.keySet(), clientNames,
            "engine and client catalogs must name the exact same materials");
        for (var material : AtmosphereRenderMaterial.values()) {
            assertEquals(engineByName.get(material.name()), material.cellSize,
                "engine/client cell size mismatch for " + material.name());
        }

        var serverMaterials = io.github.brooswitminecraft.dynamicatmosphere.AtmosphereMaterial.values();
        var serverNames = java.util.Arrays.stream(serverMaterials)
            .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        assertTrue(clientNames.containsAll(serverNames),
            "every server material must have a client counterpart");
        for (var material : serverMaterials) {
            assertEquals(material.cellSize(), AtmosphereRenderMaterial.valueOf(material.name()).cellSize,
                "server/client cell size mismatch for " + material.name());
        }
    }

    @Test
    void enderGasIsTenTimesItsPreviousOpticalDensity() {
        assertEquals(40, AtmosphereRenderMaterial.ENDER_GAS.opticalDensityMultiplier);
        assertEquals(4, AtmosphereRenderMaterial.ENDER_GAS.cellSize);
        assertEquals(4, AtmosphereRenderMaterial.SMOKE.cellSize);
        double oldDepth = -Math.log1p(-AtmosphereVolumeGeometry.sliceAlpha(100, 1, 1, 4));
        double newDepth = -Math.log1p(-AtmosphereVolumeGeometry.sliceAlpha(100, 1, 1, 40));
        assertEquals(oldDepth * 10, newDepth, 1e-5);
    }
    @Test
    void activeOpticalDensityMultipliersMatchDefinitionsWithoutScalingMaterial() {
        var definitions = List.of(
            io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.VAPOR,
            io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.SMOKE,
            io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.DUST,
            io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.ENDER_GAS,
            io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.VIOLENCE,
            io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.EXHAUST,
            io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.SLIME);
        var materials = AtmosphereRenderMaterial.values();
        for (int i = 0; i < materials.length; i++) {
            assertEquals(definitions.get(i).settings().opticalDensityMultiplier(), materials[i].opticalDensityMultiplier);
            var cache = materials[i].newCache();
            cache.changeDimension(DIMENSION);
            cache.restore(List.of(update(0, 500)));
            var volume = cache.lodSelection(0, 0, 0, 8).volumes().getFirst();
            assertEquals(500, volume.amount(0));
            assertEquals(500, cache.storedAmount(update(0, 0).cell()));
        }
    }

    @Test
    void fourTimesOpticalDensityIsIndependentOfSliceCountAndLodThickness() {
        for (int base : new int[] {1, 2, 4, 8}) {
            for (int lod : new int[] {1, 2, 4}) {
                double thickness = base * lod;
                for (int slices : new int[] {1, 4, 8, 16}) {
                    double normal = -Math.log1p(-AtmosphereVolumeGeometry.sliceAlpha(100, thickness / slices, base, 1)) * slices;
                    double strong = -Math.log1p(-AtmosphereVolumeGeometry.sliceAlpha(100, thickness / slices, base, 4)) * slices;
                    assertEquals(0.06 * lod, normal, 1e-6);
                    assertEquals(normal * 4, strong, 1e-6);
                }
            }
        }
    }

    @Test
    void geometryIntegratesMultiplierBeforeAlphaAtEverySmokeLod() {
        int[] positions = {0, 10, 20};
        for (int level = 0; level < positions.length; level++) {
            var lod = new AtmosphereLodHierarchy(8, 2, 2);
            lod.put(new AtmosphereClientCache.Cell(positions[level], 0, 0), 1000, 1000, 0, 0);
            var volume = lod.select(0, 0, 0, 8, 0).volumes().getFirst();
            assertEquals(level, volume.level);
            var camera = new AtmosphereVolumeGeometry.Point(volume.blockX() + volume.size() / 2.0,
                volume.size() / 2.0, -2);
            var look = new AtmosphereVolumeGeometry.Point(0, 0, 1);
            var normal = AtmosphereVolumeGeometry.lodSlices(volume, 100, camera, look, 1);
            var strong = AtmosphereVolumeGeometry.lodSlices(volume, 100, camera, look, 4);
            assertEquals(normal.size(), strong.size());
            double normalDepth = normal.stream().mapToDouble(s -> -Math.log1p(-s.alpha())).sum();
            double strongDepth = strong.stream().mapToDouble(s -> -Math.log1p(-s.alpha())).sum();
            assertEquals(0.06 * (1 << level), normalDepth, 1e-6);
            assertEquals(normalDepth * 4, strongDepth, 1e-6);
            // A single occupied base cell is averaged with empty children at coarse LODs.
            assertEquals(1000f / (1 << (3 * level)), volume.amount(0));
        }
    }

    private static final String DIMENSION = "minecraft:overworld";
    private static final UUID WORLD = new UUID(1, 2);

    private static AtmosphereClientCache.Update update(int x, int amount) {
        return new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(x, 0, 0), amount, 1000);
    }

    @Test
    void dustAndEnderHaveIndependentAtomicScopesAndOwnChunkCoordinates() {
        var dust = new MaterialClientSession(AtmosphereRenderMaterial.DUST);
        var ender = new MaterialClientSession(AtmosphereRenderMaterial.ENDER_GAS);
        dust.world(DIMENSION);
        ender.world(DIMENSION);
        var scope = List.of(new AtmosphereClientCache.Chunk(0, 0), new AtmosphereClientCache.Chunk(2, 0));
        dust.receive(WORLD, DIMENSION, true, true, scope, List.of(update(8, 200)));
        ender.receive(WORLD, DIMENSION, true, true, scope, List.of(update(8, 700)));
        // x=8 is chunk 2 for both four-block grids, but their scopes remain independent.
        dust.receive(WORLD, DIMENSION, true, false, List.of(new AtmosphereClientCache.Chunk(2, 0)), List.of());
        assertEquals(200, dust.cache().storedAmount(update(8, 0).cell()));
        dust.receive(WORLD, DIMENSION, false, true, List.of(), List.of());
        assertEquals(0, dust.cache().storedAmount(update(8, 0).cell()));
        assertEquals(700, ender.cache().storedAmount(update(8, 0).cell()));
        ender.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(2, 0)), List.of());
        assertEquals(0, ender.cache().storedAmount(update(8, 0).cell()));
    }

    @Test
    void negativeChunkScopesAndDeltaRemovalWorkForBothLayouts() {
        for (var material : List.of(AtmosphereRenderMaterial.DUST, AtmosphereRenderMaterial.ENDER_GAS)) {
            var session = new MaterialClientSession(material);
            session.world(DIMENSION);
            session.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(-1, 0)), List.of(update(-1, 400)));
            assertEquals(400, session.cache().storedAmount(update(-1, 0).cell()));
            session.receive(WORLD, DIMENSION, false, false, List.of(), List.of(update(-1, 0)));
            for (int i = 0; i < 10; i++) session.cache().advance();
            assertEquals(0, session.cache().size());
        }
    }

    @Test
    void worldDimensionAndDisconnectClearOnlyTheirOwnMaterialState() {
        for (var material : List.of(AtmosphereRenderMaterial.DUST, AtmosphereRenderMaterial.ENDER_GAS)) {
            var session = new MaterialClientSession(material);
            session.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 200)));
            session.world(DIMENSION);
            assertEquals(200, session.cache().storedAmount(update(0, 0).cell()));
            session.receive(new UUID(3, 4), DIMENSION, false, false, List.of(), List.of(update(0, 900)));
            assertEquals(200, session.cache().storedAmount(update(0, 0).cell()));
            session.receive(new UUID(3, 4), DIMENSION, true, true,
                List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 300)));
            assertEquals(300, session.cache().storedAmount(update(0, 0).cell()));
            session.world("minecraft:the_end");
            assertEquals(0, session.cache().size());
            session.clear();
            session.world(DIMENSION);
            session.receive(WORLD, DIMENSION, false, false, List.of(), List.of(update(0, 900)));
            assertEquals(0, session.cache().size());
        }
    }

    @Test
    void quarterViewIsInclusiveBaseOnlyWithoutCoarseOrUnloadedFallback() {
        for (var material : List.of(AtmosphereRenderMaterial.DUST, AtmosphereRenderMaterial.ENDER_GAS)) {
            for (double distance : new double[] {31.999, 32, 32.001}) {
                var cache = material.newCache();
                cache.changeDimension(DIMENSION);
                cache.restore(List.of(update(0, 1000)));
                var selection = cache.lodSelection(-distance, 0, 0, 8);
                assertEquals(1, selection.volumes().size());
                assertTrue(selection.unloadedFallbacks().isEmpty());
                var volume = selection.volumes().getFirst();
                assertEquals(material.cellSize, volume.size());
                assertEquals(0, volume.level);
                assertEquals(distance <= 32,
                    AtmosphereLodHierarchy.withinReach(volume, -distance, 0, 0, 8, material.reach));
                assertFalse(AtmosphereLodHierarchy.visibleWhenLoaded(volume, false, false));
                assertEquals(List.of(update(0, 1000)), cache.exportUpdates());
            }
        }
    }

    @Test
    void baseOnlySelectionRemainsWorkBoundedWithoutOversizedPreview() {
        for (var material : List.of(AtmosphereRenderMaterial.DUST, AtmosphereRenderMaterial.ENDER_GAS)) {
            var lod = new AtmosphereLodHierarchy(material.cellSize, 0, material.reach);
            for (int x = -10; x <= 10; x++) for (int y = -10; y <= 10; y++) for (int z = -10; z <= 10; z++) {
                lod.put(new AtmosphereClientCache.Cell(x, y, z), 1000, 1000, 0, 0);
            }
            var selection = lod.select(0, 0, 0, 8, 0);
            assertTrue(lod.lastSelectionWork() <= AtmosphereLodHierarchy.SELECTION_WORK_PER_TICK);
            assertTrue(selection.volumes().stream().allMatch(v -> v.size() == material.cellSize && v.level == 0));
            assertTrue(selection.unloadedFallbacks().isEmpty());
        }
    }

    @Test
    void quarterViewBoundaryGeometryIsClippedWithoutTouchingVaporReach() {
        var forward = new AtmosphereVolumeGeometry.Point(0, 0, 1);
        var crossing = new AtmosphereVolumeGeometry.Slice(31.5, 0.2f, List.of(
            new AtmosphereVolumeGeometry.Point(-8, -8, 31.5), new AtmosphereVolumeGeometry.Point(8, -8, 31.5),
            new AtmosphereVolumeGeometry.Point(8, 8, 31.5), new AtmosphereVolumeGeometry.Point(-8, 8, 31.5)));
        var clipped = AtmosphereVolumeGeometry.clipToReach(List.of(crossing), forward, 32);
        assertEquals(1, clipped.size());
        assertTrue(clipped.getFirst().vertices().stream().allMatch(p -> p.dot(p) <= 32 * 32 + 1e-8));
        assertEquals(2, AtmosphereRenderMaterial.VAPOR.reach);
        assertEquals(2, AtmosphereRenderMaterial.SMOKE.reach);
    }

    @Test
    void paletteAndSevenMaterialSliceOrderingAreIndependent() {
        float[] fog = {0.2f, 0.5f, 0.8f};
        assertEquals(fog[1], AtmosphereRenderMaterial.VAPOR.channel(1, fog));
        assertEquals(0, AtmosphereRenderMaterial.SMOKE.channel(0, fog));
        assertEquals(139 / 255f, AtmosphereRenderMaterial.DUST.channel(0, fog));
        assertEquals(69 / 255f, AtmosphereRenderMaterial.DUST.channel(1, fog));
        assertEquals(19 / 255f, AtmosphereRenderMaterial.DUST.channel(2, fog));
        assertEquals(128 / 255f, AtmosphereRenderMaterial.ENDER_GAS.channel(0, fog));
        assertEquals(0, AtmosphereRenderMaterial.ENDER_GAS.channel(1, fog));
        assertEquals(128 / 255f, AtmosphereRenderMaterial.ENDER_GAS.channel(2, fog));
        assertEquals(1, AtmosphereRenderMaterial.VIOLENCE.channel(0, fog));
        assertEquals(0, AtmosphereRenderMaterial.VIOLENCE.channel(1, fog));
        assertEquals(1, AtmosphereRenderMaterial.EXHAUST.channel(0, fog));
        assertEquals(1, AtmosphereRenderMaterial.EXHAUST.channel(1, fog));
        assertEquals(0, AtmosphereRenderMaterial.EXHAUST.channel(2, fog));
        assertEquals(0, AtmosphereRenderMaterial.SLIME.channel(0, fog));
        assertEquals(1, AtmosphereRenderMaterial.SLIME.channel(1, fog));
        assertEquals(0, AtmosphereRenderMaterial.SLIME.channel(2, fog));
        var order = new AtmosphereSliceOrder();
        var materials = AtmosphereRenderMaterial.values();
        for (int i = 0; i < materials.length; i++) {
            order.add(List.of(slice(i + 1), slice(i + 1 + materials.length)), materials[i]);
        }
        for (int depth = materials.length * 2; depth >= 1; depth--) {
            var next = order.next();
            assertEquals(depth, next.slice().depth());
            assertEquals(materials[(depth - 1) % materials.length], next.material());
        }
        assertTrue(order.isEmpty());
    }

    @Test
    void allSevenSessionIdsKeepCoincidentCellsIndependent() {
        var sessions = new java.util.EnumMap<AtmosphereRenderMaterial, MaterialClientSession>(AtmosphereRenderMaterial.class);
        for (var material : AtmosphereRenderMaterial.values()) {
            var session = new MaterialClientSession(material);
            session.world(DIMENSION);
            session.receive(WORLD, DIMENSION, true, true,
                List.of(new AtmosphereClientCache.Chunk(0, 0)), List.of(update(0, 100 + material.ordinal())));
            sessions.put(material, session);
        }
        assertEquals(7, sessions.size());
        for (var material : AtmosphereRenderMaterial.values()) {
            sessions.get(material).receive(WORLD, DIMENSION, false, false, List.of(), List.of(update(0, 0)));
            for (var other : AtmosphereRenderMaterial.values()) {
                int expected = other.ordinal() <= material.ordinal() ? 0 : 100 + other.ordinal();
                assertEquals(expected, sessions.get(other).cache().storedAmount(update(0, 0).cell()));
            }
        }
    }

    @Test
    void allSevenRenderCutoffsAreInclusiveAndDoNotDiscardStoredCells() {
        for (var material : AtmosphereRenderMaterial.values()) {
            double reach = material.reach * 128;
            for (double distance : new double[] {reach - 0.001, reach, reach + 0.001}) {
                var cache = material.newCache();
                cache.changeDimension(DIMENSION);
                cache.restore(List.of(update(0, 1000)));
                var selection = cache.lodSelection(-distance, 0, 0, 8);
                var volume = selection.volumes().getFirst();
                assertEquals(distance <= reach, AtmosphereLodHierarchy.withinReach(volume, -distance, 0, 0, 8, material.reach));
                assertEquals(List.of(update(0, 1000)), cache.exportUpdates());
                if (material.rootLevel == 0) {
                    assertEquals(material.cellSize, volume.size());
                    assertTrue(selection.unloadedFallbacks().isEmpty());
                }
            }
        }
    }

    @Test
    void violenceAndSlimeStayBaseThroughViewThenDoubleThroughTwoView() {
        for (var material : List.of(AtmosphereRenderMaterial.VIOLENCE, AtmosphereRenderMaterial.SLIME)) {
            for (double distance : new double[] {63.999, 64, 64.001, 127.999, 128, 128.001, 256}) {
                var lod = new AtmosphereLodHierarchy(material.cellSize, material.rootLevel, material.reach);
                lod.put(new AtmosphereClientCache.Cell(0, 0, 0), 1000, 1000, 0, 0);
                var selection = lod.select(-distance, 0, 0, 8, 0);
                assertEquals(1, selection.volumes().size());
                assertEquals(material.cellSize * (distance <= 128 ? 1 : 2), selection.volumes().getFirst().size());
                assertTrue(selection.volumes().stream().allMatch(v -> v.level <= 1));
            }
        }
    }

    @Test
    void allLayoutsReconcileNegativeChunkScopesUsingOwnCellSize() {
        for (var material : AtmosphereRenderMaterial.values()) {
            int cellsPerChunk = 16 / material.cellSize;
            var session = new MaterialClientSession(material);
            session.world(DIMENSION);
            session.receive(WORLD, DIMENSION, true, true,
                List.of(new AtmosphereClientCache.Chunk(-1, 0), new AtmosphereClientCache.Chunk(-2, 0)),
                List.of(update(-cellsPerChunk, 100), update(-cellsPerChunk - 1, 200)));
            session.receive(WORLD, DIMENSION, true, true, List.of(new AtmosphereClientCache.Chunk(-1, 0)), List.of());
            assertEquals(0, session.cache().storedAmount(update(-cellsPerChunk, 0).cell()));
            assertEquals(200, session.cache().storedAmount(update(-cellsPerChunk - 1, 0).cell()));
        }
    }

    private static AtmosphereVolumeGeometry.Slice slice(double depth) {
        return new AtmosphereVolumeGeometry.Slice(depth, 0.2f, List.of());
    }
}
