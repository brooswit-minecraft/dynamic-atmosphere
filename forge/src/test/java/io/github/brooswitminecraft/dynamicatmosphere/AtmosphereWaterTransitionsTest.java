package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosphereWaterTransitionsTest {

    @Test
    void onlySuccessfulWaterToNonWaterMutationEmitsMaterial() {
        assertEquals(40, AtmosphereWaterTransitions.materialForTransition(true, true, false));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(false, true, false));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(true, true, true));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(true, false, false));
        assertEquals(0, AtmosphereWaterTransitions.materialForTransition(true, false, true));
    }

    @Test
    void repeatedTransitionsCoalescePerCellWithoutLosingMaterial() {
        var pending = new LinkedHashMap<String, Integer>();
        AtmosphereWaterTransitions.coalesce(pending, "cell", 40);
        AtmosphereWaterTransitions.coalesce(pending, "cell", 40);
        AtmosphereWaterTransitions.coalesce(pending, "other", 40);
        AtmosphereWaterTransitions.coalesce(pending, "cell", 0);

        assertEquals(80, pending.get("cell"));
        assertEquals(40, pending.get("other"));
    }
}
