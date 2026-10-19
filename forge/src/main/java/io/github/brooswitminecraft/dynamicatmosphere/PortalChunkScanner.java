package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

/**
 * MINECRAFT-435: resumable, budget-bounded section/block scan for Nether and End portal blocks in
 * a loaded chunk, shared by {@link ObsidianPowderGameplay} and {@link PortalTransferSink} so the
 * same chunk visit is never scanned twice for the same block types. A {@link BlockPos} is
 * allocated only once a block has actually matched -- never speculatively.
 *
 * <p>Only {@link #scan(LevelChunk, Progress, int)}'s resumable bookkeeping (when is the scan
 * exhausted, is every section visited exactly once) matters for correctness; that logic itself
 * lives in {@link BoundedChunkScanner}, which is unit-tested directly against an injectable
 * fixture. This class is a thin, deliberately-untested adapter over the real Minecraft chunk
 * types, consistent with this repo's existing GameTest-less testing boundary.</p>
 */
final class PortalChunkScanner {
    record Scan(Set<BlockPos> netherPortals, Set<BlockPos> endPortals) {
        static final Scan EMPTY = new Scan(Set.of(), Set.of());
    }

    /** In-progress state for one chunk's scan, carried across calls until the scan is exhausted. */
    static final class Progress {
        private int nextSection;
        private final Set<BlockPos> nether = new HashSet<>();
        private final Set<BlockPos> end = new HashSet<>();
    }

    record Result(Progress progress, boolean exhausted, Scan completedScan) { }

    private static boolean isPortalBlock(BlockState state) {
        return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL);
    }

    /**
     * Resumes from {@code previous} (starts fresh if {@code null}), walking at most {@code
     * maxMatchingSections} palette-gated-in sections this call. Returns the completed {@link
     * Scan} once every section has been visited exactly once since the last reset; otherwise
     * returns updated progress the caller must pass back in on its next call.
     */
    static Result scan(LevelChunk chunk, Progress previous, int maxMatchingSections) {
        Progress progress = previous != null ? previous : new Progress();
        var source = LevelChunkSectionSource.of(chunk, PortalChunkScanner::isPortalBlock);
        var boundedResult = BoundedChunkScanner.scan(source, progress.nextSection, maxMatchingSections,
            PortalChunkScanner::isPortalBlock,
            (sectionIndex, blockIndex, state) -> {
                BlockPos pos = LevelChunkSectionSource.positionOf(chunk, sectionIndex, blockIndex);
                if (state.is(Blocks.NETHER_PORTAL)) progress.nether.add(pos);
                else progress.end.add(pos);
            });
        if (!boundedResult.exhausted()) {
            progress.nextSection = boundedResult.nextSection();
            return new Result(progress, false, null);
        }
        // progress is discarded here (never returned to the caller, and a fresh Progress is
        // created on the next scan() for this chunk) -- its nether/end sets are handed off as-is
        // rather than defensively copied, avoiding an O(n) copy of a result that could otherwise
        // be arbitrarily large for a densely-populated chunk.
        return new Result(null, true, new Scan(progress.nether, progress.end));
    }

    private PortalChunkScanner() { }
}
