package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Buffers one subscribed view until the client world exists; disconnect drops all state. */
public final class AtmosphereClientSession {
    private final Supplier<AtmosphereClientCache> factory;
    private AtmosphereClientCache active;
    private AtmosphereClientCache pending;
    private String dimension;
    private String pendingDimension;

    public AtmosphereClientSession() { this(AtmosphereClientCache::new); }

    AtmosphereClientSession(Supplier<AtmosphereClientCache> factory) {
        this.factory = Objects.requireNonNull(factory);
        active = factory.get();
        pending = factory.get();
    }

    public AtmosphereClientCache cache() { return dimension == null ? pending : active; }

    public void restore(String packetDimension, List<AtmosphereClientCache.Update> updates) {
        if (dimension != null) {
            if (dimension.equals(packetDimension)) active.restore(updates);
        } else {
            if (!packetDimension.equals(pendingDimension)) {
                pending.clear();
                pendingDimension = packetDimension;
                pending.changeDimension(packetDimension);
            }
            pending.restore(updates);
        }
    }

    public void world(String next) {
        if (Objects.equals(dimension, next)) {
            return;
        }
        active.clear();
        dimension = next;
        if (next != null && next.equals(pendingDimension)) {
            active = pending;
            pending = factory.get();
        } else {
            active.changeDimension(next);
            pending.clear();
        }
        pendingDimension = null;
    }

    public void receive(String packetDimension, boolean reset, List<AtmosphereClientCache.Update> updates) {
        receive(packetDimension, reset, reset, updates);
    }

    public void receive(String packetDimension, boolean reset, boolean snapshotEnd, List<AtmosphereClientCache.Update> updates) {
        receive(packetDimension, reset, snapshotEnd, null, updates);
    }

    public void receive(String packetDimension, boolean reset, boolean snapshotEnd,
                        List<AtmosphereClientCache.Chunk> authoritativeChunks, List<AtmosphereClientCache.Update> updates) {
        if (dimension != null) {
            apply(active, packetDimension, reset, snapshotEnd, authoritativeChunks, updates);
        } else {
            if (reset) {
                if (!packetDimension.equals(pendingDimension)) pending.clear();
                pendingDimension = packetDimension;
                pending.changeDimension(packetDimension);
            }
            if (packetDimension.equals(pendingDimension)) {
                apply(pending, packetDimension, reset, snapshotEnd, authoritativeChunks, updates);
            }
        }
    }

    private static void apply(AtmosphereClientCache cache, String dimension, boolean reset, boolean end,
                              List<AtmosphereClientCache.Chunk> chunks, List<AtmosphereClientCache.Update> updates) {
        if (chunks == null) cache.apply(dimension, reset, end, updates);
        else cache.apply(dimension, reset, end, chunks, updates);
    }

    public void clear() {
        active.clear();
        pending.clear();
        dimension = null;
        pendingDimension = null;
    }
}
