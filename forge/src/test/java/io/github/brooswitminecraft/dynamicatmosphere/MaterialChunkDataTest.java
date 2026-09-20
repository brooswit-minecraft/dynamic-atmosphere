package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialChunkDataTest {
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
            AtmosphereMaterial.OBSIDIAN_POWDER, 0, 0, -64, 320,
            List.of(new MaterialChunkData.Cell(16, 0, 0, 1))));
    }
}
