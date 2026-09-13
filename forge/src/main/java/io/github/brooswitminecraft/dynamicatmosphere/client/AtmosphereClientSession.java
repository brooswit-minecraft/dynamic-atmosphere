package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.List;
import java.util.Objects;

/** Buffers one subscribed view until the client world exists; disconnect drops all state. */
public final class AtmosphereClientSession {
    private AtmosphereClientCache active = new AtmosphereClientCache();
    private AtmosphereClientCache pending = new AtmosphereClientCache();
    private String dimension;
    private String pendingDimension;

    public AtmosphereClientCache cache() { return active; }

    public void world(String next) {
        if (Objects.equals(dimension, next)) {
            return;
        }
        active.clear();
        dimension = next;
        if (next != null && next.equals(pendingDimension)) {
            active = pending;
            pending = new AtmosphereClientCache();
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
        if (dimension != null) {
            active.apply(packetDimension, reset, snapshotEnd, updates);
        } else {
            if (reset) {
                pending.clear();
                pendingDimension = packetDimension;
                pending.changeDimension(packetDimension);
            }
            if (packetDimension.equals(pendingDimension)) {
                pending.apply(packetDimension, reset, snapshotEnd, updates);
            }
        }
    }

    public void clear() {
        active.clear();
        pending.clear();
        dimension = null;
        pendingDimension = null;
    }
}
