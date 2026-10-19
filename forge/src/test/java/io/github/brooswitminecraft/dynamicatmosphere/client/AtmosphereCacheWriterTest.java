package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
