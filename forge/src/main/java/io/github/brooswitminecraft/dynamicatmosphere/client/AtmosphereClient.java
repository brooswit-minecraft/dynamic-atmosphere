package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridPayload;
import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereNetwork;
import io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.lang.ref.WeakReference;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = DynamicAtmosphereMod.MODID, value = Dist.CLIENT)
public final class AtmosphereClient {
    private static final AtmosphereClientSession SESSION = new AtmosphereClientSession();
    private static ClientLevel observedLevel;
    private static ClientLevel unloadedLevel;
    private static WeakReference<Connection> disconnectedConnection = new WeakReference<>(null);
    private static final AtmosphereDiskCache DISK = new AtmosphereDiskCache(
        Minecraft.getInstance().gameDirectory.toPath().resolve("dynamicatmosphere-cache"));
    private static final AtmosphereCacheWriter SAVER = new AtmosphereCacheWriter(DISK);
    private static String cacheNamespace;
    private static String cacheDimension;
    private static boolean cacheDirty;
    private static int saveTicks;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(SAVER::shutdown, "Atmosphere cache shutdown"));
    }

    @EventBusSubscriber(modid = DynamicAtmosphereMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void initialize(FMLClientSetupEvent event) {
            event.enqueueWork(() -> AtmosphereNetwork.setClientReceiver(AtmosphereClient::receive));
        }
    }

    /** Called only by the common protocol's main-thread client-bound handler. */
    public static void receive(AtmosphereGridPayload payload, Connection connection) {
        var listener = Minecraft.getInstance().getConnection();
        if (connection == disconnectedConnection.get() || listener == null
            || listener.getConnection() != connection || !connection.isConnected()) {
            return;
        }
        syncWorld();
        if (observedLevel != null && !observedLevel.dimension().location().equals(payload.dimension())) return;
        String server = Minecraft.getInstance().getCurrentServer() == null ? "local"
            : Minecraft.getInstance().getCurrentServer().ip;
        String namespace = DISK.namespace(server, payload.worldId(), payload.dimension().toString());
        if (!namespace.equals(cacheNamespace)) {
            checkpoint();
            SESSION.clear();
            AtmosphereVolumeRenderer.close();
            if (observedLevel != null) SESSION.world(payload.dimension().toString());
            cacheNamespace = namespace;
            cacheDimension = payload.dimension().toString();
            try { SESSION.restore(cacheDimension, SAVER.load(namespace)); }
            catch (java.io.IOException failure) { LogUtils.getLogger().warn("Cannot load disposable atmosphere cache", failure); }
        }
        SESSION.receive(payload.dimension().toString(), payload.reset(), payload.snapshotEnd(), payload.authoritativeChunks().stream()
            .map(chunk -> new AtmosphereClientCache.Chunk(chunk.x(), chunk.z())).toList(), payload.cells().stream()
            .map(cell -> new AtmosphereClientCache.Update(
                new AtmosphereClientCache.Cell(cell.x(), cell.y(), cell.z()), cell.amount(), cell.capacity()))
            .toList());
        cacheDirty = true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        syncWorld();
        if (observedLevel != null && !Minecraft.getInstance().isPaused()) {
            SESSION.cache().advance();
            AtmosphereVolumeRenderer.tick(observedLevel);
            if (++saveTicks >= 200) {
                saveTicks = 0;
                checkpoint();
                SAVER.retry();
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        syncWorld();
        if (observedLevel != null) {
            AtmosphereVolumeRenderer.render(event, observedLevel, SESSION.cache());
        }
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        disconnectedConnection = new WeakReference<>(event.getConnection());
        clear();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == observedLevel) {
            ClientLevel previous = observedLevel;
            clear();
            unloadedLevel = previous;
        }
    }

    private static void syncWorld() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == unloadedLevel) {
            current = null;
        } else {
            unloadedLevel = null;
        }
        if (current != observedLevel) {
            if (current == null || !current.dimension().location().toString().equals(cacheDimension)) {
                checkpoint();
                cacheNamespace = null;
                cacheDimension = null;
            }
            if (observedLevel != null && current != null) {
                SESSION.clear();
            }
            AtmosphereVolumeRenderer.close();
            observedLevel = current;
            SESSION.world(current == null ? null : current.dimension().location().toString());
        }
    }

    private static void clear() {
        checkpoint();
        cacheNamespace = null;
        cacheDimension = null;
        observedLevel = null;
        unloadedLevel = null;
        SESSION.clear();
        AtmosphereVolumeRenderer.close();
    }

    private static void checkpoint() {
        if (cacheNamespace == null || !cacheDirty) return;
        String namespace = cacheNamespace;
        var cells = SESSION.cache().exportUpdates();
        cacheDirty = false;
        SAVER.submit(namespace, cells);
    }

    public static int cachedCellCount() {
        return SESSION.cache().size();
    }

    private AtmosphereClient() { }
}
