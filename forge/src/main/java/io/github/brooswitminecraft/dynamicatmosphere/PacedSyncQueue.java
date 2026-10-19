package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
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
 * than interleaved with it. Callers are expected to only {@link #enqueue} a fresh plan for a player
 * when {@link #hasPending} is already false for that player (see {@code ForgeAtmospherePrototype}'s
 * sync channels) -- that keeps the queue bounded to at most one planner generation's worth of
 * batches at a time, however long delivery takes or however often the live view changes, rather
 * than growing with elapsed time or arrival rate.
 *
 * <p>{@link #drain} never releases more than {@code budgetPerTick} worth of weight: a queued batch
 * whose own weight exceeds the remaining budget is split via the constructor-supplied splitter
 * into smaller pieces (preserving {@code reset} only on the first piece and {@code snapshotEnd}
 * only on the last, so wire semantics are unchanged) rather than being sent whole over the cap.
 * {@code drain} still always releases at least one (possibly split) piece when anything is
 * pending, so a single truly indivisible unit heavier than the whole budget -- the one case the
 * splitter cannot shrink below the cap -- still can never starve a player forever.
 */
final class PacedSyncQueue<P, U> {
    private final ToIntFunction<U> weigher;
    private final Predicate<U> startsReset;
    private final BiFunction<U, Integer, List<U>> splitter;
    private final Map<P, Deque<U>> pending = new HashMap<>();

    /**
     * @param splitter given a batch and a maximum weight per piece, returns an ordered list of
     *     smaller batches covering exactly the same chunks/cells (first piece keeps {@code reset},
     *     last piece keeps {@code snapshotEnd}), each weighing at most that maximum -- or a
     *     single-element list containing the original batch unchanged if it cannot be shrunk
     *     below the cap (e.g. a test double with no real sub-structure to split).
     */
    PacedSyncQueue(ToIntFunction<U> weigher, Predicate<U> startsReset, BiFunction<U, Integer, List<U>> splitter) {
        this.weigher = weigher;
        this.startsReset = startsReset;
        this.splitter = splitter;
    }

    /** Appends newly planned batches, superseding any undelivered batches of an earlier snapshot. */
    void enqueue(P player, List<U> batches) {
        if (batches.isEmpty()) return;
        Deque<U> queue = pending.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        if (startsReset.test(batches.get(0))) queue.clear();
        queue.addAll(batches);
    }

    /**
     * Releases queued batches whose combined weight is at most {@code budgetPerTick}, splitting
     * the head batch if needed to respect that cap, while always making at least some progress.
     */
    List<U> drain(P player, int budgetPerTick) {
        if (budgetPerTick <= 0) throw new IllegalArgumentException("budgetPerTick must be positive");
        Deque<U> queue = pending.get(player);
        if (queue == null || queue.isEmpty()) return List.of();
        var released = new ArrayList<U>();
        int remaining = budgetPerTick;
        while (!queue.isEmpty() && remaining > 0) {
            U head = queue.peek();
            int weight = weigher.applyAsInt(head);
            if (weight <= remaining) {
                released.add(queue.poll());
                remaining -= weight;
                continue;
            }
            List<U> pieces = splitter.apply(head, remaining);
            if (pieces.size() <= 1) {
                // The splitter could not shrink this indivisible unit below the cap. Only send it
                // whole -- the one case allowed to exceed budgetPerTick -- if nothing has been
                // released yet this call, so a player is never starved forever; otherwise leave
                // it queued rather than let an already-filled call exceed its budget further.
                if (released.isEmpty()) released.add(queue.poll());
            } else {
                queue.poll();
                for (int i = pieces.size() - 1; i >= 1; i--) queue.addFirst(pieces.get(i));
                released.add(pieces.get(0));
            }
            break;
        }
        if (queue.isEmpty()) pending.remove(player);
        return List.copyOf(released);
    }

    boolean hasPending(P player) {
        Deque<U> queue = pending.get(player);
        return queue != null && !queue.isEmpty();
    }

    /** Total weight currently queued (not yet delivered) for a player; 0 if none. Non-mutating. */
    int pendingWeight(P player) {
        Deque<U> queue = pending.get(player);
        if (queue == null) return 0;
        int total = 0;
        for (U batch : queue) total += weigher.applyAsInt(batch);
        return total;
    }

    /** Drops all queued-but-undelivered work for a player, e.g. on disconnect. */
    void release(P player) { pending.remove(player); }

    void retainPlayers(Collection<P> players) { pending.keySet().retainAll(players); }

    void clear() { pending.clear(); }
}
