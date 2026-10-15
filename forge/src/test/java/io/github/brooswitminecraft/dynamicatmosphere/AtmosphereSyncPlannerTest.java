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
    void trackedChunkMembershipGainAtSameViewRadiusIsDeltaIncludingNewChunkCells() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        var oldChunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        var gainedChunk = new AtmosphereSyncPlanner.Chunk(1, 0);
        var oldCell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        var gainedCell = new AtmosphereSyncPlanner.Cell(16, 0, 0, 400, 1000);
        planner.plan("player", "overworld", 8, List.of(oldChunk), List.of(oldCell));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "overworld", 8, List.of(oldChunk, gainedChunk), List.of(oldCell, gainedCell));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertFalse(updates.getFirst().snapshotEnd());
        assertEquals(List.of(gainedCell), updates.getFirst().cells());
    }

    @Test
    void leavingTrackedChunksAtSameViewRadiusIsDeltaThatZerosTheirCells() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        var retainedChunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        var departedChunk = new AtmosphereSyncPlanner.Chunk(4, 0);
        var retainedCell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        var departedCell = new AtmosphereSyncPlanner.Cell(16, 0, 0, 500, 1000);
        planner.plan("player", "overworld", 8, List.of(retainedChunk, departedChunk),
            List.of(retainedCell, departedCell));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "overworld", 8, List.of(retainedChunk), List.of(retainedCell));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertFalse(updates.getFirst().snapshotEnd());
        assertEquals(List.of(new AtmosphereSyncPlanner.Cell(
            departedCell.x(), departedCell.y(), departedCell.z(), 0, 0)), updates.getFirst().cells());
    }

    @Test
    void oneRingStepBoundaryCrossingAtSameViewRadiusIsDeltaWithOnlyGainedAndLostChunkCells() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        var retainedChunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        var lostChunk = new AtmosphereSyncPlanner.Chunk(-1, 0);
        var gainedChunk = new AtmosphereSyncPlanner.Chunk(1, 0);
        var retainedCell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        var lostCell = new AtmosphereSyncPlanner.Cell(-16, 0, 0, 300, 1000);
        var gainedCell = new AtmosphereSyncPlanner.Cell(16, 0, 0, 400, 1000);
        planner.plan("player", "overworld", 8, List.of(retainedChunk, lostChunk),
            List.of(retainedCell, lostCell));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "overworld", 8, List.of(retainedChunk, gainedChunk), List.of(retainedCell, gainedCell));

        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().reset());
        assertEquals(List.of(
            new AtmosphereSyncPlanner.Cell(lostCell.x(), lostCell.y(), lostCell.z(), 0, 0),
            gainedCell
        ), updates.getFirst().cells());
    }

    @Test
    void viewDistanceChangeAtSameChunkSetForcesFullResetSnapshot() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        var chunk = new AtmosphereSyncPlanner.Chunk(0, 0);
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.plan("player", "overworld", 8, List.of(chunk), List.of(cell));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "overworld", 16, List.of(chunk), List.of(cell));

        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().reset());
        assertTrue(updates.getFirst().snapshotEnd());
        assertEquals(List.of(chunk), updates.getFirst().authoritativeChunks());
        assertEquals(List.of(cell), updates.getFirst().cells());
    }

    @Test
    void dimensionChangeStillForcesFullResetSnapshotRegardlessOfViewRadius() {
        AtmosphereSyncPlanner<String, String> planner = new AtmosphereSyncPlanner<>(512);
        var cell = new AtmosphereSyncPlanner.Cell(0, 0, 0, 250, 1000);
        planner.plan("player", "overworld", 8, List.of(new AtmosphereSyncPlanner.Chunk(0, 0)), List.of(cell));

        List<AtmosphereSyncPlanner.Update<String>> updates = planner.plan(
            "player", "nether", 8, List.of(new AtmosphereSyncPlanner.Chunk(0, 0)), List.of(cell));

        assertEquals(2, updates.size());
        assertTrue(updates.get(0).reset());
        assertTrue(updates.get(1).reset());
        assertEquals("nether", updates.get(1).dimension());
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

    @Test
    void chunkScopeIsOnlyRequestedForSnapshots() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var requests = new int[1];
        var cell = new AtmosphereSyncPlanner.Cell(1, 0, 0, 5, 10);
        java.util.function.Supplier<List<AtmosphereSyncPlanner.Chunk>> scope = () -> {
            requests[0]++;
            return List.of(new AtmosphereSyncPlanner.Chunk(0, 0));
        };

        assertTrue(planner.plan("player", "overworld", 8, scope, List.of(cell)).getFirst().reset());
        assertEquals(1, requests[0]);

        assertTrue(planner.plan("player", "overworld", 8, scope, List.of(cell)).isEmpty());
        var changed = new AtmosphereSyncPlanner.Cell(1, 0, 0, 6, 10);
        assertEquals(List.of(changed), planner.plan("player", "overworld", 8, scope, List.of(changed)).getFirst().cells());
        assertEquals(1, requests[0]);

        assertTrue(planner.plan("player", "overworld", 9, scope, List.of(changed)).getFirst().reset());
        assertEquals(2, requests[0]);
    }
}
