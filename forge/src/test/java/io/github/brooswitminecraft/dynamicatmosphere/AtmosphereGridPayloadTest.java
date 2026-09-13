package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereGridPayloadTest {
    @Test
    void snapshotDataPreservesCapacityAndSignedCoordinates() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cell = new AtmosphereSyncPlanner.Cell(-5, -2, 7, 250, 500);
        var snapshot = planner.plan("player", "overworld", List.of(cell));
        assertEquals(List.of(cell), snapshot.getFirst().cells());
    }

    @Test
    void snapshotDataDoesNotClampOverfullOrTrappedMaterial() {
        var planner = new AtmosphereSyncPlanner<String, String>(512);
        var cells = List.of(new AtmosphereSyncPlanner.Cell(0, 0, 0, 1_000_000, 100),
            new AtmosphereSyncPlanner.Cell(1, 0, 0, 5000, 0));
        var snapshot = planner.plan("player", "overworld", cells);
        assertEquals(cells, snapshot.getFirst().cells());
    }

    @Test
    void protocolCarriesWorldIdentityAndIndependentChunkScope() {
        UUID worldId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        var chunk = new AtmosphereGridPayload.Chunk(-3, 7);
        var cell = new AtmosphereGridPayload.Cell(-12, 4, 28, 250, 500);
        var payload = new AtmosphereGridPayload(
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            worldId, true, true, List.of(chunk), List.of(cell));

        assertEquals(worldId, payload.worldId());
        assertEquals(List.of(chunk), payload.authoritativeChunks());
        assertEquals(List.of(cell), payload.cells());
    }

    @Test
    void protocolBoundsChunkAndCellListsIndependently() {
        UUID worldId = UUID.randomUUID();
        var dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        List<AtmosphereGridPayload.Chunk> tooManyChunks = java.util.stream.IntStream
            .rangeClosed(0, AtmosphereGridPayload.MAX_CHUNKS_PER_PAYLOAD)
            .mapToObj(index -> new AtmosphereGridPayload.Chunk(index, 0))
            .toList();
        List<AtmosphereGridPayload.Cell> tooManyCells = java.util.stream.IntStream
            .rangeClosed(0, AtmosphereGridPayload.MAX_CELLS_PER_PAYLOAD)
            .mapToObj(index -> new AtmosphereGridPayload.Cell(index, 0, 0, 1, 1000))
            .toList();

        assertThrows(IllegalArgumentException.class, () -> new AtmosphereGridPayload(
            dimension, worldId, true, true, tooManyChunks, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereGridPayload(
            dimension, worldId, true, true, List.of(), tooManyCells));
    }
}
