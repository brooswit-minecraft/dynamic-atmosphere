package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Fixed random samples in an already-loaded chunk, including every underground height. */
final class FloraProducers {
    static boolean caveFlora(BlockState state) {
        return state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET)
            || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA)
            || state.is(Blocks.SPORE_BLOSSOM) || state.is(Blocks.GLOW_LICHEN)
            || state.is(Blocks.HANGING_ROOTS) || state.is(Blocks.CAVE_VINES)
            || state.is(Blocks.CAVE_VINES_PLANT) || state.is(Blocks.SMALL_DRIPLEAF)
            || state.is(Blocks.BIG_DRIPLEAF) || state.is(Blocks.BIG_DRIPLEAF_STEM);
    }

    static boolean moldSource(BlockState state) {
        return state.is(Blocks.MYCELIUM) || state.is(Blocks.RED_MUSHROOM)
            || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM_BLOCK)
            || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM);
    }

    static boolean sample(ServerLevel level, LevelChunk chunk, ForgeAtmospherePrototype prototype) {
        var config = DynamicAtmosphereServerConfig.snapshot();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (BlockPos pos : positions(x0, z0, level.getMinBuildHeight(), level.getHeight(),
                config.flora().samplesPerChunkCycle(), level.random::nextInt)) {
            var state = chunk.getBlockState(pos);
            if (caveFlora(state)) prototype.emitVapor(level, vaporPosition(pos, state), config.flora().caveVaporEmission());
            if (moldSource(state)) prototype.emitMaterial(level, AtmosphereMaterial.MOLD, pos.above(),
                config.mold().sourceEmission());
        }
        if (level.random.nextDouble() < config.flora().desertDustChance()) {
            int x = level.random.nextInt(16), z = level.random.nextInt(16);
            BlockPos support = new BlockPos(x0 + x,
                chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z0 + z);
            var state = chunk.getBlockState(support);
            if (level.isInWorldBounds(support.above()) && level.canSeeSky(support.above())
                && chunk.getBlockState(support.above()).isAir()
                && (state.is(BlockTags.SAND) || state.is(BlockTags.DIRT))
                && level.getBiome(support.above()).is(Biomes.DESERT)) {
                prototype.emitMaterial(level, AtmosphereMaterial.DUST, support.above(), 1);
            }
        }
        return true;
    }

    static BlockPos vaporPosition(BlockPos source, BlockState state) {
        // Solid moss at y=4n+3 can occupy a completely full source cell. Emit into the
        // cell above; hanging/non-solid plants emit where they are, not into their ceiling.
        return state.is(Blocks.MOSS_BLOCK) ? source.above() : source;
    }

    static java.util.Set<BlockPos> positions(int x, int z, int minY, int height, int attempts,
                                             java.util.function.IntUnaryOperator random) {
        var positions = new java.util.LinkedHashSet<BlockPos>();
        // Repeated coordinates are not retried: this is a hard work bound, not a target count.
        for (int i = 0; i < attempts; i++) {
            positions.add(new BlockPos(x + random.applyAsInt(16), minY + random.applyAsInt(height),
                z + random.applyAsInt(16)));
        }
        return positions;
    }

    private FloraProducers() { }
}
