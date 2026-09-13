package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded server-side atmospheric grid and delivery adapter. Cells are fixed
 * Four-block cubes; this first version has local sources and decay but no flow.
 */
final class ForgeAtmospherePrototype {

    private static final int SAMPLE_INTERVAL = 100;
    private static final int SYNC_INTERVAL = 20;
    private static final int FULL_SNAPSHOT_INTERVAL = 200;
    private static final int HIGH_TERRAIN_ABOVE_SEA = 24;
    private static final int CLOUD_PARTICLE_CAP = 16;
    private static final int DEMO_PARTICLE_CAP = 24;
    private static final int MAX_GRID_CELLS = 1024;
    private static final int MAX_VISIBLE_CELLS_PER_PLAYER = 256;
    private static final int SUBSCRIPTION_RADIUS_BLOCKS = 64;
    private static final int GRID_RADIUS_CELLS = SUBSCRIPTION_RADIUS_BLOCKS / AtmosphereGridLayout.CELL_SIZE;
    private static final int GRID_VERTICAL_RADIUS_CELLS = SUBSCRIPTION_RADIUS_BLOCKS / AtmosphereGridLayout.CELL_SIZE;
    private static final int GRID_DECAY_PER_PASS = 10;
    private static final int WATER_EMISSION_PER_DEPTH = 20;
    private static final int HIGH_TERRAIN_EMISSION = 40;
    private static final int RAIN_EMISSION = 40;
    private static final int MAX_WATER_DEPTH = 8;
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
    private long gridCellsRemoved;
    private long payloadsSent;
    private int playersLastPass;
    private final AtmosphereGrid<ResourceKey<Level>> grid =
        new AtmosphereGrid<>(MAX_GRID_CELLS, GRID_DECAY_PER_PASS);
    private final AtmosphereSyncPlanner<UUID, ResourceKey<Level>> syncPlanner =
        new AtmosphereSyncPlanner<>(AtmosphereGridPayload.MAX_CELLS_PER_PAYLOAD);

