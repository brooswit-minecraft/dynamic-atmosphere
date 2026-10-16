package io.github.brooswitminecraft.dynamicatmosphere;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MINECRAFT-21: version-based config invalidation, run BEFORE NeoForge reads a registered config
 * file for the directory in question. Compares the file's stored {@code configVersion} against
 * the mod's current revision for that file; on any mismatch — including a pre-existing file with
 * no {@code configVersion} key at all, treated as version 0 — the OLD file is preserved under a
 * {@code <fileName>.bak-<oldVersion>} name and removed from its original path, so the regular
 * config loader then finds no file there and regenerates one from defaults.
 *
 * <p>This RESETS that file's configuration; it does not merge old custom settings into the new
 * defaults. Called from two sites, both needed because {@code ModConfig.Type.SERVER} files are
 * NOT limited to the global location — see DynamicAtmosphereMod:
 * <ul>
 *   <li>At mod-construction time, against NeoForge's global {@code config/} directory — covers
 *       both config files' global/default copy.</li>
 *   <li>On {@code ServerAboutToStartEvent}, against that server's own per-world
 *       {@code <world>/serverconfig/} override directory for the SERVER file — covers the actual
 *       file a dedicated server reads once an override exists there, which the global-directory
 *       check above never sees. Registered at {@code EventPriority.HIGHEST} so it runs before
 *       NeoForge's own internal per-world config reload for that event.</li>
 * </ul>
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

        // Collision-safe: a prior migration (or retry) may have already left a
        // <fileName>.bak-<storedVersion> behind. Never overwrite an existing backup — that
        // would silently discard a previously preserved set of custom settings — find the next
        // free suffix instead.
        Path backup = firstAvailableBackupPath(configDir, fileName, storedVersion);
        try {
            Files.move(file, backup);
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

    private static Path firstAvailableBackupPath(Path configDir, String fileName, int storedVersion) {
        Path candidate = configDir.resolve(fileName + ".bak-" + storedVersion);
        for (int attempt = 2; Files.exists(candidate); attempt++) {
            candidate = configDir.resolve(fileName + ".bak-" + storedVersion + "-" + attempt);
        }
        return candidate;
    }

    private ConfigVersionGuard() { }
}
