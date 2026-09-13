package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;

/**
 * Bounded server-side atmospheric grid and delivery adapter. Cells are fixed
 * Four-block cubes with local sources and conservative spreading through available air space.
 */
final class ForgeAtmospherePrototype {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SYNC_INTERVAL = 20;
    private static final int FULL_SNAPSHOT_INTERVAL = 200;
    private static final int HIGH_TERRAIN_ABOVE_SEA = 24;
    private static final int CLOUD_PARTICLE_CAP = 16;
    private static final int DEMO_PARTICLE_CAP = 24;
    private static final int MAX_CHUNK_IMPORTS_PER_TICK = 8;
    private static final int WATER_EMISSION_PER_DEPTH = 20;
    private static final int HIGH_TERRAIN_EMISSION = 40;
    private static final int RAIN_EMISSION = 40;
    private static final int MAX_WATER_DEPTH = 8;
    private static final int SOURCE_RADIUS_MULTIPLIER = 8;
    private static final int[][] SAMPLE_OFFSETS = {
        {0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}, {8, 8}, {-8, -8}, {8, -8}
    };
    private static final int[][] DEMO_CELL_OFFSETS = {
        {0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private long serverTicks;
    private long automaticPasses;
    private long particlesSent;
    private long gridEmissions;
    private long rainEmissions;
    private long darkGroundEmissions;
    private long materialMoved;
    private int blockedOverflow;
    private int sourcesProcessed;
    private boolean workRemaining;
    private boolean searchLimited;
    private long pressureBreaks;
    private long pressureSearchLimits;
    private int pressureAttempts;
    private int importsRemaining = MAX_CHUNK_IMPORTS_PER_TICK;
    private long payloadsSent;
    private int playersLastPass;
    private UUID worldId;
    private final AtmosphereGrid<ResourceKey<Level>> grid = new AtmosphereGrid<>();
    private final Map<ChunkKey, ChunkState> chunks = new HashMap<>();
    private final Map<ChunkKey, LevelChunk> pendingLoads = new LinkedHashMap<>();
    private final AtmosphereSyncPlanner<UUID, ResourceKey<Level>> syncPlanner =
        new AtmosphereSyncPlanner<>(AtmosphereGridPayload.MAX_CELLS_PER_PAYLOAD);

    void onServerTick(ServerTickEvent.Post event) {
        serverTicks++;
        importsRemaining = MAX_CHUNK_IMPORTS_PER_TICK;
        importPendingChunks(event.getServer());
        if (AtmosphereGridLayout.isSimulationTick(serverTicks)) {
            pressureAttempts = 0;
            sampleSources(event.getServer());
        }
        advanceSimulation(event.getServer());
        flushDirtyChunks();
        if (serverTicks % SYNC_INTERVAL == 0) {
            syncPlayers(event.getServer(), serverTicks % FULL_SNAPSHOT_INTERVAL == 0);
        }
        flushDirtyChunks();
    }

    void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            // Load may precede FULL promotion; defer all world queries to the tick.
            Runnable enqueue = () -> pendingLoads.put(chunkKey(level, chunk), chunk);
            if (level.getServer().isSameThread()) {
                enqueue.run();
            } else {
                level.getServer().execute(enqueue);
            }
        }
    }

