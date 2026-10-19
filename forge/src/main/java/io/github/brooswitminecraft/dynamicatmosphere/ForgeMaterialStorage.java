package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
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

/** Sparse chunk-owned persistence shared by Dust and Obsidian Powder. */
public final class ForgeMaterialStorage {
    private record Snapshot(Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> materials) { }

    private static final String ATTACHMENT_NAME = "materials";
    private static final DeferredRegister<AttachmentType<?>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DynamicAtmosphereMod.MODID);
    private static final Supplier<AttachmentType<Snapshot>> DATA =
        TYPES.register(ATTACHMENT_NAME, ForgeMaterialStorage::buildAttachmentType);

    private static AttachmentType<Snapshot> buildAttachmentType() {
        return AttachmentType.builder(() -> new Snapshot(Map.of()))
            .serialize(new IAttachmentSerializer<CompoundTag, Snapshot>() {
                @Override
                public Snapshot read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    if (!(holder instanceof ChunkAccess chunk)) {
                        throw new IllegalArgumentException("material storage requires a chunk");
                    }
                    var materials = new EnumMap<AtmosphereMaterial, MaterialChunkData.StoredMaterial>(AtmosphereMaterial.class);
                    for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
                        if (!tag.contains(material.id(), Tag.TAG_COMPOUND)) continue;
                        CompoundTag materialTag = tag.getCompound(material.id());
                        MaterialChunkData.StoredMaterial stored = MaterialChunkData.decodeStored(material,
                            chunk.getPos().x, chunk.getPos().z, chunk.getMinBuildHeight(),
                            chunk.getMaxBuildHeight(), materialTag);
                        if (stored != null) materials.put(material, stored);
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
            }).build();
    }

    public static void register(IEventBus bus) { TYPES.register(bus); }

    /**
     * Test-only. A plain unit test never fires NeoForge's mod-loading {@code RegisterEvent}, so
     * {@link #DATA}'s backing entry in {@code NeoForgeRegistries.ATTACHMENT_TYPES} is never
     * populated the way {@link #register} (wired to the real mod event bus) would populate it in
     * production -- without an entry there, NeoForge's dev-only attachment-type validation throws
     * before {@link #read}/{@link #write}/{@link #writeBatch} can run against a real chunk. This
     * directly inserts the exact same attachment type {@link #buildAttachmentType} builds (the
     * genuine default/serializer logic production uses) at the identical resource key production
     * registers it under, so {@code DATA.get()} resolves to it exactly as it would after a real
     * {@code register(bus)} call. {@code DeferredHolder.get()} additionally looks its backing
     * registry up by name inside {@code BuiltInRegistries.REGISTRY} (the registry-of-registries);
     * a full server normally populates that entry via a {@code NewRegistryEvent} this test never
     * fires either, so that entry is seeded too. Idempotent; production code never calls this.
     */
    static void registerForTesting() {
        registerRegistryOfRegistriesEntryIfAbsent();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DynamicAtmosphereMod.MODID, ATTACHMENT_NAME);
        ResourceKey<AttachmentType<?>> key = ResourceKey.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, id);
        if (NeoForgeRegistries.ATTACHMENT_TYPES.containsKey(id)) return;
        @SuppressWarnings("unchecked")
        var registry = (WritableRegistry<AttachmentType<?>>) (Registry<?>) NeoForgeRegistries.ATTACHMENT_TYPES;
        registry.register(key, buildAttachmentType(), RegistrationInfo.BUILT_IN);
    }

    @SuppressWarnings("unchecked")
    private static void registerRegistryOfRegistriesEntryIfAbsent() {
        ResourceLocation registryId = NeoForgeRegistries.Keys.ATTACHMENT_TYPES.location();
        if (BuiltInRegistries.REGISTRY.containsKey(registryId)) return;
        // BuiltInRegistries.REGISTRY itself is frozen by the time Bootstrap.bootStrap() returns
        // (the same bootstrap this test suite's @BeforeAll already relies on, mirroring
        // ForgeCapacityBlockClassifierTest), so a plain register() throws; briefly unfreeze it
        // to seed this one entry, then restore its frozen state.
        var rootRegistry = (MappedRegistry<Registry<?>>) (Registry<?>) BuiltInRegistries.REGISTRY;
        ResourceKey<Registry<?>> registryKey = (ResourceKey<Registry<?>>) (ResourceKey<?>)
            ResourceKey.create(BuiltInRegistries.REGISTRY.key(), registryId);
        rootRegistry.unfreeze();
        rootRegistry.register(registryKey, NeoForgeRegistries.ATTACHMENT_TYPES, RegistrationInfo.BUILT_IN);
        rootRegistry.freeze();
    }

    public static List<MaterialChunkData.Cell> read(ChunkAccess chunk, AtmosphereMaterial material) {
        Snapshot snapshot = chunk.getExistingDataOrNull(DATA.get());
        MaterialChunkData.StoredMaterial stored = snapshot == null ? null : snapshot.materials().get(material);
        requireReadable(material, stored);
        return stored == null ? List.of() : stored.cells();
    }

    public static void write(ChunkAccess chunk, AtmosphereMaterial material, List<MaterialChunkData.Cell> cells) {
        Snapshot previous = chunk.getExistingDataOrNull(DATA.get());
        MaterialChunkData.StoredMaterial oldMaterial = previous == null ? null : previous.materials().get(material);
        requireReadable(material, oldMaterial);
        List<MaterialChunkData.Cell> validated = MaterialChunkData.validate(material, chunk.getPos().x,
            chunk.getPos().z, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(), cells);
        if ((oldMaterial == null && validated.isEmpty())
            || (oldMaterial != null && oldMaterial.cells().equals(validated))) return;
        var materials = new EnumMap<AtmosphereMaterial, MaterialChunkData.StoredMaterial>(AtmosphereMaterial.class);
        if (previous != null) materials.putAll(previous.materials());
        if (validated.isEmpty()) materials.remove(material);
        else materials.put(material, new MaterialChunkData.StoredMaterial(validated, null));
        chunk.setData(DATA.get(), new Snapshot(Map.copyOf(materials)));
        chunk.setUnsaved(true);
    }

    /**
     * MINECRAFT-437: publishes every dirty material for one chunk as a single coherent snapshot,
     * reading the previous snapshot once, merging all of {@code updates} through
     * {@link MaterialChunkData#mergeBatch} with exactly one {@link EnumMap} copy, and — only if
     * something actually changed — issuing one {@code setData}/{@code setUnsaved} for the chunk,
     * instead of one per dirty material.
     *
     * @return one entry per material whose previous stored data was already retained as
     *     unreadable/corrupt; that material's update in {@code updates} was skipped (not applied),
     *     and the caller should mark it unreadable and stop simulating it, exactly as the
     *     single-material {@link #write} path's thrown {@link IllegalStateException} would have
     *     required — without that failure disturbing any other material in the same batch.
     */
    public static Map<AtmosphereMaterial, IllegalStateException> writeBatch(
            ChunkAccess chunk, Map<AtmosphereMaterial, List<MaterialChunkData.Cell>> updates) {
        if (updates.isEmpty()) return Map.of();
        Snapshot previous = chunk.getExistingDataOrNull(DATA.get());
        Map<AtmosphereMaterial, MaterialChunkData.StoredMaterial> previousMaterials =
            previous == null ? Map.of() : previous.materials();
        MaterialChunkData.BatchMerge merge = MaterialChunkData.mergeBatch(previousMaterials, updates,
            chunk.getPos().x, chunk.getPos().z, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        if (merge.changed()) {
            chunk.setData(DATA.get(), new Snapshot(merge.materials()));
            chunk.setUnsaved(true);
        }
        return merge.failures();
    }

    private static void requireReadable(AtmosphereMaterial material, MaterialChunkData.StoredMaterial stored) {
        if (stored != null && stored.unreadable() != null) {
            throw new IllegalStateException("Unsupported/corrupt " + material.id()
                + " data retained; skip this chunk without overwriting it");
        }
    }

    private ForgeMaterialStorage() { }
}
