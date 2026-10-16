package io.github.brooswitminecraft.dynamicatmosphere;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * MINECRAFT-21: version-based config invalidation, run BEFORE NeoForge's ConfigTracker reads a
 * registered config file (see DynamicAtmosphereMod's constructor, which calls this ahead of
 * modContainer.registerConfig for each file). Compares the file's stored {@code configVersion}
 * against the mod's current revision for that file; on any mismatch — including a pre-existing
 * file with no {@code configVersion} key at all, treated as version 0 — the OLD file is preserved
 * as {@code <fileName>.bak-<oldVersion>} and removed from its original path, so NeoForge then
 * finds no file there and regenerates one from defaults.
 *
 * <p>This RESETS that file's configuration; it does not merge old custom settings into the new
 * defaults. Scope: only the config directory's own copy of each file is checked (NeoForge's
 * global {@code config/} location at mod-construction time). A dedicated server's per-world
 * {@code <world>/serverconfig/} override copy, if NeoForge has already created one from an
 * earlier run, is a separate file this check does not see or touch.
 */
public final class ConfigVersionGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void applyVersionInvalidation(Path configDir, String fileName, int currentVersion) {
        Path file = configDir.resolve(fileName);
        if (!Files.isRegularFile(file)) return;

        int storedVersion;
        try (FileConfig config = FileConfig.of(file)) {
            config.load();
            Number stored = config.get("configVersion");
            storedVersion = stored == null ? 0 : stored.intValue();
        } catch (RuntimeException e) {
            LOGGER.warn("[dynamicatmosphere] could not read configVersion from {}; leaving it untouched: {}",
                file, e.toString());
            return;
        }
        if (storedVersion == currentVersion) return;

        Path backup = configDir.resolve(fileName + ".bak-" + storedVersion);
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("[dynamicatmosphere] configVersion mismatch on {} (found {}, expected {}) but "
                    + "failed to back it up to {}; leaving the old file in place: {}",
                file, storedVersion, currentVersion, backup, e.toString());
            return;
        }
        LOGGER.warn("[dynamicatmosphere] {} configVersion mismatch (found {}, expected {}): preserved the "
                + "old file as {} and will regenerate defaults. This RESETS this file's configuration; it "
                + "does not merge old custom settings into the new defaults.",
            fileName, storedVersion, currentVersion, backup);
    }

    private ConfigVersionGuard() { }
}
