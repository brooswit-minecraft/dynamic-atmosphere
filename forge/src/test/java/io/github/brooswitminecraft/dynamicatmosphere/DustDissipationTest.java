package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DustDissipationTest {
    @Test
    void successfulRollConsumesRemainingDustUpToForty() {
        assertEquals(1, DustDissipation.amount(1, () -> 0));
        assertEquals(39, DustDissipation.amount(39, () -> 0));
        assertEquals(40, DustDissipation.amount(40, () -> 0));
        assertEquals(40, DustDissipation.amount(1_000_000, () -> 0));
    }

    @Test
    void chanceHasExactIndependentOneIn64Boundary() {
        assertEquals(40, DustDissipation.amount(40, () -> Math.nextDown(1.0 / 64)));
        for (double roll : new double[]{1.0 / 64, 0.5, 1, Double.NaN, -1}) {
            assertEquals(0, DustDissipation.amount(40, () -> roll));
        }
    }

    @Test
    void absentDustDoesNotRoll() {
        for (int amount : new int[]{0, -1}) {
            assertEquals(0, DustDissipation.amount(amount,
                () -> { fail("No Dust to dissipate"); return 0; }));
        }
    }

    @Test
    void zeroUsesExistingDirtyRemovalAndOtherMaterialIsUntouched() {
        var dust = new AtmosphereGrid<String>();
        var obsidianPowder = new AtmosphereGrid<String>();
        var key = new AtmosphereGrid.CellKey<>("world", 0, 0, 0);
        dust.set(key, 7, 0, 1000);
        obsidianPowder.set(key, 7, 0, 1000);
        dust.drainDirtyKeys();

        int remaining = dust.get(key).amount();
        dust.set(key, remaining - DustDissipation.amount(remaining, () -> 0), 1, 1000);

        assertNull(dust.get(key));
        assertTrue(dust.drainDirtyKeys().contains(key));
        assertEquals(7, obsidianPowder.get(key).amount());
    }
}
