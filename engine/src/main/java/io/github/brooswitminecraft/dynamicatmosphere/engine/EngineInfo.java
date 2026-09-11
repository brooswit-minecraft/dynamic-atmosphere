package io.github.brooswitminecraft.dynamicatmosphere.engine;

/**
 * Identifying constants for the Minecraft-free engine module. The
 * NeoForge-facing subproject reads {@link #DESCRIPTION} at startup and logs
 * it, which proves at runtime that this module's classes made it into the
 * built mod jar and are reachable from the loaded mod.
 */
public final class EngineInfo {

    public static final String NAME = "dynamic-atmosphere-engine";

    /**
     * Deliberately does NOT follow the mod's release tag (SICKOS-40
     * deliverable 4 decided this explicitly, both ways are defensible).
     * {@code engine} has no release of its own to version: this constant
     * exists only so the log line below can prove {@code engine}'s classes
     * made it into the built jar. Keeping it in lockstep with the mod
     * version would need wiring the same CI-supplied property into a module
     * whose whole point is knowing nothing about Minecraft or the mod that
     * embeds it, in exchange for no benefit beyond making this string look
     * like it means something it doesn't. Bump it by hand if this module's
     * own internal shape changes meaningfully; do not sync it to a tag.
     */
    public static final String VERSION = "0.0.1";

    public static final String DESCRIPTION = NAME + " v" + VERSION;

    private EngineInfo() {
    }
}
