package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;

/** Additional atmospheric materials introduced after the dedicated Vapor and Smoke formats. */
public enum AtmosphereMaterial {
    DUST("dust"),
    OBSIDIAN_POWDER("obsidian_powder"),
    VOID_GAS("void_gas"),
    EXHAUST("exhaust"),
    SLIME("slime"),
    GLOW_GAS("glow_gas"),
    NETHER_FOG("nether_fog");

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

    AtmosphereMaterial(String id) {
        this.id = id;
    }

    public String id() { return id; }
    /** Every material shares the grid's cell size; there is no per-material override. */
    public int cellSize() { return AtmosphereGridLayout.CELL_SIZE; }
    public int simulationIntervalTicks() { return DynamicAtmosphereServerConfig.snapshot().runtime().simulationIntervalTicks(); }

    public int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, cellSize());
    }

    public int chunkCoordinate(int cellCoordinate) {
        return Math.floorDiv(cellCoordinate, 16 / cellSize());
    }

    public int capacityForAirBlocks(int airBlocks) {
        int volume = cellSize() * cellSize() * cellSize();
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

    /**
     * MINECRAFT-1 (B1/B2/B3): the preferred resting height ("home") this material's cells drift
     * toward. Dust, Obsidian Powder, Void Gas, Exhaust and Nether Fog rest on the dimension's own
     * bottom (structural, not configurable); Slime rests at an absolute, configurable height
     * clamped to the dimension's build range; Glow Gas rests at a literal Y=0, not configurable.
     */
    public int home(ServerLevel level) {
        var snapshot = DynamicAtmosphereServerConfig.snapshot();
        return switch (this) {
            case DUST, OBSIDIAN_POWDER, VOID_GAS, EXHAUST, NETHER_FOG -> level.getMinBuildHeight();
            case SLIME -> Math.clamp(snapshot.slime().preferredHeight(),
                level.getMinBuildHeight(), level.getMaxBuildHeight());
            case GLOW_GAS -> Math.clamp(0, level.getMinBuildHeight(), level.getMaxBuildHeight());
        };
    }
}
