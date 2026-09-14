package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Additional atmospheric materials introduced after the dedicated Vapor and Smoke formats. */
public enum AtmosphereMaterial {
    DUST("dust", 2, 50),
    ENDER_GAS("ender_gas", 1, 25),
    VIOLENCE("violence", 8, 200),
    EXHAUST("exhaust", 2, 50),
    SLIME("slime", 16, 400);

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
    private final int simulationIntervalTicks;

    AtmosphereMaterial(String id, int cellSize, int simulationIntervalTicks) {
        this.id = id;
        this.cellSize = cellSize;
        this.simulationIntervalTicks = simulationIntervalTicks;
    }

    public String id() { return id; }
    public int cellSize() { return cellSize; }
    public int simulationIntervalTicks() { return simulationIntervalTicks; }

    public int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, cellSize);
    }

    public int chunkCoordinate(int cellCoordinate) {
        return Math.floorDiv(cellCoordinate, 16 / cellSize);
    }

    public int capacityForAirBlocks(int airBlocks) {
        int volume = cellSize * cellSize * cellSize;
        return Math.clamp(airBlocks, 0, volume) * AtmosphereGrid.MAX_AMOUNT / volume;
    }

    public long nextSimulationTick(long tick) {
        return Math.multiplyExact(Math.floorDiv(tick, simulationIntervalTicks) + 1,
            (long) simulationIntervalTicks);
    }

    public long nextProducerTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("tick must be non-negative");
        long intervalNumerator = Math.multiplyExact(
            (long) AtmosphereGridLayout.PRODUCER_INTERVAL_TICKS, simulationIntervalTicks);
        long intervalDenominator = AtmosphereGridLayout.SIMULATION_INTERVAL_TICKS;
        long elapsedIntervals = Math.floorDiv(
            Math.multiplyExact(tick, intervalDenominator), intervalNumerator);
        long nextNumerator = Math.multiplyExact(elapsedIntervals + 1, intervalNumerator);
        return Math.floorDiv(Math.addExact(nextNumerator, intervalDenominator - 1), intervalDenominator);
    }
}
