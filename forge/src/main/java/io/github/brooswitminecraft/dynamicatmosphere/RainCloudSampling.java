package io.github.brooswitminecraft.dynamicatmosphere;

/** Shared rain-source coordinates derived from the configured cloud height. */
final class RainCloudSampling {
    private RainCloudSampling() {
    }

    static Range forLanding(int landingY, int configuredCloudY) {
        return new Range(configuredCloudY, Math.max(landingY, configuredCloudY));
    }

    static boolean isEligible(
        boolean ultraWarm, boolean hasCeiling, boolean cloudInWorldBounds, boolean cloudIsAir, boolean rainingAtCloud
    ) {
        return !ultraWarm && !hasCeiling && cloudInWorldBounds && cloudIsAir && rainingAtCloud;
    }

    record Range(int cloudY, int airborneUpperY) {
    }
}
