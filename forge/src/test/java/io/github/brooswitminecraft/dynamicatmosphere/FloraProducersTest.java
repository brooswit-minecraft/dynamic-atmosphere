package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloraProducersTest {
    @BeforeAll static void bootstrap() { ForgeCapacityBlockClassifierTest.bootstrap(); }

    @Test void samplesIncludeUndergroundAndTopWithHardBoundAndNoDuplicatePositions() {
        int[] calls = {0};
        var low = FloraProducers.positions(-16, -32, -64, 384, 32, bound -> { calls[0]++; return 0; });
        assertEquals(96, calls[0]);
        assertEquals(1, low.size());
        assertEquals(-64, low.iterator().next().getY());
        var high = FloraProducers.positions(-16, -32, -64, 384, 32, bound -> bound - 1);
        assertEquals(319, high.iterator().next().getY());
        assertEquals(-1, high.iterator().next().getX());
    }
    @Test void floraAndMoldSourcesAreDistinctAndExplicit() {
        assertTrue(FloraProducers.caveFlora(Blocks.CAVE_VINES.defaultBlockState()));
        assertTrue(FloraProducers.caveFlora(Blocks.MOSS_BLOCK.defaultBlockState()));
        assertFalse(FloraProducers.caveFlora(Blocks.CLAY.defaultBlockState()));
        for (var block : new net.minecraft.world.level.block.Block[] {Blocks.MYCELIUM, Blocks.RED_MUSHROOM,
                Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM}) {
            assertTrue(FloraProducers.moldSource(block.defaultBlockState()));
        }
        assertFalse(FloraProducers.moldSource(Blocks.STONE.defaultBlockState()));
    }
    @Test void everySnowLayerUsesVaporWhileOrdinaryGroundUsesDust() {
        for (int layer = 1; layer <= 8; layer++) {
            assertTrue(DustGameplay.isSnowOrIce(Blocks.SNOW.defaultBlockState()
                .setValue(BlockStateProperties.LAYERS, layer)));
        }
        assertFalse(DustGameplay.isSnowOrIce(Blocks.SAND.defaultBlockState()));
        assertFalse(DustGameplay.isSnowOrIce(Blocks.DIRT.defaultBlockState()));
    }
}
