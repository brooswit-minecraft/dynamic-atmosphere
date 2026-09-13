package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashMap;
import java.util.Map;

/** Defers successful water-loss mutations until the owning server tick. */
public final class AtmosphereWaterTransitions {
    public static final int MATERIAL_PER_BLOCK = 40;

    private static final Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer> PENDING = new LinkedHashMap<>();

    private AtmosphereWaterTransitions() {
    }

    public static void observe(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState next) {
        if (!(chunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        int material = materialForTransition(previous != null,
            previous != null && previous.getFluidState().is(FluidTags.WATER),
            next.getFluidState().is(FluidTags.WATER));
        if (material == 0) return;
        var key = new AtmosphereGrid.CellKey<ResourceKey<Level>>(
            level.dimension(),
            AtmosphereGridLayout.cellCoordinate(pos.getX()),
            AtmosphereGridLayout.cellCoordinate(pos.getY()),
            AtmosphereGridLayout.cellCoordinate(pos.getZ()));
        coalesce(PENDING, key, material);
    }

    static int materialForTransition(boolean mutationSucceeded, boolean previousWater, boolean nextWater) {
        return mutationSucceeded && previousWater && !nextWater ? MATERIAL_PER_BLOCK : 0;
    }

    static <K> void coalesce(Map<K, Integer> pending, K key, int material) {
        if (material <= 0) return;
        pending.merge(key, material, (left, right) -> (int) Math.min(Integer.MAX_VALUE, (long) left + right));
    }

    static Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer> drain() {
        Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer> drained = Map.copyOf(PENDING);
        PENDING.clear();
        return drained;
    }

    static void clear() {
        PENDING.clear();
    }
}
