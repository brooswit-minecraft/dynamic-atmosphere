package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * flags regardless of cause (player break, explosion, mob, or a Create-style
 * machine).</p>
 *
 * <p>A piston move is a special case: it vacates its source position (which
 * looks identical to a removal at that position) while the same block still
 * exists at its destination. {@link #onPistonPre}/{@link #onPistonPost}
 * exempt a piston's own source positions from rolling for exactly the
 * duration of that move, using {@link PistonEvent}, which — unlike
 * {@code NeighborNotifyEvent} — exposes the pusher's structure resolver
 * before the move happens.</p>
 *
 * <p><strong>Accepted gaps, stated rather than papered over:</strong> a
 * removal path whose write deliberately suppresses neighbor-notify flags
 * fires no event here and is missed; the overwhelming majority of vanilla
 * and Create-style removal does not do this, since it would leave redstone
 * and observers silently missing the same change too. A Create contraption
 * that <em>assembles</em> around a magma block — lifting it out of the world
 * as part of a moving structure rather than removing it in place — is a
 * second, narrower case of the same move-not-remove distinction the piston
 * handling above addresses, but is not itself exempted; it was not found to
 * be in scope for what this ticket asked to cover.</p>
 */
public final class MagmaLavaGameplay {

    private final MagmaPositionTracker<DimPos> tracker = new MagmaPositionTracker<>();
    private final Map<ChunkKey, Set<DimPos>> chunkMembership = new HashMap<>();
    private final Map<DimPos, List<DimPos>> pistonBatches = new HashMap<>();

    record DimPos(ResourceKey<Level> dimension, BlockPos pos) { }

    private record ChunkKey(ResourceKey<Level> dimension, ChunkPos chunk) { }

    private MagmaLavaGameplay() { }

    public static MagmaLavaGameplay create() {
        return new MagmaLavaGameplay();
    }

    /** Pure roll, separable from the game hook so it is unit-testable without a running game. */
    static boolean rollsLava(double chance, double roll) {
        return AtmosphereProducerSchedule.passesChance(chance, roll);
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // Load may precede FULL promotion, so a world query made off the server thread is deferred
        // to it (matching ForgeAtmospherePrototype.onChunkLoad); already on it, the scan runs inline,
        // in this same call, with no deferral.
        Runnable scan = () -> scanChunk(level, chunk);
        if (level.getServer().isSameThread()) {
            scan.run();
        } else {
            level.getServer().execute(scan);
        }
    }

    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<DimPos> members = chunkMembership.remove(new ChunkKey(level.dimension(), event.getChunk().getPos()));
        if (members != null) tracker.forgetAll(members);
    }

    public void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;

        List<DimPos> batch = new ArrayList<>();
        for (BlockPos pos : resolver.getToPush()) {
            DimPos key = new DimPos(level.dimension(), pos.immutable());
            tracker.markPistonExempt(key);
            batch.add(key);
        }
        // getToDestroy() positions are genuinely destroyed, not moved, and stay eligible to roll.
        if (!batch.isEmpty()) {
            pistonBatches.put(new DimPos(level.dimension(), event.getPos().immutable()), batch);
        }
    }

    public void onPistonPost(PistonEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<DimPos> batch = pistonBatches.remove(new DimPos(level.dimension(), event.getPos().immutable()));
        // Clears the exemption whether or not NeighborNotifyEvent already consumed it, so a move
        // that was recorded on Pre but never actually completed can't leak a permanent exemption.
        if (batch != null) batch.forEach(tracker::clearPistonExempt);
    }

    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        DimPos key = new DimPos(level.dimension(), pos.immutable());
        boolean isMagmaNow = event.getState().is(Blocks.MAGMA_BLOCK);

        if (isMagmaNow) {
            chunkMembership.computeIfAbsent(new ChunkKey(level.dimension(), new ChunkPos(pos)), ignored -> new HashSet<>())
                .add(key);
        }
        if (!tracker.observe(key, isMagmaNow)) return;

        DynamicAtmosphereServerConfig.Magma config = DynamicAtmosphereServerConfig.snapshot().magma();
        if (!rollsLava(config.breakLavaChance(), level.random.nextDouble())) return;

        // Re-read state at write time rather than trusting the event's snapshot: something else
        // (another mod, a second event handler) may have already written to pos in the meantime.
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || current.canBeReplaced()) {
            level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        }
    }

    private void scanChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        Set<DimPos> found = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            // Skips the getBlockState walk entirely for a section whose palette has no magma at all.
            if (section == null || !section.maybeHas(state -> state.is(Blocks.MAGMA_BLOCK))) continue;
            int baseY = chunk.getMinBuildHeight() + i * 16;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = baseY; y < baseY + 16; y++) {
                        cursor.set(minX + x, y, minZ + z);
                        if (chunk.getBlockState(cursor).is(Blocks.MAGMA_BLOCK)) {
                            if (found == null) found = new HashSet<>();
                            found.add(new DimPos(level.dimension(), cursor.immutable()));
                        }
                    }
                }
            }
        }
        if (found != null) {
            found.forEach(position -> tracker.observe(position, true));
            chunkMembership.put(new ChunkKey(level.dimension(), chunkPos), found);
        }
    }
}
