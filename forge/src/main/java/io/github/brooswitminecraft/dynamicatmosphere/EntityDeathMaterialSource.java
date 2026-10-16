package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Generic entity-death-to-material source (spec section 17, "Entity Sources": "Entity
 * events may produce Weather Materials... Zombie death &rarr; emit small amount of
 * Zombie Fog."). This is one table-driven handler, not a new bespoke class per
 * material/entity pair -- Void Gas's hostile-death source ({@link VoidGasGameplay})
 * predates this generic layer and is left as-is, but any new entity source (built-in or
 * third-party) is just another entry in {@link #ENTRIES}.
 */
final class EntityDeathMaterialSource {

    private record Entry(EntityType<?> entityType, AtmosphereMaterial material, IntSupplier amount) { }

    private static final List<Entry> ENTRIES = List.of(
        new Entry(EntityType.ZOMBIE, AtmosphereMaterial.ZOMBIE_FOG,
            () -> DynamicAtmosphereServerConfig.snapshot().zombieFog().deathEmission())
    );

    static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        EntityType<?> type = event.getEntity().getType();
        for (Entry entry : ENTRIES) {
            if (entry.entityType() != type) continue;
            int amount = entry.amount().getAsInt();
            if (amount > 0) {
                DynamicAtmosphereMod.emitMaterial(level, entry.material(), event.getEntity().blockPosition(), amount);
            }
        }
    }

    private EntityDeathMaterialSource() { }
}
