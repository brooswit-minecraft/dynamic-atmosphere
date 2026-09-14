package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;

/**
 * Bounded server-side atmospheric grids and delivery adapter. Each material
 * keeps its own fixed cell size, sparse persistence, and conservative spreading.
 */
final class ForgeAtmospherePrototype {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static DynamicAtmosphereServerConfig.Snapshot tuning() {
        return DynamicAtmosphereServerConfig.snapshot();
    }

    private static final int DEMO_PARTICLE_CAP = 24;
    private static final int[][] DEMO_CELL_OFFSETS = {
        {0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private long serverTicks;
    private long producerCycles;
    private long producerChecks;
    private long producerSamples;
    private int producerLastCycleChunks;
    private long particlesSent;
    private long gridEmissions;
    private long rainEmissions;
    private long darkGroundEmissions;
    private long materialMoved;
    private long condensationChecks;
    private long waterBlocksCreated;
    private long materialCondensed;
    private long waterRemovalEmissions;
    private int blockedOverflow;
    private int sourcesProcessed;
    private boolean workRemaining;
    private boolean searchLimited;
    private long pressureBreaks;
    private long pressureSearchLimits;
    private int pressureAttempts;
    private int importsRemaining;
    private long payloadsSent;
    private long smokeEmissions;
    private long smokeProducerChecks;
    private long smokeProducerCycles;
    private long smokePayloadsSent;
    private long materialPayloadsSent;
    private UUID worldId;
    private final AtmosphereGrid<ResourceKey<Level>> grid = new AtmosphereGrid<>();
    private final Map<ChunkKey, ChunkState> chunks = new HashMap<>();
    private final Map<ChunkKey, LevelChunk> pendingLoads = new LinkedHashMap<>();
    private final AtmosphereProducerSchedule<ChunkKey> producerSchedule = new AtmosphereProducerSchedule<>();
    private final AtmosphereSyncPlanner<UUID, ResourceKey<Level>> syncPlanner =
        new AtmosphereSyncPlanner<>(AtmosphereGridPayload.MAX_CELLS_PER_PAYLOAD);
    private final AtmosphereGrid<ResourceKey<Level>> smokeGrid =
        new AtmosphereGrid<>(SmokeGridLayout::nextSimulationTick);
    private final AtmosphereProducerSchedule<ChunkKey> smokeProducerSchedule = new AtmosphereProducerSchedule<>();
    private final SmokeSyncPlanner<UUID, ResourceKey<Level>> smokeSyncPlanner =
        new SmokeSyncPlanner<>(SmokeGridPayload.MAX_CELLS_PER_PAYLOAD);
    private final Map<AtmosphereMaterial, AtmosphereGrid<ResourceKey<Level>>> materialGrids =
        createMaterialGrids();
    private final Map<AtmosphereMaterial, SmokeSyncPlanner<UUID, ResourceKey<Level>>> materialSyncPlanners =
        createMaterialSyncPlanners();
    private final DustGameplay dustGameplay = DustGameplay.create();
    private final EnderGasGameplay enderGasGameplay = EnderGasGameplay.create();
    private final ViolenceGameplay violenceGameplay = ViolenceGameplay.create();
    private final SlimeGameplay slimeGameplay = SlimeGameplay.create();
    private final ExhaustGameplay exhaustGameplay = ExhaustGameplay.create();
    private final Map<AtmosphereMaterial, AtmosphereProducerSchedule<ChunkKey>> materialProducerSchedules =
        createMaterialProducerSchedules();
    private final Map<AtmosphereMaterial, List<AtmosphereMaterialProducer>> materialProducerHooks =
        createMaterialProducerHooks();
    private final Map<AtmosphereMaterial, Long> nextMaterialProducerTicks = createMaterialProducerTicks();

    ForgeAtmospherePrototype() {
        registerMaterialProducer(AtmosphereMaterial.ENDER_GAS, this::sampleEnderGasChunk);
        registerMaterialProducer(AtmosphereMaterial.VIOLENCE, (level, chunk) -> {
            violenceGameplay.onLoadedChunkProducerCheck(level, chunk);
            int x = chunk.getPos().getMinBlockX() + level.random.nextInt(16);
            int z = chunk.getPos().getMinBlockZ() + level.random.nextInt(16);
            int y = level.getMinBuildHeight() + level.random.nextInt(level.getHeight());
            BlockPos pos = new BlockPos(x, y, z);
            violenceGameplay.onPassiveBlockSample(level, pos, chunk.getBlockState(pos));
        });
        registerMaterialProducer(AtmosphereMaterial.SLIME, slimeGameplay::onLoadedChunkProducerCheck);
    }

    private static Map<AtmosphereMaterial, AtmosphereGrid<ResourceKey<Level>>> createMaterialGrids() {
        var grids = new EnumMap<AtmosphereMaterial, AtmosphereGrid<ResourceKey<Level>>>(AtmosphereMaterial.class);
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            grids.put(material, new AtmosphereGrid<>(material::nextSimulationTick));
        }
        return grids;
    }

    private static Map<AtmosphereMaterial, SmokeSyncPlanner<UUID, ResourceKey<Level>>> createMaterialSyncPlanners() {
        var planners = new EnumMap<AtmosphereMaterial,
            SmokeSyncPlanner<UUID, ResourceKey<Level>>>(AtmosphereMaterial.class);
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            planners.put(material, new SmokeSyncPlanner<>(
                MaterialGridPayload.MAX_CELLS_PER_PAYLOAD, material::chunkCoordinate));
        }
        return planners;
    }

