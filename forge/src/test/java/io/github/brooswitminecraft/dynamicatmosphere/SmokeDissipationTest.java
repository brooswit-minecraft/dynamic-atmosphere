package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeDissipationTest {
    @Test
    void successfulRollClearsResidueButCapsLargeAmounts() {
        assertEquals(1, SmokeDissipation.amount(1, () -> 0));
        assertEquals(39, SmokeDissipation.amount(39, () -> 0));
        assertEquals(40, SmokeDissipation.amount(40, () -> 0));
        assertEquals(40, SmokeDissipation.amount(1_000_000, () -> 0));
    }

    @Test
    void chanceHasExactOneIn64Boundary() {
        assertEquals(40, SmokeDissipation.amount(40, () -> Math.nextDown(1.0 / 64)));
        for (double roll : new double[]{1.0 / 64, 0.5, 1, Double.NaN, -1}) {
            assertEquals(0, SmokeDissipation.amount(40, () -> roll));
        }
    }

    @Test
    void absentMaterialDoesNotRoll() {
        for (int amount : new int[]{0, -1}) {
            assertEquals(0, SmokeDissipation.amount(amount,
                () -> { fail("No material to dissipate"); return 0; }));
        }
    }

    @Test
    void zeroResultUsesExistingDirtyRemovalWithoutAffectingVapor() {
        var smoke = new AtmosphereGrid<String>();
        var vapor = new AtmosphereGrid<String>();
        var key = new AtmosphereGrid.CellKey<>("world", 0, 0, 0);
        smoke.set(key, 7, 0, 1000);
        vapor.set(key, 7, 0, 1000);
        smoke.drainDirtyKeys();
        int remaining = smoke.get(key).amount();
        smoke.set(key, remaining - SmokeDissipation.amount(remaining, () -> 0), 1, 1000);
        assertNull(smoke.get(key));
        assertTrue(smoke.drainDirtyKeys().contains(key));
        assertEquals(7, vapor.get(key).amount());
    }
}
