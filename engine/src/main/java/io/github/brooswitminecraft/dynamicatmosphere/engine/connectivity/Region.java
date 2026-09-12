package io.github.brooswitminecraft.dynamicatmosphere.engine.connectivity;

import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.BlockPos;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.Direction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One connected component (6-connected, face adjacency only — no diagonals)
 * of passable block positions inside a single Weather Block cell (spec
 * section 7).
 *
 * <p>Identity is by reference, deliberately: a rebuild of the owning cell
 * discards every {@code Region} it previously held and builds fresh ones,
 * even where a new region happens to occupy exactly the same blocks as an
 * old one. A {@code Region} is therefore usable as a map key, and stays
 * valid, only until its cell's next rebuild; {@link RemapReport} is the only
 * supported bridge from an old region to whatever it became.
 *
 * <p>{@link #neighbors()} is a precomputed collection, not a search — cheap
 * to iterate every simulation step, which is the point: story 2 walks this
 * graph every step to move material, and must never trigger a flood fill to
 * do it.
 */
public final class Region {

    private final Set<BlockPos> blocks;
    private final Set<Direction> touchedFaces;
    private final List<RegionNeighbor> neighbors = new ArrayList<>();
    private final List<RegionNeighbor> neighborsView = Collections.unmodifiableList(neighbors);
    private final int orderKey;

    Region(Set<BlockPos> blocks, EnumSet<Direction> touchedFaces, int orderKey) {
        this.blocks = Set.copyOf(blocks);
        this.touchedFaces = Collections.unmodifiableSet(touchedFaces.clone());
        this.orderKey = orderKey;
    }

    /** Member block positions, LOCAL to the owning cell (each in {@code [0, cellSize)}). */
    public Set<BlockPos> blocks() {
        return blocks;
    }

    public int size() {
        return blocks.size();
    }

    /** Which faces of the cell this region has at least one passable block on the boundary layer of. */
    public Set<Direction> touchedFaces() {
        return touchedFaces;
    }

    /** The other regions (possibly in neighbouring cells) this region is directly connected to, and which face each crosses. */
    public List<RegionNeighbor> neighbors() {
        return neighborsView;
    }

    /**
     * The sort key used to order a cell's regions deterministically: the
     * lowest linear index (see {@code ConnectivityEngine}'s scan order)
     * among this region's member blocks.
     */
    int orderKey() {
        return orderKey;
    }

    void clearNeighbors(Direction direction) {
        neighbors.removeIf(n -> n.direction() == direction);
    }

    void addNeighbor(Direction direction, Region other) {
        neighbors.add(new RegionNeighbor(direction, other));
    }
}
