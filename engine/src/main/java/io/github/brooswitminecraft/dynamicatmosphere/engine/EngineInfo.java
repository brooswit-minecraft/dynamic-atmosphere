package io.github.brooswitminecraft.dynamicatmosphere.engine;

/**
 * Identifying constants for the Minecraft-free engine module. The
 * NeoForge-facing subproject reads {@link #DESCRIPTION} at startup and logs
 * it, which proves at runtime that this module's classes made it into the
 * built mod jar and are reachable from the loaded mod.
 */
public final class EngineInfo {

    public static final String NAME = "dynamic-atmosphere-engine";

    public static final String VERSION = "0.0.1";

    public static final String DESCRIPTION = NAME + " v" + VERSION;

    private EngineInfo() {
    }
}
