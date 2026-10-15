package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.DoubleSupplier;

/** Chooses a vertical distribution direction from a cell's normalized fullness. */
public final class FillBiasedVerticalMovement {
    private FillBiasedVerticalMovement() { }

    public enum Direction { UP, DOWN }

    public record Probabilities(double towardCloudline, double down) { }

    public static Probabilities probabilities(int amount, int capacity, double towardCloudlineEmpty,
                                               double towardCloudlineFull) {
        double fill = capacity <= 0 ? 0 : Math.clamp((double) amount / capacity, 0, 1);
        double towardCloudline = towardCloudlineEmpty
            + fill * (towardCloudlineFull - towardCloudlineEmpty);
        return new Probabilities(towardCloudline, 1 - towardCloudline);
    }

    /**
     * At or above the cloudline both "toward cloudline" and downward are down, so they collapse
     * to a guaranteed downward selection.
     */
    public static Direction choose(int cellY, int cloudlineY, int amount, int capacity,
                                   double towardCloudlineEmpty, double towardCloudlineFull,
                                   DoubleSupplier random) {
        if (cellY >= cloudlineY) return Direction.DOWN;
        return random.getAsDouble() < probabilities(amount, capacity, towardCloudlineEmpty,
            towardCloudlineFull).towardCloudline() ? Direction.UP : Direction.DOWN;
    }
}
