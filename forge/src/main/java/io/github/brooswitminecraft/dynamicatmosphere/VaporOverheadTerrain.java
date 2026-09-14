package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Loaded-only cache of the highest qualifying terrain block in each chunk column. */
public final class VaporOverheadTerrain {
    private static final Map<LevelChunk, ColumnCache> CACHES = new WeakHashMap<>();

    public static boolean hasQualifyingTerrainAbove(ServerLevel level, BlockPos spawnPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
            Math.floorDiv(spawnPos.getX(), 16), Math.floorDiv(spawnPos.getZ(), 16));
        if (chunk == null) {
            return false;
        }

        int localX = Math.floorMod(spawnPos.getX(), 16);
        int localZ = Math.floorMod(spawnPos.getZ(), 16);
        int column = localZ * 16 + localX;
        int highest;
        synchronized (CACHES) {
            ColumnCache cache = CACHES.computeIfAbsent(chunk, ignored -> new ColumnCache());
            highest = cache.highest(column);
            if (highest == ColumnCache.UNKNOWN) {
                highest = scanHighest(chunk, spawnPos.getX(), spawnPos.getZ());
                cache.record(column, highest);
            }
        }
        return isAbove(highest, spawnPos.getY());
    }

    public static void blockChanged(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState next) {
        if (previous == null || chunk.getLevel().isClientSide) {
            return;
        }
        if (isQualifyingTerrain(previous, chunk, pos) == isQualifyingTerrain(next, chunk, pos)) {
            return;
        }
        synchronized (CACHES) {
            ColumnCache cache = CACHES.get(chunk);
            if (cache != null) {
                cache.invalidate(Math.floorMod(pos.getZ(), 16) * 16 + Math.floorMod(pos.getX(), 16));
            }
        }
    }

    /** Clears classifications after a data-pack tag reload. */
    public static void clear() {
        synchronized (CACHES) {
            CACHES.clear();
        }
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) {
            clear();
        }
    }

    static boolean isAbove(int highestTerrainY, int spawnY) {
        return highestTerrainY != ColumnCache.NONE && highestTerrainY > spawnY;
    }

    private static int scanHighest(LevelChunk chunk, int blockX, int blockZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMaxBuildHeight() - 1; y >= chunk.getMinBuildHeight(); y--) {
            cursor.set(blockX, y, blockZ);
            if (isQualifyingTerrain(chunk.getBlockState(cursor), chunk, cursor)) {
                return y;
            }
        }
        return ColumnCache.NONE;
    }

    private static boolean isQualifyingTerrain(BlockState state, LevelChunk chunk, BlockPos pos) {
        return isTerrainFamily(state) && state.isCollisionShapeFullBlock(chunk, pos);
    }

    private static boolean isTerrainFamily(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
            || isExplicitDirtTerrain(state);
    }

    static boolean isExplicitDirtTerrain(BlockState state) {
        return state.is(Blocks.DIRT)
            || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.MYCELIUM)
            || state.is(Blocks.ROOTED_DIRT);
    }

    static final class ColumnCache {
        static final int UNKNOWN = Integer.MIN_VALUE;
        static final int NONE = Integer.MIN_VALUE + 1;
        private final int[] highest = new int[16 * 16];

        ColumnCache() {
            Arrays.fill(highest, UNKNOWN);
        }

        int highest(int column) {
            return highest[column];
        }

        void record(int column, int y) {
            highest[column] = y;
        }

        void invalidate(int column) {
            highest[column] = UNKNOWN;
        }
    }

    private VaporOverheadTerrain() { }
}
