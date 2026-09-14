package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeProducerRulesTest {
    @Test
    void conservativeOngoingAmountsRequireLitCombustionBlocks() {
        assertEquals(40, SmokeProducerRules.ongoing(SmokeProducerRules.Source.FIRE, true));
        assertEquals(40, SmokeProducerRules.ongoing(SmokeProducerRules.Source.LAVA, true));
        assertEquals(20, SmokeProducerRules.ongoing(SmokeProducerRules.Source.FURNACE, true));
        assertEquals(20, SmokeProducerRules.ongoing(SmokeProducerRules.Source.CAMPFIRE, true));
        assertEquals(2, SmokeProducerRules.ongoing(SmokeProducerRules.Source.TORCH, true));
        for (var source : new SmokeProducerRules.Source[]{SmokeProducerRules.Source.FURNACE,
            SmokeProducerRules.Source.CAMPFIRE, SmokeProducerRules.Source.TORCH, SmokeProducerRules.Source.NONE}) {
            assertEquals(0, SmokeProducerRules.ongoing(source, false));
        }
    }

    @Test
    void realFireAndLavaAdditionsAndRemovalsEmitButAgeAndFluidLevelsDoNot() {
        assertEquals(40, SmokeProducerRules.transition(true, false, true, false, false, false));
        assertEquals(40, SmokeProducerRules.transition(true, true, false, false, false, false));
        assertEquals(40, SmokeProducerRules.transition(true, false, false, false, true, false));
        assertEquals(40, SmokeProducerRules.transition(true, false, false, true, false, false));
        assertEquals(0, SmokeProducerRules.transition(true, true, true, false, false, false));
        assertEquals(0, SmokeProducerRules.transition(true, false, false, true, true, false));
        assertEquals(0, SmokeProducerRules.transition(false, false, false, true, false, false));
    }

    @Test
    void transportCannotAmplifyMaterialEvenWhenPresenceChanges() {
        assertEquals(0, SmokeProducerRules.transition(true, false, false, true, false, true));
        assertEquals(0, SmokeProducerRules.transition(true, false, false, false, true, true));
        assertEquals(0, SmokeProducerRules.transition(true, true, false, false, true, true));
    }

    @Test
    void emptyExplosionStillBurstsButCanceledAndUnbrokenBlocksDoNot() {
        assertEquals(80, SmokeProducerRules.explosionBurst(true));
        assertEquals(0, SmokeProducerRules.explosionBurst(false));
        assertEquals(10, SmokeProducerRules.explosionBlock(true, false, true));
        assertEquals(0, SmokeProducerRules.explosionBlock(false, false, true));
        assertEquals(0, SmokeProducerRules.explosionBlock(true, true, true));
        assertEquals(0, SmokeProducerRules.explosionBlock(true, false, false));
    }
}
