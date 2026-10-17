package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigVersionGuardTest {
    @Test
    void mismatchedVersionIsBackedUpByItsOldVersionAndReseededWithJustTheMarker(@TempDir Path dir)
        throws IOException {
        Path file = dir.resolve("dynamicatmosphere-server.toml");
        Files.writeString(file, "configVersion = 0\n\n[runtime]\nsyncIntervalTicks = 999\n");

        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-server.toml", 1);

        Path backup = dir.resolve("dynamicatmosphere-server.toml.bak-0");
        assertTrue(Files.exists(backup), "old file must be preserved under its old-version backup name");
        assertTrue(Files.readString(backup).contains("syncIntervalTicks = 999"),
            "backup must contain the original custom content, not a regenerated default");
        // The original path is NOT left absent: it's replaced with a marker-only file, so
        // NeoForge's own loader sees an "incomplete" file and regenerates every other key from
        // current defaults around that marker instead of the file staying versionless forever.
        assertTrue(Files.exists(file), "original path must be re-seeded with the version marker");
        String reseeded = Files.readString(file);
        assertTrue(reseeded.contains("configVersion = 1"));
        assertFalse(reseeded.contains("syncIntervalTicks"), "the seeded file must carry ONLY the marker");
    }

    @Test
    void missingConfigVersionKeyIsTreatedAsVersionZero(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dynamicatmosphere-client.toml"), "[rendering]\nenabled = true\n");

        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-client.toml", 1);

        assertTrue(Files.exists(dir.resolve("dynamicatmosphere-client.toml.bak-0")),
            "a pre-MINECRAFT-21 file with no configVersion key must be treated as version 0");
    }

    @Test
    void matchingVersionIsLeftUntouched(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dynamicatmosphere-server.toml");
        Files.writeString(file, "configVersion = 1\n");

        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-server.toml", 1);

        assertTrue(Files.exists(file), "a file already at the current version must not be touched");
        assertEquals(1, Files.list(dir).count(), "no backup file must be created on a version match");
    }

    @Test
    void absentFileIsSeededWithJustTheVersionMarker(@TempDir Path dir) throws IOException {
        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-server.toml", 1);

        Path file = dir.resolve("dynamicatmosphere-server.toml");
        assertTrue(Files.exists(file), "a version marker must be seeded so NeoForge's own fresh "
            + "default file ends up carrying a configVersion too");
        assertTrue(Files.readString(file).contains("configVersion = 1"));
    }

    @Test
    void repeatedSameOldVersionInvalidationOverwritesThePriorBackup(@TempDir Path dir) throws IOException {
        // Preserving every historical backup forever is not a requirement (product direction):
        // a repeat mismatch at the same old version simply overwrites the previous backup.
        Path existingBackup = dir.resolve("dynamicatmosphere-server.toml.bak-0");
        Files.writeString(existingBackup, "configVersion = 0\nsyncIntervalTicks = 111\n");

        Path file = dir.resolve("dynamicatmosphere-server.toml");
        Files.writeString(file, "configVersion = 0\nsyncIntervalTicks = 222\n");
        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-server.toml", 1);

        assertTrue(Files.readString(existingBackup).contains("syncIntervalTicks = 222"),
            "the newest mismatch overwrites the previous backup");
        assertTrue(Files.readString(file).contains("configVersion = 1"),
            "original path must end up re-seeded with the marker, not left stale");
    }
}
