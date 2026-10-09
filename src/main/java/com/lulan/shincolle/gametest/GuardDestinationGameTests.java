package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ConfigHandler;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardDestinationGameTests {

    private GuardDestinationGameTests() {
    }

    @GameTest(template = "arena")
    public static void newShipStartsWithoutPhantomGuardDestination(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities, "new-ship guard initialization");
            ship.setStateMinor(ID.M.NumGrudge, 100);

            helper.assertTrue(ship.getStateFlag(ID.F.CanFollow),
                    "A new friendly ship must start in follow mode");
            helper.assertTrue(!ship.hasGuardDestination(),
                    "Cleared legacy coordinates must not become a guard destination");
            helper.assertTrue(!new ShipGuardingGoal(ship).canUse(),
                    "Guarding goal must not start without an explicit command");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void legacyClearedGuardStateRestoresFollowMode(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = createShip(helper, entities, "legacy guard source");
            source.setStateFlag(ID.F.CanFollow, false);
            CompoundTag saved = new CompoundTag();
            source.addAdditionalSaveData(saved);
            saved.remove("GuardDimension");
            saved.remove("GuardEntityUUID");

            BasicEntityShip restored = createShip(helper, entities, "legacy guard restore");
            restored.readAdditionalSaveData(saved);

            helper.assertTrue(restored.getStateFlag(ID.F.CanFollow),
                    "A legacy cleared guard state must be repaired to follow mode");
            helper.assertTrue(!restored.hasGuardDestination(),
                    "A repaired legacy state must not expose a guard destination");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void unassignedGuardRepairLeavesDimensionedAndEntityTuples(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            // cleared coordinates that carry a saved dimension are an order, not the cleared tuple
            BasicEntityShip dimensioned = createShip(helper, entities, "dimensioned cleared-coordinate source");
            dimensioned.setGuardedPos(-1, -1, -1, helper.getLevel().dimension(), 0);
            dimensioned.setStateFlag(ID.F.CanFollow, false);
            CompoundTag saved = new CompoundTag();
            dimensioned.addAdditionalSaveData(saved);
            helper.assertTrue(saved.contains("GuardDimension") && !saved.hasUUID("GuardEntityUUID"),
                    "Fixture must save a dimension and no guarded entity");
            BasicEntityShip restored = createShip(helper, entities, "dimensioned cleared-coordinate restore");
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(!restored.getStateFlag(ID.F.CanFollow) && restored.getGuardedPos(1) == -1
                            && restored.getGuardedPos(4) == 0,
                    "A cleared-coordinate order with a saved dimension must not be repaired to follow mode");

            // an entity guard without its entity is left for the guard goal, not repaired on load
            BasicEntityShip entityGuard = createShip(helper, entities, "entity guard without identity source");
            entityGuard.setGuardedPos(-1, -1, -1, 0, 2);
            entityGuard.setStateFlag(ID.F.CanFollow, false);
            CompoundTag savedGuard = new CompoundTag();
            entityGuard.addAdditionalSaveData(savedGuard);
            helper.assertTrue(!savedGuard.contains("GuardDimension") && !savedGuard.hasUUID("GuardEntityUUID"),
                    "Fixture must save neither a dimension nor a guarded entity");
            BasicEntityShip restoredGuard = createShip(helper, entities, "entity guard without identity restore");
            restoredGuard.readAdditionalSaveData(savedGuard);
            helper.assertTrue(!restoredGuard.getStateFlag(ID.F.CanFollow) && restoredGuard.getGuardedPos(4) == 2,
                    "An entity guard without its entity must not be repaired to follow mode on load");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void negativeYGuardDestinationSurvivesNbtRoundTrip(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = createShip(helper, entities, "negative-Y guard source");
            BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 1));
            if (destination.getY() >= 0) {
                throw new AssertionError("GameTest arena no longer exercises a negative Y: " + destination);
            }
            source.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                    helper.getLevel().dimension(), 1);
            source.setStateFlag(ID.F.CanFollow, false);
            CompoundTag saved = new CompoundTag();
            source.addAdditionalSaveData(saved);

            BasicEntityShip restored = createShip(helper, entities, "negative-Y guard restore");
            restored.readAdditionalSaveData(saved);

            helper.assertTrue(!restored.getStateFlag(ID.F.CanFollow),
                    "An active negative-Y guard must not be repaired to follow mode");
            helper.assertTrue(restored.hasGuardDestination(),
                    "An active negative-Y guard must survive NBT");
            helper.assertTrue(restored.getGuardedPos(0) == destination.getX()
                            && restored.getGuardedPos(1) == destination.getY()
                            && restored.getGuardedPos(2) == destination.getZ(),
                    "Guard coordinates changed during NBT round-trip");
            helper.assertTrue(restored.isGuardedInCurrentDimension(),
                    "Guard dimension changed during NBT round-trip");

            source.setReleaseGuardOnArrival(true);
            source.addAdditionalSaveData(saved);
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(restored.shouldReleaseGuardOnArrival(),
                    "One-shot move became a persistent guard after reload");
            saved.remove("ReleaseGuardOnArrival");
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(!restored.shouldReleaseGuardOnArrival(),
                    "Legacy save unexpectedly became a one-shot move");
            source.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                    helper.getLevel().dimension(), 1);
            helper.assertTrue(!source.shouldReleaseGuardOnArrival(),
                    "A replacement guard inherited the previous move completion policy");

            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void negativeYBlockDestinationRemainsActive(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities, "negative-Y guard test");

            Vec3 shipPos = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
            BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 1));
            if (destination.getY() >= 0) {
                throw new AssertionError("GameTest arena no longer exercises a negative Y: " + destination);
            }

            ship.moveTo(shipPos.x, shipPos.y, shipPos.z, 0F, 0F);
            helper.getLevel().addFreshEntity(ship);
            ship.setStateMinor(ID.M.NumGrudge, 100);
            ship.setStateMinor(ID.M.FormatType, 1);
            ship.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                    helper.getLevel().dimension(), 1);
            ship.setStateFlag(ID.F.CanFollow, false);

            var owner = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "negative_guard_owner"));
            try (var team = new FormationGameTestTeam(owner, ship, entities)) {
            ShipGuardingGoal goal = new ShipGuardingGoal(ship);
            if (!goal.canUse()) {
                throw new AssertionError("Guarding goal rejected a valid negative-Y block destination.");
            }
            if (!ship.hasGuardDestination() || ship.getGuardedPos(1) != destination.getY()
                    || ship.getStateFlag(ID.F.CanFollow)) {
                throw new AssertionError("Negative-Y guard destination was cleared or marked inactive.");
            }
            if (!ship.isGuardedInCurrentDimension()) {
                throw new AssertionError("Guard destination lost its ResourceLocation dimension.");
            }

            helper.succeed();
            }
        }
        });
    }

    @GameTest(template = "arena")
    public static void moveCompletionRejectsMissingPartialAndStalePaths(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities, "move completion path safety");
            ship.setNoAi(true);
            ship.setStateMinor(ID.M.NumGrudge, 100);
            BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 1));
            ship.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                    helper.getLevel().dimension(), 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setReleaseGuardOnArrival(true);
            ShipGuardingGoal goal = new ShipGuardingGoal(ship);
            ship.getNavigation().stop();
            goal.canUse();
            helper.assertTrue(ship.hasGuardDestination(), "Missing path released a move");

            Path partial = new Path(List.of(new Node(destination.getX() - 3, destination.getY(),
                    destination.getZ())), destination, false);
            ship.getNavigation().moveTo(partial, 1D);
            partial.setNextNodeIndex(partial.getNodeCount());
            goal.canUse();
            helper.assertTrue(ship.hasGuardDestination(), "Partial path released an unreachable move");

            BlockPos previous = destination.offset(-3, 0, 0);
            Path stale = new Path(List.of(new Node(previous.getX(), previous.getY(), previous.getZ())),
                    previous, true);
            ship.getNavigation().moveTo(stale, 1D);
            stale.setNextNodeIndex(stale.getNodeCount());
            goal.canUse();
            helper.assertTrue(ship.hasGuardDestination(), "Previous destination's path released a new move");
            helper.succeed();
        }
    }

    private static BasicEntityShip createShip(GameTestHelper helper, GameTestEntities entities, String purpose) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        if (ship == null) {
            throw new AssertionError("Failed to create ship for " + purpose + ".");
        }
        ship.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)));
        helper.getLevel().addFreshEntity(ship);
        return ship;
    }

    @GameTest(template = "arena")
    public static void mountedAndGroundedCompletedMovesBothRelease(GameTestHelper helper) {
        try (ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip grounded = createShip(helper, entities, "grounded completion control");
            BasicEntityShip mounted = createShip(helper, entities, "mounted completion control");
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            mount.moveTo(mounted.position());
            helper.getLevel().addFreshEntity(mount);
            mount.setHost(mounted);
            helper.assertTrue(mounted.startRiding(mount, true), "Fixture must mount the second ship");
            BlockPos destination = helper.absolutePos(new BlockPos(1, 2, 1));
            for (BasicEntityShip ship : List.of(grounded, mounted)) {
                prepareCompletedMove(helper, ship, destination);
            }
            installCompletedPath(mount.getNavigation(), destination);
            helper.assertTrue(!grounded.getIsRiding() && mounted.getIsRiding(),
                    "Only the second control must be riding");
            new ShipGuardingGoal(grounded).canUse();
            helper.assertTrue(!grounded.hasGuardDestination() && grounded.getStateFlag(ID.F.CanFollow),
                    "Grounded control must release before checking the mounted ship");
            new ShipGuardingGoal(mounted).canUse();
            helper.assertTrue(!mounted.hasGuardDestination() && mounted.getStateFlag(ID.F.CanFollow),
                    "Grounded control released; mounted ship retained the completed move");
            helper.assertTrue(mounted.getVehicle() == mount && mount.getHost() == mounted,
                    "Completion must preserve the mount and its host");
            helper.assertTrue(!new ShipFollowOwnerGoal(mounted).canUse(),
                    "Mounted ship must not start its own walking follow goal");
            ShipGuardingGoal goal = new ShipGuardingGoal(mounted);
            for (int check = 0; check < 20; check++) {
                helper.assertTrue(!goal.canUse() && !goal.canContinueToUse(),
                        "Released mounted move must not restart guarding");
            }
            helper.assertTrue(!mounted.shouldReleaseGuardOnArrival() && mount.getNavigation().isDone(),
                    "Completion must clear the one-shot command and finish navigation");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void mountedCompletionUsesVehiclePathAtEveryGoalEntry(GameTestHelper helper) {
        try (ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities, "vehicle navigation completion");
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            mount.moveTo(ship.position());
            helper.getLevel().addFreshEntity(mount);
            mount.setHost(ship);
            helper.assertTrue(ship.startRiding(mount, true), "Fixture must mount ship");
            BlockPos destination = helper.absolutePos(new BlockPos(1, 2, 1));
            for (int entry = 0; entry < 3; entry++) {
                prepareCompletedMove(helper, ship, destination);
                mount.getNavigation().stop();
                ShipGuardingGoal goal = new ShipGuardingGoal(ship);
                goal.canUse();
                helper.assertTrue(ship.hasGuardDestination(),
                        "Stale ship path must not stand in for the vehicle path");
                ship.getNavigation().stop();
                installCompletedPath(mount.getNavigation(), destination);
                if (entry == 0) {
                    goal.canUse();
                } else if (entry == 1) {
                    goal.canContinueToUse();
                } else {
                    goal.tick();
                }
                helper.assertTrue(!ship.hasGuardDestination() && ship.getStateFlag(ID.F.CanFollow),
                        "Vehicle completion was not released at goal entry " + entry);
                helper.assertTrue(ship.getVehicle() == mount, "Completion dismounted the ship");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void completedMoveRespectsOtherGuardBlockers(GameTestHelper helper) {
        try (ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities, "guard completion blockers");
            BlockPos destination = helper.absolutePos(new BlockPos(1, 2, 1));
            for (int blocker = 0; blocker < 4; blocker++) {
                for (int entry = 0; entry < 3; entry++) {
                    ship.setEntitySit(false);
                    ship.setStateTimer(ID.T.CrandDelay, 0);
                    ship.setStateMinor(ID.M.CraneState, 0);
                    prepareCompletedMove(helper, ship, destination);
                    if (blocker == 0) ship.setEntitySit(true);
                    if (blocker == 1) ship.setStateFlag(ID.F.CanFollow, true);
                    if (blocker == 2) ship.setStateMinor(ID.M.CraneState, 1);
                    if (blocker == 3) ship.setStateMinor(ID.M.NumGrudge, 0);
                    installCompletedPath(ship.getNavigation(), destination);
                    helper.assertTrue(blocker != 2 || ship.getStateMinor(ID.M.CraneState) == 1,
                            "Crane fixture must not be rejected by the reactivation cooldown");
                    ShipGuardingGoal goal = new ShipGuardingGoal(ship);
                    if (entry == 0) {
                        helper.assertTrue(!goal.canUse(), "Blocked goal started");
                    } else if (entry == 1) {
                        helper.assertTrue(!goal.canContinueToUse(), "Blocked goal continued");
                    } else {
                        goal.tick();
                    }
                    helper.assertTrue(ship.getGuardedPos(0) == destination.getX(),
                            "Blocked command was cleared: blocker=" + blocker + " entry=" + entry);
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void newCompletedMoveRetainsItsOrderWhenMovementIsBlocked(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities, "new guard completion blockers");
            BlockPos destination = helper.absolutePos(new BlockPos(1, 2, 1));
            for (int blocker = 0; blocker < 3; blocker++) {
                for (int entry = 0; entry < 3; entry++) {
                    ship.setEntitySit(false);
                    ship.setStateTimer(ID.T.CrandDelay, 0);
                    ship.setStateMinor(ID.M.CraneState, 0);
                    ship.setStateFlag(ID.F.NoFuel, false);
                    prepareCompletedMove(helper, ship, destination);
                    if (blocker == 0) ship.setEntitySit(true);
                    if (blocker == 1) {
                        ship.setStateMinor(ID.M.NumGrudge, 0);
                        ship.setStateFlag(ID.F.NoFuel, true);
                    }
                    if (blocker == 2) ship.setStateMinor(ID.M.CraneState, 1);
                    helper.assertTrue(ship.hasGuardDestination() && ship.shouldReleaseGuardOnArrival(),
                            "Blocked fixture must retain a pending one-shot order");
                    helper.assertTrue(blocker != 2 || ship.getStateMinor(ID.M.CraneState) == 1,
                            "Crane fixture was rejected by the reactivation cooldown");
                    ShipGuardingGoal goal = new ShipGuardingGoal(ship);
                    if (entry == 0) {
                        helper.assertTrue(!goal.canUse(), "Blocked NEW goal started");
                    } else if (entry == 1) {
                        helper.assertTrue(!goal.canContinueToUse(), "Blocked NEW goal continued");
                    } else {
                        goal.tick();
                    }
                    helper.assertTrue(ship.hasGuardDestination() && ship.shouldReleaseGuardOnArrival(),
                            "Blocked NEW command was released: blocker=" + blocker + " entry=" + entry);
                }
            }
            helper.succeed();
        }
    }

    private static void prepareCompletedMove(GameTestHelper helper, BasicEntityShip ship, BlockPos destination) {
        ship.setNoAi(true);
        ship.calcShipAttributes(31, false);
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                helper.getLevel().dimension(), 1);
        ship.setStateFlag(ID.F.CanFollow, false);
        ship.setReleaseGuardOnArrival(true);
        installCompletedPath(ship.getNavigation(), destination);
    }

    private static void installCompletedPath(net.minecraft.world.entity.ai.navigation.PathNavigation navigation,
                                             BlockPos destination) {
        Path path = new Path(List.of(new Node(destination.getX(), destination.getY(), destination.getZ())),
                destination, true);
        navigation.stop();
        navigation.moveTo(path, 1D);
        path.setNextNodeIndex(path.getNodeCount());
    }
}
