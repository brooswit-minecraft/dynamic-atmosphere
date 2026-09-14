package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosphereFanTransportTest {
    @Test
    void onlyMovesMaterialsWithCellsNoLargerThanFourBlocks() {
        for (int size : new int[] {1, 2, 4}) assertEquals(true, AtmosphereFanTransport.supportsCellSize(size));
        for (int size : new int[] {-1, 0, 5, 8, 16}) assertEquals(false, AtmosphereFanTransport.supportsCellSize(size));
        for (var material : AtmosphereMaterial.values()) {
            assertEquals(material != AtmosphereMaterial.VIOLENCE && material != AtmosphereMaterial.SLIME,
                AtmosphereFanTransport.supportsCellSize(material.cellSize()));
        }
    }

    @Test
    void transferOverfillsCellsWithEmptySpaceButRespectsSourceStorageCeilingAndBarriers() {
        assertEquals(128, AtmosphereFanTransport.movableAmount(128, 600, 200, 1000, true));
        assertEquals(128, AtmosphereFanTransport.movableAmount(128, 600, 950, 1000, true));
        assertEquals(20, AtmosphereFanTransport.movableAmount(128, 20, 0, 1000, true));
        assertEquals(128, AtmosphereFanTransport.movableAmount(128, 600, 2000, 1, true));
        assertEquals(0, AtmosphereFanTransport.movableAmount(128, 600, 1000, 0, true));
        assertEquals(10, AtmosphereFanTransport.movableAmount(128, 600, 999990, 1000, true));
        assertEquals(0, AtmosphereFanTransport.movableAmount(128, 600, 1000000, 1000, true));
        assertEquals(0, AtmosphereFanTransport.movableAmount(128, 600, 0, -1, true));
        assertEquals(0, AtmosphereFanTransport.movableAmount(128, 600, 0, 1000, false));
    }

    @Test
    void scalesAbsoluteRpmAndDiscardsFractionalUnits() {
        assertEquals(0, amount(0, 0.10));
        assertEquals(0, amount(8, 0.10));
        assertEquals(12, amount(128, 0.10));
        assertEquals(12, amount(-128, 0.10));
        assertEquals(25, amount(256, 0.10));
        assertEquals(64, amount(128, 0.5));
    }

    @Test
    void rejectsInvalidInputsAndSupportsDisabling() {
        for (double rpm : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(0, amount(rpm, 0.10));
        }
        for (double coefficient : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY,
                                                Double.NEGATIVE_INFINITY}) {
            assertEquals(0, amount(128, coefficient));
        }
        assertEquals(0, AtmosphereFanTransport.requestedAmount(128, 1, 0));
        assertEquals(0, AtmosphereFanTransport.requestedAmount(128, 1, -1));
    }

    @Test
    void boundsEvenOverflowingProductsWithoutWrapping() {
        assertEquals(1000, amount(Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(1000, amount(-Double.MAX_VALUE, 1000));
        assertEquals(1000, amount(256, 1000));
        assertEquals(7, AtmosphereFanTransport.requestedAmount(128, 1, 7));
    }

    private static int amount(double rpm, double coefficient) {
        return AtmosphereFanTransport.requestedAmount(rpm, coefficient, 1000);
    }
}
