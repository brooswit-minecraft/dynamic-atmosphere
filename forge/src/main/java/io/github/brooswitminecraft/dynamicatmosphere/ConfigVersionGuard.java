package io.github.brooswitminecraft.dynamicatmosphere;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MINECRAFT-21: version-based config invalidation. Compares a file's stored {@code configVersion}
 * against the mod's current revision for that file; on any mismatch — including a pre-existing
 * file with no {@code configVersion} key at all, treated as version 0 — the OLD file is preserved
 * under a {@code <fileName>.bak-<oldVersion>} name, and a fresh file containing ONLY the
 * {@code configVersion} marker is written in its place, so the regular NeoForge config loader then
 * finds an "incomplete" file and regenerates every other key from current defaults around that
 * marker (NeoForge's correct-and-write-back step only ADDS missing spec-defined keys; it never
 * removes the marker, an unknown key to it).
 *
 * <p>This RESETS that file's configuration; it does not merge old custom settings into the new
 * defaults.
 *
 * <p><b>Why {@code configVersion} is deliberately NOT a {@code ModConfigSpec}-registered key.</b>
 * It was originally registered with the spec (comment, default, range) like any other setting.
 * PR review caught that this is actively wrong: NeoForge's {@code ConfigTracker.loadConfig} calls
 * {@code spec.isCorrect(config)}, and on a pre-MINECRAFT-21 file — which, by definition, has no
 * {@code configVersion} key — that check fails (a spec-defined key is missing), triggering
 * {@code spec.correct(config)} + a synchronous write-back BEFORE this mod ever gets a chance to
 * read the file. {@code correct()} fills every missing defined key with its default, so it would
 * silently insert {@code configVersion = CURRENT_VERSION} itself — making a genuinely stale file
 * look already-current to this guard on the very same load that should have reset it, and
 * permanently hiding the mismatch (the file never looks "missing the key" again after that first
 * correction). Keeping {@code configVersion} OUT of the spec means NeoForge's correction has no
 * reason to ever touch it — it only "corrects" keys it knows about — so whatever this guard reads
 * is exactly what was in the file before any framework code ran, regardless of load-lifecycle
 * ordering. See {@code DynamicAtmosphereServerConfig}/{@code DynamicAtmosphereClientConfig} for
 * the (deliberately spec-less) {@code CURRENT_VERSION} constants this method is called with.
 *
 * <p>Called from two sites in DynamicAtmosphereMod, both needed because {@code ModConfig.Type.
 * SERVER} files are NOT limited to the global location:
 * <ul>
 *   <li>At mod-construction time, against NeoForge's global {@code config/} directory — covers
 *       both config files' global/default copy, before {@code modContainer.registerConfig} or any
 *       later FML config-loading phase reads it.</li>
 *   <li>On {@code ServerAboutToStartEvent}, against that server's own per-world
 *       {@code <world>/serverconfig/} override directory for the SERVER file. This one is NOT a
 *       before-load check — {@code ServerLifecycleHooks.handleServerAboutToStart} calls
 *       {@code ConfigTracker.loadConfigs(SERVER, ...)} directly, synchronously, BEFORE it
 *       constructs and posts the event, so no listener at any priority can run first (confirmed
 *       by decompiling NeoForge 21.1). The caller must instead explicitly re-invoke NeoForge's own
 *       public {@code ConfigTracker.loadConfigs(Type, Path, Path)} afterward when this method
 *       reports a mismatch, to correct the active in-memory spec for this session — see
 *       DynamicAtmosphereMod.onServerAboutToStart. That reload call is intentionally scoped to
 *       firing only when THIS mod found ITS OWN mismatch, not unconditionally on every server
 *       start; NeoForge's {@code ConfigTracker} has no narrower public per-mod reload entry point
 *       (confirmed: {@code ConfigTracker}'s per-{@code ModConfig} {@code loadConfig} is
 *       package-private), so the {@code Type}-wide call — the exact same one NeoForge's own
 *       bootstrap code uses for this purpose — is the only available integration point.</li>
 * </ul>
 */
public final class ConfigVersionGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** @return true if a mismatch was found and the old file backed up/reset, false otherwise
     * (including on an unreadable file, left untouched rather than guessed about, and on a freshly
     * seeded/already-current file) — callers that load this directory into an active spec
     * synchronously before this check runs (so a stale value may already be cached) must treat
     * true as "now reload/reset the active spec too". */
    public static boolean applyVersionInvalidation(Path configDir, String fileName, int currentVersion) {
        Path file = configDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            // No file yet (fresh install, or a world that has never had an override before):
            // seed just the version marker so whatever NeoForge regenerates around it on first
            // load carries a configVersion from this point on. Without this, a config-less file
            // NeoForge creates from pure spec defaults would never gain a configVersion key
            // either (it's deliberately not spec-registered) and would look like "version 0"
            // forever, re-triggering this same backup/reset on every future start.
            writeVersionMarkerOnly(file, currentVersion);
            return false;
        }

        int storedVersion;
        try (FileConfig config = FileConfig.of(file)) {
            config.load();
            Number stored = config.get("configVersion");
            storedVersion = stored == null ? 0 : stored.intValue();
        } catch (RuntimeException e) {
            LOGGER.warn("[dynamicatmosphere] could not read configVersion from {}; leaving it untouched: {}",
                file, e.toString());
            return false;
        }
        if (storedVersion == currentVersion) return false;

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
            return false;
        }
        writeVersionMarkerOnly(file, currentVersion);
        LOGGER.warn("[dynamicatmosphere] {} configVersion mismatch (found {}, expected {}): preserved the "
                + "old file as {} and will regenerate defaults. This RESETS this file's configuration; it "
                + "does not merge old custom settings into the new defaults.",
            fileName, storedVersion, currentVersion, backup);
        return true;
    }

    private static void writeVersionMarkerOnly(Path file, int currentVersion) {
        try {
            Files.writeString(file, "configVersion = " + currentVersion + System.lineSeparator());
        } catch (IOException e) {
            LOGGER.error("[dynamicatmosphere] failed to seed a configVersion marker at {}: {}",
                file, e.toString());
        }
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
