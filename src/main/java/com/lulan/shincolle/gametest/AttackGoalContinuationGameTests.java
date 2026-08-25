package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttackGoalContinuationGameTests {

    private static final int INITIAL_SELECTOR_TICKS = 12;
    private static final int TRANSITION_SELECTOR_TICKS = 6;

    private AttackGoalContinuationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyCannonGoalRetainsCooldownAcrossSightLoss(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (!(entity instanceof IShipCannonAttack host)) {
            throw new AssertionError("Failed to create a friendly cannon ship.");
        }
        verifyCannonContinuation(helper, host);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileCannonGoalRetainsCooldownAcrossSightLoss(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        if (!(entity instanceof IShipCannonAttack host)) {
            throw new AssertionError("Failed to create a hostile cannon ship.");
        }
        verifyCannonContinuation(helper, host);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyCarrierGoalContinuesAcrossSightLoss(GameTestHelper helper) {
        Entity entity = ModEntities.CV_WO.get().create(helper.getLevel());
        if (!(entity instanceof IShipAircraftAttack host)) {
            throw new AssertionError("Failed to create a friendly carrier.");
        }
        verifyCarrierContinuation(helper, host);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileCarrierGoalContinuesAcrossSightLoss(GameTestHelper helper) {
        Entity entity = ModEntities.CV_AKAGI_MOB.get().create(helper.getLevel());
        if (!(entity instanceof IShipAircraftAttack host)) {
            throw new AssertionError("Failed to create a hostile carrier.");
        }
        verifyCarrierContinuation(helper, host);
    }

    private static void verifyCannonContinuation(GameTestHelper helper, IShipCannonAttack host) {
        Mob ship = prepareHost(helper, host);
        Zombie target = createTarget(helper);
        ship.setTarget(target);
        host.setAmmoLight(100_000);
        host.setStateFlag(ID.F.AtkType_Light, true);
        host.setStateFlag(ID.F.AtkType_Heavy, false);
        host.setStateFlag(ID.F.UseAmmoLight, true);
        host.setStateFlag(ID.F.UseAmmoHeavy, false);

        CountingRangeAttackGoal goal = new CountingRangeAttackGoal(host);
        GoalSelector selector = installOnlyAttackGoal(ship, goal);
        advanceSelector(ship, selector, INITIAL_SELECTOR_TICKS);

        assertGoalRunning(helper, selector, ShipRangeAttackGoal.class, true);
        helper.assertTrue(goal.getStartCount() == 1,
                "Cannon goal did not start exactly once. starts=" + goal.getStartCount());
        int aimTime = readIntField(ShipRangeAttackGoal.class, goal, "aimTime");
        int delayBeforeLoss = readIntField(ShipRangeAttackGoal.class, goal, "delayLight");
        helper.assertTrue(delayBeforeLoss < aimTime,
                "Cannon cooldown did not decrease before sight loss. delay=" + delayBeforeLoss
                        + " aimTime=" + aimTime);

        setSightBarrier(helper, Blocks.STONE);
        awaitInternalStop(helper, ship, selector, goal, ShipRangeAttackGoal.class);
        int delayAfterLoss = readIntField(ShipRangeAttackGoal.class, goal, "delayLight");
        helper.assertTrue(delayAfterLoss < delayBeforeLoss,
                "Cannon cooldown did not advance while the lost-sight stop ran. before="
                        + delayBeforeLoss + " after=" + delayAfterLoss);
        verifyStillRunningWithoutRestart(helper, ship, selector, goal, ShipRangeAttackGoal.class);

        setSightBarrier(helper, Blocks.AIR);
        awaitTargetReacquisition(helper, ship, selector, goal, ShipRangeAttackGoal.class, target);
        int delayAfterReturn = readIntField(ShipRangeAttackGoal.class, goal, "delayLight");
        helper.assertTrue(delayAfterReturn <= delayAfterLoss && delayAfterReturn < aimTime,
                "Cannon cooldown rewound when sight returned. afterLoss=" + delayAfterLoss
                        + " afterReturn=" + delayAfterReturn + " aimTime=" + aimTime);

        target.discard();
        tickSelector(ship, selector);
        assertGoalRunning(helper, selector, ShipRangeAttackGoal.class, false);
        helper.succeed();
    }

    private static void verifyCarrierContinuation(GameTestHelper helper, IShipAircraftAttack host) {
        Mob ship = prepareHost(helper, host);
        Zombie target = createTarget(helper);
        ship.setTarget(target);
        host.setAmmoLight(100_000);
        host.setNumAircraftLight(6);
        host.setStateFlag(ID.F.AtkType_AirLight, true);
        host.setStateFlag(ID.F.AtkType_AirHeavy, false);
        host.setStateFlag(ID.F.UseAirLight, true);
        host.setStateFlag(ID.F.UseAirHeavy, false);

        CountingCarrierAttackGoal goal = new CountingCarrierAttackGoal(host);
        GoalSelector selector = installOnlyAttackGoal(ship, goal);
        advanceSelector(ship, selector, INITIAL_SELECTOR_TICKS);

        assertGoalRunning(helper, selector, ShipCarrierAttackGoal.class, true);
        helper.assertTrue(goal.getStartCount() == 1,
                "Carrier goal did not start exactly once. starts=" + goal.getStartCount());
        int delayBeforeLoss = readIntField(ShipCarrierAttackGoal.class, goal, "launchDelay");
        helper.assertTrue(delayBeforeLoss < 20,
                "Carrier launch delay did not decrease before sight loss. delay=" + delayBeforeLoss);

        setSightBarrier(helper, Blocks.STONE);
        awaitInternalStop(helper, ship, selector, goal, ShipCarrierAttackGoal.class);
        verifyStillRunningWithoutRestart(helper, ship, selector, goal, ShipCarrierAttackGoal.class);

        setSightBarrier(helper, Blocks.AIR);
        awaitTargetReacquisition(helper, ship, selector, goal, ShipCarrierAttackGoal.class, target);
        int delayAfterReturn = readIntField(ShipCarrierAttackGoal.class, goal, "launchDelay");
        helper.assertTrue(delayAfterReturn <= delayBeforeLoss,
                "Carrier launch delay rewound when sight returned. before=" + delayBeforeLoss
                        + " after=" + delayAfterReturn);

        target.discard();
        tickSelector(ship, selector);
        assertGoalRunning(helper, selector, ShipCarrierAttackGoal.class, false);
        helper.succeed();
    }

    private static Mob prepareHost(GameTestHelper helper, Object host) {
        if (!(host instanceof Mob ship)) {
            throw new AssertionError("Attack host is not a Mob.");
        }
        moveTo(helper, ship, new Vec3(1.5D, 2D, 1.5D));
        if (host instanceof IShipCannonAttack cannon) {
            cannon.setStateFlag(ID.F.OnSightChase, true);
            cannon.setStateFlag(ID.F.NoFuel, false);
            cannon.setStateFlag(ID.F.UseMelee, false);
        } else if (host instanceof IShipAircraftAttack carrier) {
            carrier.setStateFlag(ID.F.OnSightChase, true);
            carrier.setStateFlag(ID.F.NoFuel, false);
            carrier.setStateFlag(ID.F.UseMelee, false);
        }
        return ship;
    }

    private static Zombie createTarget(GameTestHelper helper) {
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        if (target == null) {
            throw new AssertionError("Failed to create an attack target.");
        }
        target.setNoAi(true);
        target.setInvulnerable(true);
        moveTo(helper, target, new Vec3(7.5D, 2D, 1.5D));
        if (!helper.getLevel().addFreshEntity(target)) {
            throw new AssertionError("Failed to add an attack target.");
        }
        return target;
    }

    private static GoalSelector installOnlyAttackGoal(Mob ship, Goal goal) {
        GoalSelector selector = extractSelector(ship, "goalSelector");
        selector.removeAllGoals(existing -> true);
        extractSelector(ship, "targetSelector").removeAllGoals(existing -> true);
        selector.addGoal(1, goal);
        return selector;
    }

    private static void awaitInternalStop(GameTestHelper helper, Mob ship, GoalSelector selector,
                                          Goal goal, Class<? extends Goal> type) {
        for (int tick = 0; tick < TRANSITION_SELECTOR_TICKS; tick++) {
            tickSelector(ship, selector);
            if (readEntityField(type, goal, "target") == null) {
                assertGoalRunning(helper, selector, type, true);
                return;
            }
        }
        helper.assertTrue(false, type.getSimpleName() + " did not run its lost-sight stop.");
    }

    private static void verifyStillRunningWithoutRestart(GameTestHelper helper, Mob ship,
                                                         GoalSelector selector, CountingGoal goal,
                                                         Class<? extends Goal> type) {
        for (int tick = 0; tick < TRANSITION_SELECTOR_TICKS; tick++) {
            tickSelector(ship, selector);
            assertGoalRunning(helper, selector, type, true);
            helper.assertTrue(goal.getStartCount() == 1,
                    type.getSimpleName() + " restarted after its internal stop. starts="
                            + goal.getStartCount());
        }
    }

    private static void awaitTargetReacquisition(GameTestHelper helper, Mob ship, GoalSelector selector,
                                                  CountingGoal goal, Class<? extends Goal> type,
                                                  Entity target) {
        for (int tick = 0; tick < TRANSITION_SELECTOR_TICKS; tick++) {
            tickSelector(ship, selector);
            assertGoalRunning(helper, selector, type, true);
            helper.assertTrue(goal.getStartCount() == 1,
                    type.getSimpleName() + " restarted while reacquiring sight. starts="
                            + goal.getStartCount());
            if (readEntityField(type, (Goal) goal, "target") == target) {
                return;
            }
        }
        helper.assertTrue(false, type.getSimpleName() + " did not reacquire its live target.");
    }

    private static void advanceSelector(Mob ship, GoalSelector selector, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tickSelector(ship, selector);
        }
    }

    private static void tickSelector(Mob ship, GoalSelector selector) {
        ship.tickCount++;
        ship.getSensing().tick();
        selector.tick();
    }

    private static void setSightBarrier(GameTestHelper helper, Block block) {
        for (int y = 0; y <= 7; y++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(4, y, z), block);
            }
        }
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relativePos) {
        Vec3 absolute = helper.absoluteVec(relativePos);
        entity.moveTo(absolute.x, absolute.y, absolute.z, 0F, 0F);
    }

    private static void assertGoalRunning(GameTestHelper helper, GoalSelector selector,
                                          Class<? extends Goal> type, boolean expected) {
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (type.isInstance(wrapped.getGoal())) {
                helper.assertTrue(wrapped.isRunning() == expected,
                        type.getSimpleName() + " running state mismatch. expected=" + expected
                                + " actual=" + wrapped.isRunning());
                return;
            }
        }
        helper.assertTrue(false, "Goal selector has no " + type.getSimpleName() + '.');
    }

    private static int readIntField(Class<?> owner, Object instance, String name) {
        Object value = readField(owner, instance, name);
        if (value instanceof Integer integer) {
            return integer;
        }
        throw new AssertionError(name + " is not an int field.");
    }

    private static Entity readEntityField(Class<?> owner, Object instance, String name) {
        Object value = readField(owner, instance, name);
        if (value == null || value instanceof Entity) {
            return (Entity) value;
        }
        throw new AssertionError(name + " is not an Entity field.");
    }

    private static Object readField(Class<?> owner, Object instance, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect " + owner.getSimpleName() + '.' + name + '.', e);
        }
    }

    private static GoalSelector extractSelector(Mob mob, String fieldName) {
        Object value = readField(Mob.class, mob, fieldName);
        if (value instanceof GoalSelector selector) {
            return selector;
        }
        throw new AssertionError("Failed to resolve " + fieldName + '.');
    }

    private interface CountingGoal {
        int getStartCount();
    }

    private static final class CountingRangeAttackGoal extends ShipRangeAttackGoal implements CountingGoal {
        private int startCount;

        private CountingRangeAttackGoal(IShipCannonAttack host) {
            super(host);
        }

        @Override
        public void start() {
            this.startCount++;
            super.start();
        }

        @Override
        public int getStartCount() {
            return this.startCount;
        }
    }

    private static final class CountingCarrierAttackGoal extends ShipCarrierAttackGoal implements CountingGoal {
        private int startCount;

        private CountingCarrierAttackGoal(IShipAircraftAttack host) {
            super(host);
        }

        @Override
        public void start() {
            this.startCount++;
            super.start();
        }

        @Override
        public int getStartCount() {
            return this.startCount;
        }
    }
}
