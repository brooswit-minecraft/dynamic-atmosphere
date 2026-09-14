package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderGasGameplayTest {
    @Test
    void passiveSourceListIncludesEverySpecifiedBlockFamily() {
        for (String path : new String[]{
            "nether_portal", "ender_chest", "soul_torch", "soul_wall_torch", "soul_fire", "soul_sand",
            "crying_obsidian"
        }) {
            assertTrue(EnderGasGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace(path)));
        }
        assertFalse(EnderGasGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace("chest")));
    }

    @Test
    void fullMoonBurstRequiresNightFullPhaseAndExactIndependentRoll() {
        assertTrue(EnderGasGameplay.fullMoonBurst(true, 0, 0));
        assertFalse(EnderGasGameplay.fullMoonBurst(false, 0, 0));
        assertFalse(EnderGasGameplay.fullMoonBurst(true, 1, 0));
        assertFalse(EnderGasGameplay.fullMoonBurst(true, 0, 1));
    }

    @Test
    void futureNaturalEndermanGateUsesOnlyStrictPurpleDecision() {
        assertTrue(EnderGasSpawnGate.denies(true, true, false));
        assertFalse(EnderGasSpawnGate.denies(true, true, true));
        assertFalse(EnderGasSpawnGate.denies(true, false, false));
        assertFalse(EnderGasSpawnGate.denies(false, true, false));
    }
}
