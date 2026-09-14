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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Sparse chunk-owned persistence shared by Dust and Ender Gas. */
public final class ForgeMaterialStorage {
    private record MaterialSnapshot(List<MaterialChunkData.Cell> cells, CompoundTag unreadable, boolean migrated) { }
    private record Snapshot(Map<AtmosphereMaterial, MaterialSnapshot> materials) { }

    private static final DeferredRegister<AttachmentType<?>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DynamicAtmosphereMod.MODID);
    private static final Supplier<AttachmentType<Snapshot>> DATA = TYPES.register("materials", () ->
        AttachmentType.builder(() -> new Snapshot(Map.of()))
            .serialize(new IAttachmentSerializer<CompoundTag, Snapshot>() {
                @Override
                public Snapshot read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    if (!(holder instanceof ChunkAccess chunk)) {
                        throw new IllegalArgumentException("material storage requires a chunk");
                    }
                    var materials = new EnumMap<AtmosphereMaterial, MaterialSnapshot>(AtmosphereMaterial.class);
                    for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
                        if (!tag.contains(material.id(), Tag.TAG_COMPOUND)) continue;
                        CompoundTag materialTag = tag.getCompound(material.id());
                        try {
                            if (material == AtmosphereMaterial.ENDER_GAS
                                && materialTag.contains("version", Tag.TAG_INT)
                                && materialTag.getInt("version") == MaterialChunkData.VERSION
                                && materialTag.contains("cell_size", Tag.TAG_INT)
                                && materialTag.getInt("cell_size") == 1
                                && materialTag.contains("cells", Tag.TAG_INT_ARRAY)) {
                                materials.put(material, new MaterialSnapshot(MaterialChunkData.migrateEnderGas(
                                    chunk.getPos().x, chunk.getPos().z, chunk.getMinBuildHeight(),
                                    chunk.getMaxBuildHeight(), materialTag.getIntArray("cells")), null, true));
                                continue;
                            }
                            if (!materialTag.contains("version", Tag.TAG_INT)
                                || materialTag.getInt("version") != MaterialChunkData.VERSION
                                || !materialTag.contains("cell_size", Tag.TAG_INT)
                                || materialTag.getInt("cell_size") != material.cellSize()
                                || !materialTag.contains("cells", Tag.TAG_INT_ARRAY)) {
                                throw new IllegalArgumentException("unsupported material chunk format");
                            }
                            materials.put(material, new MaterialSnapshot(MaterialChunkData.decode(material,
                                chunk.getPos().x, chunk.getPos().z, chunk.getMinBuildHeight(),
                                chunk.getMaxBuildHeight(), materialTag.getIntArray("cells")), null, false));
                        } catch (IllegalArgumentException exception) {
                            materials.put(material, new MaterialSnapshot(List.of(), materialTag.copy(), false));
                        }
                    }
                    return new Snapshot(Map.copyOf(materials));
                }

                @Override
                public CompoundTag write(Snapshot snapshot, HolderLookup.Provider provider) {
                    CompoundTag root = new CompoundTag();
                    for (var entry : snapshot.materials().entrySet()) {
                        CompoundTag materialTag;
                        if (entry.getValue().unreadable() != null) {
                            materialTag = entry.getValue().unreadable().copy();
                        } else {
                            materialTag = new CompoundTag();
                            materialTag.putInt("version", MaterialChunkData.VERSION);
                            materialTag.putInt("cell_size", entry.getKey().cellSize());
                            materialTag.putIntArray("cells", MaterialChunkData.encode(entry.getValue().cells()));
                        }
                        root.put(entry.getKey().id(), materialTag);
                    }
                    return root;
                }
            }).build());

    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static List<MaterialChunkData.Cell> read(LevelChunk chunk, AtmosphereMaterial material) {
        Snapshot snapshot = chunk.getExistingDataOrNull(DATA.get());
        MaterialSnapshot materialSnapshot = snapshot == null ? null : snapshot.materials().get(material);
        requireReadable(material, materialSnapshot);
        if (materialSnapshot != null && materialSnapshot.migrated()) chunk.setUnsaved(true);
        return materialSnapshot == null ? List.of() : materialSnapshot.cells();
    }

    public static void write(LevelChunk chunk, AtmosphereMaterial material, List<MaterialChunkData.Cell> cells) {
        Snapshot previous = chunk.getExistingDataOrNull(DATA.get());
        MaterialSnapshot oldMaterial = previous == null ? null : previous.materials().get(material);
        requireReadable(material, oldMaterial);
        List<MaterialChunkData.Cell> validated = MaterialChunkData.validate(material, chunk.getPos().x,
            chunk.getPos().z, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(), cells);
        if ((oldMaterial == null && validated.isEmpty())
            || (oldMaterial != null && oldMaterial.cells().equals(validated))) return;
        var materials = new EnumMap<AtmosphereMaterial, MaterialSnapshot>(AtmosphereMaterial.class);
        if (previous != null) materials.putAll(previous.materials());
        if (validated.isEmpty()) materials.remove(material);
        else materials.put(material, new MaterialSnapshot(validated, null, false));
        chunk.setData(DATA.get(), new Snapshot(Map.copyOf(materials)));
        chunk.setUnsaved(true);
    }

    private static void requireReadable(AtmosphereMaterial material, MaterialSnapshot snapshot) {
        if (snapshot != null && snapshot.unreadable() != null) {
            throw new IllegalStateException("Unsupported/corrupt " + material.id()
                + " data retained; skip this chunk without overwriting it");
        }
    }

    private ForgeMaterialStorage() { }
}