    void onServerTick(ServerTickEvent.Post event) {
        serverTicks++;
        if (serverTicks % SAMPLE_INTERVAL == 0) {
            sampleAndAdvance(event.getServer());
        }
        if (serverTicks % SYNC_INTERVAL == 0) {
            syncPlayers(event.getServer(), serverTicks % FULL_SNAPSHOT_INTERVAL == 0);
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
        gridCellsRemoved = 0;
        payloadsSent = 0;
        playersLastPass = 0;
        grid.clear();
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
                + ", cells=" + grid.size() + "/" + MAX_GRID_CELLS
                + ", sampleInterval=" + SAMPLE_INTERVAL
                + ", syncInterval=" + SYNC_INTERVAL
                + ", fullSnapshotInterval=" + FULL_SNAPSHOT_INTERVAL
                + ", passes=" + automaticPasses
                + ", emissions=" + gridEmissions
                + ", rainEmissions=" + rainEmissions
                + ", darkGroundEmissions=" + darkGroundEmissions
                + ", removed=" + gridCellsRemoved
                + ", payloads=" + payloadsSent
                + ", particles=" + particlesSent
                + ", playersLastPass=" + playersLastPass
                + ". Server authoritative; local decay only, no intercell flow or world weather changes."), false);
        return 1;
    }

    private int demo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AtmosphereGrid.CellKey<ResourceKey<Level>> center = cellKey(player.serverLevel(), player.blockPosition());
        int populated = 0;
        for (int[] offset : DEMO_CELL_OFFSETS) {
            AtmosphereGrid.CellKey<ResourceKey<Level>> key = new AtmosphereGrid.CellKey<>(
                center.dimension(), center.x() + offset[0], center.y() + offset[1], center.z() + offset[2]);
            if (isCellLoaded(player.serverLevel(), key) && grid.set(key, AtmosphereGrid.MAX_AMOUNT, serverTicks)) {
                populated++;
            }
        }
        sendCloudParticles(
            player, player.getX(), player.getY() + 1.2, player.getZ(), DEMO_PARTICLE_CAP, 2.5, 0.7, 2.5, 0.02);
        syncPlanner.reset(player.getUUID());
        syncPlayer(player);
        int populatedCells = populated;
        source.sendSuccess(() -> Component.literal(
            "Dynamic Atmosphere demo filled " + populatedCells
                + " loaded grid cells and emitted " + DEMO_PARTICLE_CAP + " vanilla cloud particles."), false);
        return populated;
    }

    private void sampleAndAdvance(MinecraftServer server) {
        automaticPasses++;
        playersLastPass = 0;
        Set<SourceKey> emittedSources = new HashSet<>();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            playersLastPass++;
            sampleAutomatic(player, emittedSources);
        }
        gridCellsRemoved += grid.decay(serverTicks);
        gridCellsRemoved += grid.retain(key -> isActiveLoadedCell(server, players, key));
    }

    private void sampleAutomatic(ServerPlayer player, Set<SourceKey> emittedSources) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int sent = 0;

        for (int[] offset : SAMPLE_OFFSETS) {
            int x = center.getX() + offset[0];
            int z = center.getZ() + offset[1];
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
                emitSource(emittedSources, source, cell, depth * WATER_EMISSION_PER_DEPTH);
            } else if (surfaceY >= level.getSeaLevel() + HIGH_TERRAIN_ABOVE_SEA) {
                AtmosphereGrid.CellKey<ResourceKey<Level>> cell =
                    cellKey(level, new BlockPos(x, surfaceY + 5, z));
                SourceKey source = new SourceKey(SourceKind.HIGH_TERRAIN, level.dimension(), surface.immutable());
                emitSource(emittedSources, source, cell, HIGH_TERRAIN_EMISSION);
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
            && AtmosphereGrid.emitSourceOnce(
                emittedSources, rainSource, grid, cell, RAIN_EMISSION, serverTicks)) {
            gridEmissions++;
            rainEmissions++;
        }

        int darkGroundEmission = AtmosphereSourceStrength.lightToEmission(
            level.getMaxLocalRawBrightness(landing));
        SourceKey darkGroundSource = new SourceKey(
            SourceKind.DARK_GROUND, level.dimension(), landing.immutable());
        if (darkGroundEmission > 0
            && level.canSeeSky(landing)
            && level.getFluidState(surface).isEmpty()
            && AtmosphereGrid.emitSourceOnce(
                emittedSources, darkGroundSource, grid, cell, darkGroundEmission, serverTicks)) {
            gridEmissions++;
            darkGroundEmissions++;
        }
    }

    private void emitSource(
        Set<SourceKey> emittedSources,
        SourceKey source,
        AtmosphereGrid.CellKey<ResourceKey<Level>> cell,
        int amount
    ) {
        if (AtmosphereGrid.emitSourceOnce(emittedSources, source, grid, cell, amount, serverTicks)) {
            gridEmissions++;
        }
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
        syncPlanner.retainPlayers(players.stream().map(ServerPlayer::getUUID).toList());
        for (ServerPlayer player : players) {
            if (forceSnapshot) {
                syncPlanner.reset(player.getUUID());
            }
            syncPlayer(player);
        }
    }

    private void syncPlayer(ServerPlayer player) {
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        AtmosphereGrid.CellKey<ResourceKey<Level>> playerCell = cellKey(player.serverLevel(), player.blockPosition());
        List<AtmosphereSyncPlanner.Cell> visible = grid.cells().stream()
            .filter(cell -> cell.key().dimension().equals(dimension))
            .filter(cell -> isCellLoaded(player.serverLevel(), cell.key()))
            .filter(cell -> isNear(playerCell, cell.key()))
            .sorted(Comparator.comparingLong(cell -> cellDistanceSquared(playerCell, cell.key())))
            .limit(MAX_VISIBLE_CELLS_PER_PLAYER)
            .map(cell -> new AtmosphereSyncPlanner.Cell(
                cell.key().x(), cell.key().y(), cell.key().z(), cell.amount()))
            .toList();

        for (AtmosphereSyncPlanner.Update<ResourceKey<Level>> update
            : syncPlanner.plan(player.getUUID(), dimension, visible)) {
            List<AtmosphereGridPayload.Cell> cells = update.cells().stream()
                .map(cell -> new AtmosphereGridPayload.Cell(cell.x(), cell.y(), cell.z(), cell.amount()))
                .toList();
            PacketDistributor.sendToPlayer(player, new AtmosphereGridPayload(
                update.dimension().location(), update.reset(), cells));
            payloadsSent++;
        }
    }

    private boolean isActiveLoadedCell(
        MinecraftServer server,
        List<ServerPlayer> players,
        AtmosphereGrid.CellKey<ResourceKey<Level>> key
    ) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null || !isCellLoaded(level, key)) {
            return false;
        }
        return players.stream()
            .filter(player -> player.serverLevel().dimension().equals(key.dimension()))
            .map(player -> cellKey(level, player.blockPosition()))
            .anyMatch(playerCell -> isNear(playerCell, key));
    }

    private boolean isCellLoaded(ServerLevel level, AtmosphereGrid.CellKey<ResourceKey<Level>> key) {
        return level.hasChunkAt(cellCenter(key));
    }

    private boolean isNear(
        AtmosphereGrid.CellKey<ResourceKey<Level>> first,
        AtmosphereGrid.CellKey<ResourceKey<Level>> second
    ) {
        return Math.abs((long) first.y() - second.y()) <= GRID_VERTICAL_RADIUS_CELLS
            && horizontalCellDistanceSquared(first, second) <= (long) GRID_RADIUS_CELLS * GRID_RADIUS_CELLS;
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

    private BlockPos cellCenter(AtmosphereGrid.CellKey<ResourceKey<Level>> key) {
        return new BlockPos(
            key.x() * AtmosphereGrid.CELL_SIZE + AtmosphereGrid.CELL_SIZE / 2,
            key.y() * AtmosphereGrid.CELL_SIZE + AtmosphereGrid.CELL_SIZE / 2,
            key.z() * AtmosphereGrid.CELL_SIZE + AtmosphereGrid.CELL_SIZE / 2
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
