package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import io.github.brooswitminecraft.dynamicatmosphere.client.AtmosphereClientCache;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereSyncPlannerTest {

    @Test
    void largeSnapshotCommitsEntireClientViewOnlyAtLastPacket() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cache = new AtmosphereClientCache();
        cache.changeDimension("overworld");
        var cells = IntStream.range(0, 4609)
            .mapToObj(i -> new AtmosphereSyncPlanner.Cell(i, 0, 0, 500, 1000)).toList();
        var packets = planner.plan("player", "overworld", cells);
        for (var packet : packets) {
            cache.apply(packet.dimension(), packet.reset(), packet.snapshotEnd(), packet.cells().stream()
                .map(cell -> new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(cell.x(), cell.y(), cell.z()),
                    cell.amount(), cell.capacity())).toList());
            assertEquals(packet.snapshotEnd() ? 4609 : 0, cache.size());
        }
        for (int i = 0; i < 10; i++) cache.advance();
        planner.reset("player");
        for (var packet : planner.plan("player", "overworld", cells)) {
            cache.apply(packet.dimension(), packet.reset(), packet.snapshotEnd(), packet.cells().stream()
                .map(cell -> new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(cell.x(), cell.y(), cell.z()),
                    cell.amount(), cell.capacity())).toList());
            assertEquals(4609, cache.size());
            assertTrue(cache.visible(0).stream().allMatch(cell -> cell.amount() == 500));
        }
    }

    @Test
    void capacityOnlyChangeIsSentEvenWhenAmountIsUnchanged() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        planner.plan("player", "overworld", List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000)));
        var changed = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 500);
        var updates = planner.plan("player", "overworld", List.of(changed));
        assertEquals(List.of(changed), updates.getFirst().cells());
        assertFalse(updates.getFirst().reset());
        assertTrue(planner.plan("player", "overworld", List.of(changed)).isEmpty());
    }

    @Test
    void firstPlanIsSnapshotAndUnchangedPlanIsSilent() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        List<AtmosphereSyncPlanner.Cell> cells = List.of(new AtmosphereSyncPlanner.Cell(-1, 2, 3, 50, 1000));

        List<AtmosphereSyncPlanner.Update<String>> snapshot = planner.plan("player", "overworld", cells);
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.getFirst().reset());
        assertTrue(snapshot.getFirst().snapshotEnd());
        assertEquals(cells, snapshot.getFirst().cells());
        assertTrue(planner.plan("player", "overworld", cells).isEmpty());
    }

    @Test
    void deltaSendsRemovalsBeforeChangedAndAddedCells() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        planner.plan("player", "overworld", List.of(
            new AtmosphereSyncPlanner.Cell(0, 0, 0, 10, 1000),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 20, 1000)
        ));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan("player", "overworld", List.of(
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 30, 1000),
            new AtmosphereSyncPlanner.Cell(2, 0, 0, 40, 1000)
        ));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertFalse(updates.getFirst().snapshotEnd());
        assertEquals(List.of(
            new AtmosphereSyncPlanner.Cell(0, 0, 0, 0, 0),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 30, 1000),
            new AtmosphereSyncPlanner.Cell(2, 0, 0, 40, 1000)
        ), updates.getFirst().cells());
    }

    @Test
    void dimensionChangeClearsOldDimensionAndSnapshotsNewOne() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        planner.plan("player", "overworld", List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 10, 1000)));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "nether", List.of(new AtmosphereSyncPlanner.Cell(4, 5, 6, 20, 1000)));

        assertEquals(2, updates.size());
        assertEquals("overworld", updates.get(0).dimension());
        assertTrue(updates.get(0).reset());
        assertTrue(updates.get(0).snapshotEnd());
        assertTrue(updates.get(0).cells().isEmpty());
        assertEquals("nether", updates.get(1).dimension());
        assertTrue(updates.get(1).reset());
    }

    @Test
    void resetAndDisconnectBothRequireFreshSnapshots() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        List<AtmosphereSyncPlanner.Cell> cells = List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 10, 1000));
        planner.plan("player", "overworld", cells);

        planner.reset("player");
        assertTrue(planner.plan("player", "overworld", cells).getFirst().reset());
        planner.disconnect("player");
        assertTrue(planner.plan("player", "overworld", cells).getFirst().reset());
    }

    @Test
    void batchesAllTrackedCellsAndTheirRemovalsWithoutDistanceCutoff() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        List<AtmosphereSyncPlanner.Cell> cells = IntStream.range(0, 4_609)
            .mapToObj(index -> new AtmosphereSyncPlanner.Cell(index * 100, index % 32, -index * 100, 250, 1000))
            .toList();

        List<AtmosphereSyncPlanner.Update<String>> snapshot = planner.plan("player", "overworld", cells);
        assertEquals(10, snapshot.size());
        assertTrue(snapshot.getFirst().reset());
        assertFalse(snapshot.getFirst().snapshotEnd());
        assertTrue(snapshot.getLast().snapshotEnd());
        assertEquals(1, snapshot.stream().filter(AtmosphereSyncPlanner.Update::snapshotEnd).count());
        assertTrue(snapshot.stream().skip(1).noneMatch(AtmosphereSyncPlanner.Update::reset));
        assertTrue(snapshot.stream().allMatch(update -> update.cells().size() <= 512));
        assertEquals(cells, snapshot.stream().flatMap(update -> update.cells().stream()).toList());

        List<AtmosphereSyncPlanner.Chunk> authoritativeChunks = cells.stream()
            .map(cell -> new AtmosphereSyncPlanner.Chunk(
                AtmosphereGridLayout.chunkCoordinate(cell.x()),
                AtmosphereGridLayout.chunkCoordinate(cell.z())))
            .distinct()
            .toList();
        List<AtmosphereSyncPlanner.Update<String>> removals =
            planner.plan("player", "overworld", authoritativeChunks, List.of());
        assertEquals(10, removals.size());
        assertTrue(removals.stream().noneMatch(AtmosphereSyncPlanner.Update::reset));
        assertTrue(removals.stream().noneMatch(AtmosphereSyncPlanner.Update::snapshotEnd));
        assertTrue(removals.stream().allMatch(update -> update.cells().size() <= 512));
        List<AtmosphereSyncPlanner.Cell> removedCells = removals.stream()
            .flatMap(update -> update.cells().stream())
            .toList();
        assertEquals(4_609, removedCells.size());
        assertTrue(removedCells.stream().allMatch(cell -> cell.amount() == 0 && cell.capacity() == 0));
        Set<String> removedCoordinates = removedCells.stream()
            .map(cell -> cell.x() + ":" + cell.y() + ":" + cell.z())
            .collect(Collectors.toSet());
        Set<String> expectedCoordinates = cells.stream()
            .map(cell -> cell.x() + ":" + cell.y() + ":" + cell.z())
            .collect(Collectors.toSet());
        assertEquals(expectedCoordinates, removedCoordinates);
    }

    @Test
    void trackedChunkMembershipChangeForcesScopedSnapshotIncludingEmptyChunks() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        var oldChunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        var freshEmptyChunk = new AtmosphereSyncPlanner.Chunk(1, 0);
        var oldCell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.plan("player", "overworld", List.of(oldChunk), List.of(oldCell));

        List<AtmosphereSyncPlanner.Update<String>> snapshot = planner.plan(
            "player", "overworld", List.of(oldChunk, freshEmptyChunk), List.of());

        assertEquals(1, snapshot.size());
        assertTrue(snapshot.getFirst().reset());
        assertTrue(snapshot.getFirst().snapshotEnd());
        assertEquals(List.of(oldChunk, freshEmptyChunk), snapshot.getFirst().authoritativeChunks());
        assertTrue(snapshot.getFirst().cells().isEmpty());
    }

    @Test
    void leavingTrackedChunksPreservesTheirCachedCells() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        var retainedChunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        var departedChunk = new AtmosphereSyncPlanner.Chunk(4, 0);
        var retainedCell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        var departedCell = new AtmosphereSyncPlanner.Cell(16, 0, 0, 500, 1000);
        planner.plan("player", "overworld", List.of(retainedChunk, departedChunk),
            List.of(retainedCell, departedCell));

        List<AtmosphereSyncPlanner.Update<String>> snapshot = planner.plan(
            "player", "overworld", List.of(retainedChunk), List.of(retainedCell));

        assertEquals(1, snapshot.size());
        assertTrue(snapshot.getFirst().reset());
        assertTrue(snapshot.getFirst().snapshotEnd());
        assertEquals(List.of(retainedChunk), snapshot.getFirst().authoritativeChunks());
        assertEquals(List.of(retainedCell), snapshot.getFirst().cells());
        assertTrue(snapshot.stream().flatMap(update -> update.cells().stream())
            .noneMatch(cell -> cell.x() == departedCell.x() && cell.amount() == 0));
    }

    @Test
    void snapshotEndsOnlyAfterChunkAndCellStreamsAreBothComplete() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        List<AtmosphereSyncPlanner.Chunk> chunks = IntStream.range(0, 1_025)
            .mapToObj(index -> new AtmosphereSyncPlanner.Chunk(index, -index))
            .toList();
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 100, 1000);

        List<AtmosphereSyncPlanner.Update<String>> snapshot =
            planner.plan("player", "overworld", chunks, List.of(cell));

        assertEquals(3, snapshot.size());
        assertEquals(512, snapshot.get(0).authoritativeChunks().size());
        assertEquals(512, snapshot.get(1).authoritativeChunks().size());
        assertEquals(1, snapshot.get(2).authoritativeChunks().size());
        assertEquals(List.of(cell), snapshot.getFirst().cells());
        assertTrue(snapshot.get(1).cells().isEmpty());
        assertFalse(snapshot.getFirst().snapshotEnd());
        assertFalse(snapshot.get(1).snapshotEnd());
        assertTrue(snapshot.getLast().snapshotEnd());
    }
}
