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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
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
 *
 * <p><b>MINECRAFT-435 bounded, finite passes:</b> {@link #onLoadedChunkProducerCheck} only
 * begins a new pass over a chunk's just-discovered source positions when the previous pass has
 * fully drained ({@link #hasPendingWork}); a pass in progress is continued via {@link
 * #drainPending} instead, without touching the discovery side at all. Each call drains at most
 * {@code portalTransferSink.maxSourcesPerCall} positions per leg; the caller is told whether the
 * whole pass finished (both legs' queues empty) so it knows whether to revisit this chunk before
 * considering its own visit complete -- a finite pass is always serviced exactly once, never
 * abandoned partway and never looped over in a single call.</p>
 */
public final class PortalTransferSink {
    static final int NETHER_PORTAL_COORDINATE_SCALE = 8;
    static final int NETHER_LINK_SEARCH_RADIUS = 128;

    private final Map<ResourceKey<Level>, NavigableMap<Long, Set<BlockPos>>> knownNetherPortals = new HashMap<>();
    private final Map<ResourceKey<Level>, NavigableMap<Long, Set<BlockPos>>> knownEndPortals = new HashMap<>();
    /**
     * MINECRAFT-435: a finite, persisted-per-chunk queue of source positions still owed a
     * transfer attempt from the current pass -- populated once (from a freshly completed
     * discovery) and drained to empty before a new pass may begin for that chunk.
     */
    private final Map<ResourceKey<Level>, Map<Long, Deque<BlockPos>>> pendingNetherTransfers = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<Long, Deque<BlockPos>>> pendingEndTransfers = new HashMap<>();
    /**
     * MINECRAFT-435: opportunistic cache of the last resolved End destination, checked first
     * (O(1)) so a repeatedly-valid destination never pays for {@link #resolveEndDestination}'s
     * bounded registry probe at all; see that method.
     */
    private BlockPos cachedEndDestination;
    /** Persisted, rotating cursor into the End registry's own chunk-entry key space; see {@link #resolveEndDestination}. */
    private long endRegistryProbeCursor = Long.MIN_VALUE;
    /**
     * MINECRAFT-435: in-progress, resumable nearest-candidate search for one Nether destination
     * box -- keyed by {@link NetherBoxKey}, which includes the destination dimension, since the
     * same box coordinates can independently arise for both an Overworld source (destination the
     * Nether) and a Nether source (destination the Overworld), and this map is shared across
     * every chunk and every dimension's sources, not scoped to one call or one chunk. Persisted
     * across calls (not just within one), so a box whose candidate set is too dense to fully
     * examine within one call's {@link
     * DynamicAtmosphereServerConfig.PortalTransferSinkTuning#maxCandidateOperationsPerCall} budget
     * keeps its running nearest-so-far and resumes exactly where it left off next call, rather
     * than ever picking a non-nearest candidate from only a partial view. See {@link
     * #advanceNetherCandidateScan}. A box resolved to completion is removed immediately
     * (reusing it is instead the job of the PER-CALL {@code resolvedThisCall} cache {@link
     * #transferNetherPortal} is given -- see {@link #drainNetherTransfers} -- so a stale answer
     * is never reused into a later, separate call).
     */
    private final Map<NetherBoxKey, CandidateScan> netherCandidateScans = new HashMap<>();

    /** MINECRAFT-435: see {@link #netherCandidateScans}. Pure value key, safe as a Map key (records implement equals/hashCode). */
    private record NetherBoxKey(ResourceKey<Level> destinationDimension, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) { }

    /**
     * MINECRAFT-435: {@code netherPortalsHere}/{@code endPortalsHere} are the positions {@link
     * PortalChunkScanner} already found for this chunk visit -- scanning itself no longer happens
     * here, since {@link ObsidianPowderGameplay} needs the identical Nether portal positions on
     * the same cadence and the two scans were otherwise redundant. The caller must not call this
     * while {@link #hasPendingWork} still reports a pass in progress for this chunk (it would
     * silently be ignored below); call {@link #drainPending} instead in that case.
     *
     * @return {@code true} once every position discovered this pass has been serviced exactly
     *     once (both legs); {@code false} if bounded work remains and the caller must revisit
     *     this chunk (via {@link #drainPending}, not another discovery) before its own visit is done.
     */
    public boolean onLoadedChunkProducerCheck(
        ServerLevel level, LevelChunk chunk, Set<BlockPos> netherPortalsHere, Set<BlockPos> endPortalsHere
    ) {
        long chunkKey = chunk.getPos().toLong();
        recordObserved(knownNetherPortals, level.dimension(), chunkKey, netherPortalsHere);
        recordObserved(knownEndPortals, level.dimension(), chunkKey, endPortalsHere);
        ensureQueue(pendingNetherTransfers, level.dimension(), chunkKey, netherPortalsHere);
        ensureQueue(pendingEndTransfers, level.dimension(), chunkKey, endPortalsHere);
        return drainPending(level, chunk);
    }

    /** Whether a chunk still has an in-progress, not-yet-fully-drained pass from a prior discovery. */
    public boolean hasPendingWork(ResourceKey<Level> dimension, long chunkKey) {
        return queueFor(pendingNetherTransfers, dimension, chunkKey) != null
            || queueFor(pendingEndTransfers, dimension, chunkKey) != null;
    }

    /**
     * Continues draining whatever is already queued for this chunk, without touching discovery
     * at all -- safe to call repeatedly across ticks while {@link #hasPendingWork} is {@code true}.
     *
     * @return {@code true} once both legs' queues are empty.
     */
    public boolean drainPending(ServerLevel level, LevelChunk chunk) {
        MinecraftServer server = level.getServer();
        long chunkKey = chunk.getPos().toLong();
        int throughput = DynamicAtmosphereServerConfig.snapshot().portalTransferSink().throughputPerPortalBlock();
        if (throughput <= 0) {
            clearQueue(pendingNetherTransfers, level.dimension(), chunkKey);
            clearQueue(pendingEndTransfers, level.dimension(), chunkKey);
            return true;
        }
        var tuning = DynamicAtmosphereServerConfig.snapshot().portalTransferSink();
        int maxPerCall = tuning.maxSourcesPerCall();
        // MINECRAFT-435: separate, per-leg candidate-operation budgets, shared across every
        // source this call processes for that leg -- bounds the total registry-position
        // live-checks/nearest-comparisons this call can do, regardless of how many sources are
        // batched or how densely populated any single destination chunk entry is.
        boolean netherDone = drainNetherTransfers(server, level, level.dimension(), chunkKey, throughput,
            maxPerCall, new Budget(tuning.maxCandidateOperationsPerCall()));
        var endBudget = new Budget(tuning.maxCandidateOperationsPerCall());
        boolean endDone = drainQueue(pendingEndTransfers, level.dimension(), chunkKey, maxPerCall,
            pos -> transferEndPortal(server, level, pos, throughput, endBudget));
        return netherDone && endDone;
    }

    /** Drops any in-progress pass for this chunk -- see the unload/replacement invalidation this ticket requires. */
    public void clearPendingWork(ResourceKey<Level> dimension, long chunkKey) {
        clearQueue(pendingNetherTransfers, dimension, chunkKey);
        clearQueue(pendingEndTransfers, dimension, chunkKey);
    }

    /** Drops every in-progress pass, for a full world/session reset. */
    public void clearAllPendingWork() {
        pendingNetherTransfers.clear();
        pendingEndTransfers.clear();
        netherCandidateScans.clear();
    }

    /**
     * MINECRAFT-435: bespoke drain for the Nether leg, unlike the generic {@link #drainQueue} the
     * End leg still uses -- a source whose destination box resolution is not yet complete (budget
     * exhausted mid-scan, see {@link #advanceNetherCandidateScan}) must stay at the FRONT of the
     * queue and be retried, never popped and never counted as serviced. Once a source's box
     * resolution genuinely cannot make further progress this call, there is no point trying later
     * sources either (the shared budget is spent), so this stops rather than skipping ahead.
     *
     * <p>{@code resolvedThisCall} is a fresh, call-local memo of boxes already fully resolved
     * during THIS call: several source portals in the same chunk visit that resolve to an
     * identical box reuse that one answer for free (the "pass-local reuse" this ticket requires),
     * without reusing a possibly-stale answer across separate, later calls -- unlike {@link
     * #netherCandidateScans}, which only ever holds a NOT-YET-complete scan.</p>
     */
    private boolean drainNetherTransfers(
        MinecraftServer server, ServerLevel level, ResourceKey<Level> dimension, long chunkKey,
        int throughput, int maxSources, Budget candidateBudget
    ) {
        var queue = queueFor(pendingNetherTransfers, dimension, chunkKey);
        if (queue == null) return true;
        var resolvedThisCall = new HashMap<NetherBoxKey, Optional<BlockPos>>();
        int processed = 0;
        while (processed < maxSources && !queue.isEmpty()) {
            BlockPos pos = queue.peek();
            if (!transferNetherPortal(server, level, pos, throughput, candidateBudget, resolvedThisCall)) break;
            queue.poll();
            processed++;
        }
        if (queue.isEmpty()) {
            clearQueue(pendingNetherTransfers, dimension, chunkKey);
            return true;
        }
        return false;
    }

    static void ensureQueue(
        Map<ResourceKey<Level>, Map<Long, Deque<BlockPos>>> queues,
        ResourceKey<Level> dimension, long chunkKey, Set<BlockPos> positions
    ) {
        if (positions.isEmpty()) return;
        // computeIfAbsent on the inner map only creates an entry if one is not already there, so
        // a still-draining prior pass's queue is never stomped by a fresh (re-)discovery.
        queues.computeIfAbsent(dimension, ignored -> new HashMap<>())
            .computeIfAbsent(chunkKey, ignored -> new ArrayDeque<>(positions));
    }

    static Deque<BlockPos> queueFor(
        Map<ResourceKey<Level>, Map<Long, Deque<BlockPos>>> queues, ResourceKey<Level> dimension, long chunkKey
    ) {
        var byChunk = queues.get(dimension);
        return byChunk == null ? null : byChunk.get(chunkKey);
    }

    static void clearQueue(
        Map<ResourceKey<Level>, Map<Long, Deque<BlockPos>>> queues, ResourceKey<Level> dimension, long chunkKey
    ) {
        var byChunk = queues.get(dimension);
        if (byChunk != null) byChunk.remove(chunkKey);
    }

    /**
     * Pure queue-draining bookkeeping (no live level access of its own -- {@code action} is the
     * only place that happens), so it is directly unit-testable: drains at most {@code
     * maxPerCall} elements, reports {@code true} once the queue is empty (including when there
     * was never one to begin with -- an already-finished or never-started pass counts as done).
     */
    static boolean drainQueue(
        Map<ResourceKey<Level>, Map<Long, Deque<BlockPos>>> queues,
        ResourceKey<Level> dimension, long chunkKey, int maxPerCall, Consumer<BlockPos> action
    ) {
        var queue = queueFor(queues, dimension, chunkKey);
        if (queue == null) return true;
        for (int i = 0; i < maxPerCall && !queue.isEmpty(); i++) action.accept(queue.poll());
        if (queue.isEmpty()) {
            clearQueue(queues, dimension, chunkKey);
            return true;
        }
        return false;
    }

    private static void recordObserved(
        Map<ResourceKey<Level>, NavigableMap<Long, Set<BlockPos>>> registry,
        ResourceKey<Level> dimension, long chunkKey, Set<BlockPos> observedThisScan
    ) {
        var byChunk = registry.computeIfAbsent(dimension, ignored -> new TreeMap<>());
        if (observedThisScan.isEmpty()) {
            byChunk.remove(chunkKey);
        } else {
            byChunk.put(chunkKey, Set.copyOf(observedThisScan));
        }
    }

    /**
     * MINECRAFT-435: bounded registry lookup for the End leg, which has no spatial search-radius
     * concept to narrow by (the End's exit is not coordinate-linked to any source). Walks a
     * {@link NavigableMap} directly via {@code ceilingKey}/{@code higherKey} starting at {@code
     * startKey} (wrapping around), examining at most {@code maxChunkEntries} distinct chunk
     * entries -- unlike a sort-then-slice approach, the registry's full key set is never
     * materialized or sorted, so the traversal cost itself (not just the returned entry count) is
     * bounded regardless of how large the registry has grown. Pure and injectable. (The Nether leg
     * has an analogous bounded-traversal concern but, unlike the End leg, also needs the true
     * nearest candidate, not just any one -- see {@link #advanceNetherCandidateScan}.)
     */
    record WindowedPositions(Collection<BlockPos> positions, long nextStartKey) { }

    static WindowedPositions knownPositionsWindowed(
        Map<ResourceKey<Level>, ? extends NavigableMap<Long, Set<BlockPos>>> registry, ResourceKey<Level> dimension,
        long startKey, int maxChunkEntries
    ) {
        if (maxChunkEntries <= 0) throw new IllegalArgumentException("maxChunkEntries must be positive");
        var byChunk = registry.get(dimension);
        if (byChunk == null || byChunk.isEmpty()) return new WindowedPositions(Set.of(), startKey);
        int toExamine = Math.min(maxChunkEntries, byChunk.size());
        var result = new HashSet<BlockPos>();
        Long key = byChunk.ceilingKey(startKey);
        if (key == null) key = byChunk.firstKey();
        long nextStart = startKey;
        for (int i = 0; i < toExamine; i++) {
            result.addAll(byChunk.getOrDefault(key, Set.of()));
            Long next = byChunk.higherKey(key);
            nextStart = next != null ? next : byChunk.firstKey();
            key = nextStart;
        }
        return new WindowedPositions(result, nextStart);
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

    /**
     * MINECRAFT-435: a per-call budget shared across however many positions a resumable
     * candidate search examines, independent of {@code MaterialPorts} or any live level --
     * decrementing it is the only side effect {@link #advanceNetherCandidateScan} and {@link
     * #resolveEndDestination} have in common with each other.
     */
    static final class Budget {
        private int remaining;

        Budget(int remaining) {
            this.remaining = remaining;
        }

        boolean hasRemaining() {
            return remaining > 0;
        }

        void spend() {
            remaining--;
        }
    }

    /**
     * MINECRAFT-435: in-progress state for one Nether destination box's resumable nearest-candidate
     * search -- which chunk entry (by index into the box's own deterministic coordinate sequence)
     * and which position within that entry's set to resume at, plus the nearest eligible candidate
     * found so far. Package-private fields: driven entirely by {@link #advanceNetherCandidateScan}.
     */
    static final class CandidateScan {
        int chunkIndex;
        Iterator<BlockPos> currentChunkPositions = Collections.emptyIterator();
        BlockPos best;
        boolean done;
    }

    /**
     * MINECRAFT-435: resumable, budget-bounded advance of one box's nearest-candidate search --
     * the part of destination resolution the review found still unbounded ("one source can still
     * collect and live-check every portal block in the ... chunk box"). Pure and injectable: {@code
     * byChunk} is a plain registry snapshot and {@code isEligible} stands in for a live
     * loaded-chunk-and-block check, so this is directly unit-testable with a fake dense chunk entry
     * and a counting predicate, without a real level.
     *
     * <p>Walks the box's (bounded, radius-derived) chunk-coordinate sequence in a fixed
     * deterministic order, and within each chunk entry walks its {@code Set<BlockPos>} via a
     * live {@link Iterator} retained in {@code scan} -- never sorted or copied into a List, so an
     * arbitrarily densely-populated single chunk entry still only costs exactly {@code
     * budget}-many examinations per call, resumed via the retained iterator on the next call
     * rather than re-walked from its start. {@code scan.best} only ever reflects positions
     * actually examined, combined via {@link #nearestWithinRadius} on every advance, so a decision
     * is never made against a non-nearest candidate from a partial view -- {@link
     * CandidateScan#done} only becomes {@code true} once every chunk entry in the box has been
     * fully walked.</p>
     */
    static void advanceNetherCandidateScan(
        Map<Long, Set<BlockPos>> byChunk, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
        BlockPos anchor, int maxRadius, CandidateScan scan, Budget budget, Predicate<BlockPos> isEligible
    ) {
        if (scan.done) return;
        int heightInChunks = maxChunkZ - minChunkZ + 1;
        int totalChunks = (maxChunkX - minChunkX + 1) * heightInChunks;
        var eligibleThisAdvance = new ArrayList<BlockPos>();
        while (budget.hasRemaining()) {
            if (!scan.currentChunkPositions.hasNext()) {
                if (scan.chunkIndex >= totalChunks) {
                    scan.done = true;
                    break;
                }
                int chunkX = minChunkX + scan.chunkIndex / heightInChunks;
                int chunkZ = minChunkZ + scan.chunkIndex % heightInChunks;
                scan.chunkIndex++;
                Set<BlockPos> positions = byChunk == null ? null : byChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                scan.currentChunkPositions = positions == null ? Collections.emptyIterator() : positions.iterator();
                continue;
            }
            BlockPos candidate = scan.currentChunkPositions.next();
            budget.spend();
            if (isEligible.test(candidate)) eligibleThisAdvance.add(candidate);
        }
        if (!scan.done && scan.chunkIndex >= totalChunks && !scan.currentChunkPositions.hasNext()) {
            scan.done = true;
        }
        if (!eligibleThisAdvance.isEmpty() || scan.best != null) {
            var combined = new ArrayList<BlockPos>(eligibleThisAdvance.size() + 1);
            combined.addAll(eligibleThisAdvance);
            if (scan.best != null) combined.add(scan.best);
            scan.best = nearestWithinRadius(anchor, combined, maxRadius).orElse(null);
        }
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
     * historical registry for a later pass where its chunk might be loaded, but a farther,
     * currently-reachable candidate is always preferred over it this pass.
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
     * MINECRAFT-435: {@code pos} is first revalidated against the already-loaded source chunk's
     * OWN live block state -- a portal discovered by the scan several ticks ago may have since
     * been destroyed, and a destroyed source is never a valid transfer source regardless of what
     * the atmosphere grid still records there. The source-amount check then runs so a portal with
     * nothing to transfer never pays for the (bounded, but still non-free) destination search at
     * all.
     *
     * @return {@code true} if this source was fully handled this call (transferred, declined, or
     *     found to have nothing to transfer) and the caller should advance past it; {@code false}
     *     if its destination box's candidate search is still in progress (the shared {@code
     *     candidateBudget} ran out mid-scan) and the caller must retry this exact source later,
     *     never treating it as serviced.
     */
    private boolean transferNetherPortal(
        MinecraftServer server, ServerLevel level, BlockPos pos, int throughput, Budget candidateBudget,
        Map<NetherBoxKey, Optional<BlockPos>> resolvedThisCall
    ) {
        if (!level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) return true;
        if (ports.state(level, AtmosphereMaterial.NETHER_GAS, pos).map(AtmosphereMaterialState::amount).orElse(0) <= 0) {
            return true;
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
            return true;
        }
        ServerLevel destinationLevel = server.getLevel(destinationDimension);
        if (destinationLevel == null) return true;
        int[] box = chunkBoxAround(anchor, NETHER_LINK_SEARCH_RADIUS);
        var boxKey = new NetherBoxKey(destinationDimension, box[0], box[1], box[2], box[3]);

        if (resolvedThisCall.containsKey(boxKey)) {
            resolvedThisCall.get(boxKey).ifPresent(
                destination -> transfer(level, pos, destinationLevel, destination, AtmosphereMaterial.NETHER_GAS, throughput));
            return true;
        }
        CandidateScan scan = netherCandidateScans.computeIfAbsent(boxKey, ignored -> new CandidateScan());
        advanceNetherCandidateScan(knownNetherPortals.get(destinationDimension), box[0], box[1], box[2], box[3],
            anchor, NETHER_LINK_SEARCH_RADIUS, scan, candidateBudget,
            candidate -> !eligibleCandidates(destinationLevel, List.of(candidate), state -> state.is(Blocks.NETHER_PORTAL)).isEmpty());
        if (!scan.done) return false;
        netherCandidateScans.remove(boxKey);
        resolvedThisCall.put(boxKey, Optional.ofNullable(scan.best));
        if (scan.best != null) {
            transfer(level, pos, destinationLevel, scan.best, AtmosphereMaterial.NETHER_GAS, throughput);
        }
        return true;
    }

    /**
     * MINECRAFT-435: revalidates {@code pos} against the already-loaded source chunk's own live
     * block state first -- a destroyed End portal is never a valid transfer source, regardless of
     * what the atmosphere grid still records there -- then skips the destination search entirely
     * when nothing at {@code pos} has anything to transfer. Unlike the Nether leg, declining to
     * resolve a destination this call (shared {@code candidateBudget} exhausted) is always safe
     * to simply retry the SOURCE again on its next scheduled visit: the End leg only ever needs
     * "any" eligible destination, never "the nearest," so there is no risk of committing to a
     * worse-than-available choice by giving up early.
     */
    private void transferEndPortal(MinecraftServer server, ServerLevel level, BlockPos pos, int throughput, Budget candidateBudget) {
        if (level.dimension().equals(Level.END) || !level.getBlockState(pos).is(Blocks.END_PORTAL)) return;
        List<AtmosphereMaterial> withSource = new ArrayList<>();
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            if (ports.state(level, material, pos).map(AtmosphereMaterialState::amount).orElse(0) > 0) {
                withSource.add(material);
            }
        }
        if (withSource.isEmpty()) return;
        ServerLevel endLevel = server.getLevel(Level.END);
        if (endLevel == null) return;
        Optional<BlockPos> destination = resolveEndDestination(endLevel, candidateBudget);
        if (destination.isEmpty()) return;
        for (AtmosphereMaterial material : withSource) {
            transfer(level, pos, endLevel, destination.get(), material, throughput);
        }
    }

    /**
     * MINECRAFT-435: bounded alternative to flattening and validating the whole historical End
     * registry on every call. Tries the last resolved destination first (cheap, O(1), free --
     * does not spend {@code candidateBudget}); only when that is no longer eligible does it fall
     * back to a small rotating, non-materializing probe of the registry's own chunk entries
     * ({@link #knownPositionsWindowed}, cursor persisted in {@link #endRegistryProbeCursor}), so
     * repeated calls eventually cover the whole registry rather than always re-probing the same
     * prefix. The live-check loop over that probe's positions is itself bounded by {@code
     * candidateBudget} and stops (short-circuiting on the first eligible hit, since "any" is all
     * the End leg needs) rather than validating every position a single densely-populated
     * registry chunk entry might contribute.
     */
    private Optional<BlockPos> resolveEndDestination(ServerLevel endLevel, Budget candidateBudget) {
        if (cachedEndDestination != null && isEligibleEndPortal(endLevel, cachedEndDestination)) {
            return Optional.of(cachedEndDestination);
        }
        cachedEndDestination = null;
        int maxChunkEntries = DynamicAtmosphereServerConfig.snapshot().portalTransferSink().maxSourcesPerCall();
        var windowed = knownPositionsWindowed(knownEndPortals, Level.END, endRegistryProbeCursor, maxChunkEntries);
        endRegistryProbeCursor = windowed.nextStartKey();
        for (BlockPos candidate : windowed.positions()) {
            if (!candidateBudget.hasRemaining()) break;
            candidateBudget.spend();
            if (isEligibleEndPortal(endLevel, candidate)) {
                cachedEndDestination = candidate;
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean isEligibleEndPortal(ServerLevel endLevel, BlockPos pos) {
        return !eligibleCandidates(endLevel, List.of(pos), state -> state.is(Blocks.END_PORTAL)).isEmpty();
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
