package io.github.brooswitminecraft.dynamicatmosphere.engine.adapter;

import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A hand-buildable, in-memory {@link EnvironmentalAdapter} for tests. This
 * is NOT a real implementation of the interface — SICKOS-27 writes the one
 * backed by the actual game — which is why it lives in the test source set
 * rather than under {@code engine}'s main code.
 *
 * <p>An unset position defaults to {@link Passability#IMPASSABLE}: solid,
 * not air and not unknown. Tests are expected to build worlds outside-in —
 * fill a box solid, then carve out what the test actually needs — and a
 * solid default means forgetting to set a position fails toward an
 * unintended wall rather than toward an unintended opening that silently
 * merges two regions a test meant to keep apart.
 *
 * <p>Every mutator here notifies the registered change listener (see
 * {@link #onChange(Consumer)}) with every world position it touches — there
 * is no separate "quiet" mutator that skips this, so a test cannot forget to
 * mark a change dirty. Wire the listener to
 * {@code ConnectivityEngine::markDirty} to get real dirty tracking; it
 * defaults to a no-op, which is useful while assembling a world before an
 * engine even exists to notify.
 */
public final class SyntheticTerrain implements EnvironmentalAdapter {

    private final Map<BlockPos, Passability> data = new HashMap<>();
    private Consumer<BlockPos> onChange = pos -> { };

    public SyntheticTerrain onChange(Consumer<BlockPos> listener) {
        this.onChange = listener;
        return this;
    }

    public SyntheticTerrain set(BlockPos pos, Passability value) {
        data.put(pos, value);
        onChange.accept(pos);
        return this;
    }

    public SyntheticTerrain fillSolid(BlockPos a, BlockPos b) {
        return fill(a, b, Passability.IMPASSABLE);
    }

    public SyntheticTerrain carveAir(BlockPos a, BlockPos b) {
        return fill(a, b, Passability.PASSABLE);
    }

    /** Places a one-block-thick solid wall represented by the inclusive box between the two corners. */
    public SyntheticTerrain placeWall(BlockPos a, BlockPos b) {
        return fillSolid(a, b);
    }

    public SyntheticTerrain punchHole(BlockPos pos) {
        return set(pos, Passability.PASSABLE);
    }

    public SyntheticTerrain markUnknown(BlockPos a, BlockPos b) {
        return fill(a, b, Passability.UNKNOWN);
    }

    /** Fills the inclusive box between {@code a} and {@code b}; corners may be given in any order. */
    public SyntheticTerrain fill(BlockPos a, BlockPos b, Passability value) {
        int minX = Math.min(a.x(), b.x());
        int maxX = Math.max(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int maxY = Math.max(a.y(), b.y());
        int minZ = Math.min(a.z(), b.z());
        int maxZ = Math.max(a.z(), b.z());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(new BlockPos(x, y, z), value);
                }
            }
        }
        return this;
    }

    @Override
    public Passability passability(BlockPos worldPos) {
        return data.getOrDefault(worldPos, Passability.IMPASSABLE);
    }
}
