package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Denies hostile spawning without qualifying overhead terrain unless the local vapor cell is
 * strictly more than half full. Overworld only: Vapor does not gate Nether or
 * End monster spawns. Applies uniformly to every monster-category entity
 * type, including Endermen — there is no per-entity-type carve-out. This
 * gate never consumes vapor and never forces a chunk load. Commands, eggs,
 * spawners, and underground spawning retain their normal behavior.
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

    /**
     * Same decision, keyed by entity type rather than category. Lets tests exercise the exact
     * entity type an event carries (e.g. {@code EntityType.ENDERMAN}) and confirm it collapses
     * to the same category-based decision as any other monster — no entity type is special-cased.
     */
    static Decision evaluate(
        MobSpawnType spawnType,
        EntityType<?> entityType,
        boolean overworld,
        boolean qualifyingTerrainAbove,
        boolean vaporMoreThanHalfFull
    ) {
        return evaluate(
            spawnType, entityType.getCategory(), overworld, qualifyingTerrainAbove, vaporMoreThanHalfFull);
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        EntityType<?> entityType = event.getEntityType();
        if (!appliesTo(spawnType, entityType.getCategory())) {
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
        if (evaluate(spawnType, entityType, true, qualifyingTerrainAbove, vaporMoreThanHalfFull) == Decision.DENY) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private static boolean appliesTo(MobSpawnType spawnType, MobCategory category) {
        return (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
            && category == MobCategory.MONSTER;
    }

    private VaporHostileSpawnGate() { }
}
