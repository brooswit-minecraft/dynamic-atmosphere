package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Stable random identity for client caches belonging to one server world. */
final class AtmosphereWorldIdentity extends SavedData {
    private static final String DATA_NAME = DynamicAtmosphereMod.MODID + "_world_identity";
    private static final String WORLD_ID_TAG = "world_id";
    private static final Factory<AtmosphereWorldIdentity> FACTORY =
        new Factory<>(AtmosphereWorldIdentity::create, AtmosphereWorldIdentity::load);

    private final UUID worldId;

    AtmosphereWorldIdentity(UUID worldId) {
        this.worldId = worldId;
    }

    static UUID get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME).worldId;
    }

    static AtmosphereWorldIdentity create() {
        AtmosphereWorldIdentity identity = new AtmosphereWorldIdentity(UUID.randomUUID());
        identity.setDirty();
        return identity;
    }

    static AtmosphereWorldIdentity load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID(WORLD_ID_TAG)) {
            return create();
        }
        return new AtmosphereWorldIdentity(tag.getUUID(WORLD_ID_TAG));
    }

    UUID worldId() {
        return worldId;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID(WORLD_ID_TAG, worldId);
        return tag;
    }
}
