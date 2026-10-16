package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Phase 4 (design brief section 32): "A Transfer Sink consumes material locally but does
 * not destroy it: it re-emits it at the linked destination." Built entirely on the existing
 * generic material grid's public emit/consume boundary ({@link DynamicAtmosphereMod}), so no
 * scenario-specific Java logic is needed for either leg:
 *
 * <ul>
 *   <li><b>Nether portals</b> (section 33): Nether Gas crosses between the Nether and the
 *       Overworld. The vanilla 8:1 coordinate scale is only a SEARCH ANCHOR, not the actual
 *       linked exit -- vanilla's own portal search can place the real exit at a displaced
 *       X/Y/Z. This sink therefore only transfers to a destination it has itself already
 *       observed (via its own loaded-chunk scanning, below) within {@link #NETHER_LINK_SEARCH_RADIUS}
 *       blocks of the scaled anchor; if nothing verified is nearby, the transfer is declined
 *       for this pass rather than guessed at. Smoke is also named in section 33's example, but
 *       Smoke uses a separate legacy grid without a public cross-grid consume API; extending
 *       that grid is left to a follow-up rather than risked here.</li>
 *   <li><b>End portals</b> (section 34/35): every generic-grid material near an End portal
 *       drains into The End -- at a verified, already-observed End portal position there (The
 *       End's own exit portal is not coordinate-linked to the source at all, unlike the Nether),
 *       where {@link DimensionAtmosphereConfigs}' global dissipation multiplier makes it vanish
 *       almost immediately. "This is not a special industrial feature. It is an emergent use of
 *       normal atmospheric rules" (section 48).</li>
 * </ul>
 *
 * <p><b>Verified destinations, never a forced load:</b> this class remembers every portal
 * block position its own loaded-chunk scan has observed, per dimension, replacing each chunk's
 * remembered positions whenever that chunk is rescanned (so a destroyed portal falls out of the
 * cache the next time its chunk is visited). That alone still leaves a window: a portal destroyed
 * in a still-loaded chunk stays cached until that chunk's own next scheduled scan. So before
 * selecting a candidate, any one of them whose chunk is CURRENTLY loaded is re-checked live
 * against the expected block right now ({@link #liveCandidates}) -- a candidate only survives
 * that check if it is still the expected portal block, or if its chunk isn't loaded at all (and
 * therefore can't be verified without forcing a load, so it is trusted from the cache instead).
 * A transfer only proceeds against a surviving position -- never a freshly computed/guessed one,
 * and never one that requires loading or generating anything.</p>
 *
 * <p><b>Conservation under a partial/clamped emit:</b> {@link DynamicAtmosphereMod#emitMaterial}
 * returns a boolean, but the underlying grid can clamp an emit short of what was requested while
 * still reporting success (e.g. a near-full destination). The actual accepted amount is therefore
 * measured from the destination's own before/after state ({@link #acceptedAmount}), and only that
 * much is ever debited from the source -- never the full planned amount regardless of what
 * actually landed. If the source debit itself then fails, the accepted amount is rolled back out
 * of the destination so material is never duplicated. An unreachable destination (chunk not
 * already loaded/tracked) simply fails the emit for this pass, so a declined or failed transfer
 * never destroys material either -- it is retried on a later producer pass instead. A portal links
 * two independently simulated regions; nothing here forces either one to stay active just because
 * the other is (design brief section 50's "ONE THING TO WATCH").</p>
 */
public final class PortalTransferSink {
    static final int NETHER_PORTAL_COORDINATE_SCALE = 8;
    static final int NETHER_LINK_SEARCH_RADIUS = 128;

    private final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> knownNetherPortals = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> knownEndPortals = new HashMap<>();

    public void onLoadedChunkProducerCheck(ServerLevel level, LevelChunk chunk) {
        MinecraftServer server = level.getServer();
        ChunkPos chunkPos = chunk.getPos();
        var netherPortalsHere = new HashSet<BlockPos>();
        var endPortalsHere = new HashSet<BlockPos>();
        var sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            boolean hasNetherPortal = section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL));
            boolean hasEndPortal = section.maybeHas(state -> state.is(Blocks.END_PORTAL));
            if (!hasNetherPortal && !hasEndPortal) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var state = section.getBlockState(x, y, z);
                var pos = new BlockPos(chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z);
                if (hasNetherPortal && state.is(Blocks.NETHER_PORTAL)) netherPortalsHere.add(pos);
                if (hasEndPortal && state.is(Blocks.END_PORTAL)) endPortalsHere.add(pos);
            }
        }
        recordObserved(knownNetherPortals, level.dimension(), chunkPos.toLong(), netherPortalsHere);
        recordObserved(knownEndPortals, level.dimension(), chunkPos.toLong(), endPortalsHere);

        int throughput = DynamicAtmosphereServerConfig.snapshot().portalTransferSink().throughputPerPortalBlock();
        if (throughput <= 0) return;
        for (BlockPos pos : netherPortalsHere) transferNetherPortal(server, level, pos, throughput);
        for (BlockPos pos : endPortalsHere) transferEndPortal(server, level, pos, throughput);
    }

    private static void recordObserved(
        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> registry,
        ResourceKey<Level> dimension, long chunkKey, Set<BlockPos> observedThisScan
    ) {
        var byChunk = registry.computeIfAbsent(dimension, ignored -> new HashMap<>());
        if (observedThisScan.isEmpty()) {
            byChunk.remove(chunkKey);
        } else {
            byChunk.put(chunkKey, Set.copyOf(observedThisScan));
        }
    }

    private static Collection<BlockPos> knownPositions(
        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> registry, ResourceKey<Level> dimension
    ) {
        var byChunk = registry.get(dimension);
        if (byChunk == null || byChunk.isEmpty()) return Set.of();
        var all = new HashSet<BlockPos>();
        byChunk.values().forEach(all::addAll);
        return all;
    }

    /** Pure, so the vanilla 8:1 Nether/Overworld coordinate scale is unit-testable without a live level. */
    static BlockPos netherToOverworld(BlockPos pos) {
        return new BlockPos(pos.getX() * NETHER_PORTAL_COORDINATE_SCALE, pos.getY(),
            pos.getZ() * NETHER_PORTAL_COORDINATE_SCALE);
    }

    static BlockPos overworldToNether(BlockPos pos) {
        return new BlockPos(pos.getX() / NETHER_PORTAL_COORDINATE_SCALE, pos.getY(),
            pos.getZ() / NETHER_PORTAL_COORDINATE_SCALE);
    }

    /**
     * Horizontal-only (X/Z) nearest candidate within {@code maxRadius}, matching vanilla
     * portal search's own horizontal-first semantics closely enough to find a displaced exit
     * without requiring an exact Y or coordinate match. Pure and injectable: the candidates set
     * is a plain {@code Collection<BlockPos>}, never a live level lookup.
     */
    static Optional<BlockPos> nearestWithinRadius(BlockPos anchor, Collection<BlockPos> candidates, int maxRadius) {
        long maxDistanceSquared = (long) maxRadius * maxRadius;
        BlockPos best = null;
        long bestDistanceSquared = Long.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            long dx = (long) candidate.getX() - anchor.getX();
            long dz = (long) candidate.getZ() - anchor.getZ();
            long distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= maxDistanceSquared && distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The End's exit portal is not coordinate-linked to any source position; any known one will do. */
    static Optional<BlockPos> any(Collection<BlockPos> candidates) {
        return candidates.stream().findFirst();
    }

    /**
     * Pure, injectable filter dropping a candidate only when {@code isPlausible} says it can no
     * longer be trusted (verified, via an already-loaded chunk, to no longer be the expected
     * portal block). A candidate in a chunk that is not currently loaded cannot be verified
     * without forcing a load, so it stays -- same "no-force-load" rule as everywhere else here.
     */
    static Collection<BlockPos> filterStale(Collection<BlockPos> candidates, Predicate<BlockPos> isPlausible) {
        return candidates.stream().filter(isPlausible).toList();
    }

    /**
     * Drops a known position only when its chunk is CURRENTLY loaded and the live block there no
     * longer matches {@code expected}. Never forces a load to check -- an unloaded candidate is
     * kept (unverifiable, trusted from the cache) rather than forced to resolve.
     */
    private Collection<BlockPos> liveCandidates(
        ServerLevel destinationLevel, Collection<BlockPos> candidates, Predicate<BlockState> expected
    ) {
        return filterStale(candidates, pos -> {
            ChunkPos chunkPos = new ChunkPos(pos);
            LevelChunk chunk = destinationLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            return chunk == null || expected.test(chunk.getBlockState(pos));
        });
    }

    /**
     * Pure transfer sizing, so source conservation is unit-testable without a live level:
     * nothing is planned when the source is empty, the throughput is non-positive, or no
     * destination was resolved -- the caller must then leave the source untouched.
     */
    record TransferPlan(int amount) {
        static final TransferPlan NONE = new TransferPlan(0);

        boolean active() { return amount > 0; }
    }

    static TransferPlan plan(int sourceAmount, int throughput, boolean destinationResolved) {
        if (sourceAmount <= 0 || throughput <= 0 || !destinationResolved) return TransferPlan.NONE;
        return new TransferPlan(Math.min(sourceAmount, throughput));
    }

    /**
     * Pure, so partial/clamped-emit conservation is unit-testable without a live level: the
     * destination grid can clamp an emit short of what was requested while still reporting
     * success, so the actual amount accepted is measured from the destination's own before/after
     * state rather than assumed to equal the planned amount.
     */
    static int acceptedAmount(int destinationBefore, int destinationAfter) {
        return Math.max(0, destinationAfter - destinationBefore);
    }

    private void transferNetherPortal(MinecraftServer server, ServerLevel level, BlockPos pos, int throughput) {
        ResourceKey<Level> destinationDimension;
        BlockPos anchor;
        if (level.dimension().equals(Level.NETHER)) {
            destinationDimension = Level.OVERWORLD;
            anchor = netherToOverworld(pos);
        } else if (level.dimension().equals(Level.OVERWORLD)) {
            destinationDimension = Level.NETHER;
            anchor = overworldToNether(pos);
        } else {
            return;
        }
        ServerLevel destinationLevel = server.getLevel(destinationDimension);
        if (destinationLevel == null) return;
        var candidates = liveCandidates(destinationLevel,
            knownPositions(knownNetherPortals, destinationDimension), state -> state.is(Blocks.NETHER_PORTAL));
        Optional<BlockPos> destination = nearestWithinRadius(anchor, candidates, NETHER_LINK_SEARCH_RADIUS);
        if (destination.isEmpty()) return;
        transfer(level, pos, destinationLevel, destination.get(), AtmosphereMaterial.NETHER_GAS, throughput);
    }

    private void transferEndPortal(MinecraftServer server, ServerLevel level, BlockPos pos, int throughput) {
        if (level.dimension().equals(Level.END)) return;
        ServerLevel endLevel = server.getLevel(Level.END);
        if (endLevel == null) return;
        var candidates = liveCandidates(endLevel, knownPositions(knownEndPortals, Level.END), state -> state.is(Blocks.END_PORTAL));
        Optional<BlockPos> destination = any(candidates);
        if (destination.isEmpty()) return;
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            transfer(level, pos, endLevel, destination.get(), material, throughput);
        }
    }

    private void transfer(
        ServerLevel sourceLevel, BlockPos sourcePos,
        ServerLevel destinationLevel, BlockPos destinationPos,
        AtmosphereMaterial material, int throughput
    ) {
        var sourceState = DynamicAtmosphereMod.materialState(sourceLevel, material, sourcePos);
        var transferPlan = plan(sourceState.map(AtmosphereMaterialState::amount).orElse(0), throughput, true);
        if (!transferPlan.active()) return;
        var destinationBefore = DynamicAtmosphereMod.materialState(destinationLevel, material, destinationPos);
        if (destinationBefore.isEmpty()) return;
        if (!DynamicAtmosphereMod.emitMaterial(destinationLevel, material, destinationPos, transferPlan.amount())) return;
        var destinationAfter = DynamicAtmosphereMod.materialState(destinationLevel, material, destinationPos);
        int accepted = acceptedAmount(destinationBefore.get().amount(),
            destinationAfter.map(AtmosphereMaterialState::amount).orElse(destinationBefore.get().amount()));
        int debit = Math.min(transferPlan.amount(), accepted);
        if (debit <= 0) return;
        if (!DynamicAtmosphereMod.consumeMaterial(sourceLevel, material, sourcePos, debit) && accepted > 0) {
            // The destination already accepted material but the source couldn't be debited:
            // roll back the exact accepted amount so nothing is ever duplicated.
            DynamicAtmosphereMod.consumeMaterial(destinationLevel, material, destinationPos, accepted);
        }
    }

    private PortalTransferSink() { }

    public static PortalTransferSink create() { return new PortalTransferSink(); }
}
