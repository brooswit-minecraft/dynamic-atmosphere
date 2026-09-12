package io.github.brooswitminecraft.dynamicatmosphere.engine.grid;

/**
 * An integer block coordinate. Depending on context this is either a WORLD
 * block coordinate or a coordinate LOCAL to one Weather Block cell (local
 * coordinates run {@code [0, cellSize)} on each axis) — which one is always
 * determined by the API using it, never by this type itself.
 */
public record BlockPos(int x, int y, int z) {
}
