package io.github.brooswitminecraft.dynamicatmosphere;

/** Pure classification for the three vanilla lava-and-water placement results. */
public final class LavaWaterMagmaRules {
    public enum ProposedBlock { OBSIDIAN, COBBLESTONE, STONE, OTHER }

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
