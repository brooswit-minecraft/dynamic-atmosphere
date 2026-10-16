package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicAtmosphereConfigTest {
    @Test
    void serverSnapshotFallsBackToCurrentDefaultsBeforeConfigLoads() {
        var first = DynamicAtmosphereServerConfig.snapshot();
        var second = DynamicAtmosphereServerConfig.snapshot();

        assertSame(first, second);
        assertEquals(4, first.smoke().lavaEmission());
        assertEquals(1.0, first.smoke().leafRemovalChance());
        assertEquals(10.0 / 128, first.smoke().farmlandConversionChance());
        assertEquals(10.0 / 256, first.smoke().villagerConversionChance());
        assertEquals(10.0 / 64, first.smoke().dissipationChance());
        assertEquals(0.75, first.runtime().simulationSkipChance());
        assertEquals(100, first.obsidianPowder().portalBlockEmission());
        assertEquals(256, first.vapor().rainCloudHeight());
        assertEquals(120, first.vapor().netherCloudlineHeight());
        assertEquals(16, first.vapor().endCloudlineBlocksBelowTop());
        assertEquals(2, first.verticalMovement().deadBand());
        assertEquals(0.40, first.verticalMovement().w());
        assertEquals(0.10, first.verticalMovement().driftFraction());
        assertEquals(144, first.smoke().preferredHeight());
        assertEquals(64, first.slime().preferredHeight());
        assertEquals(24, first.vapor().highTerrainBlocksAboveSeaLevel());
        assertEquals(200, first.runtime().simulationIntervalTicks());
        assertEquals(300, first.runtime().producerIntervalTicks());
        assertEquals(1.0, first.integrations().createFanTransportPerRpm());
        assertEquals(100, first.integrations().createFanIntervalTicks());
        assertEquals(32, first.integrations().maxFanChunksPerTick());
        assertEquals(12.0, first.heavyGas().dissipationFactor());
        assertEquals(0.1, first.magma().breakLavaChance());
        assertEquals(32, first.slime().dissipationSpawnChanceDenominator());
        assertEquals(8, first.slime().dissipationSpawnAttempts());
        assertEquals(0.25, first.slime().mediumDensityThreshold());
        assertEquals(0.75, first.slime().bigDensityThreshold());
    }

    @Test
    void onlyAbsoluteHomedMaterialsHaveAPreferredHeightKey() {
        // MINECRAFT-1 (B1): Dust, Void Gas, Exhaust and Obsidian Powder rest on the dimension's
        // own bottom, which is structural, not a tunable number, so they get no preferredHeight
        // key at all. Smoke and Slime rest at an absolute height and do get one.
        var names = flattenConfigKeys(DynamicAtmosphereServerConfig.SPEC.getValues(), "");
        for (String expected : List.of("smoke.preferredheight", "slime.preferredheight")) {
            assertTrue(names.contains(expected), expected + " must be defined");
        }
        for (String absent : List.of("dust.preferredheight", "voidgas.preferredheight",
            "exhaust.preferredheight", "obsidianpowder.preferredheight")) {
            assertFalse(names.contains(absent), absent + " must not be defined");
        }
    }

    @Test
    void removedVoidGasSpawnKeysAreNoLongerDefined() {
        // MINECRAFT-17: the consumption-driven zombie spawn (and these two keys) are removed.
        // This flattens the spec into full dotted paths so the assertion is against the
        // voidGas-qualified path specifically, not a bare leaf-name match.
        var names = flattenConfigKeys(DynamicAtmosphereServerConfig.SPEC.getValues(), "");
        assertTrue(names.contains("voidgas.bottomemission"), "sanity: voidGas section keys must use a dotted path");
        for (String removed : List.of("voidgas.spawnchancedenominator", "voidgas.spawnattempts")) {
            assertFalse(names.contains(removed), removed + " must no longer be defined");
        }
    }

    @Test
    void removedSlimeConsumptionSpawnKeysAreReplacedByDissipationSpawnKeys() {
        // MINECRAFT-18: the consumption-driven spawn (and these two keys) are removed in favor
        // of a dissipation-triggered spawn with its own chance/attempts keys plus density
        // thresholds that pick the spawned slime's size.
        var names = flattenConfigKeys(DynamicAtmosphereServerConfig.SPEC.getValues(), "");
        for (String removed : List.of("slime.spawnchancedenominator", "slime.spawnattempts")) {
            assertFalse(names.contains(removed), removed + " must no longer be defined");
        }
        for (String added : List.of("slime.dissipationspawnchancedenominator", "slime.dissipationspawnattempts",
            "slime.mediumdensitythreshold", "slime.bigdensitythreshold")) {
            assertTrue(names.contains(added), added + " must be defined");
        }
    }

    private static java.util.Set<String> flattenConfigKeys(
        com.electronwill.nightconfig.core.UnmodifiableConfig config, String prefix
    ) {
        var result = new java.util.HashSet<String>();
        config.valueMap().forEach((key, value) -> {
            String path = (prefix.isEmpty() ? "" : prefix + ".") + key.toLowerCase(java.util.Locale.ROOT);
            if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested) {
                result.addAll(flattenConfigKeys(nested, path));
            } else {
                result.add(path);
            }
        });
        return result;
    }

    @Test
    void clientSnapshotFallsBackToCurrentDefaultsBeforeConfigLoads() {
        var first = DynamicAtmosphereClientConfig.snapshot();
        var second = DynamicAtmosphereClientConfig.snapshot();

        assertSame(first, second);
        assertEquals(4, first.slicesPerBaseCell());
        assertEquals(4096, first.selectionWorkPerTick());
        assertEquals(2, first.maxDistanceMultiplier());
        assertEquals(0.75, first.fadeStartFraction());
        assertEquals(2, first.smoke().opticalDensity());
        assertEquals(40, first.obsidianPowder().opticalDensity());
        assertEquals(2, first.glowGas().opticalDensity());
        assertEquals(1, first.netherFog().opticalDensity());
        assertEquals(200_000, first.allocation().cellBudget());
    }

    @Test
    void glowGasServerConfigDefaultsMatchTheEpicSpec() {
        var snapshot = DynamicAtmosphereServerConfig.snapshot();
        assertEquals(100, snapshot.glowGas().glowstoneEmission());
        // The renderer reads the client config value, not the engine catalog field: both must
        // agree, or the change is invisible in game (the 0.20.0 smokeOpticalDensity trap).
        assertEquals(io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.GLOW_GAS
            .settings().opticalDensityMultiplier(), DynamicAtmosphereClientConfig.snapshot().glowGas().opticalDensity());
    }

    @Test
    void netherFogServerConfigDefaultsMatchTheEpicSpec() {
        var snapshot = DynamicAtmosphereServerConfig.snapshot();
        assertEquals(0.10, snapshot.netherFog().maxCondensationChance());
        assertEquals(io.github.brooswitminecraft.dynamicatmosphere.engine.AtmosphericMaterials.NETHER_FOG
            .settings().opticalDensityMultiplier(), DynamicAtmosphereClientConfig.snapshot().netherFog().opticalDensity());
        // MINECRAFT-1 (B3): one full block's capacity share at the production cell size (4),
        // pinned down numerically so a change here is deliberate. ForgeAtmospherePrototype's
        // harvest (sampleNetherFogChunk) and condensation (condenseNetherFog) both call this
        // exact same method for their amount, which is what keeps the lava<->fog cycle from
        // draining or flooding lava over repeated round trips -- not a value independently
        // duplicated in two places.
        assertEquals(15, AtmosphereMaterial.NETHER_FOG.capacityForAirBlocks(1));
    }

    @Test
    void endSandAndEndOnlyEnderGasServerConfigDefaultsMatchTheEpicSpec() {
        var snapshot = DynamicAtmosphereServerConfig.snapshot();
        // MINECRAFT-1 (B4): End-only Ender Gas (Obsidian Powder) production and condensation.
        assertEquals(200, snapshot.obsidianPowder().endEmission());
        assertEquals(0.10, snapshot.obsidianPowder().endGateChance());
        assertEquals(0.10, snapshot.obsidianPowder().endSandCondensationChance());
        // End Sand's own rise interval -- read by the registered block, not simulation code.
        assertEquals(20, snapshot.endSand().riseIntervalTicks());
    }

    @Test
    void oldPerMaterialReachKeysAreNoLongerDefined() {
        var names = DynamicAtmosphereClientConfig.SPEC.getValues().valueMap().keySet().stream()
            .map(Object::toString).collect(java.util.stream.Collectors.toSet());
        for (String removed : List.of(
            "vaporReachMultiplier", "smokeReachMultiplier", "dustReachMultiplier",
            "voidGasReachMultiplier", "exhaustReachMultiplier", "slimeReachMultiplier",
            "obsidianPowderReachMultiplier")) {
            assertFalse(names.stream().anyMatch(n -> n.toLowerCase(java.util.Locale.ROOT)
                .contains(removed.toLowerCase(java.util.Locale.ROOT))), removed + " must no longer be defined");
        }
    }

    @Test
    void maxDistanceAndFadeStartAreValidatedInRange() {
        assertEquals(2.0, DynamicAtmosphereClientConfig.validDouble(0.1, 2.0, 0.25, 4));
        assertEquals(2.0, DynamicAtmosphereClientConfig.validDouble(5.0, 2.0, 0.25, 4));
        assertEquals(0.75, DynamicAtmosphereClientConfig.validDouble(-1.0, 0.75, 0, 1));
        assertEquals(0.75, DynamicAtmosphereClientConfig.validDouble(1.5, 0.75, 0, 1));
    }

    @Test
    void invalidServerValuesFallBackInsteadOfEscaping() {
        assertEquals(4, DynamicAtmosphereServerConfig.validInt(null, 4, 0, 10));
        assertEquals(4, DynamicAtmosphereServerConfig.validInt(-1, 4, 0, 10));
        assertEquals(4, DynamicAtmosphereServerConfig.validInt(11, 4, 0, 10));
        assertEquals(0.5, DynamicAtmosphereServerConfig.validDouble(Double.NaN, 0.5, 0, 1));
        assertEquals(0.5, DynamicAtmosphereServerConfig.validDouble(2.0, 0.5, 0, 1));
    }

    @Test
    void invalidClientValuesFallBackInsteadOfEscaping() {
        assertEquals(4096, DynamicAtmosphereClientConfig.validInt(null, 4096, 1, 10_000));
        assertEquals(4096, DynamicAtmosphereClientConfig.validInt(0, 4096, 1, 10_000));
        assertEquals(2.0, DynamicAtmosphereClientConfig.validDouble(
            Double.POSITIVE_INFINITY, 2.0, 0.25, 4));
        assertEquals(2.0, DynamicAtmosphereClientConfig.validDouble(0.1, 2.0, 0.25, 4));
    }
}
