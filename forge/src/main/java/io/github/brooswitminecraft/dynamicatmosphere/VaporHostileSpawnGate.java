package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Denies hostile spawning without qualifying overhead terrain unless the local vapor cell is
 * strictly more than half full. Overworld only: Vapor does not gate Nether or
 * End monster spawns. This gate never consumes vapor and never forces a
 * chunk load. Commands, eggs, spawners, and underground spawning retain
 * their normal behavior.
 */
public final class VaporHostileSpawnGate {

    enum Decision {
        PASS_THROUGH,
        DENY
    }

    static Decision evaluate(
        MobSpawnType spawnType,
        MobCategory category,
        boolean overworld,
        boolean qualifyingTerrainAbove,
        boolean vaporMoreThanHalfFull
    ) {
        if (!overworld || !appliesTo(spawnType, category) || qualifyingTerrainAbove) {
            return Decision.PASS_THROUGH;
        }
        return vaporMoreThanHalfFull ? Decision.PASS_THROUGH : Decision.DENY;
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        // Endermen use the independent, purple-only Ender Gas gate.
        if (event.getEntityType() == EntityType.ENDERMAN) return;
        MobSpawnType spawnType = event.getSpawnType();
        MobCategory category = event.getEntityType().getCategory();
        if (!appliesTo(spawnType, category)) {
            return;
        }

        var level = event.getLevel().getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        var pos = event.getPos();
        boolean qualifyingTerrainAbove = VaporOverheadTerrain.hasQualifyingTerrainAbove(level, pos);
        boolean vaporMoreThanHalfFull = !qualifyingTerrainAbove
            && DynamicAtmosphereMod.isVaporMoreThanHalfFull(level, pos);
        if (evaluate(spawnType, category, true, qualifyingTerrainAbove, vaporMoreThanHalfFull) == Decision.DENY) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private static boolean appliesTo(MobSpawnType spawnType, MobCategory category) {
        return (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
            && category == MobCategory.MONSTER;
    }

    private VaporHostileSpawnGate() { }
}
