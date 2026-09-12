package io.github.brooswitminecraft.dynamicatmosphere.engine.connectivity;

import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.Direction;

/**
 * One precomputed edge in the region adjacency graph: {@code region} is
 * reachable from the owning {@link Region} by crossing {@code direction}.
 * Story 2 needs both the neighbour region and the direction, since altitude
 * bias depends on whether a transfer is up, down, or sideways.
 */
public record RegionNeighbor(Direction direction, Region region) {
}
