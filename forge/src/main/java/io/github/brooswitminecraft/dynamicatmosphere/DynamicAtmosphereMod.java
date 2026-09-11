package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.logging.LogUtils;
import io.github.brooswitminecraft.dynamicatmosphere.engine.EngineInfo;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The mod's sole NeoForge entry point. Its only runtime behaviour, by
 * design (SICKOS-36 item 4), is the one log line below — everything else
 * this mod will ever do belongs to a later epic. That line reads
 * {@link EngineInfo#DESCRIPTION} from the Minecraft-free :engine
 * subproject, so seeing it in a server log proves both that :engine's
 * classes were packaged into this jar and that they are reachable at
 * runtime.
 */
@Mod(DynamicAtmosphereMod.MODID)
public class DynamicAtmosphereMod {

    public static final String MODID = "dynamicatmosphere";

    private static final Logger LOGGER = LogUtils.getLogger();

    public DynamicAtmosphereMod() {
        LOGGER.info("[{}] engine module reachable: {}", MODID, EngineInfo.DESCRIPTION);
    }
}
