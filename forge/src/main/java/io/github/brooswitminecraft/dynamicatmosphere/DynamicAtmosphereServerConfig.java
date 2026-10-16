package io.github.brooswitminecraft.dynamicatmosphere;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

/** Reloadable server gameplay and work-budget tuning. Structural grid formats are intentionally excluded. */
public final class DynamicAtmosphereServerConfig {
    public static final String FILE_NAME = "dynamicatmosphere-server.toml";
    public static final ModConfigSpec SPEC;

    private static final IntOption SYNC_INTERVAL;
    private static final IntOption FULL_SNAPSHOT_INTERVAL;
    private static final IntOption SIMULATION_INTERVAL;
    private static final IntOption PRODUCER_INTERVAL;
    private static final IntOption CHUNK_IMPORTS;
    private static final IntOption PRODUCER_CHUNKS;
    private static final IntOption SMOKE_PRODUCER_CHUNKS;

    private static final DoubleOption VAPOR_PRODUCER_CHANCE;
    private static final IntOption VERTICAL_MOVEMENT_DEAD_BAND;
    private static final DoubleOption VERTICAL_MOVEMENT_W;
    private static final DoubleOption VERTICAL_MOVEMENT_DRIFT_FRACTION;
    private static final DoubleOption SIMULATION_SKIP_CHANCE;
    private static final IntOption RAIN_CLOUD_HEIGHT;
    private static final IntOption NETHER_CLOUDLINE_HEIGHT;
    private static final IntOption END_CLOUDLINE_BLOCKS_BELOW_TOP;
    private static final IntOption HIGH_TERRAIN_ABOVE_SEA;
    private static final IntOption HIGH_TERRAIN;
    private static final IntOption RAIN_CLOUD;
    private static final IntOption SNOW_ICE;
    private static final IntOption DARK_GROUND;
    private static final IntOption SNOW_LAYER;
    private static final IntOption ICE_REMOVAL;
    private static final DoubleOption CONDENSATION_CHANCE;

    private static final IntOption FIRE;
    private static final IntOption LAVA;
    private static final IntOption FURNACE;
    private static final IntOption TORCH;
    private static final IntOption CAMPFIRE;
    private static final IntOption EXPLOSION;
    private static final IntOption EXPLOSION_BLOCK;
    private static final DoubleOption LEAF_CHANCE;
    private static final IntOption LEAF_COST;
    private static final DoubleOption FARMLAND_CHANCE;
    private static final IntOption FARMLAND_COST;
    private static final DoubleOption VILLAGER_CHANCE;
    private static final IntOption VILLAGER_COST;
    private static final DoubleOption SMOKE_DISSIPATION_CHANCE;
    private static final IntOption SMOKE_DISSIPATION_AMOUNT;
    private static final IntOption SMOKE_PREFERRED_HEIGHT;

    private static final IntOption DUST_WALK;
    private static final IntOption DUST_RUN;
    private static final IntOption DUST_JUMP;
    private static final IntOption DUST_LAND;
    private static final IntOption DUST_FALL_BASE;
    private static final IntOption DUST_FALL_PER_POINT;
    private static final IntOption DUST_FALL_MAX;
    private static final IntOption DUST_BREAK;
    private static final IntOption DUST_PLACE;
    private static final IntOption DUST_WALK_INTERVAL;
    private static final IntOption DUST_RUN_INTERVAL;
    private static final IntOption FALLING_BLOCK;
    private static final DoubleOption GRAVEL_CHANCE;
    private static final DoubleOption DUST_DISSIPATION_CHANCE;
    private static final IntOption DUST_DISSIPATION_AMOUNT;

    private static final IntOption VOID_GAS_HOSTILE_DEATH;
    private static final IntOption VOID_GAS_NETHERRACK;
    private static final IntOption VOID_GAS_BOTTOM;
    private static final IntOption VOID_GAS_BOTTOM_DENOMINATOR;
    private static final IntOption VOID_GAS_BREAD;

    private static final IntOption EXHAUST_PASSIVE;
    private static final IntOption EXHAUST_LIVING_DENOMINATOR;
    private static final IntOption EXHAUST_CREEPER_DENOMINATOR;
    private static final IntOption EXHAUST_DAMAGE_PER_POINT;
    private static final IntOption EXHAUST_DAMAGE_MAX;
    private static final DoubleOption EXHAUST_MIN_FULLNESS;
    private static final DoubleOption EXHAUST_MAX_FULLNESS;
    private static final DoubleOption EXHAUST_MIN_DAMAGE;
    private static final DoubleOption EXHAUST_MAX_DAMAGE;
    private static final DoubleOption EXHAUST_MIN_COST;
    private static final DoubleOption EXHAUST_MAX_COST;
    private static final IntOption EXHAUST_CREEPER_SPAWN_CONSUMPTION;

