package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Exhaust producers and processed-cell suffocation gameplay.
 *
 * <p>Implementation defaults for otherwise unspecified producer values are one
 * unit from ordinary mobs at 1/128 ticks, one unit from creepers at 1/16 ticks,
 * and four units per point of successful final damage, rounded up and capped at
 * 64. Suffocation samples entity eye positions, rounds material cost upward,
 * costs the cell once when at least one entity accepts vanilla in-wall damage,
 * and never feeds that damage back into the damage producer.</p>
 */
public final class ExhaustGameplay {
    static final int PASSIVE_AMOUNT = 1;
    static final int LIVING_CHANCE_DENOMINATOR = 128;
    static final int CREEPER_CHANCE_DENOMINATOR = 16;
    static final int DAMAGE_UNITS_PER_POINT = 4;
    static final int MAX_DAMAGE_EMISSION = 64;
    private static final ThreadLocal<Set<UUID>> EXHAUST_DAMAGE =
        ThreadLocal.withInitial(HashSet::new);

    record Suffocation(float damage, int cost) {
        static final Suffocation NONE = new Suffocation(0, 0);

        boolean active() {
            return damage > 0 && cost > 0;
        }
    }

    static int passiveEmission(boolean creeper, double roll) {
        return passiveEmission(creeper, roll, PASSIVE_AMOUNT, LIVING_CHANCE_DENOMINATOR,
            CREEPER_CHANCE_DENOMINATOR);
    }

    static int passiveEmission(boolean creeper, double roll, int amount,
                               int livingDenominator, int creeperDenominator) {
        int denominator = creeper ? creeperDenominator : livingDenominator;
        return roll >= 0 && roll < 1.0 / denominator ? amount : 0;
    }

    static int damageEmission(float finalDamage, boolean causedByExhaust) {
        return damageEmission(finalDamage, causedByExhaust, DAMAGE_UNITS_PER_POINT, MAX_DAMAGE_EMISSION);
    }

    static int damageEmission(float finalDamage, boolean causedByExhaust, int amountPerPoint, int maximum) {
        if (causedByExhaust || !(finalDamage > 0) || !Float.isFinite(finalDamage)) return 0;
        return Math.min(maximum, (int) Math.ceil(finalDamage * amountPerPoint));
    }

    static Suffocation suffocation(int amount, int capacity) {
        return suffocation(amount, capacity, 0.5, 1.0, 1.0, 4.0, 0.25, 0.5);
    }

    static Suffocation suffocation(int amount, int capacity, double minimumFullness, double maximumFullness,
                                   double minimumDamage, double maximumDamage,
                                   double minimumCostFraction, double maximumCostFraction) {
        if (amount <= 0 || capacity <= 0 || amount / (double) capacity < minimumFullness) return Suffocation.NONE;
        double range = maximumFullness - minimumFullness;
        double progress = range > 0
            ? Math.clamp((amount / (double) capacity - minimumFullness) / range, 0.0, 1.0)
            : 1.0;
        float damage = (float) (minimumDamage + (maximumDamage - minimumDamage) * progress);
        double fraction = minimumCostFraction + (maximumCostFraction - minimumCostFraction) * progress;
        int cost = Math.clamp((int) Math.ceil(amount * fraction), 1, amount);
        return new Suffocation(damage, cost);
    }

    static boolean sameExhaustCell(BlockPos first, BlockPos second) {
        return AtmosphereMaterial.EXHAUST.cellCoordinate(first.getX())
                == AtmosphereMaterial.EXHAUST.cellCoordinate(second.getX())
            && AtmosphereMaterial.EXHAUST.cellCoordinate(first.getY())
                == AtmosphereMaterial.EXHAUST.cellCoordinate(second.getY())
            && AtmosphereMaterial.EXHAUST.cellCoordinate(first.getZ())
                == AtmosphereMaterial.EXHAUST.cellCoordinate(second.getZ());
    }

    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        DynamicAtmosphereServerConfig.Exhaust config = DynamicAtmosphereServerConfig.snapshot().exhaust();
        int amount = passiveEmission(mob instanceof Creeper, level.random.nextDouble(), config.passiveEmission(),
            config.livingChanceDenominator(), config.creeperChanceDenominator());
        if (amount > 0) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.EXHAUST,
                mob.blockPosition(), amount);
        }
    }

    public void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        DynamicAtmosphereServerConfig.Exhaust config = DynamicAtmosphereServerConfig.snapshot().exhaust();
        int amount = damageEmission(event.getNewDamage(), EXHAUST_DAMAGE.get().contains(entity.getUUID()),
            config.damageEmissionPerPoint(), config.maxDamageEmission());
        if (amount > 0) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.EXHAUST,
                entity.blockPosition(), amount);
        }
    }

    /** Called exactly once for an Exhaust cell selected by its simulation turn. */
    public int processTurn(ServerLevel level, BlockPos source, Iterable<? extends LivingEntity> entities) {
        Optional<AtmosphereMaterialState> state =
            DynamicAtmosphereMod.materialState(level, AtmosphereMaterial.EXHAUST, source);
        if (state.isEmpty()) return 0;
        AtmosphereMaterialState current = state.orElseThrow();
        DynamicAtmosphereServerConfig.Exhaust config = DynamicAtmosphereServerConfig.snapshot().exhaust();
        Suffocation effect = suffocation(current.amount(), current.capacity(), config.suffocationMinFullness(),
            config.suffocationMaxFullness(), config.suffocationMinDamage(), config.suffocationMaxDamage(),
            config.suffocationMinCostFraction(), config.suffocationMaxCostFraction());
        if (!effect.active()) return 0;

        int damaged = 0;
        Set<UUID> visited = new HashSet<>();
        for (LivingEntity entity : entities) {
            BlockPos exposure = BlockPos.containing(entity.getEyePosition());
            if (!visited.add(entity.getUUID()) || !entity.isAlive() || entity.level() != level
                || !sameExhaustCell(source, exposure)) continue;
            Set<UUID> guarded = EXHAUST_DAMAGE.get();
            guarded.add(entity.getUUID());
            try {
                if (entity.hurt(level.damageSources().inWall(), effect.damage())) damaged++;
            } finally {
                guarded.remove(entity.getUUID());
                if (guarded.isEmpty()) EXHAUST_DAMAGE.remove();
            }
        }
        if (damaged == 0) return 0;
        return DynamicAtmosphereMod.consumeMaterial(
            level, AtmosphereMaterial.EXHAUST, source, effect.cost()) ? damaged : 0;
    }

    private ExhaustGameplay() { }

    public static ExhaustGameplay create() {
        return new ExhaustGameplay();
    }
}
