package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.brooswitminecraft.dynamicatmosphere.VaporHostileSpawnGate.Decision.DENY;
import static io.github.brooswitminecraft.dynamicatmosphere.VaporHostileSpawnGate.Decision.PASS_THROUGH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VaporHostileSpawnGateTest {

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
            VaporHostileSpawnGate.evaluate(MobSpawnType.NATURAL, MobCategory.CREATURE, true, false, false));
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

    @Test
    void netherAndEndMonstersAreNeverGatedByVapor() {
        // ATMO-9: no qualifying terrain above and zero Vapor must still pass through
        // outside the Overworld — the gate has no business there at all.
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.NATURAL, MobCategory.MONSTER, false, false, false));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.CHUNK_GENERATION, MobCategory.MONSTER, false, false, false));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.NATURAL, MobCategory.MONSTER, false, false, true));
    }

    @Test
    void endermenAreNotSpecialCased() {
        // ATMO-19: the Enderman exemption (and the independent Obsidian Powder spawn gate it used) is
        // removed. Endermen must get the exact same natural/chunk-generation decision as any
        // other Overworld monster. These call evaluate() with EntityType.ENDERMAN specifically,
        // not just MobCategory.MONSTER, so a reintroduced Enderman carve-out in the decision
        // layer would flip these assertions.
        assertEquals(DENY, VaporHostileSpawnGate.evaluate(
            MobSpawnType.NATURAL, EntityType.ENDERMAN, true, false, false));
        assertEquals(DENY, VaporHostileSpawnGate.evaluate(
            MobSpawnType.CHUNK_GENERATION, EntityType.ENDERMAN, true, false, false));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.NATURAL, EntityType.ENDERMAN, true, false, true));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.NATURAL, EntityType.ENDERMAN, true, true, false));
    }

    @Test
    void endermenOutsideTheOverworldAreNeverGatedByVaporOrObsidianPowder() {
        // Vapor gating is Overworld-only (ATMO-9) and Obsidian Powder never gates spawning at all
        // (ATMO-19). `evaluate` does not distinguish Nether from End — both are simply
        // `overworld = false` — so covering that one boolean covers both dimensions.
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.NATURAL, EntityType.ENDERMAN, false, false, false));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.CHUNK_GENERATION, EntityType.ENDERMAN, false, false, false));
    }

    @Test
    void nonNaturalEndermanSpawnsRemainUnderVanillaControl() {
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.COMMAND, EntityType.ENDERMAN, true, false, false));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.SPAWN_EGG, EntityType.ENDERMAN, true, false, false));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.SPAWNER, EntityType.ENDERMAN, true, false, false));
        assertEquals(PASS_THROUGH, VaporHostileSpawnGate.evaluate(
            MobSpawnType.TRIAL_SPAWNER, EntityType.ENDERMAN, true, false, false));
    }

    private static VaporHostileSpawnGate.Decision decide(
        MobSpawnType spawnType,
        boolean qualifyingTerrainAbove,
        boolean vaporMoreThanHalfFull
    ) {
        return VaporHostileSpawnGate.evaluate(
            spawnType, MobCategory.MONSTER, true, qualifyingTerrainAbove, vaporMoreThanHalfFull);
    }
}
