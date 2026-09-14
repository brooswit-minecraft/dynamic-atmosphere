package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

import static io.github.brooswitminecraft.dynamicatmosphere.VaporHostileSpawnGate.Decision.DENY;
import static io.github.brooswitminecraft.dynamicatmosphere.VaporHostileSpawnGate.Decision.PASS_THROUGH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VaporHostileSpawnGateTest {

    @Test
    void monstersWithoutQualifyingOverheadTerrainRequireDenseVapor() {
        assertEquals(DENY, decide(MobSpawnType.NATURAL, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, false, true));
        assertEquals(DENY, decide(MobSpawnType.CHUNK_GENERATION, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.CHUNK_GENERATION, false, true));
    }

    @Test
    void qualifyingOverheadTerrainLeavesMonstersUnderVanillaControl() {
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, true, false));
    }

    @Test
    void nonHostileAndExplicitSpawnSourcesRemainUnderVanillaControl() {
        assertEquals(PASS_THROUGH,
            VaporHostileSpawnGate.evaluate(MobSpawnType.NATURAL, MobCategory.CREATURE, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.COMMAND, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.SPAWN_EGG, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.SPAWNER, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.TRIAL_SPAWNER, false, false));
    }

    @Test
    void densityDecisionIsReadOnlyAndRepeatable() {
        boolean vaporMoreThanHalfFull = true;
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, false, vaporMoreThanHalfFull));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, false, vaporMoreThanHalfFull));
    }

    private static VaporHostileSpawnGate.Decision decide(
        MobSpawnType spawnType,
        boolean qualifyingTerrainAbove,
        boolean vaporMoreThanHalfFull
    ) {
        return VaporHostileSpawnGate.evaluate(
            spawnType, MobCategory.MONSTER, qualifyingTerrainAbove, vaporMoreThanHalfFull);
    }
}
