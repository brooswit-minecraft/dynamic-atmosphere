package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlimeGameplayTest {
    @Test
    void undergroundProductionRequiresSlimeChunkAndFiresUnconditionallyOtherwise() {
        assertEquals(1, SlimeGameplay.UNDERGROUND_CHANCE_DENOMINATOR);
        assertTrue(SlimeGameplay.undergroundEmission(true, 0));
        assertFalse(SlimeGameplay.undergroundEmission(false, 0));
    }

    @Test
    void highDensitySpawnIsChanceGatedAndFinite() {
        var spawn = SlimeGameplay.spawnDecision(750, 1_000, 0);
        assertTrue(spawn.spawn());
        assertEquals(250, spawn.cost());
        assertEquals(SlimeGameplay.SpawnDecision.NONE, SlimeGameplay.spawnDecision(749, 1_000, 0));
        assertEquals(SlimeGameplay.SpawnDecision.NONE, SlimeGameplay.spawnDecision(750, 1_000, 1));
        assertEquals(SlimeGameplay.SpawnDecision.NONE, SlimeGameplay.spawnDecision(1, 0, 0));
    }

    @Test
    void tinyCapacityStillRequiresNonzeroSpawnCost() {
        assertEquals(1, SlimeGameplay.spawnDecision(1, 1, 0).cost());
    }
}
