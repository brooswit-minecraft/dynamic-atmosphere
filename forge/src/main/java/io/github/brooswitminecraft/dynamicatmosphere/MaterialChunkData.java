package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Pure sparse chunk format shared by post-Smoke atmospheric materials. */
public final class MaterialChunkData {
    /** Bumped when the uniform 4x4x4 cell size replaced per-material sizes; old chunks are discarded, not reinterpreted. */
    public static final int VERSION = 2;
    private static final int INTS_PER_CELL = 4;

    public record Cell(int x, int y, int z, int amount) { }
    private record Position(int x, int y, int z) { }

    /** A material's decoded chunk cells, or the raw tag preserved because it is corrupt/unparseable. */
    public record StoredMaterial(List<Cell> cells, CompoundTag unreadable) { }

    /** Null means a stale version/cell_size: caller should treat it as empty, not preserve-and-skip. */
    public static StoredMaterial decodeStored(AtmosphereMaterial material, int chunkX, int chunkZ,
                                               int minBlockY, int maxBlockY, CompoundTag materialTag) {
        if (isStaleVersion(material, materialTag)) return null;
        if (!materialTag.contains("cells", Tag.TAG_INT_ARRAY)) {
            return new StoredMaterial(List.of(), materialTag.copy());
        }
        try {
            return new StoredMaterial(decode(material, chunkX, chunkZ, minBlockY, maxBlockY,
                materialTag.getIntArray("cells")), null);
        } catch (IllegalArgumentException exception) {
            return new StoredMaterial(List.of(), materialTag.copy());
        }
    }

    /** Checked before "cells" is even looked at, so a version/cell_size mismatch never counts as corrupt. */
    private static boolean isStaleVersion(AtmosphereMaterial material, CompoundTag materialTag) {
        return !materialTag.contains("version", Tag.TAG_INT)
            || materialTag.getInt("version") != VERSION
            || !materialTag.contains("cell_size", Tag.TAG_INT)
            || materialTag.getInt("cell_size") != material.cellSize();
    }

    public static List<Cell> validate(AtmosphereMaterial material, int chunkX, int chunkZ,
                                      int minBlockY, int maxBlockY, List<Cell> cells) {
        int minCellY = material.cellCoordinate(minBlockY);
        int maxCellY = material.cellCoordinate(maxBlockY - 1);
        int side = 16 / material.cellSize();
        long maxCells = ((long) maxCellY - minCellY + 1) * side * side;
        if (maxBlockY <= minBlockY || cells.size() > maxCells) {
            throw new IllegalArgumentException("invalid " + material.id() + " chunk size");
        }
        var seen = new HashSet<Position>();
        var result = new ArrayList<Cell>(cells.size());
        for (Cell cell : cells) {
            if (material.chunkCoordinate(cell.x()) != chunkX
                || material.chunkCoordinate(cell.z()) != chunkZ
                || cell.y() < minCellY || cell.y() > maxCellY
                || cell.amount() <= 0 || cell.amount() > AtmosphereGrid.MAX_STORED_AMOUNT
                || !seen.add(new Position(cell.x(), cell.y(), cell.z()))) {
                throw new IllegalArgumentException("invalid or duplicate " + material.id() + " cell");
            }
            result.add(cell);
        }
        result.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z));
        return List.copyOf(result);
    }

    /**
     * MINECRAFT-437: the outcome of merging several materials' pending cell updates for one chunk
     * into a single previous-materials snapshot in one pass.
     *
     * <p>{@code materials} is the new snapshot to publish when {@code changed} is true, or simply
     * {@code previous} (unmodified, zero-copy) when nothing actually changed and nothing should be
     * published. {@code failures} holds one entry per material whose previous stored entry was
     * already retained as unreadable/corrupt (so its update was skipped, not applied) — callers
     * should treat each failed material exactly as the single-material write path does: mark it
     * unreadable and stop simulating it, without disturbing any other, unrelated material.
     */
    public record BatchMerge(Map<AtmosphereMaterial, StoredMaterial> materials, boolean changed,
                              Map<AtmosphereMaterial, IllegalStateException> failures) { }

    /**
     * Merges {@code updates} (one pending cell list per dirty material) into {@code previous} with
     * exactly one {@link EnumMap} copy for the whole batch, reusing every untouched material's
     * {@link StoredMaterial} by reference. A material whose validated cells are byte-for-byte equal
     * to what is already stored is treated as unchanged and left untouched (not just zero-copy, but
     * not even considered for inclusion in a new map). A material whose previous entry is already
     * {@linkplain StoredMaterial#unreadable() unreadable} is skipped and reported in
     * {@link BatchMerge#failures()} instead of being applied, so one corrupt/retained material never
     * blocks or corrupts the batch's other, readable materials.
     */
    public static BatchMerge mergeBatch(Map<AtmosphereMaterial, StoredMaterial> previous,
                                         Map<AtmosphereMaterial, List<Cell>> updates,
                                         int chunkX, int chunkZ, int minBlockY, int maxBlockY) {
        EnumMap<AtmosphereMaterial, StoredMaterial> materials = null;
        Map<AtmosphereMaterial, IllegalStateException> failures = null;
        boolean changed = false;
        for (var entry : updates.entrySet()) {
            AtmosphereMaterial material = entry.getKey();
            StoredMaterial oldMaterial = previous.get(material);
            if (oldMaterial != null && oldMaterial.unreadable() != null) {
                if (failures == null) failures = new EnumMap<>(AtmosphereMaterial.class);
                failures.put(material, new IllegalStateException("Unsupported/corrupt " + material.id()
                    + " data retained; skip this chunk without overwriting it"));
                continue;
            }
            List<Cell> validated = validate(material, chunkX, chunkZ, minBlockY, maxBlockY, entry.getValue());
            boolean unchanged = (oldMaterial == null && validated.isEmpty())
                || (oldMaterial != null && oldMaterial.cells().equals(validated));
            if (unchanged) continue;
            if (materials == null) {
                // EnumMap's Map constructor throws IllegalArgumentException on an empty map (it
                // cannot infer the key type from no entries), so seed the key type explicitly.
                materials = new EnumMap<>(AtmosphereMaterial.class);
                materials.putAll(previous);
            }
            if (validated.isEmpty()) materials.remove(material);
            else materials.put(material, new StoredMaterial(validated, null));
            changed = true;
        }
        Map<AtmosphereMaterial, StoredMaterial> result = changed ? Map.copyOf(materials) : previous;
        return new BatchMerge(result, changed, failures == null ? Map.of() : Map.copyOf(failures));
    }

    public static int[] encode(List<Cell> cells) {
        int[] values = new int[Math.multiplyExact(cells.size(), INTS_PER_CELL)];
        int index = 0;
        for (Cell cell : cells) {
            values[index++] = cell.x();
            values[index++] = cell.y();
            values[index++] = cell.z();
            values[index++] = cell.amount();
        }
        return values;
    }

    public static List<Cell> decode(AtmosphereMaterial material, int chunkX, int chunkZ,
                                    int minBlockY, int maxBlockY, int[] values) {
        if (values.length % INTS_PER_CELL != 0) {
            throw new IllegalArgumentException("invalid " + material.id() + " chunk encoding");
        }
        var cells = new ArrayList<Cell>(values.length / INTS_PER_CELL);
        for (int index = 0; index < values.length; index += INTS_PER_CELL) {
            cells.add(new Cell(values[index], values[index + 1], values[index + 2], values[index + 3]));
        }
        return validate(material, chunkX, chunkZ, minBlockY, maxBlockY, cells);
    }

    private MaterialChunkData() { }
}
