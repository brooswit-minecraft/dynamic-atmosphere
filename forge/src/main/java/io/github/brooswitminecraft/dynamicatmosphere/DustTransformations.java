package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Optional;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Bounded Dust transformations, called only for a Dust cell already selected by the simulation. */
public final class DustTransformations {
    static final double GRAVEL_CHANCE = 1.0 / 16.0;

    public enum Result {
        NONE,
        MUD,
        GRAVEL
    }

    static double mudChance(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) return 0;
        return Math.clamp((amount / (double) capacity - 0.5) * 2.0, 0.0, 1.0);
    }

    static int mudCost(int currentAmount) {
        return currentAmount <= 0 ? 0 : Math.floorDiv(currentAmount, 2) + Math.floorMod(currentAmount, 2);
    }

    static boolean createsGravel(int amount, int capacity, double roll) {
        return capacity > 0 && amount > capacity && roll >= 0 && roll < GRAVEL_CHANCE;
    }

    static int gravelCost(int currentAmount) {
        return AtmosphereCondensation.consumedAmount(currentAmount);
    }

    /** At most one conversion and eight block reads per invocation. */
    public static Result process(ServerLevel level, BlockPos source, DoubleSupplier rolls) {
        Optional<AtmosphereMaterialState> stateResult =
            DynamicAtmosphereMod.materialState(level, AtmosphereMaterial.DUST, source);
        if (stateResult.isEmpty()) return Result.NONE;
        AtmosphereMaterialState state = stateResult.orElseThrow();
        BlockPos origin = cellOrigin(source);

        double mudRoll = rolls.getAsDouble();
        if (mudRoll >= 0 && mudRoll < mudChance(state.amount(), state.capacity())) {
            BlockPos water = find(origin, level, true);
            if (water != null) {
                var previous = level.getBlockState(water);
                int cost = mudCost(state.amount());
                if (cost > 0 && level.setBlock(water, Blocks.MUD.defaultBlockState(), Block.UPDATE_ALL)) {
                    if (DynamicAtmosphereMod.consumeMaterial(level, AtmosphereMaterial.DUST, source, cost)) {
                        return Result.MUD;
                    }
                    level.setBlock(water, previous, Block.UPDATE_ALL);
                }
            }
        }

        if (createsGravel(state.amount(), state.capacity(), rolls.getAsDouble())) {
            BlockPos air = find(origin, level, false);
            if (air != null) {
                var previous = level.getBlockState(air);
                int cost = gravelCost(state.amount());
                if (cost > 0 && level.setBlock(air, Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_ALL)) {
                    if (DynamicAtmosphereMod.consumeMaterial(level, AtmosphereMaterial.DUST, source, cost)) {
                        return Result.GRAVEL;
                    }
                    level.setBlock(air, previous, Block.UPDATE_ALL);
                }
            }
        }
        return Result.NONE;
    }

    private static BlockPos cellOrigin(BlockPos source) {
        int size = AtmosphereMaterial.DUST.cellSize();
        return new BlockPos(Math.floorDiv(source.getX(), size) * size,
            Math.floorDiv(source.getY(), size) * size, Math.floorDiv(source.getZ(), size) * size);
    }

    private static BlockPos find(BlockPos origin, ServerLevel level, boolean water) {
        int size = AtmosphereMaterial.DUST.cellSize();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    var state = level.getBlockState(candidate);
                    if (water ? state.is(Blocks.WATER) : state.isAir()) return candidate;
                }
            }
        }
        return null;
    }

    private DustTransformations() { }
}
