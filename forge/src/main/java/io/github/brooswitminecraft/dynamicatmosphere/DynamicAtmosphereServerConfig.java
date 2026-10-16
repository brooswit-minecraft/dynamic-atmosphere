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
    private static final DoubleOption VERTICAL_MOVEMENT_EMPTY_TOWARD_CLOUDLINE;
    private static final DoubleOption VERTICAL_MOVEMENT_FULL_TOWARD_CLOUDLINE;
    private static final DoubleOption SIMULATION_SKIP_CHANCE;
    private static final IntOption RAIN_CLOUD_HEIGHT;
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

    private static final IntOption SLIME_UNDERGROUND;
    private static final IntOption SLIME_UNDERGROUND_DENOMINATOR;
    private static final IntOption SLIME_DISSIPATION_SPAWN_DENOMINATOR;
    private static final IntOption SLIME_DISSIPATION_SPAWN_ATTEMPTS;
    private static final DoubleOption SLIME_MEDIUM_DENSITY_THRESHOLD;
    private static final DoubleOption SLIME_BIG_DENSITY_THRESHOLD;

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

    private static final DoubleOption PLANT_CHANCE;
    private static final IntOption PLANT_COST;

    private static final DoubleOption CREATE_FAN_RPM_COEFFICIENT;
    private static final IntOption CREATE_FAN_INTERVAL;
    private static final IntOption CREATE_FAN_CHUNK_BUDGET;

    private static final DoubleOption MAGMA_BREAK_LAVA_CHANCE;

    private static final DoubleOption LIGHTNING_MIN_CONCENTRATION;
    private static final DoubleOption LIGHTNING_CHANCE;
    private static final DoubleOption LIGHTNING_MATERIAL_COST;
    private static final IntOption LIGHTNING_SURFACE_ATTEMPTS;

    private static final DoubleOption GHAST_MIN_CONCENTRATION;
    private static final DoubleOption GHAST_CHANCE;
    private static final DoubleOption GHAST_MATERIAL_COST;

    private static final IntOption ZOMBIE_FOG_DEATH_EMISSION;
    private static final DoubleOption ZOMBIE_FOG_SPAWN_CONCENTRATION;
    private static final DoubleOption ZOMBIE_FOG_SPAWN_MULTIPLIER;
    private static final DoubleOption ZOMBIE_FOG_HEAT_MIN_TEMPERATURE;
    private static final DoubleOption ZOMBIE_FOG_HEAT_CHANCE;
    private static final DoubleOption ZOMBIE_FOG_HEAT_MATERIAL_COST;

    private static final DoubleOption SLIME_RAIN_MIN_CONCENTRATION;
    private static final DoubleOption SLIME_RAIN_MIN_TEMPERATURE;
    private static final DoubleOption SLIME_RAIN_CHANCE;
    private static final DoubleOption SLIME_RAIN_MATERIAL_COST;
    private static final DoubleOption SLIME_SNOW_MIN_CONCENTRATION;
    private static final DoubleOption SLIME_SNOW_MAX_TEMPERATURE;
    private static final DoubleOption SLIME_SNOW_CHANCE;
    private static final DoubleOption SLIME_SNOW_MATERIAL_COST;
    private static final IntOption SLIME_PRECIPITATION_SURFACE_ATTEMPTS;

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
        VERTICAL_MOVEMENT_EMPTY_TOWARD_CLOUDLINE = decimal(builder, "emptyTowardCloudlineChance", 0.75, 0, 1);
        VERTICAL_MOVEMENT_FULL_TOWARD_CLOUDLINE = decimal(builder, "fullTowardCloudlineChance", 0.25, 0, 1);
        builder.pop();

        builder.push("vapor");
        VAPOR_PRODUCER_CHANCE = decimal(builder, "producerChance", 0.10, 0, 1);
        RAIN_CLOUD_HEIGHT = integer(builder, "rainCloudHeight", 256, -2048, 2048);
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
        builder.pop();

        builder.push("voidGas");
        VOID_GAS_HOSTILE_DEATH = integer(builder, "hostileDeathEmission", 320, 0, 1_000_000);
        VOID_GAS_NETHERRACK = integer(builder, "netherrackEmission", 16, 0, 1_000_000);
        VOID_GAS_BOTTOM = integer(builder, "bottomEmission", 64, 0, 1_000_000);
        VOID_GAS_BOTTOM_DENOMINATOR = integer(builder, "bottomChanceDenominator", 1, 1, 1_000_000);
        VOID_GAS_BREAD = integer(builder, "breedingBread", 3, 0, 64);
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

        builder.push("lightning");
        // Phase 3 (MINECRAFT-51): lightning is an ordinary generic atmospheric event on the
        // Vapor/Cloud grid -- a dense, precipitating cell occasionally spawns a lightning
        // bolt and consumes material, the same event/action machinery as any other event.
        // No electrical charge simulation and no thunderstorm material/subsystem exist (spec
        // section 25): a thunderstorm IS simply a sufficiently dense Cloud that is both
        // precipitating and occasionally spawning lightning.
        builder.comment("Vapor concentration (amount/capacity) a cell must reach before lightning is possible.");
        LIGHTNING_MIN_CONCENTRATION = decimal(builder, "minConcentration", 0.85, 0, 1);
        builder.comment("Chance, per processed cell per tick, once minConcentration and precipitating both hold.");
        LIGHTNING_CHANCE = decimal(builder, "chance", 0.002, 0, 1);
        builder.comment("Fraction of the cell's capacity consumed when lightning fires -- this, repeated, is",
            "what makes a severe storm dissipate itself.");
        LIGHTNING_MATERIAL_COST = decimal(builder, "materialCost", 0.03, 0, 1);
        builder.comment("Bounded loaded-column search attempts for the precipitation_surface target.");
        LIGHTNING_SURFACE_ATTEMPTS = integer(builder, "surfaceSearchAttempts", 8, 1, 64);
        builder.pop();

        builder.push("ghastSpawn");
        // Spec sections 30/46: "Ghast spawning and lightning use the same event/action
        // machinery. That is intentional." A second, independent material (Smoke) proving
        // the same generic engine, not a second subsystem.
        builder.comment("Smoke concentration (amount/capacity) a cell must reach before a Ghast can appear.");
        GHAST_MIN_CONCENTRATION = decimal(builder, "minConcentration", 0.90, 0, 1);
        builder.comment("Chance, per processed cell per tick, once minConcentration holds.");
        GHAST_CHANCE = decimal(builder, "chance", 0.0001, 0, 1);
        builder.comment("Fraction of the cell's capacity consumed when a Ghast appears.");
        GHAST_MATERIAL_COST = decimal(builder, "materialCost", 0.20, 0, 1);
        builder.pop();

        builder.push("zombieFog");
        // Spec section 17/47: zombie deaths are Zombie Fog's entity source; a single death
        // produces almost nothing, a mass battle produces a real atmospheric event. Section
        // 29/45: Zombie Fog's own worked spawn-modifier example is "zombie spawn multiplier
        // = 2.0" once concentration >= 0.30. Section 47: heat removes it relatively quickly.
        builder.comment("Zombie Fog emitted at a zombie's death position.");
        ZOMBIE_FOG_DEATH_EMISSION = integer(builder, "deathEmission", 24, 0, 1_000_000);
        builder.comment("Zombie Fog concentration at or above which the zombie spawn modifier applies.");
        ZOMBIE_FOG_SPAWN_CONCENTRATION = decimal(builder, "spawnConcentrationThreshold", 0.30, 0, 1);
        builder.comment("Zombie spawn-attempt multiplier once the concentration threshold is met.");
        ZOMBIE_FOG_SPAWN_MULTIPLIER = decimal(builder, "spawnMultiplier", 2.0, 0, 100);
        builder.comment("Biome base temperature at or above which the heat sink can fire.");
        ZOMBIE_FOG_HEAT_MIN_TEMPERATURE = decimal(builder, "heatMinBiomeTemperature", 0.5, -10, 10);
        builder.comment("Chance, per processed cell per tick, once heatMinBiomeTemperature holds.");
        ZOMBIE_FOG_HEAT_CHANCE = decimal(builder, "heatChance", 0.05, 0, 1);
        builder.comment("Fraction of the cell's capacity removed each time the heat sink fires.");
        ZOMBIE_FOG_HEAT_MATERIAL_COST = decimal(builder, "heatMaterialCost", 0.08, 0, 1);
        builder.pop();

        builder.push("slimePrecipitation");
        // Spec section 44, Slime Fog's precipitation example: rain mode occasionally drops
        // slimeballs, snow mode occasionally places slime blocks, selected by biome
        // temperature exactly like vanilla rain/snow. This is independent of, and does not
        // replace, Slime's existing MINECRAFT-18 dissipation-triggered spawn.
        builder.comment("Slime concentration (amount/capacity) required for either precipitation mode.");
        SLIME_RAIN_MIN_CONCENTRATION = decimal(builder, "rainMinConcentration", 0.65, 0, 1);
        builder.comment("Biome base temperature at or above which rain mode (not snow mode) applies.");
        SLIME_RAIN_MIN_TEMPERATURE = decimal(builder, "rainMinBiomeTemperature", 0.15, -10, 10);
        builder.comment("Chance, per processed cell per tick, that rain mode drops a slimeball.");
        SLIME_RAIN_CHANCE = decimal(builder, "rainChance", 0.02, 0, 1);
        builder.comment("Fraction of the cell's capacity consumed when rain mode fires.");
        SLIME_RAIN_MATERIAL_COST = decimal(builder, "rainMaterialCost", 0.01, 0, 1);
        SLIME_SNOW_MIN_CONCENTRATION = decimal(builder, "snowMinConcentration", 0.65, 0, 1);
        builder.comment("Biome base temperature at or below which snow mode (not rain mode) applies.");
        SLIME_SNOW_MAX_TEMPERATURE = decimal(builder, "snowMaxBiomeTemperature", 0.15, -10, 10);
        builder.comment("Chance, per processed cell per tick, that snow mode places a slime block.");
        SLIME_SNOW_CHANCE = decimal(builder, "snowChance", 0.002, 0, 1);
        builder.comment("Fraction of the cell's capacity consumed when snow mode fires.");
        SLIME_SNOW_MATERIAL_COST = decimal(builder, "snowMaterialCost", 0.04, 0, 1);
        builder.comment("Bounded loaded-column search attempts for the precipitation_surface target.");
        SLIME_PRECIPITATION_SURFACE_ATTEMPTS = integer(builder, "surfaceSearchAttempts", 8, 1, 64);
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
                RAIN_CLOUD_HEIGHT.get(), HIGH_TERRAIN_ABOVE_SEA.get()),
            new VerticalMovement(VERTICAL_MOVEMENT_EMPTY_TOWARD_CLOUDLINE.get(),
                VERTICAL_MOVEMENT_FULL_TOWARD_CLOUDLINE.get()),
            new Smoke(FIRE.get(), LAVA.get(), FURNACE.get(),
                TORCH.get(), CAMPFIRE.get(), EXPLOSION.get(), EXPLOSION_BLOCK.get(), LEAF_CHANCE.get(),
                LEAF_COST.get(), FARMLAND_CHANCE.get(), FARMLAND_COST.get(), VILLAGER_CHANCE.get(),
                VILLAGER_COST.get(), SMOKE_DISSIPATION_CHANCE.get(), SMOKE_DISSIPATION_AMOUNT.get()),
            new Dust(DUST_WALK.get(), DUST_RUN.get(), DUST_JUMP.get(), DUST_LAND.get(), DUST_FALL_BASE.get(),
                DUST_FALL_PER_POINT.get(), DUST_FALL_MAX.get(), DUST_BREAK.get(), DUST_PLACE.get(),
                DUST_WALK_INTERVAL.get(), DUST_RUN_INTERVAL.get(), FALLING_BLOCK.get(), GRAVEL_CHANCE.get(),
                DUST_DISSIPATION_CHANCE.get(), DUST_DISSIPATION_AMOUNT.get()),
            new VoidGas(VOID_GAS_HOSTILE_DEATH.get(), VOID_GAS_NETHERRACK.get(), VOID_GAS_BOTTOM.get(),
                VOID_GAS_BOTTOM_DENOMINATOR.get(), VOID_GAS_BREAD.get()),
            new Exhaust(EXHAUST_PASSIVE.get(), EXHAUST_LIVING_DENOMINATOR.get(),
                EXHAUST_CREEPER_DENOMINATOR.get(), EXHAUST_DAMAGE_PER_POINT.get(), EXHAUST_DAMAGE_MAX.get(),
                EXHAUST_MIN_FULLNESS.get(), EXHAUST_MAX_FULLNESS.get(), EXHAUST_MIN_DAMAGE.get(),
                EXHAUST_MAX_DAMAGE.get(), EXHAUST_MIN_COST.get(), EXHAUST_MAX_COST.get()),
            new Slime(SLIME_UNDERGROUND.get(), SLIME_UNDERGROUND_DENOMINATOR.get(),
                SLIME_DISSIPATION_SPAWN_DENOMINATOR.get(), SLIME_DISSIPATION_SPAWN_ATTEMPTS.get(),
                SLIME_MEDIUM_DENSITY_THRESHOLD.get(), SLIME_BIG_DENSITY_THRESHOLD.get()),
            new ObsidianPowder(ENDER_MOB.get(), ENDER_PORTAL.get(), OBSIDIAN_PASSIVE.get(), OBSIDIAN_BLOCK.get(),
                CRYING_OBSIDIAN.get(), ENDER_PEARL_USE.get(),
                ENDER_PEARL_IMPACT.get(), ENDER_MOB_INTERVAL.get(),
                ENDER_PORTAL_INTERVAL.get(), ENDER_PORTAL_BLOCK.get()),
            new PlantGrowth(PLANT_CHANCE.get(), PLANT_COST.get()),
            new Integrations(CREATE_FAN_RPM_COEFFICIENT.get(), CREATE_FAN_INTERVAL.get(), CREATE_FAN_CHUNK_BUDGET.get()),
            new HeavyGas(HEAVY_GAS_DISSIPATION_FACTOR.get()),
            new Magma(MAGMA_BREAK_LAVA_CHANCE.get()),
            new Lightning(LIGHTNING_MIN_CONCENTRATION.get(), LIGHTNING_CHANCE.get(), LIGHTNING_MATERIAL_COST.get(),
                LIGHTNING_SURFACE_ATTEMPTS.get()),
            new GhastSpawn(GHAST_MIN_CONCENTRATION.get(), GHAST_CHANCE.get(), GHAST_MATERIAL_COST.get()),
            new ZombieFog(ZOMBIE_FOG_DEATH_EMISSION.get(), ZOMBIE_FOG_SPAWN_CONCENTRATION.get(),
                ZOMBIE_FOG_SPAWN_MULTIPLIER.get(), ZOMBIE_FOG_HEAT_MIN_TEMPERATURE.get(),
                ZOMBIE_FOG_HEAT_CHANCE.get(), ZOMBIE_FOG_HEAT_MATERIAL_COST.get()),
            new SlimePrecipitation(SLIME_RAIN_MIN_CONCENTRATION.get(), SLIME_RAIN_MIN_TEMPERATURE.get(),
                SLIME_RAIN_CHANCE.get(), SLIME_RAIN_MATERIAL_COST.get(), SLIME_SNOW_MIN_CONCENTRATION.get(),
                SLIME_SNOW_MAX_TEMPERATURE.get(), SLIME_SNOW_CHANCE.get(), SLIME_SNOW_MATERIAL_COST.get(),
                SLIME_PRECIPITATION_SURFACE_ATTEMPTS.get())
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
                           Exhaust exhaust, Slime slime, ObsidianPowder obsidianPowder, PlantGrowth plantGrowth,
                           Integrations integrations, HeavyGas heavyGas, Magma magma,
                           Lightning lightning, GhastSpawn ghastSpawn, ZombieFog zombieFog,
                           SlimePrecipitation slimePrecipitation) { }
    public record RuntimeTuning(int syncIntervalTicks, int fullSnapshotIntervalTicks,
                                int simulationIntervalTicks, int producerIntervalTicks,
                                int maxChunkImportsPerTick, int maxProducerChunksPerTick,
                                int maxSmokeProducerChunksPerTick, double simulationSkipChance) { }
    public record Vapor(double producerChance,
                        int highTerrainEmission, int rainCloudEmission, int snowIceEmission,
                        int maxDarkGroundEmission, int snowRemovalPerLayer, int iceRemovalEmission,
                        double maxCondensationChance, int rainCloudHeight,
                        int highTerrainBlocksAboveSeaLevel) {
        public int simulationIntervalTicks() { return snapshot().runtime().simulationIntervalTicks(); }
        public int producerIntervalTicks() { return snapshot().runtime().producerIntervalTicks(); }
    }
    /** Fill fraction linearly maps from emptyTowardCloudlineChance to fullTowardCloudlineChance. */
    public record VerticalMovement(double emptyTowardCloudlineChance, double fullTowardCloudlineChance) { }
    public record Smoke(int fireEmission,
                        int lavaEmission, int litFurnaceEmission, int torchEmission, int litCampfireEmission,
                        int explosionEmission, int explosionBlockEmission, double leafRemovalChance,
                        int leafRemovalCost, double farmlandConversionChance, int farmlandConversionCost,
                        double villagerConversionChance, int villagerConversionCost,
                        double dissipationChance, int dissipationAmount) {
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
                          double suffocationMaxCostFraction) { }
    public record Slime(int undergroundEmission, int undergroundChanceDenominator,
                        int dissipationSpawnChanceDenominator, int dissipationSpawnAttempts,
                        double mediumDensityThreshold, double bigDensityThreshold) { }
    public record ObsidianPowder(int mobEmission, int portalOccupantEmission, int passiveBlockEmission,
                                 int obsidianBlockEmission, int cryingObsidianEmission,
                           int pearlUseEmission, int pearlImpactEmission,
                           int mobIntervalTicks, int portalIntervalTicks,
                           int portalBlockEmission) { }
    public record PlantGrowth(double chance, int cost) { }
    public record Integrations(double createFanTransportPerRpm, int createFanIntervalTicks,
                               int maxFanChunksPerTick) { }
    /** Shared by Void Gas, Obsidian Powder and Slime: their dissipation chance is Smoke's own divided by this. */
    public record HeavyGas(double dissipationFactor) { }
    public record Magma(double breakLavaChance) { }
    public record Lightning(double minConcentration, double chance, double materialCost, int surfaceSearchAttempts) { }
    public record GhastSpawn(double minConcentration, double chance, double materialCost) { }
    public record ZombieFog(int deathEmission, double spawnConcentrationThreshold, double spawnMultiplier,
                            double heatMinBiomeTemperature, double heatChance, double heatMaterialCost) { }
    public record SlimePrecipitation(double rainMinConcentration, double rainMinBiomeTemperature,
                                     double rainChance, double rainMaterialCost, double snowMinConcentration,
                                     double snowMaxBiomeTemperature, double snowChance, double snowMaterialCost,
                                     int surfaceSearchAttempts) { }

    private DynamicAtmosphereServerConfig() { }
}
