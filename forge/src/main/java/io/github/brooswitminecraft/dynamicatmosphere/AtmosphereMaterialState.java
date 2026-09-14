package io.github.brooswitminecraft.dynamicatmosphere;

/** Immutable loaded-cell view returned to gameplay integrations. */
public record AtmosphereMaterialState(int amount, int capacity) {
    public AtmosphereMaterialState {
        if (amount < 0 || capacity < 0 || capacity > AtmosphereGrid.MAX_AMOUNT) {
            throw new IllegalArgumentException("invalid atmospheric material state");
        }
    }

    public boolean moreThanHalfFull() {
        return capacity > 0 && (long) amount * 2 > capacity;
    }
}
