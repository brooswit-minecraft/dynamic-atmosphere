package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Surface Creeper spawn eligibility gate (MINECRAFT-2 comment 34257): a Creeper may spawn
 * where there is no {@link VaporOverheadTerrain qualifying overhead terrain} only if the
 * position is currently at night AND the local Exhaust cell is more than half full. This is
 * an ELIGIBILITY gate layered on top of ordinary surface spawning -- like
 * {@link VaporHostileSpawnGate}, it can only deny a spawn vanilla/NeoForge already attempted,
 * never create one. Below-ground (and any other sheltered) spawning is unaffected, since it
 * always has qualifying overhead terrain and never reaches the deny branch below.
 *
 * <p>Charging Exhaust is split from the deny decision (PR #63 review): {@link #onSpawnPlacementCheck}
 * only ever denies, it never consumes, since passing its check is not proof the mob will actually
 * spawn -- obstruction/pathfinding ({@link MobSpawnEvent.PositionCheck}) or another mod's
 * FinalizeSpawn veto can still fail the attempt afterward, and charging there would drain Exhaust
 * for spawns that never happen. Instead, {@link #onPositionCheck} tags the exact Mob instance our
 * gate made eligible (never a qualifying-terrain pass-through, which is never tagged), and
 * {@link #onEntityJoin} charges it exactly once, only once that Mob actually finishes joining the
 * level -- the one point downstream of every other veto.</p>
 */
public final class CreeperExhaustSpawnGate {

    private static final Set<Mob> ELIGIBLE_FOR_EXHAUST_CHARGE = Collections.newSetFromMap(new WeakHashMap<>());

    enum Decision {
        PASS_THROUGH,
        DENY
    }

    static Decision evaluate(
        MobSpawnType spawnType,
        EntityType<?> entityType,
        boolean overworld,
        boolean qualifyingTerrainAbove,
        boolean night,
        boolean exhaustDense
    ) {
        if (!overworld || !appliesTo(spawnType, entityType) || qualifyingTerrainAbove) {
            return Decision.PASS_THROUGH;
        }
        return (night && exhaustDense) ? Decision.PASS_THROUGH : Decision.DENY;
    }

    /**
     * True exactly for the spawn attempts this gate itself made eligible: never a
     * qualifying-terrain pass-through (those were never gated in the first place), never outside
     * the Overworld, never a non-Creeper/non-natural spawn, and only when both night and dense
     * Exhaust hold. This is the one condition {@link #onPositionCheck} tags for charging.
     */
    static boolean isEligibleForExhaustCharge(
        MobSpawnType spawnType,
        EntityType<?> entityType,
        boolean overworld,
        boolean qualifyingTerrainAbove,
        boolean night,
        boolean exhaustDense
    ) {
        return overworld && appliesTo(spawnType, entityType) && !qualifyingTerrainAbove && night && exhaustDense;
    }

    /**
     * True only for a tagged attempt that actually finishes joining the level on the logical
     * server, fresh (not a chunk reload bringing back a persisted entity). A tagged attempt later
     * vetoed downstream, loaded from disk, or observed on the client never satisfies this.
     */
    static boolean shouldChargeOnJoin(boolean tagged, boolean loadedFromDisk, boolean clientSide) {
        return tagged && !loadedFromDisk && !clientSide;
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        EntityType<?> entityType = event.getEntityType();
        if (!appliesTo(spawnType, entityType)) {
            return;
        }

        var level = event.getLevel().getLevel();
        if (level.dimension() != Level.OVERWORLD || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = event.getPos();
        boolean qualifyingTerrainAbove = VaporOverheadTerrain.hasQualifyingTerrainAbove(serverLevel, pos);
        if (qualifyingTerrainAbove) {
            return;
        }

        boolean night = level.isNight();
        boolean exhaustDense = DynamicAtmosphereMod.isMaterialMoreThanHalfFull(
            serverLevel, AtmosphereMaterial.EXHAUST, pos);
        if (evaluate(spawnType, entityType, true, false, night, exhaustDense) == Decision.DENY) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /**
     * Fires once the Mob instance exists, after {@link #onSpawnPlacementCheck} already let it
     * through. Tags it for charging by re-deriving {@link #isEligibleForExhaustCharge} at this
     * Mob's actual position -- the attempt may still fail downstream, in which case this tag
     * simply expires unused (the set is weak-keyed).
     */
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        if (!appliesTo(event.getSpawnType(), mob.getType())) {
            return;
        }

        var level = event.getLevel().getLevel();
        if (level.dimension() != Level.OVERWORLD || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = mob.blockPosition();
        boolean qualifyingTerrainAbove = VaporOverheadTerrain.hasQualifyingTerrainAbove(serverLevel, pos);
        boolean night = level.isNight();
        boolean exhaustDense = DynamicAtmosphereMod.isMaterialMoreThanHalfFull(
            serverLevel, AtmosphereMaterial.EXHAUST, pos);
        if (isEligibleForExhaustCharge(
            event.getSpawnType(), mob.getType(), true, qualifyingTerrainAbove, night, exhaustDense)) {
            ELIGIBLE_FOR_EXHAUST_CHARGE.add(mob);
        }
    }

    /**
     * Charges Exhaust exactly once, only for a Creeper actually joining the level -- the default
     * listener registration here already skips an event some other mod cancelled first, so a
     * tagged attempt that was vetoed downstream (obstruction, pathfinding, FinalizeSpawn) is
     * simply dropped by {@code remove}, uncharged.
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) {
            return;
        }
        boolean tagged = ELIGIBLE_FOR_EXHAUST_CHARGE.remove(creeper);
        if (!shouldChargeOnJoin(tagged, event.loadedFromDisk(), event.getLevel().isClientSide())
            || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        DynamicAtmosphereMod.consumeMaterial(serverLevel, AtmosphereMaterial.EXHAUST, creeper.blockPosition(),
            DynamicAtmosphereServerConfig.snapshot().exhaust().creeperSpawnConsumption());
    }

    private static boolean appliesTo(MobSpawnType spawnType, EntityType<?> entityType) {
        return (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
            && entityType == EntityType.CREEPER;
    }

    private CreeperExhaustSpawnGate() { }
}
