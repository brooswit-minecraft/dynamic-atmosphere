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

    AtmosphereCacheWriter(AtmosphereDiskCache disk) { this.disk = disk; }

    /** Merge dirty-chunk deltas (an empty chunk list is an explicit removal) into the pending checkpoint. */
    synchronized void submit(String namespace, Map<AtmosphereClientCache.Chunk, List<AtmosphereClientCache.Update>> deltas) {
        if (deltas.isEmpty()) return;
        pending.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>()).putAll(deltas);
        retry();
    }

    /** Same-session reconnect view: disk state with any not-yet-flushed deltas overlaid. Blocks on disk I/O. */
    synchronized List<AtmosphereClientCache.Update> load(String namespace) throws IOException {
        var deltas = pending.get(namespace);
        var base = disk.load(namespace);
        if (deltas == null || deltas.isEmpty()) return base;
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
            Map<AtmosphereClientCache.Chunk, List<AtmosphereClientCache.Update>> deltas;
            synchronized (this) {
                namespace = pending.keySet().stream().filter(key -> !failed.contains(key)).findFirst().orElse(null);
                if (namespace == null) { running = false; return; }
                deltas = pending.get(namespace);
            }
            try { disk.saveDelta(namespace, deltas); }
            catch (IOException | RuntimeException failure) {
                LogUtils.getLogger().warn("Cannot save disposable atmosphere cache; checkpoint retained for retry", failure);
                failed.add(namespace);
                continue;
            }
            synchronized (this) {
                if (pending.get(namespace) == deltas) pending.remove(namespace);
            }
        }
    }

    void shutdown() {
        retry();
        executor.shutdown();
        try { executor.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
