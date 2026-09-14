package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnowRemovalRulesTest {
    @Test
    void iceRemovalIncludesMeltingButNotReplacementWithinIceTagOrLaterWaterLoss() {
        assertEquals(40, SnowRemovalRules.iceMaterial(true, true, false));
        assertEquals(0, SnowRemovalRules.iceMaterial(true, true, true));
        assertEquals(0, SnowRemovalRules.iceMaterial(false, true, false));
        assertEquals(0, SnowRemovalRules.iceMaterial(true, false, true));
        assertEquals(0, SnowRemovalRules.iceMaterial(true, false, false));
    }

    @Test
    void fullBlockAndSingleLayerUseConservativeDefaults() {
        assertEquals(40, SnowRemovalRules.material(true, 8, 0));
        assertEquals(5, SnowRemovalRules.material(true, 1, 0));
    }

    @Test
    void partialRemovalsSumToOneFullRemovalWithoutDoubleCounting() {
        int gradual = SnowRemovalRules.material(true, 8, 5)
            + SnowRemovalRules.material(true, 5, 1)
            + SnowRemovalRules.material(true, 1, 0);
        assertEquals(40, gradual);
        assertEquals(15, SnowRemovalRules.material(true, 8, 5));
        assertEquals(0, SnowRemovalRules.material(true, 5, 5));
    }

    @Test
    void failedChangesAndSnowAdditionsDoNotEmit() {
        assertEquals(0, SnowRemovalRules.material(false, 8, 0));
        assertEquals(0, SnowRemovalRules.material(true, 0, 8));
        assertEquals(0, SnowRemovalRules.material(true, 2, 7));
        assertEquals(0, SnowRemovalRules.material(true, 0, 0));
    }

    @Test
    void equivalentFullSnowFormsDoNotManufactureMaterial() {
        assertEquals(0, SnowRemovalRules.material(true, 8, 8));
        assertThrows(IllegalArgumentException.class, () -> SnowRemovalRules.material(true, 9, 0));
        assertThrows(IllegalArgumentException.class, () -> SnowRemovalRules.material(true, 1, -1));
    }
}
