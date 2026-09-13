package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereChunkDataTest {
    @Test
    void sparseRoundTripPreservesNegativeCoordinatesAndPressureAmounts() {
        var cells = List.of(new AtmosphereChunkData.Cell(-1, -16, -5, 1_000_000),
            new AtmosphereChunkData.Cell(-4, 79, -8, 1));
        var expected = AtmosphereChunkData.validate(-1, -2, -64, 320, cells);
        assertEquals(expected, AtmosphereChunkData.decode(-1, -2, -64, 320, AtmosphereChunkData.encode(cells)));
        assertEquals(1_000_001, expected.stream().mapToInt(AtmosphereChunkData.Cell::amount).sum());
    }

    @Test
    void snapshotOwnsItsDataAndHasStableOrder() {
        var input = new ArrayList<>(List.of(new AtmosphereChunkData.Cell(3, 0, 3, 100),
            new AtmosphereChunkData.Cell(0, 0, 0, 200)));
        var snapshot = AtmosphereChunkData.validate(0, 0, -64, 320, input);
        input.clear();
        assertEquals(2, snapshot.size());
        assertEquals(0, snapshot.getFirst().x());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        int[] encoded = AtmosphereChunkData.encode(snapshot);
        var restored = AtmosphereChunkData.decode(0, 0, -64, 320, encoded);
        encoded[3] = 0;
        assertEquals(snapshot, restored);
    }

    @Test
    void emptySnapshotPersistsRemovalWithoutCapacityOrDimensionFields() {
        assertArrayEquals(new int[0], AtmosphereChunkData.encode(List.of()));
        assertTrue(AtmosphereChunkData.decode(0, 0, -64, 320, new int[0]).isEmpty());
        assertArrayEquals(new int[]{0, 1, 2, 5000},
            AtmosphereChunkData.encode(List.of(new AtmosphereChunkData.Cell(0, 1, 2, 5000))));
    }

    @Test
    void rejectsCrossChunkOutOfHeightDuplicateAndInvalidAmounts() {
        for (var cell : List.of(new AtmosphereChunkData.Cell(-1, 0, 0, 1),
            new AtmosphereChunkData.Cell(4, 0, 0, 1),
            new AtmosphereChunkData.Cell(0, 0, 4, 1),
            new AtmosphereChunkData.Cell(0, -17, 0, 1),
            new AtmosphereChunkData.Cell(0, 80, 0, 1),
            new AtmosphereChunkData.Cell(0, 0, 0, 0),
            new AtmosphereChunkData.Cell(0, 0, 0, -1),
            new AtmosphereChunkData.Cell(0, 0, 0, 1_000_001))) {
            assertThrows(IllegalArgumentException.class,
                () -> AtmosphereChunkData.validate(0, 0, -64, 320, List.of(cell)));
        }
        var cell = new AtmosphereChunkData.Cell(0, 0, 0, 1);
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereChunkData.validate(0, 0, -64, 320, List.of(cell, cell)));
    }

    @Test
    void rejectsMalformedOrPhysicallyOversizedEncoding() {
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereChunkData.decode(0, 0, -64, 320, new int[]{0, 0, 0}));
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereChunkData.decode(0, 0, 0, 4, new int[17 * 4]));
        assertThrows(IllegalArgumentException.class,
            () -> AtmosphereChunkData.decode(0, 0, 4, 4, new int[0]));
    }

    @Test
    void moreThanOldGlobalLimitFitsOnePhysicalChunk() {
        var cells = new ArrayList<AtmosphereChunkData.Cell>();
        for (int x = 0; x < 4; x++) {
            for (int y = -16; y < 80; y++) {
                for (int z = 0; z < 4; z++) {
                    cells.add(new AtmosphereChunkData.Cell(x, y, z, 1000));
                }
            }
        }
        assertEquals(1536, AtmosphereChunkData.decode(0, 0, -64, 320, AtmosphereChunkData.encode(cells)).size());
    }
}
