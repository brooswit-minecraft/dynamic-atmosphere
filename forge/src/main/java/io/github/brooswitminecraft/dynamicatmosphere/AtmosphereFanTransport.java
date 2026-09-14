package io.github.brooswitminecraft.dynamicatmosphere;

/** Minecraft-free quantity calculation for one fan during one source-cell processing pass. */
public final class AtmosphereFanTransport {
    private AtmosphereFanTransport() { }

    public static boolean supportsCellSize(int size) {
        return size > 0 && size <= 4;
    }

    public static int movableAmount(int requested, int source, int destination, int capacity, boolean allowed) {
        if (!allowed || requested <= 0 || source <= 0 || destination < 0 || capacity <= 0) return 0;
        // Physical capacity may be exceeded; only the storage ceiling prevents loss by saturation.
        return Math.max(0, Math.min(requested, Math.min(source, AtmosphereGrid.MAX_STORED_AMOUNT - destination)));
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
