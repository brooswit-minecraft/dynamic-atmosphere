package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * MINECRAFT-368: Phantom attacks never reduce health. Phantoms stay purely
 * atmospheric (see {@link ObsidianPowderGameplay}'s listed-mob emission) with
 * no separate combat threat.
 *
 * <p>Zeroing happens in {@link LivingDamageEvent.Pre}, after armor/potion
 * reductions but before health changes, rather than by removing or zeroing
 * the Phantom's attack-damage attribute: a {@code Pre} listener catches the
 * final post-reduction value regardless of equipment, enchantments, or other
 * mods' modifiers to that attribute, so a zero attack is guaranteed, not just
 * likely.</p>
 */
public final class PhantomAttackGameplay {
    static boolean isPhantomAttack(Entity attacker) {
        return attacker != null && isPhantomType(attacker.getType());
    }

    static boolean isPhantomType(EntityType<?> type) {
        return type == EntityType.PHANTOM;
    }

    /** Pure post-reduction damage adjustment; the only caller is {@link #onLivingDamagePre}. */
    static float zeroedDamage(EntityType<?> attackerType, float originalDamage) {
        return isPhantomType(attackerType) ? 0f : originalDamage;
    }

    /** Seam over {@link LivingDamageEvent.Pre}'s own get/set pair, so production wiring can be
     * exercised with a fake in tests without constructing a real event/{@code DamageContainer}. */
    interface DamageAdjustment {
        float getNewDamage();

        void setNewDamage(float newDamage);
    }

    /** Production damage-adjustment wiring; a null {@code attackerType} (e.g. fall or fire
     * damage, which carry no responsible entity) leaves the damage untouched. */
    static void adjustDamage(EntityType<?> attackerType, DamageAdjustment adjustment) {
        if (attackerType != null) {
            adjustment.setNewDamage(zeroedDamage(attackerType, adjustment.getNewDamage()));
        }
    }

    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        adjustDamage(attacker == null ? null : attacker.getType(), new DamageAdjustment() {
            @Override
            public float getNewDamage() {
                return event.getNewDamage();
            }

            @Override
            public void setNewDamage(float newDamage) {
                event.setNewDamage(newDamage);
            }
        });
    }

    private PhantomAttackGameplay() { }

    public static PhantomAttackGameplay create() {
        return new PhantomAttackGameplay();
    }
}
