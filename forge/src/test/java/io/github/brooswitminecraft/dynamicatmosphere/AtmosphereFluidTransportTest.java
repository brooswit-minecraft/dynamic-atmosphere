package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereFluidTransportTest {
    @Test
    void transportSuppressesRemovalButDirectMutationsBeforeAndAfterStillEmit() {
        assertEquals(45, removal());
        AtmosphereFluidTransport.enter();
        try {
            assertTrue(AtmosphereFluidTransport.active());
            assertEquals(0, removal());
        } finally {
            AtmosphereFluidTransport.exit();
        }
        assertFalse(AtmosphereFluidTransport.active());
        assertEquals(45, removal());
    }

    @Test
    void nestedCancelledOrThrowingTicksRestoreOuterScope() {
        AtmosphereFluidTransport.enter();
        try {
            assertThrows(IllegalArgumentException.class, () -> {
                AtmosphereFluidTransport.enter();
                try {
                    assertEquals(0, removal());
                    throw new IllegalArgumentException("simulated fluid failure");
                } finally {
                    AtmosphereFluidTransport.exit();
                }
            });
            assertTrue(AtmosphereFluidTransport.active());
            cancelledTick();
            assertEquals(0, removal());
        } finally {
            AtmosphereFluidTransport.exit();
        }
        assertEquals(45, removal());
    }

    @Test
    void scopeDoesNotLeakToOtherThreads() {
        AtmosphereFluidTransport.enter();
        try {
            assertEquals(45, CompletableFuture.supplyAsync(AtmosphereFluidTransportTest::removal).join());
            assertEquals(0, removal());
        } finally {
            AtmosphereFluidTransport.exit();
        }
    }

    private static int removal() {
        return AtmosphereWaterTransitions.materialForTransition(true, true, false, 0.5);
    }

    private static void cancelledTick() {
        AtmosphereFluidTransport.enter();
        try {
            return;
        } finally {
            AtmosphereFluidTransport.exit();
        }
    }
}
