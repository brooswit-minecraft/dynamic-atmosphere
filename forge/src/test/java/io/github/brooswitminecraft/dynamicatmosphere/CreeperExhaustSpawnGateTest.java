package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.brooswitminecraft.dynamicatmosphere.CreeperExhaustSpawnGate.Decision.DENY;
import static io.github.brooswitminecraft.dynamicatmosphere.CreeperExhaustSpawnGate.Decision.PASS_THROUGH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CreeperExhaustSpawnGateTest {

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void surfaceCreeperSpawnsRequireBothNightAndDenseExhaust() {
        assertEquals(DENY, decide(MobSpawnType.NATURAL, false, false, false));
        assertEquals(DENY, decide(MobSpawnType.NATURAL, false, true, false));
        assertEquals(DENY, decide(MobSpawnType.NATURAL, false, false, true));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, false, true, true));
        assertEquals(DENY, decide(MobSpawnType.CHUNK_GENERATION, false, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.CHUNK_GENERATION, false, true, true));
    }

    @Test
    void qualifyingOverheadTerrainLeavesCreepersUnderVanillaControl() {
        // Below-ground (and any other sheltered) spawn position always has qualifying terrain
        // above it, so this gate must never touch it, regardless of night or Exhaust.
        assertEquals(PASS_THROUGH, decide(MobSpawnType.NATURAL, true, false, false));
    }

    @Test
    void nonCreeperMonstersAreNeverGatedHere() {
        assertEquals(PASS_THROUGH, CreeperExhaustSpawnGate.evaluate(
            MobSpawnType.NATURAL, EntityType.ZOMBIE, true, false, true, true));
        assertEquals(PASS_THROUGH, CreeperExhaustSpawnGate.evaluate(
            MobSpawnType.NATURAL, EntityType.ZOMBIE, true, false, false, false));
    }

    @Test
    void explicitSpawnSourcesRemainUnderVanillaControl() {
        assertEquals(PASS_THROUGH, decide(MobSpawnType.COMMAND, false, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.SPAWN_EGG, false, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.SPAWNER, false, false, false));
        assertEquals(PASS_THROUGH, decide(MobSpawnType.TRIAL_SPAWNER, false, false, false));
    }

    @Test
    void creepersOutsideTheOverworldAreNeverGatedByThisRule() {
        assertEquals(PASS_THROUGH, CreeperExhaustSpawnGate.evaluate(
            MobSpawnType.NATURAL, EntityType.CREEPER, false, false, false, false));
        assertEquals(PASS_THROUGH, CreeperExhaustSpawnGate.evaluate(
            MobSpawnType.CHUNK_GENERATION, EntityType.CREEPER, false, false, true, true));
    }

    private static CreeperExhaustSpawnGate.Decision decide(
        MobSpawnType spawnType,
        boolean qualifyingTerrainAbove,
        boolean night,
        boolean exhaustMoreThanHalfFull
    ) {
        return CreeperExhaustSpawnGate.evaluate(
            spawnType, EntityType.CREEPER, true, qualifyingTerrainAbove, night, exhaustMoreThanHalfFull);
    }
}
