package io.github.brooswitminecraft.dynamicatmosphere.engine.adapter;

import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.BlockPos;

/**
 * The engine's one boundary onto the environment it runs inside (spec
 * section 12). This story ships only the query the connectivity builder
 * needs: whether a block position is passable to atmosphere. Later stories
 * are expected to grow this interface with altitude, sky access, water/lava,
 * biome, and world-gen climate queries — the shape here (one method per
 * query, each free to answer honestly with an "unknown" value rather than
 * fabricate one) is meant to absorb that growth without the interface itself
 * having to change shape.
 *
 * <p>NOTHING Minecraft-backed implements this in this repo's {@code engine}
 * module. The real, game-backed implementation is SICKOS-27's job; the only
 * implementation here lives in the test source set, over synthetic terrain.
 */
public interface EnvironmentalAdapter {

    /**
     * Whether atmosphere can occupy this WORLD block position.
     * {@link Passability#UNKNOWN} means the environment could not answer —
     * for example, an unloaded chunk, a case SICKOS-27 will hit for real —
     * and must be reported as such rather than guessed at as passable or
     * impassable.
     */
    Passability passability(BlockPos worldPos);
}
