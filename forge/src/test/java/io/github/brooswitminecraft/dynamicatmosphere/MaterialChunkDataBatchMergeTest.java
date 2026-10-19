package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-437: pins {@link MaterialChunkData#mergeBatch}, the actual production merge the
 * batched chunk-attachment write path ({@code ForgeMaterialStorage#writeBatch}) delegates to --
 * not a test reimplementation. There is no LevelChunk/attachment mocking in this repo, so this
 * exercises the pure merge exactly as {@code ForgeAtmospherePrototype#flushMaterialDirtyChunks}
 * drives it: one previous-materials snapshot plus a per-chunk batch of per-material updates.
 */
class MaterialChunkDataBatchMergeTest {
    private static final AtmosphereMaterial A = AtmosphereMaterial.DUST;
    private static final AtmosphereMaterial B = AtmosphereMaterial.OBSIDIAN_POWDER;
    private static final AtmosphereMaterial C = AtmosphereMaterial.VOID_GAS;

    private static MaterialChunkData.Cell cell(AtmosphereMaterial material, int amount) {
        int x = material.cellCoordinate(0);
        int z = material.cellCoordinate(0);
        int y = material.cellCoordinate(0);
        return new MaterialChunkData.Cell(x, y, z, amount);
    }

    /** (a): several materials dirty in the same chunk collapse into one coherent published snapshot. */
    @Test
    void multipleDirtyMaterialsInOneChunkProduceOneMergedSnapshot() {
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> previous = Map.of();
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = new EnumMap<>(AtmosphereMaterial.class);
        updates.put(A, List.of(cell(A, 10)));
        updates.put(B, List.of(cell(B, 20)));

        var merge = MaterialChunkData.mergeBatch(previous, updates, 0, 0, -64, 320);

        assertTrue(merge.changed());
        assertTrue(merge.failures().isEmpty());
        assertEquals(List.of(cell(A, 10)), merge.materials().get(A).cells());
        assertEquals(List.of(cell(B, 20)), merge.materials().get(B).cells());
    }

    /** (a): an untouched material's StoredMaterial survives a batch that only changes a different material, by identity. */
    @Test
    void untouchedMaterialKeepsTheSameStoredMaterialReferenceAcrossABatchThatChangesAnotherMaterial() {
        var unrelated = new MaterialChunkData.StoredMaterial(List.of(cell(C, 99)), null);
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> previous =
            Map.of(A, new MaterialChunkData.StoredMaterial(List.of(cell(A, 5)), null), C, unrelated);
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = new EnumMap<>(AtmosphereMaterial.class);
        updates.put(A, List.of(cell(A, 7)));

        var merge = MaterialChunkData.mergeBatch(previous, updates, 0, 0, -64, 320);

        assertTrue(merge.changed());
        assertSame(unrelated, merge.materials().get(C),
            "an unrelated, untouched material must be reused by reference, not recomputed or copied");
    }

    /** (b): a material whose validated cells are byte-for-byte unchanged does not dirty/republish anything. */
    @Test
    void byteForByteUnchangedCellsForEveryUpdatedMaterialProduceNoChangeAtAll() {
        var stored = new MaterialChunkData.StoredMaterial(List.of(cell(A, 5)), null);
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> previous = Map.of(A, stored);
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = Map.of(A, List.of(cell(A, 5)));

        var merge = MaterialChunkData.mergeBatch(previous, updates, 0, 0, -64, 320);

        assertFalse(merge.changed(), "re-submitting the exact same stored cells must not count as a change");
        assertSame(previous, merge.materials(), "nothing changed, so the previous map must be reused, not copied");
    }

    /** (b): within a mixed batch, the unchanged material is left out of consideration even though another material changed. */
    @Test
    void mixedBatchLeavesTheUnchangedMaterialAloneWhileApplyingTheChangedOne() {
        var unchangedStored = new MaterialChunkData.StoredMaterial(List.of(cell(A, 5)), null);
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> previous = Map.of(A, unchangedStored);
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = new EnumMap<>(AtmosphereMaterial.class);
        updates.put(A, List.of(cell(A, 5))); // unchanged
        updates.put(B, List.of(cell(B, 30))); // new

        var merge = MaterialChunkData.mergeBatch(previous, updates, 0, 0, -64, 320);

        assertTrue(merge.changed());
        assertSame(unchangedStored, merge.materials().get(A));
        assertEquals(List.of(cell(B, 30)), merge.materials().get(B).cells());
    }

    /** (c): a material already retained as unreadable/corrupt is skipped and reported, never overwritten. */
    @Test
    void unreadableMaterialIsSkippedAndReportedWithoutBeingOverwritten() {
        CompoundTag corruptTag = new CompoundTag();
        corruptTag.putString("bogus", "data");
        var corrupt = new MaterialChunkData.StoredMaterial(List.of(), corruptTag.copy());
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> previous = Map.of(A, corrupt);
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = Map.of(A, List.of(cell(A, 11)));

        var merge = MaterialChunkData.mergeBatch(previous, updates, 0, 0, -64, 320);

        assertFalse(merge.changed());
        assertSame(previous, merge.materials());
        assertTrue(merge.failures().containsKey(A));
        assertEquals(corruptTag, previous.get(A).unreadable(), "the corrupt tag itself must remain untouched");
    }

    /** (c): one unreadable material does not block or corrupt an unrelated readable material's update in the same batch. */
    @Test
    void oneUnreadableMaterialDoesNotInvalidateAValidUpdateToAnUnrelatedMaterialInTheSameBatch() {
        CompoundTag corruptTag = new CompoundTag();
        corruptTag.putString("bogus", "data");
        var corrupt = new MaterialChunkData.StoredMaterial(List.of(), corruptTag.copy());
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> previous = Map.of(A, corrupt);
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates = new EnumMap<>(AtmosphereMaterial.class);
        updates.put(A, List.of(cell(A, 11))); // must be refused
        updates.put(B, List.of(cell(B, 42))); // must still succeed

        var merge = MaterialChunkData.mergeBatch(previous, updates, 0, 0, -64, 320);

        assertTrue(merge.changed(), "the valid material's update must still publish even though A failed");
        assertEquals(1, merge.failures().size());
        assertTrue(merge.failures().containsKey(A));
        assertEquals(List.of(cell(B, 42)), merge.materials().get(B).cells());
        assertSame(corrupt, merge.materials().get(A), "the corrupt entry for A must be carried over untouched, not dropped");
    }

    /**
     * (d): pins the same-coordinate new-chunk-incarnation path ({@code ensureImported}'s
     * "previous != null" branch) against the batched write path -- the old incarnation's flush
     * and the new incarnation's first batch must never see or leak each other's materials, since
     * {@code ensureImported} flushes and clears grid state for the old {@code ChunkState} before a
     * new one is ever read from.
     */
    @Test
    void newChunkIncarnationAtTheSameCoordinatesStartsFromAnIndependentEmptySnapshot() {
        // Old incarnation: dirty in two materials, flushed via one batch (as ensureImported does
        // before swapping in the replacement ChunkState).
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> oldPrevious = Map.of();
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> oldUpdates = new EnumMap<>(AtmosphereMaterial.class);
        oldUpdates.put(A, List.of(cell(A, 15)));
        oldUpdates.put(B, List.of(cell(B, 25)));
        var oldFlush = MaterialChunkData.mergeBatch(oldPrevious, oldUpdates, 0, 0, -64, 320);
        assertTrue(oldFlush.changed());

        // New incarnation at the same chunk coordinates starts over from Map.of() (a fresh
        // ChunkState has nothing imported yet) -- its own batch must not see the old incarnation's
        // in-memory cells, which ensureImported never passes forward.
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> newPrevious = Map.of();
        Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> newUpdates = Map.of(A, List.of(cell(A, 3)));
        var newFlush = MaterialChunkData.mergeBatch(newPrevious, newUpdates, 0, 0, -64, 320);

        assertTrue(newFlush.changed());
        assertEquals(List.of(cell(A, 3)), newFlush.materials().get(A).cells());
        assertFalse(newFlush.materials().containsKey(B),
            "the new incarnation must never inherit the old incarnation's unrelated material");
    }
}
