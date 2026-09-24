package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure, Minecraft-free state machine behind magma-removal detection. Keyed on
 * an opaque, equals/hashCode-correct position type so it needs no game types
 * and is fully unit-testable without a running game.
 *
 * <p>A key is "magma" exactly when the caller most recently reported it as
 * such via {@link #observe}. A genuine, roll-worthy removal is a key that was
 * magma and is now reported as not-magma, and was not piston-exempt at that
 * moment (a piston move vacates its source position the same way a removal
 * does, but the block still exists at its destination).</p>
 */
final class MagmaPositionTracker<K> {

    private final Set<K> magma = new HashSet<>();
    private final Set<K> pistonExempt = new HashSet<>();

    /** Marks a position whose block is about to move (not be destroyed) by a piston. */
    void markPistonExempt(K key) {
        pistonExempt.add(key);
    }

    /** Clears an exemption whether or not it was ever consumed by {@link #observe}. */
    void clearPistonExempt(K key) {
        pistonExempt.remove(key);
    }

    /**
     * Reports the current magma-ness of a position after a block change there.
     *
     * @return true iff a tracked magma position genuinely became non-magma and was not piston-exempt
     */
    boolean observe(K key, boolean isMagmaNow) {
        boolean wasMagma = magma.contains(key);
        boolean exempt = pistonExempt.remove(key);
        if (isMagmaNow) {
            magma.add(key);
            return false;
        }
        magma.remove(key);
        return wasMagma && !exempt;
    }

    /** Drops all state for a key, e.g. because its chunk unloaded. */
    void forget(K key) {
        magma.remove(key);
        pistonExempt.remove(key);
    }

    void forgetAll(Collection<K> keys) {
        keys.forEach(this::forget);
    }
}
