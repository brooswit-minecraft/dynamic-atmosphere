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
    /**
     * MINECRAFT-435: in-progress, resumable "is there any eligible End destination" search --
     * global, not keyed by source or box, since the End leg only ever needs ANY known destination
     * (never "the nearest"), so there is exactly one such search in flight at a time regardless of
     * how many End sources are waiting on it. See {@link #advanceEndCandidateScan}.
     */
    private EndCandidateScan pendingEndScan;
    /**
     * MINECRAFT-435: where the NEXT fresh {@link EndCandidateScan} should start -- persisted
     * independently of any one scan attempt, and advanced to wherever that attempt left off once
     * it completes (whether it found a destination or not), so repeated scan attempts rotate
     * forward through the registry over time instead of always re-examining the same prefix (which
     * would starve a dense entry's suffix, or anything beyond the first {@code maxSourcesPerCall}
     * chunk entries, forever).
     */
    private long endRegistryCursor = Long.MIN_VALUE;
    /**
     * MINECRAFT-435: in-progress, resumable "which positions in this destination box are
     * currently eligible" search -- keyed by {@link NetherBoxKey}, which includes the destination
     * dimension, since the same box coordinates can independently arise for both an Overworld
     * source (destination the Nether) and a Nether source (destination the Overworld), and this
     * map is shared across every chunk and every dimension's sources, not scoped to one call or
     * one chunk. Persisted across calls (not just within one), so a box whose candidate set is too
     * dense to fully examine within one call's {@link
     * DynamicAtmosphereServerConfig.PortalTransferSinkTuning#maxCandidateOperationsPerCall} budget
     * resumes exactly where it left off next call.
     *
     * <p>Deliberately anchor-independent: the SET of positions in a box that are live Nether
     * portals does not depend on which source's anchor the box happens to be centered near, so
     * this discovery work -- the only part worth sharing -- is kept separate from the
     * anchor-specific "which of these is nearest to THIS source" selection, which {@link
     * #transferNetherPortal} always computes fresh per source via {@link #selectLiveNearest}. Two
     * different sources whose anchors happen to fall in the same 17x17 box therefore correctly
     * reuse the shared discovery work without ever reusing each other's (potentially different)
     * nearest answer.</p>
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
        boolean endDone = drainEndTransfers(server, level, level.dimension(), chunkKey, throughput,
            maxPerCall, new Budget(tuning.maxCandidateOperationsPerCall()));
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
        pendingEndScan = null;
    }

    /**
     * MINECRAFT-435: bespoke peek/pop drain for the Nether leg -- a source whose destination box
     * resolution is not yet complete (budget exhausted mid-scan, see {@link
     * #advanceNetherCandidateScan}) must stay at the FRONT of the queue and be retried, never
     * popped and never counted as serviced. Once a source's box resolution genuinely cannot make
     * further progress this call, there is no point trying later sources either (the shared
     * budget is spent), so this stops rather than skipping ahead.
     *
     * <p>{@code resolvedThisCall} is a fresh, call-local memo of boxes already fully DISCOVERED
     * during THIS call: several source portals in the same chunk visit that resolve to an
     * identical box reuse that one discovery (the "pass-local reuse" this ticket requires)
     * without reusing a possibly-stale answer across separate, later calls -- unlike {@link
     * #netherCandidateScans}, which only ever holds a NOT-YET-complete scan. The per-anchor
     * nearest SELECTION itself is never shared (anchor-specific; see {@link
     * #transferNetherPortal}).</p>
     */
    private boolean drainNetherTransfers(
        MinecraftServer server, ServerLevel level, ResourceKey<Level> dimension, long chunkKey,
        int throughput, int maxSources, Budget candidateBudget
    ) {
        var queue = queueFor(pendingNetherTransfers, dimension, chunkKey);
        if (queue == null) return true;
        var resolvedThisCall = new HashMap<NetherBoxKey, Set<BlockPos>>();
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

    /**
     * MINECRAFT-435: bespoke peek/pop drain for the End leg, mirroring {@link
     * #drainNetherTransfers} -- a source is popped only once {@link #transferEndPortal} reports
     * it fully handled (transferred, declined, or found to have nothing to transfer); one whose
     * destination search is still in progress (budget exhausted) stays at the front and is
     * retried, never dropped. Earlier revisions popped every polled source unconditionally, which
     * could silently drop a source whose resolution simply hadn't finished yet.
     */
    private boolean drainEndTransfers(
        MinecraftServer server, ServerLevel level, ResourceKey<Level> dimension, long chunkKey,
        int throughput, int maxSources, Budget candidateBudget
    ) {
        var queue = queueFor(pendingEndTransfers, dimension, chunkKey);
        if (queue == null) return true;
        int processed = 0;
        while (processed < maxSources && !queue.isEmpty()) {
            BlockPos pos = queue.peek();
            if (!transferEndPortal(server, level, pos, throughput, candidateBudget)) break;
            queue.poll();
            processed++;
        }
        if (queue.isEmpty()) {
            clearQueue(pendingEndTransfers, dimension, chunkKey);
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
     * MINECRAFT-435: in-progress state for the End leg's resumable "is there any eligible
     * destination" search -- which registry chunk entry to resume at (a {@link NavigableMap}
     * cursor, wrapping around), which position within that entry's set to resume at, and the
     * first eligible position found (if any). Package-private fields: driven entirely by {@link
     * #advanceEndCandidateScan}.
     */
    static final class EndCandidateScan {
        long nextChunkKey = Long.MIN_VALUE;
        int entriesExamined;
        Iterator<BlockPos> currentChunkPositions = Collections.emptyIterator();
        BlockPos found;
        boolean done;
    }

    /**
     * MINECRAFT-435: resumable, budget-bounded search for ANY eligible End destination -- the End
     * leg never needs "nearest", so this short-circuits the instant one eligible position is
     * found, unlike {@link #advanceNetherCandidateScan}. Pure and injectable like that method, for
     * the same reason: {@code registry} is a plain snapshot and {@code isEligible} stands in for a
     * live loaded-chunk-and-block check.
     *
     * <p>Walks the registry's {@link NavigableMap} directly via {@code ceilingKey}/{@code
     * higherKey} (wrapping around), examining at most {@code maxChunkEntries} distinct chunk
     * entries -- unlike a sort-then-slice approach, the registry's full key set is never
     * materialized or sorted. Within each chunk entry, its {@code Set<BlockPos>} is walked via a
     * live {@link Iterator} retained in {@code scan} -- also never sorted or copied into a List --
     * so an arbitrarily densely-populated single chunk entry still only costs exactly {@code
     * budget}-many examinations per call, resumed via the retained iterator on the next call
     * rather than re-walked from its start. {@link EndCandidateScan#done} becomes {@code true}
     * either once a position is found, or once {@code maxChunkEntries} distinct entries have been
     * fully examined without finding one -- a finite pass over the registry as it exists right
     * now, never an unbounded search.</p>
     */
    static void advanceEndCandidateScan(
        NavigableMap<Long, Set<BlockPos>> registry, int maxChunkEntries,
        EndCandidateScan scan, Budget budget, Predicate<BlockPos> isEligible
    ) {
        if (scan.done) return;
        if (registry == null || registry.isEmpty()) {
            scan.done = true;
            return;
        }
        // Never try to examine more distinct entries than actually exist -- with a GLOBAL
        // wraparound cursor (unlike the Nether leg's fixed, inherently non-repeating box-index
        // range), continuing past the registry's own size would revisit an already-fully-examined
        // entry a second time within the same pass instead of finishing.
        int entryBudget = Math.min(maxChunkEntries, registry.size());
        while (budget.hasRemaining()) {
            if (!scan.currentChunkPositions.hasNext()) {
                if (scan.entriesExamined >= entryBudget) {
                    scan.done = true;
                    return;
                }
                Long key = registry.ceilingKey(scan.nextChunkKey);
                if (key == null) key = registry.firstKey();
                scan.entriesExamined++;
                Long next = registry.higherKey(key);
                scan.nextChunkKey = next != null ? next : registry.firstKey();
                scan.currentChunkPositions = registry.getOrDefault(key, Set.of()).iterator();
                continue;
            }
            BlockPos candidate = scan.currentChunkPositions.next();
            budget.spend();
            if (isEligible.test(candidate)) {
                scan.found = candidate;
                scan.done = true;
                return;
            }
        }
        if (scan.entriesExamined >= entryBudget && !scan.currentChunkPositions.hasNext()) {
            scan.done = true;
        }
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
     * MINECRAFT-435: in-progress state for one Nether destination box's resumable ELIGIBILITY
     * discovery -- which chunk entry (by index into the box's own deterministic coordinate
     * sequence) and which position within that entry's set to resume at, plus every eligible
     * candidate found so far. Deliberately anchor-independent (see {@link #netherCandidateScans});
     * {@link #transferNetherPortal} computes the anchor-specific nearest selection separately, via
     * {@link #selectLiveNearest}, once this discovery is {@link #done}. Package-private fields:
     * driven entirely by {@link #advanceNetherCandidateScan}.
     */
    static final class CandidateScan {
        int chunkIndex;
        Iterator<BlockPos> currentChunkPositions = Collections.emptyIterator();
        final Set<BlockPos> eligibleCandidates = new HashSet<>();
        boolean done;
    }

    /**
     * MINECRAFT-435: resumable, budget-bounded discovery of every eligible position in one box --
     * the part of destination resolution the review found still unbounded ("one source can still
     * collect and live-check every portal block in the ... chunk box"). Deliberately
     * anchor-independent (accumulates every eligible position found, not "the nearest to some
     * anchor") so it is safely shareable across different sources/anchors that happen to resolve
     * to the same box -- see {@link #netherCandidateScans}; the nearest-to-a-specific-anchor
     * selection happens afterward, separately, in {@link #selectLiveNearest}. Pure and injectable:
     * {@code byChunk} is a plain registry snapshot and {@code isEligible} stands in for a live
     * loaded-chunk-and-block check, so this is directly unit-testable with a fake dense chunk
     * entry and a counting predicate, without a real level.
     *
     * <p>Walks the box's (bounded, radius-derived) chunk-coordinate sequence in a fixed
     * deterministic order, and within each chunk entry walks its {@code Set<BlockPos>} via a
     * live {@link Iterator} retained in {@code scan} -- never sorted or copied into a List, so an
     * arbitrarily densely-populated single chunk entry still only costs exactly {@code
     * budget}-many examinations per call, resumed via the retained iterator on the next call
     * rather than re-walked from its start. {@link CandidateScan#done} only becomes {@code true}
     * once every chunk entry in the box has been fully walked.</p>
     */
    static void advanceNetherCandidateScan(
        Map<Long, Set<BlockPos>> byChunk, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
        CandidateScan scan, Budget budget, Predicate<BlockPos> isEligible
    ) {
        if (scan.done) return;
        int heightInChunks = maxChunkZ - minChunkZ + 1;
        int totalChunks = (maxChunkX - minChunkX + 1) * heightInChunks;
        while (budget.hasRemaining()) {
            if (!scan.currentChunkPositions.hasNext()) {
                if (scan.chunkIndex >= totalChunks) {
                    scan.done = true;
                    return;
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
            if (isEligible.test(candidate)) scan.eligibleCandidates.add(candidate);
        }
        if (scan.chunkIndex >= totalChunks && !scan.currentChunkPositions.hasNext()) {
            scan.done = true;
        }
    }

    /**
     * MINECRAFT-435: the anchor-specific half of Nether destination selection, kept separate from
     * the (shareable, anchor-independent) discovery in {@link #advanceNetherCandidateScan} -- see
     * {@link #netherCandidateScans}. Picks the nearest candidate in {@code pool} to {@code anchor},
     * but revalidates it with {@code isEligible} immediately before accepting it: a candidate added
     * to the pool when it was live during an earlier call's advance may have since been destroyed
     * or unloaded by the time a (possibly much later) call finally finishes the discovery and
     * selects from it. A stale winner is discarded from the pool (so neither this nor a sibling
     * source sharing the pool reconsiders it) and the next-nearest is tried, repeating until a
     * genuinely currently-valid winner is found or the pool is exhausted -- a farther but currently
     * loaded and valid exit is therefore never hidden by a nearer one that merely used to be valid.
     * Bounded by {@code pool}'s own (already budget-bounded) size, so this loop itself can never
     * cause unbounded work.
     */
    static Optional<BlockPos> selectLiveNearest(BlockPos anchor, Set<BlockPos> pool, int maxRadius, Predicate<BlockPos> isEligible) {
        while (true) {
            Optional<BlockPos> candidate = nearestWithinRadius(anchor, pool, maxRadius);
            if (candidate.isEmpty()) return Optional.empty();
            if (isEligible.test(candidate.get())) return candidate;
            pool.remove(candidate.get());
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
        Map<NetherBoxKey, Set<BlockPos>> resolvedThisCall
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
        Predicate<BlockPos> isEligible = candidate ->
            !eligibleCandidates(destinationLevel, List.of(candidate), state -> state.is(Blocks.NETHER_PORTAL)).isEmpty();

        Set<BlockPos> pool = resolvedThisCall.get(boxKey);
        if (pool == null) {
            CandidateScan scan = netherCandidateScans.computeIfAbsent(boxKey, ignored -> new CandidateScan());
            advanceNetherCandidateScan(knownNetherPortals.get(destinationDimension), box[0], box[1], box[2], box[3],
                scan, candidateBudget, isEligible);
            if (!scan.done) return false;
            netherCandidateScans.remove(boxKey);
            pool = scan.eligibleCandidates;
            resolvedThisCall.put(boxKey, pool);
        }
        selectLiveNearest(anchor, pool, NETHER_LINK_SEARCH_RADIUS, isEligible)
            .ifPresent(destination -> transfer(level, pos, destinationLevel, destination, AtmosphereMaterial.NETHER_GAS, throughput));
        return true;
    }

    /**
     * MINECRAFT-435: revalidates {@code pos} against the already-loaded source chunk's own live
     * block state first -- a destroyed End portal is never a valid transfer source, regardless of
     * what the atmosphere grid still records there -- then skips the destination search entirely
     * when nothing at {@code pos} has anything to transfer.
     *
     * @return {@code true} if this source was fully handled this call (transferred, declined, or
     *     found to have nothing to transfer) and the caller should advance past it; {@code false}
     *     if the (single, global) End destination search is still in progress and the caller must
     *     retry this exact source later, never treating it as serviced.
     */
    private boolean transferEndPortal(MinecraftServer server, ServerLevel level, BlockPos pos, int throughput, Budget candidateBudget) {
        if (level.dimension().equals(Level.END) || !level.getBlockState(pos).is(Blocks.END_PORTAL)) return true;
        List<AtmosphereMaterial> withSource = new ArrayList<>();
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            if (ports.state(level, material, pos).map(AtmosphereMaterialState::amount).orElse(0) > 0) {
                withSource.add(material);
            }
        }
        if (withSource.isEmpty()) return true;
        ServerLevel endLevel = server.getLevel(Level.END);
        if (endLevel == null) return true;
        Optional<BlockPos> destination = resolveEndDestination(endLevel, candidateBudget);
        if (destination.isEmpty() && pendingEndScan != null) return false;
        destination.ifPresent(found -> {
            for (AtmosphereMaterial material : withSource) {
                transfer(level, pos, endLevel, found, material, throughput);
            }
        });
        return true;
    }

    /**
     * MINECRAFT-435: bounded, resumable alternative to flattening and validating the whole
     * historical End registry on every call. Tries the last resolved destination first (cheap,
     * O(1), free -- does not spend {@code candidateBudget}); only when that is no longer eligible
     * does it advance the single, global, persisted {@link #pendingEndScan} ({@link
     * #advanceEndCandidateScan}), which bounds both the number of registry chunk entries AND the
     * number of individual positions examined this call.
     *
     * @return the resolved destination, or empty either because none exists (the scan completed
     *     with nothing found -- {@link #pendingEndScan} is then {@code null}) or because the scan
     *     is still in progress ({@link #pendingEndScan} is non-null) -- the caller must
     *     distinguish these via {@link #pendingEndScan}'s nullness, since only the latter means
     *     "retry the source, don't treat it as serviced".
     */
    private Optional<BlockPos> resolveEndDestination(ServerLevel endLevel, Budget candidateBudget) {
        if (cachedEndDestination != null && isEligibleEndPortal(endLevel, cachedEndDestination)) {
            return Optional.of(cachedEndDestination);
        }
        cachedEndDestination = null;
        if (pendingEndScan == null) {
            pendingEndScan = new EndCandidateScan();
            pendingEndScan.nextChunkKey = endRegistryCursor;
        }
        int maxChunkEntries = DynamicAtmosphereServerConfig.snapshot().portalTransferSink().maxSourcesPerCall();
        advanceEndCandidateScan(knownEndPortals.get(Level.END), maxChunkEntries, pendingEndScan, candidateBudget,
            candidate -> isEligibleEndPortal(endLevel, candidate));
        if (!pendingEndScan.done) return Optional.empty();
        endRegistryCursor = pendingEndScan.nextChunkKey;
        BlockPos found = pendingEndScan.found;
        pendingEndScan = null;
        if (found != null) cachedEndDestination = found;
        return Optional.ofNullable(found);
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
