package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MINECRAFT-387: "the darker the vapor atmosphere colour is ... the more opaque it should be".
 * Exercises {@link AtmosphereRenderMaterial#vaporOpacity} and {@link
 * AtmosphereRenderMaterial#vaporSliceAlpha} -- the exact methods {@link
 * AtmosphereVolumeRenderer}'s {@code visit()} calls for every VAPOR slice, and the exact
 * combination (boost-then-fade, never fade-then-boost) that method requires. There is no
 * separate/unused helper here standing in for the real renderer routing.
 */
class AtmosphereVaporOpacityTest {
    private static final double BOOST =
        io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().vaporDarknessOpacityBoost();
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLACK = {0, 0, 0};
    private static final float[] MID = {0.5f, 0.5f, 0.5f};

    @Test
    void brightFogLeavesAlphaUnchanged() {
        // luminance 1 -> exponent 1 -> transmittance == 1 - alpha unchanged, for any alpha.
        for (float alpha : new float[] {0, 0.01f, 0.2f, 0.6f, 0.999f}) {
            assertEquals(alpha, AtmosphereRenderMaterial.vaporOpacity(alpha, WHITE), 1e-6f);
        }
    }

    @Test
    void blackFogAppliesTheFullConfiguredBoost() {
        float alpha = 0.3f;
        double expected = 1 - Math.pow(1 - alpha, BOOST);
        assertEquals(expected, AtmosphereRenderMaterial.vaporOpacity(alpha, BLACK), 1e-6);
    }

    @Test
    void midLuminanceBoostIsStrictlyBetweenBrightAndDark() {
        float alpha = 0.3f;
        float bright = AtmosphereRenderMaterial.vaporOpacity(alpha, WHITE);
        float mid = AtmosphereRenderMaterial.vaporOpacity(alpha, MID);
        float dark = AtmosphereRenderMaterial.vaporOpacity(alpha, BLACK);
        assertTrue(bright < mid, "grey fog must boost opacity above the unboosted baseline");
        assertTrue(mid < dark, "grey fog must boost opacity less than pitch-black fog");
    }

    @Test
    void opacityIsMonotonicAsFogDarkensAtFixedAlpha() {
        float alpha = 0.4f;
        float previous = AtmosphereRenderMaterial.vaporOpacity(alpha, new float[] {1, 1, 1});
        for (float luminance = 0.9f; luminance >= 0; luminance -= 0.1f) {
            float[] fog = {luminance, luminance, luminance};
            float current = AtmosphereRenderMaterial.vaporOpacity(alpha, fog);
            assertTrue(current >= previous - 1e-7f,
                "opacity must increase monotonically as fog luminance decreases");
            previous = current;
        }
    }

    @Test
    void zeroAmountStaysFullyInvisibleAtAnyFogColour() {
        for (float[] fog : new float[][] {WHITE, MID, BLACK, {0, 0, 0}}) {
            assertEquals(0f, AtmosphereRenderMaterial.vaporOpacity(0f, fog));
        }
    }

    @Test
    void outputStaysClampedToValidAlphaRangeAtEveryLuminance() {
        for (float alpha : new float[] {0f, 0.1f, 0.5f, 0.9f, 0.999999f}) {
            for (float luminance = 0; luminance <= 1; luminance += 0.05f) {
                float[] fog = {luminance, luminance, luminance};
                float boosted = AtmosphereRenderMaterial.vaporOpacity(alpha, fog);
                assertTrue(boosted >= 0f && boosted <= 1f, "alpha must stay in [0,1]");
                assertTrue(boosted >= alpha - 1e-6f, "the boost must never REDUCE opacity");
            }
        }
    }

    @Test
    void nonFiniteOrOutOfRangeFogChannelsFoldToTheSafeUnboostedDefault() {
        float alpha = 0.3f;
        assertEquals(alpha, AtmosphereRenderMaterial.vaporOpacity(alpha, new float[] {Float.NaN, 1, 1}), 1e-6f);
        assertEquals(alpha, AtmosphereRenderMaterial.vaporOpacity(alpha, new float[] {Float.POSITIVE_INFINITY, 0, 0}), 1e-6f);
        // Clamped, not folded: a channel above 1 or below 0 (never produced by the shader, but
        // not itself non-finite) still yields a valid in-range result, never beyond [0,1].
        float boosted = AtmosphereRenderMaterial.vaporOpacity(alpha, new float[] {-5, -5, -5});
        assertTrue(boosted >= 0f && boosted <= 1f);
        assertTrue(boosted >= alpha - 1e-6f);
    }

