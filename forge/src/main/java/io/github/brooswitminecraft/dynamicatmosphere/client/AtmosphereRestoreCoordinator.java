package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import com.mojang.logging.LogUtils;

/**
 * Owns the bounded, generation-gated historical disk-restore lifecycle: when to (re)start loading,
 * when a result must be discarded as stale, and what to hand back for incremental application. No
 * Minecraft dependency — the caller (AtmosphereClient) supplies level identity as an opaque
 * reference and dimension as a plain string, so the lifecycle rules themselves (packet-before-world,
 * same-dimension level replacement, disconnect/reconnect to the same world) are unit-testable
 * without a live Minecraft world.
 */
final class AtmosphereRestoreCoordinator {
    private static final int RESTORE_BATCH_SIZE = 2048;

    enum LevelChange {
        /** The level identity did not change; no action taken. */
        UNCHANGED,
        /** The namespace was still valid for the new level; the restore was restarted in place. */
        NAMESPACE_RESTARTED,
        /** The namespace is no longer valid; the caller must checkpoint and clear it. */
        NAMESPACE_RESET
    }

    record Drained(String dimension, List<AtmosphereClientCache.Update> updates) { }

    private final AtmosphereCacheWriter saver;
    private final Deque<AtmosphereClientCache.Update> pendingRestore = new ArrayDeque<>();
    private String pendingRestoreNamespace;
    private String pendingRestoreDimension;
    /** Monotonic token for the in-flight restore; bumped on every begin/cancel so a stale disk read
     *  from a superseded connection/world (even one sharing the same namespace, e.g. a disconnect
     *  and reconnect to the same world) can never be admitted into a later session. */
    private long restoreGeneration;
    private long pendingRestoreGeneration = -1;

    AtmosphereRestoreCoordinator(AtmosphereCacheWriter saver) { this.saver = saver; }

    /**
     * Called whenever the observed level identity may have changed. {@code cacheNamespace}/
     * {@code cacheDimension} are the caller's currently-adopted namespace (may be null). When the
     * new level is non-null and the namespace is still valid for it (same dimension), the old
     * generation is replaced by a fresh restore for that same namespace rather than merely
     * canceled — otherwise, since a namespace change is normally what (re)starts a restore, a
     * namespace-preserving level swap (a packet arriving before the world exists, then the world
     * loading; or a same-dimension level replacement without an intervening disconnect) would never
     * restart one, and historical chunks would never load for that session.
     */
    synchronized LevelChange syncLevel(Object previousLevel, Object currentLevel, String currentDimension,
                                       String cacheNamespace, String cacheDimension) {
        if (Objects.equals(previousLevel, currentLevel)) return LevelChange.UNCHANGED;
        if (currentLevel != null && cacheNamespace != null && Objects.equals(currentDimension, cacheDimension)) {
            beginRestore(cacheNamespace, cacheDimension);
            return LevelChange.NAMESPACE_RESTARTED;
        }
        cancelRestore();
        return LevelChange.NAMESPACE_RESET;
    }

    /** Kicks off the historical disk read off the caller's thread; never blocks the caller. */
    void beginRestore(String namespace, String dimension) {
        long generation;
        synchronized (this) {
            generation = ++restoreGeneration;
            pendingRestore.clear();
            pendingRestoreNamespace = namespace;
            pendingRestoreDimension = dimension;
            pendingRestoreGeneration = generation;
        }
        saver.loadAsync(namespace).whenComplete((updates, failure) -> {
            if (failure != null) {
                LogUtils.getLogger().warn("Cannot load disposable atmosphere cache", failure);
                return;
            }
            synchronized (this) {
                // A later namespace switch, disconnect/reconnect (even to the same world/namespace)
                // or level replacement bumps the generation and supersedes this result.
                if (generation == pendingRestoreGeneration) pendingRestore.addAll(updates);
            }
        });
    }

    void cancelRestore() {
        synchronized (this) {
            restoreGeneration++;
            pendingRestore.clear();
            pendingRestoreNamespace = null;
            pendingRestoreDimension = null;
            pendingRestoreGeneration = -1;
        }
    }

    /** At most one bounded batch, only if the pending restore still targets the given namespace. */
    synchronized Drained drainBatch(String cacheNamespace) {
        if (pendingRestore.isEmpty() || !Objects.equals(pendingRestoreNamespace, cacheNamespace)) return null;
        var batch = new ArrayList<AtmosphereClientCache.Update>(Math.min(RESTORE_BATCH_SIZE, pendingRestore.size()));
        for (int i = 0; i < RESTORE_BATCH_SIZE && !pendingRestore.isEmpty(); i++) batch.add(pendingRestore.poll());
        return new Drained(pendingRestoreDimension, batch);
    }
}