    private static final IntOption SLIME_UNDERGROUND;
    private static final IntOption SLIME_UNDERGROUND_DENOMINATOR;
    private static final IntOption SLIME_DISSIPATION_SPAWN_DENOMINATOR;
    private static final IntOption SLIME_DISSIPATION_SPAWN_ATTEMPTS;
    private static final DoubleOption SLIME_MEDIUM_DENSITY_THRESHOLD;
    private static final DoubleOption SLIME_BIG_DENSITY_THRESHOLD;
    private static final IntOption SLIME_PREFERRED_HEIGHT;

    private static final IntOption GLOW_GAS_GLOWSTONE;

    private static final DoubleOption NETHER_FOG_MAX_CONDENSATION_CHANCE;

    private static final DoubleOption HEAVY_GAS_DISSIPATION_FACTOR;

    private static final IntOption ENDER_MOB;
    private static final IntOption ENDER_PORTAL;
    private static final IntOption ENDER_PORTAL_BLOCK;
    private static final IntOption OBSIDIAN_PASSIVE;
    private static final IntOption OBSIDIAN_BLOCK;
    private static final IntOption CRYING_OBSIDIAN;
    private static final IntOption ENDER_PEARL_USE;
    private static final IntOption ENDER_PEARL_IMPACT;
    private static final IntOption ENDER_MOB_INTERVAL;
    private static final IntOption ENDER_PORTAL_INTERVAL;
    private static final IntOption ENDER_END;
    private static final DoubleOption ENDER_END_GATE_CHANCE;
    private static final DoubleOption ENDER_END_SAND_CONDENSATION_CHANCE;

    private static final IntOption END_SAND_RISE_INTERVAL;

    private static final DoubleOption PLANT_CHANCE;
    private static final IntOption PLANT_COST;

    private static final DoubleOption CREATE_FAN_RPM_COEFFICIENT;
    private static final IntOption CREATE_FAN_INTERVAL;
    private static final IntOption CREATE_FAN_CHUNK_BUDGET;

    private static final DoubleOption MAGMA_BREAK_LAVA_CHANCE;
    private static volatile Snapshot cached;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("runtime");
        SYNC_INTERVAL = integer(builder, "syncIntervalTicks", 20, 1, 1200);
        FULL_SNAPSHOT_INTERVAL = integer(builder, "fullSnapshotIntervalTicks", 200, 1, 72000);
        SIMULATION_INTERVAL = integer(builder, "simulationIntervalTicks", 200, 1, 72000);
        PRODUCER_INTERVAL = integer(builder, "producerIntervalTicks", 300, 1, 72000);
        SIMULATION_SKIP_CHANCE = decimal(builder, "simulationSkipChance", 0.75, 0, 1);
        CHUNK_IMPORTS = integer(builder, "maxChunkImportsPerTick", 8, 1, 1024);
        PRODUCER_CHUNKS = integer(builder, "maxProducerChunksPerTick", 32, 1, 4096);
        SMOKE_PRODUCER_CHUNKS = integer(builder, "maxSmokeProducerChunksPerTick", 4, 1, 4096);
        builder.pop();

        builder.push("verticalMovement");
        // MINECRAFT-1 (B1): a thin cell drifts toward its material's preferred resting height
        // ("home"); a dense cell sinks. See PreferredHeightDrift for the exact formula.
        builder.comment("Blocks of slack around home where neither direction dominates (p_up=0.5).");
        VERTICAL_MOVEMENT_DEAD_BAND = integer(builder, "deadBand", 2, 0, 64);
        builder.comment("How strongly fill/home-direction bias the up/down split; 0 disables the bias",
            "entirely (always 50/50), 0.5 is the maximum before p_up could be pushed outside [0,1].");
        VERTICAL_MOVEMENT_W = decimal(builder, "w", 0.40, 0, 0.5);
        builder.comment("Fraction of a cell's amount moved by this drift per processed simulation turn.");
        VERTICAL_MOVEMENT_DRIFT_FRACTION = decimal(builder, "driftFraction", 0.10, 0, 1);
        builder.pop();

