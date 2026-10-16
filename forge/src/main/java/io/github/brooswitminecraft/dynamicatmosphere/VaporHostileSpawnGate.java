package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Denies hostile spawning without qualifying overhead terrain unless the local vapor cell is
 * strictly more than half full, or (MINECRAFT-17/ATMO-22) a sufficiently full Void Gas cell
 * makes the position eligible the same way. Applies uniformly to every
 * monster-category entity type, including Endermen — there is no
 * per-entity-type carve-out. This gate never consumes vapor or Void Gas and
 * never forces a chunk load. Commands, eggs, spawners, and underground
 * spawning retain their normal behavior.
 *
 * <p>Vapor's own gating stays Overworld-only, unconditionally — it does not gate Nether or
 * End monster spawns. Void Gas's dimension scope is instead governed by
 * {@code voidGas.surfaceSpawnEligibleOverworldOnly}: Brooswit has not confirmed whether "like
 * vapor" means Overworld-only for Void Gas too (ATMO-22 comment 23231), so that reading is a
 * configurable default rather than a hard-coded assumption.</p>
 */
public final class VaporHostileSpawnGate {

    enum Decision {
        PASS_THROUGH,
        DENY
    }

    static Decision evaluate(
        MobSpawnType spawnType,
        MobCategory category,
        boolean gateAppliesInDimension,
        boolean qualifyingTerrainAbove,
        boolean denseAtmosphere
    ) {
        if (!gateAppliesInDimension || !appliesTo(spawnType, category) || qualifyingTerrainAbove) {
            return Decision.PASS_THROUGH;
        }
        return denseAtmosphere ? Decision.PASS_THROUGH : Decision.DENY;
    }

    /**
     * Same decision, keyed by entity type rather than category. Lets tests exercise the exact
     * entity type an event carries (e.g. {@code EntityType.ENDERMAN}) and confirm it collapses
     * to the same category-based decision as any other monster — no entity type is special-cased.
     */
    static Decision evaluate(
        MobSpawnType spawnType,
        EntityType<?> entityType,
        boolean gateAppliesInDimension,
        boolean qualifyingTerrainAbove,
        boolean denseAtmosphere
    ) {
        return evaluate(
            spawnType, entityType.getCategory(), gateAppliesInDimension, qualifyingTerrainAbove, denseAtmosphere);
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        EntityType<?> entityType = event.getEntityType();
        if (!appliesTo(spawnType, entityType.getCategory())) {
            return;
        }

        var level = event.getLevel().getLevel();
        boolean overworld = level.dimension() == Level.OVERWORLD;
        boolean voidGasAppliesHere = overworld
            || !DynamicAtmosphereServerConfig.snapshot().voidGas().surfaceSpawnEligibleOverworldOnly();
        if (!overworld && !voidGasAppliesHere) {
            return;
        }

        var pos = event.getPos();
        boolean qualifyingTerrainAbove = VaporOverheadTerrain.hasQualifyingTerrainAbove(level, pos);
        boolean vaporMoreThanHalfFull = overworld && !qualifyingTerrainAbove
            && DynamicAtmosphereMod.isVaporMoreThanHalfFull(level, pos);
        boolean voidGasEligible = voidGasAppliesHere && !qualifyingTerrainAbove
            && DynamicAtmosphereMod.isMaterialMoreThanHalfFull(level, AtmosphereMaterial.VOID_GAS, pos);
        boolean denseAtmosphere = vaporMoreThanHalfFull || voidGasEligible;
        if (evaluate(spawnType, entityType, true, qualifyingTerrainAbove, denseAtmosphere) == Decision.DENY) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private static boolean appliesTo(MobSpawnType spawnType, MobCategory category) {
        return (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)
            && category == MobCategory.MONSTER;
    }

    private VaporHostileSpawnGate() { }
}
