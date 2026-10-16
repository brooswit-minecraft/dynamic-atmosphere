package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericAction;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericEvent;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.Condition;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.PrecipitationAction;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.PrecipitationDirection;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.PrecipitationRule;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.SpawnModifier;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.TargetSelection;

import java.util.List;
import java.util.Map;

/**
 * Builds this mod's built-in, data-defined events/precipitation rules/spawn modifiers
 * from the reloadable config snapshot. Per spec section 56 ("the engine should remain
 * boring, the data should be weird"): everything that varies between Lightning, a
 * Ghast spawn, Zombie Fog's spawn modifier and heat sink, and Slime Fog's precipitation
 * lives here as plain data built from config numbers -- none of it is a new code path
 * in the simulation engine, and this class uses exactly the same public
 * {@code engine.event} types a third-party mod registering its own material would use
 * (spec section 51).
 */
final class AtmosphericEvents {

    private AtmosphericEvents() { }

    /** Spec sections 25/40/41: a dense, precipitating Cloud (Vapor) cell occasionally strikes lightning. */
    static List<AtmosphericEvent> vaporEvents(DynamicAtmosphereServerConfig.Snapshot config) {
        var lightning = config.lightning();
        return List.of(new AtmosphericEvent("lightning",
            List.of(new Condition.ConcentrationAtLeast(lightning.minConcentration()), new Condition.Precipitating(true)),
            lightning.chance(), lightning.materialCost(), TargetSelection.PRECIPITATION_SURFACE,
            List.of(new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY,
                Map.of("entity", "minecraft:lightning_bolt")))));
    }

    /**
     * Spec sections 30/46: dense Smoke occasionally spawns a Ghast, through the identical
     * event/action machinery as Lightning -- "that is intentional."
     */
    static List<AtmosphericEvent> smokeEvents(DynamicAtmosphereServerConfig.Snapshot config) {
        var ghast = config.ghastSpawn();
        return List.of(new AtmosphericEvent("ghast_spawn",
            List.of(new Condition.ConcentrationAtLeast(ghast.minConcentration())),
            ghast.chance(), ghast.materialCost(), TargetSelection.SELF,
            List.of(new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY,
                Map.of("entity", "minecraft:ghast")))));
    }

    /**
     * Spec section 47: "heat should remove [Zombie Fog] relatively quickly." A
     * threshold-based event (section 26) with no actions is a generic sink: it only
     * consumes material, which is exactly what a sink is.
     */
    static List<AtmosphericEvent> zombieFogEvents(DynamicAtmosphereServerConfig.Snapshot config) {
        var zombieFog = config.zombieFog();
        return List.of(new AtmosphericEvent("heat_sink",
            List.of(new Condition.BiomeTemperatureAtLeast(zombieFog.heatMinBiomeTemperature())),
            zombieFog.heatChance(), zombieFog.heatMaterialCost(), TargetSelection.SELF, List.of()));
    }

    /** Spec section 45's own worked example: Zombie Fog doubles the zombie spawn attempt. */
    static List<SpawnModifier> zombieFogSpawnModifiers(DynamicAtmosphereServerConfig.Snapshot config) {
        var zombieFog = config.zombieFog();
        return List.of(new SpawnModifier("minecraft:zombie",
            List.of(new Condition.ConcentrationAtLeast(zombieFog.spawnConcentrationThreshold())),
            zombieFog.spawnMultiplier()));
    }

    /**
     * Spec section 44: Slime Fog's own precipitation example, rain drops slimeballs and
     * snow places slime blocks, selected by biome temperature like vanilla rain/snow.
     * Independent of, and not a replacement for, Slime's existing (MINECRAFT-18)
     * dissipation-triggered spawn.
     */
    static List<PrecipitationRule> slimePrecipitation(DynamicAtmosphereServerConfig.Snapshot config) {
        var slime = config.slimePrecipitation();
        var rain = new PrecipitationRule("rain",
            List.of(new Condition.ConcentrationAtLeast(slime.rainMinConcentration()),
                new Condition.BiomeTemperatureAtLeast(slime.rainMinBiomeTemperature())),
            PrecipitationDirection.DOWN, slime.rainMaterialCost(),
            List.of(new PrecipitationAction(new AtmosphericAction(AtmosphericAction.Type.SPAWN_ITEM,
                Map.of("item", "minecraft:slime_ball")), slime.rainChance())));
        var snow = new PrecipitationRule("snow",
            List.of(new Condition.ConcentrationAtLeast(slime.snowMinConcentration()),
                new Condition.BiomeTemperatureAtMost(slime.snowMaxBiomeTemperature())),
            PrecipitationDirection.DOWN, slime.snowMaterialCost(),
            List.of(new PrecipitationAction(new AtmosphericAction(AtmosphericAction.Type.PLACE_BLOCK,
                Map.of("block", "minecraft:slime_block")), slime.snowChance())));
        return List.of(rain, snow);
    }
}
