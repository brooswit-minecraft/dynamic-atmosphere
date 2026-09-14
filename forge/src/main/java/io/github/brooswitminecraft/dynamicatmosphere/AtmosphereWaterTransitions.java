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
    record Pending(int material, long removals) { }

    private static final Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, Pending> PENDING = new LinkedHashMap<>();

    private AtmosphereWaterTransitions() {
    }

    public static void observe(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState next) {
        if (!(chunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        if (previous == null || !previous.getFluidState().is(FluidTags.WATER)
            || next.getFluidState().is(FluidTags.WATER)) return;
        // Read this loaded chunk only and snapshot climate before deferring the emission.
        float downfall = chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2)
            .value().getModifiedClimateSettings().downfall();
        int material = materialForTransition(true, true, false, downfall);
        var key = new AtmosphereGrid.CellKey<ResourceKey<Level>>(
            level.dimension(),
            AtmosphereGridLayout.cellCoordinate(pos.getX()),
            AtmosphereGridLayout.cellCoordinate(pos.getY()),
            AtmosphereGridLayout.cellCoordinate(pos.getZ()));
        coalesce(PENDING, key, material);
    }

    enum EvaporationAction { KEEP, DRAIN_WATERLOGGED, REMOVE_FLUID }

    static EvaporationAction evaporationAction(boolean water, boolean waterlogged, boolean liquidBlock) {
        if (!water) return EvaporationAction.KEEP;
        if (waterlogged) return EvaporationAction.DRAIN_WATERLOGGED;
        return liquidBlock ? EvaporationAction.REMOVE_FLUID : EvaporationAction.KEEP;
    }

    static double evaporationChance(double temperature) {
        return Double.isNaN(temperature) ? 0 : Math.clamp(temperature / 2, 0, 1);
    }

    static int materialForHumidity(double downfall) {
        double humidity = Double.isNaN(downfall) ? 0 : Math.clamp(downfall, 0, 1);
        return (int) Math.round(10 + 70 * humidity);
    }

    static int materialForTransition(boolean mutationSucceeded, boolean previousWater, boolean nextWater,
                                     double downfall) {
        return mutationSucceeded && previousWater && !nextWater ? materialForHumidity(downfall) : 0;
    }

    static <K> void coalesce(Map<K, Pending> pending, K key, int material) {
        if (material <= 0) return;
        pending.merge(key, new Pending(material, 1), (left, right) ->
            new Pending((int) Math.min(Integer.MAX_VALUE, (long) left.material() + right.material()),
                left.removals() + right.removals()));
    }

    static Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, Pending> drain() {
        Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, Pending> drained = Map.copyOf(PENDING);
        PENDING.clear();
        return drained;
    }

    static void clear() {
        PENDING.clear();
    }
}
