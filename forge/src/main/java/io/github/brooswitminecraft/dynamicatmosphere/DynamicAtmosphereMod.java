package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.logging.LogUtils;
import io.github.brooswitminecraft.dynamicatmosphere.engine.EngineInfo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The mod's NeoForge entry point. The engine reference remains the packaging
 * proof; the Forge event adapter is a deliberately small visual delivery
 * spike and is not the planned material simulation.
 */
@Mod(DynamicAtmosphereMod.MODID)
public class DynamicAtmosphereMod {

    public static final String MODID = "dynamicatmosphere";

    private static final Logger LOGGER = LogUtils.getLogger();

    public DynamicAtmosphereMod(IEventBus modEventBus) {
        LOGGER.info("[{}] engine module reachable: {}", MODID, EngineInfo.DESCRIPTION);
        modEventBus.addListener(AtmosphereNetwork::register);
        ForgeAtmosphereStorage.register(modEventBus);
        ForgeAtmospherePrototype prototype = new ForgeAtmospherePrototype();
        NeoForge.EVENT_BUS.addListener(prototype::onServerTick);
        NeoForge.EVENT_BUS.addListener(prototype::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(prototype::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(prototype::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(prototype::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(prototype::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(prototype::onServerStopped);
        LOGGER.info("[{}] bounded atmospheric grid enabled", MODID);
    }
}
