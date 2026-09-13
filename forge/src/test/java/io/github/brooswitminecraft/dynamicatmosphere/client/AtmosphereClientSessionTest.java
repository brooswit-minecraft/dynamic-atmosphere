package io.github.brooswitminecraft.dynamicatmosphere.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereClientSessionTest {
    private static List<AtmosphereClientCache.Update> update(int amount) {
        return List.of(new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(-1, 4, 0), amount, 1000));
    }

    @Test
    void snapshotAndFollowingDeltasSurviveBeforeWorldExists() {
        var session = new AtmosphereClientSession();
        session.receive("overworld", true, update(400));
        session.receive("overworld", false, update(800));
        session.world(null);
        assertEquals(0, session.cache().size());
        session.world("overworld");
        for (int i = 0; i < 10; i++) session.cache().advance();
        assertEquals(800, session.cache().visible(0).getFirst().amount());
    }

    @Test
    void wrongDimensionAndDeferredOldPacketsNeverRender() {
        var session = new AtmosphereClientSession();
        session.receive("overworld", true, update(400));
        session.world("nether");
        session.receive("overworld", true, update(900));
        session.receive("nether", false, update(500));
        assertEquals(0, session.cache().size());
        session.receive("nether", true, update(600));
        assertEquals(1, session.cache().size());
    }

    @Test
    void disconnectClearsPendingAndActiveAndRequiresNewSnapshot() {
        var session = new AtmosphereClientSession();
        session.receive("overworld", true, update(400));
        session.clear();
        session.world("overworld");
        session.receive("overworld", false, update(500));
        assertEquals(0, session.cache().size());
        session.receive("overworld", true, update(600));
        session.clear();
        session.world("overworld");
        assertEquals(0, session.cache().size());
    }

    @Test
    void worldUnloadSupportsNewEarlySnapshotWithoutOldCells() {
        var session = new AtmosphereClientSession();
        session.world("overworld");
        session.receive("overworld", true, update(400));
        session.world(null);
        session.receive("nether", true, update(500));
        session.world("nether");
        assertEquals(1, session.cache().size());
        session.world("overworld");
        assertEquals(0, session.cache().size());
    }

    @Test
    void pendingViewIsNotTruncatedAndIsReleasedOnDisconnect() {
        var session = new AtmosphereClientSession();
        session.receive("overworld", false, update(800));
        session.world("overworld");
        assertEquals(0, session.cache().size());
        session.clear();
        session.receive("overworld", true, List.of());
        for (int batch = 0; batch < 3; batch++) {
            final int offset = batch * 3000;
            session.receive("overworld", false, IntStream.range(offset, offset + 3000)
                .mapToObj(x -> new AtmosphereClientCache.Update(new AtmosphereClientCache.Cell(x, 0, 0), 100, 1000))
                .toList());
        }
        session.world("overworld");
        assertEquals(9000, session.cache().size());
        session.clear();
        assertEquals(0, session.cache().size());
    }

    @Test
    void snapshotCanFinishAfterWorldBecomesReady() {
        var session = new AtmosphereClientSession();
        session.receive("overworld", true, false, update(400));
        session.world("overworld");
        assertEquals(0, session.cache().size());
        assertEquals(1, session.cache().pendingSize());
        session.receive("overworld", false, true, update(800));
        for (int i = 0; i < 10; i++) session.cache().advance();
        assertEquals(800, session.cache().visible(0).getFirst().amount());
    }
}
