package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for a review finding on MINECRAFT-1 (B3): sampleNetherFogChunk's harvest used to
 * destroy a lava source and then call emitMaterial without checking whether the emission
 * actually delivered the full amount -- AtmosphereGrid#emit clamps at MAX_STORED_AMOUNT and can
 * refuse outright, so a saturated or unreadable destination could lose lava for a partial or
 * zero fog gain, breaking the harvest<->condense conservation claim. These exercise
 * {@link ForgeAtmospherePrototype#hasExactHeadroom}, the actual production precheck the harvest
 * now runs before touching any block -- not a reimplementation fed arbitrary units.
 */
class ForgeAtmospherePrototypeHeadroomTest {
    @Test
    void hasHeadroomWhenWellUnderTheCeiling() {
        assertTrue(ForgeAtmospherePrototype.hasExactHeadroom(0, 15));
        assertTrue(ForgeAtmospherePrototype.hasExactHeadroom(500, 15));
    }

    @Test
    void hasHeadroomAtTheExactCeilingButNotOneUnitPast() {
        int amount = 15;
        int atCeiling = AtmosphereGrid.MAX_STORED_AMOUNT - amount;
        assertTrue(ForgeAtmospherePrototype.hasExactHeadroom(atCeiling, amount));
        assertFalse(ForgeAtmospherePrototype.hasExactHeadroom(atCeiling + 1, amount));
    }

    @Test
    void declinesWhenAlreadySaturated() {
        assertFalse(ForgeAtmospherePrototype.hasExactHeadroom(AtmosphereGrid.MAX_STORED_AMOUNT, 15));
        assertFalse(ForgeAtmospherePrototype.hasExactHeadroom(AtmosphereGrid.MAX_STORED_AMOUNT, 1));
    }

    @Test
    void declinesANonPositiveAmountRatherThanTreatingItAsFree() {
        assertFalse(ForgeAtmospherePrototype.hasExactHeadroom(0, 0));
        assertFalse(ForgeAtmospherePrototype.hasExactHeadroom(0, -1));
    }

    @Test
    void neverOverflowsWhenCurrentIsAlreadyNearIntegerMax() {
        // A defensive case: the check must not wrap around when summing near Integer.MAX_VALUE.
        assertFalse(ForgeAtmospherePrototype.hasExactHeadroom(Integer.MAX_VALUE - 1, 15));
    }
}
