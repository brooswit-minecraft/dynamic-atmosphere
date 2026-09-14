package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

/** Optional Create adapter. Its public API and outer class have no Create type references. */
public final class ForgeFanTransport {
    private ForgeFanTransport() { }

    public static List<BlockPos> activeFanPositions(LevelChunk chunk) {
        if (ModList.get() == null || !ModList.get().isLoaded("create")) return List.of();
        return CreateAccess.activeFanPositions(chunk);
    }

    /**
     * Call once per fan-containing source cell in an independent fan pass, using its minimum block position.
     * Returns one request per rotating fan inside the half-open cell bounds, in X/Z/Y coordinate order.
     * The caller moves material one cell in request.direction(), bounded by remaining source material,
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
        return CreateAccess.collect(level, cellMin, cellSize, rpmCoefficient);
    }

    public record Request(Direction direction, int amount) {
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
        private static List<BlockPos> activeFanPositions(LevelChunk chunk) {
            return chunk.getBlockEntities().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof
                    com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity fan
                    && !fan.isRemoved() && Float.isFinite(fan.getSpeed()) && fan.getSpeed() != 0)
                .map(entry -> entry.getKey().immutable()).sorted().toList();
        }

        private static List<Request> collect(ServerLevel level, BlockPos min, int size, double coefficient) {
            List<LocatedRequest> requests = new ArrayList<>();
            int maxChunkX = Math.floorDiv(min.getX() + size - 1, 16);
            int maxChunkZ = Math.floorDiv(min.getZ() + size - 1, 16);
            for (int chunkX = Math.floorDiv(min.getX(), 16); chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = Math.floorDiv(min.getZ(), 16); chunkZ <= maxChunkZ; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) continue;
                    for (var entry : chunk.getBlockEntities().entrySet()) {
                        BlockPos pos = entry.getKey();
                        if (!contains(min, size, pos)) continue;
                        BlockEntity entity = entry.getValue();
                        if (!(entity instanceof com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity fan)
                            || fan.isRemoved()) continue;
                        // Verified against Create 6.0.10: getSpeed() is zero when overstressed/frozen.
                        int amount = AtmosphereFanTransport.requestedAmount(
                            fan.getSpeed(), coefficient, AtmosphereGrid.MAX_AMOUNT);
                        if (amount > 0) {
                            // Facing is intentional: reverse RPM changes neither the target nor the quantity.
                            requests.add(new LocatedRequest(pos.immutable(),
                                new Request(fan.getAirflowOriginSide(), amount)));
                        }
                    }
                }
            }
            return orderedRequests(requests);
        }
    }
}
