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
        var result = event.getResult();

        for (Entry entry : ENTRIES) {
            for (SpawnModifier modifier : entry.modifiers().get()) {
                if (!modifier.entityId().equals(entityId)) continue;
                var state = DynamicAtmosphereMod.materialState(level, entry.material(), pos);
                if (state.isEmpty()) continue;
                var context = EventContext.of(state.get().amount(), state.get().capacity(), false, 0);
                if (!modifier.conditionsMatch(context)) continue;
                double roll = level.random.nextDouble();
                result = applyModifier(result, modifier, roll);
            }
        }
        event.setResult(result);
    }

    /**
     * Folds one modifier's roll into the running decision. A vanilla placement check starts
     * as {@code DEFAULT} (vanilla's own predicate decides) and only becomes {@code FAIL} when
     * something -- vanilla itself, or an earlier gate such as {@link VaporHostileSpawnGate} --
     * has explicitly denied it; {@code DEFAULT} is therefore NOT "already allowed" the way an
     * explicit {@code SUCCEED} is -- treating it as such (as an earlier version of this gate
     * did) silently skips the override-deny roll for the ordinary case where vanilla's own
     * predicate was always going to fail, since nothing had set {@code FAIL} yet. Overriding
     * must also set {@code SUCCEED}, never {@code DEFAULT}: resetting an explicit {@code FAIL}
     * back to {@code DEFAULT} only asks vanilla to decide again, which can discard another
     * gate's deliberate denial without actually forcing the spawn through. Suppression is the
     * mirror image: anything other than an explicit {@code FAIL} is still "could still spawn",
     * so it may be knocked down to {@code FAIL}.
     *
     * <p>Package-private for {@code AtmosphericSpawnModifierGateTest}: a plain
     * {@code Result}-in/{@code Result}-out function has no Minecraft world/level dependency, so
     * it is testable without bootstrapping Minecraft or constructing a {@code MobSpawnEvent}.
     */
    static MobSpawnEvent.SpawnPlacementCheck.Result applyModifier(
        MobSpawnEvent.SpawnPlacementCheck.Result current,
        SpawnModifier modifier,
        double roll
    ) {
        if (current != MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED
            && AtmosphericEventEngine.shouldOverrideDeny(modifier, roll)) {
            return MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED;
        }
        if (current != MobSpawnEvent.SpawnPlacementCheck.Result.FAIL
            && AtmosphericEventEngine.shouldSuppress(modifier, roll)) {
            return MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;
        }
        return current;
    }

    private AtmosphericSpawnModifierGate() { }
}
