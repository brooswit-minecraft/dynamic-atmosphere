package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolenceGameplayTest {
    @Test
    void onlyMonsterCategoryDeathsAreHostileProduction() {
        assertTrue(ViolenceGameplay.isHostileCategory(MobCategory.MONSTER));
        assertFalse(ViolenceGameplay.isHostileCategory(MobCategory.CREATURE));
        assertFalse(ViolenceGameplay.isHostileCategory(MobCategory.MISC));
    }

    @Test
    void bottomProducerUsesOneExactOutcomeOfEight() {
        assertTrue(ViolenceGameplay.bottomEmission(0));
        for (int roll = 1; roll < ViolenceGameplay.BOTTOM_CHANCE_DENOMINATOR; roll++) {
            assertFalse(ViolenceGameplay.bottomEmission(roll));
        }
    }

    @Test
    void tenThroughTwentyFivePercentReadiesVillagerForFivePercentCurrentCost() {
        var atTen = ViolenceGameplay.effectFor(100, 1_000, 1);
        var atTwentyFive = ViolenceGameplay.effectFor(250, 1_000, 1);
        assertTrue(atTen.villagerBand());
        assertEquals(5, atTen.cost());
        assertTrue(atTwentyFive.villagerBand());
        assertEquals(13, atTwentyFive.cost());
        assertEquals(ViolenceGameplay.CellEffect.NONE, ViolenceGameplay.effectFor(99, 1_000, 0));
        assertEquals(ViolenceGameplay.CellEffect.NONE, ViolenceGameplay.effectFor(251, 1_000, 1));
    }

    @Test
    void highDensitySpawnIsChanceGatedAndHasFiniteQuarterCapacityCost() {
        var spawn = ViolenceGameplay.effectFor(750, 1_000, 0);
        assertTrue(spawn.spawnZombie());
        assertEquals(250, spawn.cost());
        assertEquals(ViolenceGameplay.CellEffect.NONE, ViolenceGameplay.effectFor(749, 1_000, 0));
        assertEquals(ViolenceGameplay.CellEffect.NONE, ViolenceGameplay.effectFor(750, 1_000, 1));
        assertEquals(ViolenceGameplay.CellEffect.NONE, ViolenceGameplay.effectFor(100, 0, 0));
    }
}
