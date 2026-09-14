package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Event-driven Dust producers. Movement is sampled from actual position and
 * ground-state transitions; no entity or world scan is introduced.
 *
 * <p>Conservative MVP amounts: walking 1/10 ticks, running 3/5 ticks,
 * jump 8, ordinary landing 6, damaging landing 16 + 2 per damage point
 * (capped at 64), block break 16, and block placement 12.</p>
 */
public final class DustGameplay {
    static final int WALK_AMOUNT = 1;
    static final int RUN_AMOUNT = 3;
    static final int JUMP_AMOUNT = 8;
    static final int LAND_AMOUNT = 6;
    static final int FALL_DAMAGE_BASE_AMOUNT = 16;
    static final int FALL_DAMAGE_PER_POINT = 2;
    static final int MAX_FALL_DAMAGE_AMOUNT = 64;
    static final int BLOCK_BREAK_AMOUNT = 16;
    static final int BLOCK_PLACE_AMOUNT = 12;
    static final int WALK_INTERVAL_TICKS = 10;
    static final int RUN_INTERVAL_TICKS = 5;
    private static final double MIN_HORIZONTAL_DISTANCE_SQUARED = 0.0004;
    private static final double MIN_JUMP_VELOCITY = 0.05;

    private final Map<UUID, MotionState> motion = new HashMap<>();

    record MotionState(double x, double z, boolean onGround, long damagingLandingTick) {
        MotionState withDamagingLanding(long tick) {
            return new MotionState(x, z, onGround, tick);
        }
    }

    enum MotionEmission {
        NONE(0), WALK(WALK_AMOUNT), RUN(RUN_AMOUNT), JUMP(JUMP_AMOUNT), LAND(LAND_AMOUNT);

        private final int amount;

        MotionEmission(int amount) { this.amount = amount; }
        int amount() { return amount; }
    }

    enum MovementMaterial {
        DUST,
        VAPOR
    }

    static MovementMaterial movementMaterial(boolean inWater, boolean snowOrIceSupport) {
        return inWater || snowOrIceSupport ? MovementMaterial.VAPOR : MovementMaterial.DUST;
    }

    static MotionEmission movementEmission(
        MotionState previous,
        double x,
        double z,
        boolean onGround,
        double verticalVelocity,
        boolean sprinting,
        long gameTick,
        int cadenceOffset
    ) {
        if (previous.onGround() && !onGround && verticalVelocity > MIN_JUMP_VELOCITY) {
            return MotionEmission.JUMP;
        }
        if (!previous.onGround() && onGround) {
            return previous.damagingLandingTick() == gameTick ? MotionEmission.NONE : MotionEmission.LAND;
        }
        double dx = x - previous.x();
        double dz = z - previous.z();
        if (!onGround || dx * dx + dz * dz < MIN_HORIZONTAL_DISTANCE_SQUARED) {
            return MotionEmission.NONE;
        }
        int interval = sprinting ? RUN_INTERVAL_TICKS : WALK_INTERVAL_TICKS;
        return Math.floorMod(gameTick + cadenceOffset, interval) == 0
            ? (sprinting ? MotionEmission.RUN : MotionEmission.WALK)
            : MotionEmission.NONE;
    }

    static int damagingLandingAmount(float finalDamage) {
        if (!(finalDamage > 0) || !Float.isFinite(finalDamage)) return 0;
        return Math.min(MAX_FALL_DAMAGE_AMOUNT,
            FALL_DAMAGE_BASE_AMOUNT + (int) Math.ceil(finalDamage * FALL_DAMAGE_PER_POINT));
    }

    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
            || (!(entity instanceof Player) && !(entity instanceof Mob))
            || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        long tick = level.getGameTime();
        MotionState current = new MotionState(entity.getX(), entity.getZ(), entity.onGround(), Long.MIN_VALUE);
        MotionState previous = motion.putIfAbsent(entity.getUUID(), current);
        if (previous == null) return;

        MotionEmission emission = movementEmission(previous, entity.getX(), entity.getZ(), entity.onGround(),
            entity.getDeltaMovement().y(), entity.isSprinting(), tick, entity.getId());
        motion.put(entity.getUUID(), new MotionState(entity.getX(), entity.getZ(), entity.onGround(),
            previous.damagingLandingTick()));
        if (emission != MotionEmission.NONE) {
            if ((emission == MotionEmission.WALK || emission == MotionEmission.RUN)
                && movementMaterial(entity.isInWater(), isSnowOrIce(level.getBlockState(entity.getOnPos())))
                    == MovementMaterial.VAPOR) {
                DynamicAtmosphereMod.emitVapor(level, entity.blockPosition(), emission.amount());
            } else {
                DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.DUST, entity.blockPosition(), emission.amount());
            }
        }
    }

    public void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !event.getSource().is(DamageTypeTags.IS_FALL)) return;
        int amount = damagingLandingAmount(event.getNewDamage());
        if (amount == 0) return;

        long tick = level.getGameTime();
        motion.compute(entity.getUUID(), (ignored, state) -> state == null
            ? new MotionState(entity.getX(), entity.getZ(), entity.onGround(), tick)
            : state.withDamagingLanding(tick));
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.DUST, entity.blockPosition(), amount);
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.DUST, event.getPos(), BLOCK_BREAK_AMOUNT);
        }
    }

    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.DUST, event.getPos(), BLOCK_PLACE_AMOUNT);
        }
    }

    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity) {
            motion.remove(event.getEntity().getUUID());
        }
    }

    private static boolean isSnowOrIce(BlockState state) {
        return state.is(BlockTags.ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
            || state.is(Blocks.POWDER_SNOW);
    }

    private DustGameplay() { }

    public static DustGameplay create() { return new DustGameplay(); }
}
