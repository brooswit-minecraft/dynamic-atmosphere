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
        assertEquals(0.85, first.lightning().minConcentration());
        assertEquals(0.002, first.lightning().chance());
        assertEquals(0.03, first.lightning().materialCost());
        assertEquals(8, first.lightning().surfaceSearchAttempts());
        assertEquals(0.90, first.ghastSpawn().minConcentration());
        assertEquals(0.0001, first.ghastSpawn().chance());
        assertEquals(0.20, first.ghastSpawn().materialCost());
        assertEquals(24, first.zombieFog().deathEmission());
        assertEquals(0.30, first.zombieFog().spawnConcentrationThreshold());
        assertEquals(2.0, first.zombieFog().spawnMultiplier());
        assertEquals(0.5, first.zombieFog().heatMinBiomeTemperature());
        assertEquals(0.05, first.zombieFog().heatChance());
        assertEquals(0.08, first.zombieFog().heatMaterialCost());
        assertEquals(0.65, first.slimePrecipitation().rainMinConcentration());
        assertEquals(0.15, first.slimePrecipitation().rainMinBiomeTemperature());
        assertEquals(0.02, first.slimePrecipitation().rainChance());
        assertEquals(0.01, first.slimePrecipitation().rainMaterialCost());
        assertEquals(0.65, first.slimePrecipitation().snowMinConcentration());
        assertEquals(0.15, first.slimePrecipitation().snowMaxBiomeTemperature());
        assertEquals(0.002, first.slimePrecipitation().snowChance());
        assertEquals(0.04, first.slimePrecipitation().snowMaterialCost());
        assertEquals(8, first.slimePrecipitation().surfaceSearchAttempts());
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
        assertEquals(0.35, first.celestialTintMaxStrength());
        assertEquals(0xFFCB82, first.sunTintColor());
        assertEquals(0xA9C4E8, first.moonTintColor());
        assertEquals(4, first.smoke().opticalDensity());
        assertEquals(0.3, first.obsidianPowder().opticalDensity());
        assertEquals(1.2, first.vapor().opticalDensity());
        assertEquals(2, first.glowGas().opticalDensity());
        assertEquals(1, first.netherFog().opticalDensity());
        assertEquals(200_000, first.allocation().cellBudget());
    }

    @Test
    void materialColorsAreClientConfigValuesWithTheExpectedDefaults() {
        // MINECRAFT-21: all materials' colours are now client config values, not
        // hard-coded AtmosphereRenderMaterial enum literals. Smoke/Obsidian Powder/Vapor
        // changed there; MINECRAFT-398 later changed Dust; the rest stay at their
        // long-standing catalog colours.
        var first = DynamicAtmosphereClientConfig.snapshot();
        assertEquals(0x0D0A08, first.smoke().color(), "Smoke: deliberately NOT pure black");
        assertEquals(0x1A1025, first.obsidianPowder().color(), "Obsidian Powder: PROPOSED, pending visual acceptance");
        assertEquals(0x000000, first.vapor().color());
        // MINECRAFT-398: 0x8B4513 (saddle brown) -> 0xD2B48C (tan), a sandier default.
        assertEquals(0xD2B48C, first.dust().color());
        assertEquals(0x000000, first.voidGas().color());
        assertEquals(0xFFFF00, first.exhaust().color());
        assertEquals(0x00FF00, first.slime().color());
        assertEquals(0x808080, first.zombieFog().color());
        assertEquals(0xFFC83C, first.glowGas().color());

        var names = flattenConfigKeys(DynamicAtmosphereClientConfig.SPEC.getValues(), "");
        for (String colorKey : List.of("materials.vaporcolor", "materials.smokecolor", "materials.dustcolor",
            "materials.voidgascolor", "materials.exhaustcolor", "materials.slimecolor",
            "materials.obsidianpowdercolor", "materials.zombiefogcolor", "materials.glowgascolor")) {
            assertTrue(names.contains(colorKey), colorKey + " must be defined");
        }
    }

    @Test
    void configVersionIsDeliberatelyNotPartOfEitherModConfigSpec() {
        // MINECRAFT-21 re-review: configVersion must NOT be a ModConfigSpec-registered key.
        // If it were, NeoForge's own correct-and-write-back pass (ConfigTracker.loadConfig,
        // triggered whenever a defined key is missing) would invent/overwrite it with the
        // current default BEFORE this mod ever reads a pre-existing file — making a genuinely
        // stale file look already-current on the very load that should have reset it. See
        // ConfigVersionGuard's class javadoc. This pins that design decision so it isn't
        // "helpfully" reverted later.
        var serverNames = flattenConfigKeys(DynamicAtmosphereServerConfig.SPEC.getValues(), "");
        var clientNames = flattenConfigKeys(DynamicAtmosphereClientConfig.SPEC.getValues(), "");
        assertFalse(serverNames.contains("configversion"), "server configVersion must NOT be spec-registered");
        assertFalse(clientNames.contains("configversion"), "client configVersion must NOT be spec-registered");
        assertTrue(DynamicAtmosphereServerConfig.CURRENT_VERSION >= 1);
        assertTrue(DynamicAtmosphereClientConfig.CURRENT_VERSION >= 1);
    }

    @Test
    void neoForgeSpecCorrectionNeverInventsOrOverwritesTheVersionMarker() throws java.io.IOException {
        // Regression for the exact sequence PR review asked for: existing versionless override
        // -> framework load/correction -> our migration -> active spec. Simulates NeoForge's own
        // ConfigTracker.loadConfig behaviour (spec.isCorrect() false on a missing defined key ->
        // spec.correct() fills every MISSING DEFINED key with its default) directly against the
        // real DynamicAtmosphereServerConfig.SPEC, without needing a bootstrapped MinecraftServer.
        var tempDir = java.nio.file.Files.createTempDirectory("minecraft-21-lifecycle");
        try {
            var file = tempDir.resolve("dynamicatmosphere-server.toml");
            // A pre-MINECRAFT-21 file: no configVersion key, and missing every other defined key
            // too (as a real pre-existing file from an older mod version would be, since this
            // ticket added no other server-side key) — so NeoForge's isCorrect() must fail and
            // correct() must run, exactly like a real load.
            java.nio.file.Files.writeString(file, "");

            try (var config = com.electronwill.nightconfig.core.file.CommentedFileConfig.of(file)) {
                config.load();
                assertFalse(DynamicAtmosphereServerConfig.SPEC.isCorrect(config),
                    "sanity: an empty file must be missing defined keys");
                DynamicAtmosphereServerConfig.SPEC.correct(config);
                // NeoForge's own correction must have filled in its OWN defined keys...
                assertTrue(DynamicAtmosphereServerConfig.SPEC.isCorrect(config),
                    "sanity: spec.correct() must have filled in every missing defined key");
                // ...but must NOT have invented a configVersion entry, since it isn't spec-defined.
                org.junit.jupiter.api.Assertions.assertNull(config.get("configVersion"),
                    "spec.correct() must never touch configVersion - it doesn't know this key exists");
                config.save();
            }

            // Reload fresh from disk to prove the absence of configVersion survives persistence,
            // not just the in-memory object correct() ran against.
            try (var corrected = com.electronwill.nightconfig.core.file.FileConfig.of(file)) {
                corrected.load();
                org.junit.jupiter.api.Assertions.assertNull(corrected.get("configVersion"),
                    "configVersion must still be absent from the on-disk file after correction+save");
            }

            // Our own guard, reading the SAME file afterward, must still correctly see this as a
            // version-0 (missing-key) mismatch and migrate it - unaffected by the correction pass
            // that already ran against it.
            ConfigVersionGuard.applyVersionInvalidation(
                tempDir, "dynamicatmosphere-server.toml", DynamicAtmosphereServerConfig.CURRENT_VERSION);
            assertTrue(java.nio.file.Files.exists(tempDir.resolve("dynamicatmosphere-server.toml.bak-0")),
                "the framework-corrected file (not just a hypothetical original) must be the one backed up");
        } finally {
            try (var walk = java.nio.file.Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        java.nio.file.Files.delete(p);
                    } catch (java.io.IOException ignored) {
                        // best-effort cleanup of a JUnit temp dir
                    }
                });
            }
        }
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
