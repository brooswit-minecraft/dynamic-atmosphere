package io.github.brooswitminecraft.dynamicatmosphere.engine.grid;

/**
 * One of the six faces of a Weather Block cell (and, equivalently, a
 * face-adjacency step between block positions). REQUIRED by the spec: a
 * 6-value direction type with an {@link #opposite()} and a way to ask
 * whether it is up, down, or sideways — story 2 needs that distinction
 * directly, without deriving it from raw axis arithmetic, because altitude
 * bias depends on it.
 */
public enum Direction {

    UP(0, 1, 0),
    DOWN(0, -1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    public boolean isUp() {
        return this == UP;
    }

    public boolean isDown() {
        return this == DOWN;
    }

    public boolean isSideways() {
        return !isUp() && !isDown();
    }
}
