package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Loaded-only Slime production and high-density spawning.
 *
 * <p>Amplified defaults (ATMO-24 C1): each scheduled loaded slime-chunk check
 * unconditionally (denominator 1) emits 64 units at a random position below
 * Y=40. At or above 75% fullness, one 1/32 roll may create one slime for 25%
 * of cell capacity, after at most eight loaded-only positions pass vanilla
 * spawn rules. The finite debit and one-spawn-per-callback limit prevent
 * duplicate runaway.</p>
 */
public final class SlimeGameplay {
    static final int UNDERGROUND_AMOUNT = 64;
    static final int UNDERGROUND_CHANCE_DENOMINATOR = 1;
    static final int HIGH_SPAWN_CHANCE_DENOMINATOR = 32;
    static final int SPAWN_ATTEMPTS = 8;
    private static final long SLIME_SALT = 987234911L;
    private static final int VANILLA_UNDERGROUND_CEILING = 40;

    record SpawnDecision(boolean spawn, int cost) {
        static final SpawnDecision NONE = new SpawnDecision(false, 0);
    }

    static boolean undergroundEmission(boolean slimeChunk, int roll) {
        return slimeChunk && roll == 0;
    }

    static SpawnDecision spawnDecision(int amount, int capacity, int roll) {
        if (amount <= 0 || capacity <= 0 || (long) amount * 4 < (long) capacity * 3 || roll != 0) {
            return SpawnDecision.NONE;
        }
        return new SpawnDecision(true, Math.max(1, (capacity + 3) / 4));
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

    /** Called once for a processed Slime cell whose origin is already loaded. */
    public void onProcessedCell(ServerLevel level, BlockPos cellOrigin) {
        DynamicAtmosphereServerConfig.Slime config = DynamicAtmosphereServerConfig.snapshot().slime();
        DynamicAtmosphereMod.materialState(level, AtmosphereMaterial.SLIME, cellOrigin).ifPresent(state -> {
            SpawnDecision decision = spawnDecision(state.amount(), state.capacity(),
                level.random.nextInt(config.spawnChanceDenominator()));
            if (!decision.spawn()) return;
            BlockPos pos = findSpawnPosition(level, cellOrigin, config.spawnAttempts());
            if (pos == null || !DynamicAtmosphereMod.consumeMaterial(
                level, AtmosphereMaterial.SLIME, cellOrigin, decision.cost())) return;
            EntityType.SLIME.spawn(level, pos, MobSpawnType.NATURAL);
        });
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
