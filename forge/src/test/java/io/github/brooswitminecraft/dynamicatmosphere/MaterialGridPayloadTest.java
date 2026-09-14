package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialGridPayloadTest {
    @Test
    void preservesMaterialAndSnapshotScope() {
        var payload = new MaterialGridPayload(AtmosphereMaterial.DUST,
            ResourceLocation.withDefaultNamespace("overworld"), UUID.randomUUID(), true, true,
            List.of(new MaterialGridPayload.Chunk(-1, 2)),
            List.of(new MaterialGridPayload.Cell(-1, 32, 16, 500, 750)));

        assertEquals(AtmosphereMaterial.DUST, payload.material());
        assertEquals(-1, payload.authoritativeChunks().getFirst().x());
        assertEquals(500, payload.cells().getFirst().amount());
    }

    @Test
    void rejectsOversizedBatchesAndInvalidAmounts() {
        var chunks = Collections.nCopies(MaterialGridPayload.MAX_CHUNKS_PER_PAYLOAD + 1,
            new MaterialGridPayload.Chunk(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MaterialGridPayload(
            AtmosphereMaterial.ENDER_GAS, ResourceLocation.withDefaultNamespace("overworld"),
            UUID.randomUUID(), false, false, chunks, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new MaterialGridPayload.Cell(0, 0, 0, -1, 1000));
    }
}
