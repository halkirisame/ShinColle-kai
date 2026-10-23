package com.lulan.shincolle.handler;

import com.lulan.shincolle.entity.IShipOwner;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps other mods' spell damage from hitting allied ships.
 * <p>
 * Spell mods apply area damage to every living entity near the caster. A ship owned by the
 * caster, or by a player the caster is allied with, is a friendly, so that damage is cancelled
 * before the ship (and the revenge propagation) ever sees it. Identification is by the damage
 * type namespace only, so no optional-mod class is referenced.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AllySpellDamageGuard {

    private static final String SPELL_NAMESPACE = "irons_spellbooks";

    private AllySpellDamageGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (shouldBlock(event.getSource(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * True when {@code source} is spell damage that must not reach {@code target}. A ship
     * calls this at the top of its own hurt handling, before it sets a revenge target or
     * stands up, because the attack event only fires after those side effects.
     */
    public static boolean shouldBlock(DamageSource source, Entity target) {
        return !target.level().isClientSide() && isSpellDamage(source)
                && isAllied(source.getEntity(), target);
    }

    /**
     * True when {@code attacker} (or the owner of a summon) is the owner of the ship
     * {@code target}, or an ally of that owner. Server side only.
     */
    public static boolean isAllied(Entity attacker, Entity target) {
        if (attacker == null || !(target instanceof IShipOwner ship)) {
            return false;
        }

        int shipUID = ship.getPlayerUID();
        if (shipUID <= 0) {
            return false;
        }

        Entity source = attacker;
        if (!(source instanceof IShipOwner) && source instanceof OwnableEntity ownable
                && ownable.getOwner() != null) {
            source = ownable.getOwner();
        }

        int attackerUID = TeamHelper.getPlayerUID(source);
        if (attackerUID <= 0) {
            return false;
        }

        // an alliance is stored on one side only, so either owner listing the other counts
        return attackerUID == shipUID || TeamHelper.checkIsAlly(shipUID, attackerUID)
                || TeamHelper.checkIsAlly(attackerUID, shipUID);
    }

    public static boolean isSpellDamage(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> SPELL_NAMESPACE.equals(key.location().getNamespace()))
                .orElse(false);
    }
}
