package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

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
