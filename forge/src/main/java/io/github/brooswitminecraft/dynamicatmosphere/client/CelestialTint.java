package io.github.brooswitminecraft.dynamicatmosphere.client;

/**
 * MINECRAFT-399: blends every atmospheric material's base colour toward the sun's or moon's
 * light colour as the camera's look direction converges on that body, independent of the
 * fog-derived/config-derived base colour computed by {@link AtmosphereRenderMaterial#channel}.
 * All methods here are pure (no Minecraft/NeoForge types) so the angular math and blend curve
 * are unit-testable without a running client; {@code AtmosphereVolumeRenderer} supplies the
 * Minecraft-specific inputs (sun angle, sky type, rain level, sky occlusion) once per frame.
 *
 * <p><b>Direction model.</b> Mirrors vanilla {@code LevelRenderer#renderSky}'s own transform
 * (identity rotated {@code Y -90deg} then {@code X (sunAngleRadians)}, sun quad centred at local
 * {@code +Y}, moon quad at local {@code -Y}) rather than reinventing sun geometry: composing
 * those two rotations against the local up vector reduces to {@code (-sin(angle), cos(angle), 0)}
 * for the sun; the moon is the same quad flipped to local {@code -Y}, i.e. exactly the negated
 * vector -- vanilla draws sun and moon diametrically opposite, so this needs no separate formula.
 *
 * <p><b>Blend weight.</b> A smoothstep of {@code cos(angle between look direction and body
 * direction)} across the full hemisphere facing the body: zero once the body is more than 90
 * degrees from the look direction (looking away or across), rising smoothly to {@code maxStrength}
 * exactly on the body. A hemisphere-wide falloff (rather than a narrow cone around the body) keeps
 * the transition imperceptibly smooth near the edges of view, in place of inventing a bespoke
 * half-angle as an artistic constant; the one remaining free parameter, {@code maxStrength}
 * (surfaced as {@code celestialTintMaxStrength} in client config), caps the blend so the tint
 * never overrides a material's identity even looking straight at the body -- see that config key's
 * comment for the chosen default and why it is capped well under 1.
 *
 * <p><b>Visibility gating is the caller's job</b> (see {@code AtmosphereVolumeRenderer}): a body
 * below the horizon, in a dimension with no sky bodies, or with the camera column covered
 * contributes a visibility fraction of 0, collapsing {@link #weight} to 0 regardless of angle.
 */
final class CelestialTint {
    private CelestialTint() { }

    record Vector(double x, double y, double z) {
        double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        Vector negate() { return new Vector(-x, -y, -z); }
    }

    /** Vanilla sun direction for a given {@code Level#getSunAngle} radians value; see class javadoc. */
    static Vector sunDirection(double sunAngleRadians) {
        return new Vector(-Math.sin(sunAngleRadians), Math.cos(sunAngleRadians), 0);
    }

    /** The moon sits diametrically opposite the sun in vanilla's sky dome; see class javadoc. */
    static Vector moonDirection(double sunAngleRadians) {
        return sunDirection(sunAngleRadians).negate();
    }

    /**
     * Smoothstep blend weight in {@code [0, maxStrength]} for a body at {@code bodyDirection}
     * (unit vector, camera to body), the camera's normalized look direction, and a
     * {@code visibility} fraction in {@code [0, 1]} (0 = fully hidden/absent; see class javadoc).
     * A non-finite or non-unit {@code lookDirection}/{@code bodyDirection} folds to weight 0 --
     * the safe direction, since it leaves the material exactly as it rendered before this change
     * rather than guessing a tint from garbage input.
     */
    static double weight(Vector lookDirection, Vector bodyDirection, double visibility, double maxStrength) {
        double cosTheta = lookDirection.dot(bodyDirection);
        if (!Double.isFinite(cosTheta) || !Double.isFinite(visibility) || !Double.isFinite(maxStrength)) return 0;
        if (cosTheta <= 0) return 0;
        cosTheta = Math.clamp(cosTheta, 0, 1);
        double smooth = cosTheta * cosTheta * (3 - 2 * cosTheta);
        double clampedVisibility = Math.clamp(visibility, 0, 1);
        double clampedStrength = Math.clamp(maxStrength, 0, 1);
        return smooth * clampedVisibility * clampedStrength;
    }

    /**
     * Blends {@code base} toward {@code sunColor} by {@code sunWeight} and toward {@code moonColor}
     * by {@code moonWeight} as one convex combination: {@code base*(1-sunWeight-moonWeight) +
     * sunColor*sunWeight + moonColor*moonWeight}. Weights are each already bounded to {@code
     * [0, maxStrength]} by {@link #weight}, but their SUM is re-clamped to {@code [0, 1]} here
     * (scaling both down proportionally if needed) so the combination can never go negative or
     * exceed 1 even if a caller feeds weights from different frames/sources; every channel of
     * the result is therefore guaranteed in {@code [0, 1]} whenever the three inputs are.
     * Sun and moon cannot both be near-maximal in practice (they are antipodal, see class
     * javadoc), but this stays correct even if they were.
     */
    static float[] blend(float[] base, float[] sunColor, double sunWeight, float[] moonColor, double moonWeight) {
        double sw = Math.clamp(sunWeight, 0, 1);
        double mw = Math.clamp(moonWeight, 0, 1);
        double sum = sw + mw;
        if (sum > 1) {
            sw /= sum;
            mw /= sum;
            sum = 1;
        }
        double baseWeight = 1 - sum;
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = (float) Math.clamp(base[i] * baseWeight + sunColor[i] * sw + moonColor[i] * mw, 0, 1);
        }
        return result;
    }
}
