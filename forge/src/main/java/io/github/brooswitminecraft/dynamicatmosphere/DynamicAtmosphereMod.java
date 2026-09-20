package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.logging.LogUtils;
import io.github.brooswitminecraft.dynamicatmosphere.engine.EngineInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

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
        LavaIceInteractions.register();
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
        DustGameplay dust = prototype.dustGameplay();
        NeoForge.EVENT_BUS.addListener(dust::onEntityTick);
        NeoForge.EVENT_BUS.addListener(dust::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, dust::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, dust::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(dust::onEntityLeave);
        EnderGasGameplay enderGas = prototype.enderGasGameplay();
        NeoForge.EVENT_BUS.addListener(enderGas::onEntityTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, enderGas::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, enderGas::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(enderGas::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(prototype.voidGasGameplay()::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(prototype.exhaustGameplay()::onEntityTick);
        NeoForge.EVENT_BUS.addListener(prototype.exhaustGameplay()::onLivingDamage);
        LOGGER.info("[{}] bounded atmospheric grid enabled", MODID);
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
