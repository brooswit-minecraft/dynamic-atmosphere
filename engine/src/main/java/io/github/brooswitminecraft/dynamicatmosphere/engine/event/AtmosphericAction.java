package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

import java.util.Map;
import java.util.Objects;

/**
 * A single generic action descriptor (spec section 23: "Atmospheric rules should
 * trigger generic actions... This action layer is one of the most important
 * abstractions in the mod. It keeps special content out of the simulation engine.").
 *
 * <p>Deliberately Minecraft-free: {@code type} and {@code params} are plain strings.
 * The Forge adapter's action executor is what turns {@code spawn_entity}/{@code entity}
 * {@code =minecraft:lightning_bolt} into an actual lightning bolt; this record only
 * carries the data-defined intent, so the engine never needs to know what an
 * {@code EntityType} or a {@code Block} is.
 */
public record AtmosphericAction(Type type, Map<String, String> params) {
    public AtmosphericAction {
        Objects.requireNonNull(type, "type");
        params = Map.copyOf(params);
    }

    public String param(String key) {
        String value = params.get(key);
        if (value == null) {
            throw new IllegalStateException("action " + type + " is missing required param \"" + key + "\"");
        }
        return value;
    }

    public String param(String key, String fallback) {
        return params.getOrDefault(key, fallback);
    }

    /** Spec section 23's minimum action set. Additional actions can be registered later. */
    public enum Type {
        SPAWN_ENTITY,
        SPAWN_ITEM,
        PLACE_BLOCK,
        REPLACE_BLOCK,
        APPLY_EFFECT,
        EMIT_MATERIAL,
        TRANSFER_MATERIAL
    }
}
