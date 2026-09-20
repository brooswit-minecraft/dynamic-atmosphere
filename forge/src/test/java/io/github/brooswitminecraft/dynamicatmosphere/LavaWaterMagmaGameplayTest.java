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

class LavaWaterMagmaGameplayTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void recognizesExactlyTheThreeVanillaBlocksThisEventCanProposeForLavaAndWater() {
        assertEquals(LavaWaterMagmaRules.ProposedBlock.OBSIDIAN,
            LavaWaterMagmaGameplay.proposedBlock(Blocks.OBSIDIAN.defaultBlockState()));
        assertEquals(LavaWaterMagmaRules.ProposedBlock.COBBLESTONE,
            LavaWaterMagmaGameplay.proposedBlock(Blocks.COBBLESTONE.defaultBlockState()));
        assertEquals(LavaWaterMagmaRules.ProposedBlock.STONE,
            LavaWaterMagmaGameplay.proposedBlock(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void treatsEveryOtherProposalIncludingBasaltAndMagmaItselfAsOutOfScope() {
        assertEquals(LavaWaterMagmaRules.ProposedBlock.OTHER,
            LavaWaterMagmaGameplay.proposedBlock(Blocks.BASALT.defaultBlockState()));
        assertEquals(LavaWaterMagmaRules.ProposedBlock.OTHER,
            LavaWaterMagmaGameplay.proposedBlock(Blocks.MAGMA_BLOCK.defaultBlockState()));
        assertEquals(LavaWaterMagmaRules.ProposedBlock.OTHER,
            LavaWaterMagmaGameplay.proposedBlock(Blocks.AIR.defaultBlockState()));
    }
}
