package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashSet;
import java.util.Set;

/**
 * MINECRAFT-435: single palette-gated section/block scan for Nether and End portal blocks in a
 * loaded chunk, shared by {@link ObsidianPowderGameplay} and {@link PortalTransferSink} so the
 * same chunk visit is never scanned twice for the same block types. A {@link BlockPos} is
 * allocated only once a block in a gated section has actually been tested and matched -- never
 * speculatively for every candidate block the section-level palette gate lets through.
 */
final class PortalChunkScanner {
    record Scan(Set<BlockPos> netherPortals, Set<BlockPos> endPortals) {
        static final Scan EMPTY = new Scan(Set.of(), Set.of());
    }

    static Scan scan(LevelChunk chunk) {
        Set<BlockPos> netherPortals = null;
        Set<BlockPos> endPortals = null;
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            boolean hasNetherPortal = section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL));
            boolean hasEndPortal = section.maybeHas(state -> state.is(Blocks.END_PORTAL));
            if (!hasNetherPortal && !hasEndPortal) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        var state = section.getBlockState(x, y, z);
                        if (hasNetherPortal && state.is(Blocks.NETHER_PORTAL)) {
                            if (netherPortals == null) netherPortals = new HashSet<>();
                            netherPortals.add(new BlockPos(
                                chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z));
                        } else if (hasEndPortal && state.is(Blocks.END_PORTAL)) {
                            if (endPortals == null) endPortals = new HashSet<>();
                            endPortals.add(new BlockPos(
                                chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
        if (netherPortals == null && endPortals == null) return Scan.EMPTY;
        return new Scan(
            netherPortals == null ? Set.of() : Set.copyOf(netherPortals),
            endPortals == null ? Set.of() : Set.copyOf(endPortals));
    }

    private PortalChunkScanner() { }
}
