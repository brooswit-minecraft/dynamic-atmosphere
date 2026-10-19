package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINECRAFT-435: unit tests for {@link BoundedChunkScanner}'s resumable/budget bookkeeping
 * against an injectable fake, per the review's "pure injectable fixtures are sufficient"
 * guidance -- this is the part of a chunk-section scan that actually needs testing; the thin
 * adapter over real Minecraft chunk types ({@link LevelChunkSectionSource}) is not exercised
 * here, consistent with this repo's existing GameTest-less testing boundary.
 */
class BoundedChunkScannerTest {

    /** Every section has {@link #blocksPerSection} blocks; {@code true} marks a matching block. */
    private static final class FakeSource implements BoundedChunkScanner.SectionSource<Boolean> {
        private final boolean[][] sections;
        private final boolean[] paletteGate;
        private final List<Integer> mayMatchCalls = new ArrayList<>();

        FakeSource(boolean[][] sections, boolean[] paletteGate) {
            this.sections = sections;
            this.paletteGate = paletteGate;
        }

        /** A source where every section's palette gate matches exactly where it has a true block. */
        static FakeSource derivingGateFromContent(boolean[][] sections) {
            boolean[] gate = new boolean[sections.length];
            for (int i = 0; i < sections.length; i++) {
                for (boolean block : sections[i]) {
                    if (block) { gate[i] = true; break; }
                }
            }
            return new FakeSource(sections, gate);
        }

        @Override
        public int sectionCount() { return sections.length; }

        @Override
        public boolean mayMatch(int sectionIndex) {
            mayMatchCalls.add(sectionIndex);
            return paletteGate[sectionIndex];
        }

        @Override
        public Boolean blockAt(int sectionIndex, int blockIndex) { return sections[sectionIndex][blockIndex]; }
    }

    private static boolean[][] sectionsWithMatchesAt(int sectionCount, Set<Integer> matchingSections) {
        boolean[][] sections = new boolean[sectionCount][BoundedChunkScanner.BLOCKS_PER_SECTION];
        for (int section : matchingSections) {
            sections[section][0] = true;
            sections[section][1] = true;
        }
        return sections;
    }

    @Test
    void throwsOnNonPositiveBudget() {
        var source = FakeSource.derivingGateFromContent(sectionsWithMatchesAt(4, Set.of(0)));
        assertThrows(IllegalArgumentException.class,
            () -> BoundedChunkScanner.scan(source, 0, 0, b -> b, (s, b, block) -> { }));
    }

    @Test
    void sparseChunkFinishesInOneCallWhenBudgetLeavesRoomToConfirmNoMoreMatchesRemain() {
        // A budget of 1 is exactly used up by the single matching section itself, leaving no
        // room to also confirm (for free) that nothing in sections 41-63 matches either -- that
        // is a one-extra-call inefficiency, not incorrectness (the very next call resumes at 41
        // and immediately reports exhausted). A budget of 2 has room to spare, so the whole
        // sparse chunk finishes in one call as expected.
        var source = FakeSource.derivingGateFromContent(sectionsWithMatchesAt(64, Set.of(40)));
        var found = new HashSet<Integer>();
        var result = BoundedChunkScanner.scan(source, 0, 2, b -> b, (s, b, block) -> found.add(s * 10_000 + b));
        assertTrue(result.exhausted());
        assertEquals(0, result.nextSection());
        assertEquals(Set.of(40 * 10_000, 40 * 10_000 + 1), found);
    }

    @Test
    void nonMatchingSectionsAreFreeAndDoNotConsumeBudget() {
        // 1000 sections, only 2 of them actually matching -- a budget of 2 must still finish in one call.
        var source = FakeSource.derivingGateFromContent(sectionsWithMatchesAt(1000, Set.of(5, 999)));
        var result = BoundedChunkScanner.scan(source, 0, 2, b -> b, (s, b, block) -> { });
        assertTrue(result.exhausted());
        assertEquals(1000, source.mayMatchCalls.size(), "every section is still visited exactly once per pass");
    }

    @Test
    void denseChunkRequiresMultipleCallsAndResumesWithoutOmissionOrDuplication() {
        var matching = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        var source = FakeSource.derivingGateFromContent(sectionsWithMatchesAt(10, matching));
        var found = new ArrayList<Integer>();
        int nextSection = 0;
        int calls = 0;
        BoundedChunkScanner.Result result;
        do {
            result = BoundedChunkScanner.scan(source, nextSection, 3, b -> b,
                (s, b, block) -> found.add(s * 10_000 + b));
            nextSection = result.nextSection();
            calls++;
        } while (!result.exhausted());

        assertTrue(calls >= 4, "a budget of 3 over 10 matching sections must take at least 4 calls");
        var expected = new HashSet<Integer>();
        for (int section : matching) {
            expected.add(section * 10_000);
            expected.add(section * 10_000 + 1);
        }
        assertEquals(expected, Set.copyOf(found), "every matching block found exactly once, none omitted");
        assertEquals(found.size(), Set.copyOf(found).size(), "no block reported more than once across the resumed pass");
    }

    @Test
    void resumeCursorPicksUpExactlyWhereTheLastCallStopped() {
        var source = FakeSource.derivingGateFromContent(sectionsWithMatchesAt(5, Set.of(0, 1, 2, 3, 4)));
        var firstCall = BoundedChunkScanner.scan(source, 0, 2, b -> b, (s, b, block) -> { });
        assertFalse(firstCall.exhausted());
        assertEquals(2, firstCall.nextSection());

        source.mayMatchCalls.clear();
        BoundedChunkScanner.scan(source, firstCall.nextSection(), 2, b -> b, (s, b, block) -> { });
        assertEquals(List.of(2, 3), source.mayMatchCalls.subList(0, 2),
            "the resumed call starts at the returned cursor, not from the beginning");
    }
}