        builder.push("vapor");
        VAPOR_PRODUCER_CHANCE = decimal(builder, "producerChance", 0.10, 0, 1);
        // MINECRAFT-1: this is also Vapor's own preferred resting height (home) in the Overworld.
        RAIN_CLOUD_HEIGHT = integer(builder, "rainCloudHeight", 256, -2048, 2048);
        builder.comment("Vapor's preferred resting height (home) in the Nether, where rainCloudHeight",
            "does not apply.");
        NETHER_CLOUDLINE_HEIGHT = integer(builder, "netherCloudlineHeight", 120, -2048, 2048);
        builder.comment("Vapor's preferred resting height (home) in the End, expressed as this many",
            "blocks below the dimension's build height ceiling.");
        END_CLOUDLINE_BLOCKS_BELOW_TOP = integer(builder, "endCloudlineBlocksBelowTop", 16, 0, 2048);
        HIGH_TERRAIN_ABOVE_SEA = integer(builder, "highTerrainBlocksAboveSeaLevel", 24, 0, 2048);
        HIGH_TERRAIN = integer(builder, "highTerrainEmission", 40, 0, 1_000_000);
        RAIN_CLOUD = integer(builder, "rainCloudEmission", 320, 0, 1_000_000);
        SNOW_ICE = integer(builder, "snowIceEmission", 40, 0, 1_000_000);
        DARK_GROUND = integer(builder, "maxDarkGroundEmission", 40, 0, 1_000_000);
        SNOW_LAYER = integer(builder, "snowRemovalPerLayer", 5, 0, 1_000_000);
        ICE_REMOVAL = integer(builder, "iceRemovalEmission", 40, 0, 1_000_000);
        CONDENSATION_CHANCE = decimal(builder, "maxCondensationChance", 0.10, 0, 1);
        builder.pop();

        builder.push("smoke");
        FIRE = integer(builder, "fireEmission", 40, 0, 1_000_000);
        LAVA = integer(builder, "lavaEmission", 4, 0, 1_000_000);
        FURNACE = integer(builder, "litFurnaceEmission", 20, 0, 1_000_000);
        TORCH = integer(builder, "torchEmission", 2, 0, 1_000_000);
        CAMPFIRE = integer(builder, "litCampfireEmission", 20, 0, 1_000_000);
        EXPLOSION = integer(builder, "explosionEmission", 80, 0, 1_000_000);
        EXPLOSION_BLOCK = integer(builder, "explosionBlockEmission", 10, 0, 1_000_000);
        LEAF_CHANCE = decimal(builder, "leafRemovalChance", 1.0, 0, 1);
        LEAF_COST = integer(builder, "leafRemovalCost", 40, 0, 1_000_000);
        FARMLAND_CHANCE = decimal(builder, "farmlandConversionChance", 10.0 / 128, 0, 1);
        FARMLAND_COST = integer(builder, "farmlandConversionCost", 40, 0, 1_000_000);
        VILLAGER_CHANCE = decimal(builder, "villagerConversionChance", 10.0 / 256, 0, 1);
        VILLAGER_COST = integer(builder, "villagerConversionCost", 40, 0, 1_000_000);
        SMOKE_DISSIPATION_CHANCE = decimal(builder, "dissipationChance", 10.0 / 64, 0, 1);
        SMOKE_DISSIPATION_AMOUNT = integer(builder, "dissipationAmount", 40, 0, 1_000_000);
        builder.comment("MINECRAFT-1 (B1): Smoke's preferred resting height (home), absolute and the",
            "same in every dimension. Corrected by Brooswit from an earlier 92 to 144.");
        SMOKE_PREFERRED_HEIGHT = integer(builder, "preferredHeight", 144, -2048, 2048);
        builder.pop();

