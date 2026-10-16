package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import java.util.Objects;

/** One of a {@link PrecipitationRule}'s possible outcomes, with its own independent chance. */
public record PrecipitationAction(AtmosphericAction action, double chance) {
    public PrecipitationAction {
        Objects.requireNonNull(action, "action");
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("chance must be within [0, 1]");
        }
    }
}
