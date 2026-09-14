package io.github.brooswitminecraft.dynamicatmosphere;

/** Minecraft-free quantity calculation for one fan during one source-cell processing pass. */
public final class AtmosphereFanTransport {
    private AtmosphereFanTransport() { }

    public static boolean supportsCellSize(int size) {
        return size > 0 && size <= 4;
    }

    public record Exchange(int centerAmount, int[] pulled, int[] pushed) { }

    /** Plan intake first, then output; each stage shares one RPM-sized budget. */
    public static Exchange exchange(int requested, int center, int centerCapacity,
        int[] neighbors, int[] capacities, boolean[] canPull, boolean[] canPush,
        int facing, boolean reverse, int rotation) {
        if (neighbors.length != 6 || capacities.length != 6 || canPull.length != 6
            || canPush.length != 6 || facing < 0 || facing >= 6 || requested < 0
            || center < 0 || center > AtmosphereGrid.MAX_STORED_AMOUNT) {
            throw new IllegalArgumentException("Invalid fan neighborhood");
        }
        int[] limits = new int[6];
        for (int i = 0; i < 6; i++) {
            if (neighbors[i] < 0 || neighbors[i] > AtmosphereGrid.MAX_STORED_AMOUNT)
                throw new IllegalArgumentException("Invalid neighboring amount");
            boolean intake = reverse ? i == facing : i != facing;
            if (intake && capacities[i] >= 0)
                limits[i] = movableAmount(requested, neighbors[i], center, centerCapacity, canPull[i]);
        }
        int[] pulled = evenlyAllocate(Math.min(requested, AtmosphereGrid.MAX_STORED_AMOUNT - center), limits, rotation);
        for (int amount : pulled) center += amount;
        for (int i = 0; i < 6; i++) {
            boolean outlet = reverse ? i != facing : i == facing;
            limits[i] = outlet ? movableAmount(requested, center, neighbors[i] - pulled[i], capacities[i], canPush[i]) : 0;
        }
        int[] pushed = evenlyAllocate(Math.min(requested, center), limits, rotation);
        for (int amount : pushed) center -= amount;
        return new Exchange(center, pulled, pushed);
    }

    private static int[] evenlyAllocate(int budget, int[] limits, int rotation) {
        int[] amounts = new int[limits.length];
        int start = Math.floorMod(rotation, limits.length);
        while (budget > 0) {
            int eligible = 0;
            for (int i = 0; i < limits.length; i++) if (amounts[i] < limits[i]) eligible++;
            if (eligible == 0) break;
            int share = Math.max(1, budget / eligible);
            for (int offset = 0; offset < limits.length && budget > 0; offset++) {
                int i = (start + offset) % limits.length;
                int amount = Math.min(budget, Math.min(share, limits[i] - amounts[i]));
                amounts[i] += amount;
                budget -= amount;
            }
        }
        return amounts;
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
