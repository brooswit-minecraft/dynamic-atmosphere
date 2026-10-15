package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FillBiasedVerticalMovementTest {
    @Test
    void interpolatesContinuouslyAndComplementsDownwardProbability() {
        assertProbabilities(0, 0.75, 0.25);
        assertProbabilities(50, 0.50, 0.50);
        assertProbabilities(100, 0.25, 0.75);
    }

    @Test
    void atOrAboveCloudlineAlwaysChoosesDownBecauseBothDirectionsCollapse() {
        assertEquals(FillBiasedVerticalMovement.Direction.DOWN,
            FillBiasedVerticalMovement.choose(64, 64, 0, 100, .75, .25, () -> 0));
        assertEquals(FillBiasedVerticalMovement.Direction.DOWN,
            FillBiasedVerticalMovement.choose(65, 64, 0, 100, .75, .25, () -> .999));
    }

    private static void assertProbabilities(int amount, double expectedCloudline, double expectedDown) {
        var probabilities = FillBiasedVerticalMovement.probabilities(amount, 100, .75, .25);
        assertEquals(expectedCloudline, probabilities.towardCloudline(), 0.000001);
        assertEquals(expectedDown, probabilities.down(), 0.000001);
        assertEquals(1.0, probabilities.towardCloudline() + probabilities.down(), 0.000001);
    }
}
