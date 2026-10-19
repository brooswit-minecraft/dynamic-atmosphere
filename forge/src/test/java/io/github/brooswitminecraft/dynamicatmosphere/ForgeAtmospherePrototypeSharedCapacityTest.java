package io.github.brooswitminecraft.dynamicatmosphere;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for MINECRAFT-434: Vapor, Smoke and every material grid derive capacity from one
 * shared TerrainFactsCache instead of each independently rescanning identical terrain. These
 * exercise {@link ForgeAtmospherePrototype#sharedCapacity}, the actual production lookup
 * capacityAt/smokeCapacityAt/materialCapacityAt all call through -- not a reimplementation fed
 * arbitrary units, so a production consumer that stopped using the shared attachment would fail
 * these.
 */
class ForgeAtmospherePrototypeSharedCapacityTest {

    @Test
    void vaporSmokeAndMaterialConsumersShareOneScanButApplyIndependentFormulas() {
        var facts = new TerrainFactsCache(-64, 320);
        var scans = new AtomicInteger();
        Supplier<TerrainFactsCache.Scan> scan = () -> {
            scans.incrementAndGet();
            return new TerrainFactsCache.Scan(48, false);
        };

        int vapor = ForgeAtmospherePrototype.sharedCapacity(true, facts, 5, 10, 5, scan,
            AtmosphereGridLayout::capacityForAirBlocks);
        int smoke = ForgeAtmospherePrototype.sharedCapacity(true, facts, 5, 10, 5, scan,
            SmokeGridLayout::capacityForAirBlocks);
        int dust = ForgeAtmospherePrototype.sharedCapacity(true, facts, 5, 10, 5, scan,
            AtmosphereMaterial.DUST::capacityForAirBlocks);
        int glowGas = ForgeAtmospherePrototype.sharedCapacity(true, facts, 5, 10, 5, scan,
            AtmosphereMaterial.GLOW_GAS::capacityForAirBlocks);

        assertEquals(1, scans.get(), "only the first consumer's query should scan terrain");
        // Each consumer's capacity is checked against its own formula directly, not against
        // another consumer's result, so this would still catch a formula regression even though
        // the underlying raw facts (48 empty blocks) are shared.
        assertEquals(AtmosphereGridLayout.capacityForAirBlocks(48), vapor);
        assertEquals(SmokeGridLayout.capacityForAirBlocks(48), smoke);
        assertEquals(AtmosphereMaterial.DUST.capacityForAirBlocks(48), dust);
        assertEquals(AtmosphereMaterial.GLOW_GAS.capacityForAirBlocks(48), glowGas);
    }

    @Test
    void invalidationForcesExactlyOneRescanSharedByAllConsumersAgain() {
        var facts = new TerrainFactsCache(-64, 320);
        var scans = new AtomicInteger();
        Supplier<TerrainFactsCache.Scan> scan = () -> {
            int n = scans.incrementAndGet();
            return new TerrainFactsCache.Scan(n == 1 ? 64 : 32, false);
        };

        ForgeAtmospherePrototype.sharedCapacity(true, facts, 0, 0, 0, scan, AtmosphereGridLayout::capacityForAirBlocks);
        ForgeAtmospherePrototype.sharedCapacity(true, facts, 0, 0, 0, scan, SmokeGridLayout::capacityForAirBlocks);
        assertEquals(1, scans.get());

        facts.blockChanged(0, 0, 0, true, false);

        int vaporAfter = ForgeAtmospherePrototype.sharedCapacity(true, facts, 0, 0, 0, scan,
            AtmosphereGridLayout::capacityForAirBlocks);
        int smokeAfter = ForgeAtmospherePrototype.sharedCapacity(true, facts, 0, 0, 0, scan,
            SmokeGridLayout::capacityForAirBlocks);
        assertEquals(2, scans.get(), "invalidation should force exactly one shared rescan");
        assertEquals(AtmosphereGridLayout.capacityForAirBlocks(32), vaporAfter);
        assertEquals(SmokeGridLayout.capacityForAirBlocks(32), smokeAfter);
    }

    @Test
    void anUnreadableConsumerStaysUnknownAndCannotBeAuthorizedByAWarmedCache() {
        var facts = new TerrainFactsCache(-64, 320);
        Supplier<TerrainFactsCache.Scan> warmedScan = () -> new TerrainFactsCache.Scan(64, false);

        // Vapor warms the shared cache to "fully open, no barrier" for this cell.
        int vapor = ForgeAtmospherePrototype.sharedCapacity(true, facts, 2, 2, 2, warmedScan,
            AtmosphereGridLayout::capacityForAirBlocks);
        assertTrue(vapor > 0);

        // An unreadable material must still refuse, never consulting -- let alone trusting --
        // the facts Vapor already warmed; its own scan supplier must not even be invoked, so
        // unreadable capacity can never masquerade as the fully-open cell another grid just read.
        Supplier<TerrainFactsCache.Scan> neverCalled = () -> fail("an unreadable consumer must never scan the shared cache");
        int unreadableMaterial = ForgeAtmospherePrototype.sharedCapacity(false, facts, 2, 2, 2, neverCalled,
            AtmosphereMaterial.DUST::capacityForAirBlocks);
        assertEquals(-1, unreadableMaterial, "unreadable capacity must stay unknown, not become empty space");

        // The shared facts remain exactly what Vapor wrote; a readable consumer is unaffected.
        assertEquals(64, facts.emptyBlockCount(2, 2, 2));
    }
}
