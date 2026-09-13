package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;

/** Chunk-space envelope; the render frustum and loaded-chunk check refine it. */
public final class AtmosphereClientView {
    public static boolean contains(AtmosphereClientCache.Cell cell, double cameraX, double cameraZ, int viewChunks) {
        long cameraChunkX = (long) Math.floor(cameraX / 16.0);
        long cameraChunkZ = (long) Math.floor(cameraZ / 16.0);
        return Math.abs((long) AtmosphereGridLayout.chunkCoordinate(cell.x()) - cameraChunkX) <= viewChunks
            && Math.abs((long) AtmosphereGridLayout.chunkCoordinate(cell.z()) - cameraChunkZ) <= viewChunks;
    }

    private AtmosphereClientView() { }
}
