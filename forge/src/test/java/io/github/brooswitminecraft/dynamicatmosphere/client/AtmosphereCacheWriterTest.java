package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereCacheWriterTest {
    @TempDir Path root;

    @Test void reconnectAndDiskBothSeeLatestCheckpointWithoutDroppingOtherWorld() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        String first = disk.namespace("server", UUID.randomUUID(), "overworld");
        String second = disk.namespace("server", UUID.randomUUID(), "nether");
        var cell = new AtmosphereClientCache.Cell(0, 0, 0);
        var chunk = AtmosphereDiskCache.chunkOf(cell);
        var old = List.of(new AtmosphereClientCache.Update(cell, 100, 1000));
        var latest = List.of(new AtmosphereClientCache.Update(cell, 700, 1000));
        try {
            writer.submit(first, Map.of(chunk, old));
            writer.submit(second, Map.of(chunk, old));
            writer.submit(first, Map.of(chunk, latest));
            assertEquals(latest, writer.load(first));
        } finally { writer.shutdown(); }
        assertEquals(latest, disk.load(first));
        assertEquals(old, disk.load(second));
    }

    @Test void explicitRemovalDeletesTheChunkOnDiskAndOnReconnect() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        var cell = new AtmosphereClientCache.Cell(4, 0, 0);
        var chunk = AtmosphereDiskCache.chunkOf(cell);
        try {
            writer.submit(namespace, Map.of(chunk, List.of(new AtmosphereClientCache.Update(cell, 300, 1000))));
            writer.submit(namespace, Map.of(chunk, List.of()));
            assertEquals(List.of(), writer.load(namespace));
        } finally { writer.shutdown(); }
        assertEquals(List.of(), disk.load(namespace));
    }

    @Test void loadAsyncNeverRunsDiskIOOnTheCallingThread() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        var cell = new AtmosphereClientCache.Cell(0, 0, 0);
        var update = new AtmosphereClientCache.Update(cell, 300, 1000);
        try {
            writer.submit(namespace, Map.of(AtmosphereDiskCache.chunkOf(cell), List.of(update)));
            assertEquals(List.of(update), writer.loadAsync(namespace).get());
        } finally { writer.shutdown(); }
    }

    @Test void concurrentSubmitDuringAnInFlightSaveKeepsTheNewerDeltaPendingAndBothEventuallyPersist() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        var cell = new AtmosphereClientCache.Cell(0, 0, 0);
        var chunk = AtmosphereDiskCache.chunkOf(cell);
        var first = List.of(new AtmosphereClientCache.Update(cell, 100, 1000));
        var second = List.of(new AtmosphereClientCache.Update(cell, 900, 1000));
        var readyToRace = new CountDownLatch(1);
        var proceedWithFirstSave = new CountDownLatch(1);
        var bothAttemptsDone = new CountDownLatch(2);
        writer.beforeSave = () -> {
            readyToRace.countDown();
            try { proceedWithFirstSave.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            writer.beforeSave = () -> { }; // Only the in-flight save of `first` needs to pause.
        };
        writer.afterAttempt = bothAttemptsDone::countDown;
        try {
            writer.submit(namespace, Map.of(chunk, first));
            assertTrue(readyToRace.await(5, TimeUnit.SECONDS)); // `first`'s save is now in flight.
            // A second submit for the SAME chunk arrives mid-write: it must replace the pending
            // delta without corrupting the in-flight save, and must not be acknowledged (dropped)
            // just because `first`'s save succeeds.
            writer.submit(namespace, Map.of(chunk, second));
            proceedWithFirstSave.countDown();
            assertTrue(bothAttemptsDone.await(5, TimeUnit.SECONDS));
            assertEquals(second, writer.load(namespace));
        } finally { writer.shutdown(); }
        assertEquals(second, disk.load(namespace));
    }

    @Test void saveFailureKeepsTheCheckpointPendingUntilRetrySucceeds() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        var cell = new AtmosphereClientCache.Cell(0, 0, 0);
        var chunk = AtmosphereDiskCache.chunkOf(cell);
        var update = new AtmosphereClientCache.Update(cell, 300, 1000);
        Path blocked = root.resolve(namespace);
        Files.write(blocked, new byte[0]); // A plain file sits where the cache directory must go.
        var firstAttemptDone = new CountDownLatch(1);
        writer.afterAttempt = firstAttemptDone::countDown;
        try {
            writer.submit(namespace, Map.of(chunk, List.of(update)));
            assertTrue(firstAttemptDone.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(update), writer.load(namespace)); // Never silently dropped on failure.
            assertTrue(disk.load(namespace).isEmpty()); // Nothing actually reached disk.

            var secondAttemptDone = new CountDownLatch(1);
            writer.afterAttempt = secondAttemptDone::countDown;
            Files.delete(blocked);
            // retry() is a no-op while the first (failing) drain pass hasn't yet flipped back to
            // idle; poll it rather than racing that internal transition.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (secondAttemptDone.getCount() > 0 && System.nanoTime() < deadline) {
                writer.retry();
                Thread.sleep(5);
            }
            assertTrue(secondAttemptDone.await(1, TimeUnit.SECONDS));
            assertEquals(List.of(update), disk.load(namespace));
        } finally { writer.shutdown(); }
    }
}
