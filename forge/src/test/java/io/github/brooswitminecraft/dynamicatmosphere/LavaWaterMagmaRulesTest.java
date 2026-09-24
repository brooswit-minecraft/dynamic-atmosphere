package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaWaterMagmaRulesTest {
    @Test
    void waterReachesLavaSourceOrFlowingLavaAppliesClearsTheNeighborAndEmitsSmoke() {
        for (var proposed : new LavaWaterMagmaRules.ProposedBlock[] {
            LavaWaterMagmaRules.ProposedBlock.OBSIDIAN, LavaWaterMagmaRules.ProposedBlock.COBBLESTONE}) {
            var decision = LavaWaterMagmaRules.classify(proposed);
            assertTrue(decision.applies(), proposed.toString());
            assertTrue(decision.clearsNeighborWater(), proposed.toString());
            assertTrue(decision.emitsLavaSmoke(), proposed.toString());
        }
    }

    @Test
    void lavaSpreadingDownIntoWaterAppliesButTouchesNoNeighborAndNeverEmitsSmoke() {
        var decision = LavaWaterMagmaRules.classify(LavaWaterMagmaRules.ProposedBlock.STONE);
        assertTrue(decision.applies());
        assertFalse(decision.clearsNeighborWater());
        assertFalse(decision.emitsLavaSmoke());
    }

    @Test
    void anythingElseNeoForgeProposesThroughThisEventIsLeftAlone() {
        var decision = LavaWaterMagmaRules.classify(LavaWaterMagmaRules.ProposedBlock.OTHER);
        assertFalse(decision.applies());
        assertFalse(decision.clearsNeighborWater());
        assertFalse(decision.emitsLavaSmoke());
    }
}
