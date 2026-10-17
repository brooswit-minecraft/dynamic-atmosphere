package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObsidianPowderGameplayTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class FakeSink implements ObsidianPowderGameplay.EmissionSink {
        record Call(AtmosphereMaterial material, BlockPos pos, int amount) { }

        final List<Call> calls = new ArrayList<>();

        @Override
        public boolean emit(AtmosphereMaterial material, BlockPos pos, int amount) {
            calls.add(new Call(material, pos, amount));
            return true;
        }
    }

    @Test
    void portalEmissionFacesArePerpendicularToPortalPlane() {
        assertEquals(Direction.NORTH, ObsidianPowderGameplay.portalNormal(Direction.Axis.X));
        assertEquals(Direction.EAST, ObsidianPowderGameplay.portalNormal(Direction.Axis.Z));
    }

    @Test
    void passiveSourceListIncludesEverySpecifiedBlockFamily() {
        for (String path : new String[]{
            "nether_portal", "ender_chest", "soul_torch", "soul_wall_torch", "soul_fire", "soul_sand",
            "crying_obsidian", "obsidian"
        }) {
            assertTrue(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace(path)));
        }
        assertFalse(ObsidianPowderGameplay.isPassiveSourceId(ResourceLocation.withDefaultNamespace("chest")));
    }

    @Test
    void existingListedEnderMobsAreUnconditionalAndPhantomIsNotAmongThem() {
        // isEnderMob itself must stay exactly as it was for the pre-existing set -- Phantom's
        // extra airborne/moving condition lives in isFlyingPhantom/isListedMobTick instead, so
        // existing Enderman-style behavior for these mobs is untouched by this ticket.
        for (EntityType<?> type : new EntityType<?>[]{
            EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.ENDER_DRAGON,
            EntityType.WITCH, EntityType.SHULKER
        }) {
            assertTrue(ObsidianPowderGameplay.isEnderMob(type), type + " should be a listed mob");
        }
        assertFalse(ObsidianPowderGameplay.isEnderMob(EntityType.PHANTOM));
        assertFalse(ObsidianPowderGameplay.isEnderMob(EntityType.ZOMBIE));
    }

    @Test
    void phantomTicksOnlyWhileActuallyAirborneAndMoving() {
        // The ticket's "stationary/non-emitting Phantom" regression case: grounded or
        // zero-velocity must not emit even on an otherwise-matching mob-interval tick, while a
        // genuinely flying Phantom on that same tick does, exactly like the other listed mobs.
        assertTrue(ObsidianPowderGameplay.isListedMobTick(EntityType.PHANTOM, true, true));
        assertFalse(ObsidianPowderGameplay.isListedMobTick(EntityType.PHANTOM, false, true), "grounded");
        assertFalse(ObsidianPowderGameplay.isListedMobTick(EntityType.PHANTOM, true, false), "stationary");
        assertFalse(ObsidianPowderGameplay.isListedMobTick(EntityType.PHANTOM, false, false), "grounded and stationary");
        // Other listed mobs stay unconditional regardless of airborne/moving state.
        assertTrue(ObsidianPowderGameplay.isListedMobTick(EntityType.ENDERMAN, false, false));
        assertFalse(ObsidianPowderGameplay.isListedMobTick(EntityType.ZOMBIE, true, true));
    }

    @Test
    void phantomEmitsObsidianPowderOnTheSameMobIntervalAsOtherListedMobsOnlyWhileFlying() {
        assertEquals(5, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, true, true, false, 40L, 0, 40, 5, 20, 2));
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, false, true, false, 40L, 0, 40, 5, 20, 2), "grounded on a matching tick");
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, true, false, false, 40L, 0, 40, 5, 20, 2), "stationary on a matching tick");
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, true, true, false, 41L, 0, 40, 5, 20, 2), "off-schedule tick");
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.ZOMBIE, true, true, false, 40L, 0, 40, 5, 20, 2), "non-listed mob");
    }

    @Test
    void stationaryNonPortalPhantomProducesNoEmission() {
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, true, false, false, 40L, 0, 40, 5, 20, 2));
        assertEquals(0, ObsidianPowderGameplay.entityTickEmission(
            EntityType.PHANTOM, false, false, false, 40L, 0, 40, 5, 20, 2));
    }

    @Test
    void productionEntityTickWiringEmitsExactMaterialPositionAndAmountForAFlyingPhantom() {
        // Exercises the real onEntityTick wiring (seamed via EmissionSink), not just the pure
        // schedule helper: a flying Phantom on its mob-interval tick must reach the sink with
        // the exact OBSIDIAN_POWDER identity, its own block position, and the configured amount.
        FakeSink sink = new FakeSink();
        BlockPos pos = new BlockPos(5, 70, -3);
        ObsidianPowderGameplay.emitForEntityTick(sink, EntityType.PHANTOM, true, true, false,
            40L, 0, pos, 40, 5, 20, 2);
        assertEquals(1, sink.calls.size());
        assertEquals(new FakeSink.Call(AtmosphereMaterial.OBSIDIAN_POWDER, pos, 5), sink.calls.get(0));
    }

    @Test
    void productionEntityTickWiringEmitsNothingForAGroundedOrStationaryPhantom() {
        FakeSink sink = new FakeSink();
        BlockPos pos = new BlockPos(5, 70, -3);
        ObsidianPowderGameplay.emitForEntityTick(sink, EntityType.PHANTOM, false, true, false,
            40L, 0, pos, 40, 5, 20, 2);
        ObsidianPowderGameplay.emitForEntityTick(sink, EntityType.PHANTOM, true, false, false,
            40L, 0, pos, 40, 5, 20, 2);
        assertTrue(sink.calls.isEmpty(), "grounded or stationary Phantom must never reach the sink");
    }
}
