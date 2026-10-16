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
 * this ticket's own description says stays per-world. These tests exercise the exact method
 * wired to ServerAboutToStartEvent (see DynamicAtmosphereMod.onServerAboutToStart), proving it
 * invalidates a stale per-world file using the SERVER file's own name/version, and touches
 * nothing else in that directory — without needing a bootstrapped MinecraftServer.
 */
class DynamicAtmosphereModServerConfigInvalidationTest {
    @Test
    void staleExistingWorldServerConfigIsBackedUpAndReset(@TempDir Path worldServerConfigDir) throws IOException {
        Path serverFile = worldServerConfigDir.resolve(DynamicAtmosphereServerConfig.FILE_NAME);
        Files.writeString(serverFile, "configVersion = 0\nsyncIntervalTicks = 777\n");

        DynamicAtmosphereMod.invalidateServerConfigAt(worldServerConfigDir);

        assertFalse(Files.exists(serverFile), "the stale per-world override must be moved away");
        Path backup = worldServerConfigDir.resolve(DynamicAtmosphereServerConfig.FILE_NAME + ".bak-0");
        assertTrue(Files.exists(backup), "the stale per-world override must be preserved as a backup");
        assertTrue(Files.readString(backup).contains("syncIntervalTicks = 777"));
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

        DynamicAtmosphereMod.invalidateServerConfigAt(worldServerConfigDir);

        assertTrue(Files.exists(serverFile), "an override already at the current version must not be touched");
    }
}
