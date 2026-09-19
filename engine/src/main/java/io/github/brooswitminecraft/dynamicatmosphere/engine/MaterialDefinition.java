package io.github.brooswitminecraft.dynamicatmosphere.engine;

import java.util.Objects;
import java.util.Set;

/** Declarative identities only: no producer rates, activation, or transformation thresholds. */
public record MaterialDefinition(
    String id,
    Color color,
    MaterialSettings settings,
    Set<Producer> producers,
    Set<Transformation> transformations
) {
    public MaterialDefinition {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("id must be a lowercase material identifier");
        }
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(settings, "settings");
        producers = Set.copyOf(producers);
        transformations = Set.copyOf(transformations);
    }

    // Named colors preserve the specification without selecting unspecified RGB values.
    public enum Color { CURRENT_FOG, BROWN, DARK_BROWN, BLACK, YELLOW, GREEN, PURPLE }

    public enum Producer {
        EXISTING_VAPOR_RULES, SNOW_ICE_SURFACE,
        PLAYER_WALKING, MOB_WALKING, BLOCK_BREAKING, BLOCK_PLACEMENT,
        PLAYER_FALL_DAMAGE, MOB_FALL_DAMAGE,
        LAVA, FIRE, EXPLOSIONS, EXPLOSION_DESTROYED_BLOCKS, FURNACES, TORCHES, CAMPFIRES,
        HOSTILE_MOB_DEATHS, NETHERRACK,
        LIVING_MOBS_RANDOMLY, CREEPERS_FREQUENTLY, PLAYER_DAMAGE, MOB_DAMAGE,
        UNDERGROUND_SLIME_CHUNKS_RANDOMLY,
        NETHER_PORTAL_BLOCKS_SLOWLY, ENDERMEN, ENDERMITES, ENDER_DRAGON,
        ENDER_PEARL_USE, STANDING_IN_NETHER_PORTAL, SOUL_TORCHES, SOUL_FIRES, SOUL_SAND
    }

    public enum Transformation { WATER, GRAVEL, ZOMBIE, SLIME }
}
