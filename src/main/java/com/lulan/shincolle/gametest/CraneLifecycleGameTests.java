package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.ai.ShipCranePositionGoal;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.utility.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneLifecycleGameTests {
    private static final Vec3 UNDER_CRANE = new Vec3(3.5D, 3D, 3.5D);

    private CraneLifecycleGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneArrivalWaitsAndDismountsShipPassengers(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip ship = ship(helper, entities, fixture, UNDER_CRANE, 0);
                BasicEntityShip passenger = ship(helper, entities, fixture, UNDER_CRANE, 0);
                helper.assertTrue(passenger.startRiding(ship, true), "Fixture must carry a ship passenger.");
                fixture.crane().setNextWaypoint(fixture.crane().getBlockPos().east(5));
                EntityHelper.updateWaypointMove(ship);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 1,
                        "Arriving at the crane must enter the waiting state.");
                helper.assertTrue(!passenger.isPassenger(), "Arriving must dismount a carried ship.");
                helper.assertTrue(guards(ship, fixture.crane().getBlockPos()),
                        "A waiting ship must not skip crane work and advance the route.");
                helper.succeed();
            }
        }, UNDER_CRANE);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneCapturePrioritizesWorkingShipAndChecksGuardDestination(GameTestHelper helper) {
        Vec3 edge = UNDER_CRANE.add(5.5D, 0D, 0D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip wrongDestination = ship(helper, entities, fixture, UNDER_CRANE, 2);
                BlockPos elsewhere = fixture.crane().getBlockPos().west(9);
                wrongDestination.setGuardedPos(elsewhere.getX(), elsewhere.getY(), elsewhere.getZ(),
                        helper.getLevel().dimension(), 1);
                wrongDestination.setStateFlag(ID.F.CanFollow, false);
                BasicEntityShip waiting = ship(helper, entities, fixture, UNDER_CRANE.add(1D, 0D, 0D), 1);
                BasicEntityShip working = ship(helper, entities, fixture, edge, 2);
                fixture.crane().setCraneMode(24);
                tick(helper, fixture.crane(), 80);
                helper.assertTrue(fixture.crane().getDockedShip() == working,
                        "A matching working ship in the wider crane area must outrank a waiting or unrelated ship.");
                helper.assertTrue(waiting.getStateMinor(ID.M.CraneState) == 1,
                        "Capturing another ship must leave the waiting ship in its queue.");
                helper.succeed();
            }
        }, UNDER_CRANE, edge);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneFinishesLoadingAndReleasesShipToNextWaypoint(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip ship = ship(helper, entities, fixture, UNDER_CRANE, 0);
                BlockPos next = fixture.crane().getBlockPos().east(5);
                fixture.crane().setNextWaypoint(next);
                fixture.crane().setEnabUnload(false);
                fixture.chest().setItem(0, new ItemStack(Items.STONE, 2));
                EntityHelper.updateWaypointMove(ship);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 1, "Fixture must reach the waiting state.");
                ship.setStateTimer(ID.T.CrandDelay, 0);
                tick(helper, fixture.crane(), 80);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 2
                                && fixture.crane().getDockedShip() == ship,
                        "The crane must capture the waiting ship before doing work.");
                tick(helper, fixture.crane(), 32);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0 && ship.getStateTimer(ID.T.CraneTime) == 0,
                        "Completing work must clear both crane state and elapsed time.");
                helper.assertTrue(fixture.crane().getDockedShip() == null && fixture.chest().getItem(0).isEmpty(),
                        "The completed crane must release its ship after loading the chest cargo.");
                helper.assertTrue(guards(ship, next) && ship.getStateMinor(ID.M.FollowMin) == 2
                                && fixture.crane().getBlockPos().equals(ship.getLastWaypoint()),
                        "Completion must advance the route, remember the crane and restore the departure range.");
                int cargo = 0;
                for (int slot = 6; slot < ship.getCapaShipInventory().getSlots(); slot++) {
                    ItemStack stack = ship.getCapaShipInventory().getStackInSlot(slot);
                    if (stack.is(Items.STONE)) cargo += stack.getCount();
                }
                helper.assertTrue(cargo == 2, "Both loaded items must remain in ship cargo.");
                helper.succeed();
            }
        }, UNDER_CRANE);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneArrivalDismountsShipFromItsMount(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip ship = ship(helper, entities, fixture, UNDER_CRANE, 0);
                BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
                helper.assertTrue(mount != null, "Fixture mount must exist.");
                mount.setHost(ship);
                mount.moveTo(ship.getX(), ship.getY(), ship.getZ());
                helper.getLevel().addFreshEntity(mount);
                helper.assertTrue(ship.startRiding(mount, true), "Fixture ship must ride its own mount.");
                EntityHelper.updateWaypointMove(ship);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 1 && !ship.isPassenger(),
                        "An arrived mounted ship must dismount and enter the waiting queue.");
                helper.succeed();
            }
        }, UNDER_CRANE);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneDockingGoalReadsGateAndExecutesBoundedPlan(GameTestHelper helper) {
        Vec3 position = UNDER_CRANE.add(2D, 0D, 0D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip ship = ship(helper, entities, fixture, position, 2);
                fixture.crane().setCraneMode(24);
                tick(helper, fixture.crane(), 80);
                helper.assertTrue(fixture.crane().getDockedShip() == ship && ShipMovementGate.craneBusy(ship),
                        "Fixture must be captured while ordinary crane-busy semantics remain active.");
                ShipCranePositionGoal goal = new ShipCranePositionGoal(ship);
                helper.assertTrue(goal.canUse(), "A captured ship must have an executable crane positioning intent.");
                goal.tick();
                helper.assertTrue(ship.shipMovementExecutor().last().isPresent()
                                && ship.shipMovementExecutor().last().orElseThrow().step().reason() == MovementReason.CRANE_DOCK,
                        "The goal must send the gate's docking plan through the normal executor.");
                var request = ship.shipMovementExecutor().last();
                goal.tick();
                helper.assertTrue(request.equals(ship.shipMovementExecutor().last()),
                        "Repeated calls in the same tick must not issue unconditional paths.");
                ship.setStateMinor(ID.M.NumGrudge, 0);
                ship.setStateFlag(ID.F.NoFuel, true);
                helper.assertTrue(!goal.canUse(), "The docking goal must still obey fuel and action constraints.");
                helper.succeed();
            }
        }, position);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneUnlinkAndRemovalClearCapturedShip(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip ship = ship(helper, entities, fixture, UNDER_CRANE, 2);
                fixture.crane().setCraneMode(24);
                tick(helper, fixture.crane(), 80);
                helper.assertTrue(fixture.crane().getDockedShip() == ship, "Fixture must capture its ship.");
                ship.setStateTimer(ID.T.CraneTime, 11);
                fixture.crane().setPairedChest(null);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0 && ship.getStateTimer(ID.T.CraneTime) == 0
                                && fixture.crane().getDockedShip() == null,
                        "Unlinking must immediately clear the captured session.");
                fixture.crane().setPairedChest(fixture.chest().getBlockPos());
                ship.setStateTimer(ID.T.CrandDelay, 0);
                ship.setStateMinor(ID.M.CraneState, 2);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 2, "Fixture must restore its working phase.");
                tick(helper, fixture.crane(), 16);
                helper.assertTrue(fixture.crane().getDockedShip() == ship, "Fixture must be captured again.");
                helper.getLevel().setBlock(fixture.crane().getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0 && ship.getStateTimer(ID.T.CraneTime) == 0,
                        "Removing the crane must not leave its ship frozen in a saved work session.");
                helper.succeed();
            }
        }, UNDER_CRANE);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneTimedWaitMeasuresCapturedShipRatherThanBlockAge(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip ship = ship(helper, entities, fixture, UNDER_CRANE, 2);
                fixture.crane().setCraneMode(5);
                tick(helper, fixture.crane(), 80);
                helper.assertTrue(fixture.crane().getDockedShip() == ship && ship.getStateTimer(ID.T.CraneTime) == 0,
                        "The crane's startup age must not satisfy a newly captured ship's wait.");
                tick(helper, fixture.crane(), 16);
                helper.assertTrue(fixture.crane().getDockedShip() == null && ship.getStateMinor(ID.M.CraneState) == 0
                                && ship.getStateTimer(ID.T.CraneTime) == 0,
                        "The first timed mode must release after 16 captured ticks and reset the timer.");
                helper.succeed();
            }
        }, UNDER_CRANE);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneFullWaitPreservesNoEmptyCargoSlotRule(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Fixture fixture = fixture(helper);
                BasicEntityShip ship = ship(helper, entities, fixture, UNDER_CRANE, 2);
                fixture.crane().setCraneMode(1);
                fixture.crane().setEnabUnload(false);
                int last = 0;
                for (int slot = 6; slot < ship.getCapaShipInventory().getSlots(); slot++) {
                    if (ship.getCapaShipInventory().isSlotAvailable(slot)) {
                        ship.getCapaShipInventory().setStackInSlot(slot, new ItemStack(Items.STONE));
                        last = slot;
                    }
                }
                helper.assertTrue(last >= 6, "Fixture must expose cargo slots.");
                ship.getCapaShipInventory().setStackInSlot(last, ItemStack.EMPTY);
                tick(helper, fixture.crane(), 80);
                helper.assertTrue(fixture.crane().getDockedShip() == ship, "One empty cargo slot must keep the ship waiting.");
                ship.getCapaShipInventory().setStackInSlot(last, new ItemStack(Items.STONE));
                tick(helper, fixture.crane(), 16);
                helper.assertTrue(fixture.crane().getDockedShip() == null && ship.getStateMinor(ID.M.CraneState) == 0,
                        "No empty slots must complete full mode even when the stacks are not at maximum count.");
                helper.succeed();
            }
        }, UNDER_CRANE);
    }

    private static Fixture fixture(GameTestHelper helper) {
        BlockPos cranePos = helper.absolutePos(new BlockPos(3, 5, 3));
        BlockPos chestPos = cranePos.east(2);
        helper.getLevel().setBlock(cranePos, ModBlocks.CRANE.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        TileEntityCrane crane = (TileEntityCrane) helper.getLevel().getBlockEntity(cranePos);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
        UUID owner = UUID.randomUUID();
        CompoundTag tag = crane.saveWithFullMetadata();
        tag.putUUID("OwnerUUID", owner);
        tag.putInt("PlayerUID", 12549);
        crane.load(tag);
        crane.setPairedChest(chestPos);
        crane.setActive(true);
        helper.assertTrue(crane.isPaired() && owner.equals(crane.getOwnerUUID()),
                "Fixture must have an active, owned and paired crane.");
        return new Fixture(crane, chest, owner);
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities, Fixture fixture,
                                        Vec3 relative, int phase) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Fixture ship must exist.");
        ship.setOwnerUUID(fixture.owner());
        ship.setPlayerUID(12549);
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setInvulnerable(true);
        Vec3 position = helper.absoluteVec(relative);
        ship.moveTo(position.x, position.y, position.z);
        BlockPos crane = fixture.crane().getBlockPos();
        ship.setGuardedPos(crane.getX(), crane.getY(), crane.getZ(), helper.getLevel().dimension(), 1);
        ship.setStateFlag(ID.F.CanFollow, false);
        ship.setStateTimer(ID.T.CrandDelay, 0);
        ship.setStateMinor(ID.M.CraneState, phase);
        helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == phase && ship.getStateMinor(ID.M.NumGrudge) > 0
                        && !ship.getStateFlag(ID.F.NoFuel) && guards(ship, crane),
                "Fixture must retain its phase, fuel and crane guard destination.");
        helper.getLevel().addFreshEntity(ship);
        return ship;
    }

    private static boolean guards(BasicEntityShip ship, BlockPos pos) {
        return ship.hasGuardDestination() && ship.getGuardedPos(0) == pos.getX()
                && ship.getGuardedPos(1) == pos.getY() && ship.getGuardedPos(2) == pos.getZ();
    }

    private static void tick(GameTestHelper helper, TileEntityCrane crane, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TileEntityCrane.serverTick(helper.getLevel(), crane.getBlockPos(), crane.getBlockState(), crane);
        }
    }

    private record Fixture(TileEntityCrane crane, ChestBlockEntity chest, UUID owner) {
    }
}
