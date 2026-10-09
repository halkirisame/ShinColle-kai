package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipSitGoal;
import com.lulan.shincolle.ai.command.ShipCommandDispatcher;
import com.lulan.shincolle.ai.domain.command.CommandStateChange;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandKind;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.command.ShipTransitionReason;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCommandStateGameTests {
    private ShipCommandStateGameTests() { }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        ship.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)));
        helper.getLevel().addFreshEntity(ship);
        ship.setNoAi(true);
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.enableCommandProjectionCheckForTest();
        return ship;
    }

    private static void check(BasicEntityShip ship) {
        ship.assertCommandProjection();
    }

    private static FakePlayer owner(GameTestHelper helper, BasicEntityShip ship) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.randomUUID(), "command_state_owner"));
        int uid = Math.floorMod(ship.getUUID().hashCode(), 100000) + 21000;
        player.getCapability(CapaTeitokuProvider.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Missing owner capability")).setPlayerUID(uid);
        ship.setOwnerUUID(player.getUUID());
        ship.setPlayerUID(uid);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return player;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rightClickTogglesSittingForShipAndPassenger(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip host = ship(helper, entities);
            BasicEntityShip passenger = ship(helper, entities);
            helper.assertTrue(passenger.startRiding(host, true), "Fixture needs a ship passenger");
            FakePlayer player = owner(helper, host);
            host.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(host.getCommandState().sitting() && passenger.getCommandState().sitting(),
                    "Right click should seat the host and its passenger");
            check(host);
            check(passenger);
            host.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!host.getCommandState().sitting() && !passenger.getCommandState().sitting(),
                    "A second click should stand both ships");
            check(host);
            check(passenger);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ownerDamageStandsSittingShip(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            FakePlayer player = owner(helper, ship);
            ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            ship.hurt(player.damageSources().playerAttack(player), 1F);
            helper.assertTrue(!ship.getCommandState().sitting(), "Owner damage should make the ship stand");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void completedMoveUpdatesCommandState(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 1));
            ship.setGuardedPos(pos.getX(), pos.getY(), pos.getZ(), helper.getLevel().dimension(), 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setReleaseGuardOnArrival(true);
            ship.getCommandState();
            Path path = new Path(List.of(new Node(pos.getX(), pos.getY(), pos.getZ())), pos, true);
            ship.getNavigation().moveTo(path, 1D);
            path.setNextNodeIndex(path.getNodeCount());
            new ShipGuardingGoal(ship).canUse();
            check(ship);
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                    "Arrival should finish the movement order");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deadGuardTargetUpdatesCommandState(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            cow.moveTo(ship.position());
            helper.getLevel().addFreshEntity(cow);
            ship.setGuardedEntity(cow);
            ship.setGuardedPos(-1, -1, -1, helper.getLevel().dimension(), 2);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.getCommandState();
            cow.discard();
            new ShipGuardingGoal(ship).canUse();
            check(ship);
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                    "A dead guarded entity should end the movement order");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void otherDimensionUpdatesCommandState(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            ship.setGuardedPos(2, 2, 2, Level.NETHER, 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.getCommandState();
            new ShipGuardingGoal(ship).canUse();
            check(ship);
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                    "A guard in another dimension should end the movement order");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void invalidManualTargetUpdatesCommandState(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            cow.moveTo(ship.position());
            helper.getLevel().addFreshEntity(cow);
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(cow))));
            cow.discard();
            TargetHelper.updateTarget(ship);
            check(ship);
            helper.assertTrue(ship.getCommandState().manualAttack().isEmpty(),
                    "Invalid manual attack should be cleared");
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void waypointAdvancesCommandWithWaypointIssuer(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BlockPos first = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos next = helper.absolutePos(new BlockPos(7, 2, 2));
            helper.getLevel().setBlock(first, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
            BlockEntity block = helper.getLevel().getBlockEntity(first);
            if (!(block instanceof TileEntityWaypoint waypoint)) throw new AssertionError("Missing waypoint");
            waypoint.setNextWaypoint(next);
            BasicEntityShip ship = ship(helper, entities);
            ship.moveTo(first.getX() + 0.5D, first.getY() + 0.5D, first.getZ() + 0.5D);
            ship.setGuardedPos(first.getX(), first.getY(), first.getZ(), helper.getLevel().dimension(), 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.getCommandState();
            helper.assertTrue(EntityHelper.updateWaypointMove(ship), "The waypoint must advance");
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition guard
                            && guard.position().equals(new CommandPos(next.getX(), next.getY(), next.getZ())),
                    "Waypoint should replace the guard destination");
            helper.assertTrue(ship.getLastCommandStateChange().issuer() instanceof CommandIssuer.Waypoint,
                    "Waypoint must be recorded as the issuer");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void persistentOrdersAndSittingSurviveNbt(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            cow.moveTo(ship.position());
            helper.getLevel().addFreshEntity(cow);
            var id = helper.getLevel().dimension().location();
            DimensionKey dimension = new DimensionKey(id.getNamespace(), id.getPath());
            CommandPos position = new CommandPos(-1, -1, -1);
            for (ShipCommand command : List.of(new ShipCommand.Follow(),
                    new ShipCommand.Move(dimension, position, true),
                    new ShipCommand.GuardPosition(dimension, position, false),
                    new ShipCommand.GuardEntity(ShipCommandStateAdapter.handle(cow)))) {
                for (boolean sit : List.of(false, true)) {
                    ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(command));
                    ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                            new CommandStateOp.Apply(new ShipCommand.SetSitting(sit)));
                    var expected = ship.getCommandState();
                    CompoundTag saved = new CompoundTag();
                    ship.addAdditionalSaveData(saved);
                    ship.readAdditionalSaveData(saved);
                    helper.assertTrue(expected.equals(ship.getCommandState()),
                            "NBT round-trip changed the movement or sitting order");
                    check(ship);
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void missingGuardIdentityRestoresFollow(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            ship.setGuardedPos(-1, -1, -1, 0, 2);
            ship.setStateFlag(ID.F.CanFollow, false);
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                    "A guard without a UUID should restore as follow");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void guardMetadataCarriesFollowInBothAuthorities(GameTestHelper helper) {
        try (var legacy = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            for (boolean follow : List.of(false, true)) {
                ship.setStateFlag(ID.F.CanFollow, follow);
                helper.assertTrue(S2CEntitySyncPacket.roundTripGuardFollowForTest(ship),
                        "Legacy guard metadata lost CanFollow");
            }
            try (var modern = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
                ship.getCommandState();
                helper.assertTrue(S2CEntitySyncPacket.roundTripGuardFollowForTest(ship),
                        "New guard metadata lost CanFollow");
                check(ship);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void authoritySwitchRebuildsOnlyAtBoundary(GameTestHelper helper) {
        try (var legacy = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            ship.getCommandState();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 2));
            ship.setGuardedPos(pos.getX(), pos.getY(), pos.getZ(), helper.getLevel().dimension(), 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            cow.moveTo(ship.position());
            helper.getLevel().addFreshEntity(cow);
            ship.setManualTarget(cow);
            try (var modern = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition,
                        "Switching to NEW should migrate the current legacy guard");
                helper.assertTrue(ship.getCommandState().manualAttack().isPresent(),
                        "A live legacy manual attack should survive the switch");
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.Follow()));
                check(ship);
                BlockPos projected = helper.absolutePos(new BlockPos(10, 2, 2));
                var dimensionId = helper.getLevel().dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(dimensionId.getNamespace(), dimensionId.getPath()),
                                new CommandPos(projected.getX(), projected.getY(), projected.getZ()), false)));
                check(ship);
            }
            helper.assertTrue(ship.getCommandState() == null && !ship.getStateFlag(ID.F.CanFollow),
                    "Switching to LEGACY should retain the projected guard order");
            ship.setStateMinor(ID.M.FormatType, 0);
            ship.setStateMinor(ID.M.FollowMax, 3);
            helper.assertTrue(new ShipGuardingGoal(ship).canUse(),
                    "The legacy guard goal should still use the projected destination");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void firstNewObservationDoesNotInheritManualAttack(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            cow.moveTo(ship.position());
            helper.getLevel().addFreshEntity(cow);
            ship.setManualTarget(cow);
            helper.assertTrue(ship.getCommandState().manualAttack().isEmpty() && ship.getManualTarget() == null,
                    "First NEW observation must not inherit the unsaved manual target");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sitGoalDoesNotChangeCommandOnStop(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            ShipSitGoal goal = new ShipSitGoal(ship);
            helper.assertTrue(goal.canUse(), "Sitting goal must start");
            goal.start();
            goal.tick();
            goal.stop();
            helper.assertTrue(ship.getCommandState().sitting() && ship.isOrderedToSit(),
                    "Stopping the goal must not cancel a sitting order");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountRightClickSeatsHost(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            mount.moveTo(ship.position());
            helper.getLevel().addFreshEntity(mount);
            mount.setHost(ship);
            FakePlayer player = owner(helper, ship);
            player.moveTo(ship.getX() + 8D, ship.getY(), ship.getZ());
            mount.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(ship.getCommandState().sitting(), "Mount interaction should seat the host");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fuelExhaustionClearsManualAttack(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Cow cow = entities.add(EntityType.COW.create(helper.getLevel()));
            cow.moveTo(ship.position());
            helper.getLevel().addFreshEntity(cow);
            ship.goalSelector.addGoal(1, new ShipSitGoal(ship));
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(cow))));
            ship.setStateMinor(ID.M.NumGrudge, 0);
            ship.decrGrudgeNum(0);
            ship.aiStep();
            helper.assertTrue(ship.getCommandState().manualAttack().isEmpty(),
                    "Fuel exhaustion should clear manual attack");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void lostGuardDuringSittingReturnsToFollowWhenStanding(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Cow target = entities.add(EntityType.COW.create(helper.getLevel()));
            target.moveTo(ship.position());
            helper.getLevel().addFreshEntity(target);
            FakePlayer player = owner(helper, ship);
            ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.GuardEntity(ShipCommandStateAdapter.handle(target))));
            ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            target.discard();
            ship.mobInteract(player, InteractionHand.MAIN_HAND);
            new ShipGuardingGoal(ship).canUse();
            helper.assertTrue(!ship.getCommandState().sitting()
                            && ship.getCommandState().movement() instanceof MovementOrder.Follow
                            && ship.getStateFlag(ID.F.CanFollow),
                    "Standing after the guarded entity dies should restore follow mode");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unresolvedManualAttackDuringMigrationDoesNotReenter(GameTestHelper helper) {
        try (var legacy = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            ship.getCommandState();
            Cow unregistered = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(unregistered != null && unregistered.isAlive(), "Fixture needs a live entity");
            ship.setManualTarget(unregistered);
            try (var modern = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW)) {
                try {
                    helper.assertTrue(ship.getCommandState().manualAttack().isEmpty(),
                            "An unresolved manual target must be cleared during migration");
                } catch (StackOverflowError recursion) {
                    throw new AssertionError("Migration reentered while resolving a manual target", recursion);
                }
                CommandStateChange change = ship.getLastCommandStateChange();
                helper.assertTrue(change != null && change.serverSequence() <= 2
                                && change.issuer() instanceof CommandIssuer.Ship issuer
                                && issuer.reason() == ShipTransitionReason.ATTACK_TARGET_INVALID,
                        "Migration should clear the invalid target once without reentering");
                check(ship);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void lostBoatStandsSittingShipButIntactDismountDoesNot(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Boat boat = entities.add(EntityType.BOAT.create(helper.getLevel()));
            boat.moveTo(ship.position());
            helper.getLevel().addFreshEntity(boat);
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            ShipSitGoal goal = new ShipSitGoal(ship);
            ship.goalSelector.addGoal(0, goal);
            ship.goalSelector.tick();
            helper.assertTrue(ship.goalSelector.getRunningGoals().anyMatch(running -> running.getGoal() == goal),
                    "The sitting goal must be running before the boat ride");
            helper.assertTrue(ship.startRiding(boat, true), "The ship must board the boat");
            // The level increments tickCount before calling tick; a direct fixture tick does not.
            ship.tickCount = 5;
            ship.tick();
            ship.goalSelector.tick();
            helper.assertTrue(ship.goalSelector.getRunningGoals().noneMatch(running -> running.getGoal() == goal),
                    "The boat must stop the sitting goal through vanilla control flags");
            helper.assertTrue(ship.getCommandState().sitting() && ship.isOrderedToSit(),
                    "A boat disabling the sitting goal must not cancel the sitting command");
            ship.stopRiding();
            ship.tick();
            helper.assertTrue(ship.getCommandState().sitting(), "Normal dismount should keep the sitting command");
            helper.assertTrue(ship.startRiding(boat, true), "The ship must reboard the boat");
            boat.discard();
            helper.assertTrue(!ship.isPassenger() && !ship.getCommandState().sitting(),
                    "Losing a boat should make the ship stand");
            helper.assertTrue(ship.getLastCommandStateChange().issuer() instanceof CommandIssuer.Ship issuer
                            && issuer.reason() == ShipTransitionReason.VEHICLE_LOST,
                    "The lost vehicle should be recorded as the reason for standing");
            AbstractMinecart minecart = entities.add(EntityType.MINECART.create(helper.getLevel()));
            minecart.moveTo(ship.position());
            helper.getLevel().addFreshEntity(minecart);
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            helper.assertTrue(ship.startRiding(minecart, true), "The ship must board the minecart");
            minecart.discard();
            helper.assertTrue(!ship.getCommandState().sitting(), "Losing a minecart should make the ship stand");
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            mount.moveTo(ship.position());
            helper.getLevel().addFreshEntity(mount);
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            helper.assertTrue(ship.startRiding(mount, true), "The ship must board its own mount");
            mount.discard();
            helper.assertTrue(ship.getCommandState().sitting(), "Losing a mod mount should retain sitting");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unloadedBoatKeepsSittingCommand(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Boat boat = entities.add(EntityType.BOAT.create(helper.getLevel()));
            boat.moveTo(ship.position());
            helper.getLevel().addFreshEntity(boat);
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            CommandStateChange before = ship.getLastCommandStateChange();
            helper.assertTrue(ship.startRiding(boat, true), "The sitting ship must board the boat");
            boat.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(!ship.isPassenger(), "Removing a boat must dismount its passenger");
            ship.tick();
            helper.assertTrue(ship.getCommandState().sitting() && ship.isOrderedToSit(),
                    "Unloading a boat must not cancel the sitting command");
            helper.assertTrue(ship.getLastCommandStateChange() == before,
                    "Unloading a boat must not record a lost vehicle transition");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyBoatRideStillCancelsSitting(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            Boat boat = entities.add(EntityType.BOAT.create(helper.getLevel()));
            boat.moveTo(ship.position());
            helper.getLevel().addFreshEntity(boat);
            ship.setEntitySit(true);
            ShipSitGoal goal = new ShipSitGoal(ship);
            ship.goalSelector.addGoal(0, goal);
            ship.goalSelector.tick();
            helper.assertTrue(ship.goalSelector.getRunningGoals().anyMatch(running -> running.getGoal() == goal),
                    "The legacy sitting goal must start before the boat ride");
            helper.assertTrue(ship.startRiding(boat, true), "The legacy ship must board the boat");
            ship.tickCount = 5;
            ship.tick();
            ship.goalSelector.tick();
            helper.assertTrue(!ship.isOrderedToSit(), "The legacy sit goal should cancel sitting on a boat (tick="
                    + ship.tickCount + ", riding=" + ship.isPassenger() + ", running="
                    + ship.goalSelector.getRunningGoals().anyMatch(running -> running.getGoal() == goal) + ")");
            boat.discard();
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void playerDamageToVanillaVehiclesStandsSittingShip(GameTestHelper helper) {
        try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                var entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);
            FakePlayer player = owner(helper, ship);
            for (boolean creative : List.of(true, false)) {
                AbstractMinecart minecart = entities.add(EntityType.MINECART.create(helper.getLevel()));
                minecart.moveTo(ship.position());
                helper.getLevel().addFreshEntity(minecart);
                ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                        new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
                helper.assertTrue(ship.startRiding(minecart, true), "The sitting ship must board the minecart");
                player.getAbilities().instabuild = creative;
                for (int hit = 0; hit < (creative ? 1 : 5); hit++) {
                    minecart.hurt(player.damageSources().playerAttack(player), 1F);
                    if (!creative && hit < 4) {
                        helper.assertTrue(!minecart.isRemoved(), "Survival damage must accumulate before destruction");
                    }
                }
                helper.assertTrue(minecart.isRemoved() && !ship.isPassenger(),
                        "Player damage must destroy and eject from the minecart");
                helper.assertTrue(ship.getCommandState().sitting(),
                        "The minecart ejects its passenger before it is removed");
                ship.tick();
                helper.assertTrue(!ship.getCommandState().sitting() && !ship.isOrderedToSit(),
                        "A destroyed minecart must make its passenger stand on the next tick");
                helper.assertTrue(ship.getLastCommandStateChange().issuer() instanceof CommandIssuer.Ship issuer
                                && issuer.reason() == ShipTransitionReason.VEHICLE_LOST,
                        "Minecart destruction must record the lost vehicle");
                check(ship);
            }
            Boat boat = entities.add(EntityType.BOAT.create(helper.getLevel()));
            boat.moveTo(ship.position());
            helper.getLevel().addFreshEntity(boat);
            ship.applyCommandState(new CommandIssuer.Player(player.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            helper.assertTrue(ship.startRiding(boat, true), "The sitting ship must board the boat");
            player.getAbilities().instabuild = true;
            boat.hurt(player.damageSources().playerAttack(player), 1F);
            helper.assertTrue(boat.isRemoved() && !ship.isPassenger(), "Player damage must destroy the boat");
            ship.tick();
            helper.assertTrue(!ship.getCommandState().sitting() && !ship.isOrderedToSit(),
                    "A destroyed boat must make its passenger stand");
            helper.assertTrue(ship.getLastCommandStateChange().issuer() instanceof CommandIssuer.Ship issuer
                            && issuer.reason() == ShipTransitionReason.VEHICLE_LOST,
                    "Boat destruction must record the lost vehicle");
            check(ship);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void repeatedFormationMoveClearsFlagshipTarget(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                    var context = PointerSingleModeGameTests.createContext(helper, "repeat_formation", 12018);
                    var online = new FormationGameTestOwner(context.player())) {
                BasicEntityShip flagship = null;
                for (int slot = 0; slot < 5; slot++) {
                    BasicEntityShip member = PointerSingleModeGameTests.addShip(context, slot, 1201800 + slot,
                            new Vec3(4.5D + slot, 2D, 1.5D));
                    member.setStateMinor(ID.M.FormatType, 1);
                    member.enableCommandProjectionCheckForTest();
                    if (slot == 0) flagship = member;
                }
                context.capa().setFormatID(0, 1);
                BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 4));
                int[] values = {context.player().getId(), 0, PointerItem.MODE_FORMATION, 0,
                        pos.getX(), pos.getY(), pos.getZ()};
                var resolver = (ShipCommandDispatcher.ShipResolver) (level, capa, team, slot) -> {
                    Entity entity = level.getEntity(capa.getTeamSID(team, slot));
                    return entity instanceof BasicEntityShip member ? member : null;
                };
                var known = (ShipCommandDispatcher.KnownShip) (level, capa, team, slot) ->
                        capa.getTeamMember(team, slot) > 0;
                var fuel = (ShipCommandDispatcher.FuelMarker) (player, ships, x, y, z) -> { };
                var first = ShipCommandDispatcher.dispatch(context.player(), CommandKind.MOVE, values,
                        resolver, known, fuel);
                helper.assertTrue(first != null && first.accepted().size() == 5
                                && flagship.getCommandState().movement() instanceof MovementOrder.MoveTo,
                        "The first formation move must reach all five ships");
                Zombie target = PointerSingleModeGameTests.addTarget(context, new Vec3(2.5D, 2D, 4.5D));
                flagship.setTarget(target);
                flagship.setEntityTarget(target);
                helper.assertTrue(flagship.getTarget() == target && flagship.getEntityTarget() == target,
                        "The flagship needs a target before repeating the move");
                var second = ShipCommandDispatcher.dispatch(context.player(), CommandKind.MOVE, values,
                        resolver, known, fuel);
                helper.assertTrue(second != null && second.accepted().size() == 5
                                && flagship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "Repeating the formation move must switch the flagship to follow");
                helper.assertTrue(flagship.getTarget() == null && flagship.getEntityTarget() == null,
                        "Repeating the formation move must clear the flagship's target");
                check(flagship);
                helper.succeed();
            }
        });
    }
}
