package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Surface Creeper spawn eligibility gate (MINECRAFT-2 comment 34257): a Creeper may spawn
 * where there is no {@link VaporOverheadTerrain qualifying overhead terrain} only if the
 * position is currently at night AND the local Exhaust cell is more than half full. This is
 * an ELIGIBILITY gate layered on top of ordinary surface spawning -- like
 * {@link VaporHostileSpawnGate}, it can only deny a spawn vanilla/NeoForge already attempted,
 * never create one. An eligible spawn consumes some of that local Exhaust; a spawn that passed
 * through because qualifying terrain was above it (including every below-ground spawn) is
 * untouched by this gate and never charged. The Exhaust source (vehicles) and the
 * car-hits-creeper explosion are out of scope here; see MINECRAFT-218.
 */
public final class CreeperExhaustSpawnGate {

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
            return;
        }
        DynamicAtmosphereMod.consumeMaterial(serverLevel, AtmosphereMaterial.EXHAUST, pos,
            DynamicAtmosphereServerConfig.snapshot().exhaust().creeperSpawnConsumption());
    }

    private static boolean appliesTo(MobSpawnType spawnType, EntityType<?> entityType) {
        return (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
            && entityType == EntityType.CREEPER;
    }

    private CreeperExhaustSpawnGate() { }
}
