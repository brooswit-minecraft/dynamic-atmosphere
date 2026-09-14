package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmokeChunkDataTest {
    @Test
    void roundTripsSparseCellsAndSortsThem() {
        var cells = List.of(
            new SmokeChunkData.Cell(1, 9, 0, 40),
            new SmokeChunkData.Cell(0, 8, 1, 80));
        int[] encoded = SmokeChunkData.encode(cells);

        assertEquals(List.of(
            new SmokeChunkData.Cell(0, 8, 1, 80),
            new SmokeChunkData.Cell(1, 9, 0, 40)),
            SmokeChunkData.decode(0, 0, -64, 320, encoded));
    }

    @Test
    void rejectsCellsOutsideOwningChunkAndDuplicatePositions() {
        assertThrows(IllegalArgumentException.class, () -> SmokeChunkData.validate(
            0, 0, -64, 320, List.of(new SmokeChunkData.Cell(4, 8, 0, 40))));
        assertThrows(IllegalArgumentException.class, () -> SmokeChunkData.validate(
            0, 0, -64, 320, List.of(
                new SmokeChunkData.Cell(0, 8, 0, 40),
                new SmokeChunkData.Cell(0, 8, 0, 80))));
    }

    @Test
    void legacyMigrationPreservesAmountsAndNegativeWorldCoordinates() {
        var cells = SmokeChunkData.migrateLegacy(-1, -1, -64, 320,
            new int[] {-1, -8, -1, 83});
        assertEquals(8, cells.size());
        assertEquals(83, cells.stream().mapToInt(SmokeChunkData.Cell::amount).sum());
        assertEquals(cells, SmokeChunkData.validate(-1, -1, -64, 320, cells));
        assertEquals(1, SmokeChunkData.migrateLegacy(0, 0, -64, 320,
            new int[] {0, 0, 0, 1}).size());
        assertThrows(IllegalArgumentException.class, () -> SmokeChunkData.migrateLegacy(0, 0, -64, 320,
            new int[] {2, 0, 0, 1}));
    }
}
