package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Comparator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForgeAtmospherePressureTest {
    @BeforeAll
    static void bootstrap() {
        ForgeCapacityBlockClassifierTest.bootstrap();
    }

    @Test
    void pressureTraversesLiquidCellsHorizontallyAndUpwardButNotDownward() {
        for (var liquid : new BlockState[] {Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()}) {
            for (var direction : new int[][] {{1, 0, 0}, {0, 1, 0}, {0, -1, 0}}) {
                var source = new AtmosphereGrid.CellKey<>("test", 0, 0, 0);
                var transit = new AtmosphereGrid.CellKey<>("test", direction[0], direction[1], direction[2]);
                var target = new AtmosphereGrid.CellKey<>("test", direction[0] * 2, direction[1] * 2, direction[2] * 2);
                var cells = Map.of(source, liquid, transit, liquid, target, Blocks.STONE.defaultBlockState());
                var terrain = new Terrain(cells);
                var result = AtmospherePressure.selectForAttempt(source, cells::containsKey,
                    cell -> ForgeAtmospherePressure.scan(terrain, cell, 1, ignored -> true),
                    Comparator.comparingInt(BlockPos::getY), 16,
                    (from, to) -> to.y() >= from.y()
                        || !ForgeCapacityBlockClassifier.blocksDownwardTransfer(cells.get(from)),
                    () -> 0.99);
                if (direction[1] < 0) {
                    assertTrue(result.selection().isEmpty());
                } else {
                    assertEquals(target, result.selection().orElseThrow().cell());
                }
            }
        }
    }

    @Test
    void scanCountsLiquidAsEmptyButKeepsWaterloggedHostsOccupiedAndProtected() {
        var source = new AtmosphereGrid.CellKey<>("test", 0, 0, 0);
        var wetSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        for (var state : new BlockState[] {Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(), wetSlab}) {
            var scan = ForgeAtmospherePressure.scan(new Terrain(Map.of(source, state)), source, 1, ignored -> true);
            assertEquals(state == wetSlab ? 0 : 1, scan.emptyBlocks());
            assertTrue(scan.candidates().isEmpty());
        }
    }

    private record Terrain(Map<AtmosphereGrid.CellKey<String>, BlockState> cells) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            return cells.getOrDefault(new AtmosphereGrid.CellKey<>("test", pos.getX(), pos.getY(), pos.getZ()),
                Blocks.BEDROCK.defaultBlockState());
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
