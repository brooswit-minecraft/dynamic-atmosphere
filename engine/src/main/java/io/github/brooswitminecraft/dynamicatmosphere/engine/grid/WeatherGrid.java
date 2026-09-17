package io.github.brooswitminecraft.dynamicatmosphere.engine.grid;

/**
 * Converts between world block coordinates and Weather Block cell
 * coordinates, in both directions, for one globally configured cube size.
 *
 * <p>X, Y and Z are deliberately NOT independently configurable — Weather
 * Blocks are always cubes (spec section 3). This is an explicit design
 * decision, not an oversight; do not add per-axis sizing.
 *
 * <p>Conversion uses {@link Math#floorDiv(int, int)} and
 * {@link Math#floorMod(int, int)}, not the naive {@code /} and {@code %}
 * operators, which truncate toward zero and give the wrong cell for negative
 * coordinates.
 */
public final class WeatherGrid {

    private final int cellSize;

    public WeatherGrid(int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive, was " + cellSize);
        }
        this.cellSize = cellSize;
    }

    public int cellSize() {
        return cellSize;
    }

    /** The cell containing this world block position. */
    public CellPos cellOf(BlockPos worldPos) {
        return new CellPos(
            Math.floorDiv(worldPos.x(), cellSize),
            Math.floorDiv(worldPos.y(), cellSize),
            Math.floorDiv(worldPos.z(), cellSize));
    }

    /** This world block position's coordinate local to its cell, in {@code [0, cellSize)} on each axis. */
    public BlockPos localOf(BlockPos worldPos) {
        return new BlockPos(
            Math.floorMod(worldPos.x(), cellSize),
            Math.floorMod(worldPos.y(), cellSize),
            Math.floorMod(worldPos.z(), cellSize));
    }

    /** The world block position of local {@code (0, 0, 0)} in {@code cell}. */
    public BlockPos originOf(CellPos cell) {
        return new BlockPos(cell.x() * cellSize, cell.y() * cellSize, cell.z() * cellSize);
    }

    /** The world block position of a coordinate local to {@code cell}. */
    public BlockPos worldOf(CellPos cell, BlockPos local) {
        BlockPos origin = originOf(cell);
        return new BlockPos(origin.x() + local.x(), origin.y() + local.y(), origin.z() + local.z());
    }

    /** The cell adjacent to {@code cell} across the given face. */
    public CellPos neighborOf(CellPos cell, Direction direction) {
        return new CellPos(cell.x() + direction.dx(), cell.y() + direction.dy(), cell.z() + direction.dz());
    }
}
