package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereWorldIdentityTest {
    @Test
    void worldIdentityRoundTripsThroughSavedData() {
        UUID worldId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        var original = new AtmosphereWorldIdentity(worldId);

        CompoundTag saved = original.save(new CompoundTag(), null);
        var restored = AtmosphereWorldIdentity.load(saved, null);

        assertEquals(worldId, restored.worldId());
    }

    @Test
    void missingIdentityCreatesNewDirtyRandomValue() {
        var first = AtmosphereWorldIdentity.load(new CompoundTag(), null);
        var second = AtmosphereWorldIdentity.load(new CompoundTag(), null);

        assertNotEquals(first.worldId(), second.worldId());
        assertTrue(first.isDirty());
        assertTrue(second.isDirty());
    }
}
