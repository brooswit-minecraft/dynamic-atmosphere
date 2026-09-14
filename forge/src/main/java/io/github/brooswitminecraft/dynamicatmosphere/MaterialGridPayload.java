package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record MaterialGridPayload(
    AtmosphereMaterial material,
    ResourceLocation dimension,
    UUID worldId,
    boolean reset,
    boolean snapshotEnd,
    List<Chunk> authoritativeChunks,
    List<Cell> cells
) implements CustomPacketPayload {
    public static final int MAX_CELLS_PER_PAYLOAD = 512;
    public static final int MAX_CHUNKS_PER_PAYLOAD = 512;
    public static final Type<MaterialGridPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicAtmosphereMod.MODID, "material_grid"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialGridPayload> STREAM_CODEC =
        new StreamCodec<>() {
            private final StreamCodec<RegistryFriendlyByteBuf, List<Chunk>> chunks =
                Chunk.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHUNKS_PER_PAYLOAD));
            private final StreamCodec<RegistryFriendlyByteBuf, List<Cell>> cells =
                Cell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS_PER_PAYLOAD));

            @Override
            public MaterialGridPayload decode(RegistryFriendlyByteBuf buffer) {
                return new MaterialGridPayload(
                    AtmosphereMaterial.STREAM_CODEC.decode(buffer),
                    ResourceLocation.STREAM_CODEC.decode(buffer),
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    chunks.decode(buffer),
                    cells.decode(buffer));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, MaterialGridPayload payload) {
                AtmosphereMaterial.STREAM_CODEC.encode(buffer, payload.material());
                ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimension());
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.worldId());
                ByteBufCodecs.BOOL.encode(buffer, payload.reset());
                ByteBufCodecs.BOOL.encode(buffer, payload.snapshotEnd());
                chunks.encode(buffer, payload.authoritativeChunks());
                cells.encode(buffer, payload.cells());
            }
        };

    public MaterialGridPayload {
        authoritativeChunks = List.copyOf(authoritativeChunks);
        cells = List.copyOf(cells);
        if (authoritativeChunks.size() > MAX_CHUNKS_PER_PAYLOAD) {
            throw new IllegalArgumentException("too many authoritative material chunks in one payload");
        }
        if (cells.size() > MAX_CELLS_PER_PAYLOAD) {
            throw new IllegalArgumentException("too many material cells in one payload");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Chunk(int x, int z) {
        static final StreamCodec<RegistryFriendlyByteBuf, Chunk> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Chunk::x, ByteBufCodecs.VAR_INT, Chunk::z, Chunk::new);
    }

    public record Cell(int x, int y, int z, int amount, int capacity) {
        static final StreamCodec<RegistryFriendlyByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Cell::x,
            ByteBufCodecs.VAR_INT, Cell::y,
            ByteBufCodecs.VAR_INT, Cell::z,
            ByteBufCodecs.VAR_INT, Cell::amount,
            ByteBufCodecs.VAR_INT, Cell::capacity,
            Cell::new);

        public Cell {
            if (capacity < 0 || capacity > AtmosphereGrid.MAX_AMOUNT) {
                throw new IllegalArgumentException("material capacity must be between 0 and 1000");
            }
            if (amount < 0 || amount > AtmosphereGrid.MAX_STORED_AMOUNT) {
                throw new IllegalArgumentException("material amount outside supported range");
            }
        }
    }
}
