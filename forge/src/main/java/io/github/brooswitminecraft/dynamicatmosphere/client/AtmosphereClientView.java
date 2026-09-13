package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;
import java.util.HashMap;
import java.util.List;

/** Chunk-space envelope; the render frustum and loaded-chunk check refine it. */
public final class AtmosphereClientView {
    public record CoarseCell(int x, int y, int z, float amount) { }
    private record Section(int x, int y, int z) { }

    public static boolean contains(AtmosphereClientCache.Cell cell, double cameraX, double cameraZ, int viewChunks) {
        return containsChunk(AtmosphereGridLayout.chunkCoordinate(cell.x()), AtmosphereGridLayout.chunkCoordinate(cell.z()),
            cameraX, cameraZ, viewChunks);
    }

    public static boolean containsChunk(int chunkX, int chunkZ, double cameraX, double cameraZ, int viewChunks) {
        long cameraChunkX = (long) Math.floor(cameraX / 16.0);
        long cameraChunkZ = (long) Math.floor(cameraZ / 16.0);
        return Math.abs((long) chunkX - cameraChunkX) <= viewChunks
            && Math.abs((long) chunkZ - cameraChunkZ) <= viewChunks;
    }

    public static boolean coarseVisible(CoarseCell cell, double cameraX, double cameraZ, int viewChunks, boolean loaded) {
        return containsChunk(cell.x(), cell.z(), cameraX, cameraZ, viewChunks * 4)
            && !(loaded && containsChunk(cell.x(), cell.z(), cameraX, cameraZ, viewChunks));
    }

    public static List<CoarseCell> aggregate(List<AtmosphereClientCache.VisibleCell> cells) {
        int cellsPerEdge = 16 / AtmosphereGridLayout.CELL_SIZE;
        int cellsPerVolume = cellsPerEdge * cellsPerEdge * cellsPerEdge;
        var totals = new HashMap<Section, Float>();
        for (var cell : cells) {
            var key = new Section(Math.floorDiv(cell.cell().x(), cellsPerEdge),
                Math.floorDiv(cell.cell().y(), cellsPerEdge), Math.floorDiv(cell.cell().z(), cellsPerEdge));
            totals.merge(key, cell.amount() / cellsPerVolume, Float::sum);
        }
        return totals.entrySet().stream()
            .map(entry -> new CoarseCell(entry.getKey().x(), entry.getKey().y(), entry.getKey().z(), entry.getValue())).toList();
    }

    private AtmosphereClientView() { }
}
