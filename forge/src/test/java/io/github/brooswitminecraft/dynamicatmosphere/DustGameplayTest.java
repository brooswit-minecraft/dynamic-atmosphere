package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import static io.github.brooswitminecraft.dynamicatmosphere.DustGameplay.MotionEmission.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DustGameplayTest {
    private static final DustGameplay.MotionState GROUNDED =
        new DustGameplay.MotionState(0, 0, true, Long.MIN_VALUE);

    @Test
    void walkingAndRunningRequireActualGroundMovementAndCadence() {
        assertEquals(WALK, movement(GROUNDED, 0.1, 0, true, 0, false, 10));
        assertEquals(RUN, movement(GROUNDED, 0.1, 0, true, 0, true, 10));
        assertEquals(NONE, movement(GROUNDED, 0, 0, true, 0, true, 10));
        assertEquals(NONE, movement(GROUNDED, 0.1, 0, true, 0, false, 11));
    }

    @Test
    void jumpingAndLandingAreTransitionsNotRepeatedAirTicks() {
        assertEquals(JUMP, movement(GROUNDED, 0, 0, false, 0.2, false, 1));
        var airborne = new DustGameplay.MotionState(0, 0, false, Long.MIN_VALUE);
        assertEquals(NONE, movement(airborne, 0, 0, false, -0.2, false, 2));
        assertEquals(LAND, movement(airborne, 0, 0, true, 0, false, 3));
        assertEquals(NONE, movement(airborne.withDamagingLanding(3), 0, 0, true, 0, false, 3));
    }

    @Test
    void damagingLandingUsesFinalDamageAndIsBounded() {
        assertEquals(0, DustGameplay.damagingLandingAmount(0));
        assertEquals(18, DustGameplay.damagingLandingAmount(1));
        assertEquals(64, DustGameplay.damagingLandingAmount(100));
    }

    @Test
    void wetOrFrozenMovementRoutesToVaporExclusively() {
        assertEquals(DustGameplay.MovementMaterial.DUST, DustGameplay.movementMaterial(false, false));
        assertEquals(DustGameplay.MovementMaterial.VAPOR, DustGameplay.movementMaterial(true, false));
        assertEquals(DustGameplay.MovementMaterial.VAPOR, DustGameplay.movementMaterial(false, true));
    }

    private static DustGameplay.MotionEmission movement(
        DustGameplay.MotionState previous, double x, double z, boolean grounded,
        double vertical, boolean running, long tick
    ) {
        return DustGameplay.movementEmission(previous, x, z, grounded, vertical, running, tick, 0);
    }
}
