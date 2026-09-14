package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Independent transient material state. Never reads or writes the vapor disk cache. */
class MaterialClientSession {
    private final AtmosphereClientSession session;
    private UUID worldId;
    private String dimension;

    MaterialClientSession(AtmosphereRenderMaterial material) {
        session = new AtmosphereClientSession(material::newCache);
    }

    AtmosphereClientCache cache() { return session.cache(); }

    void world(String next) {
        if (dimension != null && !Objects.equals(dimension, next)) worldId = null;
        dimension = next;
        session.world(next);
    }

    void receive(UUID id, String packetDimension, boolean reset, boolean end,
                 List<AtmosphereClientCache.Chunk> chunks, List<AtmosphereClientCache.Update> updates) {
        if (dimension != null && !dimension.equals(packetDimension)) return;
        if (!Objects.equals(worldId, id)) {
            if (!reset) return;
            session.clear();
            session.world(dimension);
            worldId = Objects.requireNonNull(id);
        }
        session.receive(packetDimension, reset, end, chunks, updates);
    }

    void clear() {
        session.clear();
        worldId = null;
        dimension = null;
    }
}
