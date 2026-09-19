package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Additional atmospheric materials introduced after the dedicated Vapor and Smoke formats. */
public enum AtmosphereMaterial {
    DUST("dust", 2),
    ENDER_GAS("ender_gas", 2),
    VOID_GAS("void_gas", 8),
    EXHAUST("exhaust", 2),
    SLIME("slime", 16);

    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosphereMaterial> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public AtmosphereMaterial decode(RegistryFriendlyByteBuf buffer) {
                int ordinal = buffer.readVarInt();
                AtmosphereMaterial[] values = AtmosphereMaterial.values();
                if (ordinal < 0 || ordinal >= values.length) {
                    throw new IllegalArgumentException("unknown atmospheric material " + ordinal);
                }
                return values[ordinal];
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, AtmosphereMaterial material) {
                buffer.writeVarInt(material.ordinal());
            }
        };

    private final String id;
    private final int cellSize;

    AtmosphereMaterial(String id, int cellSize) {
        this.id = id;
        this.cellSize = cellSize;
    }

    public String id() { return id; }
    public int cellSize() { return cellSize; }
    public int simulationIntervalTicks() { return DynamicAtmosphereServerConfig.snapshot().runtime().simulationIntervalTicks(); }

    public int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, cellSize);
    }

    public int chunkCoordinate(int cellCoordinate) {
        return Math.floorDiv(cellCoordinate, 16 / cellSize);
    }

    public int capacityForAirBlocks(int airBlocks) {
        int volume = cellSize * cellSize * cellSize;
        int empty = Math.clamp(airBlocks, 0, volume);
        return empty == 0 ? 0 : Math.max(1, empty * AtmosphereGrid.MAX_AMOUNT / volume);
    }

    public long nextSimulationTick(long tick) {
        int interval = simulationIntervalTicks();
        return Math.multiplyExact(Math.floorDiv(tick, interval) + 1, (long) interval);
    }

    public long nextProducerTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("tick must be non-negative");
        int interval = DynamicAtmosphereServerConfig.snapshot().runtime().producerIntervalTicks();
        return Math.multiplyExact(Math.floorDiv(tick, interval) + 1, (long) interval);
    }
}