        builder.push("dust");
        DUST_WALK = integer(builder, "walkEmission", 1, 0, 1_000_000);
        DUST_RUN = integer(builder, "runEmission", 3, 0, 1_000_000);
        DUST_JUMP = integer(builder, "jumpEmission", 8, 0, 1_000_000);
        DUST_LAND = integer(builder, "landEmission", 6, 0, 1_000_000);
        DUST_FALL_BASE = integer(builder, "fallDamageBaseEmission", 16, 0, 1_000_000);
        DUST_FALL_PER_POINT = integer(builder, "fallDamageEmissionPerPoint", 2, 0, 1_000_000);
        DUST_FALL_MAX = integer(builder, "maxFallDamageEmission", 64, 0, 1_000_000);
        DUST_BREAK = integer(builder, "blockBreakEmission", 16, 0, 1_000_000);
        DUST_PLACE = integer(builder, "blockPlaceEmission", 12, 0, 1_000_000);
        DUST_WALK_INTERVAL = integer(builder, "walkIntervalTicks", 10, 1, 72000);
        DUST_RUN_INTERVAL = integer(builder, "runIntervalTicks", 5, 1, 72000);
        FALLING_BLOCK = integer(builder, "fallingBlockEmission", 24, 0, 1_000_000);
        GRAVEL_CHANCE = decimal(builder, "gravelChance", 1.0 / 16, 0, 1);
        DUST_DISSIPATION_CHANCE = decimal(builder, "dissipationChance", 1.0 / 64, 0, 1);
        DUST_DISSIPATION_AMOUNT = integer(builder, "dissipationAmount", 40, 0, 1_000_000);
        // MINECRAFT-1 (B1): Dust's home is the dimension's own bottom (its minimum build height).
        // That is structural, not a tunable number, so there is no preferredHeight key here.
        builder.pop();

        builder.push("voidGas");
        VOID_GAS_HOSTILE_DEATH = integer(builder, "hostileDeathEmission", 320, 0, 1_000_000);
        VOID_GAS_NETHERRACK = integer(builder, "netherrackEmission", 16, 0, 1_000_000);
        VOID_GAS_BOTTOM = integer(builder, "bottomEmission", 64, 0, 1_000_000);
        VOID_GAS_BOTTOM_DENOMINATOR = integer(builder, "bottomChanceDenominator", 1, 1, 1_000_000);
        VOID_GAS_BREAD = integer(builder, "breedingBread", 3, 0, 64);
        // MINECRAFT-1 (B1): Void Gas's home is the dimension's own bottom; see the note under dust.
        builder.pop();

        builder.push("exhaust");
        EXHAUST_PASSIVE = integer(builder, "passiveEmission", 1, 0, 1_000_000);
        EXHAUST_LIVING_DENOMINATOR = integer(builder, "livingChanceDenominator", 128, 1, 1_000_000);
        EXHAUST_CREEPER_DENOMINATOR = integer(builder, "creeperChanceDenominator", 16, 1, 1_000_000);
        EXHAUST_DAMAGE_PER_POINT = integer(builder, "damageEmissionPerPoint", 4, 0, 1_000_000);
        EXHAUST_DAMAGE_MAX = integer(builder, "maxDamageEmission", 64, 0, 1_000_000);
        EXHAUST_MIN_FULLNESS = decimal(builder, "suffocationMinFullness", 0.50, 0, 1000);
        EXHAUST_MAX_FULLNESS = decimal(builder, "suffocationMaxFullness", 1.00, 0, 1000);
        EXHAUST_MIN_DAMAGE = decimal(builder, "suffocationMinDamage", 1, 0, 2048);
        EXHAUST_MAX_DAMAGE = decimal(builder, "suffocationMaxDamage", 4, 0, 2048);
        EXHAUST_MIN_COST = decimal(builder, "suffocationMinCostFraction", 0.25, 0, 1);
        EXHAUST_MAX_COST = decimal(builder, "suffocationMaxCostFraction", 0.50, 0, 1);
        // MINECRAFT-1 (B1): Exhaust's home is the dimension's own bottom; see the note under dust.
        builder.comment("Exhaust consumed from the local cell when the night-plus-local-Exhaust surface",
            "Creeper spawn eligibility gate (MINECRAFT-2) allows a spawn through. Only charged for a",
            "spawn this gate itself made eligible; below-ground spawning never reaches this gate.");
        EXHAUST_CREEPER_SPAWN_CONSUMPTION = integer(builder, "creeperSpawnConsumption", 50, 0, 1_000_000);
        builder.pop();

