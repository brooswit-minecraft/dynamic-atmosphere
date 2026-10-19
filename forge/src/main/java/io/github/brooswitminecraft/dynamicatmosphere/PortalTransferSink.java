package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * cache the next time its chunk is visited). The historical cache can still hold a position whose
 * chunk is not loaded right now, so eligibility for SELECTION this pass is stricter than mere
 * cache membership: {@link #eligibleCandidates} keeps only positions whose chunk is currently
 * loaded AND whose live block still matches the expected portal block right now. An unloaded
 * candidate is neither trusted nor force-loaded to check -- it is simply not eligible this pass,
 * so it can never be selected ahead of a farther but currently reachable, verified exit, and it
 * can never cause a transfer to repeatedly pick a dead end it cannot complete. It stays in the
 * historical cache in case its chunk loads again later. A transfer only proceeds against an
 * eligible position -- never a freshly computed/guessed one, and never one that requires loading
 * or generating anything.</p>
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

    /**
     * MINECRAFT-435: {@code netherPortalsHere}/{@code endPortalsHere} are the positions
     * {@link PortalChunkScanner} already found for this chunk visit -- scanning itself no
     * longer happens here, since {@link ObsidianPowderGameplay} needs the identical Nether
     * portal positions on the same cadence and the two scans were otherwise redundant.
     */
    public void onLoadedChunkProducerCheck(
        ServerLevel level, LevelChunk chunk, Set<BlockPos> netherPortalsHere, Set<BlockPos> endPortalsHere
    ) {
        MinecraftServer server = level.getServer();
        long chunkKey = chunk.getPos().toLong();
        recordObserved(knownNetherPortals, level.dimension(), chunkKey, netherPortalsHere);
        recordObserved(knownEndPortals, level.dimension(), chunkKey, endPortalsHere);

        int throughput = DynamicAtmosphereServerConfig.snapshot().portalTransferSink().throughputPerPortalBlock();
        if (throughput <= 0) return;
        // Pass-local: every position below is checked against the identical, already-recorded
        // registry snapshot for this one synchronous call, so a resolved destination chunk-box
        // is safely reused across positions that land on it rather than re-resolved each time.
        var netherCandidateCache = new HashMap<List<Integer>, Collection<BlockPos>>();
        for (BlockPos pos : netherPortalsHere) transferNetherPortal(server, level, pos, throughput, netherCandidateCache);
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

    /**
     * MINECRAFT-435: bounded alternative to {@link #knownPositions} -- touches only the chunk
     * entries whose chunk coordinates fall inside the given box, instead of flattening every
     * chunk the destination dimension has ever recorded a portal in. The box is expected to
     * already cover every block position within the real search radius (chunk-granular, so it
     * is allowed to be a little generous at the edges); {@link #nearestWithinRadius} still
     * applies the exact per-block distance check afterward, so a chunk-box-level over-inclusion
     * here can never admit a candidate that search radius would otherwise have rejected. Pure
     * and injectable, like {@link #knownPositions}, so it is testable with a fake registry map.
     */
    static Collection<BlockPos> knownPositionsNear(
        Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> registry, ResourceKey<Level> dimension,
        int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ
    ) {
        var byChunk = registry.get(dimension);
        if (byChunk == null || byChunk.isEmpty()) return Set.of();
        var result = new HashSet<BlockPos>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var atChunk = byChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                if (atChunk != null) result.addAll(atChunk);
            }
        }
        return result;
    }

    /** The inclusive chunk-coordinate box every block within {@code radius} of {@code anchor} must lie in. */
    static int[] chunkBoxAround(BlockPos anchor, int radius) {
        return new int[] {
            Math.floorDiv(anchor.getX() - radius, 16), Math.floorDiv(anchor.getX() + radius, 16),
            Math.floorDiv(anchor.getZ() - radius, 16), Math.floorDiv(anchor.getZ() + radius, 16)
        };
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
     * Pure, injectable filter keeping only candidates {@code isEligible} accepts. Used both for
     * production selection (a live, no-force-load loaded-and-verified check) and for tests (a
     * fake predicate standing in for that check without a real level or chunk).
     */
    static Collection<BlockPos> filterStale(Collection<BlockPos> candidates, Predicate<BlockPos> isEligible) {
        return candidates.stream().filter(isEligible).toList();
    }

    /**
     * Keeps only positions whose chunk is CURRENTLY loaded and whose live block still matches
     * {@code expected} right now. Never forces a load to check: an unloaded candidate is simply
     * not eligible for selection this pass (not trusted, not guessed at) -- it stays in the
     * historical cache ({@link #knownPositions}) for a later pass where its chunk might be
     * loaded, but a farther, currently-reachable candidate is always preferred over it this pass.
     */
    private Collection<BlockPos> eligibleCandidates(
        ServerLevel destinationLevel, Collection<BlockPos> candidates, Predicate<BlockState> expected
    ) {
        return filterStale(candidates, pos -> {
            ChunkPos chunkPos = new ChunkPos(pos);
            LevelChunk chunk = destinationLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            return chunk != null && expected.test(chunk.getBlockState(pos));
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

    /**
     * MINECRAFT-435: {@code candidateCache} lets several portal positions resolved within the
     * same {@link #onLoadedChunkProducerCheck} call reuse an already-validated candidate set for
     * an identical chunk box, instead of each re-querying and re-validating it. The source-amount
     * check below runs first so a portal with nothing to transfer never pays for the (now bounded,
     * but still non-free) destination search at all.
     */
    private void transferNetherPortal(
        MinecraftServer server, ServerLevel level, BlockPos pos, int throughput,
        Map<List<Integer>, Collection<BlockPos>> candidateCache
    ) {
        if (ports.state(level, AtmosphereMaterial.NETHER_GAS, pos).map(AtmosphereMaterialState::amount).orElse(0) <= 0) {
            return;
        }
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
        int[] box = chunkBoxAround(anchor, NETHER_LINK_SEARCH_RADIUS);
        var cacheKey = List.of(box[0], box[1], box[2], box[3]);
        var candidates = candidateCache.computeIfAbsent(cacheKey, ignored -> eligibleCandidates(destinationLevel,
            knownPositionsNear(knownNetherPortals, destinationDimension, box[0], box[1], box[2], box[3]),
            state -> state.is(Blocks.NETHER_PORTAL)));
        Optional<BlockPos> destination = nearestWithinRadius(anchor, candidates, NETHER_LINK_SEARCH_RADIUS);
        if (destination.isEmpty()) return;
        transfer(level, pos, destinationLevel, destination.get(), AtmosphereMaterial.NETHER_GAS, throughput);
    }

    /** MINECRAFT-435: skips the destination search entirely when nothing at {@code pos} has anything to transfer. */
    private void transferEndPortal(MinecraftServer server, ServerLevel level, BlockPos pos, int throughput) {
        if (level.dimension().equals(Level.END)) return;
        List<AtmosphereMaterial> withSource = new ArrayList<>();
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            if (ports.state(level, material, pos).map(AtmosphereMaterialState::amount).orElse(0) > 0) {
                withSource.add(material);
            }
        }
        if (withSource.isEmpty()) return;
        ServerLevel endLevel = server.getLevel(Level.END);
        if (endLevel == null) return;
        var candidates = eligibleCandidates(endLevel, knownPositions(knownEndPortals, Level.END), state -> state.is(Blocks.END_PORTAL));
        Optional<BlockPos> destination = any(candidates);
        if (destination.isEmpty()) return;
        for (AtmosphereMaterial material : withSource) {
            transfer(level, pos, endLevel, destination.get(), material, throughput);
        }
    }

    /**
     * The three grid operations {@link #transfer} needs, behind an interface so the whole
     * transaction -- partial/clamped acceptance, a failed source debit and its rollback, and an
     * unavailable destination -- is unit-testable with an in-memory fake, not just its pure
     * arithmetic helpers in isolation.
     */
    interface MaterialPorts {
        Optional<AtmosphereMaterialState> state(ServerLevel level, AtmosphereMaterial material, BlockPos pos);

        boolean emit(ServerLevel level, AtmosphereMaterial material, BlockPos pos, int amount);

        boolean consume(ServerLevel level, AtmosphereMaterial material, BlockPos pos, int amount);
    }

    private static final MaterialPorts LIVE_PORTS = new MaterialPorts() {
        @Override
        public Optional<AtmosphereMaterialState> state(ServerLevel level, AtmosphereMaterial material, BlockPos pos) {
            return DynamicAtmosphereMod.materialState(level, material, pos);
        }

        @Override
        public boolean emit(ServerLevel level, AtmosphereMaterial material, BlockPos pos, int amount) {
            return DynamicAtmosphereMod.emitMaterial(level, material, pos, amount);
        }

        @Override
        public boolean consume(ServerLevel level, AtmosphereMaterial material, BlockPos pos, int amount) {
            return DynamicAtmosphereMod.consumeMaterial(level, material, pos, amount);
        }
    };

    private final MaterialPorts ports;

    /**
     * Package-private so an in-memory fake {@link MaterialPorts} can exercise {@link #transfer}
     * end to end -- partial destination acceptance, a failed source debit with verified rollback,
     * and an unavailable destination preserving both totals -- without a live level.
     */
    void transfer(
        ServerLevel sourceLevel, BlockPos sourcePos,
        ServerLevel destinationLevel, BlockPos destinationPos,
        AtmosphereMaterial material, int throughput
    ) {
        var sourceState = ports.state(sourceLevel, material, sourcePos);
        var transferPlan = plan(sourceState.map(AtmosphereMaterialState::amount).orElse(0), throughput, true);
        if (!transferPlan.active()) return;
        var destinationBefore = ports.state(destinationLevel, material, destinationPos);
        if (destinationBefore.isEmpty()) return;
        if (!ports.emit(destinationLevel, material, destinationPos, transferPlan.amount())) return;
        var destinationAfter = ports.state(destinationLevel, material, destinationPos);
        int accepted = acceptedAmount(destinationBefore.get().amount(),
            destinationAfter.map(AtmosphereMaterialState::amount).orElse(destinationBefore.get().amount()));
        int debit = Math.min(transferPlan.amount(), accepted);
        if (debit <= 0) return;
        if (!ports.consume(sourceLevel, material, sourcePos, debit) && accepted > 0) {
            // The destination already accepted material but the source couldn't be debited:
            // roll back the exact accepted amount so nothing is ever duplicated.
            ports.consume(destinationLevel, material, destinationPos, accepted);
        }
    }

    private PortalTransferSink(MaterialPorts ports) {
        this.ports = ports;
    }

    public static PortalTransferSink create() {
        return new PortalTransferSink(LIVE_PORTS);
    }

    /** Test-only: injects a fake {@link MaterialPorts} so {@link #transfer} is unit-testable. */
    static PortalTransferSink createForTest(MaterialPorts ports) {
        return new PortalTransferSink(ports);
    }
}
