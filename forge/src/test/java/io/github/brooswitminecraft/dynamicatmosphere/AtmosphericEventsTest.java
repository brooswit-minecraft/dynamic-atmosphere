package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericAction;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.Condition;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.EventContext;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.PrecipitationDirection;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.TargetSelection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that {@link AtmosphericEvents} builds exactly the data the spec's own worked
 * examples describe (sections 40/41/44/45/46), entirely from config numbers -- no
 * special-cased behavior lives in this class, only {@code engine.event} records.
 */
class AtmosphericEventsTest {

    private static DynamicAtmosphereServerConfig.Snapshot configWith(
        DynamicAtmosphereServerConfig.Lightning lightning,
        DynamicAtmosphereServerConfig.GhastSpawn ghastSpawn,
        DynamicAtmosphereServerConfig.ZombieFog zombieFog,
        DynamicAtmosphereServerConfig.SlimePrecipitation slimePrecipitation
    ) {
        return new DynamicAtmosphereServerConfig.Snapshot(null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, lightning, ghastSpawn, zombieFog, slimePrecipitation,
            null, null, null);
    }

    @Test
    void vaporEventsBuildsOneLightningEventTargetingPrecipitationSurface() {
        var config = configWith(new DynamicAtmosphereServerConfig.Lightning(0.85, 0.002, 0.03, 8),
            null, null, null);
        var events = AtmosphericEvents.vaporEvents(config);
        assertEquals(1, events.size());
        var lightning = events.get(0);
        assertEquals("lightning", lightning.id());
        assertEquals(0.002, lightning.chance());
        assertEquals(0.03, lightning.materialCost());
        assertEquals(TargetSelection.PRECIPITATION_SURFACE, lightning.target());
        assertEquals(1, lightning.actions().size());
        var action = lightning.actions().get(0);
        assertEquals(AtmosphericAction.Type.SPAWN_ENTITY, action.type());
        assertEquals("minecraft:lightning_bolt", action.param("entity"));

        var dense = new EventContext(0.90, true, 0.5);
        assertTrue(lightning.conditionsMatch(dense));
        assertFalse(lightning.conditionsMatch(new EventContext(0.90, false, 0.5)));
        assertFalse(lightning.conditionsMatch(new EventContext(0.50, true, 0.5)));
    }

    @Test
    void smokeEventsBuildsGhastSpawnWithNoPrecipitationRequirement() {
        var config = configWith(null, new DynamicAtmosphereServerConfig.GhastSpawn(0.90, 0.0001, 0.20), null, null);
        var events = AtmosphericEvents.smokeEvents(config);
        assertEquals(1, events.size());
        var ghast = events.get(0);
        assertEquals("ghast_spawn", ghast.id());
        assertEquals(TargetSelection.SELF, ghast.target());
        assertEquals("minecraft:ghast", ghast.actions().get(0).param("entity"));
        // No Precipitating condition: smoke never precipitates, so it must not gate on it.
        assertTrue(ghast.conditionsMatch(new EventContext(0.95, false, 0)));
    }

    @Test
    void zombieFogEventsBuildsAnActionlessHeatSink() {
        var config = configWith(null, null,
            new DynamicAtmosphereServerConfig.ZombieFog(24, 0.30, 2.0, 0.5, 0.05, 0.08), null);
        var events = AtmosphericEvents.zombieFogEvents(config);
        assertEquals(1, events.size());
        var heatSink = events.get(0);
        assertTrue(heatSink.actions().isEmpty(), "a sink consumes material without any action");
        assertTrue(heatSink.conditionsMatch(new EventContext(0.5, false, 0.6)));
        assertFalse(heatSink.conditionsMatch(new EventContext(0.5, false, 0.4)));
    }

    @Test
    void zombieFogSpawnModifiersMatchesSpecExampleExactly() {
        // Spec section 45: "Zombie Fog: zombie spawn multiplier = 2.0" once concentration >= 0.30.
        var config = configWith(null, null,
            new DynamicAtmosphereServerConfig.ZombieFog(24, 0.30, 2.0, 0.5, 0.05, 0.08), null);
        var modifiers = AtmosphericEvents.zombieFogSpawnModifiers(config);
        assertEquals(1, modifiers.size());
        var modifier = modifiers.get(0);
        assertEquals("minecraft:zombie", modifier.entityId());
        assertEquals(2.0, modifier.multiplier());
        assertTrue(modifier.conditionsMatch(new EventContext(0.30, false, 0)));
        assertFalse(modifier.conditionsMatch(new EventContext(0.29, false, 0)));
    }

    @Test
    void slimePrecipitationBuildsRainAndSnowBySpecExample() {
        var config = configWith(null, null, null,
            new DynamicAtmosphereServerConfig.SlimePrecipitation(0.65, 0.15, 0.02, 0.01, 0.65, 0.15, 0.002, 0.04, 8));
        var rules = AtmosphericEvents.slimePrecipitation(config);
        assertEquals(2, rules.size());
        var rain = rules.get(0);
        assertEquals("rain", rain.mode());
        assertEquals(PrecipitationDirection.DOWN, rain.direction());
        assertEquals(AtmosphericAction.Type.SPAWN_ITEM, rain.actions().get(0).action().type());
        assertEquals("minecraft:slime_ball", rain.actions().get(0).action().param("item"));
        assertEquals(0.02, rain.actions().get(0).chance());
        assertTrue(rain.conditionsMatch(new EventContext(0.70, false, 0.20)));
        assertFalse(rain.conditionsMatch(new EventContext(0.70, false, 0.10)), "too cold for rain mode");

        var snow = rules.get(1);
        assertEquals("snow", snow.mode());
        assertEquals(AtmosphericAction.Type.PLACE_BLOCK, snow.actions().get(0).action().type());
        assertEquals("minecraft:slime_block", snow.actions().get(0).action().param("block"));
        assertTrue(snow.conditionsMatch(new EventContext(0.70, false, 0.10)));
        assertFalse(snow.conditionsMatch(new EventContext(0.70, false, 0.20)), "too warm for snow mode");
    }
}
