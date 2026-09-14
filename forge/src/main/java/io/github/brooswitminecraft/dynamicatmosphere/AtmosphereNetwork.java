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
        var registrar = event.registrar("9");
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
