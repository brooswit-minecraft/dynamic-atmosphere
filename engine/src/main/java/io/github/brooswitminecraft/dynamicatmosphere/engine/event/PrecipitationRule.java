package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import java.util.List;
import java.util.Objects;

/**
 * Spec section 21/22/44: "Precipitation is a generic material behavior... Each
 * material defines its own precipitation rules." A rule names an environmental
 * {@code mode} (a label such as "rain"/"snow"/"evaporation" -- not a hardcoded output;
 * see section 22), carries its own conditions/direction/materialCost, and performs one
 * of its {@code actions} (each with its own chance, spec section 44's Slime Fog
 * example: rain drops slimeballs at 0.02, snow places slime blocks at 0.002).
 */
public record PrecipitationRule(
    String mode,
    List<Condition> conditions,
    PrecipitationDirection direction,
    double materialCost,
    List<PrecipitationAction> actions
) {
    public PrecipitationRule {
        Objects.requireNonNull(mode, "mode");
        conditions = List.copyOf(conditions);
        Objects.requireNonNull(direction, "direction");
        if (!Double.isFinite(materialCost) || materialCost < 0 || materialCost > 1) {
            throw new IllegalArgumentException("materialCost must be within [0, 1]");
        }
        actions = List.copyOf(actions);
    }

    public boolean conditionsMatch(EventContext context) {
        return Condition.allMatch(conditions, context);
    }
}
