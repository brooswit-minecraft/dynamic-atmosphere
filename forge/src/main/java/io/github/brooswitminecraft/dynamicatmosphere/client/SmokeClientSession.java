package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Independent eight-block smoke state. Never reads or writes the vapor disk cache. */
final class SmokeClientSession {
    private final AtmosphereClientSession session = new AtmosphereClientSession(
        () -> new AtmosphereClientCache(AtmosphereClientCache.DEFAULT_CELL_BUDGET, 8, 2, 2));
    private UUID worldId;
    private String dimension;

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
