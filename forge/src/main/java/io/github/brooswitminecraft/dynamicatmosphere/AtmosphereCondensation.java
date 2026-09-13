package io.github.brooswitminecraft.dynamicatmosphere;

/** Pure policy for deciding when atmospheric material condenses. */
final class AtmosphereCondensation {

    private static final double MAX_PROBABILITY = 0.1;

    private AtmosphereCondensation() {
    }

    static double probability(int amount, int capacity) {
        if (capacity <= 0 || amount <= 0) {
            return 0.0;
        }
        double fullness = (double) amount / capacity;
        if (fullness <= 0.5) {
            return 0.0;
        }
        return Math.min(MAX_PROBABILITY, (fullness - 0.5) * 0.2);
    }

    static boolean shouldCondense(int amount, int capacity, double roll) {
        if (!(roll >= 0.0 && roll < 1.0)) {
            throw new IllegalArgumentException("roll must be in [0, 1)");
        }
        return roll < probability(amount, capacity);
    }

    /** Rounds one quarter down to whole material units, with one consumed for any positive amount. */
    static int consumedAmount(int amount) {
        return amount <= 0 ? 0 : Math.max(1, amount / 4);
    }
}
