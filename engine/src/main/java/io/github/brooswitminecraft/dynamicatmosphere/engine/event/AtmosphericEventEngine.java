package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import java.util.List;
import java.util.Optional;

/**
 * The generic atmospheric event/action evaluator (spec sections 23, 26, 40, 41, 46).
 *
 * <p>This class is the entire "generic event system": it decides WHICH data-defined
 * event or precipitation outcome fires, given a context and a single random roll. It
 * never spawns an entity, places a block, or touches a {@code ServerLevel} -- that is
 * the Forge adapter's job (its action executor interprets the returned
 * {@link AtmosphericAction}s). Keeping this split is what lets Lightning and a Ghast
 * spawning from Smoke share identical code here while remaining completely different
 * data, and it is why this class has a pure, deterministic, Minecraft-free unit test
 * suite: every decision below is a function of its inputs, not of world state.
 */
public final class AtmosphericEventEngine {

    private AtmosphericEventEngine() { }

    /**
     * Evaluates an ordered list of events against one context and one roll. Returns the
     * first event whose conditions match and whose chance beats the roll (spec section
     * 41: "the dense storm qualifies for both or uses priority/exclusivity rules" -- this
     * engine chooses the simplest exclusivity rule, first match wins, so at most one
     * event fires per cell per tick).
     */
    public static Optional<AtmosphericEvent> evaluate(List<AtmosphericEvent> events, EventContext context, double roll) {
        for (AtmosphericEvent event : events) {
            if (event.conditionsMatch(context) && roll < event.chance()) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    /**
     * Evaluates one precipitation rule's conditions, then (independently) which of its
     * weighted actions fires for this roll, in order, first match wins.
     */
    public static Optional<PrecipitationAction> evaluatePrecipitation(
        PrecipitationRule rule, EventContext context, double roll
    ) {
        if (!rule.conditionsMatch(context)) return Optional.empty();
        for (PrecipitationAction action : rule.actions()) {
            if (roll < action.chance()) return Optional.of(action);
        }
        return Optional.empty();
    }

    /** First spawn modifier whose conditions match this context, if any. */
    public static Optional<SpawnModifier> matchingSpawnModifier(List<SpawnModifier> modifiers, EventContext context) {
        for (SpawnModifier modifier : modifiers) {
            if (modifier.conditionsMatch(context)) return Optional.of(modifier);
        }
        return Optional.empty();
    }

    /** Whether an otherwise-denied vanilla spawn attempt should be allowed to proceed. */
    public static boolean shouldOverrideDeny(SpawnModifier modifier, double roll) {
        return roll < modifier.overrideDenyProbability();
    }

    /** Whether an otherwise-allowed vanilla spawn attempt should be suppressed. */
    public static boolean shouldSuppress(SpawnModifier modifier, double roll) {
        return roll < modifier.suppressProbability();
    }
}
