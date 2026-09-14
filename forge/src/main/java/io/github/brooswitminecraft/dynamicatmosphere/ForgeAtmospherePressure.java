package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Loaded-world adapter; the caller owns pressure state and the per-pass budget. */
final class ForgeAtmospherePressure {

    static final int MAX_BREAKS_PER_PASS = 4;
    static final int MATERIAL_PER_BREAK = 1;
    private static final Comparator<BlockPos> BLOCK_ORDER = Comparator.comparingInt((BlockPos pos) -> pos.getX())
        .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ());

    record BreakResult(AtmosphereGrid.CellKey<ResourceKey<Level>> cell, BlockPos block, int addedMaterial) {
        BreakResult {
            block = block.immutable();
        }
    }

    /** searchLimited distinguishes an unfinished search from a search with no candidate. */
    record PressureResult(Optional<BreakResult> broken, boolean searchLimited) {
    }

    /**
     * Attempts at most one vanilla block destruction, with drops, after a
     * bounded search. Call on the server thread only after outward overflow
     * has been attempted. The caller must cap attempts at MAX_BREAKS_PER_PASS
     * across all pressure sources in the configured sampling interval, refresh capacity,
     * add the returned material unit, and retry overflow before breaking again.
     * No atmospheric state is changed here; an unsuccessful break adds nothing.
     */
    static PressureResult breakForPressure(
        ServerLevel level,
        AtmosphereGrid.CellKey<ResourceKey<Level>> source,
        Predicate<AtmosphereGrid.CellKey<ResourceKey<Level>>> activeLoaded,
        BiPredicate<AtmosphereGrid.CellKey<ResourceKey<Level>>,
            AtmosphereGrid.CellKey<ResourceKey<Level>>> canTransfer
    ) {
        return breakForPressure(level, source, activeLoaded, canTransfer, AtmosphereGridLayout.CELL_SIZE);
    }

    static PressureResult breakForPressure(
        ServerLevel level,
        AtmosphereGrid.CellKey<ResourceKey<Level>> source,
        Predicate<AtmosphereGrid.CellKey<ResourceKey<Level>>> activeLoaded,
        BiPredicate<AtmosphereGrid.CellKey<ResourceKey<Level>>,
            AtmosphereGrid.CellKey<ResourceKey<Level>>> canTransfer,
        int cellSize
    ) {
        if (cellSize < 1 || cellSize > 16 || 16 % cellSize != 0) {
            throw new IllegalArgumentException("cell size must divide a Minecraft chunk");
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activeLoaded, "activeLoaded");
        Objects.requireNonNull(canTransfer, "canTransfer");
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("pressure destruction must run on the server thread");
        }

        Predicate<AtmosphereGrid.CellKey<ResourceKey<Level>>> eligible = cell ->
            level.dimension().equals(cell.dimension()) && activeLoaded.test(cell)
                && level.getChunkSource().getChunkNow(Math.floorDiv(cell.x(), 16 / cellSize),
                    Math.floorDiv(cell.z(), 16 / cellSize)) != null;
        var selected = AtmospherePressure.selectForAttempt(
            source, eligible, cell -> scan(level, cell, cellSize), BLOCK_ORDER,
            AtmospherePressure.MAX_VISITED_CELLS, canTransfer, level.random::nextDouble);
        if (selected.selection().isEmpty()) {
            return new PressureResult(Optional.empty(), selected.searchLimited());
        }

        var target = selected.selection().get();
        BlockPos block = target.block();
        if (!eligible.test(target.cell()) || !level.isInWorldBounds(block)) {
            return new PressureResult(Optional.empty(), false);
        }
        var state = level.getBlockState(block);
        float hardness = state.getDestroySpeed(level, block);
        if (state.isAir() || !state.getFluidState().isEmpty() || !Float.isFinite(hardness) || hardness < 0) {
            return new PressureResult(Optional.empty(), false);
        }
        if (!level.destroyBlock(block, true)) {
            return new PressureResult(Optional.empty(), false);
        }
        return new PressureResult(Optional.of(new BreakResult(target.cell(), block, MATERIAL_PER_BREAK)), false);
    }

    private static AtmospherePressure.CellScan<BlockPos> scan(
        ServerLevel level, AtmosphereGrid.CellKey<ResourceKey<Level>> cell, int size
    ) {
        return scan(level, cell, size, level::isInWorldBounds);
    }

    static AtmospherePressure.CellScan<BlockPos> scan(
        BlockGetter level, AtmosphereGrid.CellKey<?> cell, int size, Predicate<BlockPos> inWorldBounds
    ) {
        long originX = (long) cell.x() * size;
        long originY = (long) cell.y() * size;
        long originZ = (long) cell.z() * size;
        int emptyBlocks = 0;
        var candidates = new ArrayList<AtmospherePressure.Candidate<BlockPos>>();
        var pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    long worldX = originX + x;
                    long worldY = originY + y;
                    long worldZ = originZ + z;
                    if (worldX < Integer.MIN_VALUE || worldX > Integer.MAX_VALUE
                        || worldY < Integer.MIN_VALUE || worldY > Integer.MAX_VALUE
                        || worldZ < Integer.MIN_VALUE || worldZ > Integer.MAX_VALUE) {
                        continue;
                    }
                    pos.set((int) worldX, (int) worldY, (int) worldZ);
                    if (!inWorldBounds.test(pos)) {
                        continue;
                    }
                    var state = level.getBlockState(pos);
                    if (ForgeCapacityBlockClassifier.isEmptySpace(state)) {
                        emptyBlocks++;
                    } else if (state.getFluidState().isEmpty()) {
                        // Keep fluid-bearing hosts excluded from pressure destruction.
                        // Their occupied volume still counts against capacity above.
                        candidates.add(new AtmospherePressure.Candidate<>(
                            pos.immutable(), state.getDestroySpeed(level, pos)));
                    }
                }
            }
        }
        return new AtmospherePressure.CellScan<>(emptyBlocks, size * size * size, candidates);
    }

    private ForgeAtmospherePressure() {
    }
}
