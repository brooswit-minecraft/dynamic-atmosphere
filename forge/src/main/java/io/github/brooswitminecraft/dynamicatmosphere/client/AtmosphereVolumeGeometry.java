package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Camera-facing slices clipped to fixed world cells, including when the camera is inside. */
public final class AtmosphereVolumeGeometry {
    public static final int CELL_SIZE = AtmosphereGridLayout.CELL_SIZE;
    public static final double SLICE_SPACING = CELL_SIZE / 4.0;
    private static final int[][] EDGES = {
        {0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3},
        {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    public record Point(double x, double y, double z) {
        Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        Point subtract(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        Point scale(double n) { return new Point(x * n, y * n, z * n); }
        double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }
        Point normalized() { return scale(1 / Math.sqrt(dot(this))); }
    }

    public record Slice(double depth, float alpha, List<Point> vertices) { }

    /**
     * Clip boundary-crossing slices to an inscribed reach CYLINDER (bounded horizontal
     * X/Z radius, unbounded Y); no fallback can draw past reach. Slice vertices are
     * camera-relative world axes (translation only, no rotation), so a vertex's own
     * x/z components already are its horizontal offset from the camera — the clip
     * planes are vertical half-planes in world X/Z and need no view-direction basis,
     * unlike the sphere this replaced.
     */
    static List<Slice> clipToReach(List<Slice> slices, double reach) {
        double reachSquared = reach * reach;
        var result = new ArrayList<Slice>(slices.size());
        for (Slice slice : slices) {
            if (slice.vertices().stream().allMatch(p -> horizontalDistanceSquared(p) <= reachSquared)) {
                result.add(slice);
                continue;
            }
            // An inscribed 32-sided prism is conservative and only needed at the outer boundary.
            double limit = reach * Math.cos(Math.PI / 32);
            List<Point> polygon = slice.vertices();
            for (int edge = 0; edge < 32 && polygon.size() >= 3; edge++) {
                double angle = (edge + 0.5) * Math.PI / 16;
                double nx = Math.cos(angle), nz = Math.sin(angle);
                var clipped = new ArrayList<Point>();
                Point previous = polygon.getLast();
                double previousGap = previous.x() * nx + previous.z() * nz - limit;
                for (Point point : polygon) {
                    double gap = point.x() * nx + point.z() * nz - limit;
                    if ((gap <= 0) != (previousGap <= 0)) {
                        clipped.add(previous.add(point.subtract(previous).scale(previousGap / (previousGap - gap))));
                    }
                    if (gap <= 0) clipped.add(point);
                    previous = point;
                    previousGap = gap;
                }
                polygon = clipped;
            }
            if (polygon.size() >= 3) result.add(new Slice(slice.depth(), slice.alpha(), List.copyOf(polygon)));
        }
        return result;
    }

    private static double horizontalDistanceSquared(Point p) { return p.x() * p.x() + p.z() * p.z(); }

    /**
     * Continuous 1..0 fade factor: full opacity at/below fadeStart, linearly falling
     * to fully transparent at/beyond maxDistance. Checking the maxDistance cutoff
     * before the fadeStart one means a degenerate band (fadeStart >= maxDistance)
     * still resolves to a hard cutoff rather than dividing by zero.
     */
    static float fadeMultiplier(double distance, double fadeStart, double maxDistance) {
        if (distance >= maxDistance) return 0;
        if (distance <= fadeStart) return 1;
        return (float) (1 - (distance - fadeStart) / (maxDistance - fadeStart));
    }

    public static float sliceAlpha(float amount, double thickness) {
        return sliceAlpha(amount, thickness, CELL_SIZE);
    }

    static float sliceAlpha(float amount, double thickness, int baseCellSize) {
        return sliceAlpha(amount, thickness, baseCellSize, 1);
    }

    static float sliceAlpha(float amount, double thickness, int baseCellSize, double opticalDensityMultiplier) {
        return (float) -Math.expm1(-0.6 * Math.clamp(amount, 0, 1000) / 1000.0
            * opticalDensityMultiplier * thickness / baseCellSize);
    }

    public static List<Slice> slices(AtmosphereClientCache.Cell cell, float amount, Point camera, Point look) {
        return boxSlices((double) cell.x() * CELL_SIZE, (double) cell.y() * CELL_SIZE, (double) cell.z() * CELL_SIZE,
            CELL_SIZE, CELL_SIZE / (double) io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().slicesPerBaseCell(), amount, camera, look);
    }

    public static List<Slice> coarseSlices(int sectionX, int sectionY, int sectionZ, float amount, Point camera, Point look) {
        return boxSlices((double) sectionX * 16, (double) sectionY * 16, (double) sectionZ * 16,
            16, 16, amount, camera, look);
    }

    static List<Slice> lodSlices(AtmosphereLodHierarchy.Volume volume, float amount, Point camera, Point look) {
        return lodSlices(volume, amount, camera, look, 1);
    }

    static List<Slice> lodSlices(AtmosphereLodHierarchy.Volume volume, float amount, Point camera, Point look,
                                 double opticalDensityMultiplier) {
        int size = volume.size();
        return boxSlices(volume.blockX(), volume.blockY(), volume.blockZ(), size,
            volume.baseCellSize / (double) io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().slicesPerBaseCell(), amount, camera, look, volume.baseCellSize,
            opticalDensityMultiplier);
    }

    private static List<Slice> boxSlices(double x, double y, double z, int size, double spacing,
                                         float amount, Point camera, Point look) {
        return boxSlices(x, y, z, size, spacing, amount, camera, look, CELL_SIZE, 1);
    }

    private static List<Slice> boxSlices(double x, double y, double z, int size, double spacing,
                                         float amount, Point camera, Point look, int baseCellSize, double opticalDensityMultiplier) {
        if (amount <= 0) return List.of();
        Point forward = look.normalized();
        Point right = forward.cross(Math.abs(forward.y) < 0.9 ? new Point(0, 1, 0) : new Point(1, 0, 0)).normalized();
        Point up = right.cross(forward);
        Point[] corners = new Point[8];
        double near = Double.POSITIVE_INFINITY;
        double far = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            // Subtract in double precision before submitting floats to avoid far-world jitter.
            Point point = new Point(
                x + ((i & 1) == 0 ? 0 : size) - camera.x,
                y + ((i & 2) == 0 ? 0 : size) - camera.y,
                z + ((i & 4) == 0 ? 0 : size) - camera.z);
            corners[i] = point;
            near = Math.min(near, point.dot(forward));
            far = Math.max(far, point.dot(forward));
        }
        List<Slice> result = new ArrayList<>(16);
        if (far <= 0.05) {
            return result;
        }
        // Global view-depth planes keep adjacent cells' samples aligned; partial end slabs
        // receive proportionally smaller opacity instead of adding an opaque boundary shell.
        for (double start = Math.floor(Math.max(near, 0.05) / spacing) * spacing;
             start < far; start += spacing) {
            double low = Math.max(Math.max(start, near), 0.05);
            double high = Math.min(start + spacing, far);
            if (high <= low) {
                continue;
            }
            double depth = (low + high) / 2;
            List<Point> polygon = new ArrayList<>(6);
            for (int[] edge : EDGES) {
                Point a = corners[edge[0]];
                Point b = corners[edge[1]];
                double aDepth = a.dot(forward);
                double difference = b.dot(forward) - aDepth;
                if (Math.abs(difference) < 1.0e-9) {
                    continue;
                }
                double t = (depth - aDepth) / difference;
                if (t < 0 || t > 1) {
                    continue;
                }
                Point intersection = a.add(b.subtract(a).scale(t));
                if (polygon.stream().noneMatch(p -> p.subtract(intersection).dot(p.subtract(intersection)) < 1.0e-12)) {
                    polygon.add(intersection);
                }
            }
            if (polygon.size() < 3) {
                continue;
            }
            Point center = new Point(0, 0, 0);
            for (Point point : polygon) {
                center = center.add(point);
            }
            Point centroid = center.scale(1.0 / polygon.size());
            polygon.sort(Comparator.comparingDouble(p -> Math.atan2(p.subtract(centroid).dot(up), p.subtract(centroid).dot(right))));
            result.add(new Slice(depth, sliceAlpha(amount, high - low, baseCellSize, opticalDensityMultiplier), List.copyOf(polygon)));
        }
        return result;
    }

    private AtmosphereVolumeGeometry() { }
}
