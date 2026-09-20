package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry.InteractionInformation;

/**
 * Registers the ice-ladder -&gt; obsidian route (ATMO-34 cases 4-6) as three
 * separate {@code FluidInteractionRegistry} entries, one per rung, mirroring
 * how NeoForge itself registers the built-in lava+water and
 * lava+soul-soil+blue-ice(-&gt;basalt) entries. Rule outcomes come from
 * {@link LavaIceRules}; this class only wires them to the registry.
 *
 * <p>{@code FluidInteractionRegistry#canInteract} walks
 * {@code LiquidBlock.POSSIBLE_FLOW_DIRECTIONS} (DOWN, SOUTH, NORTH, EAST, WEST)
 * and checks {@code pos.relative(direction.getOpposite())} for each — i.e. the
 * neighbours actually probed are UP, NORTH, SOUTH, EAST, WEST. Ice directly
 * BELOW a lava source is never checked and never triggers these rules.
 *
 * <p>Known, accepted collision (ATMO-34, deliberately not fixed here):
 * NeoForge's own lava+soul-soil-below+blue-ice-adjacent -&gt; basalt entry is
 * registered first (see {@code FluidInteractionRegistry}'s static
 * initializer), so it wins over rule 6 whenever the blue ice sits directly
 * above soul soil — the standard basalt-generator layout.
 */
public final class LavaIceInteractions {
    private LavaIceInteractions() {
    }

    public static void register() {
        // Case 4: lava + ice -> water (neighbor), magma (lava's own position).
        FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(), new InteractionInformation(
            (level, currentPos, relativePos, currentState) ->
                LavaIceRules.rungOf(level.getBlockState(relativePos)) == LavaIceRules.Rung.ICE,
            (level, currentPos, relativePos, currentState) ->
                apply(level, currentPos, relativePos, LavaIceRules.Rung.ICE)));

        // Case 5: lava + packed ice -> ice (neighbor), obsidian (lava's own position).
        FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(), new InteractionInformation(
            (level, currentPos, relativePos, currentState) ->
                LavaIceRules.rungOf(level.getBlockState(relativePos)) == LavaIceRules.Rung.PACKED_ICE,
            (level, currentPos, relativePos, currentState) ->
                apply(level, currentPos, relativePos, LavaIceRules.Rung.PACKED_ICE)));

        // Case 6: lava + blue ice -> packed ice (neighbor), obsidian (lava's own position).
        FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(), new InteractionInformation(
            (level, currentPos, relativePos, currentState) ->
                LavaIceRules.rungOf(level.getBlockState(relativePos)) == LavaIceRules.Rung.BLUE_ICE,
            (level, currentPos, relativePos, currentState) ->
                apply(level, currentPos, relativePos, LavaIceRules.Rung.BLUE_ICE)));
    }

    /**
     * Degrades the neighbouring ice one rung and converts the lava's own position,
     * firing the same fluid-place event and 1501 fizz sound NeoForge's own
     * convenience constructor fires, since both rules act on the lava's position too.
     */
    private static void apply(Level level, BlockPos currentPos, BlockPos relativePos, LavaIceRules.Rung rung) {
        level.setBlockAndUpdate(relativePos, LavaIceRules.neighborResult(rung));
        BlockState lavaResult = EventHooks.fireFluidPlaceBlockEvent(
            level, currentPos, currentPos, LavaIceRules.lavaResult(rung));
        level.setBlockAndUpdate(currentPos, lavaResult);
        level.levelEvent(1501, currentPos, 0);
    }
}
