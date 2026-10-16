package io.github.brooswitminecraft.dynamicatmosphere;

/**
 * B1: a thin cell drifts toward its own material's preferred resting height (its "home"); a
 * dense cell sinks. Continuous in fill, with a dead band around home where neither direction
 * dominates.
 */
public final class PreferredHeightDrift {
    private PreferredHeightDrift() { }

    public record Split(int up, int down) { }

    /** +1 above a dead band below home, -1 above a dead band above home, 0 inside the dead band. */
    static int sign(int home, int y, int deadBand) {
        int delta = home - y;
        if (delta > deadBand) return 1;
        if (delta < -deadBand) return -1;
        return 0;
    }

    /**
     * p_up = clamp01(0.5 + w * ((1 - f) * h - f)), where f is fill fraction and h is {@link #sign}.
     * At f=0 (thin): h=1 -&gt; 0.5+w; h=-1 -&gt; 0.5-w; h=0 -&gt; 0.5. At f=1 (dense): always 0.5-w,
     * regardless of h, since the h term is scaled by (1-f)=0.
     */
    public static double upProbability(int home, int y, int amount, int capacity, double w, int deadBand) {
        double f = capacity <= 0 ? 0 : Math.clamp((double) amount / capacity, 0, 1);
        int h = sign(home, y, deadBand);
        double pUp = 0.5 + w * ((1 - f) * h - f);
        return Math.clamp(pUp, 0, 1);
    }

    public static double downProbability(int home, int y, int amount, int capacity, double w, int deadBand) {
        return 1 - upProbability(home, y, amount, capacity, w, deadBand);
    }

    /**
     * Deterministic expected-value split of {@code driftFraction * amount} between up and down;
     * {@code up + down} never exceeds the rounded drift amount, so any fractional remainder stays
     * in the source cell rather than being moved or randomly assigned.
     */
    public static Split split(
        int home, int y, int amount, int capacity, double w, int deadBand, double driftFraction
    ) {
        int drift = Math.round((float) (amount * driftFraction));
        if (drift <= 0) {
            return new Split(0, 0);
        }
        double pUp = upProbability(home, y, amount, capacity, w, deadBand);
        int up = Math.round((float) (drift * pUp));
        up = Math.min(up, drift);
        int down = drift - up;
        return new Split(up, down);
    }
}
