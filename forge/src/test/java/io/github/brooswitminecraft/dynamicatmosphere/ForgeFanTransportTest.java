package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
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
        var first = new ForgeFanTransport.Request(Direction.NORTH, 1);
        var second = new ForgeFanTransport.Request(Direction.SOUTH, 2);
        var third = new ForgeFanTransport.Request(Direction.EAST, 3);
        var fourth = new ForgeFanTransport.Request(Direction.WEST, 4);
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
}
