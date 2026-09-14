package io.github.brooswitminecraft.dynamicatmosphere.engine;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Independent material configuration, without allocating a grid or scheduling work. */
public record MaterialSettings(
    int cellSize,
    List<LodBand> lod,
    double simulationSpeed,
    OptionalDouble producerSpeed,
    double opticalDensityMultiplier
) {
    public MaterialSettings(int cellSize, List<LodBand> lod, double simulationSpeed, OptionalDouble producerSpeed) {
        this(cellSize, lod, simulationSpeed, producerSpeed, 1);
    }

    public MaterialSettings {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive");
        }
        lod = List.copyOf(lod);
        if (lod.isEmpty()) {
            throw new IllegalArgumentException("lod must contain at least one band");
        }
        double previousDistance = 0;
        int previousScale = 0;
        for (LodBand band : lod) {
            if (band.maxViewDistance() <= previousDistance || band.cellMultiplier() <= previousScale) {
                throw new IllegalArgumentException("LOD distances and cell multipliers must increase");
            }
            previousDistance = band.maxViewDistance();
            previousScale = band.cellMultiplier();
        }
        requirePositiveFinite(simulationSpeed);
        Objects.requireNonNull(producerSpeed, "producerSpeed");
        producerSpeed.ifPresent(MaterialSettings::requirePositiveFinite);
        if (!Double.isFinite(opticalDensityMultiplier) || opticalDensityMultiplier <= 0) {
            throw new IllegalArgumentException("optical density multiplier must be finite and positive");
        }
    }

    /** Speed scales frequency: callers divide their base interval by this multiplier. */
    private static void requirePositiveFinite(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("speed must be finite and positive");
        }
    }

    /** Ordered outer extents in view-distance units; no rendering beyond the last band. */
    public record LodBand(int cellMultiplier, double maxViewDistance) {
        public LodBand {
            if (cellMultiplier <= 0 || !Double.isFinite(maxViewDistance) || maxViewDistance <= 0) {
                throw new IllegalArgumentException("LOD scale and distance must be positive and finite");
            }
        }
    }
}
