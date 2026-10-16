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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // --- Consumption/lifecycle (PR #63 review): charging is tagged at PositionCheck time and
    // only actually applied at a confirmed EntityJoinLevelEvent, never at the earlier deny check.

    @Test
    void onlyTheGatedBranchIsTaggedForExhaustCharge() {
        // The exact branch evaluate() would PASS_THROUGH via night && exhaustDense is tagged.
        assertTrue(tagged(MobSpawnType.NATURAL, false, true, true));
        assertTrue(tagged(MobSpawnType.CHUNK_GENERATION, false, true, true));
    }

    @Test
    void deniedAttemptsAreNeverTaggedForCharge() {
        // Same inputs that would DENY at onSpawnPlacementCheck must never be tagged either --
        // there is nothing to charge for a spawn that was refused outright.
        assertFalse(tagged(MobSpawnType.NATURAL, false, false, false));
        assertFalse(tagged(MobSpawnType.NATURAL, false, true, false));
        assertFalse(tagged(MobSpawnType.NATURAL, false, false, true));
    }

    @Test
    void qualifyingTerrainPassThroughIsNeverTaggedForCharge() {
        // Below-ground/sheltered spawns pass through ungated and must never be charged, even
        // when night and Exhaust both happen to hold at that position.
        assertFalse(tagged(MobSpawnType.NATURAL, true, true, true));
    }

    @Test
    void nonCreeperOrNonOverworldOrExplicitSpawnsAreNeverTaggedForCharge() {
        assertFalse(CreeperExhaustSpawnGate.isEligibleForExhaustCharge(
            MobSpawnType.NATURAL, EntityType.ZOMBIE, true, false, true, true));
        assertFalse(CreeperExhaustSpawnGate.isEligibleForExhaustCharge(
            MobSpawnType.NATURAL, EntityType.CREEPER, false, false, true, true));
        assertFalse(CreeperExhaustSpawnGate.isEligibleForExhaustCharge(
            MobSpawnType.SPAWN_EGG, EntityType.CREEPER, true, false, true, true));
    }

    @Test
    void joiningIsChargedOnlyWhenTaggedFreshAndServerSide() {
        assertTrue(CreeperExhaustSpawnGate.shouldChargeOnJoin(true, false, false));
    }

    @Test
    void untaggedLoadedFromDiskOrClientSideJoinsAreNeverCharged() {
        // A tagged attempt that was vetoed downstream (obstruction, pathfinding, FinalizeSpawn)
        // never reaches EntityJoinLevelEvent tagged, which this models as tagged=false here --
        // the real listener only ever sees `tagged` true for an attempt that actually joins.
        assertFalse(CreeperExhaustSpawnGate.shouldChargeOnJoin(false, false, false));
        // A chunk reload bringing back a persisted Creeper must never be charged again.
        assertFalse(CreeperExhaustSpawnGate.shouldChargeOnJoin(true, true, false));
        // The logical-client mirror of the same join must never charge either.
        assertFalse(CreeperExhaustSpawnGate.shouldChargeOnJoin(true, false, true));
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

    private static boolean tagged(
        MobSpawnType spawnType,
        boolean qualifyingTerrainAbove,
        boolean night,
        boolean exhaustMoreThanHalfFull
    ) {
        return CreeperExhaustSpawnGate.isEligibleForExhaustCharge(
            spawnType, EntityType.CREEPER, true, qualifyingTerrainAbove, night, exhaustMoreThanHalfFull);
    }
}
