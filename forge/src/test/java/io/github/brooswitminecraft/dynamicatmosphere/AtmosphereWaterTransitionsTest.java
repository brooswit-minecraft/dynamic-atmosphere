package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosphereWaterTransitionsTest {
    @Test
    void evaporationColumnWalksContiguousWaterAndDetectsImmediateMagma() {
        Set<Integer> water = Set.of(70, 69, 68);
        var column = AtmosphereWaterTransitions.evaporationColumn(
            70, -64, water::contains, y -> y == 67);

        assertEquals(68, column.bottomY());
        assertEquals(true, column.magma());
        assertEquals(true, column.removesSurface(70));
    }

    @Test
    void evaporationColumnStopsAtGapAndIgnoresDeeperMagma() {
        Set<Integer> water = Set.of(70, 68);
        var column = AtmosphereWaterTransitions.evaporationColumn(
            70, -64, water::contains, y -> y == 67);

        assertEquals(70, column.bottomY());
        assertEquals(false, column.magma());
        assertEquals(false, column.removesSurface(70));
    }

    @Test
    void evaporationColumnNeverQueriesBelowBuildHeightOrDuplicatesSingleLayer() {
        Set<Integer> queriedWater = new java.util.HashSet<>();
        Set<Integer> queriedMagma = new java.util.HashSet<>();
        var column = AtmosphereWaterTransitions.evaporationColumn(0, 0,
            y -> {
                queriedWater.add(y);
                return true;
            },
            y -> {
                queriedMagma.add(y);
                return true;
            });

        assertEquals(0, column.bottomY());
        assertEquals(false, column.magma());
        assertEquals(false, column.removesSurface(0));
        assertEquals(Set.of(), queriedWater);
        assertEquals(Set.of(), queriedMagma);
    }

    @Test
    void biomeTemperatureChanceIsClampedAndHotterMeansMoreEvaporation() {
        assertEquals(0, AtmosphereWaterTransitions.evaporationChance(-1));
        assertEquals(0, AtmosphereWaterTransitions.evaporationChance(0));
        assertEquals(0.4, AtmosphereWaterTransitions.evaporationChance(0.8), 1e-9);
        assertEquals(1, AtmosphereWaterTransitions.evaporationChance(2));
        assertEquals(1, AtmosphereWaterTransitions.evaporationChance(3));
        assertEquals(0, AtmosphereWaterTransitions.evaporationChance(Double.NaN));
    }

    @Test
    void magmaBypassesTemperatureChanceWithoutReadingTheRoll() {
        assertEquals(true, AtmosphereWaterTransitions.evaporationPasses(true, 0,
            () -> {
                throw new AssertionError("Magma must not consume a temperature roll");
            }));
        assertEquals(false, AtmosphereWaterTransitions.evaporationPasses(false, 0, () -> 0));
        assertEquals(true, AtmosphereWaterTransitions.evaporationPasses(false, 0.8, () -> 0.39));
        assertEquals(false, AtmosphereWaterTransitions.evaporationPasses(false, 0.8, () -> 0.4));
    }

    @Test
    void biomeDownfallScalesRemovalMaterialFromTenToEighty() {
        assertEquals(10, AtmosphereWaterTransitions.materialForHumidity(-1));
        assertEquals(10, AtmosphereWaterTransitions.materialForHumidity(0));
        assertEquals(38, AtmosphereWaterTransitions.materialForHumidity(0.4));
        assertEquals(45, AtmosphereWaterTransitions.materialForHumidity(0.5));
        assertEquals(80, AtmosphereWaterTransitions.materialForHumidity(1));
        assertEquals(80, AtmosphereWaterTransitions.materialForHumidity(2));
        assertEquals(10, AtmosphereWaterTransitions.materialForHumidity(Double.NaN));
    }

    @Test
    void evaporationPreservesHostsAndOnlyRemovesWater() {
        assertEquals(AtmosphereWaterTransitions.EvaporationAction.REMOVE_FLUID,
            AtmosphereWaterTransitions.evaporationAction(true, false, true));
        assertEquals(AtmosphereWaterTransitions.EvaporationAction.DRAIN_WATERLOGGED,
            AtmosphereWaterTransitions.evaporationAction(true, true, false));
        assertEquals(AtmosphereWaterTransitions.EvaporationAction.KEEP,
            AtmosphereWaterTransitions.evaporationAction(true, false, false));
        assertEquals(AtmosphereWaterTransitions.EvaporationAction.KEEP,
            AtmosphereWaterTransitions.evaporationAction(false, false, true));
        assertEquals(AtmosphereWaterTransitions.EvaporationAction.KEEP,
            AtmosphereWaterTransitions.evaporationAction(false, true, false));
        assertEquals(AtmosphereWaterTransitions.EvaporationAction.KEEP,
            AtmosphereWaterTransitions.evaporationAction(false, false, false));
    }

    @Test
    void onlySuccessfulWaterToNonWaterMutationEmitsMaterial() {
        assertEquals(45, AtmosphereWaterTransitions.materialForTransition(true, true, false, 0.5));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(false, true, false, 0.5));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(true, true, true, 0.5));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(true, false, false, 0.5));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(true, false, true, 0.5));
    }

    @Test
    void repeatedTransitionsCoalescePerCellWithoutLosingMaterial() {
        var pending = new LinkedHashMap<String, AtmosphereWaterTransitions.Pending>();
        AtmosphereWaterTransitions.coalesce(pending, "cell", 10);
        AtmosphereWaterTransitions.coalesce(pending, "cell", 80);
        AtmosphereWaterTransitions.coalesce(pending, "other", 40);
        AtmosphereWaterTransitions.coalesce(pending, "cell", 0);

        assertEquals(90, pending.get("cell").material());
        assertEquals(2, pending.get("cell").removals());
        assertEquals(40, pending.get("other").material());
        assertEquals(1, pending.get("other").removals());
    }
}
