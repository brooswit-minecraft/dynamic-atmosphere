package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialChunkDataTest {
    @Test
    void legacyEnderGasMergesEightCellsAndPreservesNegativeCoordinatesAndAmount() {
        var legacy = new java.util.ArrayList<MaterialChunkData.Cell>();
        for (int x = -2; x < 0; x++) for (int y = -2; y < 0; y++) for (int z = -2; z < 0; z++) {
            legacy.add(new MaterialChunkData.Cell(x, y, z, 100));
        }
        var migrated = MaterialChunkData.migrateEnderGas(-1, -1, -64, 320,
            MaterialChunkData.encode(legacy));
        assertEquals(List.of(new MaterialChunkData.Cell(-1, -1, -1, 800)), migrated);
        assertEquals(migrated, MaterialChunkData.decode(AtmosphereMaterial.ENDER_GAS,
            -1, -1, -64, 320, MaterialChunkData.encode(migrated)));
    }

    @Test
    void legacyEnderGasRejectsCorruptionAndOverflowInsteadOfLosingMaterial() {
        for (int[] values : new int[][] {
            {0}, {0, 0, 0, 1, 0, 0, 0, 1}, {16, 0, 0, 1}, {0, -65, 0, 1},
            {0, 0, 0, 1_000_000, 1, 0, 0, 1}
        }) {
            assertThrows(IllegalArgumentException.class,
                () -> MaterialChunkData.migrateEnderGas(0, 0, -64, 320, values));
        }
    }

    @Test
    void roundTripsAndSortsEachMaterialLayout() {
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            int x = material.cellCoordinate(31);
            int z = material.cellCoordinate(-17);
            int chunkX = material.chunkCoordinate(x);
            int chunkZ = material.chunkCoordinate(z);
            var cells = List.of(
                new MaterialChunkData.Cell(x, material.cellCoordinate(64), z, 40),
                new MaterialChunkData.Cell(x, material.cellCoordinate(48), z, 80));

            var decoded = MaterialChunkData.decode(material, chunkX, chunkZ, -64, 320,
                MaterialChunkData.encode(cells));

            assertEquals(80, decoded.getFirst().amount());
            assertEquals(40, decoded.getLast().amount());
        }
    }

    @Test
    void rejectsCellsOutsideTheOwningChunkAndDuplicateCoordinates() {
        var duplicate = new MaterialChunkData.Cell(0, 0, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> MaterialChunkData.validate(
            AtmosphereMaterial.DUST, 0, 0, -64, 320, List.of(duplicate, duplicate)));
        assertThrows(IllegalArgumentException.class, () -> MaterialChunkData.validate(
            AtmosphereMaterial.ENDER_GAS, 0, 0, -64, 320,
            List.of(new MaterialChunkData.Cell(16, 0, 0, 1))));
    }
}
