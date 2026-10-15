package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaWaterMagmaRulesTest {
    @Test
    void waterReachesLavaSourceOrFlowingLavaConsumesWaterAndEmitsSmoke() {
        for (var proposed : new LavaWaterMagmaRules.ProposedBlock[] {
            LavaWaterMagmaRules.ProposedBlock.OBSIDIAN, LavaWaterMagmaRules.ProposedBlock.COBBLESTONE}) {
            var decision = LavaWaterMagmaRules.classify(proposed);
            assertTrue(decision.applies());
            assertTrue(decision.clearsNeighborWater());
            assertTrue(decision.emitsLavaSmoke());
        }
    }

    @Test
    void lavaSpreadingDownIntoWaterLeavesTheSourceAndDoesNotEmitSmoke() {
        var decision = LavaWaterMagmaRules.classify(LavaWaterMagmaRules.ProposedBlock.STONE);
        assertTrue(decision.applies());
        assertFalse(decision.clearsNeighborWater());
        assertFalse(decision.emitsLavaSmoke());
    }

    @Test
    void unrelatedFluidPlacementsAreUnchanged() {
        assertFalse(LavaWaterMagmaRules.classify(LavaWaterMagmaRules.ProposedBlock.OTHER).applies());
    }
}
