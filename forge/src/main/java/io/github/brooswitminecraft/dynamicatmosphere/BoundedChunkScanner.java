package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.Predicate;

/**
 * MINECRAFT-435: generic, resumable, budget-bounded scan over a chunk's sections, decoupled from
 * any concrete Minecraft chunk type so the resumption/boundedness logic itself -- the part the
 * MINECRAFT-435 review found untested -- is directly unit-testable against an injectable
 * {@link SectionSource} fake, not just exercised indirectly through a real {@code LevelChunk}
 * (which this repo has no GameTest harness to construct).
 *
 * <p>At most {@code maxMatchingSections} sections whose section-level gate ({@link
 * SectionSource#mayMatch}) accepts are actually walked block-by-block per call; a section the
 * gate rejects is free (does not count against the budget) and is still visited exactly once per
 * full pass, so a chunk with no matching sections at all always finishes in a single call
 * regardless of its height. A chunk with MORE matching sections than the budget allows returns
 * {@code exhausted = false} and a {@code nextSection} cursor the caller must pass back in on its
 * next call to resume -- every section is visited exactly once per full pass (never re-walked,
 * never skipped) across however many calls that takes.</p>
 */
final class BoundedChunkScanner {

    static final int BLOCKS_PER_SECTION = 4096;

    /** A chunk's sections, abstracted so {@link #scan} needs no real Minecraft chunk type. */
    interface SectionSource<B> {
        int sectionCount();

        /** Cheap section-level gate (e.g. a palette check); a section it rejects is never walked. */
        boolean mayMatch(int sectionIndex);

        /** The block at local flat index {@code 0..4095} ({@code (y << 8) | (z << 4) | x}) in the section. */
        B blockAt(int sectionIndex, int blockIndex);
    }

    @FunctionalInterface
    interface MatchConsumer<B> {
        void accept(int sectionIndex, int blockIndex, B block);
    }

    record Result(int nextSection, boolean exhausted) {
        static final Result DONE = new Result(0, true);
    }

    /**
     * @param fromSection        the section index to resume at; clamped up to 0
     * @param maxMatchingSections how many gate-accepted sections this call may walk block-by-block
     * @param onMatch            receives {@code (sectionIndex, blockIndex, block)} for every block
     *                           in a walked section that {@code blockMatches} accepts
     */
    static <B> Result scan(
        SectionSource<B> source, int fromSection, int maxMatchingSections,
        Predicate<B> blockMatches, MatchConsumer<B> onMatch
    ) {
        if (maxMatchingSections <= 0) {
            throw new IllegalArgumentException("maxMatchingSections must be positive");
        }
        int sectionCount = source.sectionCount();
        int sectionIndex = Math.max(0, fromSection);
        int walked = 0;
        while (sectionIndex < sectionCount && walked < maxMatchingSections) {
            if (source.mayMatch(sectionIndex)) {
                walked++;
                for (int blockIndex = 0; blockIndex < BLOCKS_PER_SECTION; blockIndex++) {
                    B block = source.blockAt(sectionIndex, blockIndex);
                    if (blockMatches.test(block)) onMatch.accept(sectionIndex, blockIndex, block);
                }
            }
            sectionIndex++;
        }
        return sectionIndex >= sectionCount ? Result.DONE : new Result(sectionIndex, false);
    }

    private BoundedChunkScanner() { }
}
