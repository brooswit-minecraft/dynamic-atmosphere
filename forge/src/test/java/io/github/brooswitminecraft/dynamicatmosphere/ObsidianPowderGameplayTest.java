package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void passiveSourceListIncludesEverySpecifiedBlockFamilyExceptTheTwoSplitOutOnTheirOwnKeys() {
        for (String path : new String[]{
            "nether_portal", "ender_chest", "soul_torch", "soul_wall_torch", "soul_fire", "soul_sand"
        }) {
            assertTrue(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace(path)));
        }
        assertFalse(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace("chest")));
        // Crying obsidian and plain obsidian now read their own config keys instead of the shared one.
        assertFalse(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace("crying_obsidian")));
        assertFalse(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace("obsidian")));
    }

    @Test
    void cryingObsidianAndObsidianReadTheirOwnKeysNotTheSharedPassiveKey() {
        assertEquals(ObsidianPowderGameplay.PassiveEmissionKey.CRYING_OBSIDIAN,
            ObsidianPowderGameplay.passiveEmissionKey(Blocks.CRYING_OBSIDIAN.defaultBlockState()));
        assertEquals(ObsidianPowderGameplay.PassiveEmissionKey.OBSIDIAN,
            ObsidianPowderGameplay.passiveEmissionKey(Blocks.OBSIDIAN.defaultBlockState()));
    }

    @Test
    void restOfThePassiveSetStillUsesTheSharedKey() {
        for (var state : new BlockState[] {
            Blocks.ENDER_CHEST.defaultBlockState(), Blocks.SOUL_TORCH.defaultBlockState(),
            Blocks.SOUL_WALL_TORCH.defaultBlockState(), Blocks.SOUL_FIRE.defaultBlockState(),
            Blocks.SOUL_SAND.defaultBlockState()
        }) {
            assertEquals(ObsidianPowderGameplay.PassiveEmissionKey.SHARED,
                ObsidianPowderGameplay.passiveEmissionKey(state), state.toString());
        }
        // Nether portal is listed but excluded from passive sampling; it has its own portalBlockEmission key.
        assertEquals(ObsidianPowderGameplay.PassiveEmissionKey.NONE,
            ObsidianPowderGameplay.passiveEmissionKey(Blocks.NETHER_PORTAL.defaultBlockState()));
        assertEquals(ObsidianPowderGameplay.PassiveEmissionKey.NONE,
            ObsidianPowderGameplay.passiveEmissionKey(Blocks.STONE.defaultBlockState()));
    }
}
