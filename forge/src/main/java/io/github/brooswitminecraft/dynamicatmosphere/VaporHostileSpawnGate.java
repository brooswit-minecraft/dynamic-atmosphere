package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Denies exposed vanilla hostile spawning unless the local vapor cell is
 * strictly more than half full. This gate never consumes vapor and never
 * forces a chunk load. Commands, eggs, spawners, and underground spawning
 * retain their normal behavior.
 */
public final class VaporHostileSpawnGate {

    enum Decision {
        PASS_THROUGH,
        DENY
    }

    static Decision evaluate(
        MobSpawnType spawnType,
        MobCategory category,
        boolean canSeeSky,
        boolean vaporMoreThanHalfFull
    ) {
        if (!appliesTo(spawnType, category) || !canSeeSky) {
            return Decision.PASS_THROUGH;
        }
        return vaporMoreThanHalfFull ? Decision.PASS_THROUGH : Decision.DENY;
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        MobCategory category = event.getEntityType().getCategory();
        if (!appliesTo(spawnType, category)) {
            return;
        }

        var pos = event.getPos();
        boolean canSeeSky = event.getLevel().canSeeSky(pos);
        boolean vaporMoreThanHalfFull = canSeeSky
            && DynamicAtmosphereMod.isVaporMoreThanHalfFull(event.getLevel().getLevel(), pos);
        if (evaluate(spawnType, category, canSeeSky, vaporMoreThanHalfFull) == Decision.DENY) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private static boolean appliesTo(MobSpawnType spawnType, MobCategory category) {
        return (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
            && category == MobCategory.MONSTER;
    }

    private VaporHostileSpawnGate() { }
}
