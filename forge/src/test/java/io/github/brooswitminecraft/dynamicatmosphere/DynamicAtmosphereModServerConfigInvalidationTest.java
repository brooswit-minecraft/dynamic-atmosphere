package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-21 review: the mod-construction-time ConfigVersionGuard calls in
 * DynamicAtmosphereMod only ever see NeoForge's global config/ directory, never a dedicated
 * server's actual per-world <world>/serverconfig/ override — the ModConfig.Type.SERVER file
 * this ticket's own description says stays per-world. Re-review found the first fix for this
 * (a HIGHEST-priority ServerAboutToStartEvent listener) still wrong: decompiling NeoForge 21.1's
 * ServerLifecycleHooks.handleServerAboutToStart shows it calls ConfigTracker.loadConfigs(SERVER,
 * ...) BEFORE constructing and posting that event, so no listener at any priority can run first.
 * invalidateServerConfigAt's boolean return is what lets DynamicAtmosphereMod.onServerAboutToStart
 * know to explicitly re-invoke NeoForge's own ConfigTracker.loadConfigs afterward and correct the
 * already-loaded active spec, instead of only preventing the NEXT start's load.
 *
 * <p>These tests exercise the exact method wired to that event, proving it invalidates a stale
 * per-world file using the SERVER file's own name/version, reports the mismatch so the caller
 * knows a reload is owed, and touches nothing else in that directory — without needing a
 * bootstrapped MinecraftServer.
 */
class DynamicAtmosphereModServerConfigInvalidationTest {
    @Test
    void staleExistingWorldServerConfigIsBackedUpAndReset(@TempDir Path worldServerConfigDir) throws IOException {
        Path serverFile = worldServerConfigDir.resolve(DynamicAtmosphereServerConfig.FILE_NAME);
        Files.writeString(serverFile, "configVersion = 0\nsyncIntervalTicks = 777\n");

        boolean invalidated = DynamicAtmosphereMod.invalidateServerConfigAt(worldServerConfigDir);

        assertTrue(invalidated, "caller must be told a mismatch happened, so it knows to reload the active spec");
        Path backup = worldServerConfigDir.resolve(DynamicAtmosphereServerConfig.FILE_NAME + ".bak-0");
        assertTrue(Files.exists(backup), "the stale per-world override must be preserved as a backup");
        assertTrue(Files.readString(backup).contains("syncIntervalTicks = 777"));
        assertTrue(Files.readString(serverFile).contains("configVersion = " + DynamicAtmosphereServerConfig.CURRENT_VERSION),
            "original path must be re-seeded with the marker, not left stale or absent");
    }

    @Test
    void unrelatedFilesInTheSameWorldServerConfigDirectoryAreUntouched(@TempDir Path worldServerConfigDir)
        throws IOException {
        Path serverFile = worldServerConfigDir.resolve(DynamicAtmosphereServerConfig.FILE_NAME);
        Files.writeString(serverFile, "configVersion = 0\n");
        // Another mod's per-world override living in the same directory.
        Path otherModFile = worldServerConfigDir.resolve("someOtherMod-server.toml");
        Files.writeString(otherModFile, "configVersion = 0\nunrelated = true\n");

        DynamicAtmosphereMod.invalidateServerConfigAt(worldServerConfigDir);

        assertTrue(Files.exists(otherModFile), "a sibling file this mod does not own must be left alone");
        assertTrue(Files.readString(otherModFile).contains("unrelated = true"));
    }

    @Test
    void currentVersionExistingWorldOverrideIsLeftUntouched(@TempDir Path worldServerConfigDir) throws IOException {
        Path serverFile = worldServerConfigDir.resolve(DynamicAtmosphereServerConfig.FILE_NAME);
        Files.writeString(serverFile, "configVersion = " + DynamicAtmosphereServerConfig.CURRENT_VERSION + "\n");

        boolean invalidated = DynamicAtmosphereMod.invalidateServerConfigAt(worldServerConfigDir);

        assertFalse(invalidated, "no mismatch means the caller owes no explicit reload");
        assertTrue(Files.exists(serverFile), "an override already at the current version must not be touched");
    }
}
