package io.github.brooswitminecraft.dynamicatmosphere;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

/** Client presentation and bounded-work tuning. Fetch a fresh snapshot to observe config reloads. */
public final class DynamicAtmosphereClientConfig {
    public static final String FILE_NAME = "dynamicatmosphere-client.toml";
    /**
     * Config-format revision for THIS file. MINECRAFT-21: on mismatch between this and the
     * stored {@code configVersion}, ConfigVersionGuard preserves the old file as
     * {@code dynamicatmosphere-client.toml.bak-<oldVersion>} and lets the file regenerate from
     * defaults. This resets configuration; it does not merge old custom settings into new
     * defaults. Deliberately NOT a {@link ModConfigSpec}-registered key — see
     * {@link ConfigVersionGuard}'s class javadoc for why: a real spec entry would be invented
     * and overwritten by NeoForge's own correct-and-write-back pass before this mod can ever
     * read the file, defeating the whole mismatch check for anything but a first-ever write.
     */
    public static final int CURRENT_VERSION = 1;
    public static final ModConfigSpec SPEC;

    private static final BooleanOption ENABLED;
    private static final IntOption SLICES_PER_BASE_CELL;
    private static final IntOption TRANSITION_TICKS;
    private static final IntOption SELECTION_WORK;
    private static final IntOption SLICES_PER_BATCH;
    private static final DoubleOption MAX_DISTANCE_MULTIPLIER;
    private static final DoubleOption FADE_START_FRACTION;

    private static final DoubleOption VAPOR_DENSITY;
    private static final DoubleOption SMOKE_DENSITY;
    private static final DoubleOption DUST_DENSITY;
    private static final DoubleOption VOID_GAS_DENSITY;
    private static final DoubleOption EXHAUST_DENSITY;
    private static final DoubleOption SLIME_DENSITY;
    private static final DoubleOption OBSIDIAN_POWDER_DENSITY;

    private static final IntOption VAPOR_COLOR;
    private static final IntOption SMOKE_COLOR;
    private static final IntOption DUST_COLOR;
    private static final IntOption VOID_GAS_COLOR;
    private static final IntOption EXHAUST_COLOR;
    private static final IntOption SLIME_COLOR;
    private static final IntOption OBSIDIAN_POWDER_COLOR;

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
        // Uniform max visible distance, shared by every material (replaces the seven
        // per-material *ReachMultiplier keys removed in ATMO-28/ATMO-39). Default 2.0
        // matches Vapor/Smoke/Void Gas/Slime's prior multiplier; Dust/Exhaust/Obsidian Powder
        // move from 0.25 to 2.0, rendering substantially farther (see PR body/CHANGELOG).
        MAX_DISTANCE_MULTIPLIER = decimal(builder, "maxDistanceMultiplier", 2, 0.25, 4);
        // Fraction of maxDistanceMultiplier's distance at which the horizontal fade
        // begins; opacity is full below this fraction, falls to zero at max distance.
        FADE_START_FRACTION = decimal(builder, "fadeStartFraction", 0.75, 0, 1);
        builder.pop();

        builder.push("materials");
        // MINECRAFT-21: vaporOpticalDensity 1 -> 1.2; smokeOpticalDensity 2 -> 4;
        // obsidianPowderOpticalDensity (renamed by MINECRAFT-16/C2 from enderGasOpticalDensity) 40 -> 0.3.
        // Every value here is paired with the matching AtmosphereRenderMaterial enum literal default
        // (client package) — the two must change together or the renderer's config read stays stale.
        VAPOR_DENSITY = decimal(builder, "vaporOpticalDensity", 1.2, 0, 1000);
        SMOKE_DENSITY = decimal(builder, "smokeOpticalDensity", 4, 0, 1000);
        DUST_DENSITY = decimal(builder, "dustOpticalDensity", 1, 0, 1000);
        VOID_GAS_DENSITY = decimal(builder, "voidGasOpticalDensity", 4, 0, 1000);
        EXHAUST_DENSITY = decimal(builder, "exhaustOpticalDensity", 1, 0, 1000);
        SLIME_DENSITY = decimal(builder, "slimeOpticalDensity", 4, 0, 1000);
        OBSIDIAN_POWDER_DENSITY = decimal(builder, "obsidianPowderOpticalDensity", 0.3, 0, 1000);

        // MINECRAFT-21 (transferred from MINECRAFT-20): all seven materials' colours are now
        // client config values, not AtmosphereRenderMaterial enum literals (a full mod release
        // used to be required to retune a colour). Colours are packed 0xRRGGBB, range 0-0xFFFFFF.
        // Vapor's own colour key is defined for catalog/config parity but is NOT read by the
        // renderer: AtmosphereRenderMaterial.channel() always uses the live fog colour for Vapor.
        builder.comment("Packed 0xRRGGBB. Defined for config/catalog parity; the renderer always uses the",
            "live fog colour for Vapor, never this value.");
        VAPOR_COLOR = integer(builder, "vaporColor", 0x000000, 0x000000, 0xFFFFFF);
        builder.comment("Packed 0xRRGGBB. Deliberately NOT pure black (0x000000): off-black keeps Smoke",
            "visually distinct from Void Gas, the other common heavy material at this density.");
        SMOKE_COLOR = integer(builder, "smokeColor", 0x0D0A08, 0x000000, 0xFFFFFF);
        builder.comment("Packed 0xRRGGBB.");
        DUST_COLOR = integer(builder, "dustColor", 0x8B4513, 0x000000, 0xFFFFFF);
        builder.comment("Packed 0xRRGGBB.");
        VOID_GAS_COLOR = integer(builder, "voidGasColor", 0x000000, 0x000000, 0xFFFFFF);
        builder.comment("Packed 0xRRGGBB.");
        EXHAUST_COLOR = integer(builder, "exhaustColor", 0xFFFF00, 0x000000, 0xFFFFFF);
        builder.comment("Packed 0xRRGGBB.");
        SLIME_COLOR = integer(builder, "slimeColor", 0x00FF00, 0x000000, 0xFFFFFF);
        builder.comment("Packed 0xRRGGBB. PROPOSED value (ATMO-22 comment 23234), pending in-game",
            "visual confirmation against Smoke/Void Gas at representative densities.");
        OBSIDIAN_POWDER_COLOR = integer(builder, "obsidianPowderColor", 0x1A1025, 0x000000, 0xFFFFFF);
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
            SLICES_PER_BATCH.get(), MAX_DISTANCE_MULTIPLIER.get(), FADE_START_FRACTION.get(),
            new Material(VAPOR_DENSITY.get(), VAPOR_COLOR.get()),
            new Material(SMOKE_DENSITY.get(), SMOKE_COLOR.get()),
            new Material(DUST_DENSITY.get(), DUST_COLOR.get()),
            new Material(VOID_GAS_DENSITY.get(), VOID_GAS_COLOR.get()),
            new Material(EXHAUST_DENSITY.get(), EXHAUST_COLOR.get()),
            new Material(SLIME_DENSITY.get(), SLIME_COLOR.get()),
            new Material(OBSIDIAN_POWDER_DENSITY.get(), OBSIDIAN_POWDER_COLOR.get()),
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
                           int selectionWorkPerTick, int slicesPerBatch,
                           double maxDistanceMultiplier, double fadeStartFraction,
                           Material vapor, Material smoke,
                           Material dust, Material voidGas, Material exhaust, Material slime,
                           Material obsidianPowder, Allocation allocation) { }
    public record Material(double opticalDensity, int color) { }
    public record Allocation(int cellBudget) { }

    private DynamicAtmosphereClientConfig() { }
}
