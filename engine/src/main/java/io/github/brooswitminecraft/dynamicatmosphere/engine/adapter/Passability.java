package io.github.brooswitminecraft.dynamicatmosphere.engine.adapter;

/**
 * Whether a block position can be occupied by atmosphere, as answered by an
 * {@link EnvironmentalAdapter}. {@link #UNKNOWN} is a first-class result,
 * distinct from {@link #IMPASSABLE} — the environment could not answer (for
 * example, an unloaded chunk), which is a different fact from "answered no."
 * Any caller that collapses {@link #UNKNOWN} into one of the other two
 * values is doing so as its own deliberate choice; nothing at this layer
 * makes that collapse for it. (The connectivity builder's own choice is
 * documented where it makes it, not here.)
 */
public enum Passability {
    PASSABLE,
    IMPASSABLE,
    UNKNOWN
}
