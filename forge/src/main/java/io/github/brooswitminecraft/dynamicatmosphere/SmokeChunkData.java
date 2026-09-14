package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Pure sparse chunk format for persisted smoke material. */
public final class SmokeChunkData {
    public static final int VERSION = 1;
    private static final int INTS_PER_CELL = 4;
    private static final int HORIZONTAL_CELLS_PER_CHUNK = 4;

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

    private SmokeChunkData() { }
}
