package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record AtmosphereGridPayload(ResourceLocation dimension, boolean reset, boolean snapshotEnd, List<Cell> cells)
    implements CustomPacketPayload {

    public static final int MAX_CELLS_PER_PAYLOAD = 512;
    public static final Type<AtmosphereGridPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicAtmosphereMod.MODID, "atmosphere_grid"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosphereGridPayload> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC,
        AtmosphereGridPayload::dimension,
        ByteBufCodecs.BOOL,
        AtmosphereGridPayload::reset,
        ByteBufCodecs.BOOL,
        AtmosphereGridPayload::snapshotEnd,
        Cell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS_PER_PAYLOAD)),
        AtmosphereGridPayload::cells,
        AtmosphereGridPayload::new
    );

    public AtmosphereGridPayload {
        cells = List.copyOf(cells);
        if (cells.size() > MAX_CELLS_PER_PAYLOAD) {
            throw new IllegalArgumentException("too many atmosphere cells in one payload");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Cell(int x, int y, int z, int amount, int capacity) {
        static final StreamCodec<RegistryFriendlyByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            Cell::x,
            ByteBufCodecs.VAR_INT,
            Cell::y,
            ByteBufCodecs.VAR_INT,
            Cell::z,
            ByteBufCodecs.VAR_INT,
            Cell::amount,
            ByteBufCodecs.VAR_INT,
            Cell::capacity,
            Cell::new
        );

        public Cell {
            if (capacity < 0 || capacity > AtmosphereGrid.MAX_AMOUNT) {
                throw new IllegalArgumentException("atmosphere capacity must be between 0 and 1000");
            }
            if (amount < 0 || amount > AtmosphereGrid.MAX_STORED_AMOUNT) {
                throw new IllegalArgumentException("atmosphere amount must be between 0 and " + AtmosphereGrid.MAX_STORED_AMOUNT);
            }
        }
    }
}
