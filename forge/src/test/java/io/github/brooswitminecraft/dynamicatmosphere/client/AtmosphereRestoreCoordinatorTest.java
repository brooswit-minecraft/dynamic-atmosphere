package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises the restore lifecycle (packet-before-world, same-dimension level replacement,
 * superseded generations) directly through the coordinator used by production — no Minecraft
 * world required, since level identity here is a plain Object and dimension a plain String.
 */
class AtmosphereRestoreCoordinatorTest {
    @TempDir Path root;

    private static <T> T poll(Supplier<T> probe, Predicate<T> ready) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        T value = probe.get();
        while (!ready.test(value) && System.nanoTime() < deadline) {
            Thread.sleep(5);
            value = probe.get();
        }
        return value;
    }

    @Test void packetBeforeWorldThenWorldLoadingRestartsTheRestoreForTheSameNamespace() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        var coordinator = new AtmosphereRestoreCoordinator(writer);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        var update = new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(0, 0, 0), 500, 1000);
        disk.save(namespace, List.of(update));
        try {
            // A packet arrives (and the namespace is adopted, starting a restore) before the client
            // world object exists.
            coordinator.beginRestore(namespace, "overworld");
            // The world then finishes loading: syncLevel sees null -> a real level, same namespace.
            var change = coordinator.syncLevel(null, new Object(), "overworld", namespace, "overworld");
            assertEquals(AtmosphereRestoreCoordinator.LevelChange.NAMESPACE_RESTARTED, change);
            var drained = poll(() -> coordinator.drainBatch(namespace), batch -> batch != null);
            assertEquals(new AtmosphereRestoreCoordinator.Drained("overworld", List.of(update)), drained);
        } finally { writer.shutdown(); }
    }

    @Test void sameDimensionLevelReplacementRestartsRatherThanPermanentlyCancelsTheRestore() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        var coordinator = new AtmosphereRestoreCoordinator(writer);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        var update = new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(0, 0, 0), 500, 1000);
        disk.save(namespace, List.of(update));
        var levelA = new Object();
        try {
            // No namespace adopted yet: the first-ever level sync always resets.
            assertEquals(AtmosphereRestoreCoordinator.LevelChange.NAMESPACE_RESET,
                coordinator.syncLevel(null, levelA, "overworld", null, null));
            coordinator.beginRestore(namespace, "overworld"); // receive() would adopt the namespace here.
            // A disconnect/reconnect (or any other lifecycle event) replaces the level object while
            // staying in the same dimension/namespace.
            var levelB = new Object();
            var change = coordinator.syncLevel(levelA, levelB, "overworld", namespace, "overworld");
            assertEquals(AtmosphereRestoreCoordinator.LevelChange.NAMESPACE_RESTARTED, change);
            var drained = poll(() -> coordinator.drainBatch(namespace), batch -> batch != null);
            assertEquals(List.of(update), drained.updates());
        } finally { writer.shutdown(); }
    }

    @Test void aSupersededGenerationsLateCompletionIsNeverAdmitted() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        var coordinator = new AtmosphereRestoreCoordinator(writer);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        disk.save(namespace, List.of(new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(0, 0, 0), 500, 1000)));
        try {
            coordinator.beginRestore(namespace, "overworld"); // generation 1, in flight.
            coordinator.cancelRestore(); // generation 2 supersedes it before (or after) it completes.
            // Give generation 1's disk read every chance to finish and attempt to apply; whichever
            // order the cancel and the completion actually land in, it must never become drainable.
            Thread.sleep(200);
            assertNull(coordinator.drainBatch(namespace));
        } finally { writer.shutdown(); }
    }

    @Test void drainBatchIgnoresAPendingRestoreForADifferentNamespace() throws Exception {
        var disk = new AtmosphereDiskCache(root);
        var writer = new AtmosphereCacheWriter(disk);
        var coordinator = new AtmosphereRestoreCoordinator(writer);
        String namespace = disk.namespace("server", UUID.randomUUID(), "overworld");
        var update = new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(0, 0, 0), 500, 1000);
        disk.save(namespace, List.of(update));
        try {
            coordinator.beginRestore(namespace, "overworld");
            // Repeatedly probing with the WRONG namespace must never drain it, however long we wait.
            Thread.sleep(200);
            assertNull(coordinator.drainBatch("a-completely-different-namespace"));
            var drained = poll(() -> coordinator.drainBatch(namespace), batch -> batch != null);
            assertEquals(List.of(update), drained.updates());
        } finally { writer.shutdown(); }
    }
}
