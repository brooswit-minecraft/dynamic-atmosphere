package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import java.util.List;
import java.util.Objects;

/**
 * Spec section 29/45: "Weather Materials may modify vanilla spawn attempts." A
 * multiplier above 1 encourages a vanilla spawn attempt for {@code entityId} that would
 * otherwise fail; a multiplier below 1 suppresses one that would otherwise pass. This
 * is data, not a new subsystem: the Zombie Fog example from section 45
 * ({@code zombie spawn multiplier = 2.0} once concentration &gt;= 0.30) and a
 * hypothetical "suppress spiders" modifier are both just instances of this one record.
 */
public record SpawnModifier(String entityId, List<Condition> conditions, double multiplier) {
    public SpawnModifier {
        Objects.requireNonNull(entityId, "entityId");
        conditions = List.copyOf(conditions);
        if (!Double.isFinite(multiplier) || multiplier < 0) {
            throw new IllegalArgumentException("multiplier must be finite and non-negative");
        }
    }

    public boolean conditionsMatch(EventContext context) {
        return Condition.allMatch(conditions, context);
    }

    /** Probability (roll &lt; threshold) that this modifier overrides a vanilla DENY to PASS_THROUGH. */
    public double overrideDenyProbability() {
        return Math.min(1.0, Math.max(0.0, multiplier - 1.0));
    }

    /** Probability (roll &lt; threshold) that this modifier overrides a vanilla PASS_THROUGH to DENY. */
    public double suppressProbability() {
        return Math.min(1.0, Math.max(0.0, 1.0 - multiplier));
    }
}
