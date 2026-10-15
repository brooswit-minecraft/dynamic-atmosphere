package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the two production fluid-place listeners together on the real event bus:
 * {@link LavaIceInteractions#fireLavaResult} (ice ladder) and
 * {@link LavaWaterMagmaGameplay#onFluidPlaceBlock} (lava + water -> magma). Pure-rule
 * tests cannot catch the two listeners overriding each other.
 */
class LavaIceWaterMagmaInteractionTest {
    private static final Consumer<BlockEvent.FluidPlaceBlockEvent> LISTENER = LavaWaterMagmaGameplay::onFluidPlaceBlock;

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.FluidPlaceBlockEvent.class, LISTENER);
        // FML starts the game bus at launch; nothing does in a unit test, and an unstarted bus drops every post.
        try {
            NeoForge.EVENT_BUS.start();
        } catch (IllegalStateException alreadyStarted) {
            // another test class in this JVM started it first
        }
    }

    @AfterAll
    static void unregister() {
        NeoForge.EVENT_BUS.unregister(LISTENER);
    }

    /** A level whose only water is at {@code waterAt} (or none); records block writes. */
    private static LevelAccessor level(BlockPos waterAt, List<BlockPos> writes) {
        return (LevelAccessor) Proxy.newProxyInstance(
            LavaIceWaterMagmaInteractionTest.class.getClassLoader(),
            new Class<?>[] { LevelAccessor.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getFluidState" -> ((BlockPos) args[0]).equals(waterAt)
                    ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
                case "getBlockState" -> Blocks.LAVA.defaultBlockState();
                case "setBlock" -> { writes.add((BlockPos) args[0]); yield true; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    @Test
    void packedIceLavaStaysObsidianWithBothListenersRegistered() {
        BlockState result = LavaIceInteractions.fireLavaResult(
            level(null, new ArrayList<>()), BlockPos.ZERO, LavaIceRules.Rung.PACKED_ICE);
        assertEquals(Blocks.OBSIDIAN.defaultBlockState(), result);
    }

    @Test
    void blueIceLavaStaysObsidianWithBothListenersRegistered() {
        BlockState result = LavaIceInteractions.fireLavaResult(
            level(null, new ArrayList<>()), BlockPos.ZERO, LavaIceRules.Rung.BLUE_ICE);
        assertEquals(Blocks.OBSIDIAN.defaultBlockState(), result);
    }

    @Test
    void iceLavaStillBecomesMagma() {
        BlockState result = LavaIceInteractions.fireLavaResult(
            level(null, new ArrayList<>()), BlockPos.ZERO, LavaIceRules.Rung.ICE);
        assertEquals(Blocks.MAGMA_BLOCK.defaultBlockState(), result);
    }

    @Test
    void iceLadderObsidianStaysObsidianEvenWithUnrelatedWaterNextToTheLava() {
        List<BlockPos> writes = new ArrayList<>();
        BlockState result = LavaIceInteractions.fireLavaResult(
            level(BlockPos.ZERO.north(), writes), BlockPos.ZERO, LavaIceRules.Rung.BLUE_ICE);
        assertEquals(Blocks.OBSIDIAN.defaultBlockState(), result);
    }

    @Test
    void genuineWaterOnLavaStillBecomesMagmaAndClearsTheWater() {
        BlockPos water = BlockPos.ZERO.north();
        List<BlockPos> writes = new ArrayList<>();
        // The guard must be released after an ice-ladder event, so vanilla's obsidian is still overridden.
        LavaIceInteractions.fireLavaResult(level(null, new ArrayList<>()), BlockPos.ZERO, LavaIceRules.Rung.PACKED_ICE);

        BlockState result = EventHooks.fireFluidPlaceBlockEvent(
            level(water, writes), BlockPos.ZERO, BlockPos.ZERO, Blocks.OBSIDIAN.defaultBlockState());

        // Water clearing itself is not asserted here: FluidState.is(FluidTags.WATER) needs bound tags,
        // which a bootstrap-only unit test does not have (it is covered by the live-server check).
        assertEquals(Blocks.MAGMA_BLOCK.defaultBlockState(), result);
    }

    @Test
    void genuineLavaSpreadingDownIntoWaterStillBecomesMagma() {
        BlockState result = EventHooks.fireFluidPlaceBlockEvent(
            level(null, new ArrayList<>()), BlockPos.ZERO, BlockPos.ZERO.above(), Blocks.STONE.defaultBlockState());
        assertEquals(Blocks.MAGMA_BLOCK.defaultBlockState(), result);
    }
}
