package io.github.brooswitminecraft.dynamicatmosphere;

/** Conservative MVP amounts per existing producer pass, not per block/entity tick. */
public final class SmokeProducerRules {
    public static final int FIRE = 40;
    public static final int LAVA = 4;
    public static final int LIT_FURNACE = 20;
    public static final int TORCH = 2;
    public static final int LIT_CAMPFIRE = 20;
    public static final int EXPLOSION_BURST = 80;
    public static final int EXPLOSION_BLOCK = 10;

    public enum Source { NONE, FIRE, LAVA, FURNACE, TORCH, CAMPFIRE }

    public static int ongoing(Source source, boolean lit) {
        DynamicAtmosphereServerConfig.Smoke config = DynamicAtmosphereServerConfig.snapshot().smoke();
        return ongoing(source, lit, config.fireEmission(), config.lavaEmission(), config.litFurnaceEmission(),
            config.torchEmission(), config.litCampfireEmission());
    }

    public static int ongoing(Source source, boolean lit, int fire, int lava, int furnace, int torch, int campfire) {
        return switch (source) {
            case NONE -> 0;
            case FIRE -> fire;
            case LAVA -> lava;
            case FURNACE -> lit ? furnace : 0;
            case TORCH -> lit ? torch : 0;
            case CAMPFIRE -> lit ? campfire : 0;
        };
    }

    /** Compare material presence, not fluid amount/level or fire age. Failed mutations emit nothing. */
    public static int transition(boolean succeeded, boolean previousFire, boolean nextFire,
                                 boolean previousLava, boolean nextLava, boolean fluidTransport) {
        DynamicAtmosphereServerConfig.Smoke config = DynamicAtmosphereServerConfig.snapshot().smoke();
        return transition(succeeded, previousFire, nextFire, previousLava, nextLava, fluidTransport,
            config.fireEmission(), config.lavaEmission());
    }

    public static int transition(boolean succeeded, boolean previousFire, boolean nextFire,
                                 boolean previousLava, boolean nextLava, boolean fluidTransport,
                                 int fire, int lava) {
        if (!succeeded || fluidTransport) return 0;
        return (previousFire != nextFire ? fire : 0) + (previousLava != nextLava ? lava : 0);
    }

    /** Call once from the accepted explosion callback, even with an empty affected-block list. */
    public static int explosionBurst(boolean accepted) {
        return explosionBurst(accepted, DynamicAtmosphereServerConfig.snapshot().smoke().explosionEmission());
    }

    public static int explosionBurst(boolean accepted, int amount) {
        return accepted ? amount : 0;
    }

    /** An affected-block list alone does not establish that destruction succeeded. */
    public static int explosionBlock(boolean mutationSucceeded, boolean previousAir, boolean nextAir) {
        return explosionBlock(mutationSucceeded, previousAir, nextAir,
            DynamicAtmosphereServerConfig.snapshot().smoke().explosionBlockEmission());
    }

    public static int explosionBlock(boolean mutationSucceeded, boolean previousAir, boolean nextAir, int amount) {
        return mutationSucceeded && !previousAir && nextAir ? amount : 0;
    }

    /** Runtime amount for parent callbacks that already proved a successful destruction. */
    public static int explosionBlockAmount() {
        return DynamicAtmosphereServerConfig.snapshot().smoke().explosionBlockEmission();
    }

    private SmokeProducerRules() { }
}
