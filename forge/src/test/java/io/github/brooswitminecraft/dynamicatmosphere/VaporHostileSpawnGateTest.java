package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

import static io.github.brooswitminecraft.dynamicatmosphere.VaporHostileSpawnGate.Decision.DENY;
import static io.github.brooswitminecraft.dynamicatmosphere.VaporHostileSpawnGate.Decision.PASS_THROUGH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VaporHostileSpawnGateTest {

    @Test
    void exposedNaturalAndChunkGenerationMonstersRequireDenseVapor() {
        assertEquals(DENY, decide(MobSpawnType.NATURAL, true, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, true, true));
        assertEquals(DENY, decide(MobSpawnType.CHUNK_GENERATION, true, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.CHUNK_GENERATION, true, true));
    }

    @Test
    void undergroundMonstersRemainUnderVanillaControl() {
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, false, false));
    }

    @Test
    void nonHostileAndExplicitSpawnSourcesRemainUnderVanillaControl() {
        assertEquals(PASS_THROUGH,
            VaporHostileSpawnGate.evaluate(MobSpawnType.NATURAL, MobCategory.CREATURE, true, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.COMMAND, true, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.SPAWN_EGG, true, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.SPAWNER, true, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.TRIAL_SPAWNER, true, false));
    }

    @Test
    void densityDecisionIsReadOnlyAndRepeatable() {
        boolean vaporMoreThanHalfFull = true;
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, true, vaporMoreThanHalfFull));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, true, vaporMoreThanHalfFull));
    }

    private static VaporHostileSpawnGate.Decision decide(
        MobSpawnType spawnType,
        boolean canSeeSky,
        boolean vaporMoreThanHalfFull
    ) {
        return VaporHostileSpawnGate.evaluate(
            spawnType, MobCategory.MONSTER, canSeeSky, vaporMoreThanHalfFull);
    }
}
