package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.mojang.logging.LogUtils;

/** Coalesces checkpoints per world; failures remain pending for the next retry. */
final class AtmosphereCacheWriter {
    private final AtmosphereDiskCache disk;
    private final Map<String, List<AtmosphereClientCache.Update>> pending = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Atmosphere visual cache");
        thread.setDaemon(true);
        return thread;
    });
    private boolean running;

    AtmosphereCacheWriter(AtmosphereDiskCache disk) { this.disk = disk; }

    synchronized void submit(String namespace, List<AtmosphereClientCache.Update> cells) {
        pending.put(namespace, List.copyOf(cells));
        retry();
    }

    synchronized List<AtmosphereClientCache.Update> load(String namespace) throws IOException {
        var latest = pending.get(namespace);
        return latest == null ? disk.load(namespace) : latest;
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
            List<AtmosphereClientCache.Update> cells;
            synchronized (this) {
                namespace = pending.keySet().stream().filter(key -> !failed.contains(key)).findFirst().orElse(null);
                if (namespace == null) { running = false; return; }
                cells = pending.get(namespace);
            }
            try { disk.save(namespace, cells); }
            catch (IOException | RuntimeException failure) {
                LogUtils.getLogger().warn("Cannot save disposable atmosphere cache; checkpoint retained for retry", failure);
                failed.add(namespace);
                continue;
            }
            synchronized (this) {
                if (pending.get(namespace) == cells) pending.remove(namespace);
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
