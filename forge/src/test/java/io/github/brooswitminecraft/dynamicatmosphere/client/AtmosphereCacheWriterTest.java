package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
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
        var old = List.of(new AtmosphereClientCache.Update(cell, 100, 1000));
        var latest = List.of(new AtmosphereClientCache.Update(cell, 700, 1000));
        try {
            writer.submit(first, old);
            writer.submit(second, old);
            writer.submit(first, latest);
            assertEquals(latest, writer.load(first));
        } finally { writer.shutdown(); }
        assertEquals(latest, disk.load(first));
        assertEquals(old, disk.load(second));
    }
}
