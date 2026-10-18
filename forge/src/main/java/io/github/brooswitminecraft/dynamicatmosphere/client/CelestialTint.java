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
 * below the horizon, in a dimension with no sky bodies, or obstructed by terrain/weather along
 * the actual direction to that body contributes a visibility fraction of 0, collapsing
 * {@link #weight} to 0 regardless of angle. Both the horizon and the obstruction check are
 * themselves smoothed ({@link #horizonWeight} and a jittered ray bundle built from
 * {@link #jitterRing}) rather than binary, so a body crossing the horizon or sliding behind
 * cover never pops the tint instantly -- see those methods' own javadoc.
 */
final class CelestialTint {
    private CelestialTint() { }

    record Vector(double x, double y, double z) {
        double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        Vector negate() { return new Vector(-x, -y, -z); }
        Vector add(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        Vector scale(double factor) { return new Vector(x * factor, y * factor, z * factor); }
        Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }
        Vector normalized() {
            double length = Math.sqrt(dot(this));
            return length > 0 && Double.isFinite(length) ? scale(1 / length) : new Vector(0, 0, 0);
        }
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
     * Smoothstep fade for a body crossing the horizon, in {@code [0, 1]}: 0 at or below
     * {@code -halfWidth}, 1 at or above {@code +halfWidth}, smooth in between. Replaces a bare
     * {@code directionY > 0} check (which pops the entire tint instantly as the body crosses
     * Y=0) with a continuous band a few degrees wide either side of the horizon, matching how
     * the angular {@link #weight} curve is already smooth and avoiding a visible seam the ticket's
     * "no abrupt transitions" requirement calls out. Non-finite input, or a non-positive
     * {@code halfWidth}, falls back to the bare step (0 or 1) rather than guessing a band width.
     */
    static double horizonWeight(double directionY, double halfWidth) {
        if (!Double.isFinite(directionY) || !Double.isFinite(halfWidth) || halfWidth <= 0) {
            return directionY > 0 ? 1 : 0;
        }
        double t = Math.clamp((directionY + halfWidth) / (2 * halfWidth), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * A centre ray plus {@code count} rays arranged in a ring around {@code direction}, each
     * displaced by approximately {@code jitterRadius} (small-angle approximation: the exact
     * angular radius is {@code atan(jitterRadius)}, not {@code jitterRadius} itself, which is
     * immaterial at the small radii this is used for) before renormalizing. Used to turn a
     * single binary ray-occlusion test into a smooth {@code clearRays / totalRays} visibility
     * fraction: as the camera moves and the bundle's rays cross a cover edge one at a time
     * rather than all at once, the fraction -- and therefore the tint -- changes gradually
     * instead of popping. The basis is built from an arbitrary stable reference axis ({@code
     * +Y}, or {@code +X} when {@code direction} is nearly parallel to {@code +Y}) so the ring
     * is well-defined for every {@code direction} the sun/moon can take, including straight
     * overhead. A non-unit {@code direction} (including the zero vector) yields a ring of zero
     * vectors -- the caller's occlusion test must treat that as "nothing to sample" rather than
     * a real direction, exactly like {@link #weight}'s own non-finite handling.
     */
    static Vector[] jitterRing(Vector direction, double jitterRadius, int count) {
        Vector reference = Math.abs(direction.y()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector right = reference.cross(direction).normalized();
        Vector up = direction.cross(right).normalized();
        Vector[] rays = new Vector[count + 1];
        rays[0] = direction;
        for (int i = 0; i < count; i++) {
            double theta = 2 * Math.PI * i / count;
            rays[i + 1] = direction.add(right.scale(jitterRadius * Math.cos(theta)))
                .add(up.scale(jitterRadius * Math.sin(theta))).normalized();
        }
        return rays;
    }

    /**
     * Moves {@code current} toward {@code target} by at most {@code maxStep}, landing exactly on
     * {@code target} once within range rather than overshooting. Used to turn the per-frame
     * occlusion sample -- itself only as fine-grained as the ray bundle's ray count, so it can
     * only take a handful of discrete values -- into an actually continuous visibility signal
     * over time: a body crossing a cover edge changes its RAW sampled visibility abruptly from
     * one frame to the next, but the caller feeds that raw value in as {@code target} every
     * frame and keeps {@code current} as persistent state, so the tint interpolates smoothly
     * across several frames instead of jumping straight to the new sample. Non-finite
     * {@code current}/{@code target} fold to {@code target}'s clamped value directly (or 0 if
     * that is also non-finite) -- the safe landing value, never a NaN propagating forward through
     * every later frame's state. A non-positive {@code maxStep} disables smoothing (jumps straight
     * to target), consistent with {@link #horizonWeight}'s own invalid-parameter fallback.
     */
    static double smoothTowards(double current, double target, double maxStep) {
        double safeTarget = Double.isFinite(target) ? Math.clamp(target, 0, 1) : 0;
        if (!Double.isFinite(current) || !Double.isFinite(maxStep) || maxStep <= 0) return safeTarget;
        double diff = safeTarget - current;
        if (Math.abs(diff) <= maxStep) return safeTarget;
        return current + Math.signum(diff) * maxStep;
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
