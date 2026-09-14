package io.github.brooswitminecraft.dynamicatmosphere.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AtmosphericMaterialsTest {
    @Test
    void opticalDensityIsRenderingOnlyAndUnspecifiedSettingsDefaultToOne() {
        assertEquals(List.of(1.0, 1.0, 4.0, 4.0, 1.0, 4.0, 40.0),
            AtmosphericMaterials.ALL.stream().map(m -> m.settings().opticalDensityMultiplier()).toList());
        var vapor = AtmosphericMaterials.VAPOR.settings();
        assertEquals(1, new MaterialSettings(vapor.cellSize(), vapor.lod(), vapor.simulationSpeed(),
            vapor.producerSpeed()).opticalDensityMultiplier());
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new MaterialSettings(
                vapor.cellSize(), vapor.lod(), vapor.simulationSpeed(), vapor.producerSpeed(), bad));
        }
    }

    @Test
    void catalogMatchesIndependentSizesColorsAndSimulationSpeeds() {
        assertEquals(List.of("vapor", "dust", "smoke", "violence", "exhaust", "slime", "ender_gas"),
            AtmosphericMaterials.ALL.stream().map(MaterialDefinition::id).toList());
        assertEquals(List.of(4, 2, 4, 8, 2, 16, 2),
            AtmosphericMaterials.ALL.stream().map(m -> m.settings().cellSize()).toList());
        assertEquals(java.util.Collections.nCopies(7, 1.0),
            AtmosphericMaterials.ALL.stream().map(m -> m.settings().simulationSpeed()).toList());
        assertEquals(List.of(MaterialDefinition.Color.values()),
            AtmosphericMaterials.ALL.stream().map(MaterialDefinition::color).toList());
    }

    @Test
    void allProducerSpeedsUseTheSameBaseline() {
        assertEquals(java.util.Collections.nCopies(7, OptionalDouble.of(1)),
            AtmosphericMaterials.ALL.stream().map(m -> m.settings().producerSpeed()).toList());
    }

    @Test
    void lodUsesMaterialRelativeScalesAndExactViewExtents() {
        var vapor = List.of(new MaterialSettings.LodBand(1, 0.5), new MaterialSettings.LodBand(2, 1),
            new MaterialSettings.LodBand(4, 2));
        var dust = List.of(new MaterialSettings.LodBand(1, 0.25));
        var violence = List.of(new MaterialSettings.LodBand(1, 1), new MaterialSettings.LodBand(2, 2));
        assertEquals(List.of(vapor, dust, vapor, violence, dust, violence, dust),
            AtmosphericMaterials.ALL.stream().map(m -> m.settings().lod()).toList());
    }

    @Test
    void producerIdentitiesRemainDistinctWithoutRatesOrActivation() {
        assertEquals(Set.of(MaterialDefinition.Producer.EXISTING_VAPOR_RULES,
            MaterialDefinition.Producer.SNOW_ICE_SURFACE), AtmosphericMaterials.VAPOR.producers());
        assertEquals(List.of(2, 6, 7, 2, 4, 1, 9),
            AtmosphericMaterials.ALL.stream().map(m -> m.producers().size()).toList());
        var identities = new HashSet<MaterialDefinition.Producer>();
        AtmosphericMaterials.ALL.forEach(m -> identities.addAll(m.producers()));
        assertEquals(Set.of(MaterialDefinition.Producer.values()), identities);
        assertEquals(Set.of(MaterialDefinition.Transformation.GRAVEL), AtmosphericMaterials.DUST.transformations());
        assertEquals(Set.of(MaterialDefinition.Transformation.ZOMBIE), AtmosphericMaterials.VIOLENCE.transformations());
        assertEquals(Set.of(MaterialDefinition.Transformation.SLIME), AtmosphericMaterials.SLIME.transformations());
        assertEquals(Set.of(MaterialDefinition.Transformation.WATER), AtmosphericMaterials.VAPOR.transformations());
        assertTrue(AtmosphericMaterials.SMOKE.transformations().isEmpty());
        assertTrue(AtmosphericMaterials.EXHAUST.transformations().isEmpty());
        assertTrue(AtmosphericMaterials.ENDER_GAS.transformations().isEmpty());
    }

    @Test
    void configurationDefensivelyCopiesCollections() {
        var bands = new ArrayList<>(AtmosphericMaterials.VAPOR.settings().lod());
        var settings = new MaterialSettings(4, bands, 1, OptionalDouble.empty());
        bands.clear();
        assertEquals(3, settings.lod().size());
        var producers = new HashSet<>(AtmosphericMaterials.DUST.producers());
        var transformations = new HashSet<>(AtmosphericMaterials.DUST.transformations());
        var custom = new MaterialDefinition("custom", MaterialDefinition.Color.BROWN, settings, producers, transformations);
        producers.clear();
        transformations.clear();
        assertEquals(6, custom.producers().size());
        assertEquals(1, custom.transformations().size());
        assertThrows(UnsupportedOperationException.class, () -> settings.lod().clear());
        assertThrows(UnsupportedOperationException.class, () -> custom.producers().clear());
        assertThrows(UnsupportedOperationException.class, () -> AtmosphericMaterials.ALL.clear());
    }

    @Test
    void rejectsInvalidSizesSpeedsAndLodOrdering() {
        var lod = AtmosphericMaterials.VAPOR.settings().lod();
        assertThrows(IllegalArgumentException.class, () -> new MaterialSettings(0, lod, 1, OptionalDouble.empty()));
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new MaterialSettings(4, lod, bad, OptionalDouble.empty()));
            assertThrows(IllegalArgumentException.class, () -> new MaterialSettings(4, lod, 1, OptionalDouble.of(bad)));
            assertThrows(IllegalArgumentException.class, () -> new MaterialSettings.LodBand(1, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new MaterialSettings(4, List.of(), 1, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MaterialSettings(4,
            List.of(new MaterialSettings.LodBand(1, 1), new MaterialSettings.LodBand(2, 0.5)), 1, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MaterialSettings(4,
            List.of(new MaterialSettings.LodBand(2, 1), new MaterialSettings.LodBand(1, 2)), 1, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MaterialSettings.LodBand(0, 1));
    }

    @Test
    void rejectsInvalidMaterialIdentityAndMissingSettings() {
        for (String id : List.of("", "Ender Gas", "../vapor")) {
            assertThrows(IllegalArgumentException.class, () -> new MaterialDefinition(id,
                MaterialDefinition.Color.CURRENT_FOG, AtmosphericMaterials.VAPOR.settings(), Set.of(), Set.of()));
        }
        assertThrows(NullPointerException.class, () -> new MaterialDefinition("vapor",
            MaterialDefinition.Color.CURRENT_FOG, null, Set.of(), Set.of()));
    }
}
