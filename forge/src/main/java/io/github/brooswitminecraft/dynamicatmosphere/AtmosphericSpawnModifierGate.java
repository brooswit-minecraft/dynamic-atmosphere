package io.github.brooswitminecraft.dynamicatmosphere;

import io.github.brooswitminecraft.dynamicatmosphere.engine.event.AtmosphericEventEngine;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.EventContext;
import io.github.brooswitminecraft.dynamicatmosphere.engine.event.SpawnModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Generic, data-driven spawn-attempt modifier (spec section 29/45: "Weather Materials
 * may modify vanilla spawn attempts... encourage mobs, suppress mobs, change group
 * sizes."). One gate, registered once, reading a list of {@link SpawnModifier} per
 * material -- unlike {@link VaporHostileSpawnGate} and {@link CreeperExhaustSpawnGate},
 * which predate this layer and are each their own bespoke class for one material/entity
 * pair, adding a new spawn modifier here (built-in or third-party) never requires a new
 * NeoForge listener class, only a new {@link SpawnModifier} entry.
 */
final class AtmosphericSpawnModifierGate {

    private record Entry(AtmosphereMaterial material, Supplier<List<SpawnModifier>> modifiers) { }

    private static final List<Entry> ENTRIES = List.of(
        new Entry(AtmosphereMaterial.ZOMBIE_FOG,
            () -> AtmosphericEvents.zombieFogSpawnModifiers(DynamicAtmosphereServerConfig.snapshot()))
    );

    static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel().getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType()).toString();
        boolean allowed = event.getResult() != MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;

        for (Entry entry : ENTRIES) {
            for (SpawnModifier modifier : entry.modifiers().get()) {
                if (!modifier.entityId().equals(entityId)) continue;
                var state = DynamicAtmosphereMod.materialState(level, entry.material(), pos);
                if (state.isEmpty()) continue;
                var context = EventContext.of(state.get().amount(), state.get().capacity(), false, 0);
                if (!modifier.conditionsMatch(context)) continue;
                double roll = level.random.nextDouble();
                if (!allowed && AtmosphericEventEngine.shouldOverrideDeny(modifier, roll)) {
                    event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT);
                    allowed = true;
                } else if (allowed && AtmosphericEventEngine.shouldSuppress(modifier, roll)) {
                    event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
                    allowed = false;
                }
            }
        }
    }

    private AtmosphericSpawnModifierGate() { }
}
