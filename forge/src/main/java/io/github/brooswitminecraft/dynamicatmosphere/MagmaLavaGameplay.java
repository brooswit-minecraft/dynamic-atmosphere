package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Broken-magma lava aftermath: any removal of a magma block, by any actor or
 * mechanism, independently rolls a config-backed chance to leave a lava
 * source behind.
 *
 * <p>No mixin is available to intercept the low-level block write directly,
 * so this instead tracks which loaded positions currently hold magma (seeded
 * by a one-time scan on chunk load, then kept live by the same hook that
 * detects removal) and reacts to {@link BlockEvent.NeighborNotifyEvent} —
 * the same "BUD" style hook vanilla itself uses to notify redstone of a
 * physics update. It fires for a standard {@code Level#setBlock} on default
 * flags regardless of cause (player break, explosion, piston, mob, or a
 * Create-style machine), which a player-only hook like
 * {@link BlockEvent.BreakEvent} does not.</p>
 *
 * <p>The roll's own write is deferred to {@link ServerTickEvent.Post}, not
 * applied immediately in {@code onNeighborNotify}: a piston push notifies at
 * its vacated origin mid-animation, then overwrites that same position again
 * later in the SAME tick once the piston structure resolver settles (to
 * {@code piston_head} or air) — an immediate write there is clobbered by that
 * later same-tick write every time (confirmed live: the roll fired and set
 * lava, then the piston's own settle silently replaced it). Writing at the
 * tick's end instead means our write is the last one standing, for a piston
 * push and any other transient multi-step same-tick resolution.</p>
 */
public final class MagmaLavaGameplay {

    private final Map<ChunkKey, Set<BlockPos>> magmaByChunk = new HashMap<>();
    private final Map<ResourceKey<Level>, Set<BlockPos>> pendingLava = new HashMap<>();

    private record ChunkKey(ResourceKey<Level> dimension, ChunkPos chunk) { }

    private MagmaLavaGameplay() { }

    public static MagmaLavaGameplay create() {
        return new MagmaLavaGameplay();
    }

    /** Pure: a genuine removal is a tracked magma position that is no longer magma. */
    static boolean isRemoval(boolean wasMagma, boolean isMagmaNow) {
        return wasMagma && !isMagmaNow;
    }

    /** Pure: lava may only be written where nothing but air or a fluid now occupies the removed magma's position. */
    static boolean canCommitLava(BlockState current) {
        return current.isAir() || current.getBlock() instanceof LiquidBlock;
    }

    /** Pure roll, separable from the game hook so it is unit-testable without a running game. */
    static boolean rollsLava(double chance, double roll) {
        return AtmosphereProducerSchedule.passesChance(chance, roll);
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // Load may precede FULL promotion; defer the block-state scan to the tick, matching
        // ForgeAtmospherePrototype's own chunk-load handling.
        Runnable scan = () -> scanChunk(level, chunk);
        if (level.getServer().isSameThread()) {
            scan.run();
        } else {
            level.getServer().execute(scan);
        }
    }

    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            magmaByChunk.remove(new ChunkKey(level.dimension(), event.getChunk().getPos()));
        }
    }

    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        ChunkKey key = new ChunkKey(level.dimension(), new ChunkPos(pos));
        Set<BlockPos> tracked = magmaByChunk.get(key);
        boolean wasMagma = tracked != null && tracked.contains(pos);
        boolean isMagmaNow = event.getState().is(Blocks.MAGMA_BLOCK);

        if (isMagmaNow) {
            // Covers both a fresh placement and magma settling back after a transient change;
            // an unchanged magma-to-magma write never reaches here since vanilla's own
            // setBlockState short-circuits when the new state equals the old one.
            magmaByChunk.computeIfAbsent(key, ignored -> new HashSet<>()).add(pos.immutable());
            return;
        }
        if (tracked != null) {
            tracked.remove(pos);
            if (tracked.isEmpty()) magmaByChunk.remove(key);
        }
        if (!isRemoval(wasMagma, isMagmaNow)) return;

        DynamicAtmosphereServerConfig.Magma config = DynamicAtmosphereServerConfig.snapshot().magma();
        if (rollsLava(config.breakLavaChance(), level.random.nextDouble())) {
            // Deferred to tick-end (see class doc): writing here would be clobbered by a
            // same-tick follow-up write, e.g. a piston settling into its final resting block.
            pendingLava.computeIfAbsent(level.dimension(), ignored -> new HashSet<>()).add(pos.immutable());
        }
    }

    public void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer(), event.getServer().tickRateManager().runsNormally());
    }

    /**
     * MINECRAFT-384: split out from {@link #onServerTick} for the same reason as
     * {@code ForgeAtmospherePrototype.tick} -- {@code ServerTickEvent.Post} still fires during
     * platform {@code /tick freeze}, so without this gate a pending lava commit (itself only a
     * deferred write of an already-rolled decision, see class doc) could land while the
     * simulated world is frozen. Gating here defers the pending entries to the next
     * non-frozen tick rather than dropping them.
     */
    void tick(MinecraftServer server, boolean runsNormally) {
        if (!runsNormally) return;
        if (pendingLava.isEmpty()) return;
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : pendingLava.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) continue;
            for (BlockPos pos : entry.getValue()) {
                // Commit only into a vacated position; a block that took the place within the
                // same tick (piston head, placed container, regrown magma) is left untouched.
                if (!canCommitLava(level.getBlockState(pos))) continue;
                level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
            }
        }
        pendingLava.clear();
    }

    private void scanChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        Set<BlockPos> found = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                    cursor.set(minX + x, y, minZ + z);
                    if (chunk.getBlockState(cursor).is(Blocks.MAGMA_BLOCK)) {
                        if (found == null) found = new HashSet<>();
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        if (found != null) {
            magmaByChunk.put(new ChunkKey(level.dimension(), chunkPos), found);
        }
    }
}
