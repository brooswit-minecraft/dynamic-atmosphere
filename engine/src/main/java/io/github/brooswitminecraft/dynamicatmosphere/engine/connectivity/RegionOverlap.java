package io.github.brooswitminecraft.dynamicatmosphere.engine.connectivity;

/**
 * How much of {@code oldRegion} (a region that existed before a rebuild)
 * ended up inside {@code newRegion} (a region that exists after it):
 * {@code overlapVolume} block positions were members of both. Part of a
 * {@link RemapReport}; see that type for how this is meant to be used to
 * conserve per-region material across a split or merge.
 */
public record RegionOverlap(Region oldRegion, Region newRegion, int overlapVolume) {
}
