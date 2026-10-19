package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * MINECRAFT-436: paces an already-planned per-player stream of sync batches ({@code
 * AtmosphereSyncPlanner.Update}/{@code SmokeSyncPlanner.Update}) across ticks under an explicit
 * per-player, per-tick operation budget, instead of sending an entire snapshot's batches in the
 * single tick that generated them.
 *
 * <p>A batch for which {@code startsReset} is true marks the first batch of a fresh snapshot (or
 * of an immediate dimension-change reset pair); enqueuing it clears any still-undelivered batches
 * left over from an earlier, incomplete snapshot for that player, so a client never receives a mix
 * of two generations' data ("newer snapshots supersede incomplete older ones without accepting
 * stale deltas"). Ordinary (non-reset) batches are appended after whatever is already queued, so a
 * delta planned while a snapshot is still draining is delivered once that snapshot finishes rather
 * than interleaved with it.
 *
 * <p>{@link #drain} always releases at least one queued batch when any are pending, even if that
 * single batch alone exceeds the budget, so one oversized batch can never starve a player
 * ("bounded and fair" continuation, never deadlocked). The budget is supplied fresh to each
 * {@code drain} call (rather than fixed at construction) so a live config reload of {@code
 * maxSyncOperationsPerPlayerPerTick} takes effect on the very next tick, consistent with how
 * every other per-tick runtime budget in this mod is re-read from {@code tuning()} every tick.
 */
final class PacedSyncQueue<P, U> {
    private final ToIntFunction<U> weigher;
    private final Predicate<U> startsReset;
    private final Map<P, Deque<U>> pending = new HashMap<>();

    PacedSyncQueue(ToIntFunction<U> weigher, Predicate<U> startsReset) {
        this.weigher = weigher;
        this.startsReset = startsReset;
    }

    /** Appends newly planned batches, superseding any undelivered batches of an earlier snapshot. */
    void enqueue(P player, List<U> batches) {
        if (batches.isEmpty()) return;
        Deque<U> queue = pending.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        if (startsReset.test(batches.get(0))) queue.clear();
        queue.addAll(batches);
    }

    /** Releases up to {@code budgetPerTick} worth of queued batches, in FIFO order. */
    List<U> drain(P player, int budgetPerTick) {
        if (budgetPerTick <= 0) throw new IllegalArgumentException("budgetPerTick must be positive");
        Deque<U> queue = pending.get(player);
        if (queue == null || queue.isEmpty()) return List.of();
        var released = new ArrayList<U>();
        int spent = 0;
        while (!queue.isEmpty()) {
            U next = queue.peek();
            int weight = weigher.applyAsInt(next);
            if (!released.isEmpty() && spent + weight > budgetPerTick) break;
            released.add(queue.poll());
            spent += weight;
        }
        if (queue.isEmpty()) pending.remove(player);
        return List.copyOf(released);
    }

    boolean hasPending(P player) {
        Deque<U> queue = pending.get(player);
        return queue != null && !queue.isEmpty();
    }

    /** Drops all queued-but-undelivered work for a player, e.g. on disconnect. */
    void release(P player) { pending.remove(player); }

    void retainPlayers(Collection<P> players) { pending.keySet().retainAll(players); }

    void clear() { pending.clear(); }
}