        builder.push("slime");
        // No worldRestart/gameRestart marker is applied in this section: NeoForge's default is RestartType.NONE.
        SLIME_UNDERGROUND = integer(builder, "undergroundEmission", 64, 0, 1_000_000);
        SLIME_UNDERGROUND_DENOMINATOR = integer(builder, "undergroundChanceDenominator", 1, 1, 1_000_000);
        builder.comment("Chance, as 1/denominator, that a Slime spawns when a cell actually loses material to",
            "heavyGas dissipation (MINECRAFT-18; replaces the removed consumption-driven spawn).");
        SLIME_DISSIPATION_SPAWN_DENOMINATOR = integer(builder, "dissipationSpawnChanceDenominator", 32, 1, 1_000_000);
        builder.comment("Loaded-only spawn-position attempts tried per dissipation-triggered spawn roll.");
        SLIME_DISSIPATION_SPAWN_ATTEMPTS = integer(builder, "dissipationSpawnAttempts", 8, 1, 1024);
        builder.comment("Local amount/capacity density, at the point of dissipation, at or above which the",
            "spawned slime is medium (size 2) instead of small (size 1).");
        SLIME_MEDIUM_DENSITY_THRESHOLD = decimal(builder, "mediumDensityThreshold", 0.25, 0, 1);
        builder.comment("Local amount/capacity density, at the point of dissipation, at or above which the",
            "spawned slime is big (size 4) instead of medium. Must be configured at or above",
            "mediumDensityThreshold to behave as intended; this is not cross-validated.");
        SLIME_BIG_DENSITY_THRESHOLD = decimal(builder, "bigDensityThreshold", 0.75, 0, 1);
        builder.comment("MINECRAFT-1 (B1): Slime's preferred resting height (home), absolute and the",
            "same in every dimension, clamped to that dimension's build range.");
        SLIME_PREFERRED_HEIGHT = integer(builder, "preferredHeight", 64, -2048, 2048);
        builder.pop();

        builder.push("glowGas");
        // No worldRestart/gameRestart marker is applied in this section: NeoForge's default is RestartType.NONE.
        builder.comment("Emission per glowstone block found by the bounded, palette-gated section scan.");
        GLOW_GAS_GLOWSTONE = integer(builder, "glowstoneEmission", 100, 0, 1_000_000);
        // MINECRAFT-1 (B2): Glow Gas's home is a literal Y=0, not configurable.
        builder.pop();

        builder.push("netherFog");
        // No worldRestart/gameRestart marker is applied in this section: NeoForge's default is RestartType.NONE.
        // MINECRAFT-1 (B3): per-harvest/condensation amount is NOT a config key here -- it is
        // always exactly one full block's worth of capacity (AtmosphereMaterial.capacityForAirBlocks(1)),
        // shared by both directions of the lava<->fog cycle so the cycle neither drains nor floods lava.
        builder.comment("Maximum chance per processed turn that a high-fullness Nether Fog cell places a",
            "lava source back, mirroring Vapor's own maxCondensationChance.");
        NETHER_FOG_MAX_CONDENSATION_CHANCE = decimal(builder, "maxCondensationChance", 0.10, 0, 1);
        // MINECRAFT-1 (B3): Nether Fog's home is the dimension's own bottom; see the note under dust.
        builder.pop();

        builder.push("heavyGas");
        // Shared by Void Gas, Obsidian Powder and Slime: their dissipation chance is Smoke's own
        // dissipationChance() divided by this factor, keeping Smoke's dissipationAmount() as maxLoss.
        // 1 means "dissipate exactly as fast as Smoke"; a factor must stay finite and greater than zero.
        HEAVY_GAS_DISSIPATION_FACTOR = decimal(builder, "dissipationFactor", 12.0, 0.001, 1_000.0);
        builder.pop();

        builder.push("obsidianPowder");
        // No worldRestart/gameRestart marker is applied in this section: NeoForge's default is RestartType.NONE.
        builder.comment("Emission from Endermen, endermites, dragons, witches, and shulkers.");
        ENDER_MOB = integer(builder, "mobEmission", 1, 0, 1_000_000);
        builder.comment("Emission for entities occupying a Nether portal.");
        ENDER_PORTAL = integer(builder, "portalOccupantEmission", 2, 0, 1_000_000);
        builder.comment("Emission split across the two faces of each Nether portal block.");
        ENDER_PORTAL_BLOCK = integer(builder, "portalBlockEmission", 100, 0, 1_000_000);
        builder.comment("Emission from the existing passive soul and Ender block sources.");
        OBSIDIAN_PASSIVE = integer(builder, "passiveBlockEmission", 1, 0, 1_000_000);
        builder.comment("Emission from plain obsidian blocks sampled by the passive producer.");
        OBSIDIAN_BLOCK = integer(builder, "obsidianBlockEmission", 1, 0, 1_000_000);
        builder.comment("Emission from crying obsidian blocks; deliberately much stronger than plain obsidian.");
        CRYING_OBSIDIAN = integer(builder, "cryingObsidianEmission", 1000, 0, 1_000_000);
        builder.comment("Emission when an Ender pearl is spawned.");
        ENDER_PEARL_USE = integer(builder, "pearlUseEmission", 24, 0, 1_000_000);
        builder.comment("Emission at the first impact of an Ender pearl.");
        ENDER_PEARL_IMPACT = integer(builder, "pearlImpactEmission", 48, 0, 1_000_000);
        builder.comment("Ticks between mob emission checks.");
        ENDER_MOB_INTERVAL = integer(builder, "mobIntervalTicks", 40, 1, 72000);
        builder.comment("Ticks between portal occupancy emission checks.");
        ENDER_PORTAL_INTERVAL = integer(builder, "portalIntervalTicks", 20, 1, 72000);
        // MINECRAFT-1 (B1): Obsidian Powder's (Ender Gas's) home is the dimension's own bottom;
        // see the note under dust.
        builder.comment("MINECRAFT-1 (B4): End-only emission near the dimension's build-height",
            "ceiling, per loaded End chunk per producer pass that clears endGateChance.");
        ENDER_END = integer(builder, "endEmission", 200, 0, 1_000_000);
        builder.comment("MINECRAFT-1 (B4): chance, per loaded End chunk per producer pass, that the",
            "End-only emission above actually fires (mirrors Vapor's own producerChance).");
        ENDER_END_GATE_CHANCE = decimal(builder, "endGateChance", 0.10, 0, 1);
        builder.comment("MINECRAFT-1 (B4): maximum chance per processed turn that a dense Ender Gas",
            "cell condenses into End Sand; End only (mirrors Vapor's own maxCondensationChance).");
        ENDER_END_SAND_CONDENSATION_CHANCE = decimal(builder, "endSandCondensationChance", 0.10, 0, 1);
        builder.pop();

