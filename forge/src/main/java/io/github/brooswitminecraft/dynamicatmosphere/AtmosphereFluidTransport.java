package io.github.brooswitminecraft.dynamicatmosphere;

/** Per-thread fluid-tick scope; one reusable counter, no per-mutation allocation. */
public final class AtmosphereFluidTransport {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private AtmosphereFluidTransport() { }

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        int[] depth = DEPTH.get();
        if (depth[0] <= 0) throw new IllegalStateException("Unbalanced fluid transport scope");
        depth[0]--;
    }

    public static boolean active() {
        return DEPTH.get()[0] != 0;
    }
}
