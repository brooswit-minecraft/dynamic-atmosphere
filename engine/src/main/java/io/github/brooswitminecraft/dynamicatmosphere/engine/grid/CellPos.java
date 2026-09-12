package io.github.brooswitminecraft.dynamicatmosphere.engine.grid;

/**
 * The coordinate of one Weather Block cell in the coarse grid — not a block
 * coordinate. {@link WeatherGrid} converts between the two. Comparable so
 * that cell processing order (e.g. debounce tie-breaking) can be made
 * deterministic without inventing a second ordering elsewhere.
 */
public record CellPos(int x, int y, int z) implements Comparable<CellPos> {

    @Override
    public int compareTo(CellPos other) {
        if (x != other.x) {
            return Integer.compare(x, other.x);
        }
        if (y != other.y) {
            return Integer.compare(y, other.y);
        }
        return Integer.compare(z, other.z);
    }
}
