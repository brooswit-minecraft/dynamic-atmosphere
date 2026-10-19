package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

/** Optional Create adapter. Its public API and outer class have no Create type references. */
public final class ForgeFanTransport {
    private ForgeFanTransport() { }

    /**
     * Call once per fan-containing source cell in an independent fan pass, using its minimum block position.
     * Returns one request per rotating fan inside the half-open cell bounds, in X/Z/Y coordinate order.
     * The caller performs intake and output around request.direction(), reversing these for negative RPM,
     * bounded by remaining source material,
     * destination empty space, loaded/readable destination state, and its liquid/bedrock edge rules.
     * Physical capacity may be exceeded to create pressure.
     * Read the coefficient from a fresh server config snapshot at the start of the processing tick.
     * No chunks or block entities are loaded, no grid is mutated, and requests are not cached across ticks.
     */
    public static List<Request> collect(ServerLevel level, BlockPos cellMin, int cellSize,
                                        double rpmCoefficient) {
        if (cellSize < 1 || cellSize > 16) {
            throw new IllegalArgumentException("Fan cell size must be between 1 and 16 blocks");
        }
        ModList mods = ModList.get();
        if (!Double.isFinite(rpmCoefficient) || rpmCoefficient <= 0
            || mods == null || !mods.isLoaded("create")) {
            return List.of();
        }
        return ChunkFanCache.forLevel(level, rpmCoefficient).requestsInCell(cellMin, cellSize);
    }

    /**
     * Scans each chunk's active Create fans at most once per cache instance, no matter how many
     * times active-fan positions or cell/grid requests are asked for that chunk. Construct one cache
     * per pass-local chunk iteration (never reused across ticks) and reuse it across position discovery,
     * Vapor, Smoke and every material grid instead of letting each independently re-scan the same
     * block entities.
     */
    public static final class ChunkFanCache {
        private final BiFunction<Integer, Integer, ChunkScan> scanner;
        private final Map<Long, ChunkScan> scanned = new HashMap<>();

        ChunkFanCache(BiFunction<Integer, Integer, ChunkScan> scanner) {
            this.scanner = scanner;
        }

        public static ChunkFanCache forLevel(ServerLevel level, double rpmCoefficient) {
            return new ChunkFanCache((chunkX, chunkZ) -> scanChunk(level, chunkX, chunkZ, rpmCoefficient));
        }

        /** Active fan positions in one chunk, same filter and order as before: not removed, finite nonzero speed. */
        public List<BlockPos> positionsInChunk(int chunkX, int chunkZ) {
            return chunkScan(chunkX, chunkZ).positions();
        }

        /** Requests inside the half-open cell, in the same deterministic X/Z/Y order as before. */
        public List<Request> requestsInCell(BlockPos cellMin, int size) {
            if (size < 1 || size > 16) {
                throw new IllegalArgumentException("Fan cell size must be between 1 and 16 blocks");
            }
            int minChunkX = Math.floorDiv(cellMin.getX(), 16);
            int maxChunkX = Math.floorDiv(cellMin.getX() + size - 1, 16);
            int minChunkZ = Math.floorDiv(cellMin.getZ(), 16);
            int maxChunkZ = Math.floorDiv(cellMin.getZ() + size - 1, 16);
            List<LocatedRequest> located = new ArrayList<>();
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    for (var entry : chunkScan(chunkX, chunkZ).requests()) {
                        if (contains(cellMin, size, entry.pos())) located.add(entry);
                    }
                }
            }
            return orderedRequests(located);
        }

        private ChunkScan chunkScan(int chunkX, int chunkZ) {
            return scanned.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), key -> scanner.apply(chunkX, chunkZ));
        }
    }

    /** One chunk's active fan positions and material-transport requests, from a single block-entity scan. */
    record ChunkScan(List<BlockPos> positions, List<LocatedRequest> requests) {
        static final ChunkScan EMPTY = new ChunkScan(List.of(), List.of());
    }

    private static ChunkScan scanChunk(ServerLevel level, int chunkX, int chunkZ, double rpmCoefficient) {
        ModList mods = ModList.get();
        if (!Double.isFinite(rpmCoefficient) || rpmCoefficient <= 0
            || mods == null || !mods.isLoaded("create")) {
            return ChunkScan.EMPTY;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) return ChunkScan.EMPTY;
        return CreateAccess.scanChunk(chunk, rpmCoefficient);
    }

    public record Request(Direction direction, int amount, boolean reverse) {
        public Request {
            Objects.requireNonNull(direction, "direction");
            if (amount < 1 || amount > AtmosphereGrid.MAX_AMOUNT) {
                throw new IllegalArgumentException("Fan amount must be within cell capacity");
            }
        }
    }

    static boolean contains(BlockPos min, int size, BlockPos pos) {
        return pos.getX() >= min.getX() && (long) pos.getX() < (long) min.getX() + size
            && pos.getY() >= min.getY() && (long) pos.getY() < (long) min.getY() + size
            && pos.getZ() >= min.getZ() && (long) pos.getZ() < (long) min.getZ() + size;
    }

    record LocatedRequest(BlockPos pos, Request request) { }

    static List<Request> orderedRequests(List<LocatedRequest> requests) {
        return requests.stream().sorted(Comparator
            .comparingInt((LocatedRequest request) -> request.pos().getX())
            .thenComparingInt(request -> request.pos().getZ())
            .thenComparingInt(request -> request.pos().getY()))
            .map(LocatedRequest::request).toList();
    }

    // JVM resolution of Create classes is deferred until the mod-presence guard above passes.
    private static final class CreateAccess {
        private static ChunkScan scanChunk(LevelChunk chunk, double coefficient) {
            List<BlockPos> positions = new ArrayList<>();
            List<LocatedRequest> requests = new ArrayList<>();
            for (var entry : chunk.getBlockEntities().entrySet()) {
                BlockEntity entity = entry.getValue();
                if (!(entity instanceof com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity fan)
                    || fan.isRemoved()) continue;
                // Verified against Create 6.0.10: getSpeed() is zero when overstressed/frozen.
                if (Float.isFinite(fan.getSpeed()) && fan.getSpeed() != 0) positions.add(entry.getKey().immutable());
                int amount = AtmosphereFanTransport.requestedAmount(
                    fan.getSpeed(), coefficient, AtmosphereGrid.MAX_AMOUNT);
                if (amount > 0) {
                    requests.add(new LocatedRequest(entry.getKey().immutable(),
                        new Request(fan.getAirflowOriginSide(), amount, fan.getSpeed() < 0)));
                }
            }
            positions.sort(null);
            return new ChunkScan(List.copyOf(positions), requests);
        }
    }
}
