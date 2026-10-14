package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObsidianPowderGameplayTest {
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
}
