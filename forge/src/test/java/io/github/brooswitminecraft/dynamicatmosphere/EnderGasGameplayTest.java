package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderGasGameplayTest {
    @Test
    void passiveSourceListIncludesEverySpecifiedBlockFamily() {
        assertTrue(EnderGasGameplay.isPassiveSource(Blocks.NETHER_PORTAL.defaultBlockState()));
        assertTrue(EnderGasGameplay.isPassiveSource(Blocks.ENDER_CHEST.defaultBlockState()));
        assertTrue(EnderGasGameplay.isPassiveSource(Blocks.SOUL_TORCH.defaultBlockState()));
        assertTrue(EnderGasGameplay.isPassiveSource(Blocks.SOUL_WALL_TORCH.defaultBlockState()));
        assertTrue(EnderGasGameplay.isPassiveSource(Blocks.SOUL_FIRE.defaultBlockState()));
        assertTrue(EnderGasGameplay.isPassiveSource(Blocks.SOUL_SAND.defaultBlockState()));
        assertFalse(EnderGasGameplay.isPassiveSource(Blocks.CHEST.defaultBlockState()));
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
        assertTrue(EnderGasSpawnGate.denies(EntityType.ENDERMAN, MobSpawnType.NATURAL, false));
        assertFalse(EnderGasSpawnGate.denies(EntityType.ENDERMAN, MobSpawnType.NATURAL, true));
        assertFalse(EnderGasSpawnGate.denies(EntityType.ENDERMAN, MobSpawnType.COMMAND, false));
        assertFalse(EnderGasSpawnGate.denies(EntityType.ZOMBIE, MobSpawnType.NATURAL, false));
    }
}
