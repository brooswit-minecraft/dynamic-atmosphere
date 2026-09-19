package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins ATMO-21: a stale version/cell_size is discarded as empty; genuine corruption is still preserved. */
class ForgeMaterialStorageStaleVersionTest {
    private static final AtmosphereMaterial MATERIAL = AtmosphereMaterial.DUST;

    @Test
    void oldVersionAndCellSizeIsDiscardedAsEmptyEvenWithAWellFormedCellsArray() {
        CompoundTag staleTag = new CompoundTag();
        staleTag.putInt("version", MaterialChunkData.VERSION - 1);
        staleTag.putInt("cell_size", MATERIAL.cellSize());
        staleTag.put("cells", new IntArrayTag(new int[] {1, 1, 1, 10}));

        assertNull(MaterialChunkData.decodeStored(MATERIAL, 0, 0, 0, 16, staleTag),
            "an old version must be discarded (null), not preserved as unreadable");
    }

    @Test
    void oldCellSizeAloneIsDiscardedAsEmpty() {
        CompoundTag staleTag = new CompoundTag();
        staleTag.putInt("version", MaterialChunkData.VERSION);
        staleTag.putInt("cell_size", MATERIAL.cellSize() + 1);
        staleTag.put("cells", new IntArrayTag(new int[0]));

        assertNull(MaterialChunkData.decodeStored(MATERIAL, 0, 0, 0, 16, staleTag));
    }

    @Test
    void currentVersionAndCellSizeDecodesNormally() {
        CompoundTag currentTag = new CompoundTag();
        currentTag.putInt("version", MaterialChunkData.VERSION);
        currentTag.putInt("cell_size", MATERIAL.cellSize());
        currentTag.put("cells", new IntArrayTag(new int[] {1, 1, 1, 10}));

        var stored = MaterialChunkData.decodeStored(MATERIAL, 0, 0, 0, 16, currentTag);
        assertEquals(List.of(new MaterialChunkData.Cell(1, 1, 1, 10)), stored.cells());
        assertNull(stored.unreadable());
    }

    @Test
    void missingCellsArrayUnderTheCurrentVersionIsPreservedAsCorruptNotDiscarded() {
        CompoundTag corruptTag = new CompoundTag();
        corruptTag.putInt("version", MaterialChunkData.VERSION);
        corruptTag.putInt("cell_size", MATERIAL.cellSize());

        var stored = MaterialChunkData.decodeStored(MATERIAL, 0, 0, 0, 16, corruptTag);
        assertTrue(stored.cells().isEmpty());
        assertEquals(corruptTag, stored.unreadable());
    }

    @Test
    void unparseableCellsUnderTheCurrentVersionIsPreservedAsCorruptNotDiscarded() {
        CompoundTag corruptTag = new CompoundTag();
        corruptTag.putInt("version", MaterialChunkData.VERSION);
        corruptTag.putInt("cell_size", MATERIAL.cellSize());
        // Odd length: not a whole number of (x, y, z, amount) cells.
        corruptTag.put("cells", new IntArrayTag(new int[] {1, 1, 1}));

        var stored = MaterialChunkData.decodeStored(MATERIAL, 0, 0, 0, 16, corruptTag);
        assertTrue(stored.cells().isEmpty());
        assertEquals(corruptTag, stored.unreadable());
    }
}
