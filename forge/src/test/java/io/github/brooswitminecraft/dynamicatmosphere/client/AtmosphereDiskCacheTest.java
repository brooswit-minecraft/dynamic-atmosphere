package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereDiskCacheTest {
    @TempDir Path root;

    @Test void persistsAcrossInstancesAndSeparatesWorldsAndServers() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        UUID world = UUID.randomUUID();
        String key = disk.namespace("server", world, "overworld");
        var data = List.of(new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(-1, 12, 8), 700, 500));
        disk.save(key, data);
        assertEquals(data, new AtmosphereDiskCache(root).load(key));
        assertTrue(disk.load(disk.namespace("another", world, "overworld")).isEmpty());
        assertTrue(disk.load(disk.namespace("server", UUID.randomUUID(), "overworld")).isEmpty());
        assertTrue(disk.load(disk.namespace("server", world, "nether")).isEmpty());
        assertFalse(key.contains("server"));
    }

    @Test void unchangedChunksAreNotRewrittenAndEmptyDataClearsThem() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        String key = disk.namespace("server", UUID.randomUUID(), "overworld");
        var data = List.of(new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(0, 12, 0), 700, 500));
        disk.save(key, data);
        Path chunk = root.resolve(key).resolve("0_0.bin");
        var marker = java.nio.file.attribute.FileTime.fromMillis(123456);
        Files.setLastModifiedTime(chunk, marker);
        disk.save(key, data);
        assertEquals(marker, Files.getLastModifiedTime(chunk));
        disk.save(key, List.of());
        assertTrue(disk.load(key).isEmpty());
        assertFalse(Files.exists(chunk));
    }

    @Test void corruptChunksDoNotPreventLoadingGoodChunks() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        String key = disk.namespace("server", UUID.randomUUID(), "overworld");
        var good = new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(0, 12, 0), 700, 500);
        disk.save(key, List.of(good));
        Files.write(root.resolve(key).resolve("1_0.bin"), new byte[] {1, 2, 3});
        assertEquals(List.of(good), disk.load(key));
        assertThrows(IllegalArgumentException.class, () -> disk.load("../../escape"));
    }
}
