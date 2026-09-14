package io.github.brooswitminecraft.dynamicatmosphere;

/** Conservative MVP amounts per existing producer pass, not per block/entity tick. */
public final class SmokeProducerRules {
    public static final int FIRE = 40;
    public static final int LAVA = 40;
    public static final int LIT_FURNACE = 20;
    public static final int TORCH = 2;
    public static final int LIT_CAMPFIRE = 20;
    public static final int EXPLOSION_BURST = 80;
    public static final int EXPLOSION_BLOCK = 10;

    public enum Source { NONE, FIRE, LAVA, FURNACE, TORCH, CAMPFIRE }

    public static int ongoing(Source source, boolean lit) {
        return switch (source) {
            case NONE -> 0;
            case FIRE -> FIRE;
            case LAVA -> LAVA;
            case FURNACE -> lit ? LIT_FURNACE : 0;
            case TORCH -> lit ? TORCH : 0;
            case CAMPFIRE -> lit ? LIT_CAMPFIRE : 0;
        };
    }

    /** Compare material presence, not fluid amount/level or fire age. Failed mutations emit nothing. */
    public static int transition(boolean succeeded, boolean previousFire, boolean nextFire,
                                 boolean previousLava, boolean nextLava, boolean fluidTransport) {
        if (!succeeded || fluidTransport) return 0;
        return (previousFire != nextFire ? FIRE : 0) + (previousLava != nextLava ? LAVA : 0);
    }

    /** Call once from the accepted explosion callback, even with an empty affected-block list. */
    public static int explosionBurst(boolean accepted) {
        return accepted ? EXPLOSION_BURST : 0;
    }

    /** An affected-block list alone does not establish that destruction succeeded. */
    public static int explosionBlock(boolean mutationSucceeded, boolean previousAir, boolean nextAir) {
        return mutationSucceeded && !previousAir && nextAir ? EXPLOSION_BLOCK : 0;
    }

    private SmokeProducerRules() { }
}
