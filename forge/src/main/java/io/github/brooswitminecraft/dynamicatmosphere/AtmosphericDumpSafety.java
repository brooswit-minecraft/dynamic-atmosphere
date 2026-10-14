package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Testable safety contract for the temporary destructive command.  Its input is
 * deliberately an explicit collection of already-loaded targets: an operation
 * cannot discover, load, or mutate a persisted-unloaded chunk through this API.
 */
final class AtmosphericDumpSafety {
    static final int REQUIRED_PERMISSION_LEVEL = 2;
    static final String CONFIRM_LITERAL = "confirm";

    static boolean mayExecute(int permissionLevel) {
        return permissionLevel >= REQUIRED_PERMISSION_LEVEL;
    }

    static String confirmationMessage() {
        return "WARNING: this permanently deletes loaded Dynamic Atmosphere material data. "
            + "Run /dynamicatmosphere delete-material-data " + CONFIRM_LITERAL + " to proceed. "
            + "Unloaded chunk attachments are not touched.";
    }

    /** Returns only the supplied targets; callers must pass loaded chunks explicitly. */
    static <T> List<T> loadedTargets(Collection<T> loadedTargets) {
        return List.copyOf(loadedTargets);
    }

    /** Runs a supplied destructive action only over the caller's loaded snapshot. */
    static <T> void cleanLoadedOnly(Collection<T> loadedTargets, Consumer<T> cleanup) {
        loadedTargets(loadedTargets).forEach(cleanup);
    }

    /** Clears transient work only after the loaded attachment cleanup has completed. */
    static void clearRuntimeState(Runnable... clearers) {
        for (Runnable clearer : clearers) clearer.run();
    }

    interface LoadedChunkTarget {
        boolean vaporReadable();
        int deleteVapor();
        boolean smokeReadable();
        int deleteSmoke();
        boolean materialReadable(AtmosphereMaterial material);
        int deleteMaterial(AtmosphereMaterial material);
        void clearCapacities();
    }

    static void cleanLoadedChunk(LoadedChunkTarget target, ForgeAtmospherePrototype.DumpReport report) {
        if (target.vaporReadable()) report.vapor += delete(target::deleteVapor, report);
        else report.unreadable++;
        if (target.smokeReadable()) report.smoke += delete(target::deleteSmoke, report);
        else report.unreadable++;
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            if (!target.materialReadable(material)) { report.unreadable++; continue; }
            int cells = delete(() -> target.deleteMaterial(material), report);
            report.materials.merge(material, cells, Integer::sum);
        }
        target.clearCapacities();
    }

    private static int delete(java.util.function.IntSupplier action, ForgeAtmospherePrototype.DumpReport report) {
        try { return action.getAsInt(); } catch (IllegalStateException exception) { report.unreadable++; return 0; }
    }

    private AtmosphericDumpSafety() { }
}
