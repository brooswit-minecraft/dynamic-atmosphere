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
import net.minecraft.world.entity.EntityType;
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
 * ticks, passive source sample 1, pearl use 24, pearl impact 48, and 100 per portal
 * block per scheduled producer pass. Random sky bursts are not produced.</p>
 *
 * <p>MINECRAFT-368: a Phantom emits on the exact same interval-ticks schedule, through
 * the exact same OBSIDIAN_POWDER call site, that the other listed mobs already use --
 * but only while actually airborne and moving. A Phantom that is grounded (rare, but not
 * impossible: see {@code horizontalCollision} handling in its move control) or has zero
 * velocity (e.g. frozen by a mod, or mid-despawn) does not emit, unlike the other listed
 * mobs, which stay unconditional exactly as before.</p>
 */
public final class ObsidianPowderGameplay {
    private static final Set<ResourceLocation> PASSIVE_SOURCE_IDS = Set.of(
        ResourceLocation.withDefaultNamespace("nether_portal"),
        ResourceLocation.withDefaultNamespace("ender_chest"),
        ResourceLocation.withDefaultNamespace("soul_torch"),
        ResourceLocation.withDefaultNamespace("soul_wall_torch"),
        ResourceLocation.withDefaultNamespace("soul_fire"),
        ResourceLocation.withDefaultNamespace("soul_sand"),
        ResourceLocation.withDefaultNamespace("crying_obsidian"),
        ResourceLocation.withDefaultNamespace("obsidian")
    );
    static final int MOB_AMOUNT = 1;
    static final int PORTAL_OCCUPANT_AMOUNT = 2;
    static final int PASSIVE_BLOCK_AMOUNT = 1;
    static final int PEARL_USE_AMOUNT = 24;
    static final int PEARL_IMPACT_AMOUNT = 48;
    static final int MOB_INTERVAL_TICKS = 40;
    static final int PORTAL_INTERVAL_TICKS = 20;

    private final Set<UUID> impactedPearls = new HashSet<>();

    static boolean isEnderMob(EntityType<?> type) {
        return type == EntityType.ENDERMAN || type == EntityType.ENDERMITE || type == EntityType.ENDER_DRAGON
            || type == EntityType.WITCH || type == EntityType.SHULKER;
    }

    /** The Phantom's own emission eligibility: unlike the unconditional {@link #isEnderMob}
     * set, a Phantom must actually be airborne (not {@code onGround()}) and moving (nonzero
     * velocity) -- "while flying", not merely "while it exists". */
    static boolean isFlyingPhantom(EntityType<?> type, boolean airborne, boolean moving) {
        return type == EntityType.PHANTOM && airborne && moving;
    }

    static boolean isListedMobTick(EntityType<?> type, boolean airborne, boolean moving) {
        return isEnderMob(type) || isFlyingPhantom(type, airborne, moving);
    }

    /** Seam over the real {@link DynamicAtmosphereMod#emitMaterial} call, so
     * {@link #emitForEntityTick(EmissionSink, EntityType, boolean, boolean, boolean, long, int, BlockPos, int, int, int, int)}
     * can be exercised with a fake in tests. */
    interface EmissionSink {
        boolean emit(AtmosphereMaterial material, BlockPos pos, int amount);
    }

    private record ServerLevelEmissionSink(ServerLevel level) implements EmissionSink {
        @Override
        public boolean emit(AtmosphereMaterial material, BlockPos pos, int amount) {
            return DynamicAtmosphereMod.emitMaterial(level, material, pos, amount);
        }
    }

    static boolean isPassiveSource(BlockState state) {
        return isPassiveSourceId(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    static boolean isPassiveSourceId(ResourceLocation id) {
        return PASSIVE_SOURCE_IDS.contains(id);
    }

    /** Pure tick-schedule logic shared by every listed mob; the single OBSIDIAN_POWDER
     * emission call site lives only in {@link #emitForEntityTick}. */
    static int entityTickEmission(EntityType<?> type, boolean airborne, boolean moving, boolean insidePortalThisTick,
                                   long tick, int entityId, int mobIntervalTicks, int mobEmission,
                                   int portalIntervalTicks, int portalOccupantEmission) {
        int amount = 0;
        if (isListedMobTick(type, airborne, moving) && Math.floorMod(tick + entityId, mobIntervalTicks) == 0) {
            amount += mobEmission;
        }
        if (insidePortalThisTick && Math.floorMod(tick + entityId, portalIntervalTicks) == 0) {
            amount += portalOccupantEmission;
        }
        return amount;
    }

    /** Production entity-tick wiring, seamed over {@link EmissionSink} so tests can verify the
     * exact material/position/amount reaching it without a real {@code ServerLevel}. */
    static void emitForEntityTick(EmissionSink sink, EntityType<?> type, boolean airborne, boolean moving,
                              boolean insidePortalThisTick, long tick, int entityId, BlockPos pos,
                              int mobIntervalTicks, int mobEmission,
                              int portalIntervalTicks, int portalOccupantEmission) {
        int amount = entityTickEmission(type, airborne, moving, insidePortalThisTick, tick, entityId,
            mobIntervalTicks, mobEmission, portalIntervalTicks, portalOccupantEmission);
        if (amount > 0) {
            sink.emit(AtmosphereMaterial.OBSIDIAN_POWDER, pos, amount);
        }
    }

    public void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        DynamicAtmosphereServerConfig.ObsidianPowder config = DynamicAtmosphereServerConfig.snapshot().obsidianPowder();
        boolean insidePortal = entity.portalProcess != null && entity.portalProcess.isInsidePortalThisTick();
        emitForEntityTick(new ServerLevelEmissionSink(level), entity.getType(), !entity.onGround(),
            entity.getDeltaMovement().lengthSqr() > 0.0, insidePortal, level.getGameTime(), entity.getId(),
            entity.blockPosition(), config.mobIntervalTicks(), config.mobEmission(),
            config.portalIntervalTicks(), config.portalOccupantEmission());
    }

    /** Called by the bounded producer schedule for a position it already selected in a loaded chunk. */
    public void onPassiveBlockSample(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isPassiveSource(state) || state.is(Blocks.NETHER_PORTAL)) return;
        var config = DynamicAtmosphereServerConfig.snapshot().obsidianPowder();
        int amount = state.is(Blocks.OBSIDIAN) ? config.obsidianBlockEmission()
            : state.is(Blocks.CRYING_OBSIDIAN) ? config.cryingObsidianEmission()
            : config.passiveBlockEmission();
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
