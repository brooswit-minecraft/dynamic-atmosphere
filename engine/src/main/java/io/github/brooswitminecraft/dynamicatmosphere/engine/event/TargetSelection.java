package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

/**
 * Where an action's effect should land, relative to the cell that triggered it (spec
 * section 24, "Rain Targeting"). {@code PRECIPITATION_SURFACE} is the generic "randomly
 * selected valid location beneath (or above, for an UP-directed material) the
 * precipitating Weather Block" search the spec requires for rain/snow/lightning alike;
 * {@code SELF} means "the cell's own origin", used by sinks and entity sources that
 * don't need a surface search at all.
 */
public enum TargetSelection {
    SELF,
    PRECIPITATION_SURFACE
}
