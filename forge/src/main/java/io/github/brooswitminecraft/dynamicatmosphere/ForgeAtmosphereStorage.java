package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/** Chunk-owned persistence; never retains unloaded chunks in a level/global map. */
public final class ForgeAtmosphereStorage {
    private record Snapshot(List<AtmosphereChunkData.Cell> cells, CompoundTag unreadable) { }

    private static final DeferredRegister<AttachmentType<?>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DynamicAtmosphereMod.MODID);
    private static final Supplier<AttachmentType<Snapshot>> DATA = TYPES.register("atmosphere", () ->
        AttachmentType.builder(() -> new Snapshot(List.of(), null))
            .serialize(new IAttachmentSerializer<CompoundTag, Snapshot>() {
                @Override
                public Snapshot read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    if (!(holder instanceof ChunkAccess chunk)) {
                        throw new IllegalArgumentException("atmospheric storage requires a chunk");
                    }
                    try {
                        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != AtmosphereChunkData.VERSION
                            || !tag.contains("cell_size", Tag.TAG_INT)
                            || tag.getInt("cell_size") != AtmosphereGridLayout.CELL_SIZE
                            || !tag.contains("cells", Tag.TAG_INT_ARRAY)) {
                            throw new IllegalArgumentException("unsupported atmospheric chunk format");
                        }
                        return new Snapshot(AtmosphereChunkData.decode(chunk.getPos().x, chunk.getPos().z,
                            chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(), tag.getIntArray("cells")), null);
                    } catch (IllegalArgumentException exception) {
                        // Preserve unknown/corrupt data verbatim, and refuse to simulate or overwrite it.
                        return new Snapshot(List.of(), tag.copy());
                    }
                }

                @Override
                public CompoundTag write(Snapshot snapshot, HolderLookup.Provider provider) {
                    if (snapshot.unreadable() != null) {
                        return snapshot.unreadable().copy();
                    }
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("version", AtmosphereChunkData.VERSION);
                    tag.putInt("cell_size", AtmosphereGridLayout.CELL_SIZE);
                    tag.putIntArray("cells", AtmosphereChunkData.encode(snapshot.cells()));
                    return tag;
                }
            }).build());

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** Safe for already-loaded chunks too; import once before simulation, never add twice. */
    public static List<AtmosphereChunkData.Cell> read(LevelChunk chunk) {
        Snapshot snapshot = chunk.getExistingDataOrNull(DATA.get());
        requireReadable(snapshot);
        return snapshot == null ? List.of() : snapshot.cells();
    }

    /** Server-thread only. Call after every mutation batch, not first during unload. */
    public static void write(LevelChunk chunk, List<AtmosphereChunkData.Cell> cells) {
        Snapshot previous = chunk.getExistingDataOrNull(DATA.get());
        requireReadable(previous);
        List<AtmosphereChunkData.Cell> snapshot = AtmosphereChunkData.validate(chunk.getPos().x, chunk.getPos().z,
            chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(), cells);
        if ((previous == null && snapshot.isEmpty()) || (previous != null && previous.cells().equals(snapshot))) {
            return;
        }
        chunk.setData(DATA.get(), new Snapshot(snapshot, null));
        chunk.setUnsaved(true);
    }

    private static void requireReadable(Snapshot snapshot) {
        if (snapshot != null && snapshot.unreadable() != null) {
            throw new IllegalStateException("Unsupported/corrupt atmospheric chunk data retained; skip this chunk without overwriting it");
        }
    }

    private ForgeAtmosphereStorage() { }
}
