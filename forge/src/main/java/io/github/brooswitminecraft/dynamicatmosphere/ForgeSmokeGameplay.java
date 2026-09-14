package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Dedicated Smoke gameplay adapter. The parent runtime owns grid mutation and calls drain once per tick. */
@EventBusSubscriber(modid = DynamicAtmosphereMod.MODID)
public final class ForgeSmokeGameplay {
    private record Queues(SmokeEmissionQueue<BlockPos> smoke, SmokeEmissionQueue<BlockPos> vapor) {
        Queues() { this(new SmokeEmissionQueue<>(), new SmokeEmissionQueue<>()); }
    }
    private static final Map<ServerLevel, Queues> PENDING = new IdentityHashMap<>();

    public static int sourceAmount(BlockState state) { return ForgeSmokeSources.ongoing(state); }

    public static void observeMutation(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState next) {
        if (previous == null || !(chunk.getLevel() instanceof ServerLevel level)
            || !level.getServer().isSameThread()) return;
        int smoke = ForgeSmokeSources.transition(previous, next);
        int vapor = ForgeSnowSources.transition(previous, next);
        if (smoke > 0) enqueue(level, pos, smoke, true);
        if (vapor > 0) enqueue(level, pos, vapor, false);
    }

    public static void explosionBurst(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server && server.getServer().isSameThread()) {
            enqueue(server, pos, SmokeProducerRules.explosionBurst(true), true);
        }
    }

    /** Called after the actual per-block explosion callback, not from its tentative affected-block list. */
    public static void explosionBlock(Level level, BlockPos pos, BlockState previous) {
        if (previous == null || previous.isAir() || !(level instanceof ServerLevel server)
            || !server.getServer().isSameThread()) return;
        LevelChunk chunk = loaded(server, pos);
        if (chunk == null) return;
        BlockState next = chunk.getBlockState(pos);
        // Waterlogged destruction can leave water: the original block still ceased to exist.
        if (previous.getBlock() != next.getBlock()) enqueue(server, pos, SmokeProducerRules.explosionBlockAmount(), true);
    }

    private static void enqueue(ServerLevel level, BlockPos pos, int amount, boolean smoke) {
        if (!level.isInWorldBounds(pos) || loaded(level, pos) == null) return;
        int size = smoke ? SmokeGridLayout.CELL_SIZE : AtmosphereGridLayout.CELL_SIZE;
        BlockPos origin = new BlockPos(Math.floorDiv(pos.getX(), size) * size,
            Math.floorDiv(pos.getY(), size) * size, Math.floorDiv(pos.getZ(), size) * size);
        Queues queues = PENDING.computeIfAbsent(level, ignored -> new Queues());
        (smoke ? queues.smoke() : queues.vapor()).offer(origin, amount);
    }

    /** At most 128 cells per material per tick. Sinks return true only when accepted; rejected entries wait. */
    public static void drain(ServerLevel level, BiPredicate<BlockPos, Integer> smokeSink,
                             BiPredicate<BlockPos, Integer> vaporSink) {
        requireServerThread(level);
        Queues queues = PENDING.get(level);
        if (queues == null) return;
        drain(level, queues.smoke(), smokeSink);
        drain(level, queues.vapor(), vaporSink);
    }

    private static void drain(ServerLevel level, SmokeEmissionQueue<BlockPos> queue,
                              BiPredicate<BlockPos, Integer> sink) {
        for (var emission : queue.drain(SmokeEmissionQueue.DEFAULT_DRAIN_LIMIT)) {
            if (loaded(level, emission.key()) == null || !sink.test(emission.key(), emission.amount())) {
                queue.offer(emission.key(), emission.amount());
            }
        }
    }

    public static long rejectedAmount(ServerLevel level) {
        Queues queues = PENDING.get(level);
        return queues == null ? 0 : queues.smoke().rejectedAmount() + queues.vapor().rejectedAmount();
    }

    /** Origin is an aligned Smoke cell. Parent subtracts the returned amount through its grid API. */
    public static int processTurn(ServerLevel level, BlockPos cellOrigin, int currentAmount) {
        requireServerThread(level);
        if (currentAmount <= 0 || loaded(level, cellOrigin) == null) return 0;
        int size = SmokeGridLayout.CELL_SIZE;
        if (Math.floorMod(cellOrigin.getX(), size) != 0 || Math.floorMod(cellOrigin.getY(), size) != 0
            || Math.floorMod(cellOrigin.getZ(), size) != 0) throw new IllegalArgumentException("Unaligned Smoke cell");
        LevelChunk chunk = loaded(level, cellOrigin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int remaining = currentAmount;
        remaining -= SmokeLeafEffect.attempt(remaining, level.random::nextDouble,
            index -> {
                position(pos, cellOrigin, index);
                return level.isInWorldBounds(pos) && chunk.getBlockState(pos).is(BlockTags.LEAVES);
            }, index -> {
                position(pos, cellOrigin, index);
                return neighborsLoaded(level, pos) && chunk.getBlockState(pos).is(BlockTags.LEAVES)
                    && level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            });
        remaining -= SmokeFarmlandEffect.attempt(remaining, level.random::nextDouble,
            index -> {
                position(pos, cellOrigin, index);
                return level.isInWorldBounds(pos) && chunk.getBlockState(pos).is(Blocks.FARMLAND);
            }, index -> {
                position(pos, cellOrigin, index);
                return neighborsLoaded(level, pos) && chunk.getBlockState(pos).is(Blocks.FARMLAND)
                    && level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            });
        AABB bounds = new AABB(cellOrigin.getX(), cellOrigin.getY(), cellOrigin.getZ(),
            cellOrigin.getX() + size, cellOrigin.getY() + size, cellOrigin.getZ() + size);
        remaining -= SmokeVillagerEffect.attempt(remaining, level.random::nextDouble,
            () -> {
                var villagers = new ArrayList<Villager>();
                level.getEntities(EntityTypeTest.forClass(Villager.class), bounds, entity -> true,
                    villagers, SmokeVillagerEffect.MAX_CANDIDATES);
                return villagers.iterator();
            }, villager -> bounds.contains(villager.position()) && ForgeSmokeVillagerEffect.eligible(villager),
            ForgeSmokeVillagerEffect::convert);
        remaining -= SmokeDissipation.amount(remaining, level.random::nextDouble);
        return currentAmount - remaining;
    }

    private static void position(BlockPos.MutableBlockPos target, BlockPos origin, int index) {
        int size = SmokeGridLayout.CELL_SIZE;
        target.set(origin.getX() + index % size, origin.getY() + index / (size * size),
            origin.getZ() + index / size % size);
    }

    private static boolean neighborsLoaded(ServerLevel level, BlockPos pos) {
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            if (level.getChunkSource().getChunkNow(Math.floorDiv(pos.getX() + x, 16),
                Math.floorDiv(pos.getZ() + z, 16)) == null) return false;
        }
        return level.isInWorldBounds(pos);
    }

    private static LevelChunk loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Smoke gameplay requires server thread");
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { PENDING.clear(); }

    private ForgeSmokeGameplay() { }
}
