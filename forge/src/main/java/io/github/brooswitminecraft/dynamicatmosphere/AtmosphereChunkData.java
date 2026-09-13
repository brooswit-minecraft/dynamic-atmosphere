package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Pure sparse chunk format. Capacity and simulation timestamps are deliberately absent. */
public final class AtmosphereChunkData {
    public static final int VERSION = 1;
    private static final int MAX_AMOUNT = 1_000_000;
    private static final int INTS_PER_CELL = 4;

    public record Cell(int x, int y, int z, int amount) { }
    private record Position(int x, int y, int z) { }

    public static List<Cell> validate(int chunkX, int chunkZ, int minBlockY, int maxBlockY, List<Cell> cells) {
        int minCellY = AtmosphereGridLayout.cellCoordinate(minBlockY);
        int maxCellY = AtmosphereGridLayout.cellCoordinate(maxBlockY - 1);
        long maxCells = ((long) maxCellY - minCellY + 1) * 16;
        if (maxBlockY <= minBlockY || cells.size() > maxCells) {
            throw new IllegalArgumentException("invalid atmospheric chunk size");
        }
        var seen = new HashSet<Position>();
        var result = new ArrayList<Cell>(cells.size());
        for (Cell cell : cells) {
            if (AtmosphereGridLayout.chunkCoordinate(cell.x()) != chunkX
                || AtmosphereGridLayout.chunkCoordinate(cell.z()) != chunkZ
                || cell.y() < minCellY || cell.y() > maxCellY
                || cell.amount() <= 0 || cell.amount() > MAX_AMOUNT
                || !seen.add(new Position(cell.x(), cell.y(), cell.z()))) {
                throw new IllegalArgumentException("invalid or duplicate atmospheric cell");
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
        long maxCells = ((long) maxBlockY - minBlockY + AtmosphereGridLayout.CELL_SIZE - 1)
            / AtmosphereGridLayout.CELL_SIZE * 16;
        if (maxBlockY <= minBlockY || values.length % INTS_PER_CELL != 0
            || values.length / INTS_PER_CELL > maxCells) {
            throw new IllegalArgumentException("invalid atmospheric chunk encoding");
        }
        var cells = new ArrayList<Cell>(values.length / INTS_PER_CELL);
        for (int i = 0; i < values.length; i += INTS_PER_CELL) {
            cells.add(new Cell(values[i], values[i + 1], values[i + 2], values[i + 3]));
        }
        return validate(chunkX, chunkZ, minBlockY, maxBlockY, cells);
    }

    private AtmosphereChunkData() { }
}
