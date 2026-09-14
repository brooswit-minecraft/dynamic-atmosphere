package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record SmokeGridPayload(
    ResourceLocation dimension,
    UUID worldId,
    boolean reset,
    boolean snapshotEnd,
    List<Chunk> authoritativeChunks,
    List<Cell> cells
) implements CustomPacketPayload {
    public static final int MAX_CELLS_PER_PAYLOAD = 512;
    public static final int MAX_CHUNKS_PER_PAYLOAD = 512;
    public static final Type<SmokeGridPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicAtmosphereMod.MODID, "smoke_grid"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SmokeGridPayload> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, SmokeGridPayload::dimension,
        UUIDUtil.STREAM_CODEC, SmokeGridPayload::worldId,
        ByteBufCodecs.BOOL, SmokeGridPayload::reset,
        ByteBufCodecs.BOOL, SmokeGridPayload::snapshotEnd,
        Chunk.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHUNKS_PER_PAYLOAD)), SmokeGridPayload::authoritativeChunks,
        Cell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS_PER_PAYLOAD)), SmokeGridPayload::cells,
        SmokeGridPayload::new);

    public SmokeGridPayload {
        authoritativeChunks = List.copyOf(authoritativeChunks);
        cells = List.copyOf(cells);
        if (authoritativeChunks.size() > MAX_CHUNKS_PER_PAYLOAD) {
            throw new IllegalArgumentException("too many authoritative smoke chunks in one payload");
        }
        if (cells.size() > MAX_CELLS_PER_PAYLOAD) {
            throw new IllegalArgumentException("too many smoke cells in one payload");
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
                throw new IllegalArgumentException("smoke capacity must be between 0 and 1000");
            }
            if (amount < 0 || amount > AtmosphereGrid.MAX_STORED_AMOUNT) {
                throw new IllegalArgumentException("smoke amount outside supported range");
            }
        }
    }
}
