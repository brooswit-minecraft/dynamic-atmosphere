package io.github.brooswitminecraft.dynamicatmosphere.engine;

import static io.github.brooswitminecraft.dynamicatmosphere.engine.MaterialDefinition.Color.*;
import static io.github.brooswitminecraft.dynamicatmosphere.engine.MaterialDefinition.Producer.*;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/** Approved specification catalog, deliberately not registered with any running simulation. */
public final class AtmosphericMaterials {
    private static final List<MaterialSettings.LodBand> VAPOR_LOD = List.of(
        new MaterialSettings.LodBand(1, 0.5), new MaterialSettings.LodBand(2, 1),
        new MaterialSettings.LodBand(4, 2));
    private static final List<MaterialSettings.LodBand> DUST_LOD = List.of(
        new MaterialSettings.LodBand(1, 0.25));
    private static final List<MaterialSettings.LodBand> VIOLENCE_LOD = List.of(
        new MaterialSettings.LodBand(1, 1), new MaterialSettings.LodBand(2, 2));

    public static final MaterialDefinition VAPOR = new MaterialDefinition("vapor", CURRENT_FOG,
        settings(4, VAPOR_LOD, 1, OptionalDouble.of(1)), Set.of(EXISTING_VAPOR_RULES, SNOW_ICE_SURFACE),
        Set.of(MaterialDefinition.Transformation.WATER));
    public static final MaterialDefinition DUST = new MaterialDefinition("dust", BROWN,
        settings(2, DUST_LOD, 1, OptionalDouble.of(1)),
        Set.of(PLAYER_WALKING, MOB_WALKING, BLOCK_BREAKING, BLOCK_PLACEMENT,
            PLAYER_FALL_DAMAGE, MOB_FALL_DAMAGE), Set.of(MaterialDefinition.Transformation.GRAVEL));
    public static final MaterialDefinition SMOKE = new MaterialDefinition("smoke", BLACK,
        new MaterialSettings(4, VAPOR_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(LAVA, FIRE, EXPLOSIONS, EXPLOSION_DESTROYED_BLOCKS, FURNACES, TORCHES, CAMPFIRES), Set.of());
    public static final MaterialDefinition VIOLENCE = new MaterialDefinition("violence", RED,
        new MaterialSettings(8, VIOLENCE_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(HOSTILE_MOB_DEATHS, NETHERRACK), Set.of(MaterialDefinition.Transformation.ZOMBIE));
    public static final MaterialDefinition EXHAUST = new MaterialDefinition("exhaust", YELLOW,
        settings(2, DUST_LOD, 1, OptionalDouble.of(1)),
        Set.of(LIVING_MOBS_RANDOMLY, CREEPERS_FREQUENTLY, PLAYER_DAMAGE, MOB_DAMAGE), Set.of());
    public static final MaterialDefinition SLIME = new MaterialDefinition("slime", GREEN,
        new MaterialSettings(16, VIOLENCE_LOD, 1, OptionalDouble.of(1), 4),
        Set.of(UNDERGROUND_SLIME_CHUNKS_RANDOMLY), Set.of(MaterialDefinition.Transformation.SLIME));
    public static final MaterialDefinition ENDER_GAS = new MaterialDefinition("ender_gas", PURPLE,
        new MaterialSettings(1, DUST_LOD, 1, OptionalDouble.of(1), 40),
        Set.of(NETHER_PORTAL_BLOCKS_SLOWLY, ENDERMEN, ENDERMITES, ENDER_DRAGON,
            ENDER_PEARL_USE, STANDING_IN_NETHER_PORTAL, SOUL_TORCHES, SOUL_FIRES, SOUL_SAND), Set.of());

    public static final List<MaterialDefinition> ALL = List.of(VAPOR, DUST, SMOKE, VIOLENCE, EXHAUST, SLIME, ENDER_GAS);

    private AtmosphericMaterials() { }

    private static MaterialSettings settings(int size, List<MaterialSettings.LodBand> lod,
                                            double simulationSpeed, OptionalDouble producerSpeed) {
        return new MaterialSettings(size, lod, simulationSpeed, producerSpeed);
    }
}
