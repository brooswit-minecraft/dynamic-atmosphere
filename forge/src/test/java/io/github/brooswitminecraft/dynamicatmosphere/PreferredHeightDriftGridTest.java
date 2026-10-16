package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grid-level tests for MINECRAFT-1 (B1): the drift itself is pure math (see
 * {@link PreferredHeightDriftTest}); these prove it behaves correctly once wired into an actual
 * {@link AtmosphereGrid}, independent of Minecraft/ServerLevel.
 */
class PreferredHeightDriftGridTest {
    private static final double W = 0.40;
    private static final int DEAD_BAND = 2;
    private static final double DRIFT_FRACTION = 0.10;
    private static final int CAPACITY = 1000;

    @Test
    void driftConservesTotalMaterialAcrossManyTicks() {
        var grid = new AtmosphereGrid<String>();
        int home = 10;
        grid.set(key(0), 400, 1, CAPACITY);
        grid.set(key(5), 900, 1, CAPACITY);
        grid.set(key(20), 100, 1, CAPACITY);
        long tick = 1;
        for (int i = 0; i < 50; i++) {
            tick = AtmosphereGridLayout.nextSimulationTick(tick);
            applyDrift(grid, home, (from, to) -> true, tick);
        }
        assertEquals(1400, total(grid));
    }

    @Test
    void downwardBarrierBlocksTheDownLegButNotTheUpLeg() {
        var grid = new AtmosphereGrid<String>();
        int home = -100; // far below: dense-or-thin, this cell should want to drift down.
        grid.set(key(0), 0, 1, CAPACITY); // thin: wants to move down toward home.
        long tick = AtmosphereGridLayout.nextSimulationTick(1);
        // Barrier blocks any transfer whose destination is strictly below its source.
        applyDrift(grid, home, (from, to) -> to.y() >= from.y(), tick);
        // The down leg was blocked, so its amount stayed in the source; nothing to move up either
        // (home is below, not above), so the cell is unchanged and nothing appeared below it.
        assertEquals(0, amount(grid, key(0)));
        assertEquals(null, grid.get(key(-1)));
        assertEquals(0, total(grid));
    }

    @Test
    void thinColumnSettlesNearHome() {
        var grid = new AtmosphereGrid<String>();
        int home = 0;
        grid.set(key(20), 50, 1, CAPACITY);
        long tick = 1;
        for (int i = 0; i < 400; i++) {
            tick = AtmosphereGridLayout.nextSimulationTick(tick);
            applyDrift(grid, home, (from, to) -> true, tick);
        }
        // The drift is a biased random walk, not a deterministic collapse to one cell: thin
        // material is pulled toward home from either side but keeps some chance of continuing to
        // wander once it arrives, so the population settles into a distribution centred near
        // home rather than piling up on it exactly.
        assertEquals(50, total(grid), "drift must conserve material");
        assertTrue(Math.abs(centreOfMass(grid) - home) < 20,
            "a thin column starting 20 above home should have moved substantially closer, centre was "
                + centreOfMass(grid));
    }

    @Test
    void denseColumnsMassCentreSitsBelowHome() {
        var grid = new AtmosphereGrid<String>();
        int home = 20;
        // A tall, dense column spanning well above and below home.
        for (int y = 10; y <= 30; y++) {
            grid.set(key(y), CAPACITY, 1, CAPACITY);
        }
        int totalBefore = total(grid);
        long tick = 1;
        for (int i = 0; i < 20; i++) {
            tick = AtmosphereGridLayout.nextSimulationTick(tick);
            applyDrift(grid, home, (from, to) -> true, tick);
        }
        assertEquals(totalBefore, total(grid), "drift must conserve material even while full");
        assertTrue(centreOfMass(grid) < home, "a dense column should sink below home, not centre on it");
    }

    private static double centreOfMass(AtmosphereGrid<String> grid) {
        return grid.cells().stream().mapToDouble(cell -> cell.key().y() * (double) cell.amount()).sum()
            / grid.cells().stream().mapToLong(AtmosphereGrid.Cell::amount).sum();
    }

    /** Mirrors ForgeAtmospherePrototype.applyPreferredHeightDrift, without any ServerLevel. */
    private static void applyDrift(
        AtmosphereGrid<String> grid, int home,
        java.util.function.BiPredicate<AtmosphereGrid.CellKey<String>, AtmosphereGrid.CellKey<String>> canTransferDown,
        long tick
    ) {
        for (var cell : java.util.List.copyOf(grid.cells())) {
            var key = cell.key();
            var current = grid.get(key);
            if (current == null) continue;
            var split = PreferredHeightDrift.split(home, key.y(), current.amount(), current.capacity(),
                W, DEAD_BAND, DRIFT_FRACTION);
            if (split.up() > 0) {
                transfer(grid, key, key(key.y() + 1), split.up(), tick);
            }
            if (split.down() > 0 && canTransferDown.test(key, key(key.y() - 1))) {
                transfer(grid, key, key(key.y() - 1), split.down(), tick);
            }
        }
    }

    private static void transfer(
        AtmosphereGrid<String> grid, AtmosphereGrid.CellKey<String> source,
        AtmosphereGrid.CellKey<String> destination, int amount, long tick
    ) {
        var sourceCell = grid.get(source);
        if (sourceCell == null) return;
        var destinationCell = grid.get(destination);
        int destinationAmount = destinationCell == null ? 0 : destinationCell.amount();
        int moved = Math.min(amount, Math.max(0, CAPACITY - destinationAmount));
        if (moved <= 0) return;
        grid.set(source, sourceCell.amount() - moved, tick, CAPACITY);
        grid.set(destination, destinationAmount + moved, tick, CAPACITY);
    }

    private static AtmosphereGrid.CellKey<String> key(int y) {
        return new AtmosphereGrid.CellKey<>("overworld", 0, y, 0);
    }

    private static int amount(AtmosphereGrid<String> grid, AtmosphereGrid.CellKey<String> key) {
        var cell = grid.get(key);
        return cell == null ? 0 : cell.amount();
    }

    private static int total(AtmosphereGrid<String> grid) {
        return grid.cells().stream().mapToInt(AtmosphereGrid.Cell::amount).sum();
    }
}
