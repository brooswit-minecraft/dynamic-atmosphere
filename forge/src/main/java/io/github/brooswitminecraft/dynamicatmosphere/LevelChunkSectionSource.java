package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.function.Predicate;

/**
 * MINECRAFT-435: thin adapter from a real {@link LevelChunk}'s sections to {@link
 * BoundedChunkScanner.SectionSource}, so {@link BoundedChunkScanner}'s resumable/budget logic is
 * the only part of a chunk-section scan that needs testing against real Minecraft chunk types --
 * this adapter is deliberately untested, consistent with this repo's existing GameTest-less
 * testing boundary for this kind of code, but it is now only index arithmetic with no
 * resumption/boundedness logic of its own to get wrong.
 */
record LevelChunkSectionSource(LevelChunkSection[] sections, Predicate<BlockState> paletteGate)
    implements BoundedChunkScanner.SectionSource<BlockState> {

    static LevelChunkSectionSource of(LevelChunk chunk, Predicate<BlockState> paletteGate) {
        return new LevelChunkSectionSource(chunk.getSections(), paletteGate);
    }

    @Override
    public int sectionCount() {
        return sections.length;
    }

    @Override
    public boolean mayMatch(int sectionIndex) {
        return sections[sectionIndex].maybeHas(paletteGate);
    }

    @Override
    public BlockState blockAt(int sectionIndex, int blockIndex) {
        return sections[sectionIndex].getBlockState(blockIndex & 15, blockIndex >> 8, (blockIndex >> 4) & 15);
    }

    /** The world {@link BlockPos} a {@code (sectionIndex, blockIndex)} pair from {@link #blockAt} refers to. */
    static BlockPos positionOf(LevelChunk chunk, int sectionIndex, int blockIndex) {
        int localX = blockIndex & 15;
        int localZ = (blockIndex >> 4) & 15;
        int localY = blockIndex >> 8;
        int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
        ChunkPos chunkPos = chunk.getPos();
        return new BlockPos(chunkPos.getMinBlockX() + localX, baseY + localY, chunkPos.getMinBlockZ() + localZ);
    }
}
