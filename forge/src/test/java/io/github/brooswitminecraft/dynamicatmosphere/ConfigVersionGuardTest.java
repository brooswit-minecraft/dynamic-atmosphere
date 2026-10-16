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
    void mismatchedVersionIsBackedUpByItsOldVersionAndOriginalIsRemoved(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dynamicatmosphere-server.toml");
        Files.writeString(file, "configVersion = 0\n\n[runtime]\nsyncIntervalTicks = 999\n");

        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-server.toml", 1);

        assertFalse(Files.exists(file), "original file must be moved away, not left in place");
        Path backup = dir.resolve("dynamicatmosphere-server.toml.bak-0");
        assertTrue(Files.exists(backup), "old file must be preserved under its old-version backup name");
        assertTrue(Files.readString(backup).contains("syncIntervalTicks = 999"),
            "backup must contain the original custom content, not a regenerated default");
    }

    @Test
    void missingConfigVersionKeyIsTreatedAsVersionZero(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dynamicatmosphere-client.toml");
        Files.writeString(file, "[rendering]\nenabled = true\n");

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
    void absentFileIsANoOp(@TempDir Path dir) {
        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-server.toml", 1);
        assertEquals(0, dir.toFile().list().length, "nothing to invalidate when the file does not exist yet");
    }

    @Test
    void repeatedSameOldVersionInvalidationPreservesBothOriginals(@TempDir Path dir) throws IOException {
        // A prior migration (or a retried server start) may have already left a
        // .bak-0 behind; a second mismatch at the same old version must not clobber it.
        Path existingBackup = dir.resolve("dynamicatmosphere-server.toml.bak-0");
        Files.writeString(existingBackup, "configVersion = 0\nsyncIntervalTicks = 111\n");

        Path file = dir.resolve("dynamicatmosphere-server.toml");
        Files.writeString(file, "configVersion = 0\nsyncIntervalTicks = 222\n");
        ConfigVersionGuard.applyVersionInvalidation(dir, "dynamicatmosphere-server.toml", 1);

        assertTrue(Files.readString(existingBackup).contains("syncIntervalTicks = 111"),
            "the pre-existing backup must be left untouched");
        Path secondBackup = dir.resolve("dynamicatmosphere-server.toml.bak-0-2");
        assertTrue(Files.exists(secondBackup), "a colliding backup must fall back to a free suffix");
        assertTrue(Files.readString(secondBackup).contains("syncIntervalTicks = 222"),
            "the newly-backed-up file's own content must be preserved under the free suffix");
        assertFalse(Files.exists(file), "original file must still be moved away");
    }
}
