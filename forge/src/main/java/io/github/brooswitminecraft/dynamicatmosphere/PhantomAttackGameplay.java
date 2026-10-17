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

    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null) {
            event.setNewDamage(zeroedDamage(attacker.getType(), event.getNewDamage()));
        }
    }

    private PhantomAttackGameplay() { }

    public static PhantomAttackGameplay create() {
        return new PhantomAttackGameplay();
    }
}