        builder.push("endSand");
        // No worldRestart/gameRestart marker is applied in this section: NeoForge's default is RestartType.NONE.
        builder.comment("Ticks between each 1-block rise. Baked into the block's own scheduled",
            "ticks, so a live edit takes effect the next time a given End Sand block ticks.");
        END_SAND_RISE_INTERVAL = integer(builder, "riseIntervalTicks", 20, 1, 72000);
        builder.pop();

        builder.push("plantGrowth");
        PLANT_CHANCE = decimal(builder, "chance", 0.10, 0, 1);
        PLANT_COST = integer(builder, "cost", 40, 0, 1_000_000);
        builder.pop();

        builder.push("integrations");
        CREATE_FAN_RPM_COEFFICIENT = decimal(builder, "createFanTransportPerRpm", 1.0, 0, 1000);
        CREATE_FAN_INTERVAL = integer(builder, "createFanIntervalTicks", 100, 1, 72000);
        CREATE_FAN_CHUNK_BUDGET = integer(builder, "maxFanChunksPerTick", 32, 1, 10000);
        builder.pop();

        builder.push("magma");
        // Chance a removed magma block leaves a lava source; read fresh each roll, no restart needed.
        MAGMA_BREAK_LAVA_CHANCE = decimal(builder, "breakLavaChance", 0.1, 0, 1);
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
            new RuntimeTuning(SYNC_INTERVAL.get(), FULL_SNAPSHOT_INTERVAL.get(), SIMULATION_INTERVAL.get(),
                PRODUCER_INTERVAL.get(), CHUNK_IMPORTS.get(), PRODUCER_CHUNKS.get(), SMOKE_PRODUCER_CHUNKS.get(), SIMULATION_SKIP_CHANCE.get()),
            new Vapor(VAPOR_PRODUCER_CHANCE.get(),
                HIGH_TERRAIN.get(), RAIN_CLOUD.get(), SNOW_ICE.get(), DARK_GROUND.get(),
                SNOW_LAYER.get(), ICE_REMOVAL.get(), CONDENSATION_CHANCE.get(),
                RAIN_CLOUD_HEIGHT.get(), HIGH_TERRAIN_ABOVE_SEA.get(),
                NETHER_CLOUDLINE_HEIGHT.get(), END_CLOUDLINE_BLOCKS_BELOW_TOP.get()),
            new VerticalMovement(VERTICAL_MOVEMENT_DEAD_BAND.get(), VERTICAL_MOVEMENT_W.get(),
                VERTICAL_MOVEMENT_DRIFT_FRACTION.get()),
            new Smoke(FIRE.get(), LAVA.get(), FURNACE.get(),
                TORCH.get(), CAMPFIRE.get(), EXPLOSION.get(), EXPLOSION_BLOCK.get(), LEAF_CHANCE.get(),
                LEAF_COST.get(), FARMLAND_CHANCE.get(), FARMLAND_COST.get(), VILLAGER_CHANCE.get(),
                VILLAGER_COST.get(), SMOKE_DISSIPATION_CHANCE.get(), SMOKE_DISSIPATION_AMOUNT.get(),
                SMOKE_PREFERRED_HEIGHT.get()),
            new Dust(DUST_WALK.get(), DUST_RUN.get(), DUST_JUMP.get(), DUST_LAND.get(), DUST_FALL_BASE.get(),
                DUST_FALL_PER_POINT.get(), DUST_FALL_MAX.get(), DUST_BREAK.get(), DUST_PLACE.get(),
                DUST_WALK_INTERVAL.get(), DUST_RUN_INTERVAL.get(), FALLING_BLOCK.get(), GRAVEL_CHANCE.get(),
                DUST_DISSIPATION_CHANCE.get(), DUST_DISSIPATION_AMOUNT.get()),
            new VoidGas(VOID_GAS_HOSTILE_DEATH.get(), VOID_GAS_NETHERRACK.get(), VOID_GAS_BOTTOM.get(),
                VOID_GAS_BOTTOM_DENOMINATOR.get(), VOID_GAS_BREAD.get()),
            new Exhaust(EXHAUST_PASSIVE.get(), EXHAUST_LIVING_DENOMINATOR.get(),
                EXHAUST_CREEPER_DENOMINATOR.get(), EXHAUST_DAMAGE_PER_POINT.get(), EXHAUST_DAMAGE_MAX.get(),
                EXHAUST_MIN_FULLNESS.get(), EXHAUST_MAX_FULLNESS.get(), EXHAUST_MIN_DAMAGE.get(),
                EXHAUST_MAX_DAMAGE.get(), EXHAUST_MIN_COST.get(), EXHAUST_MAX_COST.get(),
                EXHAUST_CREEPER_SPAWN_CONSUMPTION.get()),
            new Slime(SLIME_UNDERGROUND.get(), SLIME_UNDERGROUND_DENOMINATOR.get(),
                SLIME_DISSIPATION_SPAWN_DENOMINATOR.get(), SLIME_DISSIPATION_SPAWN_ATTEMPTS.get(),
                SLIME_MEDIUM_DENSITY_THRESHOLD.get(), SLIME_BIG_DENSITY_THRESHOLD.get(),
                SLIME_PREFERRED_HEIGHT.get()),
            new ObsidianPowder(ENDER_MOB.get(), ENDER_PORTAL.get(), OBSIDIAN_PASSIVE.get(), OBSIDIAN_BLOCK.get(),
                CRYING_OBSIDIAN.get(), ENDER_PEARL_USE.get(),
                ENDER_PEARL_IMPACT.get(), ENDER_MOB_INTERVAL.get(),
                ENDER_PORTAL_INTERVAL.get(), ENDER_PORTAL_BLOCK.get(),
                ENDER_END.get(), ENDER_END_GATE_CHANCE.get(), ENDER_END_SAND_CONDENSATION_CHANCE.get()),
            new GlowGas(GLOW_GAS_GLOWSTONE.get()),
            new NetherFog(NETHER_FOG_MAX_CONDENSATION_CHANCE.get()),
            new EndSand(END_SAND_RISE_INTERVAL.get()),
            new PlantGrowth(PLANT_CHANCE.get(), PLANT_COST.get()),
            new Integrations(CREATE_FAN_RPM_COEFFICIENT.get(), CREATE_FAN_INTERVAL.get(), CREATE_FAN_CHUNK_BUDGET.get()),
            new HeavyGas(HEAVY_GAS_DISSIPATION_FACTOR.get()),
            new Magma(MAGMA_BREAK_LAVA_CHANCE.get())
        );
    }

    static int validInt(Integer value, int fallback, int min, int max) {
        return value != null && value >= min && value <= max ? value : fallback;
    }

    static double validDouble(Double value, double fallback, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max ? value : fallback;
    }

    private static IntOption integer(ModConfigSpec.Builder builder, String name, int fallback, int min, int max) {
        return new IntOption(builder.defineInRange(name, fallback, min, max), fallback, min, max);
    }

    private static DoubleOption decimal(
        ModConfigSpec.Builder builder, String name, double fallback, double min, double max
    ) {
        return new DoubleOption(builder.defineInRange(name, fallback, min, max), fallback, min, max);
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

    public record Snapshot(RuntimeTuning runtime, Vapor vapor, VerticalMovement verticalMovement, Smoke smoke, Dust dust, VoidGas voidGas,
                           Exhaust exhaust, Slime slime, ObsidianPowder obsidianPowder, GlowGas glowGas,
                           NetherFog netherFog, EndSand endSand, PlantGrowth plantGrowth,
                           Integrations integrations, HeavyGas heavyGas, Magma magma) { }
    public record RuntimeTuning(int syncIntervalTicks, int fullSnapshotIntervalTicks,
                                int simulationIntervalTicks, int producerIntervalTicks,
                                int maxChunkImportsPerTick, int maxProducerChunksPerTick,
                                int maxSmokeProducerChunksPerTick, double simulationSkipChance) { }
    public record Vapor(double producerChance,
                        int highTerrainEmission, int rainCloudEmission, int snowIceEmission,
                        int maxDarkGroundEmission, int snowRemovalPerLayer, int iceRemovalEmission,
                        double maxCondensationChance, int rainCloudHeight,
                        int highTerrainBlocksAboveSeaLevel, int netherCloudlineHeight,
                        int endCloudlineBlocksBelowTop) {
        public int simulationIntervalTicks() { return snapshot().runtime().simulationIntervalTicks(); }
        public int producerIntervalTicks() { return snapshot().runtime().producerIntervalTicks(); }
    }
    /** MINECRAFT-1 (B1): see {@link PreferredHeightDrift} for how these three combine. */
    public record VerticalMovement(int deadBand, double w, double driftFraction) { }
    public record Smoke(int fireEmission,
                        int lavaEmission, int litFurnaceEmission, int torchEmission, int litCampfireEmission,
                        int explosionEmission, int explosionBlockEmission, double leafRemovalChance,
                        int leafRemovalCost, double farmlandConversionChance, int farmlandConversionCost,
                        double villagerConversionChance, int villagerConversionCost,
                        double dissipationChance, int dissipationAmount, int preferredHeight) {
        public int simulationIntervalTicks() { return snapshot().runtime().simulationIntervalTicks(); }
        public int producerIntervalTicks() { return snapshot().runtime().producerIntervalTicks(); }
    }
    public record Dust(int walkEmission, int runEmission, int jumpEmission, int landEmission,
                       int fallDamageBaseEmission, int fallDamageEmissionPerPoint, int maxFallDamageEmission,
                       int blockBreakEmission, int blockPlaceEmission, int walkIntervalTicks,
                       int runIntervalTicks, int fallingBlockEmission, double gravelChance,
                       double dissipationChance, int dissipationAmount) { }
    public record VoidGas(int hostileDeathEmission, int netherrackEmission, int bottomEmission,
                          int bottomChanceDenominator, int breedingBread) { }
    public record Exhaust(int passiveEmission, int livingChanceDenominator, int creeperChanceDenominator,
                          int damageEmissionPerPoint, int maxDamageEmission, double suffocationMinFullness,
                          double suffocationMaxFullness, double suffocationMinDamage,
                          double suffocationMaxDamage, double suffocationMinCostFraction,
                          double suffocationMaxCostFraction, int creeperSpawnConsumption) { }
    public record Slime(int undergroundEmission, int undergroundChanceDenominator,
                        int dissipationSpawnChanceDenominator, int dissipationSpawnAttempts,
                        double mediumDensityThreshold, double bigDensityThreshold, int preferredHeight) { }
    public record ObsidianPowder(int mobEmission, int portalOccupantEmission, int passiveBlockEmission,
                                 int obsidianBlockEmission, int cryingObsidianEmission,
                           int pearlUseEmission, int pearlImpactEmission,
                           int mobIntervalTicks, int portalIntervalTicks,
                           int portalBlockEmission, int endEmission, double endGateChance,
                           double endSandCondensationChance) { }
    /** MINECRAFT-1 (B2): Glow Gas's home is a literal Y=0, not configurable. */
    public record GlowGas(int glowstoneEmission) { }
    /** MINECRAFT-1 (B3): Nether Fog's home is the dimension's own bottom, not configurable. */
    public record NetherFog(double maxCondensationChance) { }
    /** MINECRAFT-1 (B4): the first config section whose key is read by a registered Block, not simulation code. */
    public record EndSand(int riseIntervalTicks) { }
    public record PlantGrowth(double chance, int cost) { }
    public record Integrations(double createFanTransportPerRpm, int createFanIntervalTicks,
                               int maxFanChunksPerTick) { }
    /** Shared by Void Gas, Obsidian Powder and Slime: their dissipation chance is Smoke's own divided by this. */
    public record HeavyGas(double dissipationFactor) { }
    public record Magma(double breakLavaChance) { }

    private DynamicAtmosphereServerConfig() { }
}
