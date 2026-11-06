package com.lulan.shincolle.ai.registry;

import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipCombatHost;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.shipdata.DefinitionBackedShip;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/** Internal bridge implemented only by definition-backed ship entities. */
public interface GenericShipHost extends DefinitionBackedShip, ShipCombatHost {
    Mob behaviorEntity();

    GoalSelector behaviorGoals();

    void installStandardAi();

    default void installCannonAttack() {
        if (behaviorGoals().getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof ShipRangeAttackGoal)) {
            behaviorGoals().addGoal(11, new ShipRangeAttackGoal((IShipCannonAttack) behaviorEntity()));
        }
    }

    default void installMeleeAttack() {
        if (behaviorGoals().getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof ShipAttackOnCollideGoal)) {
            behaviorGoals().addGoal(15, new ShipAttackOnCollideGoal((IShipAttackBase) behaviorEntity(), 1.0D));
        }
    }
}
