package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipCombatHost;
import com.lulan.shincolle.ai.ShipFireControlGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.entity.IShipAttackBase;
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
import java.lang.reflect.Method;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttackGoalContinuationGameTests {

    private static final int INITIAL_SELECTOR_TICKS = 12;
    private static final int TRANSITION_SELECTOR_TICKS = 6;

    private AttackGoalContinuationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void combatExecutorsWaitForAuthorityAndHost(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = entities.add(ModEntities.CV_WO.get().create(helper.getLevel()));
            helper.assertTrue(ship instanceof IShipAircraftAttack, "The fixture must support aircraft and cannons");
            prepareHost(helper, ship);
            ship.setAmmoLight(100_000);
            ship.setStateFlag(ID.F.UseAmmoLight, true);
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.UseMelee, true);
            try {
                Method clear = BasicEntityShip.class.getDeclaredMethod("clearAITargetTasks");
                clear.setAccessible(true);
                clear.invoke(ship);
            } catch (ReflectiveOperationException failure) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(
                        "Cannot clear fixture target authority: " + failure);
            }
            Zombie target = createTarget(helper, entities);
            ship.setTarget(target);
            helper.assertTrue(!ship.hasTargetAuthority() && ship.getTarget() == target,
                    "The fixture must have an external target but no target authority");
            helper.assertTrue(!ShipCombatGate.active(ship) && !ShipCombatGate.active(null),
                    "Missing authority must leave the combat gate inactive");
            GoalSelector selector = extractSelector(ship, "goalSelector");
            selector.removeAllGoals(goal -> true);
            Goal[] attacks = {new ShipRangeAttackGoal(ship), new ShipCarrierAttackGoal((IShipAircraftAttack) ship),
                    new ShipAttackOnCollideGoal(ship, 1D), new ShipFireControlGoal(ship)};
            for (int i = 0; i < attacks.length; i++) {
                helper.assertTrue(!attacks[i].canUse(), "An executor accepted an external target without authority");
                selector.addGoal(i, attacks[i]);
            }
            var timing = ship.shipCombatState().timing(ship.tickCount);
            selector.tick();
            helper.assertTrue(selector.getRunningGoals().findAny().isEmpty()
                            && ship.shipCombatState().timing(ship.tickCount).equals(timing)
                            && ship.getStateMinor(ID.M.NumAmmoLight) == 100_000,
                    "Inactive combat fell back to an attack or advanced the timer");
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_CAWD.get().create(helper.getLevel()));
            helper.assertTrue(mount != null && mount.getHost() == null, "The mount fixture must have no host");
            helper.assertTrue(!ShipCombatGate.active(mount) && !new ShipRangeAttackGoal(mount).canUse()
                            && !new ShipFireControlGoal(mount).canUse(),
                    "A hostless mount must not fall back to old attacks");
            ship.setAITargetList();
            helper.assertTrue(ship.hasTargetAuthority() && ShipCombatGate.active(ship),
                    "Registering authority must reopen the gate, without using the external target");
            helper.assertTrue(!new ShipRangeAttackGoal(ship).canUse(),
                    "A newly registered authority must not inherit the external target");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void friendlyCannonGoalRetainsCooldownAcrossSightLoss(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity entity = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            if (!(entity instanceof IShipCannonAttack host)) {
                throw new AssertionError("Failed to create a friendly cannon ship.");
            }
            verifyCannonContinuation(helper, host);
        }
    }

    @GameTest(template = "arena")
    public static void hostileCannonGoalRetainsCooldownAcrossSightLoss(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity entity = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
            if (!(entity instanceof IShipCannonAttack host)) {
                throw new AssertionError("Failed to create a hostile cannon ship.");
            }
            verifyCannonContinuation(helper, host);
        }
    }

    @GameTest(template = "arena")
    public static void friendlyCarrierGoalContinuesAcrossSightLoss(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity entity = entities.add(ModEntities.CV_WO.get().create(helper.getLevel()));
            if (!(entity instanceof IShipAircraftAttack host)) {
                throw new AssertionError("Failed to create a friendly carrier.");
            }
            verifyCarrierContinuation(helper, host);
        }
    }

    @GameTest(template = "arena")
    public static void hostileCarrierGoalContinuesAcrossSightLoss(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity entity = entities.add(ModEntities.CV_AKAGI_MOB.get().create(helper.getLevel()));
            if (!(entity instanceof IShipAircraftAttack host)) {
                throw new AssertionError("Failed to create a hostile carrier.");
            }
            verifyCarrierContinuation(helper, host);
        }
    }

    private static void verifyCannonContinuation(GameTestHelper helper, IShipCannonAttack host) {
        Mob ship = prepareHost(helper, host);
        CountingRangeAttackGoal goal;
        GoalSelector selector;
        try (GameTestEntities targetEntities = GameTestEntities.open(helper)) {
            Zombie target = createTarget(helper, targetEntities);
            giveTarget(ship, target);
            host.setAmmoLight(100_000);
            host.setStateFlag(ID.F.AtkType_Light, true);
            host.setStateFlag(ID.F.AtkType_Heavy, false);
            host.setStateFlag(ID.F.UseAmmoLight, true);
            host.setStateFlag(ID.F.UseAmmoHeavy, false);

            goal = new CountingRangeAttackGoal(host);
            selector = installOnlyAttackGoal(ship, goal);
            advanceSelector(ship, selector, INITIAL_SELECTOR_TICKS);

            assertGoalRunning(helper, selector, ShipRangeAttackGoal.class, true);
            helper.assertTrue(goal.getStartCount() == 1,
                    "Cannon goal did not start exactly once. starts=" + goal.getStartCount());
            // the one timer holds the cannon's ready tick; the goal's restarts and stops never move it earlier
            int readyBeforeLoss = readyTick(ship, WeaponChannel.LIGHT);
            helper.assertTrue(readyBeforeLoss > 0,
                    "The cannon's ready tick was not set before sight loss. ready=" + readyBeforeLoss);

            setSightBarrier(helper, Blocks.STONE);
            awaitInternalStop(helper, ship, selector, goal, ShipRangeAttackGoal.class);
            int readyAfterLoss = readyTick(ship, WeaponChannel.LIGHT);
            helper.assertTrue(readyAfterLoss >= readyBeforeLoss,
                    "The cannon's ready tick moved earlier while the lost-sight stop ran. before="
                            + readyBeforeLoss + " after=" + readyAfterLoss);
            verifyStillRunningWithoutRestart(helper, ship, selector, goal, ShipRangeAttackGoal.class);

            setSightBarrier(helper, Blocks.AIR);
            awaitTargetReacquisition(helper, ship, selector, goal, ShipRangeAttackGoal.class, target);
            int readyAfterReturn = readyTick(ship, WeaponChannel.LIGHT);
            helper.assertTrue(readyAfterReturn >= readyAfterLoss,
                    "The cannon's ready tick moved earlier when sight returned. afterLoss=" + readyAfterLoss
                            + " afterReturn=" + readyAfterReturn);
        }
        tickSelector(ship, selector);
        assertGoalRunning(helper, selector, ShipRangeAttackGoal.class, false);
        helper.succeed();
    }

    private static void verifyCarrierContinuation(GameTestHelper helper, IShipAircraftAttack host) {
        Mob ship = prepareHost(helper, host);
        CountingCarrierAttackGoal goal;
        GoalSelector selector;
        try (GameTestEntities targetEntities = GameTestEntities.open(helper)) {
            Zombie target = createTarget(helper, targetEntities);
            giveTarget(ship, target);
            host.setAmmoLight(100_000);
            host.setNumAircraftLight(6);
            host.setStateFlag(ID.F.AtkType_AirLight, true);
            host.setStateFlag(ID.F.AtkType_AirHeavy, false);
            host.setStateFlag(ID.F.UseAirLight, true);
            host.setStateFlag(ID.F.UseAirHeavy, false);

            goal = new CountingCarrierAttackGoal(host);
            selector = installOnlyAttackGoal(ship, goal);
            advanceSelector(ship, selector, INITIAL_SELECTOR_TICKS);

            assertGoalRunning(helper, selector, ShipCarrierAttackGoal.class, true);
            helper.assertTrue(goal.getStartCount() == 1,
                    "Carrier goal did not start exactly once. starts=" + goal.getStartCount());
            int readyBeforeLoss = readyTick(ship, WeaponChannel.AIR);
            helper.assertTrue(readyBeforeLoss > 0,
                    "The carrier's ready tick was not set before sight loss. ready=" + readyBeforeLoss);

            setSightBarrier(helper, Blocks.STONE);
            awaitInternalStop(helper, ship, selector, goal, ShipCarrierAttackGoal.class);
            verifyStillRunningWithoutRestart(helper, ship, selector, goal, ShipCarrierAttackGoal.class);

            setSightBarrier(helper, Blocks.AIR);
            awaitTargetReacquisition(helper, ship, selector, goal, ShipCarrierAttackGoal.class, target);
            int readyAfterReturn = readyTick(ship, WeaponChannel.AIR);
            helper.assertTrue(readyAfterReturn >= readyBeforeLoss,
                    "The carrier's ready tick moved earlier when sight returned. before=" + readyBeforeLoss
                            + " after=" + readyAfterReturn);
        }
        tickSelector(ship, selector);
        assertGoalRunning(helper, selector, ShipCarrierAttackGoal.class, false);
        helper.succeed();
    }

    private static Mob prepareHost(GameTestHelper helper, Object host) {
        if (!(host instanceof Mob ship)) {
            throw new AssertionError("Attack host is not a Mob.");
        }
        moveTo(helper, ship, new Vec3(1.5D, 2D, 1.5D));
        if (host instanceof BasicEntityShip friendly) {
            friendly.setStateMinor(ID.M.NumGrudge, 100_000);
            friendly.setInvulnerable(true);
        }
        if (host instanceof IShipCannonAttack cannon) {
            cannon.setStateFlag(ID.F.OnSightChase, true);
            cannon.setStateFlag(ID.F.NoFuel, false);
            cannon.setStateFlag(ID.F.UseMelee, false);
        } else if (host instanceof IShipAircraftAttack carrier) {
            carrier.setStateFlag(ID.F.OnSightChase, true);
            carrier.setStateFlag(ID.F.NoFuel, false);
            carrier.setStateFlag(ID.F.UseMelee, false);
        }
        helper.assertTrue(!((IShipAttackBase) host).getStateFlag(ID.F.NoFuel)
                        && (!(host instanceof BasicEntityShip friendly)
                        || friendly.getStateMinor(ID.M.NumGrudge) > 0),
                "The continuation fixture must have fuel");
        return ship;
    }

    private static Zombie createTarget(GameTestHelper helper, GameTestEntities entities) {
        Zombie target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
        if (target == null) {
            throw new AssertionError("Failed to create an attack target.");
        }
        target.setNoAi(true);
        target.setInvulnerable(true);
        target.setPersistenceRequired();
        moveTo(helper, target, new Vec3(7.5D, 2D, 1.5D));
        if (!helper.getLevel().addFreshEntity(target)) {
            throw new AssertionError("Failed to add an attack target.");
        }
        return target;
    }

    /** The target authority locks what the ship is given and the fire control goal holds the one timer. */
    private static GoalSelector installOnlyAttackGoal(Mob ship, Goal goal) {
        GoalSelector selector = extractSelector(ship, "goalSelector");
        selector.removeAllGoals(existing -> true);
        extractSelector(ship, "targetSelector").removeAllGoals(existing -> true);
        if (ship instanceof BasicEntityShip friendly) {
            friendly.setAITargetList();
        } else {
            ((BasicEntityShipHostile) ship).setAITargetList();
        }
        selector.addGoal(10, new ShipFireControlGoal((IShipCannonAttack) ship));
        selector.addGoal(11, goal);
        return selector;
    }

    /** A revenge target the authority takes at its next tick. */
    private static void giveTarget(Mob ship, Entity target) {
        IShipAttackBase base = (IShipAttackBase) ship;
        ship.tickCount = 10;
        base.setEntityRevengeTarget(target);
        base.setEntityRevengeTime();
    }

    private static int readyTick(Mob ship, WeaponChannel channel) {
        return ((ShipCombatHost) ship).shipCombatState().timing(ship.tickCount).readyAt(channel);
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
        extractSelector(ship, "targetSelector").tick();
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
