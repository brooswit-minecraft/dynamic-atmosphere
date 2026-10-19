package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgeFanTransportTest {
    @Test
    void filtersAllSixCellFacesWithExclusiveUpperBounds() {
        BlockPos min = new BlockPos(-16, -32, -16);
        assertTrue(ForgeFanTransport.contains(min, 16, min));
        assertTrue(ForgeFanTransport.contains(min, 16, min.offset(15, 15, 15)));
        for (BlockPos offset : List.of(new BlockPos(-1, 0, 0), new BlockPos(0, -1, 0),
            new BlockPos(0, 0, -1), new BlockPos(16, 0, 0), new BlockPos(0, 16, 0),
            new BlockPos(0, 0, 16))) {
            assertFalse(ForgeFanTransport.contains(min, 16, min.offset(offset)));
        }
    }

    @Test
    void ordersRequestsByCoordinatesRegardlessOfEntryIterationOrder() {
        var first = new ForgeFanTransport.Request(Direction.NORTH, 1, false);
        var second = new ForgeFanTransport.Request(Direction.SOUTH, 2, false);
        var third = new ForgeFanTransport.Request(Direction.EAST, 3, false);
        var fourth = new ForgeFanTransport.Request(Direction.WEST, 4, false);
        var entries = List.of(
            new ForgeFanTransport.LocatedRequest(new BlockPos(0, 0, 0), fourth),
            new ForgeFanTransport.LocatedRequest(new BlockPos(-1, 0, 0), third),
            new ForgeFanTransport.LocatedRequest(new BlockPos(-1, 1, -1), second),
            new ForgeFanTransport.LocatedRequest(new BlockPos(-1, 0, -1), first));
        assertEquals(List.of(first, second, third, fourth), ForgeFanTransport.orderedRequests(entries));
    }

    @Test
    void absentCreateReturnsNoRequestsWithoutTouchingTheWorld() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity"));
        assertTrue(ForgeFanTransport.collect(null, null, 4, 0.10).isEmpty());
    }

    @Test
    void rejectsUnboundedCellScans() {
        assertThrows(IllegalArgumentException.class, () -> ForgeFanTransport.collect(null, null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ForgeFanTransport.collect(null, null, 17, 1));
    }

    @Test
    void chunkFanCacheScansEachChunkAtMostOnceAcrossManyCellRequests() {
        var scans = new AtomicInteger();
        var chunkZeroFan = new ForgeFanTransport.LocatedRequest(new BlockPos(2, 2, 2),
            new ForgeFanTransport.Request(Direction.EAST, 5, false));
        var cache = new ForgeFanTransport.ChunkFanCache((chunkX, chunkZ) -> {
            scans.incrementAndGet();
            return chunkX == 0 && chunkZ == 0
                ? new ForgeFanTransport.ChunkScan(List.of(chunkZeroFan.pos()), List.of(chunkZeroFan))
                : ForgeFanTransport.ChunkScan.EMPTY;
        });
        for (int i = 0; i < 11; i++) {
            assertEquals(List.of(chunkZeroFan.request()), cache.requestsInCell(new BlockPos(0, 0, 0), 4));
        }
        assertEquals(1, scans.get());
    }

    @Test
    void chunkFanCacheScansOnlyTheChunksACellOverlapsAndEachAtMostOnce() {
        var calls = new HashMap<Long, Integer>();
        var inChunkZero = new ForgeFanTransport.LocatedRequest(new BlockPos(14, 2, 2),
            new ForgeFanTransport.Request(Direction.EAST, 1, false));
        var inChunkOne = new ForgeFanTransport.LocatedRequest(new BlockPos(17, 2, 2),
            new ForgeFanTransport.Request(Direction.WEST, 2, true));
        var cache = new ForgeFanTransport.ChunkFanCache((chunkX, chunkZ) -> {
            calls.merge(chunkX * 1000L + chunkZ, 1, Integer::sum);
            if (chunkX == 0 && chunkZ == 0) {
                return new ForgeFanTransport.ChunkScan(List.of(inChunkZero.pos()), List.of(inChunkZero));
            }
            if (chunkX == 1 && chunkZ == 0) {
                return new ForgeFanTransport.ChunkScan(List.of(inChunkOne.pos()), List.of(inChunkOne));
            }
            return ForgeFanTransport.ChunkScan.EMPTY;
        });
        var firstCellRequests = cache.requestsInCell(new BlockPos(14, 0, 0), 4);
        assertEquals(List.of(inChunkZero.request(), inChunkOne.request()), firstCellRequests);
        var secondCellRequests = cache.requestsInCell(new BlockPos(14, 2, 2), 2);
        assertEquals(List.of(inChunkZero.request()), secondCellRequests);
        assertEquals(Map.of(0L, 1, 1000L, 1), calls);
    }

    @Test
    void chunkFanCacheFiltersRequestsOutsideTheRequestedCellBounds() {
        var inside = new ForgeFanTransport.LocatedRequest(new BlockPos(1, 1, 1),
            new ForgeFanTransport.Request(Direction.UP, 1, false));
        var outside = new ForgeFanTransport.LocatedRequest(new BlockPos(5, 1, 1),
            new ForgeFanTransport.Request(Direction.DOWN, 1, false));
        var cache = new ForgeFanTransport.ChunkFanCache((chunkX, chunkZ) ->
            new ForgeFanTransport.ChunkScan(List.of(inside.pos(), outside.pos()), List.of(inside, outside)));
        assertEquals(List.of(inside.request()), cache.requestsInCell(new BlockPos(0, 0, 0), 4));
    }

    @Test
    void chunkFanCacheScansOnceForBothPositionDiscoveryAndCellRequestsAcrossMultipleGrids() {
        var scans = new AtomicInteger();
        var fan = new ForgeFanTransport.LocatedRequest(new BlockPos(2, 2, 2),
            new ForgeFanTransport.Request(Direction.EAST, 5, false));
        var cache = new ForgeFanTransport.ChunkFanCache((chunkX, chunkZ) -> {
            scans.incrementAndGet();
            return chunkX == 0 && chunkZ == 0
                ? new ForgeFanTransport.ChunkScan(List.of(fan.pos()), List.of(fan))
                : ForgeFanTransport.ChunkScan.EMPTY;
        });
        assertEquals(List.of(fan.pos()), cache.positionsInChunk(0, 0));
        for (int size : new int[] {1, 2, 4}) {
            assertEquals(List.of(fan.request()), cache.requestsInCell(new BlockPos(0, 0, 0), 4));
        }
        assertEquals(List.of(fan.pos()), cache.positionsInChunk(0, 0));
        assertEquals(1, scans.get());
    }

    @Test
    void chunkFanCacheRejectsUnboundedCellScans() {
        var cache = ForgeFanTransport.ChunkFanCache.forLevel(null, 1);
        assertThrows(IllegalArgumentException.class, () -> cache.requestsInCell(null, 0));
        assertThrows(IllegalArgumentException.class, () -> cache.requestsInCell(null, 17));
    }
}
