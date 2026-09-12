package io.github.brooswitminecraft.dynamicatmosphere.engine.connectivity;

import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.CellPos;
import java.util.List;

/**
 * What one cell rebuild did to its regions, expressed as old-region-to-
 * new-region overlaps rather than as an inferred "this region became that
 * region" story — a split or merge does not have a single right answer to
 * the latter, but overlap volumes are exact and let a caller (story 2)
 * redistribute per-region state conservatively: split proportionally to
 * overlap, merge by summing. An old region absent from every
 * {@link RegionOverlap} here contributed to nothing in the rebuilt cell
 * (every one of its blocks became impassable); a new region absent from
 * every overlap is entirely new material-free space.
 *
 * <p>{@code overlaps} contains only pairs with a positive overlap volume.
 */
public record RemapReport(CellPos cell, List<RegionOverlap> overlaps) {
}
