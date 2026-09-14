package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereFanExchangeTest {
    private static final boolean[] OPEN = {true, true, true, true, true, true};

    @Test
    void positiveFanPullsEvenlyIntoEmptyCellThenPushesForward() {
        var result = exchange(256, 0, filled(1000), filled(1000), false, 0);
        assertArrayEquals(new int[] {0, 52, 51, 51, 51, 51}, result.pulled());
        assertArrayEquals(new int[] {256, 0, 0, 0, 0, 0}, result.pushed());
        assertEquals(0, result.centerAmount());
    }

    @Test
    void negativeFanPullsFromFacingCellAndDistributesEvenlyElsewhere() {
        var result = exchange(256, 0, new int[] {500, 0, 0, 0, 0, 0}, filled(1000), true, 0);
        assertArrayEquals(new int[] {256, 0, 0, 0, 0, 0}, result.pulled());
        assertArrayEquals(new int[] {0, 52, 51, 51, 51, 51}, result.pushed());
        assertEquals(0, result.centerAmount());
    }

    @Test
    void rotationChangesRemainderRecipientInsteadOfFavoringAnAxis() {
        var result = exchange(256, 0, filled(1000), filled(1000), false, 3);
        assertArrayEquals(new int[] {0, 51, 51, 52, 51, 51}, result.pulled());
    }

    @Test
    void shortagesRedistributeBudgetAndExistingCenterMaterialCanSupplyOutput() {
        var result = exchange(256, 0, new int[] {0, 1, 1000, 0, 0, 0}, filled(1000), false, 0);
        assertArrayEquals(new int[] {0, 1, 255, 0, 0, 0}, result.pulled());
        assertEquals(256, result.pushed()[0]);
        var stored = exchange(256, 300, filled(0), filled(1000), false, 0);
        assertEquals(256, stored.pushed()[0]);
        assertEquals(44, stored.centerAmount());
    }

    @Test
    void blockedOutletRetainsIntakeAsPressureButSolidCenterCannotReceive() {
        var capacities = filled(1000);
        capacities[0] = 0;
        var result = exchange(256, 0, filled(1000), capacities, false, 0);
        assertEquals(256, result.centerAmount());
        assertEquals(0, Arrays.stream(result.pushed()).sum());
        var solid = AtmosphereFanTransport.exchange(256, 0, 0, filled(1000), filled(1000), OPEN, OPEN, 0, false, 0);
        assertEquals(0, Arrays.stream(solid.pulled()).sum());
    }

    @Test
    void respectsEdgeBarriersAndUnloadedCellsButOverfillsAvailableOutlets() {
        var pull = OPEN.clone();
        pull[1] = false;
        var capacities = filled(1);
        capacities[2] = -1;
        var result = AtmosphereFanTransport.exchange(256, 0, 1, filled(2000), capacities, pull, OPEN, 0, false, 0);
        assertEquals(0, result.pulled()[1]);
        assertEquals(0, result.pulled()[2]);
        assertEquals(256, result.pushed()[0]);
        var push = OPEN.clone();
        push[3] = false;
        var inverse = AtmosphereFanTransport.exchange(256, 0, 1, filled(2000), filled(1), OPEN, push, 0, true, 0);
        assertEquals(0, inverse.pushed()[3]);
        assertEquals(256, Arrays.stream(inverse.pushed()).sum());
    }

    @Test
    void storageCeilingNeverDestroysIntakeOrOutputMaterial() {
        int max = AtmosphereGrid.MAX_STORED_AMOUNT;
        var result = exchange(256, max - 10, filled(max - 5), filled(1000), false, 0);
        assertEquals(10, Arrays.stream(result.pulled()).sum());
        assertEquals(5, result.pushed()[0]);
        assertEquals(max - 5, result.centerAmount());
    }

    @Test
    void conservesMassAcrossRandomAmountsDirectionsAndBlockedEdges() {
        var random = new Random(834);
        for (int trial = 0; trial < 2000; trial++) {
            int center = random.nextInt(AtmosphereGrid.MAX_STORED_AMOUNT + 1);
            int[] neighbors = new int[6];
            int[] capacities = new int[6];
            boolean[] pull = new boolean[6], push = new boolean[6];
            for (int i = 0; i < 6; i++) {
                neighbors[i] = random.nextInt(AtmosphereGrid.MAX_STORED_AMOUNT + 1);
                capacities[i] = random.nextInt(1002) - 1;
                pull[i] = random.nextBoolean();
                push[i] = random.nextBoolean();
            }
            int requested = random.nextInt(1001);
            var result = AtmosphereFanTransport.exchange(requested, center, 1000, neighbors,
                capacities, pull, push, random.nextInt(6), random.nextBoolean(), trial);
            int before = center + Arrays.stream(neighbors).sum();
            int after = result.centerAmount();
            for (int i = 0; i < 6; i++) {
                int amount = neighbors[i] - result.pulled()[i] + result.pushed()[i];
                assertTrue(amount >= 0 && amount <= AtmosphereGrid.MAX_STORED_AMOUNT);
                assertFalse(result.pulled()[i] > 0 && result.pushed()[i] > 0);
                after += amount;
            }
            assertEquals(before, after);
            assertTrue(result.centerAmount() >= 0 && result.centerAmount() <= AtmosphereGrid.MAX_STORED_AMOUNT);
            assertTrue(Arrays.stream(result.pulled()).sum() <= requested);
            assertTrue(Arrays.stream(result.pushed()).sum() <= requested);
        }
    }

    private static AtmosphereFanTransport.Exchange exchange(int requested, int center,
        int[] neighbors, int[] capacities, boolean reverse, int rotation) {
        return AtmosphereFanTransport.exchange(requested, center, 1000, neighbors, capacities,
            OPEN, OPEN, 0, reverse, rotation);
    }

    private static int[] filled(int amount) {
        int[] values = new int[6];
        Arrays.fill(values, amount);
        return values;
    }
}
