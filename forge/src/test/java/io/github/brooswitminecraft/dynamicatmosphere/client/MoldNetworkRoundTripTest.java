package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereMaterial;
import io.github.brooswitminecraft.dynamicatmosphere.MaterialGridPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MoldNetworkRoundTripTest {
    @Test void moldWireCodecFeedsItsOwnClientSessionAndPalette() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var payload = new MaterialGridPayload(AtmosphereMaterial.MOLD, dimension, UUID.randomUUID(), true, true,
                List.of(new MaterialGridPayload.Chunk(-1, -1)),
                List.of(new MaterialGridPayload.Cell(-4, 16, -4, 768, 1000)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MaterialGridPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = MaterialGridPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
            assertEquals(0, buffer.readableBytes());
            var renderMaterial = AtmosphereRenderMaterial.valueOf(decoded.material().name());
            var session = new MaterialClientSession(renderMaterial);
            session.world(dimension.toString());
            session.receive(decoded.worldId(), dimension.toString(), decoded.reset(), decoded.snapshotEnd(),
                    decoded.authoritativeChunks().stream().map(c -> new AtmosphereClientCache.Chunk(c.x(),c.z())).toList(),
                    decoded.cells().stream().map(c -> new AtmosphereClientCache.Update(
                        new AtmosphereClientCache.Cell(c.x(), c.y(), c.z()), c.amount(), c.capacity())).toList());
            assertEquals(768, session.cache().storedAmount(new AtmosphereClientCache.Cell(-4,16,-4)));
            assertEquals(0x706061, renderMaterial.tuning().color());
            session.clear();
            assertEquals(0, session.cache().size());
        } finally { buffer.release(); }
    }
}
