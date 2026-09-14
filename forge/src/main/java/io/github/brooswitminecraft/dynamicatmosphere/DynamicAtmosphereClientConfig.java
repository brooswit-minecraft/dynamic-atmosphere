package io.github.brooswitminecraft.dynamicatmosphere;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

/** Client presentation and bounded-work tuning. Fetch a fresh snapshot to observe config reloads. */
public final class DynamicAtmosphereClientConfig {
    public static final String FILE_NAME = "dynamicatmosphere-client.toml";
    public static final ModConfigSpec SPEC;

    private static final BooleanOption ENABLED;
    private static final IntOption SLICES_PER_BASE_CELL;
    private static final IntOption TRANSITION_TICKS;
    private static final IntOption SELECTION_WORK;
    private static final IntOption SLICES_PER_BATCH;

    private static final DoubleOption VAPOR_REACH;
    private static final DoubleOption VAPOR_DENSITY;
    private static final DoubleOption SMOKE_REACH;
    private static final DoubleOption SMOKE_DENSITY;
    private static final DoubleOption DUST_REACH;
    private static final DoubleOption DUST_DENSITY;
    private static final DoubleOption VIOLENCE_REACH;
    private static final DoubleOption VIOLENCE_DENSITY;
    private static final DoubleOption EXHAUST_REACH;
    private static final DoubleOption EXHAUST_DENSITY;
    private static final DoubleOption SLIME_REACH;
    private static final DoubleOption SLIME_DENSITY;
    private static final DoubleOption ENDER_GAS_REACH;
    private static final DoubleOption ENDER_GAS_DENSITY;

    private static final IntOption CELL_BUDGET;
    private static volatile Snapshot cached;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rendering");
        ENABLED = bool(builder, "enabled", true);
        SLICES_PER_BASE_CELL = integer(builder, "slicesPerBaseCell", 4, 1, 64);
        TRANSITION_TICKS = integer(builder, "transitionTicks", 10, 0, 1200);
        SELECTION_WORK = integer(builder, "selectionWorkPerTick", 4096, 1, 1_000_000);
        SLICES_PER_BATCH = integer(builder, "slicesPerBatch", 4096, 1, 1_000_000);
        builder.pop();

        builder.push("materials");
        VAPOR_REACH = decimal(builder, "vaporReachMultiplier", 2, 0.25, 4);
        VAPOR_DENSITY = decimal(builder, "vaporOpticalDensity", 1, 0, 1000);
        SMOKE_REACH = decimal(builder, "smokeReachMultiplier", 2, 0.25, 4);
        SMOKE_DENSITY = decimal(builder, "smokeOpticalDensity", 4, 0, 1000);
        DUST_REACH = decimal(builder, "dustReachMultiplier", 0.25, 0.25, 4);
        DUST_DENSITY = decimal(builder, "dustOpticalDensity", 1, 0, 1000);
        VIOLENCE_REACH = decimal(builder, "violenceReachMultiplier", 2, 0.25, 4);
        VIOLENCE_DENSITY = decimal(builder, "violenceOpticalDensity", 4, 0, 1000);
        EXHAUST_REACH = decimal(builder, "exhaustReachMultiplier", 0.25, 0.25, 4);
        EXHAUST_DENSITY = decimal(builder, "exhaustOpticalDensity", 1, 0, 1000);
        SLIME_REACH = decimal(builder, "slimeReachMultiplier", 2, 0.25, 4);
        SLIME_DENSITY = decimal(builder, "slimeOpticalDensity", 4, 0, 1000);
        ENDER_GAS_REACH = decimal(builder, "enderGasReachMultiplier", 0.25, 0.25, 4);
        ENDER_GAS_DENSITY = decimal(builder, "enderGasOpticalDensity", 40, 0, 1000);
        builder.pop();

        builder.push("allocation");
        CELL_BUDGET = restartInteger(builder, "cellBudget", 200_000, 1, 2_000_000);
        builder.pop();
        SPEC = builder.build();
        cached = readSnapshot();
    }

    public static Snapshot snapshot() {
        return cached;
    }

    public static void onLoading(ModConfigEvent.Loading event) { refresh(event); }
    public static void onReloading(ModConfigEvent.Reloading event) { refresh(event); }
    public static void onUnloading(ModConfigEvent.Unloading event) { refresh(event); }

    private static void refresh(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) cached = readSnapshot();
    }

    private static Snapshot readSnapshot() {
        return new Snapshot(
            ENABLED.get(), SLICES_PER_BASE_CELL.get(), TRANSITION_TICKS.get(), SELECTION_WORK.get(),
            SLICES_PER_BATCH.get(),
            new Material(VAPOR_REACH.get(), VAPOR_DENSITY.get()),
            new Material(SMOKE_REACH.get(), SMOKE_DENSITY.get()),
            new Material(DUST_REACH.get(), DUST_DENSITY.get()),
            new Material(VIOLENCE_REACH.get(), VIOLENCE_DENSITY.get()),
            new Material(EXHAUST_REACH.get(), EXHAUST_DENSITY.get()),
            new Material(SLIME_REACH.get(), SLIME_DENSITY.get()),
            new Material(ENDER_GAS_REACH.get(), ENDER_GAS_DENSITY.get()),
            new Allocation(CELL_BUDGET.get())
        );
    }

    static int validInt(Integer value, int fallback, int min, int max) {
        return value != null && value >= min && value <= max ? value : fallback;
    }

    static double validDouble(Double value, double fallback, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max ? value : fallback;
    }

    private static BooleanOption bool(ModConfigSpec.Builder builder, String name, boolean fallback) {
        return new BooleanOption(builder.define(name, fallback), fallback);
    }

    private static IntOption integer(ModConfigSpec.Builder builder, String name, int fallback, int min, int max) {
        return new IntOption(builder.defineInRange(name, fallback, min, max), fallback, min, max);
    }

    private static IntOption restartInteger(
        ModConfigSpec.Builder builder, String name, int fallback, int min, int max
    ) {
        builder.gameRestart();
        return integer(builder, name, fallback, min, max);
    }

    private static DoubleOption decimal(
        ModConfigSpec.Builder builder, String name, double fallback, double min, double max
    ) {
        return new DoubleOption(builder.defineInRange(name, fallback, min, max), fallback, min, max);
    }

    private record BooleanOption(ModConfigSpec.BooleanValue value, boolean fallback) {
        boolean get() {
            try {
                Boolean configured = value.get();
                return configured == null ? fallback : configured;
            } catch (IllegalStateException ignored) {
                return fallback;
            }
        }
    }

    private record IntOption(ModConfigSpec.IntValue value, int fallback, int min, int max) {
        int get() {
            try {
                return validInt(value.get(), fallback, min, max);
            } catch (IllegalStateException ignored) {
                return fallback;
            }
        }
    }

    private record DoubleOption(ModConfigSpec.DoubleValue value, double fallback, double min, double max) {
        double get() {
            try {
                return validDouble(value.get(), fallback, min, max);
            } catch (IllegalStateException ignored) {
                return fallback;
            }
        }
    }

    public record Snapshot(boolean enabled, int slicesPerBaseCell, int transitionTicks,
                           int selectionWorkPerTick, int slicesPerBatch, Material vapor, Material smoke,
                           Material dust, Material violence, Material exhaust, Material slime,
                           Material enderGas, Allocation allocation) { }
    public record Material(double reachMultiplier, double opticalDensity) { }
    public record Allocation(int cellBudget) { }

    private DynamicAtmosphereClientConfig() { }
}
