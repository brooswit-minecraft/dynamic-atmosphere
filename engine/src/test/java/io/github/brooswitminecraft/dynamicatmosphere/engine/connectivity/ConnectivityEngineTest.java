package io.github.brooswitminecraft.dynamicatmosphere.engine.connectivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brooswitminecraft.dynamicatmosphere.engine.EngineConfig;
import io.github.brooswitminecraft.dynamicatmosphere.engine.adapter.Passability;
import io.github.brooswitminecraft.dynamicatmosphere.engine.adapter.SyntheticTerrain;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.BlockPos;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.CellPos;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.Direction;
import io.github.brooswitminecraft.dynamicatmosphere.engine.grid.WeatherGrid;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests (a) through (g) plus the unknown-terrain test from the ticket, all
 * over {@link SyntheticTerrain}. Every fixture uses cell size 4 (not the
 * spec's default of 16) purely to keep hand-built geometry small; test (f)
 * in {@code WeatherGridTest} is what actually exercises a non-default size
 * as its own concern.
 *
 * <p>Fixture geometry used by most of these tests: within one 4x4x4 cell,
 * carve two triangular prisms separated by a diagonal wall along local
 * {@code x == y}, restricted to the interior z layers (z = 1, 2; z = 0 and
 * z = size-1 stay solid so neither prism touches NORTH or SOUTH). This
 * yields exactly two regions:
 * <ul>
 *   <li>Region A ({@code x < y}): touches WEST (x=0) and UP (y=size-1) only.</li>
 *   <li>Region B ({@code x > y}): touches EAST (x=size-1) and DOWN (y=0) only.</li>
 * </ul>
 * Movement between them requires crossing {@code x == y}, which is solid
 * (6-connected adjacency only — no diagonal steps), so they never connect
 * within the cell.
 */
class ConnectivityEngineTest {

    private static final int SIZE = 4;

    private static WeatherGrid newGrid() {
        return new WeatherGrid(SIZE);
    }

    private static EngineConfig newConfig(long debounceInterval) {
        return new EngineConfig(SIZE, debounceInterval, 100);
    }

    @Test
    void rejectsAConfigWhoseSingleCellSizeDoesNotMatchTheGrid() {
        WeatherGrid grid = new WeatherGrid(8);
        EngineConfig config = new EngineConfig(16, 10, 1);
        assertThrows(IllegalArgumentException.class,
            () -> new ConnectivityEngine(grid, new SyntheticTerrain(), config));
    }

    private static void carveDividedCell(SyntheticTerrain terrain, WeatherGrid grid, CellPos cell) {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (x == y) {
                    continue; // the diagonal wall; left at its solid default
                }
                for (int z = 1; z <= SIZE - 2; z++) {
                    terrain.set(grid.worldOf(cell, new BlockPos(x, y, z)), Passability.PASSABLE);
                }
            }
        }
    }

    private static void fillCellOpen(SyntheticTerrain terrain, WeatherGrid grid, CellPos cell) {
        terrain.fill(
            grid.originOf(cell),
            grid.worldOf(cell, new BlockPos(SIZE - 1, SIZE - 1, SIZE - 1)),
            Passability.PASSABLE);
    }

    /** Breadth-first search over the PUBLIC region-neighbour API only — no engine-internal shortcut. */
    private static boolean bfsReaches(Region start, Region target) {
        Set<Region> visited = new HashSet<>();
        Deque<Region> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Region current = queue.poll();
            if (current == target) {
                return true;
            }
            for (RegionNeighbor n : current.neighbors()) {
                if (visited.add(n.region())) {
                    queue.add(n.region());
                }
            }
        }
        return false;
    }

    @Test
    void fullySolidCellHasZeroRegions() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos cell = new CellPos(5, 5, 5);
        // Terrain defaults to solid; mark it dirty without changing anything
        // (an initial build still needs to be requested at least once).
        engine.markDirtyCell(cell);
        engine.advanceTo(10);

        assertEquals(0, engine.regionsAt(cell).size());
    }

    @Test
    void fullyOpenCellHasOneRegionTouchingAllSixFaces() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos cell = new CellPos(0, 0, 0);
        fillCellOpen(terrain, grid, cell);
        engine.advanceTo(10);

        List<Region> regions = engine.regionsAt(cell);
        assertEquals(1, regions.size());
        assertEquals(EnumSet.allOf(Direction.class), regions.get(0).touchedFaces());
    }

    @Test
    void sectionSevenTest_internalWallSeparatesRegionsDespiteSharedCellFaceOpenings() {
        // Catches the naive implementation that unions all of a cell's own
        // face openings into one connectivity graph per cell -- i.e. treats
        // "this cell has an opening on TOP" and "this cell has an opening on
        // EAST" as meaning TOP connects to EAST -- instead of computing
        // connectivity per REGION.
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        carveDividedCell(terrain, grid, base);

        // Neighbour above: fully open. Region B never reaches y=SIZE-1 (the
        // TOP boundary layer of `base`), so this neighbour can only ever
        // connect to region A.
        CellPos above = grid.neighborOf(base, Direction.UP);
        fillCellOpen(terrain, grid, above);

        // Neighbour east: fully open. Region A never reaches x=SIZE-1 (the
        // EAST boundary layer of `base`), so this neighbour can only ever
        // connect to region B.
        CellPos east = grid.neighborOf(base, Direction.EAST);
        fillCellOpen(terrain, grid, east);

        engine.advanceTo(10);

        List<Region> baseRegions = engine.regionsAt(base);
        assertEquals(2, baseRegions.size());

        Region regionA = baseRegions.stream()
            .filter(r -> r.touchedFaces().equals(EnumSet.of(Direction.WEST, Direction.UP)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a region touching exactly {WEST, UP}"));
        Region regionB = baseRegions.stream()
            .filter(r -> r.touchedFaces().equals(EnumSet.of(Direction.EAST, Direction.DOWN)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a region touching exactly {EAST, DOWN}"));

        assertFalse(regionA.neighbors().stream().anyMatch(n -> n.region() == regionB));
        assertFalse(regionB.neighbors().stream().anyMatch(n -> n.region() == regionA));

        Region aboveRegion = engine.regionsAt(above).get(0);
        Region eastRegion = engine.regionsAt(east).get(0);

        assertTrue(regionA.neighbors().stream()
            .anyMatch(n -> n.direction() == Direction.UP && n.region() == aboveRegion));
        assertTrue(regionB.neighbors().stream()
            .anyMatch(n -> n.direction() == Direction.EAST && n.region() == eastRegion));

        // The graph is genuinely iterable via the public API, and there is
        // no path from the above-cell's region to the east-cell's region
        // through `base` -- despite `base` having an opening on both TOP
        // and EAST.
        assertFalse(bfsReaches(aboveRegion, eastRegion));
    }

    @Test
    void controlTest_oneBlockHoleInTheWallYieldsExactlyOneRegion() {
        // Proves test (a) above is not passing merely because regions always
        // split -- without this control, (a) is not evidence of anything.
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        carveDividedCell(terrain, grid, base);
        terrain.set(grid.worldOf(base, new BlockPos(1, 1, 1)), Passability.PASSABLE);

        engine.advanceTo(10);

        assertEquals(1, engine.regionsAt(base).size());
    }

    @Test
    void rebuildAfterTerrainChange_breakingAndReplacingTheWallTogglesRegionCount() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        EngineConfig config = newConfig(10);
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, config);
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        carveDividedCell(terrain, grid, base);
        engine.advanceTo(10);
        assertEquals(2, engine.regionsAt(base).size());

        BlockPos hole = grid.worldOf(base, new BlockPos(1, 1, 1));

        terrain.set(hole, Passability.PASSABLE);
        engine.advanceTo(20);
        assertEquals(1, engine.regionsAt(base).size());

        terrain.set(hole, Passability.IMPASSABLE);
        engine.advanceTo(30);
        assertEquals(2, engine.regionsAt(base).size());
    }

    @Test
    void notRebuiltWhenNothingChanged_floodFillCountStaysStill() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        carveDividedCell(terrain, grid, base);
        engine.advanceTo(10);
        long afterInitialBuild = engine.floodFillCount();
        assertEquals(1, afterInitialBuild);

        long time = 10;
        for (int i = 0; i < 50; i++) {
            time += 100;
            engine.advanceTo(time);
        }

        assertEquals(afterInitialBuild, engine.floodFillCount(),
            "advancing many steps with no terrain change must not trigger any flood fill");
    }

    @Test
    void debounce_severalChangesToTheSameCellWithinOneIntervalCauseExactlyOneRebuild() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        carveDividedCell(terrain, grid, base);
        engine.advanceTo(10);
        long baseline = engine.floodFillCount();
        assertEquals(1, baseline);

        BlockPos hole = grid.worldOf(base, new BlockPos(1, 1, 1));

        engine.advanceTo(12);
        terrain.set(hole, Passability.PASSABLE); // dirtySince resets to 12
        engine.advanceTo(15); // 15-12=3 < 10: not eligible yet
        terrain.set(hole, Passability.IMPASSABLE); // dirtySince resets to 15
        engine.advanceTo(18); // 18-15=3 < 10: not eligible yet
        terrain.set(hole, Passability.PASSABLE); // dirtySince resets to 18 -- trailing-edge reset

        engine.advanceTo(27); // 27-18=9 < 10: still not eligible
        assertEquals(baseline, engine.floodFillCount(),
            "must not rebuild before the debounce interval has fully elapsed since the LAST change");

        engine.advanceTo(28); // 28-18=10 >= 10: eligible now
        assertEquals(baseline + 1, engine.floodFillCount(),
            "a burst of 3 changes to the same cell within one interval must cost exactly one rebuild");
        assertEquals(1, engine.regionsAt(base).size()); // hole ends up PASSABLE
    }

    @Test
    void rebuildWorkPerStepIsBoundedByConfiguration() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, new EngineConfig(SIZE, 10, 2));

        for (int x = 0; x < 5; x++) {
            engine.markDirtyCell(new CellPos(x, 0, 0));
        }

        assertEquals(2, engine.advanceTo(10).size());
        assertEquals(2, engine.floodFillCount());
        assertEquals(3, engine.queueDepth());

        assertEquals(2, engine.advanceTo(10).size());
        assertEquals(4, engine.floodFillCount());
        assertEquals(1, engine.queueDepth());
    }

    @Test
    void boundaryChange_updatesAdjacencyAsSeenFromTheNeighboringCellWithoutFloodFillingIt() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos west = new CellPos(0, 0, 0);
        CellPos east = grid.neighborOf(west, Direction.EAST);
        fillCellOpen(terrain, grid, west);
        fillCellOpen(terrain, grid, east);
        engine.advanceTo(10);

        Region westRegion = engine.regionsAt(west).get(0);
        Region eastRegion = engine.regionsAt(east).get(0);
        assertTrue(eastRegion.neighbors().stream()
            .anyMatch(n -> n.direction() == Direction.WEST && n.region() == westRegion));

        long floodFillsBefore = engine.floodFillCount();

        // A terrain change entirely within `west`, on its EAST boundary
        // face only: seal the whole shared face from west's side. No block
        // inside `east` is ever touched.
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                terrain.set(grid.worldOf(west, new BlockPos(SIZE - 1, y, z)), Passability.IMPASSABLE);
            }
        }
        engine.advanceTo(20);

        // Only `west` was flood-filled.
        assertEquals(floodFillsBefore + 1, engine.floodFillCount());
        // `east`'s own Region object is untouched -- proof it was never
        // flood-filled, only its stored adjacency view was updated.
        Region eastRegionAfter = engine.regionsAt(east).get(0);
        assertSame(eastRegion, eastRegionAfter);

        // Assert from the NEIGHBOUR's (east's) side -- this is the bug this
        // test exists to catch: reading adjacency from `west`'s own side
        // would still pass even if `east`'s stored view were never updated.
        assertFalse(eastRegionAfter.neighbors().stream().anyMatch(n -> n.direction() == Direction.WEST),
            "the neighbour's own stored view must reflect the boundary change");
    }

    @Test
    void unknownTerrainIsTreatedAsImpassableByTheConnectivityBuilder() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        BlockPos left = grid.worldOf(base, new BlockPos(0, 0, 0));
        BlockPos middle = grid.worldOf(base, new BlockPos(1, 0, 0));
        BlockPos right = grid.worldOf(base, new BlockPos(2, 0, 0));

        terrain.set(left, Passability.PASSABLE);
        // If UNKNOWN were wrongly treated as passable, this would bridge
        // `left` and `right` into a single region.
        terrain.set(middle, Passability.UNKNOWN);
        terrain.set(right, Passability.PASSABLE);

        engine.advanceTo(10);

        List<Region> regions = engine.regionsAt(base);
        assertEquals(2, regions.size(), "UNKNOWN must not be treated as passable by the flood fill");
        for (Region r : regions) {
            assertEquals(1, r.size());
        }

        // Distinguishable from solid at the adapter interface itself, even
        // though the connectivity builder happens to treat both as not
        // passable for flood-fill purposes.
        assertEquals(Passability.UNKNOWN, terrain.passability(middle));
        assertNotEquals(Passability.IMPASSABLE, terrain.passability(middle));
    }

    @Test
    void remapReportGivesOverlapVolumesAcrossASplit() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        fillCellOpen(terrain, grid, base);
        engine.advanceTo(10);
        Region wholeCellRegion = engine.regionsAt(base).get(0);
        assertEquals(SIZE * SIZE * SIZE, wholeCellRegion.size());

        // Split it with a full-height, full-depth diagonal wall (x == y, all z).
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                terrain.set(grid.worldOf(base, new BlockPos(x, x, z)), Passability.IMPASSABLE);
            }
        }
        List<RemapReport> reports = engine.advanceTo(20);
        assertEquals(1, reports.size());
        RemapReport report = reports.get(0);
        assertEquals(base, report.cell());

        List<Region> newRegions = engine.regionsAt(base);
        assertEquals(2, newRegions.size());

        assertEquals(2, report.overlaps().size());
        for (RegionOverlap overlap : report.overlaps()) {
            assertSame(wholeCellRegion, overlap.oldRegion());
            assertTrue(newRegions.contains(overlap.newRegion()));
            assertEquals(overlap.newRegion().size(), overlap.overlapVolume(),
                "every block of a brand-new sub-region was already passable in the old whole-cell region");
        }
    }

    @Test
    void remapReportGivesOverlapVolumesAcrossAMerge() {
        WeatherGrid grid = newGrid();
        SyntheticTerrain terrain = new SyntheticTerrain();
        ConnectivityEngine engine = new ConnectivityEngine(grid, terrain, newConfig(10));
        terrain.onChange(engine::markDirty);

        CellPos base = new CellPos(0, 0, 0);
        carveDividedCell(terrain, grid, base);
        engine.advanceTo(10);
        List<Region> oldRegions = engine.regionsAt(base);
        assertEquals(2, oldRegions.size());
        int oldSizeA = oldRegions.get(0).size();
        int oldSizeB = oldRegions.get(1).size();

        terrain.set(grid.worldOf(base, new BlockPos(1, 1, 1)), Passability.PASSABLE);
        List<RemapReport> reports = engine.advanceTo(20);
        RemapReport report = reports.get(0);

        List<Region> newRegions = engine.regionsAt(base);
        assertEquals(1, newRegions.size());
        Region merged = newRegions.get(0);

        assertEquals(2, report.overlaps().size());
        List<Integer> overlapVolumes = new ArrayList<>();
        for (RegionOverlap overlap : report.overlaps()) {
            assertTrue(oldRegions.contains(overlap.oldRegion()));
            assertSame(merged, overlap.newRegion());
            overlapVolumes.add(overlap.overlapVolume());
        }
        overlapVolumes.sort(null);
        List<Integer> expected = new ArrayList<>(List.of(oldSizeA, oldSizeB));
        expected.sort(null);
        assertEquals(expected, overlapVolumes);
        // The merged region also gained the hole block itself, which was not
        // part of either old region.
        assertEquals(oldSizeA + oldSizeB + 1, merged.size());
    }
}
