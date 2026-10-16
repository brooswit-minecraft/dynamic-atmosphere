package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidGasGameplayTest {
    @Test
    void onlyMonsterCategoryDeathsAreHostileProduction() {
        assertTrue(VoidGasGameplay.isHostileCategory(MobCategory.MONSTER));
        assertFalse(VoidGasGameplay.isHostileCategory(MobCategory.CREATURE));
        assertFalse(VoidGasGameplay.isHostileCategory(MobCategory.MISC));
    }

    @Test
    void bottomProducerFiresUnconditionally() {
        assertEquals(1, VoidGasGameplay.BOTTOM_CHANCE_DENOMINATOR);
        assertTrue(VoidGasGameplay.bottomEmission(0));
    }

    @Test
    void tenThroughTwentyFivePercentReadiesVillagerForFivePercentCurrentCost() {
        var atTen = VoidGasGameplay.effectFor(100, 1_000);
        var atTwentyFive = VoidGasGameplay.effectFor(250, 1_000);
        assertTrue(atTen.villagerBand());
        assertEquals(5, atTen.cost());
        assertTrue(atTwentyFive.villagerBand());
        assertEquals(13, atTwentyFive.cost());
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(99, 1_000));
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(251, 1_000));
    }

    @Test
    void highFullnessNoLongerProducesAnEffect() {
        // MINECRAFT-17: the consumption-driven zombie spawn above 75% fullness is removed.
        // A full cell now only affects VaporHostileSpawnGate surface-spawn eligibility.
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(750, 1_000));
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(1_000, 1_000));
        assertEquals(VoidGasGameplay.CellEffect.NONE, VoidGasGameplay.effectFor(100, 0));
    }
}
