package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeCapacityBlockClassifierTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void airSolidsAndBedrockHaveIndependentCapacityAndBarrierSemantics() {
        check(Blocks.AIR.defaultBlockState(), true, false);
        check(Blocks.CAVE_AIR.defaultBlockState(), true, false);
        check(Blocks.STONE.defaultBlockState(), false, false);
        check(Blocks.BEDROCK.defaultBlockState(), false, true);
    }

    @Test
    void everyWaterAndLavaLevelCountsAsEmptySpaceAndBlocksDownwardTransfer() {
        for (var liquid : new BlockState[] {Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()}) {
            for (int level = 0; level <= 15; level++) {
                check(liquid.setValue(LiquidBlock.LEVEL, level), true, true);
            }
        }
    }

    @Test
    void waterloggedHostsStayOccupiedAndAnyFluidBlocksDownwardTransfer() {
        var slab = Blocks.OAK_SLAB.defaultBlockState();
        check(slab.setValue(BlockStateProperties.WATERLOGGED, false), false, false);
        check(slab.setValue(BlockStateProperties.WATERLOGGED, true), false, true);
        check(Blocks.KELP.defaultBlockState(), false, true);
    }

    private static void check(BlockState state, boolean emptySpace, boolean downwardBarrier) {
        assertEquals(emptySpace, ForgeCapacityBlockClassifier.isEmptySpace(state), state.toString());
        assertEquals(downwardBarrier, ForgeCapacityBlockClassifier.blocksDownwardTransfer(state), state.toString());
    }
}
