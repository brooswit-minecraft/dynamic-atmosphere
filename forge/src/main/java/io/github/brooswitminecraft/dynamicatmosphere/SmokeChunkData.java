package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Pure sparse chunk format for persisted smoke material. */
public final class SmokeChunkData {
    public static final int VERSION = 2;
    private static final int INTS_PER_CELL = 4;
    private static final int HORIZONTAL_CELLS_PER_CHUNK = 16;

    public record Cell(int x, int y, int z, int amount) { }
    private record Position(int x, int y, int z) { }

    public static List<Cell> validate(int chunkX, int chunkZ, int minBlockY, int maxBlockY, List<Cell> cells) {
        int minCellY = SmokeGridLayout.cellCoordinate(minBlockY);
        int maxCellY = SmokeGridLayout.cellCoordinate(maxBlockY - 1);
        long maxCells = ((long) maxCellY - minCellY + 1) * HORIZONTAL_CELLS_PER_CHUNK;
        if (maxBlockY <= minBlockY || cells.size() > maxCells) {
            throw new IllegalArgumentException("invalid smoke chunk size");
        }
        var seen = new HashSet<Position>();
        var result = new ArrayList<Cell>(cells.size());
        for (Cell cell : cells) {
            if (SmokeGridLayout.chunkCoordinate(cell.x()) != chunkX
                || SmokeGridLayout.chunkCoordinate(cell.z()) != chunkZ
                || cell.y() < minCellY || cell.y() > maxCellY
                || cell.amount() <= 0 || cell.amount() > AtmosphereGrid.MAX_STORED_AMOUNT
                || !seen.add(new Position(cell.x(), cell.y(), cell.z()))) {
                throw new IllegalArgumentException("invalid or duplicate smoke cell");
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

    public static List<Cell> decode(int chunkX, int chunkZ, int minBlockY, int maxBlockY, int[] values) {
        if (values.length % INTS_PER_CELL != 0) {
            throw new IllegalArgumentException("invalid smoke chunk encoding");
        }
        var cells = new ArrayList<Cell>(values.length / INTS_PER_CELL);
        for (int index = 0; index < values.length; index += INTS_PER_CELL) {
            cells.add(new Cell(values[index], values[index + 1], values[index + 2], values[index + 3]));
        }
        return validate(chunkX, chunkZ, minBlockY, maxBlockY, cells);
    }

    /** Split legacy eight-block cells without creating or discarding material. */
    public static List<Cell> migrateLegacy(int chunkX, int chunkZ, int minY, int maxY, int[] values) {
        if (maxY <= minY || values.length % INTS_PER_CELL != 0
            || values.length / INTS_PER_CELL > ((long) Math.floorDiv(maxY - 1, 8)
                - Math.floorDiv(minY, 8) + 1) * 4) {
            throw new IllegalArgumentException("invalid legacy smoke chunk size");
        }
        var seen = new HashSet<Position>();
        var result = new ArrayList<Cell>();
        for (int i = 0; i < values.length; i += INTS_PER_CELL) {
            int x = values[i], y = values[i + 1], z = values[i + 2], amount = values[i + 3];
            if (Math.floorDiv(x, 2) != chunkX || Math.floorDiv(z, 2) != chunkZ
                || y < Math.floorDiv(minY, 8) || y > Math.floorDiv(maxY - 1, 8)
                || amount <= 0 || amount > AtmosphereGrid.MAX_STORED_AMOUNT
                || !seen.add(new Position(x, y, z))) {
                throw new IllegalArgumentException("invalid legacy smoke cell");
            }
            for (int child = 0; child < 8; child++) {
                int share = amount / 8 + (child < amount % 8 ? 1 : 0);
                if (share > 0) result.add(new Cell(x * 2 + child % 2,
                    y * 2 + child / 4, z * 2 + child / 2 % 2, share));
            }
        }
        return validate(chunkX, chunkZ, minY, maxY, result);
    }

    private SmokeChunkData() { }
}
