package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class AtmospherePlantGrowthTest {
    @Test
    void insufficientMaterialDoesNotScanRollOrGrow() {
        assertEquals(0, AtmospherePlantGrowth.attempt(39, 4,
            () -> { fail("no roll without full cost"); return 0; },
            index -> { fail("no scan without full cost"); return true; },
            index -> { fail("no growth without full cost"); return true; }));
    }

    @Test
    void everyEligiblePlantGetsAnIndependentChanceWithinTheCell() {
        var rolls = new ArrayList<>(List.of(0.01, 0.5, 0.09));
        var grown = new ArrayList<Integer>();
        int consumed = AtmospherePlantGrowth.attempt(120, 2,
            () -> rolls.removeFirst(),
            index -> index == 1 || index == 3 || index == 7,
            index -> { grown.add(index); return true; });
        assertEquals(80, consumed);
        assertEquals(List.of(1, 7), grown);
        assertEquals(0, rolls.size());
    }

    @Test
    void failedBonemealConsumesNothingAndLaterPlantsStillTry() {
        var attempts = new ArrayList<Integer>();
        int consumed = AtmospherePlantGrowth.attempt(40, 2, () -> 0,
            index -> index == 2 || index == 5,
            index -> { attempts.add(index); return index == 5; });
        assertEquals(40, consumed);
        assertEquals(List.of(2, 5), attempts);
    }

    @Test
    void scansAreBoundedToTheProcessedCell() {
        var scans = new AtomicInteger();
        assertEquals(0, AtmospherePlantGrowth.attempt(40, 4, () -> 0,
            index -> {
                assertEquals(scans.getAndIncrement(), index);
                return false;
            }, index -> { fail("no eligible plant"); return true; }));
        assertEquals(64, scans.get());
    }

    @Test
    void invalidRollsDoNotApplyBonemealAndCellSizeIsBounded() {
        for (double roll : new double[]{0.1, 1, -0.1, Double.NaN}) {
            assertEquals(0, AtmospherePlantGrowth.attempt(40, 2, () -> roll,
                index -> index == 0,
                index -> { fail("failed chance must not grow"); return true; }));
        }
        assertThrows(IllegalArgumentException.class,
            () -> AtmospherePlantGrowth.attempt(40, 0, () -> 0, index -> true, index -> true));
    }
}
