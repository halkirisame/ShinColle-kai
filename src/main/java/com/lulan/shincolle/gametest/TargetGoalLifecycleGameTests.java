package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipRangeTargetGoal;
import com.lulan.shincolle.ai.ShipRevengeTargetGoal;
import com.lulan.shincolle.ai.domain.ShipAiCompatibilityRules;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Predicate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TargetGoalLifecycleGameTests {

    private static final int RETENTION_TICKS = 35;

    private TargetGoalLifecycleGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_friendly_revenge_target_survives_multiple_attackers")
    public static void friendlyRevengeTargetSurvivesMultipleAttackers(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            verifyRevengeRetention(helper, entities,
                    createFriendlyShip(helper, entities, new Vec3(1.5D, 2D, 1.5D)));
        }
    }

    @GameTest(template = "arena", batch = "isolated_hostile_revenge_target_survives_multiple_attackers")
    public static void hostileRevengeTargetSurvivesMultipleAttackers(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            verifyRevengeRetention(helper, entities,
                    createHostileShip(helper, entities, new Vec3(1.5D, 2D, 1.5D)));
        }
    }

    @GameTest(template = "arena", batch = "isolated_friendly_range_target_survives_transient_sight_loss")
    public static void friendlyRangeTargetSurvivesTransientSightLoss(GameTestHelper helper) {
        Vec3 shipPosition = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        Vec3 targetPosition = helper.absoluteVec(new Vec3(7.5D, 2D, 1.5D));
        waitForRangeFixtureChunks(helper, shipPosition, targetPosition,
                () -> verifyFriendlyRangeTargetSurvivesTransientSightLoss(helper));
    }

    private static void verifyFriendlyRangeTargetSurvivesTransientSightLoss(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createFriendlyShip(helper, entities, new Vec3(1.5D, 2D, 1.5D));
            Zombie target = createZombie(helper, entities, new Vec3(7.5D, 2D, 1.5D), true);
            verifySightLossRetention(helper, ship, target);
        }
    }

    @GameTest(template = "arena", batch = "isolated_hostile_range_target_survives_transient_sight_loss")
    public static void hostileRangeTargetSurvivesTransientSightLoss(GameTestHelper helper) {
        Vec3 shipPosition = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        Vec3 targetPosition = helper.absoluteVec(new Vec3(7.5D, 2D, 1.5D));
        waitForRangeFixtureChunks(helper, shipPosition, targetPosition,
                () -> verifyHostileRangeTargetSurvivesTransientSightLoss(helper));
    }

    private static void verifyHostileRangeTargetSurvivesTransientSightLoss(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShipHostile ship = createHostileShip(helper, entities, new Vec3(1.5D, 2D, 1.5D));
            BasicEntityShip target = createFriendlyShip(helper, entities, new Vec3(7.5D, 2D, 1.5D));
            target.setNoAi(true);
            if (!helper.getLevel().addFreshEntity(target)) {
                throw new AssertionError("Failed to add a friendly range target.");
            }
            verifySightLossRetention(helper, ship, target);
        }
    }

    private static void waitForRangeFixtureChunks(GameTestHelper helper, Vec3 shipPosition,
                                                  Vec3 targetPosition, Runnable verification) {
        // Spatial queries omit entities in HIDDEN sections even when addFreshEntity succeeds.
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(shipPosition)),
                    "Waiting for the range host's chunk to tick entities");
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(targetPosition)),
                    "Waiting for the range target's chunk to tick entities");
        }).thenExecute(verification).thenSucceed();
    }

    @GameTest(template = "arena", batch = "isolated_friendly_revenge_target_releases_invalid_targets")
    public static void friendlyRevengeTargetReleasesInvalidTargets(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            verifyInvalidTargetRelease(helper, entities,
                    createFriendlyShip(helper, entities, new Vec3(1.5D, 2D, 1.5D)),
                    UUID.fromString("00000000-0000-0000-0000-000000000021"));
        }
    }

    @GameTest(template = "arena", batch = "isolated_hostile_revenge_target_releases_invalid_targets")
    public static void hostileRevengeTargetReleasesInvalidTargets(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            verifyInvalidTargetRelease(helper, entities,
                    createHostileShip(helper, entities, new Vec3(1.5D, 2D, 1.5D)),
                    UUID.fromString("00000000-0000-0000-0000-000000000022"));
        }
    }

    private static void verifyRevengeRetention(GameTestHelper helper, GameTestEntities entities,
                                               IShipAttackBase host) {
        Mob ship = (Mob) host;
        Zombie first = createZombie(helper, entities, new Vec3(3.5D, 2D, 1.5D), false);
        Zombie second = createZombie(helper, entities, new Vec3(1.5D, 2D, 3.5D), false);
        GoalSelector selector = installOnlyTargetGoal(ship, new ShipRevengeTargetGoal(host));

        queueRevengeTarget(host, ship, first, 1);
        selector.tick();
        assertTarget(helper, ship, first, "Revenge goal did not acquire the first attacker.");
        selector.tick();
        assertTarget(helper, ship, first,
                "Revenge goal stopped immediately after consuming its acquisition state.");

        queueRevengeTarget(host, ship, second, 2);
        for (int tick = 0; tick < RETENTION_TICKS; tick++) {
            selector.tick();
        }
        assertTarget(helper, ship, first,
                "A later attacker replaced the retained revenge target before the aim window elapsed.");
        assertGoalRunning(helper, selector, ShipRevengeTargetGoal.class, true);
        helper.succeed();
    }

    private static void verifySightLossRetention(GameTestHelper helper, Mob ship, Entity target) {
        IShipAttackBase host = (IShipAttackBase) ship;
        host.setStateFlag(ID.F.OnSightChase, true);
        // A passing acquisition must not depend on which foreign fixture happens
        // to win the nearest/random candidate selection in the shared world.
        int range = ShipAiCompatibilityRules.targetSearchRange(
                host.getAttrs().getAttackRange(), host.getStateMinor(ID.M.FollowMax));
        var searchBox = ship.getBoundingBox().inflate(
                range, ShipAiCompatibilityRules.targetSearchVerticalInflation(range), range);
        Predicate<Entity> selectorPredicate = ship instanceof BasicEntityShipHostile
                ? new TargetHelper.SelectorForHostile(ship) : new TargetHelper.Selector(ship);
        var candidates = helper.getLevel().getEntitiesOfClass(
                LivingEntity.class, searchBox, selectorPredicate::test);
        helper.assertTrue(candidates.size() == 1 && candidates.get(0) == target,
                "Range fixture contains foreign or missing candidates: "
                        + candidates.stream().map(TargetGoalLifecycleGameTests::describe).toList());
        com.mojang.logging.LogUtils.getLogger().info(
                "Range fixture: host={}, target={}, range={}, searchBox={}",
                ship.position(), target.position(), range, searchBox);
        GoalSelector selector = installOnlyTargetGoal(ship, new ShipRangeTargetGoal(host));

        selector.tick();
        assertTarget(helper, ship, target, "Range target goal did not acquire its visible target.");
        assertGoalRunning(helper, selector, ShipRangeTargetGoal.class, true);

        setSightBarrier(helper, Blocks.STONE);
        ship.getSensing().tick();
        selector.tick();
        assertTarget(helper, ship, target, "Range target was cleared during a transient sight loss.");
        assertGoalRunning(helper, selector, ShipRangeTargetGoal.class, true);

        setSightBarrier(helper, Blocks.AIR);
        ship.getSensing().tick();
        selector.tick();
        assertTarget(helper, ship, target, "Range target was not retained after sight returned.");
    }

    private static void verifyInvalidTargetRelease(GameTestHelper helper, GameTestEntities entities,
                                                    IShipAttackBase host,
                                                    UUID playerUuid) {
        Mob ship = (Mob) host;
        host.getAttrs().getAttrsBuffed()[ID.Attrs.HIT] = 4F;
        GoalSelector selector = installOnlyTargetGoal(ship, new ShipRevengeTargetGoal(host));
        Zombie dead;
        Zombie distant = createZombie(helper, entities, new Vec3(1.5D, 2D, 3.5D), false);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(playerUuid, "shincolle_target_lifecycle"));
        moveTo(helper, player, new Vec3(3.5D, 2D, 3.5D));
        player.getAbilities().invulnerable = false;

        try (GameTestEntities deadEntities = GameTestEntities.open(helper)) {
            dead = createZombie(helper, deadEntities, new Vec3(3.5D, 2D, 1.5D), false);
            queueRevengeTarget(host, ship, dead, 1);
            selector.tick();
            assertTarget(helper, ship, dead, "Revenge goal did not acquire the death-check target.");
        }
        selector.tick();
        assertStoppedAndUntargeted(helper, selector, ship, "Revenge goal retained a dead target.");

        queueRevengeTarget(host, ship, distant, 2);
        selector.tick();
        assertTarget(helper, ship, distant, "Revenge goal did not acquire the range-check target.");
        moveTo(helper, distant, new Vec3(7.5D, 2D, 1.5D));
        selector.tick();
        assertStoppedAndUntargeted(helper, selector, ship,
                "Revenge goal retained an out-of-range target.");

        queueRevengeTarget(host, ship, player, 3);
        selector.tick();
        assertTarget(helper, ship, player, "Revenge goal did not acquire the player target.");
        player.getAbilities().invulnerable = true;
        selector.tick();
        assertStoppedAndUntargeted(helper, selector, ship,
                "Revenge goal retained an invulnerable player target.");
        player.getAbilities().invulnerable = false;
        helper.succeed();
    }

    private static BasicEntityShip createFriendlyShip(GameTestHelper helper, GameTestEntities entities,
                                                      Vec3 relativePos) {
        Entity entity = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create a friendly ship for target lifecycle testing.");
        }
        moveTo(helper, ship, relativePos);
        return ship;
    }

    private static BasicEntityShipHostile createHostileShip(GameTestHelper helper, GameTestEntities entities,
                                                            Vec3 relativePos) {
        Entity entity = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        if (!(entity instanceof BasicEntityShipHostile ship)) {
            throw new AssertionError("Failed to create a hostile ship for target lifecycle testing.");
        }
        moveTo(helper, ship, relativePos);
        return ship;
    }

    private static Zombie createZombie(GameTestHelper helper, GameTestEntities entities,
                                       Vec3 relativePos, boolean addToLevel) {
        Zombie zombie = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
        if (zombie == null) {
            throw new AssertionError("Failed to create a zombie for target lifecycle testing.");
        }
        zombie.setNoAi(true);
        zombie.setInvulnerable(true);
        moveTo(helper, zombie, relativePos);
        if (addToLevel && !helper.getLevel().addFreshEntity(zombie)) {
            throw new AssertionError("Failed to add a zombie for target lifecycle testing.");
        }
        return zombie;
    }

    private static GoalSelector installOnlyTargetGoal(Mob mob, Goal goal) {
        extractGoalSelector(mob).removeAllGoals(existing -> true);
        GoalSelector selector = extractTargetSelector(mob);
        selector.removeAllGoals(existing -> true);
        selector.addGoal(1, goal);
        return selector;
    }

    private static void queueRevengeTarget(IShipAttackBase host, Mob ship, Entity target, int tickCount) {
        ship.tickCount = tickCount;
        host.setEntityRevengeTarget(target);
        host.setEntityRevengeTime();
    }

    private static void setSightBarrier(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
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

    private static void assertStoppedAndUntargeted(GameTestHelper helper, GoalSelector selector,
                                                    Mob ship, String message) {
        assertTarget(helper, ship, null, message);
        assertGoalRunning(helper, selector, ShipRevengeTargetGoal.class, false);
    }

    private static void assertTarget(GameTestHelper helper, Mob ship, Entity expected, String message) {
        helper.assertTrue(ship.getTarget() == expected, message + " expected=" + describe(expected)
                + " actual=" + describe(ship.getTarget()));
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
        helper.assertTrue(false, "Target selector has no " + type.getSimpleName() + '.');
    }

    private static String describe(Entity entity) {
        return entity == null ? "null" : entity.getType().toShortString() + '/' + entity.getId();
    }

    private static GoalSelector extractGoalSelector(Mob mob) {
        return extractSelector(mob, "goalSelector");
    }

    private static GoalSelector extractTargetSelector(Mob mob) {
        return extractSelector(mob, "targetSelector");
    }

    private static GoalSelector extractSelector(Mob mob, String fieldName) {
        try {
            Field field = Mob.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(mob);
            if (value instanceof GoalSelector selector) {
                return selector;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect " + fieldName + " via reflection.", e);
        }
        throw new AssertionError("Failed to resolve " + fieldName + '.');
    }
}
