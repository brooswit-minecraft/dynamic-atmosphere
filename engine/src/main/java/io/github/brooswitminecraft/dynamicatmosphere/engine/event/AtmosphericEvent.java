package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import java.util.List;
import java.util.Objects;

/**
 * The generic event shape from spec section 40: a set of conditions, a per-tick roll
 * chance, a material cost debited when the event fires, and the actions it performs.
 *
 * <p>This single type is reused, unmodified, for every event in the mod: Lightning on a
 * dense precipitating Cloud/Vapor cell, a Ghast appearing from dense Smoke, and the
 * heat-sink event that removes Zombie Fog are three different pieces of <em>data</em>
 * built from this one <em>engine</em> type. Per spec section 54/46: "if lightning needs
 * its own subsystem, the generic event layer has failed" -- so this type must stay
 * exactly as boring as every other event using it.
 */
public record AtmosphericEvent(
    String id,
    List<Condition> conditions,
    double chance,
    double materialCost,
    TargetSelection target,
    List<AtmosphericAction> actions
) {
    public AtmosphericEvent {
        Objects.requireNonNull(id, "id");
        conditions = List.copyOf(conditions);
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("chance must be within [0, 1]");
        }
        if (!Double.isFinite(materialCost) || materialCost < 0 || materialCost > 1) {
            throw new IllegalArgumentException("materialCost must be within [0, 1]");
        }
        Objects.requireNonNull(target, "target");
        actions = List.copyOf(actions);
    }

    /** True if every condition matches; chance is rolled separately by the caller. */
    public boolean conditionsMatch(EventContext context) {
        return Condition.allMatch(conditions, context);
    }
}
