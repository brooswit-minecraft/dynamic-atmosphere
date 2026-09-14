package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmokeGridPayloadTest {
    @Test
    void copiesSnapshotCollectionsAndRetainsWorldIdentity() {
        UUID world = UUID.randomUUID();
        var payload = new SmokeGridPayload(ResourceLocation.withDefaultNamespace("overworld"), world,
            true, true, List.of(new SmokeGridPayload.Chunk(0, 0)),
            List.of(new SmokeGridPayload.Cell(0, 8, 0, 40, 998)));

        assertEquals(world, payload.worldId());
        assertEquals(40, payload.cells().getFirst().amount());
    }

    @Test
    void rejectsAmountsOutsideWireBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new SmokeGridPayload.Cell(0, 0, 0, -1, 1000));
        assertThrows(IllegalArgumentException.class,
            () -> new SmokeGridPayload.Cell(0, 0, 0, 1, 1001));
    }
}