    @Test
    void nonFiniteInputAlphaFoldsToZeroRatherThanPassingNaNThroughClamp() {
        // Math.clamp does NOT sanitize NaN (it compares false against both bounds and passes
        // the value through), so this is handled explicitly before any clamp call.
        for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(0f, AtmosphereRenderMaterial.vaporOpacity(bad, BLACK));
            assertTrue(Float.isFinite(AtmosphereRenderMaterial.vaporOpacity(bad, BLACK)));
        }
    }

    @Test
    void boostIsEquivalentToScalingOpticalDensityByTheSameExponent() {
        // The curve is specified as extra optical density: boosting the transmittance exponent
        // by k is identical to multiplying opticalDensityMultiplier by k in sliceAlpha's own
        // -expm1 model (see AtmosphereVolumeGeometry#sliceAlpha). Lock that equivalence down
        // directly against black fog's exponent (1 + (BOOST - 1)), rather than re-deriving it.
        double exponent = 1 + (BOOST - 1);
        float amount = 400;
        double thickness = 2;
        int baseCellSize = 4;
        float unboosted = AtmosphereVolumeGeometry.sliceAlpha(amount, thickness, baseCellSize, 1);
        float scaledDensity = AtmosphereVolumeGeometry.sliceAlpha(amount, thickness, baseCellSize, exponent);
        assertEquals(scaledDensity, AtmosphereRenderMaterial.vaporOpacity(unboosted, BLACK), 1e-6f);
    }

    @Test
    void nonVaporMaterialsIgnoreFogColourEntirelySoTheyNeverNeedTheBoost() {
        // AtmosphereRenderMaterial#channel documents that only VAPOR reads live fog colour;
        // every other material's channel() is identical regardless of fog. That precondition
        // is why AtmosphereVolumeRenderer's visit() only ever routes VAPOR through
        // vaporSliceAlpha and leaves every other material's slice.alpha() as a plain fade
        // multiply.
        for (var material : AtmosphereRenderMaterial.values()) {
            if (material == AtmosphereRenderMaterial.VAPOR) continue;
            for (int channel = 0; channel < 3; channel++) {
                assertEquals(material.channel(channel, WHITE), material.channel(channel, BLACK),
                    "non-vapor material colour must not depend on fog: " + material.name());
            }
        }
    }

    // --- vaporSliceAlpha: the production boost-then-fade combination -----------------------

    @Test
    void boostIsAppliedBeforeFadeNotAfter() {
        // Regression for the exact rendering-order defect caught in review: boosting an
        // already-faded alpha (fade-then-boost) makes the fade band disproportionately MORE
        // opaque than the correct boost-then-fade order, because the boost curve is nonlinear.
        float rawAlpha = 0.5f;
        float fade = 0.5f;
        double boost = 3;
        float correct = AtmosphereRenderMaterial.vaporSliceAlpha(rawAlpha, fade, BLACK, boost);
        double boostThenFade = (1 - Math.pow(1 - rawAlpha, boost)) * fade;
        double fadeThenBoost = 1 - Math.pow(1 - rawAlpha * fade, boost);
        assertEquals(boostThenFade, correct, 1e-6, "must boost the RAW alpha, then apply fade");
        assertTrue(Math.abs(correct - fadeThenBoost) > 1e-3,
            "boost-then-fade and fade-then-boost must differ for a nonlinear boost -- "
                + "otherwise this test can't tell the two orders apart");
        // Pin the literal numbers from the review report, so a regression reproduces its exact
        // failure mode: fade-then-boost would read ~0.578125; boost-then-fade reads 0.4375.
        assertEquals(0.4375, correct, 1e-6);
        assertEquals(0.578125, fadeThenBoost, 1e-6);
    }

    @Test
    void fullOpacityAtNoFadeMatchesVaporOpacityAlone() {
        for (float alpha : new float[] {0f, 0.2f, 0.6f, 0.95f}) {
            for (float[] fog : new float[][] {WHITE, MID, BLACK}) {
                assertEquals(AtmosphereRenderMaterial.vaporOpacity(alpha, fog),
                    AtmosphereRenderMaterial.vaporSliceAlpha(alpha, 1f, fog), 1e-6f);
            }
        }
    }

    @Test
    void zeroFadeStaysFullyInvisibleRegardlessOfAmountOrFogColour() {
        for (float alpha : new float[] {0f, 0.2f, 0.6f, 0.95f, 1f}) {
            for (float[] fog : new float[][] {WHITE, MID, BLACK}) {
                assertEquals(0f, AtmosphereRenderMaterial.vaporSliceAlpha(alpha, 0f, fog));
            }
        }
    }

    @Test
    void combinationIsMonotonicAcrossAmountThicknessFadeAndEveryBoostLimit() {
        // Covers boost = 1 (disabled), the configured default, and the 6 ceiling, per review.
        for (double boost : new double[] {1, BOOST, 6}) {
            for (float amount : new float[] {50, 200, 800}) {
                for (double thickness : new double[] {0.5, 2, 8}) {
                    float rawAlpha = AtmosphereVolumeGeometry.sliceAlpha(amount, thickness);
                    float previous = AtmosphereRenderMaterial.vaporSliceAlpha(rawAlpha, 1f, BLACK, boost);
                    for (float fade = 0.9f; fade >= 0f; fade -= 0.1f) {
                        float current = AtmosphereRenderMaterial.vaporSliceAlpha(rawAlpha, fade, BLACK, boost);
                        assertTrue(current <= previous + 1e-6f,
                            "opacity must not increase as fade decreases (amount=" + amount
                                + " thickness=" + thickness + " boost=" + boost + ")");
                        assertTrue(current >= 0f && current <= 1f, "alpha must stay in [0,1]");
                        previous = current;
                    }
                    assertEquals(0f, AtmosphereRenderMaterial.vaporSliceAlpha(rawAlpha, 0f, BLACK, boost),
                        "fully faded vapor must stay invisible regardless of boost");
                }
            }
        }
    }

    @Test
    void boostOfOneDisablesTheCurveSoCombinationIsJustRawAlphaTimesFade() {
        for (float rawAlpha : new float[] {0f, 0.1f, 0.5f, 0.9f}) {
            for (float fade : new float[] {0f, 0.3f, 1f}) {
                float combined = AtmosphereRenderMaterial.vaporSliceAlpha(rawAlpha, fade, BLACK, 1);
                assertEquals(rawAlpha * fade, combined, 1e-6f);
            }
        }
    }

    @Test
    void nonFiniteFadeFoldsToFullyInvisibleRatherThanPassingNaNThrough() {
        for (float badFade : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float result = AtmosphereRenderMaterial.vaporSliceAlpha(0.5f, badFade, BLACK);
            assertEquals(0f, result);
            assertTrue(Float.isFinite(result));
        }
    }
}