    private static Map<AtmosphereMaterial, AtmosphereProducerSchedule<ChunkKey>> createMaterialProducerSchedules() {
        var schedules = new EnumMap<AtmosphereMaterial, AtmosphereProducerSchedule<ChunkKey>>(
            AtmosphereMaterial.class);
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            schedules.put(material, new AtmosphereProducerSchedule<>());
        }
        return schedules;
    }

    private static Map<AtmosphereMaterial, List<AtmosphereMaterialProducer>> createMaterialProducerHooks() {
        var hooks = new EnumMap<AtmosphereMaterial, List<AtmosphereMaterialProducer>>(AtmosphereMaterial.class);
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            hooks.put(material, new ArrayList<>());
        }
        return hooks;
    }

    private static Map<AtmosphereMaterial, Long> createMaterialProducerTicks() {
        var ticks = new EnumMap<AtmosphereMaterial, Long>(AtmosphereMaterial.class);
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            ticks.put(material, material.nextProducerTick(0));
        }
        return ticks;
    }

    void onServerTick(ServerTickEvent.Post event) {
        serverTicks++;
        importsRemaining = tuning().runtime().maxChunkImportsPerTick();
        importPendingChunks(event.getServer());
        drainWaterTransitions(event.getServer());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ForgeSmokeGameplay.drain(level, (pos, amount) -> emitSmoke(level, pos, amount),
                (pos, amount) -> emitVapor(level, pos, amount));
        }
        if (serverTicks % tuning().vapor().producerIntervalTicks() == 0) {
            beginProducerCycle();
        }
        if (serverTicks % tuning().smoke().producerIntervalTicks() == 0) {
            beginSmokeProducerCycle();
        }
        beginMaterialProducerCyclesIfDue();
        processProducerChunks(event.getServer());
        processSmokeProducerChunks(event.getServer());
        processMaterialProducerChunks(event.getServer());
        pressureAttempts = 0;
        blockedOverflow = 0;
        sourcesProcessed = 0;
        workRemaining = false;
        searchLimited = false;
        advanceSimulation(event.getServer());
        advanceSmokeSimulation(event.getServer());
        advanceMaterialSimulations(event.getServer());
        flushDirtyChunks();
        flushSmokeDirtyChunks();
        flushMaterialDirtyChunks();
        if (serverTicks % tuning().runtime().syncIntervalTicks() == 0) {
            syncPlayers(event.getServer(), serverTicks % tuning().runtime().fullSnapshotIntervalTicks() == 0);
            syncSmokePlayers(event.getServer(), serverTicks % tuning().runtime().fullSnapshotIntervalTicks() == 0);
            syncMaterialPlayers(event.getServer(), serverTicks % tuning().runtime().fullSnapshotIntervalTicks() == 0);
        }
        flushDirtyChunks();
        flushSmokeDirtyChunks();
        flushMaterialDirtyChunks();
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
                ForgeAtmosphereCapacity.clear(chunk);
                ForgeSmokeCapacity.clear(chunk);
                ForgeMaterialCapacity.clear(chunk);
                ChunkKey key = chunkKey(level, chunk);
                pendingLoads.remove(key, chunk);
                ChunkState state = chunks.get(key);
                if (state != null && state.chunk == chunk) {
                    flushDirtyChunks();
                    flushSmokeDirtyChunks();
                    flushMaterialDirtyChunks();
                    chunks.remove(key);
                    state.cells.keySet().forEach(grid::remove);
                    state.smokeCells.keySet().forEach(smokeGrid::remove);
                    for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
                        state.materialCells.get(material).keySet().forEach(materialGrids.get(material)::remove);
                    }
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
            smokeSyncPlanner.disconnect(player.getUUID());
            materialSyncPlanners.values().forEach(planner -> planner.disconnect(player.getUUID()));
        }
    }

    void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlanner.reset(player.getUUID());
            smokeSyncPlanner.reset(player.getUUID());
            materialSyncPlanners.values().forEach(planner -> planner.reset(player.getUUID()));
        }
    }

    void onServerStopped(ServerStoppedEvent event) {
        serverTicks = 0;
        producerCycles = 0;
        producerChecks = 0;
        producerSamples = 0;
        producerLastCycleChunks = 0;
        particlesSent = 0;
        gridEmissions = 0;
        rainEmissions = 0;
        darkGroundEmissions = 0;
        materialMoved = 0;
        condensationChecks = 0;
        waterBlocksCreated = 0;
        materialCondensed = 0;
        waterRemovalEmissions = 0;
        blockedOverflow = 0;
        sourcesProcessed = 0;
        workRemaining = false;
        searchLimited = false;
        pressureBreaks = 0;
        pressureSearchLimits = 0;
        pressureAttempts = 0;
        payloadsSent = 0;
        smokeEmissions = 0;
        smokeProducerChecks = 0;
        smokeProducerCycles = 0;
        smokePayloadsSent = 0;
        materialPayloadsSent = 0;
        worldId = null;
        grid.clear();
        grid.drainDirtyKeys();
        smokeGrid.clear();
        smokeGrid.drainDirtyKeys();
        materialGrids.values().forEach(materialGrid -> {
            materialGrid.clear();
            materialGrid.drainDirtyKeys();
        });
        chunks.clear();
        pendingLoads.clear();
        producerSchedule.clear();
        smokeProducerSchedule.clear();
        materialProducerSchedules.values().forEach(AtmosphereProducerSchedule::clear);
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            nextMaterialProducerTicks.put(material, material.nextProducerTick(0));
        }
        AtmosphereWaterTransitions.clear();
        syncPlanner.clear();
        smokeSyncPlanner.clear();
        materialSyncPlanners.values().forEach(SmokeSyncPlanner::clear);
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dynamicatmosphere")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("demo").executes(context -> demo(context.getSource())))
            .then(Commands.literal("smoke")
                .then(Commands.literal("status").executes(context -> smokeStatus(context.getSource())))));
    }

    private int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "Dynamic Atmosphere grid: active; cellSize=" + AtmosphereGrid.CELL_SIZE
                + ", cells=" + grid.size()
                + ", loadedChunks=" + chunks.size()
                + ", pendingImports=" + pendingLoads.size()
                + ", simulationInterval=" + AtmosphereGridLayout.simulationIntervalTicks()
                + ", producerInterval=" + tuning().runtime().producerIntervalTicks()
                + ", producerChance=" + tuning().vapor().producerChance()
                + ", producerCycles=" + producerCycles
                + ", producerChecks=" + producerChecks
                + ", producerSamples=" + producerSamples
                + ", producerBacklog=" + producerSchedule.pending()
                + ", producerLastCycleChunks=" + producerLastCycleChunks
                + ", syncInterval=" + tuning().runtime().syncIntervalTicks()
                + ", fullSnapshotInterval=" + tuning().runtime().fullSnapshotIntervalTicks()
                + ", emissions=" + gridEmissions
                + ", rainEmissions=" + rainEmissions
                + ", darkGroundEmissions=" + darkGroundEmissions
                + ", materialMoved=" + materialMoved
                + ", condensationChecks=" + condensationChecks
                + ", waterBlocksCreated=" + waterBlocksCreated
                + ", materialCondensed=" + materialCondensed
                + ", waterRemovalEmissions=" + waterRemovalEmissions
                + ", blockedOverflow=" + blockedOverflow
                + ", sourcesProcessed=" + sourcesProcessed
                + ", workRemaining=" + workRemaining
                + ", searchLimited=" + searchLimited
                + ", pressureBreaks=" + pressureBreaks
                + ", pressureAttempts=" + pressureAttempts + "/" + ForgeAtmospherePressure.MAX_BREAKS_PER_PASS
                + ", pressureSearchLimits=" + pressureSearchLimits
                + ", payloads=" + payloadsSent
                + ", particles=" + particlesSent
                + ". Server authoritative; no passive decay; six-neighbor spreading with air-space capacity."), false);
        return 1;
    }

    private int smokeStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "Smoke grid: cells=" + smokeGrid.size()
                + ", emissions=" + smokeEmissions
                + ", producerChecks=" + smokeProducerChecks
                + ", producerCycles=" + smokeProducerCycles
                + ", producerBacklog=" + smokeProducerSchedule.pending()
                + ", payloads=" + smokePayloadsSent), false);
        return 1;
    }

    boolean isVaporMoreThanHalfFull(ServerLevel level, BlockPos pos) {
        var key = cellKey(level, pos);
        ChunkState state = chunks.get(new ChunkKey(level.dimension(),
            AtmosphereGridLayout.chunkCoordinate(key.x()), AtmosphereGridLayout.chunkCoordinate(key.z())));
        if (state == null || !state.readable || level.getChunkSource().getChunkNow(
            AtmosphereGridLayout.chunkCoordinate(key.x()), AtmosphereGridLayout.chunkCoordinate(key.z())) != state.chunk) {
            return false;
        }
        var cell = grid.get(key);
        if (cell == null) return false;
        int capacity = capacityAt(level, key);
        return capacity > 0 && (long) cell.amount() * 2 > capacity;
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

    private void beginProducerCycle() {
        List<ChunkKey> loaded = chunks.entrySet().stream()
            .filter(entry -> entry.getValue().readable)
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing((ChunkKey key) -> key.dimension().location().toString())
                .thenComparingInt(ChunkKey::x)
                .thenComparingInt(ChunkKey::z))
            .toList();
        if (producerSchedule.beginCycle(loaded)) {
            producerCycles++;
            producerLastCycleChunks = loaded.size();
        }
    }

    private void beginSmokeProducerCycle() {
        List<ChunkKey> loaded = chunks.entrySet().stream()
            .filter(entry -> entry.getValue().smokeReadable)
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing((ChunkKey key) -> key.dimension().location().toString())
                .thenComparingInt(ChunkKey::x)
                .thenComparingInt(ChunkKey::z))
            .toList();
        if (smokeProducerSchedule.beginCycle(loaded)) smokeProducerCycles++;
    }

    private void processSmokeProducerChunks(MinecraftServer server) {
        for (ChunkKey key : smokeProducerSchedule.poll(tuning().runtime().maxSmokeProducerChunksPerTick(),
            candidate -> isSmokeProducerChunkActive(server, candidate))) {
            sampleFireSmoke(server, key);
        }
    }

    private boolean isSmokeProducerChunkActive(MinecraftServer server, ChunkKey key) {
        ServerLevel level = server.getLevel(key.dimension());
        ChunkState state = chunks.get(key);
        return level != null && state != null && state.smokeReadable
            && level.getChunkSource().getChunkNow(key.x(), key.z()) == state.chunk;
    }

    private void beginMaterialProducerCyclesIfDue() {
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            if (materialProducerHooks.get(material).isEmpty()
                || serverTicks < nextMaterialProducerTicks.get(material)) continue;
            nextMaterialProducerTicks.put(material, material.nextProducerTick(serverTicks));
            List<ChunkKey> loaded = chunks.entrySet().stream()
                .filter(entry -> entry.getValue().materialReadable.contains(material))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing((ChunkKey key) -> key.dimension().location().toString())
                    .thenComparingInt(ChunkKey::x).thenComparingInt(ChunkKey::z))
                .toList();
            materialProducerSchedules.get(material).beginCycle(loaded);
        }
    }

    private void processMaterialProducerChunks(MinecraftServer server) {
        long activeMaterials = materialProducerHooks.values().stream().filter(hooks -> !hooks.isEmpty()).count();
        if (activeMaterials == 0) return;
        int perMaterialBudget = Math.max(1, tuning().runtime().maxProducerChunksPerTick() / (int) activeMaterials);
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            List<AtmosphereMaterialProducer> hooks = List.copyOf(materialProducerHooks.get(material));
            if (hooks.isEmpty()) continue;
            for (ChunkKey key : materialProducerSchedules.get(material).poll(perMaterialBudget,
                candidate -> isMaterialProducerChunkActive(server, candidate, material))) {
                ServerLevel level = server.getLevel(key.dimension());
                ChunkState state = chunks.get(key);
                if (level == null || state == null) continue;
                for (AtmosphereMaterialProducer hook : hooks) {
                    hook.sampleLoadedChunk(level, state.chunk);
                }
            }
        }
    }

    void registerMaterialProducer(AtmosphereMaterial material, AtmosphereMaterialProducer producer) {
        materialProducerHooks.get(Objects.requireNonNull(material, "material"))
            .add(Objects.requireNonNull(producer, "producer"));
    }

    private void sampleEnderGasChunk(ServerLevel level, LevelChunk chunk) {
        enderGasGameplay.onLoadedChunkProducerCheck(level, chunk);
        int x = chunk.getPos().getMinBlockX() + level.random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + level.random.nextInt(16);
        int y = level.getMinBuildHeight()
            + level.random.nextInt(level.getMaxBuildHeight() - level.getMinBuildHeight());
        BlockPos sample = new BlockPos(x, y, z);
        enderGasGameplay.onPassiveBlockSample(level, sample, chunk.getBlockState(sample));
    }

    private boolean isMaterialProducerChunkActive(
        MinecraftServer server, ChunkKey key, AtmosphereMaterial material
    ) {
        ServerLevel level = server.getLevel(key.dimension());
        ChunkState state = chunks.get(key);
        return level != null && state != null && state.materialReadable.contains(material)
            && level.getChunkSource().getChunkNow(key.x(), key.z()) == state.chunk;
    }

    /** Palette-gates loaded sections; only sections containing actual fire pay the bounded 4096-block scan. */
    private void sampleFireSmoke(MinecraftServer server, ChunkKey key) {
        ServerLevel level = server.getLevel(key.dimension());
        ChunkState state = chunks.get(key);
        if (level == null || state == null || !state.smokeReadable) return;
        smokeProducerChecks++;
        LevelChunkSection[] sections = state.chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(block -> ForgeSmokeGameplay.sourceAmount(block) > 0)) continue;
            int sectionY = SectionPos.sectionToBlockCoord(state.chunk.getSectionYFromSectionIndex(sectionIndex));
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        int amount = ForgeSmokeGameplay.sourceAmount(section.getBlockState(localX, localY, localZ));
                        if (amount <= 0) continue;
                        BlockPos fire = new BlockPos(state.chunk.getPos().getMinBlockX() + localX,
                            sectionY + localY, state.chunk.getPos().getMinBlockZ() + localZ);
                        var smokeCell = smokeCellKey(level, fire.above());
                        int capacity = smokeCapacityAt(level, smokeCell);
                        if (capacity > 0 && smokeGrid.emit(
                            smokeCell, amount, serverTicks, capacity)) {
                            smokeEmissions++;
                        }
                    }
                }
            }
        }
    }

    private void drainWaterTransitions(MinecraftServer server) {
        for (var entry : AtmosphereWaterTransitions.drain().entrySet()) {
            var key = entry.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            ChunkKey chunkKey = new ChunkKey(key.dimension(), AtmosphereGridLayout.chunkCoordinate(key.x()),
                AtmosphereGridLayout.chunkCoordinate(key.z()));
            ChunkState state = chunks.get(chunkKey);
            if (level == null || state == null || !state.readable
                || level.getChunkSource().getChunkNow(chunkKey.x(), chunkKey.z()) != state.chunk) {
                continue;
            }
            int capacity = capacityAt(level, key);
            if (capacity >= 0 && grid.emit(key, entry.getValue().material(), serverTicks, capacity)) {
                waterRemovalEmissions += entry.getValue().removals();
                gridEmissions++;
            }
        }
    }

    /** A cycle is never replaced while backlogged; bounded work continues on following ticks. */
    private void processProducerChunks(MinecraftServer server) {
        Set<SourceKey> emittedSources = new HashSet<>();
        for (ChunkKey key : producerSchedule.poll(tuning().runtime().maxProducerChunksPerTick(),
            candidate -> isProducerChunkActive(server, candidate))) {
            sampleAutomaticChunk(server, key, emittedSources);
        }
    }

    private boolean isProducerChunkActive(MinecraftServer server, ChunkKey key) {
        ServerLevel level = server.getLevel(key.dimension());
        ChunkState state = chunks.get(key);
        return level != null && state != null && state.readable
            && level.getChunkSource().getChunkNow(key.x(), key.z()) == state.chunk;
    }

    private void advanceSimulation(MinecraftServer server) {
        var capacities = new HashMap<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer>();
        ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacityAt = key ->
            capacities.computeIfAbsent(key, candidate -> capacityAt(server.getLevel(candidate.dimension()), candidate));
        BiPredicate<AtmosphereGrid.CellKey<ResourceKey<Level>>,
            AtmosphereGrid.CellKey<ResourceKey<Level>>> canTransfer =
                (source, destination) -> canTransferDown(server, source, destination, false);
        var spread = grid.spread(serverTicks, capacityAt, key -> {
            growPlants(server.getLevel(key.dimension()), grid, key, AtmosphereGrid.CELL_SIZE, capacityAt);
            capacities.clear();
            if (condense(server.getLevel(key.dimension()), key, capacityAt.applyAsInt(key))) capacities.clear();
        }, key -> {
            ServerLevel level = server.getLevel(key.dimension());
            return level != null && level.random.nextDouble() >= tuning().vapor().skipChance();
        }, canTransfer, key -> applyFans(server.getLevel(key.dimension()), grid, key,
            AtmosphereGrid.CELL_SIZE, capacityAt, canTransfer));
        materialMoved += spread.moved();
        blockedOverflow += spread.blockedOverflow();
        sourcesProcessed += spread.sourcesProcessed();
        workRemaining |= spread.workRemaining();
        searchLimited |= spread.searchLimited();
        relievePressure(server, grid, capacityAt, canTransfer, spread,
            AtmosphereGridLayout.CELL_SIZE, capacities::clear);
    }

    private void relievePressure(
        MinecraftServer server,
        AtmosphereGrid<ResourceKey<Level>> materialGrid,
        ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacityAt,
        BiPredicate<AtmosphereGrid.CellKey<ResourceKey<Level>>,
            AtmosphereGrid.CellKey<ResourceKey<Level>>> canTransfer,
        AtmosphereGrid.SpreadResult<ResourceKey<Level>> spread,
        int cellSize,
        Runnable clearCapacities
    ) {
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
                clearCapacities.run();
                var fresh = materialGrid.redistributeOverflow(serverTicks, capacityAt, List.of(source), canTransfer);
                materialMoved += fresh.moved();
                searchLimited |= fresh.searchLimited();
                if (fresh.searchLimited() || fresh.workRemaining() || !fresh.blockedCells().stream()
                    .anyMatch(cell -> cell.key().equals(source) && cell.pressureEligible())) {
                    break;
                }
                var current = materialGrid.get(source);
                int capacity = capacityAt.applyAsInt(source);
                if (current == null || capacity < 0 || current.amount() <= capacity) {
                    break;
                }
                pressureAttempts++;
                var pressure = ForgeAtmospherePressure.breakForPressure(level, source,
                    key -> capacityAt.applyAsInt(key) >= 0, canTransfer, cellSize);
                if (pressure.searchLimited()) {
                    pressureSearchLimits++;
                }
                if (pressure.broken().isEmpty()) {
                    break;
                }
                var broken = pressure.broken().orElseThrow();
                pressureBreaks++;
                clearCapacities.run();
                int newCapacity = capacityAt.applyAsInt(broken.cell());
                materialGrid.emit(broken.cell(), broken.addedMaterial(), serverTicks, newCapacity);
                Set<AtmosphereGrid.CellKey<ResourceKey<Level>>> overflowSources = new LinkedHashSet<>();
                overflowSources.add(source);
                overflowSources.add(broken.cell());
                var retry = materialGrid.redistributeOverflow(serverTicks, capacityAt, overflowSources, canTransfer);
                materialMoved += retry.moved();
                searchLimited |= retry.searchLimited();
                if (retry.searchLimited() || !retry.blockedCells().stream()
                    .anyMatch(cell -> cell.key().equals(source) && cell.pressureEligible())) {
                    break;
                }
            }
        }
    }

    private void advanceSmokeSimulation(MinecraftServer server) {
        var capacities = new HashMap<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer>();
        ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacityAt = key ->
            capacities.computeIfAbsent(key,
                candidate -> smokeCapacityAt(server.getLevel(candidate.dimension()), candidate));
        BiPredicate<AtmosphereGrid.CellKey<ResourceKey<Level>>,
            AtmosphereGrid.CellKey<ResourceKey<Level>>> canTransfer =
                (source, destination) -> canTransferDown(server, source, destination, true);
        var spread = smokeGrid.spread(serverTicks, capacityAt, key -> {
            ServerLevel level = server.getLevel(key.dimension());
            var cell = smokeGrid.get(key);
            if (level == null || cell == null) return;
            int size = SmokeGridLayout.CELL_SIZE;
            int used = ForgeSmokeGameplay.processTurn(level,
                new BlockPos(key.x() * size, key.y() * size, key.z() * size), cell.amount());
            capacities.clear();
            var current = smokeGrid.get(key);
            if (used > 0 && current != null) smokeGrid.set(key,
                Math.max(0, current.amount() - used), serverTicks, capacityAt.applyAsInt(key));
        }, ignored -> true, canTransfer, key -> applyFans(server.getLevel(key.dimension()), smokeGrid, key,
            SmokeGridLayout.CELL_SIZE, capacityAt, canTransfer));
        materialMoved += spread.moved();
        blockedOverflow += spread.blockedOverflow();
        sourcesProcessed += spread.sourcesProcessed();
        workRemaining |= spread.workRemaining();
        searchLimited |= spread.searchLimited();
        relievePressure(server, smokeGrid, capacityAt, canTransfer, spread,
            SmokeGridLayout.CELL_SIZE, capacities::clear);
    }

    private void advanceMaterialSimulations(MinecraftServer server) {
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            var capacities = new HashMap<AtmosphereGrid.CellKey<ResourceKey<Level>>, Integer>();
            ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacityAt = key ->
                capacities.computeIfAbsent(key,
                    candidate -> materialCapacityAt(server.getLevel(candidate.dimension()), material, candidate));
            AtmosphereGrid<ResourceKey<Level>> materialGrid = materialGrids.get(material);
            BiPredicate<AtmosphereGrid.CellKey<ResourceKey<Level>>,
                AtmosphereGrid.CellKey<ResourceKey<Level>>> canTransfer =
                    (source, destination) -> canMaterialTransferDown(server, material, source, destination);
            var spread = materialGrid.spread(serverTicks, capacityAt, key -> {
                ServerLevel cellLevel = server.getLevel(key.dimension());
                if (cellLevel == null) return;
                BlockPos origin = materialCellOrigin(material, key);
                if (material == AtmosphereMaterial.VIOLENCE) violenceGameplay.onProcessedCell(cellLevel, origin);
                if (material == AtmosphereMaterial.SLIME) slimeGameplay.onProcessedCell(cellLevel, origin);
                if (material == AtmosphereMaterial.EXHAUST) {
                    int size = material.cellSize();
                    exhaustGameplay.processTurn(cellLevel, origin,
                        cellLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            new net.minecraft.world.phys.AABB(origin).expandTowards(size - 1, size - 1, size - 1)));
                    growPlants(cellLevel, materialGrid, key, size, capacityAt);
                    capacities.clear();
                }
                if (material == AtmosphereMaterial.DUST) {
                    ServerLevel level = server.getLevel(key.dimension());
                    if (level != null && DustTransformations.process(level,
                        materialCellOrigin(material, key), level.random::nextDouble) != DustTransformations.Result.NONE) {
                        capacities.clear();
                    }
                    dissipateDust(level, materialGrid, key, capacityAt);
                }
            }, ignored -> true, canTransfer, key -> applyFans(server.getLevel(key.dimension()), materialGrid, key,
                material.cellSize(), capacityAt, canTransfer));
            materialMoved += spread.moved();
            blockedOverflow += spread.blockedOverflow();
            sourcesProcessed += spread.sourcesProcessed();
            workRemaining |= spread.workRemaining();
            searchLimited |= spread.searchLimited();
            relievePressure(server, materialGrid, capacityAt, canTransfer, spread,
                material.cellSize(), capacities::clear);
        }
    }

    private void dissipateDust(
        ServerLevel level,
        AtmosphereGrid<ResourceKey<Level>> materialGrid,
        AtmosphereGrid.CellKey<ResourceKey<Level>> key,
        ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacityAt
    ) {
        if (level == null) return;
        var cell = materialGrid.get(key);
        if (cell == null) return;
        int loss = DustDissipation.amount(cell.amount(), level.random::nextDouble);
        if (loss > 0) {
            materialGrid.set(key, cell.amount() - loss, serverTicks, capacityAt.applyAsInt(key));
        }
    }

    DustGameplay dustGameplay() { return dustGameplay; }
    EnderGasGameplay enderGasGameplay() { return enderGasGameplay; }
    ViolenceGameplay violenceGameplay() { return violenceGameplay; }
    ExhaustGameplay exhaustGameplay() { return exhaustGameplay; }

    private void applyFans(ServerLevel level, AtmosphereGrid<ResourceKey<Level>> target,
        AtmosphereGrid.CellKey<ResourceKey<Level>> source, int size,
        ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacity,
        BiPredicate<AtmosphereGrid.CellKey<ResourceKey<Level>>, AtmosphereGrid.CellKey<ResourceKey<Level>>> canTransfer) {
        if (level == null || target.get(source) == null) return;
        BlockPos origin = new BlockPos(source.x() * size, source.y() * size, source.z() * size);
        for (var request : ForgeFanTransport.collect(level, origin, size,
            tuning().integrations().createFanTransportPerRpm())) {
            var current = target.get(source);
            if (current == null) break;
            var direction = request.direction();
            var destination = new AtmosphereGrid.CellKey<>(source.dimension(), source.x() + direction.getStepX(),
                source.y() + direction.getStepY(), source.z() + direction.getStepZ());
            int spareCapacity = capacity.applyAsInt(destination);
            if (spareCapacity <= 0 || !canTransfer.test(source, destination)) continue;
            var existing = target.get(destination);
            int existingAmount = existing == null ? 0 : existing.amount();
            int moved = Math.min(request.amount(), Math.min(current.amount(), spareCapacity - existingAmount));
            if (moved <= 0) continue;
            if (target.set(destination, existingAmount + moved, serverTicks, spareCapacity)) {
                target.set(source, current.amount() - moved, serverTicks, capacity.applyAsInt(source));
                materialMoved += moved;
            }
        }
    }

    private void growPlants(ServerLevel level, AtmosphereGrid<ResourceKey<Level>> target,
        AtmosphereGrid.CellKey<ResourceKey<Level>> key, int size,
        ToIntFunction<AtmosphereGrid.CellKey<ResourceKey<Level>>> capacity) {
        var cell = target.get(key);
        if (level == null || cell == null || cell.amount() < AtmospherePlantGrowth.minimumCost()) return;
        BlockPos origin = new BlockPos(key.x() * size, key.y() * size, key.z() * size);
        java.util.function.IntFunction<BlockPos> position = index ->
            origin.offset(index % size, index / (size * size), index / size % size);
        int used = AtmospherePlantGrowth.attempt(cell.amount(), size, level.random::nextDouble, index -> {
            BlockPos pos = position.apply(index);
            var state = level.getBlockState(pos);
            return (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock)
                && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock plant
                && plant.isValidBonemealTarget(level, pos, state);
        }, index -> {
            BlockPos pos = position.apply(index);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock
                && !level.hasChunksAt(pos.offset(-32, 0, -32), pos.offset(32, 0, 32))) return false;
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock plant)
                || !plant.isValidBonemealTarget(level, pos, state)
                || !plant.isBonemealSuccess(level, level.random, pos, state)) return false;
            plant.performBonemeal(level, level.random, pos, state);
            return true;
        });
        if (used > 0) {
            var current = target.get(key);
            if (current != null) target.set(key, Math.max(0, current.amount() - used), serverTicks,
                capacity.applyAsInt(key));
        }
    }

    boolean emitMaterial(ServerLevel level, AtmosphereMaterial material, BlockPos source, int amount) {
        if (level == null || material == null || source == null || amount <= 0
            || !level.getServer().isSameThread()) return false;
        var key = materialCellKey(level, material, source);
        ChunkState state = chunks.get(new ChunkKey(level.dimension(), material.chunkCoordinate(key.x()),
            material.chunkCoordinate(key.z())));
        if (state == null || !state.materialReadable.contains(material)
            || level.getChunkSource().getChunkNow(material.chunkCoordinate(key.x()),
                material.chunkCoordinate(key.z())) != state.chunk) return false;
        int capacity = materialCapacityAt(level, material, key);
        return capacity > 0 && materialGrids.get(material).emit(key, amount, serverTicks, capacity);
    }

    private boolean emitSmoke(ServerLevel level, BlockPos source, int amount) {
        var key = smokeCellKey(level, source);
        ChunkState state = chunks.get(new ChunkKey(level.dimension(), Math.floorDiv(source.getX(), 16),
            Math.floorDiv(source.getZ(), 16)));
        if (state == null || !state.smokeReadable) return false;
        int capacity = smokeCapacityAt(level, key);
        if (capacity < 0 || !smokeGrid.emit(key, amount, serverTicks, capacity)) return false;
        smokeEmissions++;
        return true;
    }

    boolean emitVapor(ServerLevel level, BlockPos source, int amount) {
        if (level == null || source == null || amount <= 0 || !level.getServer().isSameThread()) return false;
        var key = cellKey(level, source);
        ChunkState state = chunks.get(new ChunkKey(level.dimension(),
            AtmosphereGridLayout.chunkCoordinate(key.x()), AtmosphereGridLayout.chunkCoordinate(key.z())));
        if (state == null || !state.readable || level.getChunkSource().getChunkNow(
            AtmosphereGridLayout.chunkCoordinate(key.x()), AtmosphereGridLayout.chunkCoordinate(key.z())) != state.chunk) {
            return false;
        }
        int capacity = capacityAt(level, key);
        return capacity > 0 && grid.emit(key, amount, serverTicks, capacity);
    }

    Optional<AtmosphereMaterialState> materialState(
        ServerLevel level, AtmosphereMaterial material, BlockPos source
    ) {
        if (!isMaterialCellAvailable(level, material, source)) return Optional.empty();
        var key = materialCellKey(level, material, source);
        int capacity = materialCapacityAt(level, material, key);
        if (capacity < 0) return Optional.empty();
        var cell = materialGrids.get(material).get(key);
        return Optional.of(new AtmosphereMaterialState(cell == null ? 0 : cell.amount(), capacity));
    }

    boolean consumeMaterial(ServerLevel level, AtmosphereMaterial material, BlockPos source, int amount) {
        if (amount <= 0 || !isMaterialCellAvailable(level, material, source)) return false;
        var key = materialCellKey(level, material, source);
        var cell = materialGrids.get(material).get(key);
        if (cell == null || cell.amount() < amount) return false;
        int capacity = materialCapacityAt(level, material, key);
        return capacity >= 0 && materialGrids.get(material).set(key, cell.amount() - amount, serverTicks, capacity);
    }

    private boolean isMaterialCellAvailable(
        ServerLevel level, AtmosphereMaterial material, BlockPos source
    ) {
        if (level == null || material == null || source == null || !level.getServer().isSameThread()) return false;
        var key = materialCellKey(level, material, source);
        ChunkState state = chunks.get(new ChunkKey(level.dimension(), material.chunkCoordinate(key.x()),
            material.chunkCoordinate(key.z())));
        return state != null && state.materialReadable.contains(material)
            && level.getChunkSource().getChunkNow(material.chunkCoordinate(key.x()),
                material.chunkCoordinate(key.z())) == state.chunk;
    }

    private boolean condense(ServerLevel level, AtmosphereGrid.CellKey<ResourceKey<Level>> key, int capacity) {
        var cell = grid.get(key);
        if (level == null || level.dimensionType().ultraWarm() || cell == null || capacity <= 0) return false;
        condensationChecks++;
        if (!AtmosphereCondensation.shouldCondense(cell.amount(), capacity, level.random.nextDouble())) return false;

        int size = AtmosphereGridLayout.CELL_SIZE;
        BlockPos origin = new BlockPos(key.x() * size, key.y() * size, key.z() * size);
        if (!level.hasChunkAt(origin)) return false;
        // Vanilla placement notifies neighboring blocks; defer at unloaded edges.
        for (int dx = -16; dx <= 16; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) {
                if (!level.hasChunkAt(origin.offset(dx, 0, dz))) return false;
            }
        }
        BlockPos chosen = null;
        int airBlocks = 0;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        // Reservoir sampling chooses uniformly among air blocks without allocating a list.
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    probe.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.isOutsideBuildHeight(probe) && level.getBlockState(probe).isAir()
                        && level.random.nextInt(++airBlocks) == 0) chosen = probe.immutable();
                }
            }
        }
        if (chosen == null || !level.setBlockAndUpdate(chosen, Blocks.WATER.defaultBlockState())) return false;
        int consumed = AtmosphereCondensation.consumedAmount(cell.amount());
        grid.set(key, cell.amount() - consumed, serverTicks, capacityAt(level, key));
        waterBlocksCreated++;
        materialCondensed += consumed;
        return true;
    }

    private void sampleAutomaticChunk(MinecraftServer server, ChunkKey key, Set<SourceKey> emittedSources) {
        ServerLevel level = server.getLevel(key.dimension());
        ChunkState state = chunks.get(key);
        if (level == null || state == null || !state.readable) return;
        producerChecks++;
        if (!AtmosphereProducerSchedule.passesChance(tuning().vapor().producerChance(), level.random.nextDouble())) return;
        producerSamples++;

        int localX = level.random.nextInt(16);
        int localZ = level.random.nextInt(16);
        int x = state.chunk.getPos().getMinBlockX() + localX;
        int z = state.chunk.getPos().getMinBlockZ() + localZ;
        int surfaceY = state.chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) + 1;
        BlockPos surface = new BlockPos(x, surfaceY - 1, z);
        if (!level.isInWorldBounds(surface)) return;

        sampleLandingSources(level, state.chunk, x, z, localX, localZ, emittedSources);
        if (state.chunk.getFluidState(surface).is(FluidTags.WATER)) {
            var column = AtmosphereWaterTransitions.evaporationColumn(
                surface.getY(),
                level.getMinBuildHeight(),
                y -> state.chunk.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER),
                y -> state.chunk.getBlockState(new BlockPos(x, y, z)).is(Blocks.MAGMA_BLOCK));
            BlockPos bottom = new BlockPos(x, column.bottomY(), z);
            double temperature = state.chunk.getNoiseBiome(x >> 2, surface.getY() >> 2, z >> 2)
                .value().getModifiedClimateSettings().temperature();
            if (!AtmosphereWaterTransitions.evaporationPasses(
                column.magma(), temperature, level.random::nextDouble)) return;
            evaporateWater(level, bottom);
            if (column.removesSurface(surface.getY())) evaporateWater(level, surface);
        } else if (surfaceY >= level.getSeaLevel() + tuning().vapor().highTerrainBlocksAboveSeaLevel()) {
            AtmosphereGrid.CellKey<ResourceKey<Level>> cell = cellKey(level, new BlockPos(x, surfaceY + 5, z));
            SourceKey source = new SourceKey(SourceKind.HIGH_TERRAIN, level.dimension(), surface.immutable());
            emitSource(level, emittedSources, source, cell, tuning().vapor().highTerrainEmission());
        }
    }

    private void evaporateWater(ServerLevel level, BlockPos pos) {
        var water = level.getBlockState(pos);
        var action = AtmosphereWaterTransitions.evaporationAction(
            water.getFluidState().is(FluidTags.WATER),
            water.hasProperty(BlockStateProperties.WATERLOGGED)
                && water.getValue(BlockStateProperties.WATERLOGGED),
            water.getBlock() instanceof LiquidBlock);
        // Only the successful mutation hook emits; recheck after earlier block updates.
        switch (action) {
            case DRAIN_WATERLOGGED -> level.setBlockAndUpdate(pos,
                water.setValue(BlockStateProperties.WATERLOGGED, false));
            case REMOVE_FLUID -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            case KEEP -> { }
        }
    }

    private void sampleLandingSources(
        ServerLevel level,
        LevelChunk chunk,
        int x,
        int z,
        int localX,
        int localZ,
        Set<SourceKey> emittedSources
    ) {
        BlockPos landing = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1, z);
        BlockPos surface = landing.below();
        if (!level.isInWorldBounds(landing)
            || !level.isInWorldBounds(surface)
            || chunk.getBlockState(surface).isAir()) {
            return;
        }

        BlockPos frozenSurface = new BlockPos(x, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ), z);
        var surfaceState = chunk.getBlockState(frozenSurface);
        if (level.isInWorldBounds(frozenSurface.above())
            && (surfaceState.is(BlockTags.ICE) || surfaceState.is(Blocks.SNOW)
            || surfaceState.is(Blocks.SNOW_BLOCK) || surfaceState.is(Blocks.POWDER_SNOW))) {
            SourceKey frozenSource = new SourceKey(SourceKind.SNOW_ICE, level.dimension(), frozenSurface);
            emitSource(level, emittedSources, frozenSource, cellKey(level, frozenSurface.above()), tuning().vapor().snowIceEmission());
        }

        BlockPos cloud = new BlockPos(x, tuning().vapor().rainCloudHeight(), z);
        if (!level.dimensionType().ultraWarm()
            && !level.dimensionType().hasCeiling()
            && level.isInWorldBounds(cloud)
            && chunk.getBlockState(cloud).isAir()
            && level.isRainingAt(cloud)) {
            var rain = AtmosphereSourceStrength.splitRain(
                tuning().vapor().rainCloudEmission(), level.random.nextInt(tuning().vapor().rainCloudEmission() + 1));
            int upperY = Math.max(landing.getY(), tuning().vapor().rainCloudHeight());
            BlockPos airborne = new BlockPos(x,
                landing.getY() + level.random.nextInt(upperY - landing.getY() + 1), z);
            SourceKey rainSource = new SourceKey(SourceKind.RAIN_CLOUD, level.dimension(), airborne);
            SourceKey groundSource = new SourceKey(SourceKind.RAIN_GROUND, level.dimension(), landing);
            boolean emittedGround = rain.ground() > 0
                && emitSource(level, emittedSources, groundSource, cellKey(level, landing), rain.ground());
            boolean emittedCloud = rain.cloud() > 0
                && emitSource(level, emittedSources, rainSource, cellKey(level, airborne), rain.cloud());
            if (emittedGround || emittedCloud) rainEmissions++;
        }

        int darkGroundEmission = AtmosphereSourceStrength.lightToEmission(
            level.getMaxLocalRawBrightness(landing));
        SourceKey darkGroundSource = new SourceKey(
            SourceKind.DARK_GROUND, level.dimension(), landing.immutable());
        if (darkGroundEmission > 0
            && level.canSeeSky(landing)
            && chunk.getFluidState(surface).isEmpty()
            && emitSource(level, emittedSources, darkGroundSource, cellKey(level, landing), darkGroundEmission)) {
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

    private void syncSmokePlayers(MinecraftServer server, boolean forceSnapshot) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        smokeSyncPlanner.retainPlayers(players.stream().map(ServerPlayer::getUUID).toList());
        for (ServerPlayer player : players) {
            if (forceSnapshot) smokeSyncPlanner.reset(player.getUUID());
            syncSmokePlayer(player);
        }
    }

    private void syncSmokePlayer(ServerPlayer player) {
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        var scope = new ArrayList<SmokeSyncPlanner.Chunk>();
        var visible = new ArrayList<SmokeSyncPlanner.Cell>();
        player.getChunkTrackingView().forEach(chunk -> {
            ChunkState state = ensureImported(player.serverLevel(), chunk.x, chunk.z);
            if (state == null || !state.smokeReadable) return;
            scope.add(new SmokeSyncPlanner.Chunk(chunk.x, chunk.z));
            for (var key : state.smokeCells.keySet()) {
                var cell = smokeGrid.get(key);
                if (cell == null) continue;
                int capacity = smokeCapacityAt(player.serverLevel(), key);
                if (capacity >= 0) {
                    visible.add(new SmokeSyncPlanner.Cell(
                        key.x(), key.y(), key.z(), cell.amount(), capacity));
                }
            }
        });

        for (var update : smokeSyncPlanner.plan(player.getUUID(), dimension, scope, visible)) {
            List<SmokeGridPayload.Chunk> payloadChunks = update.authoritativeChunks().stream()
                .map(chunk -> new SmokeGridPayload.Chunk(chunk.x(), chunk.z())).toList();
            List<SmokeGridPayload.Cell> payloadCells = update.cells().stream()
                .map(cell -> new SmokeGridPayload.Cell(
                    cell.x(), cell.y(), cell.z(), cell.amount(), cell.capacity())).toList();
            PacketDistributor.sendToPlayer(player, new SmokeGridPayload(
                update.dimension().location(), worldId(player.serverLevel().getServer()),
                update.reset(), update.snapshotEnd(), payloadChunks, payloadCells));
            smokePayloadsSent++;
        }
    }

    private void syncMaterialPlayers(MinecraftServer server, boolean forceSnapshot) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<UUID> connected = players.stream().map(ServerPlayer::getUUID).toList();
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            SmokeSyncPlanner<UUID, ResourceKey<Level>> planner = materialSyncPlanners.get(material);
            planner.retainPlayers(connected);
            for (ServerPlayer player : players) {
                if (forceSnapshot) planner.reset(player.getUUID());
                syncMaterialPlayer(player, material, planner);
            }
        }
    }

    private void syncMaterialPlayer(
        ServerPlayer player,
        AtmosphereMaterial material,
        SmokeSyncPlanner<UUID, ResourceKey<Level>> planner
    ) {
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        var scope = new ArrayList<SmokeSyncPlanner.Chunk>();
        var visible = new ArrayList<SmokeSyncPlanner.Cell>();
        player.getChunkTrackingView().forEach(chunk -> {
            ChunkState state = ensureImported(player.serverLevel(), chunk.x, chunk.z);
            if (state == null || !state.materialReadable.contains(material)) return;
            scope.add(new SmokeSyncPlanner.Chunk(chunk.x, chunk.z));
            for (var key : state.materialCells.get(material).keySet()) {
                var cell = materialGrids.get(material).get(key);
                if (cell == null) continue;
                int capacity = materialCapacityAt(player.serverLevel(), material, key);
                if (capacity >= 0) {
                    visible.add(new SmokeSyncPlanner.Cell(
                        key.x(), key.y(), key.z(), cell.amount(), capacity));
                }
            }
        });

        for (var update : planner.plan(player.getUUID(), dimension, scope, visible)) {
            List<MaterialGridPayload.Chunk> payloadChunks = update.authoritativeChunks().stream()
                .map(chunk -> new MaterialGridPayload.Chunk(chunk.x(), chunk.z())).toList();
            List<MaterialGridPayload.Cell> payloadCells = update.cells().stream()
                .map(cell -> new MaterialGridPayload.Cell(
                    cell.x(), cell.y(), cell.z(), cell.amount(), cell.capacity())).toList();
            PacketDistributor.sendToPlayer(player, new MaterialGridPayload(
                material, update.dimension().location(), worldId(player.serverLevel().getServer()),
                update.reset(), update.snapshotEnd(), payloadChunks, payloadCells));
            materialPayloadsSent++;
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
        var cached = ForgeAtmosphereCapacity.get(state.chunk);
        int known = cached.get(key.x(), key.y(), key.z());
        if (known >= 0) return known;
        int size = AtmosphereGrid.CELL_SIZE;
        int air = 0;
        boolean bedrock = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    pos.set(key.x() * size + x, key.y() * size + y, key.z() * size + z);
                    if (level.isInWorldBounds(pos)) {
                        var block = state.chunk.getBlockState(pos);
                        if (ForgeCapacityBlockClassifier.isEmptySpace(block)) air++;
                        if (ForgeCapacityBlockClassifier.blocksDownwardTransfer(block)) bedrock = true;
                    }
                }
            }
        }
        int capacity = AtmosphereGridLayout.capacityForAirBlocks(air);
        cached.put(key.x(), key.y(), key.z(), capacity, bedrock);
        return capacity;
    }

    private int smokeCapacityAt(ServerLevel level, AtmosphereGrid.CellKey<ResourceKey<Level>> key) {
        if (level == null || !level.dimension().equals(key.dimension())) return -1;
        long originY = (long) key.y() * SmokeGridLayout.CELL_SIZE;
        if (originY >= level.getMaxBuildHeight()
            || originY + SmokeGridLayout.CELL_SIZE <= level.getMinBuildHeight()) return 0;
        ChunkState state = ensureImported(level, SmokeGridLayout.chunkCoordinate(key.x()),
            SmokeGridLayout.chunkCoordinate(key.z()));
        if (state == null || !state.smokeReadable) return -1;
        var cache = ForgeSmokeCapacity.get(state.chunk);
        int known = cache.get(key.x(), key.y(), key.z());
        if (known >= 0) return known;
        int air = 0;
        boolean bedrock = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < SmokeGridLayout.CELL_SIZE; y++) {
            for (int z = 0; z < SmokeGridLayout.CELL_SIZE; z++) {
                for (int x = 0; x < SmokeGridLayout.CELL_SIZE; x++) {
                    pos.set(key.x() * SmokeGridLayout.CELL_SIZE + x,
                        key.y() * SmokeGridLayout.CELL_SIZE + y,
                        key.z() * SmokeGridLayout.CELL_SIZE + z);
                    if (level.isInWorldBounds(pos)) {
                        var block = state.chunk.getBlockState(pos);
                        if (ForgeCapacityBlockClassifier.isEmptySpace(block)) air++;
                        if (ForgeCapacityBlockClassifier.blocksDownwardTransfer(block)) bedrock = true;
                    }
                }
            }
        }
        int capacity = SmokeGridLayout.capacityForAirBlocks(air);
        cache.put(key.x(), key.y(), key.z(), capacity, bedrock);
        return capacity;
    }

    private int materialCapacityAt(
        ServerLevel level,
        AtmosphereMaterial material,
        AtmosphereGrid.CellKey<ResourceKey<Level>> key
    ) {
        if (level == null || !level.dimension().equals(key.dimension())) return -1;
        int size = material.cellSize();
        long originY = (long) key.y() * size;
        if (originY >= level.getMaxBuildHeight() || originY + size <= level.getMinBuildHeight()) return 0;
        ChunkState state = ensureImported(level, material.chunkCoordinate(key.x()), material.chunkCoordinate(key.z()));
        if (state == null || !state.materialReadable.contains(material)) return -1;
        MaterialCapacityCache cache = ForgeMaterialCapacity.get(state.chunk, material);
        int known = cache.get(key.x(), key.y(), key.z());
        if (known >= 0) return known;
        int air = 0;
        boolean bedrock = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    pos.set(key.x() * size + x, key.y() * size + y, key.z() * size + z);
                    if (!level.isInWorldBounds(pos)) continue;
                    var block = state.chunk.getBlockState(pos);
                    if (ForgeCapacityBlockClassifier.isEmptySpace(block)) air++;
                    if (ForgeCapacityBlockClassifier.blocksDownwardTransfer(block)) bedrock = true;
                }
            }
        }
        int capacity = material.capacityForAirBlocks(air);
        cache.put(key.x(), key.y(), key.z(), capacity, bedrock);
        return capacity;
    }

    private boolean canTransferDown(
        MinecraftServer server,
        AtmosphereGrid.CellKey<ResourceKey<Level>> source,
        AtmosphereGrid.CellKey<ResourceKey<Level>> destination,
        boolean smoke
    ) {
        if (destination.y() >= source.y()) return true;
        ServerLevel level = server.getLevel(source.dimension());
        if (level == null || !source.dimension().equals(destination.dimension())) return false;
        int capacity = smoke ? smokeCapacityAt(level, source) : capacityAt(level, source);
        if (capacity < 0) return false;
        ChunkState state = chunks.get(new ChunkKey(source.dimension(),
            (smoke ? SmokeGridLayout.chunkCoordinate(source.x()) : AtmosphereGridLayout.chunkCoordinate(source.x())),
            (smoke ? SmokeGridLayout.chunkCoordinate(source.z()) : AtmosphereGridLayout.chunkCoordinate(source.z()))));
        if (state == null) return false;
        int bedrock = smoke
            ? ForgeSmokeCapacity.get(state.chunk).downwardBarrier(source.x(), source.y(), source.z())
            : ForgeAtmosphereCapacity.get(state.chunk).downwardBarrier(source.x(), source.y(), source.z());
        return bedrock == 0;
    }

    private boolean canMaterialTransferDown(
        MinecraftServer server,
        AtmosphereMaterial material,
        AtmosphereGrid.CellKey<ResourceKey<Level>> source,
        AtmosphereGrid.CellKey<ResourceKey<Level>> destination
    ) {
        if (!source.dimension().equals(destination.dimension())) return false;
        if (destination.y() >= source.y()) return true;
        ServerLevel level = server.getLevel(source.dimension());
        if (level == null || materialCapacityAt(level, material, source) < 0) return false;
        ChunkState state = chunks.get(new ChunkKey(source.dimension(), material.chunkCoordinate(source.x()),
            material.chunkCoordinate(source.z())));
        return state != null
            && ForgeMaterialCapacity.get(state.chunk, material).downwardBarrier(source.x(), source.y(), source.z()) == 0;
    }

    private void importPendingChunks(MinecraftServer server) {
        var deferred = new LinkedHashMap<ChunkKey, LevelChunk>();
        var iterator = pendingLoads.entrySet().iterator();
        int examined = 0;
        while (iterator.hasNext() && examined++ < tuning().runtime().maxChunkImportsPerTick() && importsRemaining > 0) {
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
            flushSmokeDirtyChunks();
            flushMaterialDirtyChunks();
            previous.cells.keySet().forEach(grid::remove);
            previous.smokeCells.keySet().forEach(smokeGrid::remove);
            for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
                previous.materialCells.get(material).keySet().forEach(materialGrids.get(material)::remove);
            }
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
        try {
            for (SmokeChunkData.Cell cell : ForgeSmokeStorage.read(chunk)) {
                var cellKey = new AtmosphereGrid.CellKey<>(level.dimension(), cell.x(), cell.y(), cell.z());
                state.smokeCells.put(cellKey, cell);
                smokeGrid.restore(new AtmosphereGrid.Cell<>(
                    cellKey, cell.amount(), 0, Long.MIN_VALUE, serverTicks), serverTicks);
            }
        } catch (IllegalStateException exception) {
            state.smokeReadable = false;
            LOGGER.warn("Skipping smoke simulation for chunk {} in {}: {}", chunk.getPos(),
                level.dimension().location(), exception.getMessage());
        }
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            try {
                for (MaterialChunkData.Cell cell : ForgeMaterialStorage.read(chunk, material)) {
                    var cellKey = new AtmosphereGrid.CellKey<>(level.dimension(), cell.x(), cell.y(), cell.z());
                    state.materialCells.get(material).put(cellKey, cell);
                    materialGrids.get(material).restore(new AtmosphereGrid.Cell<>(
                        cellKey, cell.amount(), 0, Long.MIN_VALUE, serverTicks), serverTicks);
                }
            } catch (IllegalStateException exception) {
                state.materialReadable.remove(material);
                LOGGER.warn("Skipping {} simulation for chunk {} in {}: {}", material.id(), chunk.getPos(),
                    level.dimension().location(), exception.getMessage());
            }
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
                if (state.cells.remove(key) == null) continue;
            } else {
                var persisted = state.cells.get(key);
                if (persisted != null && persisted.amount() == cell.amount()) continue;
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

    private void flushSmokeDirtyChunks() {
        Set<ChunkKey> dirtyChunks = new LinkedHashSet<>();
        for (var key : smokeGrid.drainDirtyKeys()) {
            ChunkKey chunkKey = new ChunkKey(key.dimension(), SmokeGridLayout.chunkCoordinate(key.x()),
                SmokeGridLayout.chunkCoordinate(key.z()));
            ChunkState state = chunks.get(chunkKey);
            if (state == null || !state.smokeReadable) continue;
            var cell = smokeGrid.get(key);
            if (cell == null) {
                if (state.smokeCells.remove(key) == null) continue;
            } else {
                var persisted = state.smokeCells.get(key);
                if (persisted != null && persisted.amount() == cell.amount()) continue;
                state.smokeCells.put(key, new SmokeChunkData.Cell(key.x(), key.y(), key.z(), cell.amount()));
            }
            dirtyChunks.add(chunkKey);
        }
        for (ChunkKey key : dirtyChunks) {
            ChunkState state = chunks.get(key);
            try {
                ForgeSmokeStorage.write(state.chunk, List.copyOf(state.smokeCells.values()));
            } catch (IllegalStateException exception) {
                state.smokeReadable = false;
                state.smokeCells.keySet().forEach(smokeGrid::remove);
                LOGGER.warn("Preserving unreadable smoke data for chunk {} in {}: {}",
                    state.chunk.getPos(), key.dimension().location(), exception.getMessage());
            }
        }
    }

    private void flushMaterialDirtyChunks() {
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            AtmosphereGrid<ResourceKey<Level>> materialGrid = materialGrids.get(material);
            Set<ChunkKey> dirtyChunks = new LinkedHashSet<>();
            for (var key : materialGrid.drainDirtyKeys()) {
                ChunkKey chunkKey = new ChunkKey(key.dimension(), material.chunkCoordinate(key.x()),
                    material.chunkCoordinate(key.z()));
                ChunkState state = chunks.get(chunkKey);
                if (state == null || !state.materialReadable.contains(material)) continue;
                var persistedCells = state.materialCells.get(material);
                var cell = materialGrid.get(key);
                if (cell == null) {
                    if (persistedCells.remove(key) == null) continue;
                } else {
                    var persisted = persistedCells.get(key);
                    if (persisted != null && persisted.amount() == cell.amount()) continue;
                    persistedCells.put(key,
                        new MaterialChunkData.Cell(key.x(), key.y(), key.z(), cell.amount()));
                }
                dirtyChunks.add(chunkKey);
            }
            for (ChunkKey key : dirtyChunks) {
                ChunkState state = chunks.get(key);
                try {
                    ForgeMaterialStorage.write(state.chunk, material,
                        List.copyOf(state.materialCells.get(material).values()));
                } catch (IllegalStateException exception) {
                    state.materialReadable.remove(material);
                    state.materialCells.get(material).keySet().forEach(materialGrid::remove);
                    LOGGER.warn("Preserving unreadable {} data for chunk {} in {}: {}", material.id(),
                        state.chunk.getPos(), key.dimension().location(), exception.getMessage());
                }
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
        private final Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, SmokeChunkData.Cell> smokeCells = new LinkedHashMap<>();
        private final Map<AtmosphereMaterial,
            Map<AtmosphereGrid.CellKey<ResourceKey<Level>>, MaterialChunkData.Cell>> materialCells =
                new EnumMap<>(AtmosphereMaterial.class);
        private final EnumSet<AtmosphereMaterial> materialReadable = EnumSet.allOf(AtmosphereMaterial.class);
        private boolean readable = true;
        private boolean smokeReadable = true;

        private ChunkState(LevelChunk chunk) {
            this.chunk = chunk;
            for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
                materialCells.put(material, new LinkedHashMap<>());
            }
        }
    }

    private AtmosphereGrid.CellKey<ResourceKey<Level>> cellKey(ServerLevel level, BlockPos pos) {
        return new AtmosphereGrid.CellKey<>(
            level.dimension(),
            AtmosphereGrid.cellCoordinate(pos.getX()),
            AtmosphereGrid.cellCoordinate(pos.getY()),
            AtmosphereGrid.cellCoordinate(pos.getZ())
        );
    }

    private AtmosphereGrid.CellKey<ResourceKey<Level>> smokeCellKey(ServerLevel level, BlockPos pos) {
        return new AtmosphereGrid.CellKey<>(level.dimension(),
            SmokeGridLayout.cellCoordinate(pos.getX()),
            SmokeGridLayout.cellCoordinate(pos.getY()),
            SmokeGridLayout.cellCoordinate(pos.getZ()));
    }

    private AtmosphereGrid.CellKey<ResourceKey<Level>> materialCellKey(
        ServerLevel level,
        AtmosphereMaterial material,
        BlockPos pos
    ) {
        return new AtmosphereGrid.CellKey<>(level.dimension(),
            material.cellCoordinate(pos.getX()), material.cellCoordinate(pos.getY()),
            material.cellCoordinate(pos.getZ()));
    }

    private BlockPos materialCellOrigin(
        AtmosphereMaterial material,
        AtmosphereGrid.CellKey<ResourceKey<Level>> key
    ) {
        return new BlockPos(key.x() * material.cellSize(), key.y() * material.cellSize(),
            key.z() * material.cellSize());
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
        HIGH_TERRAIN,
        RAIN_CLOUD,
        DARK_GROUND,
        SNOW_ICE,
        RAIN_GROUND
    }

    private record SourceKey(SourceKind kind, ResourceKey<Level> dimension, BlockPos pos) {
    }
}
