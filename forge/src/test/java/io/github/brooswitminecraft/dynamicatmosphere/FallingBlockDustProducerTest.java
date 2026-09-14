package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FallingBlockDustProducerTest {
    @Test
    void landingUsesTheConservativeBurstAmount() {
        assertEquals(24, FallingBlockDustProducer.LANDING_AMOUNT);
    }
}
