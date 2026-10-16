package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Loaded-only Slime production and dissipation-triggered spawning.
 *
 * <p>Amplified defaults (ATMO-24 C1): each scheduled loaded slime-chunk check
 * unconditionally (denominator 1) emits 64 units at a random position below
 * Y=40.</p>
 *
 * <p>MINECRAFT-18 removed the consumption-driven spawn that used to fire above
 * 75% fullness on every processed cell, along with its now-dead
 * {@code slime.spawnChanceDenominator}/{@code slime.spawnAttempts} config
 * keys, per ATMO-22 comment 23227: "Slimes also shouldn't do that
 * [consumption-spawn] behavior, just on dissipation there is an additional
 * chance that a slime spawns. Size of the slime is based on how dense the
 * slime for is there." A Slime spawn is now instead an additional, independent
 * chance rolled at the same {@link HeavyGasDissipation} call site used for
 * Void Gas and Obsidian Powder, each time a Slime cell actually loses material
 * to dissipation. The spawned slime's size (vanilla small/medium/big = 1/2/4)
 * is picked by the cell's amount/capacity density at the moment of
 * dissipation, against configurable thresholds.</p>
 */
public final class SlimeGameplay {
    static final int UNDERGROUND_AMOUNT = 64;
    static final int UNDERGROUND_CHANCE_DENOMINATOR = 1;
    static final int SMALL_SIZE = 1;
    static final int MEDIUM_SIZE = 2;
    static final int BIG_SIZE = 4;
    private static final long SLIME_SALT = 987234911L;
    private static final int VANILLA_UNDERGROUND_CEILING = 40;

    static boolean undergroundEmission(boolean slimeChunk, int roll) {
        return slimeChunk && roll == 0;
    }

    static boolean dissipationSpawnRoll(int roll) {
        return roll == 0;
    }

    /** Density is amount/capacity at the point of dissipation; ties go to the larger size. */
    static int sizeForDensity(int amount, int capacity, double mediumDensityThreshold, double bigDensityThreshold) {
        if (capacity <= 0) return SMALL_SIZE;
        double density = (double) amount / capacity;
        if (density >= bigDensityThreshold) return BIG_SIZE;
        if (density >= mediumDensityThreshold) return MEDIUM_SIZE;
        return SMALL_SIZE;
    }

    /** Called once per loaded chunk selected by the bounded producer schedule. */
    public void onLoadedChunkProducerCheck(ServerLevel level, LevelChunk chunk) {
        DynamicAtmosphereServerConfig.Slime config = DynamicAtmosphereServerConfig.snapshot().slime();
        ChunkPos chunkPos = chunk.getPos();
        if (level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != chunk) return;
        boolean slimeChunk = WorldgenRandom.seedSlimeChunk(
            chunkPos.x, chunkPos.z, level.getSeed(), SLIME_SALT).nextInt(10) == 0;
        if (!undergroundEmission(slimeChunk, level.random.nextInt(config.undergroundChanceDenominator()))) return;
        int topExclusive = Math.min(VANILLA_UNDERGROUND_CEILING, level.getMaxBuildHeight());
        if (topExclusive <= level.getMinBuildHeight()) return;
        int x = chunkPos.getMinBlockX() + level.random.nextInt(16);
        int y = level.getMinBuildHeight() + level.random.nextInt(topExclusive - level.getMinBuildHeight());
        int z = chunkPos.getMinBlockZ() + level.random.nextInt(16);
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.SLIME,
            new BlockPos(x, y, z), config.undergroundEmission());
    }

    /**
     * Called once for a Slime cell that just lost material to {@link HeavyGasDissipation}, with
     * {@code amount}/{@code capacity} captured before that loss — the local density "at the point
     * of dissipation" per ATMO-22 comment 23227.
     */
    public void onDissipation(ServerLevel level, BlockPos cellOrigin, int amount, int capacity) {
        DynamicAtmosphereServerConfig.Slime config = DynamicAtmosphereServerConfig.snapshot().slime();
        if (!dissipationSpawnRoll(level.random.nextInt(config.dissipationSpawnChanceDenominator()))) return;
        BlockPos pos = findSpawnPosition(level, cellOrigin, config.dissipationSpawnAttempts());
        if (pos == null) return;
        int size = sizeForDensity(amount, capacity, config.mediumDensityThreshold(), config.bigDensityThreshold());
        Slime slime = EntityType.SLIME.spawn(level, pos, MobSpawnType.NATURAL);
        if (slime != null) slime.setSize(size, true);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos origin, int attempts) {
        int size = AtmosphereMaterial.SLIME.cellSize();
        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos pos = origin.offset(level.random.nextInt(size), level.random.nextInt(size), level.random.nextInt(size));
            if (level.isOutsideBuildHeight(pos)
                || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !SpawnPlacements.isSpawnPositionOk(EntityType.SLIME, level, pos)
                || !SpawnPlacements.checkSpawnRules(EntityType.SLIME, level, MobSpawnType.NATURAL, pos, level.random)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private SlimeGameplay() { }

    public static SlimeGameplay create() { return new SlimeGameplay(); }
}
