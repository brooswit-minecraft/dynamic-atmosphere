package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

/**
 * Spec section 22: "A precipitation rule can also specify direction... Default: DOWN.
 * But other directions should be possible... This allows upward 'rain' without building
 * a separate system." Built-in materials only ever use DOWN today; UP exists so a
 * future exotic material (the spec's own End Mist example) needs no engine change.
 */
public enum PrecipitationDirection {
    DOWN,
    UP
}
