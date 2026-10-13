package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the adjacency claim relied on by {@link LavaIceInteractions}: pinning
 * {@code FluidInteractionRegistry.canInteract}'s own neighbour set (vanilla's
 * {@code LiquidBlock.POSSIBLE_FLOW_DIRECTIONS}, opposed) directly against the
 * real constant, without needing a {@code Level}/{@code FluidInteractionRegistry}
 * runtime fixture.
 */
class LavaIceAdjacencyTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void possibleFlowDirectionsOpposedNeverProbesBelowASourceAndCoversTheOtherFive() {
        BlockPos source = BlockPos.ZERO;
        Set<BlockPos> probed = LiquidBlock.POSSIBLE_FLOW_DIRECTIONS.stream()
            .map(direction -> source.relative(direction.getOpposite()))
            .collect(Collectors.toSet());

        assertEquals(Set.of(source.above(), source.north(), source.south(), source.east(), source.west()), probed);
        assertFalse(probed.contains(source.below()),
            "FluidInteractionRegistry must never check the position below a lava source");
    }
}