    void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Runnable unload = () -> {
                ChunkKey key = chunkKey(level, chunk);
                pendingLoads.remove(key, chunk);
                ChunkState state = chunks.get(key);
                if (state != null && state.chunk == chunk) {
                    flushDirtyChunks();
                    chunks.remove(key);
                    state.cells.keySet().forEach(grid::remove);
                }
            };
            if (level.getServer().isSameThread()) {
                unload.run();
            } else {
                level.getServer().execute(unload);
            }
        }
    }

    void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlanner.disconnect(player.getUUID());
        }
    }

    void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlanner.reset(player.getUUID());
        }
    }

    void onServerStopped(ServerStoppedEvent event) {
        serverTicks = 0;
        automaticPasses = 0;
        particlesSent = 0;
        gridEmissions = 0;
        rainEmissions = 0;
        darkGroundEmissions = 0;
        materialMoved = 0;
        blockedOverflow = 0;
        sourcesProcessed = 0;
        workRemaining = false;
        searchLimited = false;
        pressureBreaks = 0;
        pressureSearchLimits = 0;
        pressureAttempts = 0;
        payloadsSent = 0;
        playersLastPass = 0;
        worldId = null;
        grid.clear();
        grid.drainDirtyKeys();
        chunks.clear();
        pendingLoads.clear();
        syncPlanner.clear();
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dynamicatmosphere")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("demo").executes(context -> demo(context.getSource()))));
    }

    private int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "Dynamic Atmosphere grid: active; cellSize=" + AtmosphereGrid.CELL_SIZE
                + ", cells=" + grid.size()
                + ", loadedChunks=" + chunks.size()
                + ", pendingImports=" + pendingLoads.size()
                + ", sampleInterval=" + AtmosphereGridLayout.simulationIntervalTicks(AtmosphereGridLayout.CELL_SIZE)
                + ", sourceRadius=" + 12 * SOURCE_RADIUS_MULTIPLIER
                + ", syncInterval=" + SYNC_INTERVAL
                + ", fullSnapshotInterval=" + FULL_SNAPSHOT_INTERVAL
                + ", passes=" + automaticPasses
                + ", emissions=" + gridEmissions
                + ", rainEmissions=" + rainEmissions
                + ", darkGroundEmissions=" + darkGroundEmissions
                + ", materialMoved=" + materialMoved
                + ", blockedOverflow=" + blockedOverflow
                + ", sourcesProcessed=" + sourcesProcessed
                + ", workRemaining=" + workRemaining
                + ", searchLimited=" + searchLimited
                + ", pressureBreaks=" + pressureBreaks
                + ", pressureAttempts=" + pressureAttempts + "/" + ForgeAtmospherePressure.MAX_BREAKS_PER_PASS
                + ", pressureSearchLimits=" + pressureSearchLimits
                + ", payloads=" + payloadsSent
                + ", particles=" + particlesSent
                + ", playersLastPass=" + playersLastPass
                + ". Server authoritative; no passive decay; six-neighbor spreading with air-space capacity."), false);
        return 1;
    }

    private int demo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AtmosphereGrid.CellKey<ResourceKey<Level>> center = cellKey(player.serverLevel(), player.blockPosition());
        int populated = 0;
        for (int[] offset : DEMO_CELL_OFFSETS) {
            AtmosphereGrid.CellKey<ResourceKey<Level>> key = new AtmosphereGrid.CellKey<>(
                center.dimension(), center.x() + offset[0], center.y() + offset[1], center.z() + offset[2]);
            int capacity = capacityAt(player.serverLevel(), key);
            if (capacity > 0 && grid.set(key, AtmosphereGrid.MAX_AMOUNT, serverTicks, capacity)) {
                populated++;
            }
        }
        sendCloudParticles(
            player, player.getX(), player.getY() + 1.2, player.getZ(), DEMO_PARTICLE_CAP, 2.5, 0.7, 2.5, 0.02);
        syncPlanner.reset(player.getUUID());
        flushDirtyChunks();
        syncPlayer(player);
        flushDirtyChunks();
        int populatedCells = populated;
        source.sendSuccess(() -> Component.literal(
            "Dynamic Atmosphere demo filled " + populatedCells
                + " loaded grid cells and emitted " + DEMO_PARTICLE_CAP + " vanilla cloud particles."), false);
        return populated;
    }

    private void sampleSources(MinecraftServer server) {
        automaticPasses++;
        playersLastPass = 0;
        Set<SourceKey> emittedSources = new HashSet<>();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            playersLastPass++;
            sampleAutomatic(player, emittedSources);
        }
    }

    private void advanceSimulation(MinecraftServer server) {
        var capacities = new HashMap<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer>();
        ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacityAt = key ->
            capacities.computeIfAbsent(key, candidate -> capacityAt(server.getLevel(candidate.dimension()), candidate));
        var spread = grid.spread(serverTicks, capacityAt);
        materialMoved += spread.moved();
        blockedOverflow = spread.blockedOverflow();
        sourcesProcessed = spread.sourcesProcessed();
        workRemaining = spread.workRemaining();
        searchLimited = spread.searchLimited();
        if (!spread.mayBreakForPressure() || spread.searchLimited() || spread.workRemaining()) {
            return;
        }
        for (var blocked : spread.blockedCells()) {
            if (!blocked.pressureEligible()) {
                continue;
            }
            var source = blocked.key();
            ServerLevel level = server.getLevel(source.dimension());
            while (level != null && pressureAttempts < ForgeAtmospherePressure.MAX_BREAKS_PER_PASS) {
                // A resumed search cannot authorize destruction against changed world state.
                pressureAttempts++;
                capacities.clear();
                var fresh = grid.redistributeOverflow(serverTicks, capacityAt, List.of(source));
                materialMoved += fresh.moved();
                blockedOverflow = fresh.blockedOverflow();
                searchLimited |= fresh.searchLimited();
                if (fresh.searchLimited() || fresh.workRemaining() || !fresh.blockedCells().stream()
                    .anyMatch(cell -> cell.key().equals(source) && cell.pressureEligible())) {
                    break;
                }
                var current = grid.get(source);
                int capacity = capacityAt.applyAsInt(source);
                if (current == null || capacity < 0 || current.amount() <= capacity) {
                    break;
                }
                var pressure = ForgeAtmospherePressure.breakForPressure(level, source,
                    key -> capacityAt.applyAsInt(key) >= 0);
                if (pressure.searchLimited()) {
                    pressureSearchLimits++;
                }
                if (pressure.broken().isEmpty()) {
                    break;
                }
                var broken = pressure.broken().orElseThrow();
                pressureBreaks++;
                capacities.clear();
                int newCapacity = capacityAt.applyAsInt(broken.cell());
                grid.emit(broken.cell(), broken.addedMaterial(), serverTicks, newCapacity);
                Set<AtmosphereGrid.CellKey<ResourceKey<Level>>> overflowSources = new LinkedHashSet<>();
                overflowSources.add(source);
                overflowSources.add(broken.cell());
                var retry = grid.redistributeOverflow(serverTicks, capacityAt, overflowSources);
                materialMoved += retry.moved();
                blockedOverflow = retry.blockedOverflow();
                searchLimited |= retry.searchLimited();
                if (retry.searchLimited() || !retry.blockedCells().stream()
                    .anyMatch(cell -> cell.key().equals(source) && cell.pressureEligible())) {
                    break;
                }
            }
        }
    }

    private void sampleAutomatic(ServerPlayer player, Set<SourceKey> emittedSources) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int sent = 0;

        for (int[] offset : SAMPLE_OFFSETS) {
            int x = center.getX() + offset[0] * SOURCE_RADIUS_MULTIPLIER;
            int z = center.getZ() + offset[1] * SOURCE_RADIUS_MULTIPLIER;
            BlockPos loadedProbe = new BlockPos(x, center.getY(), z);
            if (!level.hasChunkAt(loadedProbe)) {
                continue;
            }

            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surface = new BlockPos(x, surfaceY - 1, z);
            if (!level.hasChunkAt(surface)) {
                continue;
            }

            sampleLandingSources(level, loadedProbe, emittedSources);
            if (level.getFluidState(surface).is(FluidTags.WATER)) {
                int depth = sampleLoadedWaterDepth(level, surface);
                AtmosphereGrid.CellKey<ResourceKey<Level>> cell =
                    cellKey(level, new BlockPos(x, surfaceY, z));
                SourceKey source = new SourceKey(SourceKind.WATER, level.dimension(), surface.immutable());
                emitSource(level, emittedSources, source, cell, depth * WATER_EMISSION_PER_DEPTH);
            } else if (surfaceY >= level.getSeaLevel() + HIGH_TERRAIN_ABOVE_SEA) {
                AtmosphereGrid.CellKey<ResourceKey<Level>> cell =
                    cellKey(level, new BlockPos(x, surfaceY + 5, z));
                SourceKey source = new SourceKey(SourceKind.HIGH_TERRAIN, level.dimension(), surface.immutable());
                emitSource(level, emittedSources, source, cell, HIGH_TERRAIN_EMISSION);
                if (sent < CLOUD_PARTICLE_CAP) {
                    int count = Math.min(4, CLOUD_PARTICLE_CAP - sent);
                    sent += sendCloudParticles(
                        player, x + 0.5, surfaceY + 5.0, z + 0.5, count, 3.0, 0.8, 3.0, 0.015);
                }
            }
        }
    }

    private void sampleLandingSources(ServerLevel level, BlockPos loadedProbe, Set<SourceKey> emittedSources) {
        BlockPos landing = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, loadedProbe);
        BlockPos surface = landing.below();
        if (!level.isInWorldBounds(landing)
            || !level.isInWorldBounds(surface)
            || !level.hasChunkAt(landing)
            || level.getBlockState(surface).isAir()) {
            return;
        }

        AtmosphereGrid.CellKey<ResourceKey<Level>> cell = cellKey(level, landing);
        SourceKey rainSource = new SourceKey(SourceKind.RAIN, level.dimension(), landing.immutable());
        if (level.isRainingAt(landing)
            && emitSource(level, emittedSources, rainSource, cell, RAIN_EMISSION)) {
            rainEmissions++;
        }

        int darkGroundEmission = AtmosphereSourceStrength.lightToEmission(
            level.getMaxLocalRawBrightness(landing));
        SourceKey darkGroundSource = new SourceKey(
            SourceKind.DARK_GROUND, level.dimension(), landing.immutable());
        if (darkGroundEmission > 0
            && level.canSeeSky(landing)
            && level.getFluidState(surface).isEmpty()
            && emitSource(level, emittedSources, darkGroundSource, cell, darkGroundEmission)) {
            darkGroundEmissions++;
        }
    }

    private boolean emitSource(
        ServerLevel level,
        Set<SourceKey> emittedSources,
        SourceKey source,
        AtmosphereGrid.CellKey<ResourceKey<Level>> cell,
        int amount
    ) {
        if (emittedSources.contains(source)) {
            return false;
        }
        int capacity = capacityAt(level, cell);
        if (capacity >= 0 && AtmosphereGrid.emitSourceOnce(
            emittedSources, source, grid, cell, amount, serverTicks, capacity)) {
            gridEmissions++;
            return true;
        }
        return false;
    }

    private int sampleLoadedWaterDepth(ServerLevel level, BlockPos surface) {
        int depth = 0;
        for (int offset = 0; offset < MAX_WATER_DEPTH; offset++) {
            BlockPos sample = surface.below(offset);
            if (!level.hasChunkAt(sample) || !level.getFluidState(sample).is(FluidTags.WATER)) {
                break;
            }
            depth++;
        }
        return Math.max(1, depth);
    }

    private void syncPlayers(MinecraftServer server, boolean forceSnapshot) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        var capacities = new HashMap<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer>();
        syncPlanner.retainPlayers(players.stream().map(ServerPlayer::getUUID).toList());
        for (ServerPlayer player : players) {
            if (forceSnapshot) {
                syncPlanner.reset(player.getUUID());
            }
            syncPlayer(player, capacities);
        }
    }

    private void syncPlayer(ServerPlayer player) {
        syncPlayer(player, new HashMap<>());
    }

    private void syncPlayer(ServerPlayer player,
        Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer> capacities) {
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        AtmosphereGrid.CellKey<ResourceKey<Level>> playerCell = cellKey(player.serverLevel(), player.blockPosition());
        var nearby = new ArrayList<AtmosphereGrid.Cell<ResourceKey<Level>>>();
        var authoritativeChunks = new ArrayList<AtmosphereSyncPlanner.Chunk>();
        // Use Minecraft's subscription, including changes to client/server view distance.
        // ensureImported only reads already loaded chunks and retains its per-tick budget.
        player.getChunkTrackingView().forEach(chunk -> {
            ChunkState state = ensureImported(player.serverLevel(), chunk.x, chunk.z);
            if (state == null || !state.readable) {
                return;
            }
            authoritativeChunks.add(new AtmosphereSyncPlanner.Chunk(chunk.x, chunk.z));
            for (var key : state.cells.keySet()) {
                var cell = grid.get(key);
                if (cell != null) {
                    nearby.add(cell);
                }
            }
        });
        nearby.sort(Comparator.comparingLong(cell -> cellDistanceSquared(playerCell, cell.key())));
        var visible = new ArrayList<AtmosphereSyncPlanner.Cell>();
        for (var cell : nearby) {
            int capacity = capacities.computeIfAbsent(cell.key(), key -> capacityAt(player.serverLevel(), key));
            if (capacity >= 0) {
                visible.add(new AtmosphereSyncPlanner.Cell(
                    cell.key().x(), cell.key().y(), cell.key().z(), cell.amount(), capacity));
            }
        }

        for (AtmosphereSyncPlanner.Update<ResourceKey<Level>> update
            : syncPlanner.plan(player.getUUID(), dimension, authoritativeChunks, visible)) {
            List<AtmosphereGridPayload.Chunk> chunks = update.authoritativeChunks().stream()
                .map(chunk -> new AtmosphereGridPayload.Chunk(chunk.x(), chunk.z()))
                .toList();
            List<AtmosphereGridPayload.Cell> cells = update.cells().stream()
                .map(cell -> new AtmosphereGridPayload.Cell(cell.x(), cell.y(), cell.z(), cell.amount(), cell.capacity()))
                .toList();
            PacketDistributor.sendToPlayer(player, new AtmosphereGridPayload(
                update.dimension().location(), worldId(player.serverLevel().getServer()),
                update.reset(), update.snapshotEnd(), chunks, cells));
            payloadsSent++;
        }
    }

    private UUID worldId(MinecraftServer server) {
        if (worldId == null) {
            worldId = AtmosphereWorldIdentity.get(server);
        }
        return worldId;
    }

    /** Negative means unknown, including loaded chunks whose import is budget-deferred. */
    private int capacityAt(ServerLevel level, AtmosphereGrid.CellKey<ResourceKey<Level>> key) {
        if (level == null || !level.dimension().equals(key.dimension())) {
            return -1;
        }
        long originY = (long) key.y() * AtmosphereGridLayout.CELL_SIZE;
        if (originY >= level.getMaxBuildHeight() || originY + AtmosphereGridLayout.CELL_SIZE <= level.getMinBuildHeight()) {
            return 0;
        }
        ChunkState state = ensureImported(level, AtmosphereGridLayout.chunkCoordinate(key.x()),
            AtmosphereGridLayout.chunkCoordinate(key.z()));
        if (state == null || !state.readable) {
            return -1;
        }
        int size = AtmosphereGrid.CELL_SIZE;
        int air = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    pos.set(key.x() * size + x, key.y() * size + y, key.z() * size + z);
                    if (level.isInWorldBounds(pos) && state.chunk.getBlockState(pos).isAir()) {
                        air++;
                    }
                }
            }
        }
        return AtmosphereGridLayout.capacityForAirBlocks(air);
    }

    private void importPendingChunks(MinecraftServer server) {
        var deferred = new LinkedHashMap<ChunkKey, LevelChunk>();
        var iterator = pendingLoads.entrySet().iterator();
        int examined = 0;
        while (iterator.hasNext() && examined++ < MAX_CHUNK_IMPORTS_PER_TICK && importsRemaining > 0) {
            var entry = iterator.next();
            ChunkKey key = entry.getKey();
            LevelChunk expected = entry.getValue();
            iterator.remove();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            if (level.getChunkSource().getChunkNow(key.x(), key.z()) == null) {
                deferred.put(key, expected);
                continue;
            }
            ensureImported(level, key.x(), key.z());
        }
        pendingLoads.putAll(deferred);
    }

    private ChunkState ensureImported(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        ChunkKey key = new ChunkKey(level.dimension(), chunkX, chunkZ);
        ChunkState previous = chunks.get(key);
        if (previous != null && previous.chunk == chunk) {
            return previous;
        }
        if (importsRemaining <= 0) {
            pendingLoads.put(key, chunk);
            return null;
        }
        importsRemaining--;
        if (previous != null) {
            // Normal unloading already persisted it; never merge two chunk incarnations.
            flushDirtyChunks();
            previous.cells.keySet().forEach(grid::remove);
        }
        ChunkState state = new ChunkState(chunk);
        chunks.put(key, state);
        pendingLoads.remove(key, chunk);
        try {
            for (AtmosphereChunkData.Cell cell : ForgeAtmosphereStorage.read(chunk)) {
                var cellKey = new AtmosphereGrid.CellKey<>(level.dimension(), cell.x(), cell.y(), cell.z());
                state.cells.put(cellKey, cell);
                grid.restore(new AtmosphereGrid.Cell<>(cellKey, cell.amount(), 0, Long.MIN_VALUE, serverTicks), serverTicks);
            }
        } catch (IllegalStateException exception) {
            state.readable = false;
            LOGGER.warn("Skipping atmospheric simulation for chunk {} in {}: {}", chunk.getPos(),
                level.dimension().location(), exception.getMessage());
        }
        return state;
    }

    private void flushDirtyChunks() {
        Set<ChunkKey> dirtyChunks = new LinkedHashSet<>();
        for (var key : grid.drainDirtyKeys()) {
            ChunkKey chunkKey = new ChunkKey(key.dimension(), AtmosphereGridLayout.chunkCoordinate(key.x()),
                AtmosphereGridLayout.chunkCoordinate(key.z()));
            ChunkState state = chunks.get(chunkKey);
            if (state == null || !state.readable) {
                continue;
            }
            var cell = grid.get(key);
            if (cell == null) {
                state.cells.remove(key);
            } else {
                state.cells.put(key, new AtmosphereChunkData.Cell(key.x(), key.y(), key.z(), cell.amount()));
            }
            dirtyChunks.add(chunkKey);
        }
        for (ChunkKey key : dirtyChunks) {
            ChunkState state = chunks.get(key);
            try {
                ForgeAtmosphereStorage.write(state.chunk, List.copyOf(state.cells.values()));
            } catch (IllegalStateException exception) {
                state.readable = false;
                state.cells.keySet().forEach(grid::remove);
                LOGGER.warn("Preserving unreadable atmospheric data for chunk {} in {}: {}",
                    state.chunk.getPos(), key.dimension().location(), exception.getMessage());
            }
        }
    }

    private static ChunkKey chunkKey(ServerLevel level, LevelChunk chunk) {
        return new ChunkKey(level.dimension(), chunk.getPos().x, chunk.getPos().z);
    }

    private record ChunkKey(ResourceKey<Level> dimension, int x, int z) {
    }

    private static final class ChunkState {
        private final LevelChunk chunk;
        private final Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, AtmosphereChunkData.Cell> cells = new LinkedHashMap<>();
        private boolean readable = true;

        private ChunkState(LevelChunk chunk) {
            this.chunk = chunk;
        }
    }

    private long cellDistanceSquared(
        AtmosphereGrid.CellKey<ResourceKey<Level>> first,
        AtmosphereGrid.CellKey<ResourceKey<Level>> second
    ) {
        long dy = (long) first.y() - second.y();
        return horizontalCellDistanceSquared(first, second) + dy * dy;
    }

    private long horizontalCellDistanceSquared(
        AtmosphereGrid.CellKey<ResourceKey<Level>> first,
        AtmosphereGrid.CellKey<ResourceKey<Level>> second
    ) {
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }

    private AtmosphereGrid.CellKey<ResourceKey<Level>> cellKey(ServerLevel level, BlockPos pos) {
        return new AtmosphereGrid.CellKey<>(
            level.dimension(),
            AtmosphereGrid.cellCoordinate(pos.getX()),
            AtmosphereGrid.cellCoordinate(pos.getY()),
            AtmosphereGrid.cellCoordinate(pos.getZ())
        );
    }

    private int sendCloudParticles(
        ServerPlayer player,
        double x,
        double y,
        double z,
        int count,
        double spreadX,
        double spreadY,
        double spreadZ,
        double speed
    ) {
        if (player.serverLevel().sendParticles(
            player, ParticleTypes.CLOUD, false, x, y, z, count, spreadX, spreadY, spreadZ, speed)) {
            particlesSent += count;
            return count;
        }
        return 0;
    }

    private enum SourceKind {
        WATER,
        HIGH_TERRAIN,
        RAIN,
        DARK_GROUND
    }

    private record SourceKey(SourceKind kind, ResourceKey<Level> dimension, BlockPos pos) {
    }
}
