package io.github.brooswitminecraft.dynamicatmosphere;

final class AtmosphereSourceStrength {

    private static final int MAX_LIGHT = 15;
    private static final int MAX_DARK_GROUND_EMISSION = 40;

    private AtmosphereSourceStrength() {
    }

    static int lightToEmission(int light) {
        int clampedLight = Math.clamp(light, 0, MAX_LIGHT);
        return MAX_DARK_GROUND_EMISSION * (MAX_LIGHT - clampedLight) / MAX_LIGHT;
    }

    static RainSplit splitRain(int total, int ground) {
        if (total < 0 || ground < 0 || ground > total) {
            throw new IllegalArgumentException("Rain split must be within the total emission");
        }
        return new RainSplit(ground, total - ground);
    }

    record RainSplit(int ground, int cloud) { }
}
