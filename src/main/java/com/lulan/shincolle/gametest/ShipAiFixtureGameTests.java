package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFleeGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipManualTargetGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.ai.ShipRangeTargetGoal;
import com.lulan.shincolle.ai.ShipRevengeTargetGoal;
import com.lulan.shincolle.ai.ShipWatchClosestGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.ShipHostileWanderGoal;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAiFixtureGameTests {

    private ShipAiFixtureGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rangeTargetCooldownIsRelativePerShip(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip first = friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D), false);
            BasicEntityShip second = friendly(helper, entities, new Vec3(3.5D, 2D, 1.5D), false);
            ShipRangeTargetGoal firstGoal = new ShipRangeTargetGoal(first);
            ShipRangeTargetGoal secondGoal = new ShipRangeTargetGoal(second);

            first.tickCount = 101;
            firstGoal.canUse();
            helper.assertTrue(readInt(ShipRangeTargetGoal.class, firstGoal, "nextScanTick") == 109,
                    "First ship did not schedule its next scan relative to its own first scan.");
            first.tickCount = 108;
            firstGoal.canUse();
            helper.assertTrue(readInt(ShipRangeTargetGoal.class, firstGoal, "nextScanTick") == 109,
                    "Cooldown call changed the first ship's next scan.");
            first.tickCount = 109;
            firstGoal.canUse();
            helper.assertTrue(readInt(ShipRangeTargetGoal.class, firstGoal, "nextScanTick") == 117,
                    "First ship did not rescan after exactly eight entity ticks.");

            second.tickCount = 104;
            secondGoal.canUse();
            helper.assertTrue(readInt(ShipRangeTargetGoal.class, secondGoal, "nextScanTick") == 112,
                    "Second ship inherited a modulo phase instead of its own cooldown.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void invisibleTargetRequiresFlareOrSearchlight(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D), true);
            Zombie target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            helper.assertTrue(target != null, "Failed to create invisible target.");
            move(helper, target, new Vec3(3.5D, 2D, 1.5D));
            target.setInvisible(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Failed to add invisible target.");
            ship.setStateFlag(ID.F.OnSightChase, false);
            TargetHelper.Selector selector = new TargetHelper.Selector(ship);

            ship.setStateMinor(ID.M.LevelFlare, 0);
            ship.setStateMinor(ID.M.LevelSearchlight, 0);
            helper.assertTrue(!selector.test(target), "Invisible target passed without detection equipment.");
            ship.setStateMinor(ID.M.LevelFlare, 1);
            helper.assertTrue(selector.test(target), "Flare did not make the invisible target detectable.");
            ship.setStateMinor(ID.M.LevelFlare, 0);
            ship.setStateMinor(ID.M.LevelSearchlight, 1);
            helper.assertTrue(selector.test(target), "Searchlight did not make the invisible target detectable.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void lightAndHeavyAttacksCanFireOnTheSameTick(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D), true);
            Zombie target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            helper.assertTrue(target != null, "Failed to create attack target.");
            move(helper, target, new Vec3(3.5D, 2D, 1.5D));
            target.setNoAi(true);
            target.setInvulnerable(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Failed to add attack target.");
            ship.setTarget(target);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.AtkType_Heavy, true);
            ship.setStateFlag(ID.F.UseAmmoLight, true);
            ship.setStateFlag(ID.F.UseAmmoHeavy, true);
            ship.setAmmoLight(100_000);
            ship.setAmmoHeavy(100_000);

            ShipRangeAttackGoal goal = new ShipRangeAttackGoal((IShipCannonAttack) ship);
            helper.assertTrue(goal.canUse(), "Dual-ammo fixture could not start its attack goal.");
            goal.start();
            writeInt(ShipRangeAttackGoal.class, goal, "delayLight", 0);
            writeInt(ShipRangeAttackGoal.class, goal, "delayHeavy", 0);
            writeInt(ShipRangeAttackGoal.class, goal, "onSightTime", 1_000);
            int lightBefore = ship.getAmmoLight();
            int heavyBefore = ship.getAmmoHeavy();
            goal.tick();

            helper.assertTrue(ship.getAmmoLight() < lightBefore,
                    "Ready light attack did not consume ammo on the shared tick.");
            helper.assertTrue(ship.getAmmoHeavy() < heavyBefore,
                    "Ready heavy attack did not consume ammo on the shared tick.");
            helper.assertTrue(readInt(ShipRangeAttackGoal.class, goal, "delayLight") > 0,
                    "Light delay was not reset independently.");
            helper.assertTrue(readInt(ShipRangeAttackGoal.class, goal, "delayHeavy") > 0,
                    "Heavy delay was not reset independently.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rangeAttackRejectsSitCraneAndShipMount(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D), true);
            Zombie target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            helper.assertTrue(target != null, "Failed to create blocker target.");
            move(helper, target, new Vec3(3.5D, 2D, 1.5D));
            target.setNoAi(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Failed to add blocker target.");
            ship.setTarget(target);
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.UseAmmoLight, true);
            ship.setAmmoLight(100_000);
            ShipRangeAttackGoal goal = new ShipRangeAttackGoal((IShipCannonAttack) ship);

            ship.setEntitySit(true);
            helper.assertTrue(!goal.canUse(), "Sitting ship could start ranged attack.");
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.CraneState, 1);
            helper.assertTrue(!goal.canUse(), "Crane-active ship could start ranged attack.");
            ship.setStateMinor(ID.M.CraneState, 0);

            BasicEntityMount mount = entities.add(ModEntities.MOUNT_CAWD.get().create(helper.getLevel()));
            helper.assertTrue(mount != null, "Failed to create attack blocker mount.");
            move(helper, mount, new Vec3(1.5D, 2D, 1.5D));
            helper.assertTrue(helper.getLevel().addFreshEntity(mount), "Failed to add attack blocker mount.");
            helper.assertTrue(ship.startRiding(mount, true), "Ship could not mount blocker fixture.");
            helper.assertTrue(!goal.canUse(), "Ship mounted on BasicEntityMount could start ranged attack.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void noFuelClearsMovementAndTargetGoals(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D), true);
            GoalSelector goals = selector(ship, "goalSelector");
            GoalSelector targets = selector(ship, "targetSelector");
            goals.removeAllGoals(goal -> true);
            targets.removeAllGoals(goal -> true);
            goals.addGoal(1, new ShipFleeGoal(ship));
            goals.addGoal(2, new ShipGuardingGoal(ship));
            targets.addGoal(1, new ShipRangeTargetGoal(ship));
            ship.setDeltaMovement(0.2D, 0.3D, 0.4D);
            ship.setStateMinor(ID.M.NumGrudge, 0);
            ship.decrGrudgeNum(0);
            invokeNoArg(ship, "applyPendingFuelAiRefresh");

            helper.assertTrue(ship.getStateFlag(ID.F.NoFuel), "Zero fuel did not set NoFuel.");
            helper.assertTrue(goals.getAvailableGoals().isEmpty(), "NoFuel retained movement goals.");
            helper.assertTrue(targets.getAvailableGoals().isEmpty(), "NoFuel retained target goals.");
            helper.assertTrue(ship.getNavigation().isDone(), "NoFuel retained a navigation path.");
            helper.assertTrue(ship.getDeltaMovement().equals(Vec3.ZERO), "NoFuel retained movement velocity.");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void passiveAiOmitsOnlyAutomaticRangeTarget(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D), true);
            GoalSelector targets = selector(ship, "targetSelector");
            targets.removeAllGoals(goal -> true);
            ship.setStateFlag(ID.F.PassiveAI, false);
            ship.setAITargetList();
            assertHasGoal(helper, targets, ShipManualTargetGoal.class, true);
            assertHasGoal(helper, targets, ShipRevengeTargetGoal.class, true);
            assertHasGoal(helper, targets, ShipRangeTargetGoal.class, true);

            ship.setStateFlag(ID.F.PassiveAI, true);
            assertHasGoal(helper, targets, ShipManualTargetGoal.class, true);
            assertHasGoal(helper, targets, ShipRevengeTargetGoal.class, true);
            assertHasGoal(helper, targets, ShipRangeTargetGoal.class, false);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void wanderChoosesStableDestinationBeforeStart(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip friendly = friendly(helper, entities, new Vec3(2.5D, 2D, 2.5D), true);
            BasicEntityShipHostile hostile = hostile(helper, entities, new Vec3(5.5D, 2D, 2.5D), true);
            friendly.getRandom().setSeed(69L);
            hostile.getRandom().setSeed(69L);
            ShipWanderGoal friendlyGoal = new ShipWanderGoal(friendly, 4, 2, 0.8D);
            ShipHostileWanderGoal hostileGoal = new ShipHostileWanderGoal(hostile, 4, 2, 0.8D);

            helper.assertTrue(awaitCanUse(friendlyGoal), "Friendly Wander never found a seeded stable candidate.");
            assertStableTarget(helper, friendly, friendlyGoal);
            helper.assertTrue(awaitCanUse(hostileGoal), "Hostile Wander never found a seeded stable candidate.");
            assertStableTarget(helper, hostile, hostileGoal);
            helper.succeed();
        }
    }

    @GameTest(template = "arena", batch = "isolated_watch_closest_keeps_legacy_filters_and_rates")
    public static void watchClosestKeepsLegacyFiltersAndRates(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip friendly = friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D), false);
            BasicEntityShipHostile hostile = hostile(helper, entities, new Vec3(8.5D, 2D, 1.5D), false);
            ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                    UUID.fromString("00000000-0000-0000-0000-000000000069"), "shincolle_watch_closest"));
            helper.getLevel().addNewPlayer(player);
            try {
                move(helper, player, new Vec3(3.5D, 2D, 1.5D));
                ShipWatchClosestGoal direct = new ShipWatchClosestGoal(friendly, Player.class, 4F, 1F);

                friendly.setStateFlag(ID.F.NoFuel, true);
                helper.assertTrue(!direct.canUse(), "NoFuel ship could start WatchClosest.");
                friendly.setStateFlag(ID.F.NoFuel, false);
                player.setInvisible(true);
                helper.assertTrue(!direct.canUse(), "Invisible nearest player could start WatchClosest.");
                player.setInvisible(false);
                helper.assertTrue(direct.canUse(), "Visible nearest player could not start WatchClosest.");

                rebuildGoals(friendly);
                rebuildGoals(hostile);
                ShipWatchClosestGoal friendlyRegistered = findGoal(
                        selector(friendly, "goalSelector"), ShipWatchClosestGoal.class);
                ShipWatchClosestGoal hostileRegistered = findGoal(
                        selector(hostile, "goalSelector"), ShipWatchClosestGoal.class);
                helper.assertTrue(readFloat(ShipWatchClosestGoal.class, friendlyRegistered, "chance") == 0.06F,
                        "Friendly WatchClosest rate is not the legacy 0.06.");
                helper.assertTrue(readFloat(ShipWatchClosestGoal.class, hostileRegistered, "chance") == 0.1F,
                        "Hostile WatchClosest rate is not the legacy 0.1.");
                helper.succeed();
            } finally {
                helper.getLevel().removePlayerImmediately(player, RemovalReason.DISCARDED);
            }
        }
    }

    private static BasicEntityShip friendly(GameTestHelper helper, GameTestEntities entities,
                                            Vec3 position, boolean addToLevel) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create friendly ship fixture.");
        move(helper, ship, position);
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.calcShipAttributes(31, false);
        if (addToLevel) {
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add friendly ship fixture.");
        }
        return ship;
    }

    private static BasicEntityShipHostile hostile(GameTestHelper helper, GameTestEntities entities,
                                                   Vec3 position, boolean addToLevel) {
        BasicEntityShipHostile ship = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create hostile ship fixture.");
        move(helper, ship, position);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.calcShipAttributes(31, false);
        if (addToLevel) {
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add hostile ship fixture.");
        }
        return ship;
    }

    private static void rebuildGoals(Mob mob) {
        selector(mob, "goalSelector").removeAllGoals(goal -> true);
        invokeNoArg(mob, "setAIList");
    }

    private static boolean awaitCanUse(Goal goal) {
        for (int attempt = 0; attempt < 2_000; attempt++) {
            if (goal.canUse()) {
                return true;
            }
        }
        return false;
    }

    private static void assertStableTarget(GameTestHelper helper, Mob mob, Goal goal) {
        double x = readDouble(goal.getClass(), goal, "targetX");
        double y = readDouble(goal.getClass(), goal, "targetY");
        double z = readDouble(goal.getClass(), goal, "targetZ");
        BlockPos target = BlockPos.containing(x, y, z);
        helper.assertTrue(mob.getNavigation().isStableDestination(target),
                goal.getClass().getSimpleName() + " retained an unstable destination: " + target);
        goal.start();
    }

    private static void assertHasGoal(GameTestHelper helper, GoalSelector selector,
                                      Class<? extends Goal> type, boolean expected) {
        boolean actual = selector.getAvailableGoals().stream().anyMatch(goal -> type.isInstance(goal.getGoal()));
        helper.assertTrue(actual == expected,
                type.getSimpleName() + " registration mismatch. expected=" + expected + " actual=" + actual);
    }

    private static <T extends Goal> T findGoal(GoalSelector selector, Class<T> type) {
        return selector.getAvailableGoals().stream()
                .map(goal -> goal.getGoal())
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing registered " + type.getSimpleName() + '.'));
    }

    private static GoalSelector selector(Mob mob, String name) {
        Object value = readField(Mob.class, mob, name);
        if (value instanceof GoalSelector selector) {
            return selector;
        }
        throw new AssertionError("Failed to resolve " + name + '.');
    }

    private static void move(GameTestHelper helper, Entity entity, Vec3 relativePosition) {
        Vec3 absolute = helper.absoluteVec(relativePosition);
        entity.moveTo(absolute.x, absolute.y, absolute.z, 0F, 0F);
    }

    private static int readInt(Class<?> owner, Object instance, String name) {
        return (Integer) readField(owner, instance, name);
    }

    private static float readFloat(Class<?> owner, Object instance, String name) {
        return (Float) readField(owner, instance, name);
    }

    private static double readDouble(Class<?> owner, Object instance, String name) {
        return (Double) readField(owner, instance, name);
    }

    private static void writeInt(Class<?> owner, Object instance, String name, int value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(instance, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to write " + owner.getSimpleName() + '.' + name + '.', exception);
        }
    }

    private static Object readField(Class<?> owner, Object instance, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to read " + owner.getSimpleName() + '.' + name + '.', exception);
        }
    }

    private static void invokeNoArg(Object instance, String name) {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                method.invoke(instance);
                return;
            } catch (NoSuchMethodException exception) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Failed to invoke " + name + '.', exception);
            }
        }
        throw new AssertionError("Missing method " + name + '.');
    }
}
