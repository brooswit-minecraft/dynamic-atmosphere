package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Bounded Obsidian Powder producers. Passive blocks and palette-filtered portal sections
 * are checked only from the loaded-chunk producer schedule; no chunks are requested.
 *
 * <p>Conservative MVP amounts: listed mobs 1/40 ticks, portal occupancy 2/20
 * ticks, shared passive source sample 1, plain obsidian 1, crying obsidian 32,
 * pearl use 24, pearl impact 48, and 100 per portal block per scheduled producer
 * pass. Random sky bursts are not produced.</p>
 */
public final class ObsidianPowderGameplay {
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
    static final int MOB_INTERVAL_TICKS = 40;
    static final int PORTAL_INTERVAL_TICKS = 20;

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

    /** Which config key, if any, a sampled passive block reads its emission amount from. */
    enum PassiveEmissionKey { CRYING_OBSIDIAN, OBSIDIAN, SHARED, NONE }

    static PassiveEmissionKey passiveEmissionKey(BlockState state) {
        if (state.is(Blocks.CRYING_OBSIDIAN)) return PassiveEmissionKey.CRYING_OBSIDIAN;
        if (state.is(Blocks.OBSIDIAN)) return PassiveEmissionKey.OBSIDIAN;
        if (isPassiveSource(state) && !state.is(Blocks.NETHER_PORTAL)) return PassiveEmissionKey.SHARED;
        return PassiveEmissionKey.NONE;
    }

    public void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        DynamicAtmosphereServerConfig.ObsidianPowder config = DynamicAtmosphereServerConfig.snapshot().obsidianPowder();
        long tick = level.getGameTime();
        int amount = 0;
        if (isEnderMob(entity) && Math.floorMod(tick + entity.getId(), config.mobIntervalTicks()) == 0) {
            amount += config.mobEmission();
        }
        if (entity.portalProcess != null && entity.portalProcess.isInsidePortalThisTick()
            && Math.floorMod(tick + entity.getId(), config.portalIntervalTicks()) == 0) {
            amount += config.portalOccupantEmission();
        }
        if (amount > 0) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.OBSIDIAN_POWDER, entity.blockPosition(), amount);
        }
    }

    /**
     * Called by the bounded producer schedule for a position it already selected in a loaded chunk.
     * Crying obsidian and plain obsidian each read their own config key; neither shares
     * {@code passiveBlockEmission} with the rest of the passive set.
     */
    public void onPassiveBlockSample(ServerLevel level, BlockPos pos, BlockState state) {
        DynamicAtmosphereServerConfig.ObsidianPowder config = DynamicAtmosphereServerConfig.snapshot().obsidianPowder();
        PassiveEmissionKey key = passiveEmissionKey(state);
        if (key == PassiveEmissionKey.NONE) return;
        int amount = switch (key) {
            case CRYING_OBSIDIAN -> config.cryingObsidianEmission();
            case OBSIDIAN -> config.obsidianEmission();
            case SHARED -> config.passiveBlockEmission();
            case NONE -> throw new AssertionError();
        };
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.OBSIDIAN_POWDER, pos, amount);
    }

    /** Called once for each loaded chunk selected by the bounded producer schedule. */
    public void onLoadedChunkProducerCheck(ServerLevel level, LevelChunk chunk) {
        DynamicAtmosphereServerConfig.ObsidianPowder config = DynamicAtmosphereServerConfig.snapshot().obsidianPowder();
        emitPortals(level, chunk, config.portalBlockEmission());
    }

    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ThrownEnderpearl pearl && event.getLevel() instanceof ServerLevel level) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.OBSIDIAN_POWDER,
                pearl.blockPosition(), DynamicAtmosphereServerConfig.snapshot().obsidianPowder().pearlUseEmission());
        }
    }

    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getProjectile() instanceof ThrownEnderpearl pearl)
            || !(pearl.level() instanceof ServerLevel level) || !impactedPearls.add(pearl.getUUID())) {
            return;
        }
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.OBSIDIAN_POWDER,
            BlockPos.containing(event.getRayTraceResult().getLocation()),
            DynamicAtmosphereServerConfig.snapshot().obsidianPowder().pearlImpactEmission());
    }

    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ThrownEnderpearl) impactedPearls.remove(event.getEntity().getUUID());
    }

    private ObsidianPowderGameplay() { }

    static Direction portalNormal(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
    }

    /** Existing chunk budget bounds work; sections without portal blocks require no block scan. */
    private void emitPortals(ServerLevel level, LevelChunk chunk, int amount) {
        if (amount <= 0) return;
        var sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            if (!section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL))) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var state = section.getBlockState(x, y, z);
                if (!state.is(Blocks.NETHER_PORTAL)) continue;
                var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                Direction normal = portalNormal(state.getValue(NetherPortalBlock.AXIS));
                // Emit on portal faces, where neighboring air provides capacity.
                DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.OBSIDIAN_POWDER,
                    pos.relative(normal), amount / 2);
                DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.OBSIDIAN_POWDER,
                    pos.relative(normal.getOpposite()), amount - amount / 2);
            }
        }
    }

    public static ObsidianPowderGameplay create() { return new ObsidianPowderGameplay(); }
}
