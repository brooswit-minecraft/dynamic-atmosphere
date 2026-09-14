package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Enderman override for the independent Ender Gas grid. Endermen are excluded
 * from the generic Vapor gate when this handler is registered.
 */
public final class EnderGasSpawnGate {

    static boolean denies(EntityType<?> type, MobSpawnType spawnType, boolean enderGasMoreThanHalfFull) {
        return type == EntityType.ENDERMAN && spawnType == MobSpawnType.NATURAL && !enderGasMoreThanHalfFull;
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != EntityType.ENDERMAN || event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (denies(event.getEntityType(), event.getSpawnType(),
            DynamicAtmosphereMod.isEnderGasMoreThanHalfFull(event.getLevel().getLevel(), event.getPos()))) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private EnderGasSpawnGate() { }
}
