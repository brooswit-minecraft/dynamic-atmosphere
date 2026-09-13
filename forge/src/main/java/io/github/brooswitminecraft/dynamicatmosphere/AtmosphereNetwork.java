package io.github.brooswitminecraft.dynamicatmosphere;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.network.Connection;

import java.util.Objects;
import java.util.function.BiConsumer;

public final class AtmosphereNetwork {

    private static volatile BiConsumer<AtmosphereGridPayload, Connection> clientReceiver = (payload, connection) -> {
    };

    private AtmosphereNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("4").playToClient(
            AtmosphereGridPayload.TYPE,
            AtmosphereGridPayload.STREAM_CODEC,
            (payload, context) -> clientReceiver.accept(payload, context.connection())
        );
    }

    public static void setClientReceiver(BiConsumer<AtmosphereGridPayload, Connection> receiver) {
        clientReceiver = Objects.requireNonNull(receiver, "receiver");
    }
}
