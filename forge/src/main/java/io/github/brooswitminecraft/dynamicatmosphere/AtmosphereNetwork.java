package io.github.brooswitminecraft.dynamicatmosphere;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.network.Connection;

import java.util.Objects;
import java.util.function.BiConsumer;

public final class AtmosphereNetwork {

    private static volatile BiConsumer<AtmosphereGridPayload, Connection> clientReceiver = (payload, connection) -> {
    };
    private static volatile BiConsumer<SmokeGridPayload, Connection> smokeClientReceiver = (payload, connection) -> {
    };
    private static volatile BiConsumer<MaterialGridPayload, Connection> materialClientReceiver = (payload, connection) -> {
    };

    private AtmosphereNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        // MINECRAFT-1 (B2) bumped 10 -> 11 for Glow Gas; MINECRAFT-51 (Zombie Fog) and
        // MINECRAFT-1 (B3, Nether Fog) each independently bumped 11 -> 12 on their own branch,
        // for two DIFFERENT 9-material layouts -- "12" cannot be reused for the combined
        // 10-material layout (Zombie Fog + Glow Gas + Nether Fog together) without risking a
        // client/server pair that both claim version 12 while actually disagreeing on ordinals.
        // Bumped straight to 13 for this merge (AtmosphereMaterial adds an ordinal; a
        // client/server running different protocol versions must not connect).
        var registrar = event.registrar("13");
        registrar.playToClient(AtmosphereGridPayload.TYPE, AtmosphereGridPayload.STREAM_CODEC,
            (payload, context) -> clientReceiver.accept(payload, context.connection()));
        registrar.playToClient(SmokeGridPayload.TYPE, SmokeGridPayload.STREAM_CODEC,
            (payload, context) -> smokeClientReceiver.accept(payload, context.connection()));
        registrar.playToClient(MaterialGridPayload.TYPE, MaterialGridPayload.STREAM_CODEC,
            (payload, context) -> materialClientReceiver.accept(payload, context.connection()));
    }

    public static void setClientReceiver(BiConsumer<AtmosphereGridPayload, Connection> receiver) {
        clientReceiver = Objects.requireNonNull(receiver, "receiver");
    }

    public static void setSmokeClientReceiver(BiConsumer<SmokeGridPayload, Connection> receiver) {
        smokeClientReceiver = Objects.requireNonNull(receiver, "receiver");
    }

    public static void setMaterialClientReceiver(BiConsumer<MaterialGridPayload, Connection> receiver) {
        materialClientReceiver = Objects.requireNonNull(receiver, "receiver");
    }
}
