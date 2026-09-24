package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LavaIceRulesTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rungOfClassifiesOnlyTheThreeIceBlocksAndNothingElse() {
        assertEquals(LavaIceRules.Rung.ICE, LavaIceRules.rungOf(Blocks.ICE.defaultBlockState()));
        assertEquals(LavaIceRules.Rung.PACKED_ICE, LavaIceRules.rungOf(Blocks.PACKED_ICE.defaultBlockState()));
        assertEquals(LavaIceRules.Rung.BLUE_ICE, LavaIceRules.rungOf(Blocks.BLUE_ICE.defaultBlockState()));
        assertNull(LavaIceRules.rungOf(Blocks.WATER.defaultBlockState()));
        assertNull(LavaIceRules.rungOf(Blocks.STONE.defaultBlockState()));
        assertNull(LavaIceRules.rungOf(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void case4LavaTouchesIceProducesWaterAndMagma() {
        assertEquals(Blocks.WATER.defaultBlockState(), LavaIceRules.neighborResult(LavaIceRules.Rung.ICE));
        assertEquals(Blocks.MAGMA_BLOCK.defaultBlockState(), LavaIceRules.lavaResult(LavaIceRules.Rung.ICE));
    }

    @Test
    void case5LavaTouchesPackedIceProducesIceAndObsidian() {
        assertEquals(Blocks.ICE.defaultBlockState(), LavaIceRules.neighborResult(LavaIceRules.Rung.PACKED_ICE));
        assertEquals(Blocks.OBSIDIAN.defaultBlockState(), LavaIceRules.lavaResult(LavaIceRules.Rung.PACKED_ICE));
    }

    @Test
    void case6LavaTouchesBlueIceProducesPackedIceAndObsidian() {
        assertEquals(Blocks.PACKED_ICE.defaultBlockState(), LavaIceRules.neighborResult(LavaIceRules.Rung.BLUE_ICE));
        assertEquals(Blocks.OBSIDIAN.defaultBlockState(), LavaIceRules.lavaResult(LavaIceRules.Rung.BLUE_ICE));
    }
}
