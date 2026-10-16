package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.logging.LogUtils;
import io.github.brooswitminecraft.dynamicatmosphere.engine.EngineInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The mod's NeoForge entry point. The engine reference remains the packaging
 * proof; the Forge adapter owns the bounded server material grids and their
 * NeoForge event integration.
 */
@Mod(DynamicAtmosphereMod.MODID)
public class DynamicAtmosphereMod {

    public static final String MODID = "dynamicatmosphere";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ForgeAtmospherePrototype prototype;

    public DynamicAtmosphereMod(IEventBus modEventBus, ModContainer modContainer) {
        // MINECRAFT-21: run the configVersion check BEFORE registerConfig, which is where
        // NeoForge's ConfigTracker actually reads the file from disk. See ConfigVersionGuard.
        ConfigVersionGuard.applyVersionInvalidation(FMLPaths.CONFIGDIR.get(),
            DynamicAtmosphereServerConfig.FILE_NAME, DynamicAtmosphereServerConfig.CURRENT_VERSION);
        ConfigVersionGuard.applyVersionInvalidation(FMLPaths.CONFIGDIR.get(),
            DynamicAtmosphereClientConfig.FILE_NAME, DynamicAtmosphereClientConfig.CURRENT_VERSION);
        modContainer.registerConfig(ModConfig.Type.SERVER, DynamicAtmosphereServerConfig.SPEC,
            DynamicAtmosphereServerConfig.FILE_NAME);
        modContainer.registerConfig(ModConfig.Type.CLIENT, DynamicAtmosphereClientConfig.SPEC,
            DynamicAtmosphereClientConfig.FILE_NAME);
        modEventBus.addListener(DynamicAtmosphereServerConfig::onLoading);
        modEventBus.addListener(DynamicAtmosphereServerConfig::onReloading);
        modEventBus.addListener(DynamicAtmosphereServerConfig::onUnloading);
        modEventBus.addListener(DynamicAtmosphereClientConfig::onLoading);
        modEventBus.addListener(DynamicAtmosphereClientConfig::onReloading);
        modEventBus.addListener(DynamicAtmosphereClientConfig::onUnloading);
        LOGGER.info("[{}] engine module reachable: {}", MODID, EngineInfo.DESCRIPTION);
        // Deferred to FMLCommonSetupEvent: NeoForgeMod.LAVA_TYPE's registry is not yet
        // populated during mod construction, and FluidInteractionRegistry.addInteraction
        // resolves it eagerly (NeoForgeMod.LAVA_TYPE.value()), not lazily.
        modEventBus.addListener((FMLCommonSetupEvent event) -> LavaIceInteractions.register());
        modEventBus.addListener(AtmosphereNetwork::register);
        ForgeAtmosphereStorage.register(modEventBus);
        ForgeAtmosphereCapacity.register(modEventBus);
        ForgeSmokeStorage.register(modEventBus);
        ForgeSmokeCapacity.register(modEventBus);
        ForgeMaterialStorage.register(modEventBus);
        ForgeMaterialCapacity.register(modEventBus);
        prototype = new ForgeAtmospherePrototype();
        NeoForge.EVENT_BUS.addListener(prototype::onServerTick);
        NeoForge.EVENT_BUS.addListener(prototype::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(prototype::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(prototype::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(prototype::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(prototype::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(prototype::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VaporHostileSpawnGate::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(VaporOverheadTerrain::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(LavaWaterMagmaGameplay::onFluidPlaceBlock);
        DustGameplay dust = prototype.dustGameplay();
        NeoForge.EVENT_BUS.addListener(dust::onEntityTick);
        NeoForge.EVENT_BUS.addListener(dust::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, dust::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, dust::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(dust::onEntityLeave);
        ObsidianPowderGameplay obsidianPowder = prototype.obsidianPowderGameplay();
        NeoForge.EVENT_BUS.addListener(obsidianPowder::onEntityTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, obsidianPowder::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, obsidianPowder::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(obsidianPowder::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(prototype.voidGasGameplay()::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(prototype.exhaustGameplay()::onEntityTick);
        NeoForge.EVENT_BUS.addListener(prototype.exhaustGameplay()::onLivingDamage);
        MagmaLavaGameplay magmaLava = MagmaLavaGameplay.create();
        NeoForge.EVENT_BUS.addListener(magmaLava::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(magmaLava::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(magmaLava::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(magmaLava::onServerTick);
        // MINECRAFT-21: the mod-construction-time ConfigVersionGuard calls above only ever see
        // the global config/ directory. DynamicAtmosphereServerConfig is ModConfig.Type.SERVER,
        // which NeoForge re-reads per-world from <world>/serverconfig/ once that directory holds
        // an override. NOT a before-load hook (see ConfigVersionGuard's javadoc and
        // onServerAboutToStart below for why no event priority can make it one); this listener
        // instead detects the mismatch and explicitly corrects the already-loaded active spec.
        NeoForge.EVENT_BUS.addListener(DynamicAtmosphereMod::onServerAboutToStart);
        LOGGER.info("[{}] bounded atmospheric grid enabled", MODID);
    }

    private static final LevelResource SERVERCONFIG = new LevelResource("serverconfig");

    /**
     * {@code ServerLifecycleHooks.handleServerAboutToStart} calls
     * {@code ConfigTracker.loadConfigs(SERVER, globalConfigDir, worldServerConfigDir)}
     * synchronously BEFORE constructing and posting this very event — confirmed by decompiling
     * NeoForge 21.1's {@code ServerLifecycleHooks.class}. So by the time ANY listener for this
     * event runs, including one at {@code EventPriority.HIGHEST}, the per-world file (stale or
     * not) has already been loaded into the active in-memory spec for this session; renaming the
     * file at that point only prevents the NEXT load, it does not undo this one. There is no
     * earlier supported event to hook instead (the internal call happens inside NeoForge's own
     * server-bootstrap code, not from a separately-postable event).
     *
     * <p>So: back up/remove the stale file as before (still correct — it stops the SAME stale
     * values from being loaded again on every future start), and when that found a mismatch,
     * explicitly re-run NeoForge's own public {@code ConfigTracker.loadConfigs(Type, Path, Path)}
     * — the exact same method {@code handleServerAboutToStart} itself just called — now that the
     * stale per-world file is gone, so it falls through to current global defaults and correctly
     * updates the active spec (and this mod's config-reload listener refreshes its cache) for
     * this session instead of running it stale until the next full restart.
     */
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path worldServerConfigDir = event.getServer().getWorldPath(SERVERCONFIG);
        if (invalidateServerConfigAt(worldServerConfigDir)) {
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, FMLPaths.CONFIGDIR.get(), worldServerConfigDir);
        }
    }

    /** Package-visible for ConfigVersionGuardTest: proves the per-world wiring touches only the
     * exact SERVER file/version this mod owns, in the exact directory handed to it, and reports
     * whether it found (and corrected) a mismatch. */
    static boolean invalidateServerConfigAt(Path worldServerConfigDir) {
        return ConfigVersionGuard.applyVersionInvalidation(worldServerConfigDir,
            DynamicAtmosphereServerConfig.FILE_NAME, DynamicAtmosphereServerConfig.CURRENT_VERSION);
    }

    /** Loaded-only live query for gameplay policy; unknown cells are never treated as dense. */
    public static boolean isVaporMoreThanHalfFull(ServerLevel level, BlockPos pos) {
        ForgeAtmospherePrototype current = prototype;
        return current != null && current.isVaporMoreThanHalfFull(level, pos);
    }

    /** Loaded-only producer boundary for every shared-grid material. */
    public static boolean emitMaterial(ServerLevel level, AtmosphereMaterial material, BlockPos source, int amount) {
        ForgeAtmospherePrototype current = prototype;
        return current != null && current.emitMaterial(level, material, source, amount);
    }

    /** Registers bounded loaded-chunk production without introducing another world scan. */
    public static void registerMaterialProducer(
        AtmosphereMaterial material, AtmosphereMaterialProducer producer
    ) {
        ForgeAtmospherePrototype current = prototype;
        if (current == null) throw new IllegalStateException("Dynamic Atmosphere is not initialized");
        current.registerMaterialProducer(material, producer);
    }

    /** Loaded-only producer boundary for the existing Vapor grid. */
    public static boolean emitVapor(ServerLevel level, BlockPos source, int amount) {
        ForgeAtmospherePrototype current = prototype;
        return current != null && current.emitVapor(level, source, amount);
    }

    /** Returns empty for unloaded, deferred, corrupt, or out-of-world material cells. */
    public static Optional<AtmosphereMaterialState> materialState(
        ServerLevel level, AtmosphereMaterial material, BlockPos source
    ) {
        ForgeAtmospherePrototype current = prototype;
        return current == null ? Optional.empty() : current.materialState(level, material, source);
    }

    /** Server-thread exact debit used after a gameplay transformation succeeds. */
    public static boolean consumeMaterial(
        ServerLevel level, AtmosphereMaterial material, BlockPos source, int amount
    ) {
        ForgeAtmospherePrototype current = prototype;
        return current != null && current.consumeMaterial(level, material, source, amount);
    }

    public static boolean isMaterialMoreThanHalfFull(
        ServerLevel level, AtmosphereMaterial material, BlockPos source
    ) {
        return materialState(level, material, source)
            .map(AtmosphereMaterialState::moreThanHalfFull).orElse(false);
    }
}
