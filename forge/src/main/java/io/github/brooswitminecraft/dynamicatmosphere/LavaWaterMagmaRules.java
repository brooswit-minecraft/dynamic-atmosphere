package io.github.brooswitminecraft.dynamicatmosphere;

/**
 * Pure classification for {@code BlockEvent.FluidPlaceBlockEvent}'s lava+water magma override (ATMO-35),
 * decoupled from NeoForge/Minecraft types so it is testable without a running game.
 *
 * <p>NeoForge proposes exactly three vanilla blocks through this event for lava+water contact:
 * <ul>
 *   <li>{@code FluidInteractionRegistry}'s lava+water interaction (cases 1-2: water reaches a lava source, or
 *   flowing lava) fires with the event's own position AT THE LAVA'S OWN BLOCK, proposing OBSIDIAN (source lava)
 *   or COBBLESTONE (flowing lava). The water neighbour that triggered it is left untouched by vanilla/NeoForge
 *   and must be cleared here; lava fluid is removed from the event's own position, so the existing Smoke
 *   removal path should fire.</li>
 *   <li>{@code LavaFluid.spreadTo}'s downward-into-water path (case 3: lava spreads down into water) fires with
 *   the event's own position AT THE WATER'S OWN BLOCK (the position lava is spreading into), proposing STONE.
 *   No neighbour is touched — the lava source above the water survives untouched, by construction, since this
 *   rule only ever writes to the event's own position — and no lava fluid is removed from the event's own
 *   position (it held water, not lava), so Smoke must not fire.</li>
 *   <li>Anything else NeoForge might propose through this event (e.g. blue-ice-over-soul-soil basalt) is out of
 *   this story's scope and must be left alone.</li>
 * </ul>
 */
public final class LavaWaterMagmaRules {

    /** The block NeoForge/vanilla proposed to place through the event, before this feature intervenes. */
    public enum ProposedBlock { OBSIDIAN, COBBLESTONE, STONE, OTHER }

    /**
     * @param applies             true when this event is one of the three lava+water magma cases in scope
     * @param clearsNeighborWater true when the water neighbour that triggered the interaction must be cleared
     * @param emitsLavaSmoke      true when lava fluid is being removed from the event's own position
     */
    public record Decision(boolean applies, boolean clearsNeighborWater, boolean emitsLavaSmoke) {
        private static final Decision NOT_APPLICABLE = new Decision(false, false, false);
    }

    public static Decision classify(ProposedBlock proposed) {
        return switch (proposed) {
            case OBSIDIAN, COBBLESTONE -> new Decision(true, true, true);
            case STONE -> new Decision(true, false, false);
            case OTHER -> Decision.NOT_APPLICABLE;
        };
    }

    private LavaWaterMagmaRules() { }
}
