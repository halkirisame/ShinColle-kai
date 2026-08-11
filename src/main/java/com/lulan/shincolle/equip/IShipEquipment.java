package com.lulan.shincolle.equip;

import com.lulan.shincolle.reference.unitclass.Attrs;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Implement this on an Item to let it be worn in a ship-girl's Curios
 * equipment slot and affect her the way ShinColle's own equipment does.
 *
 * ShinColle's own equipment stats live in
 * {@code Values.EquipAttrsMain}/{@code EquipAttrsMisc}, which are
 * {@code Collections.unmodifiableMap} statics, and a ShinColle
 * {@code BasicEquip} cannot be subclassed alongside another mod's item base
 * class (e.g. Tinkers' Construct's {@code ModifiableItem}). This interface is
 * the extension point instead: any item implementing it and carrying the
 * {@code curios:ship_equip} tag can be worn in the slot ShinColle appends to
 * every ship's inventory, with no dependency on ShinColle's internals beyond
 * this interface.
 *
 * <p>All methods are called server-side only.
 */
public interface IShipEquipment {

    /**
     * Stats this equipment contributes, as a ShinColle attribute array.
     *
     * <p>Return a fresh array of length {@link Attrs#AttrsLength}, indexed by
     * {@code com.lulan.shincolle.reference.ID.Attrs} constants - so index
     * {@code ID.Attrs.ATK_L} is light attack, {@code ID.Attrs.AA} is anti-air,
     * and so on. Values are added on top of ShinColle's own equipment totals
     * and then scaled by the same per-stat config multipliers.
     *
     * <p>Note the combined movement figure from these slots is floored, so a
     * heavy loadout cannot immobilise a ship outright.
     *
     * @param stack the equipped stack
     * @return contributed stats; an all-zero array contributes nothing
     */
    float[] computeShipAttrs(ItemStack stack);

    /**
     * Optional: effects applied to whatever the ship hits.
     *
     * <p>Entries go into ShinColle's own {@code AttackEffectMap}, keyed by
     * numeric MobEffect id, with values {@code {amplifier, duration ticks,
     * percent chance}}. Because that map only carries MobEffects, behaviour
     * that is not a potion effect has to be approximated - or handled in
     * {@link #onShipHit} instead.
     *
     * @param effectMap the ship's effect map, already cleared for this pass
     * @param stack     the equipped stack
     */
    default void applyAttackEffects(Map<Integer, int[]> effectMap, ItemStack stack) {
    }

    /**
     * Optional: called after the ship lands an attack, once per equipped piece.
     *
     * <p>This is the hook for behaviour that cannot be expressed as a stat or
     * a potion effect - for example running another mod's own on-hit logic
     * with the ship standing in as the attacker.
     *
     * <p>{@code ship} is always the entity wearing this equipment - a
     * friendly {@code BasicEntityShip} or a hostile {@code
     * BasicEntityShipHostile} - even when the actual hit came from one of its
     * carrier aircraft (aircraft attacks copy the host's stats and count as
     * the ship's own attack; see {@code BasicEntityAirplane}). Stand this in
     * as the attacker for another mod's own hooks (e.g. Tinkers' Construct
     * modifier hit hooks).
     *
     * @param ship        the ship wearing this equipment
     * @param target      what it hit
     * @param damageDealt damage dealt, for effects that scale with it
     * @param stack       the equipped stack
     */
    default void onShipHit(LivingEntity ship, Entity target, float damageDealt, ItemStack stack) {
    }
}
