package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.PrecipitationDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Generic "precipitation_surface" target selection (spec section 24, "Rain
 * Targeting"): a randomly selected valid location beneath (or, for an UP-directed
 * material, above) a cell, respecting terrain, exposed surfaces, direction and loaded
 * area. Shared by every material's precipitation rules and by Lightning, which also
 * targets {@code precipitation_surface} per spec section 40's event shape -- this is
 * the one column-search implementation used no matter which material or event fired.
 */
final class PrecipitationTargeting {

    /** Bounded scan distance from the sampled column point; this is not a world-height search. */
    private static final int MAX_SURFACE_SCAN = 48;

    private PrecipitationTargeting() { }

    /** Returns null if no valid, loaded, exposed surface was found within the attempt budget. */
    static BlockPos findSurface(
        ServerLevel level, BlockPos cellOrigin, int cellSize, PrecipitationDirection direction, int attempts
    ) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = cellOrigin.getX() + level.random.nextInt(Math.max(1, cellSize));
            int z = cellOrigin.getZ() + level.random.nextInt(Math.max(1, cellSize));
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            int y = cellOrigin.getY() + level.random.nextInt(Math.max(1, cellSize));
            BlockPos sample = new BlockPos(x, Math.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), z);
            BlockPos surface = direction == PrecipitationDirection.DOWN
                ? scanDown(level, sample)
                : scanUp(level, sample);
            if (surface != null) return surface;
        }
        return null;
    }

    /** The first exposed-to-air position scanning downward from an air column, just above solid ground. */
    private static BlockPos scanDown(ServerLevel level, BlockPos sample) {
        BlockPos.MutableBlockPos cursor = sample.mutable();
        for (int step = 0; step < MAX_SURFACE_SCAN; step++) {
            if (cursor.getY() <= level.getMinBuildHeight()) return null;
            if (!level.getBlockState(cursor).isAir()) {
                return cursor.above().immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    /** The first exposed-to-air position scanning upward, just below an overhead solid block. */
    private static BlockPos scanUp(ServerLevel level, BlockPos sample) {
        BlockPos.MutableBlockPos cursor = sample.mutable();
        for (int step = 0; step < MAX_SURFACE_SCAN; step++) {
            if (cursor.getY() >= level.getMaxBuildHeight() - 1) return null;
            if (!level.getBlockState(cursor).isAir()) {
                return cursor.below().immutable();
            }
            cursor.move(0, 1, 0);
        }
        return null;
    }
}
