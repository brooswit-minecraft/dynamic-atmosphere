package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MINECRAFT-387: "the darker the vapor atmosphere colour is ... the more opaque it should be".
 * Exercises {@link AtmosphereRenderMaterial#vaporOpacity}, the production path's own luminance
 * curve: {@link AtmosphereVolumeRenderer}'s Batch#draw calls this exact method for VAPOR and
 * passes every other material's slice.alpha() through unchanged (see that file) -- there is no
 * separate/unused helper here to accidentally test instead of the real path.
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
        // is why AtmosphereVolumeRenderer.Batch#draw only ever routes VAPOR through
        // vaporOpacity and passes every other material's slice.alpha() straight through.
        for (var material : AtmosphereRenderMaterial.values()) {
            if (material == AtmosphereRenderMaterial.VAPOR) continue;
            for (int channel = 0; channel < 3; channel++) {
                assertEquals(material.channel(channel, WHITE), material.channel(channel, BLACK),
                    "non-vapor material colour must not depend on fog: " + material.name());
            }
        }
    }
}
