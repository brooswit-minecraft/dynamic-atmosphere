package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import com.mojang.logging.LogUtils;

/** Coalesces per-chunk checkpoint deltas per world; failures remain pending for the next retry. */
final class AtmosphereCacheWriter {
    private final AtmosphereDiskCache disk;
    private final Map<String, Map<AtmosphereClientCache.Chunk, List<AtmosphereClientCache.Update>>> pending = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Atmosphere visual cache");
        thread.setDaemon(true);
        return thread;
    });
    private boolean running;
    /** Test-only synchronization seams (default no-op): beforeSave fires on the writer thread right
     *  before each disk.saveDelta attempt, letting a test deterministically inject a concurrent
     *  submit() mid-write; afterAttempt fires right after each attempt resolves, success or failure,
     *  letting a test wait for a drain pass without sleeping. */
    volatile Runnable beforeSave = () -> { };
    volatile Runnable afterAttempt = () -> { };

    AtmosphereCacheWriter(AtmosphereDiskCache disk) { this.disk = disk; }

    /** Merge dirty-chunk deltas (an empty chunk list is an explicit removal) into the pending checkpoint. */
    synchronized void submit(String namespace, Map<AtmosphereClientCache.Chunk, List<AtmosphereClientCache.Update>> deltas) {
        if (deltas.isEmpty()) return;
        pending.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>()).putAll(deltas);
        retry();
    }

    /** Same-session reconnect view: disk state with any not-yet-flushed deltas overlaid. */
    List<AtmosphereClientCache.Update> load(String namespace) throws IOException {
        Map<AtmosphereClientCache.Chunk, List<AtmosphereClientCache.Update>> deltas;
        synchronized (this) {
            var map = pending.get(namespace);
            deltas = (map == null || map.isEmpty()) ? Map.of() : Map.copyOf(map);
        }
        // Disk I/O deliberately runs without the writer monitor held, so it never blocks a
        // concurrent submit()/retry() from the client thread.
        var base = disk.load(namespace);
        if (deltas.isEmpty()) return base;
        var result = new ArrayList<AtmosphereClientCache.Update>();
        for (var update : base) {
            if (!deltas.containsKey(AtmosphereDiskCache.chunkOf(update.cell()))) result.add(update);
        }
        for (var cells : deltas.values()) result.addAll(cells);
        return result;
    }

    /** Off-thread equivalent of {@link #load}; never performs disk I/O on the caller's thread. */
    CompletableFuture<List<AtmosphereClientCache.Update>> loadAsync(String namespace) {
        var future = new CompletableFuture<List<AtmosphereClientCache.Update>>();
        try {
            executor.execute(() -> {
                try { future.complete(load(namespace)); }
                catch (IOException | RuntimeException failure) { future.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException shuttingDown) {
            future.completeExceptionally(shuttingDown);
        }
        return future;
    }

    synchronized void retry() {
        if (!running && !pending.isEmpty() && !executor.isShutdown()) {
            running = true;
            executor.execute(this::drain);
        }
    }

    private void drain() {
        var failed = new java.util.HashSet<String>();
        while (true) {
            String namespace;
            Map<AtmosphereClientCache.Chunk, List<AtmosphereClientCache.Update>> snapshot;
            synchronized (this) {
                namespace = pending.keySet().stream().filter(key -> !failed.contains(key)).findFirst().orElse(null);
                if (namespace == null) { running = false; return; }
                // Detach an immutable in-flight generation so a concurrent submit() can keep
                // mutating/replacing entries in the live pending map while disk.saveDelta runs below
                // without the writer monitor held.
                snapshot = Map.copyOf(pending.get(namespace));
            }
            beforeSave.run();
            try { disk.saveDelta(namespace, snapshot); }
            catch (IOException | RuntimeException failure) {
                LogUtils.getLogger().warn("Cannot save disposable atmosphere cache; checkpoint retained for retry", failure);
                failed.add(namespace);
                afterAttempt.run();
                continue;
            }
            synchronized (this) {
                var current = pending.get(namespace);
                if (current != null) {
                    // Acknowledge only the exact deltas we saved: a chunk re-submitted (same key,
                    // new value reference) while the write was in flight keeps its newer delta pending.
                    for (var entry : snapshot.entrySet()) {
                        if (current.get(entry.getKey()) == entry.getValue()) current.remove(entry.getKey());
                    }
                    if (current.isEmpty()) pending.remove(namespace);
                }
            }
            afterAttempt.run();
        }
    }

    void shutdown() {
        retry();
        executor.shutdown();
        try { executor.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
