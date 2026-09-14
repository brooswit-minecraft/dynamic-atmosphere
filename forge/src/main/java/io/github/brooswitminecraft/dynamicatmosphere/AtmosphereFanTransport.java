package io.github.brooswitminecraft.dynamicatmosphere;

/** Minecraft-free quantity calculation for one fan during one source-cell processing pass. */
public final class AtmosphereFanTransport {
    private AtmosphereFanTransport() { }

    public static int movableAmount(int requested, int source, int destination, int capacity, boolean allowed) {
        if (!allowed || requested <= 0 || source <= 0 || destination < 0 || capacity <= destination) return 0;
        return Math.min(requested, Math.min(source, capacity - destination));
    }

    /** Fractional units are discarded; invalid inputs disable transport and overflow saturates. */
    public static int requestedAmount(double rpm, double unitsPerRpm, int maxAmount) {
        if (!Double.isFinite(rpm) || !Double.isFinite(unitsPerRpm)
            || unitsPerRpm <= 0 || maxAmount <= 0) {
            return 0;
        }
        return (int) Math.min(maxAmount, Math.floor(Math.abs(rpm) * unitsPerRpm));
    }
}
