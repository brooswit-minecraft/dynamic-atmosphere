package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObsidianPowderGameplayTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void portalEmissionFacesArePerpendicularToPortalPlane() {
        assertEquals(Direction.NORTH, ObsidianPowderGameplay.portalNormal(Direction.Axis.X));
        assertEquals(Direction.EAST, ObsidianPowderGameplay.portalNormal(Direction.Axis.Z));
    }

    @Test
    void passiveSourceListIncludesEverySpecifiedBlockFamily() {
        for (String path : new String[]{
            "nether_portal", "ender_chest", "soul_torch", "soul_wall_torch", "soul_fire", "soul_sand",
            "crying_obsidian", "obsidian"
        }) {
            assertTrue(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace(path)));
        }
        assertFalse(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace("chest")));
    }

    @Test
    void listedEnderMobsIncludePhantomAlongsideTheExistingSet() {
        // MINECRAFT-368: the flying Phantom joins the Enderman/Endermite/EnderDragon/Witch/Shulker
        // set that already drives this exact OBSIDIAN_POWDER emission path; nothing new is added
        // for any other mob.
        for (EntityType<?> type : new EntityType<?>[]{
            EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.ENDER_DRAGON,
            EntityType.WITCH, EntityType.SHULKER, EntityType.PHANTOM
        }) {
            assertTrue(ObsidianPowderGameplay.isEnderMob(type), type + " should be a listed mob");
        }
        assertFalse(ObsidianPowderGameplay.isEnderMob(EntityType.ZOMBIE));
    }

    @Test
    void phantomEmitsObsidianPowderOnTheSameMobIntervalAsOtherListedMobs() {
        // Tick schedule and entity id combine the same way isEnderMob's callers already relied on
        // for Endermen; a non-matching tick, or a non-listed mob, must not emit.
        assertEquals(5, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, false, 40L, 0, 40, 5, 20, 2));
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, false, 41L, 0, 40, 5, 20, 2));
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.ZOMBIE, false, 40L, 0, 40, 5, 20, 2));
    }

    @Test
    void stationaryNonPortalPhantomProducesNoEmission() {
        // A Phantom off its mob-interval tick and not inside a portal must not emit at all —
        // guards against a change that makes Phantom emission unconditional instead of
        // interval-gated like every other listed mob.
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, false, 1L, 0, 40, 5, 20, 2));
    }
}
