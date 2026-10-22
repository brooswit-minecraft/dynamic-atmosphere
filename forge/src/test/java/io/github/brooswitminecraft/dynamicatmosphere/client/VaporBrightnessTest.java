package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VaporBrightnessTest {
    @Test void liveColorsBrightenUniformlyWithoutHueClipping() {
        for (float[] fog : new float[][] {{.01f,.02f,.03f}, {.2f,.4f,.6f}, {.8f,.9f,.99f}}) {
            float r = AtmosphereRenderMaterial.vaporChannel(0, fog);
            float b = AtmosphereRenderMaterial.vaporChannel(2, fog);
            assertTrue(r > fog[0]);
            assertTrue(b <= 1);
            assertEquals(fog[0] / fog[2], r / b, 1e-6);
        }
        assertEquals(0, AtmosphereRenderMaterial.vaporChannel(0, new float[3]));
        assertEquals(1, AtmosphereRenderMaterial.vaporChannel(0, new float[] {1,1,1}));
        assertEquals(0, AtmosphereRenderMaterial.vaporChannel(0, new float[] {Float.NaN,0,0}));
    }
    @Test void netherFogAndMoldLiveConfigMatchesCatalogDefaults() {
        assertEquals(0x804040, AtmosphereRenderMaterial.NETHER_FOG.tuning().color());
        assertEquals(0x706061, AtmosphereRenderMaterial.MOLD.tuning().color());
    }
}
