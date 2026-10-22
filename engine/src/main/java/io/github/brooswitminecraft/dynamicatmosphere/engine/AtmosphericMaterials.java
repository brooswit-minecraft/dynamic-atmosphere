package io.github.brooswitminecraft.dynamicatmosphere.engine;

import static io.github.brooswitminecraft.dynamicatmosphere.engine.MaterialDefinition.Color.*;
import static io.github.brooswitminecraft.dynamicatmosphere.engine.MaterialDefinition.Producer.*;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/** Approved specification catalog, deliberately not registered with any running simulation. */
public final class AtmosphericMaterials {
    /** Every material shares one cell size; there is no per-material override. */
    private static final int CELL_SIZE = 4;
    private static final List<MaterialSettings.LodBand> VAPOR_LOD = List.of(
        new MaterialSettings.LodBand(1, 0.5), new MaterialSettings.LodBand(2, 1),
        new MaterialSettings.LodBand(4, 2));
    private static final List<MaterialSettings.LodBand> DUST_LOD = List.of(
        new MaterialSettings.LodBand(1, 0.25));
    private static final List<MaterialSettings.LodBand> VOID_GAS_LOD = List.of(
        new MaterialSettings.LodBand(1, 1), new MaterialSettings.LodBand(2, 2));

    public static final MaterialDefinition VAPOR = new MaterialDefinition("vapor", CURRENT_FOG,
        // MINECRAFT-21: vaporOpticalDensity 1 -> 1.2.
        new MaterialSettings(CELL_SIZE, VAPOR_LOD, 1, OptionalDouble.of(1), 1.2), Set.of(EXISTING_VAPOR_RULES, SNOW_ICE_SURFACE, CAVE_FLORA),
        Set.of(MaterialDefinition.Transformation.WATER));
    public static final MaterialDefinition DUST = new MaterialDefinition("dust", BROWN,
        settings(CELL_SIZE, DUST_LOD, 1, OptionalDouble.of(1)),
        Set.of(PLAYER_WALKING, MOB_WALKING, BLOCK_BREAKING, BLOCK_PLACEMENT,
            PLAYER_FALL_DAMAGE, MOB_FALL_DAMAGE, DESERT_SURFACE), Set.of(MaterialDefinition.Transformation.GRAVEL));
    public static final MaterialDefinition SMOKE = new MaterialDefinition("smoke", DARK_WARM_GREY,
        // MINECRAFT-21: smokeOpticalDensity 2 -> 4.
        new MaterialSettings(CELL_SIZE, VAPOR_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(LAVA, FIRE, EXPLOSIONS, EXPLOSION_DESTROYED_BLOCKS, FURNACES, TORCHES, CAMPFIRES), Set.of());
    public static final MaterialDefinition VOID_GAS = new MaterialDefinition("void_gas", BLACK,
        new MaterialSettings(CELL_SIZE, VOID_GAS_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(HOSTILE_MOB_DEATHS, NETHERRACK), Set.of(MaterialDefinition.Transformation.ZOMBIE));
    public static final MaterialDefinition EXHAUST = new MaterialDefinition("exhaust", YELLOW,
        settings(CELL_SIZE, DUST_LOD, 1, OptionalDouble.of(1)),
        Set.of(LIVING_MOBS_RANDOMLY, CREEPERS_FREQUENTLY, PLAYER_DAMAGE, MOB_DAMAGE), Set.of());
    public static final MaterialDefinition SLIME = new MaterialDefinition("slime", GREEN,
        new MaterialSettings(CELL_SIZE, VOID_GAS_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(UNDERGROUND_SLIME_CHUNKS_RANDOMLY), Set.of(MaterialDefinition.Transformation.SLIME));
    public static final MaterialDefinition OBSIDIAN_POWDER = new MaterialDefinition("obsidian_powder", PURPLE,
        // MINECRAFT-21: obsidianPowderOpticalDensity 40 -> 0.3.
        new MaterialSettings(CELL_SIZE, DUST_LOD, 1, OptionalDouble.of(1), 0.3),
        Set.of(NETHER_PORTAL_BLOCKS_SLOWLY, ENDERMEN, ENDERMITES, ENDER_DRAGON,
            ENDER_PEARL_USE, STANDING_IN_NETHER_PORTAL, SOUL_TORCHES, SOUL_FIRES, SOUL_SAND,
            WITCH, SHULKER, ENDER_CHEST, CRYING_OBSIDIAN), Set.of());
    public static final MaterialDefinition ZOMBIE_FOG = new MaterialDefinition("zombie_fog", GRAY,
        new MaterialSettings(CELL_SIZE, VOID_GAS_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(ZOMBIE_DEATHS), Set.of());
    /** Phase 4 (MINECRAFT-52): mild-to-dangerous potion-effect hazard, sourced from Netherrack. */
    public static final MaterialDefinition NETHER_GAS = new MaterialDefinition("nether_gas", SICKLY_GREEN,
        new MaterialSettings(CELL_SIZE, VOID_GAS_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(NETHERRACK), Set.of());
    /** MINECRAFT-1 (B2): visual only, 2x Vapor's optical density. */
    public static final MaterialDefinition GLOW_GAS = new MaterialDefinition("glow_gas", GOLD,
        new MaterialSettings(CELL_SIZE, DUST_LOD, 1, OptionalDouble.of(1), 2),
        Set.of(GLOWSTONE), Set.of());
    /** MINECRAFT-1 (B3): Netherrack red, vapor-like LOD; home is TOP. */
    public static final MaterialDefinition NETHER_FOG = new MaterialDefinition("nether_fog", NETHERRACK_RED,
        settings(CELL_SIZE, VAPOR_LOD, 1, OptionalDouble.of(1)),
        Set.of(LAVA_SOURCE_HARVEST), Set.of());

    public static final MaterialDefinition MOLD = new MaterialDefinition("mold", MaterialDefinition.Color.MYCELIUM,
        settings(CELL_SIZE, VOID_GAS_LOD, 1, OptionalDouble.of(1)),
        Set.of(MaterialDefinition.Producer.MYCELIUM, MUSHROOMS, WITCH),
        Set.of(MaterialDefinition.Transformation.MUSHROOM, MaterialDefinition.Transformation.GIANT_MUSHROOM,
            MaterialDefinition.Transformation.MOOSHROOM));

    public static final List<MaterialDefinition> ALL =
        List.of(VAPOR, DUST, SMOKE, VOID_GAS, EXHAUST, SLIME, OBSIDIAN_POWDER, ZOMBIE_FOG, NETHER_GAS, GLOW_GAS, NETHER_FOG, MOLD);

    private AtmosphericMaterials() { }

    private static MaterialSettings settings(int size, List<MaterialSettings.LodBand> lod,
                                            double simulationSpeed, OptionalDouble producerSpeed) {
        return new MaterialSettings(size, lod, simulationSpeed, producerSpeed);
    }
}
