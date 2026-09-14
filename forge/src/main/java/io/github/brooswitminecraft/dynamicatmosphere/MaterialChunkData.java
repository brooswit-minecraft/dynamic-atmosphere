package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;

/** Pure sparse chunk format shared by post-Smoke atmospheric materials. */
public final class MaterialChunkData {
    public static final int VERSION = 1;
    private static final int INTS_PER_CELL = 4;

    public record Cell(int x, int y, int z, int amount) { }
    private record Position(int x, int y, int z) { }

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

    /** Merge aligned legacy one-block Ender Gas cells without silently dropping material. */
    public static List<Cell> migrateEnderGas(int chunkX, int chunkZ, int minY, int maxY, int[] values) {
        if (maxY <= minY || values.length % INTS_PER_CELL != 0
            || values.length / INTS_PER_CELL > ((long) maxY - minY) * 256) {
            throw new IllegalArgumentException("invalid legacy Ender Gas encoding");
        }
        var seen = new HashSet<Position>();
        var totals = new HashMap<Position, Long>();
        for (int i = 0; i < values.length; i += INTS_PER_CELL) {
            int x = values[i], y = values[i + 1], z = values[i + 2], amount = values[i + 3];
            if (Math.floorDiv(x, 16) != chunkX || Math.floorDiv(z, 16) != chunkZ
                || y < minY || y >= maxY || amount <= 0 || amount > AtmosphereGrid.MAX_STORED_AMOUNT
                || !seen.add(new Position(x, y, z))) {
                throw new IllegalArgumentException("invalid legacy Ender Gas cell");
            }
            var target = new Position(Math.floorDiv(x, 2), Math.floorDiv(y, 2), Math.floorDiv(z, 2));
            long total = totals.merge(target, (long) amount, Long::sum);
            if (total > AtmosphereGrid.MAX_STORED_AMOUNT) {
                // The storage reader retains the original tag on failure, rather than truncating it.
                throw new IllegalArgumentException("legacy Ender Gas merge exceeds storage capacity");
            }
        }
        var cells = new ArrayList<Cell>(totals.size());
        totals.forEach((pos, amount) -> cells.add(new Cell(pos.x(), pos.y(), pos.z(), amount.intValue())));
        return validate(AtmosphereMaterial.ENDER_GAS, chunkX, chunkZ, minY, maxY, cells);
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
