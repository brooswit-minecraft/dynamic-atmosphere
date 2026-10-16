package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalTransferSinkTest {
    @Test
    void netherToOverworldScalesHorizontallyByEightAndPreservesY() {
        assertEquals(new BlockPos(8, 64, -16), PortalTransferSink.netherToOverworld(new BlockPos(1, 64, -2)));
        assertEquals(new BlockPos(0, 0, 0), PortalTransferSink.netherToOverworld(new BlockPos(0, 0, 0)));
    }

    @Test
    void overworldToNetherScalesHorizontallyByOneEighthAndPreservesY() {
        assertEquals(new BlockPos(1, 64, -2), PortalTransferSink.overworldToNether(new BlockPos(8, 64, -16)));
        // Integer division floors toward zero per Java/BlockPos semantics, matching vanilla portal linking.
        assertEquals(new BlockPos(1, 0, 0), PortalTransferSink.overworldToNether(new BlockPos(15, 0, 0)));
    }

    @Test
    void theTwoScalesAreInverseForExactMultiplesOfEight() {
        var original = new BlockPos(24, 70, -40);
        assertEquals(original, PortalTransferSink.netherToOverworld(PortalTransferSink.overworldToNether(original)));
    }
}
