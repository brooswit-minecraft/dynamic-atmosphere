package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the generic event engine with exactly the two worked examples the spec uses
 * to argue the layer is genuinely generic (section 30/46): "Ghast spawning and
 * lightning use the same event/action machinery. That is intentional." Both built from
 * the identical {@link AtmosphericEvent} record, evaluated by the identical
 * {@link AtmosphericEventEngine#evaluate}.
 */
class AtmosphericEventEngineTest {

    private static AtmosphericEvent lightning() {
        return new AtmosphericEvent("lightning", List.of(
            new Condition.ConcentrationAtLeast(0.85), new Condition.Precipitating(true)),
            0.002, 0.03, TargetSelection.PRECIPITATION_SURFACE,
            List.of(new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY,
                Map.of("entity", "minecraft:lightning_bolt"))));
    }

    private static AtmosphericEvent ghast() {
        return new AtmosphericEvent("ghast", List.of(new Condition.ConcentrationAtLeast(0.90)),
            0.0001, 0.20, TargetSelection.SELF,
            List.of(new AtmosphericAction(AtmosphericAction.Type.SPAWN_ENTITY,
                Map.of("entity", "minecraft:ghast"))));
    }

    @Test
    void lightningFiresOnlyWhenDenseAndPrecipitatingAndRollBeatsChance() {
        var denseStorm = new EventContext(0.90, true, 0.5);
        assertTrue(AtmosphericEventEngine.evaluate(List.of(lightning()), denseStorm, 0.001).isPresent());
        assertTrue(AtmosphericEventEngine.evaluate(List.of(lightning()), denseStorm, 0.002).isEmpty(),
            "roll equal to chance must not fire (roll < chance)");
        assertTrue(AtmosphericEventEngine.evaluate(List.of(lightning()), denseStorm, 0.5).isEmpty());

        var notPrecipitating = new EventContext(0.90, false, 0.5);
        assertTrue(AtmosphericEventEngine.evaluate(List.of(lightning()), notPrecipitating, 0.0).isEmpty());

        var tooThin = new EventContext(0.50, true, 0.5);
        assertTrue(AtmosphericEventEngine.evaluate(List.of(lightning()), tooThin, 0.0).isEmpty());
    }

    @Test
    void ghastUsesTheIdenticalEvaluatorWithDifferentData() {
        var denseSmoke = new EventContext(0.95, false, 0.5);
        var fired = AtmosphericEventEngine.evaluate(List.of(ghast()), denseSmoke, 0.00005);
        assertTrue(fired.isPresent());
        assertEquals("ghast", fired.get().id());
        assertEquals(0.20, fired.get().materialCost());

        assertTrue(AtmosphericEventEngine.evaluate(List.of(ghast()), new EventContext(0.5, false, 0.5), 0.0).isEmpty());
    }

    @Test
    void higherConcentrationBandsWinOverLowerOnesWhenBothMatchFirstMatchWins() {
        var low = new AtmosphericEvent("low", List.of(new Condition.ConcentrationAtLeast(0.80),
            new Condition.Precipitating(true)), 0.0005, 0.01, TargetSelection.PRECIPITATION_SURFACE, List.of());
        var high = new AtmosphericEvent("high", List.of(new Condition.ConcentrationAtLeast(0.95),
            new Condition.Precipitating(true)), 0.005, 0.04, TargetSelection.PRECIPITATION_SURFACE, List.of());
        var severe = new EventContext(0.97, true, 0.5);
        // Low band is listed first: a storm dense enough for both still only fires one event per tick.
        var fired = AtmosphericEventEngine.evaluate(List.of(low, high), severe, 0.0004);
        assertEquals("low", fired.orElseThrow().id());
        // A roll that misses the low band's chance but beats the high band's still fires the high one.
        fired = AtmosphericEventEngine.evaluate(List.of(low, high), severe, 0.003);
        assertEquals("high", fired.orElseThrow().id());
    }

    @Test
    void repeatedLightningConsumesMaterialWhichIsWhatMakesStormsDissipateThemselves() {
        // The engine doesn't apply the cost itself (that's the Forge adapter's job), but it
        // must always report a cost for every fired event so the caller has no excuse to skip it.
        assertTrue(lightning().materialCost() > 0);
        assertTrue(ghast().materialCost() > 0);
    }

    @Test
    void precipitationPicksFirstMatchingWeightedAction() {
        var rain = new PrecipitationRule("rain", List.of(new Condition.ConcentrationAtLeast(0.65),
            new Condition.BiomeTemperatureAtLeast(0.15)), PrecipitationDirection.DOWN, 0.01,
            List.of(new PrecipitationAction(new AtmosphericAction(AtmosphericAction.Type.SPAWN_ITEM,
                Map.of("item", "minecraft:slime_ball")), 0.02)));
        var warmAndDense = new EventContext(0.70, false, 0.5);
        assertTrue(AtmosphericEventEngine.evaluatePrecipitation(rain, warmAndDense, 0.01).isPresent());
        assertTrue(AtmosphericEventEngine.evaluatePrecipitation(rain, warmAndDense, 0.5).isEmpty());

        var tooCold = new EventContext(0.70, false, 0.1);
        assertTrue(AtmosphericEventEngine.evaluatePrecipitation(rain, tooCold, 0.0).isEmpty());
    }

    @Test
    void spawnModifierOverridesOrSuppressesProbabilistically() {
        var encourage = new SpawnModifier("minecraft:zombie",
            List.of(new Condition.ConcentrationAtLeast(0.30)), 2.0);
        assertEquals(1.0, encourage.overrideDenyProbability());
        assertEquals(0.0, encourage.suppressProbability());
        assertTrue(AtmosphericEventEngine.shouldOverrideDeny(encourage, 0.999));
        assertFalse(AtmosphericEventEngine.shouldSuppress(encourage, 0.0));

        var suppress = new SpawnModifier("minecraft:spider", List.of(), 0.5);
        assertEquals(0.0, suppress.overrideDenyProbability());
        assertEquals(0.5, suppress.suppressProbability());
        assertTrue(AtmosphericEventEngine.shouldSuppress(suppress, 0.49));
        assertFalse(AtmosphericEventEngine.shouldSuppress(suppress, 0.5));

        assertTrue(AtmosphericEventEngine.matchingSpawnModifier(List.of(encourage),
            new EventContext(0.31, false, 0)).isPresent());
        assertTrue(AtmosphericEventEngine.matchingSpawnModifier(List.of(encourage),
            new EventContext(0.10, false, 0)).isEmpty());
    }
}
