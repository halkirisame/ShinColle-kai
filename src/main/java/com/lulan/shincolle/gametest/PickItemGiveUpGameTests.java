package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Under NEW the pickup goal gives up on an item it cannot take, but never on an ordinary item that
 * is only under the usual pickup delay.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PickItemGiveUpGameTests {
    private static final Vec3 AT = new Vec3(2.5D, 1D, 3.5D);
    /** The delay of an item a player drops. */
    private static final int ORDINARY_DELAY = 40;

    /** A ship with a high attack speed tries to take more often in ticks but waits just as long for the delay. */
    @GameTest(template = "arena", batch = "isolated_pick_give_up_fast")
    public static void fastShipWaitsOutAnOrdinaryPickupDelay(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = fixture(helper, entities);
                ship.setShipLevel(150, false);
                ship.setMorale(3000);
                ship.getAttrs().setAttrsBonus(ID.AttrsBase.SPD, 3);
                ship.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 1));
                ship.calcShipAttributes(31, false);
                helper.assertTrue(ship.getLevel() == 150 && ship.getAttrs().getAttackSpeed() > 10F / 3F,
                        "Fixture: the naturally recalculated attack speed makes it try every 2 ticks or less: "
                                + ship.getAttrs().getAttackSpeed());
                ItemEntity item = item(helper, entities, ship.position().add(1D, 0D, 0D), ORDINARY_DELAY);
                ShipPickItemGoal goal = new ShipPickItemGoal(ship, 5F);
                helper.assertTrue(goal.canUse(), "Fixture: the item is selectable");
                goal.start();
                boolean gaveUpWhileDelayed = false;
                for (int tick = 0; tick < 60; tick++) {
                    ship.tickCount = 100 + tick;
                    if ((tick & 1) == 0 && goal.canContinueToUse()) goal.tick();
                    if (item.hasPickUpDelay() && !goal.canContinueToUse()) gaveUpWhileDelayed = true;
                    if (item.isAlive()) item.tick();
                }
                helper.assertTrue(!gaveUpWhileDelayed && !item.isAlive(),
                        "An ordinary pickup delay was given up on: speed=" + ship.getAttrs().getAttackSpeed()
                                + " alive=" + item.isAlive());
                helper.succeed();
            }
        }, AT, AT.add(1D, 0D, 0D));
    }

    /** Failed takes of one item are not held against the next one the goal turns to. */
    @GameTest(template = "arena", batch = "isolated_pick_give_up_switch")
    public static void newItemIsNotGivenUpOnForTheFailuresOfThePreviousOne(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = fixture(helper, entities);
                ItemEntity old = item(helper, entities, ship.position().add(1D, 0D, 0D), 32767);
                ShipPickItemGoal goal = new ShipPickItemGoal(ship, 5F);
                goal.start();
                int time = 100;
                // 76 ticks of failed takes: just short of the grace after which the old item is given up on
                for (; time < 176; time += 2) {
                    ship.tickCount = time;
                    goal.tick();
                }
                helper.assertTrue(goal.canContinueToUse() && old.isAlive(),
                        "Fixture: the old item is not given up on yet");
                old.discard();
                ItemEntity fresh = item(helper, entities, ship.position().add(1D, 0D, 0D), ORDINARY_DELAY);
                boolean gaveUpWhileDelayed = false;
                for (int tick = 0; tick < 60; tick++) {
                    ship.tickCount = time + tick;
                    if ((tick & 1) == 0 && goal.canContinueToUse()) goal.tick();
                    if (fresh.hasPickUpDelay() && !goal.canContinueToUse()) gaveUpWhileDelayed = true;
                    if (fresh.isAlive()) fresh.tick();
                }
                helper.assertTrue(!gaveUpWhileDelayed && !fresh.isAlive(),
                        "A new ordinary item inherited the old failures: alive=" + fresh.isAlive());
                helper.succeed();
            }
        }, AT, AT.add(1D, 0D, 0D));
    }

    private static BasicEntityShip fixture(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, entities, AT);
        ship.setNoAi(true);
        ship.setStateFlag(ID.F.PickItem, true);
        ship.tickCount = 100;
        return ship;
    }

    private static ItemEntity item(GameTestHelper helper, GameTestEntities entities, Vec3 at, int delay) {
        ItemEntity item = entities.add(new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND)));
        item.setPickUpDelay(delay);
        item.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "Fixture: the item must be added");
        return item;
    }
}
