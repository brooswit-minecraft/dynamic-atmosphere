package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quantifies the MINECRAFT-242 payload reduction for one ordinary chunk-boundary crossing (same dimension,
 * same view radius), for both planners. "Before" numbers are produced by forcing each planner's own
 * full-reset path on the exact post-shift view via {@code reset(player)} — that is exactly what every
 * crossing did pre-fix, since any chunk-set change used to force {@code reset=true} unconditionally.
 * "After" numbers are the real {@code plan()} result for the same shift with an unchanged view radius.
 */
class ChunkBoundaryCrossingMeasurementTest {

    private static final int VIEW_RADIUS = 8;

    @Test
    void atmosphereSyncPlannerOneRingStepShiftAtViewRadius8() {
        List<AtmosphereSyncPlanner.Chunk> beforeChunks = ring(0);
        List<AtmosphereSyncPlanner.Cell> beforeCells = atmosphereCellsFor(beforeChunks);
        List<AtmosphereSyncPlanner.Chunk> afterChunks = ring(1);
        List<AtmosphereSyncPlanner.Cell> afterCells = atmosphereCellsFor(afterChunks);

        AtmosphereSyncPlanner<String, String> oldBehavior = new AtmosphereSyncPlanner<>(10_000);
        oldBehavior.plan("player", "overworld", VIEW_RADIUS, beforeChunks, beforeCells);
        oldBehavior.reset("player");
        List<AtmosphereSyncPlanner.Update<String>> oldUpdates =
            oldBehavior.plan("player", "overworld", VIEW_RADIUS, afterChunks, afterCells);
        int oldChunkCount = oldUpdates.stream().mapToInt(u -> u.authoritativeChunks().size()).sum();
        int oldCellCount = oldUpdates.stream().mapToInt(u -> u.cells().size()).sum();

        AtmosphereSyncPlanner<String, String> newBehavior = new AtmosphereSyncPlanner<>(10_000);
        newBehavior.plan("player", "overworld", VIEW_RADIUS, beforeChunks, beforeCells);
        List<AtmosphereSyncPlanner.Update<String>> newUpdates =
            newBehavior.plan("player", "overworld", VIEW_RADIUS, afterChunks, afterCells);
        int newChunkCount = newUpdates.stream().mapToInt(u -> u.authoritativeChunks().size()).sum();
        int newCellCount = newUpdates.stream().mapToInt(u -> u.cells().size()).sum();

        report("AtmosphereSyncPlanner (vapor grid)", oldChunkCount, oldCellCount, newChunkCount, newCellCount);

        assertEquals(289, oldChunkCount);
        assertEquals(289, oldCellCount);
        assertEquals(0, newChunkCount);
        assertEquals(34, newCellCount);
        assertTrue(newCellCount < oldCellCount);
    }

    @Test
    void smokeSyncPlannerOneRingStepShiftAtViewRadius8() {
        List<SmokeSyncPlanner.Chunk> beforeChunks = smokeRing(0);
        List<SmokeSyncPlanner.Cell> beforeCells = smokeCellsFor(beforeChunks);
        List<SmokeSyncPlanner.Chunk> afterChunks = smokeRing(1);
        List<SmokeSyncPlanner.Cell> afterCells = smokeCellsFor(afterChunks);

        SmokeSyncPlanner<String, String> oldBehavior = new SmokeSyncPlanner<>(10_000);
        oldBehavior.plan("player", "overworld", VIEW_RADIUS, beforeChunks, beforeCells);
        oldBehavior.reset("player");
        List<SmokeSyncPlanner.Update<String>> oldUpdates =
            oldBehavior.plan("player", "overworld", VIEW_RADIUS, afterChunks, afterCells);
        int oldChunkCount = oldUpdates.stream().mapToInt(u -> u.authoritativeChunks().size()).sum();
        int oldCellCount = oldUpdates.stream().mapToInt(u -> u.cells().size()).sum();

        SmokeSyncPlanner<String, String> newBehavior = new SmokeSyncPlanner<>(10_000);
        newBehavior.plan("player", "overworld", VIEW_RADIUS, beforeChunks, beforeCells);
        List<SmokeSyncPlanner.Update<String>> newUpdates =
            newBehavior.plan("player", "overworld", VIEW_RADIUS, afterChunks, afterCells);
        int newChunkCount = newUpdates.stream().mapToInt(u -> u.authoritativeChunks().size()).sum();
        int newCellCount = newUpdates.stream().mapToInt(u -> u.cells().size()).sum();

        report("SmokeSyncPlanner (smoke grid; every per-material grid shares this planner)",
            oldChunkCount, oldCellCount, newChunkCount, newCellCount);

        assertEquals(289, oldChunkCount);
        assertEquals(289, oldCellCount);
        assertEquals(0, newChunkCount);
        assertEquals(34, newCellCount);
        assertTrue(newCellCount < oldCellCount);
    }

    private static void report(String grid, int oldChunks, int oldCells, int newChunks, int newCells) {
        System.out.printf(
            "[MINECRAFT-242] %s, one-ring-step crossing @ viewRadius=%d: "
                + "before fix -> %d authoritative chunks / %d cells resent; "
                + "after fix -> %d authoritative chunks / %d cells resent (%.1f%% fewer cells)%n",
            grid, VIEW_RADIUS, oldChunks, oldCells, newChunks, newCells,
            100.0 * (oldCells - newCells) / oldCells);
    }

    private static List<AtmosphereSyncPlanner.Chunk> ring(int shiftX) {
        List<AtmosphereSyncPlanner.Chunk> chunks = new ArrayList<>();
        for (int x = -VIEW_RADIUS + shiftX; x <= VIEW_RADIUS + shiftX; x++) {
            for (int z = -VIEW_RADIUS; z <= VIEW_RADIUS; z++) {
                chunks.add(new AtmosphereSyncPlanner.Chunk(x, z));
            }
        }
        return chunks;
    }

    private static List<AtmosphereSyncPlanner.Cell> atmosphereCellsFor(List<AtmosphereSyncPlanner.Chunk> chunks) {
        List<AtmosphereSyncPlanner.Cell> cells = new ArrayList<>();
        for (AtmosphereSyncPlanner.Chunk chunk : chunks) {
            cells.add(new AtmosphereSyncPlanner.Cell(chunk.x() * 16, 0, chunk.z() * 16, 100, 1000));
        }
        return cells;
    }

    private static List<SmokeSyncPlanner.Chunk> smokeRing(int shiftX) {
        List<SmokeSyncPlanner.Chunk> chunks = new ArrayList<>();
        for (int x = -VIEW_RADIUS + shiftX; x <= VIEW_RADIUS + shiftX; x++) {
            for (int z = -VIEW_RADIUS; z <= VIEW_RADIUS; z++) {
                chunks.add(new SmokeSyncPlanner.Chunk(x, z));
            }
        }
        return chunks;
    }

    private static List<SmokeSyncPlanner.Cell> smokeCellsFor(List<SmokeSyncPlanner.Chunk> chunks) {
        List<SmokeSyncPlanner.Cell> cells = new ArrayList<>();
        for (SmokeSyncPlanner.Chunk chunk : chunks) {
            cells.add(new SmokeSyncPlanner.Cell(chunk.x() * 16, 0, chunk.z() * 16, 100, 1000));
        }
        return cells;
    }
}
