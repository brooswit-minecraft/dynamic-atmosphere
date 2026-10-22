package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Calls the actual cell turn, including vanilla MushroomBlock.canSurvive; the server I/O is mocked. */
class MoldPlacementIntegrationTest {
    @BeforeAll static void bootstrap() { ForgeCapacityBlockClassifierTest.bootstrap(); }

    @Test void unloadedEdgesDeferBeforeSurvivalOrMutationAndChargeOnceAfterLoading() {
        for (BlockPos target : new BlockPos[] {new BlockPos(15,64,15), new BlockPos(0,64,0),
                new BlockPos(-1,64,-1), new BlockPos(-16,64,-16)}) {
            for (boolean xEdge : new boolean[] {true, false}) {
                ServerLevel level = mock(ServerLevel.class);
                RandomSource random = mock(RandomSource.class);
                int local = Math.floorMod(target.getX(), 4);
                when(random.nextInt(4)).thenReturn(local, 0, local);
                when(random.nextDouble()).thenReturn(0.0);
                when(random.nextBoolean()).thenReturn(true);
                BlockPos origin = new BlockPos(Math.floorDiv(target.getX(),4)*4,64,Math.floorDiv(target.getZ(),4)*4);
                int direction = Math.floorMod(target.getX(),16) == 15 ? 1 : -1;
                ChunkPos missing = new ChunkPos(target.offset(xEdge ? direction : 0, 0, xEdge ? 0 : direction));
                boolean[] loaded = {false};
                when(level.hasChunkAt(any())).thenAnswer(call -> loaded[0]
                        || !new ChunkPos((BlockPos)call.getArgument(0)).equals(missing));
                when(level.isInWorldBounds(any())).thenReturn(true);
                BlockState[] targetState = {Blocks.AIR.defaultBlockState()};
                when(level.getBlockState(any())).thenAnswer(call -> target.equals(call.getArgument(0))
                        ? targetState[0] : Blocks.STONE.defaultBlockState());
                when(level.setBlockAndUpdate(eq(target), any())).thenAnswer(call -> {
                    targetState[0] = call.getArgument(1); return true;
                });

                assertEquals(0, MoldGameplay.processCell(level, origin, 100, 1000, random), "unloaded edge must debit zero");
                verify(level, never()).setBlockAndUpdate(any(), any());
                verify(level, never()).getRawBrightness(any(), anyInt());
                assertTrue(targetState[0].isAir());

                loaded[0] = true;
                when(random.nextInt(4)).thenReturn(local, 0, local);
                assertEquals(16, MoldGameplay.processCell(level, origin, 100, 1000, random));
                assertTrue(targetState[0].is(Blocks.RED_MUSHROOM));
                verify(level, times(1)).setBlockAndUpdate(eq(target), any());
                when(random.nextInt(4)).thenReturn(local, 0, local);
                assertEquals(0, MoldGameplay.processCell(level, origin, 84, 1000, random), "existing mushroom cannot be charged again");
                verify(level, times(1)).setBlockAndUpdate(eq(target), any());
                assertTrue(mockingDetails(level).getInvocations().stream()
                        .noneMatch(call -> call.getMethod().getName().startsWith("getChunk")), "placement path requested a chunk load");
            }
        }
    }
}
