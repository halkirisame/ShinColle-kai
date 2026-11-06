package com.lulan.shincolle.ai.registry;

import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.init.ModShipBehaviors;
import com.lulan.shincolle.reference.ID;

import java.util.EnumSet;
import java.util.List;

/** Builds transient goals and weapons from the server definition, never from GUI flags. */
public final class GenericShipBehaviors {
    private GenericShipBehaviors() { }

    public static void install(GenericShipHost host, boolean hostile) {
        if (host.behaviorEntity().level().isClientSide() || host.definitionState() == null
                || host.definitionState().dormant()) return;
        var definition = host.definitionState().definition();
        if (definition == null) return;
        var behavior = definition.behavior();
        ShipAiType ai = behavior.ai().map(id -> ModShipBehaviors.AI_REGISTRY.get().getValue(id))
                .orElseGet(ModShipBehaviors.STANDARD);
        List<ShipAttackType> attacks = behavior.attacks().isEmpty()
                ? hostile ? List.of(ModShipBehaviors.LIGHT_CANNON.get(), ModShipBehaviors.HEAVY_CANNON.get())
                        : List.of(ModShipBehaviors.LIGHT_CANNON.get(), ModShipBehaviors.HEAVY_CANNON.get(),
                                ModShipBehaviors.MELEE.get())
                : behavior.attacks().stream().map(id -> ModShipBehaviors.ATTACK_REGISTRY.get().getValue(id)).toList();
        ai.install(host);
        EnumSet<WeaponChannel> weapons = EnumSet.noneOf(WeaponChannel.class);
        for (ShipAttackType attack : attacks) {
            attack.install(host);
            weapons.addAll(attack.channels());
        }
        if (!weapons.contains(WeaponChannel.MELEE)) removeMeleeGoal(host);
        ShipCombatGate.installGenericWeapons(host, weapons);
        if (hostile) {
            ((IShipAttackBase) host.behaviorEntity()).setStateFlag(ID.F.UseMelee, weapons.contains(WeaponChannel.MELEE));
        }
    }

    /** The GUI can enable melee but cannot add an attack absent from the installed definition. */
    public static void removeMeleeGoal(GenericShipHost host) {
        host.behaviorGoals().getAvailableGoals().stream()
                .map(goal -> goal.getGoal()).filter(ShipAttackOnCollideGoal.class::isInstance).toList()
                .forEach(host.behaviorGoals()::removeGoal);
    }
}
