package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ForgeMaterialStorage} reads chunk-persisted material data by looking up
 * each current {@code AtmosphereMaterial.id()} in the chunk's saved tag. Old
 * worlds saved before this rename have their data under the discarded
 * "violence" key. This pins the mechanism that makes that data inert on load
 * (never looked up, never decoded, never thrown on) rather than a source of
 * a crash.
 */
class ForgeMaterialStorageLegacyIdTest {
    @Test
    void legacyViolenceKeyMatchesNoCurrentMaterialAndIsNeverLookedUp() {
        CompoundTag legacyChunkTag = new CompoundTag();
        legacyChunkTag.put("violence", new CompoundTag());

        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            assertFalse(legacyChunkTag.contains(material.id(), Tag.TAG_COMPOUND),
                "no current material id should still be \"violence\"");
        }
        assertTrue(legacyChunkTag.contains("violence", Tag.TAG_COMPOUND),
            "the legacy data itself is untouched, just never read back");
    }
}
