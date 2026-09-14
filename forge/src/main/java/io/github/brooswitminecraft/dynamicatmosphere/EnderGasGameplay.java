package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Bounded Ender Gas producers. Passive block and full-moon methods are called
 * only from an existing loaded-chunk producer schedule; this class never scans
 * the loaded world or requests a chunk.
 *
 * <p>Conservative MVP amounts: listed mobs 1/40 ticks, portal occupancy 2/20
 * ticks, passive source sample 1, pearl use 24, pearl impact 48, and full-moon
 * burst 8,000 after an independent 1/256 roll per scheduled chunk check.</p>
 */
public final class EnderGasGameplay {
    private static final Set<ResourceLocation> PASSIVE_SOURCE_IDS = Set.of(
        ResourceLocation.withDefaultNamespace("nether_portal"),
        ResourceLocation.withDefaultNamespace("ender_chest"),
        ResourceLocation.withDefaultNamespace("soul_torch"),
        ResourceLocation.withDefaultNamespace("soul_wall_torch"),
        ResourceLocation.withDefaultNamespace("soul_fire"),
        ResourceLocation.withDefaultNamespace("soul_sand")
    );
    static final int MOB_AMOUNT = 1;
    static final int PORTAL_OCCUPANT_AMOUNT = 2;
    static final int PASSIVE_BLOCK_AMOUNT = 1;
    static final int PEARL_USE_AMOUNT = 24;
    static final int PEARL_IMPACT_AMOUNT = 48;
    static final int FULL_MOON_BURST_AMOUNT = 8_000;
    static final int MOB_INTERVAL_TICKS = 40;
    static final int PORTAL_INTERVAL_TICKS = 20;
    static final int FULL_MOON_CHANCE_DENOMINATOR = 256;

    private final Set<UUID> impactedPearls = new HashSet<>();

    static boolean isEnderMob(Entity entity) {
        return entity instanceof EnderMan || entity instanceof Endermite || entity instanceof EnderDragon
            || entity instanceof Witch || entity instanceof Shulker;
    }

    static boolean isPassiveSource(BlockState state) {
        return isPassiveSourceId(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    static boolean isPassiveSourceId(ResourceLocation id) {
        return PASSIVE_SOURCE_IDS.contains(id);
    }

    static boolean fullMoonBurst(boolean night, int moonPhase, int roll) {
        return night && moonPhase == 0 && roll == 0;
    }

    public void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        long tick = level.getGameTime();
        int amount = 0;
        if (isEnderMob(entity) && Math.floorMod(tick + entity.getId(), MOB_INTERVAL_TICKS) == 0) {
            amount += MOB_AMOUNT;
        }
        if (entity.portalProcess != null && entity.portalProcess.isInsidePortalThisTick()
            && Math.floorMod(tick + entity.getId(), PORTAL_INTERVAL_TICKS) == 0) {
            amount += PORTAL_OCCUPANT_AMOUNT;
        }
        if (amount > 0) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.ENDER_GAS, entity.blockPosition(), amount);
        }
    }

    /** Called by the bounded producer schedule for a position it already selected in a loaded chunk. */
    public void onPassiveBlockSample(ServerLevel level, BlockPos pos, BlockState state) {
        if (isPassiveSource(state)) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.ENDER_GAS, pos, PASSIVE_BLOCK_AMOUNT);
        }
    }

    /** Called once for each loaded chunk selected by the bounded producer schedule. */
    public void onLoadedChunkProducerCheck(ServerLevel level, LevelChunk chunk) {
        int roll = level.random.nextInt(FULL_MOON_CHANCE_DENOMINATOR);
        if (!fullMoonBurst(level.isNight(), level.dimensionType().moonPhase(level.getDayTime()), roll)) return;
        int x = chunk.getPos().getMinBlockX() + level.random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + level.random.nextInt(16);
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.ENDER_GAS,
            new BlockPos(x, Math.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), z),
            FULL_MOON_BURST_AMOUNT);
    }

    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ThrownEnderpearl pearl && event.getLevel() instanceof ServerLevel level) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.ENDER_GAS,
                pearl.blockPosition(), PEARL_USE_AMOUNT);
        }
    }

    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getProjectile() instanceof ThrownEnderpearl pearl)
            || !(pearl.level() instanceof ServerLevel level) || !impactedPearls.add(pearl.getUUID())) {
            return;
        }
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.ENDER_GAS,
            BlockPos.containing(event.getRayTraceResult().getLocation()), PEARL_IMPACT_AMOUNT);
    }

    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ThrownEnderpearl) impactedPearls.remove(event.getEntity().getUUID());
    }

    private EnderGasGameplay() { }

    public static EnderGasGameplay create() { return new EnderGasGameplay(); }
}
