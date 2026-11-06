package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCranePositionGoal;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneEntryGameTests {
    private static final Vec3 START = new Vec3(7.5D, 5D, 3.5D);
    private static final Vec3 END = new Vec3(3.5D, 3D, 3.5D);

    private CraneEntryGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "crane_entry_walk", timeoutTicks = 400)
    public static void waitingShipWalksDownStepsIntoCraneCapture(GameTestHelper helper) {
        start(helper, false, rig -> {
            Vec3[] previous = {rig.ship.position()};
            boolean[] waitingOutside = {false};
            helper.onEachTick(() -> {
                helper.assertTrue(rig.ship.position().distanceToSqr(previous[0]) < 2.25D,
                        "Entry must walk naturally without teleporting or position corrections.");
                previous[0] = rig.ship.position();
                if (rig.ship.getStateMinor(ID.M.CraneState) == 1 && !rig.inCaptureArea()) {
                    waitingOutside[0] = true;
                    helper.assertTrue(ShipMovementGate.cranePosition(rig.ship).isPresent(),
                            "An uncaptured waiting ship above the capture area must retain its entry intent.");
                    helper.assertTrue(ShipMovementGate.craneBusy(rig.ship),
                            "Entry must not disable ordinary crane-busy suppression.");
                }
            });
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(rig.crane.getDockedShip() == rig.ship
                                && rig.ship.getStateMinor(ID.M.CraneState) == 2,
                        "Ship must naturally reach working phase through the stepped entry.");
            }).thenExecute(() -> {
                helper.assertTrue(waitingOutside[0], "Fixture must observe phase 1 outside capture before phase 2.");
                helper.assertTrue(rig.inCaptureArea() && rig.ship.getY() < rig.crane.getBlockPos().getY(),
                        "Capture must use the original lower AABB, not an expanded capture range.");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "crane_entry_wall", timeoutTicks = 400)
    public static void waitingShipCannotTeleportThroughBlockedEntry(GameTestHelper helper) {
        start(helper, true, rig -> helper.startSequence().thenIdle(180).thenExecute(() -> {
            helper.assertTrue(rig.ship.getStateMinor(ID.M.CraneState) == 1 && rig.crane.getDockedShip() == null,
                    "An impassable entry must remain waiting, not capture through the wall.");
            helper.assertTrue(!rig.inCaptureArea() && rig.ship.getX() > helper.absolutePos(new BlockPos(5, 5, 3)).getX(),
                    "No teleport fallback may carry the ship below or across the sealed wall.");
            helper.succeed();
        }));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "crane_entry_constraints", timeoutTicks = 200)
    public static void craneEntryStillRequiresFuelPostureDestinationAndOwner(GameTestHelper helper) {
        start(helper, false, rig -> {
            EntityHelper.updateWaypointMove(rig.ship);
            helper.assertTrue(rig.ship.getStateMinor(ID.M.CraneState) == 1 && !rig.inCaptureArea(),
                    "Fixture must be waiting above the capture boundary.");
            rig.ship.setStateMinor(ID.M.NumGrudge, 0);
            rig.ship.setStateFlag(ID.F.NoFuel, true);
            helper.assertTrue(rig.ship.getStateMinor(ID.M.NumGrudge) == 0 && rig.ship.getStateFlag(ID.F.NoFuel),
                    "Fixture must actually have no fuel.");
            assertNoEntry(helper, rig.ship, "Fuel exhaustion");
            rig.ship.setStateMinor(ID.M.NumGrudge, 1000);
            rig.ship.setStateFlag(ID.F.NoFuel, false);
            rig.ship.applyCommandState(new CommandIssuer.Waypoint(),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            helper.assertTrue(rig.ship.isOrderedToSit(), "Fixture must actually be ordered to sit.");
            assertNoEntry(helper, rig.ship, "Sitting");
            rig.ship.applyCommandState(new CommandIssuer.Waypoint(),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(false)));
            rig.ship.setOwnerUUID(UUID.randomUUID());
            helper.assertTrue(!rig.crane.getOwnerUUID().equals(rig.ship.getOwnerUUID()),
                    "Fixture must have mismatched owner UUIDs despite matching numeric UIDs.");
            assertNoEntry(helper, rig.ship, "Wrong owner");
            rig.ship.setOwnerUUID(rig.crane.getOwnerUUID());
            BlockPos other = rig.crane.getBlockPos().east(2);
            guard(rig.ship, other);
            helper.assertTrue(rig.ship.getGuardedPos(0) == other.getX(), "Fixture must retain another destination.");
            assertNoEntry(helper, rig.ship, "Another destination");
            helper.succeed();
        });
    }

    private static void assertNoEntry(GameTestHelper helper, BasicEntityShip ship, String condition) {
        helper.assertTrue(ShipMovementGate.cranePosition(ship).isEmpty() && !new ShipCranePositionGoal(ship).canUse(),
                condition + " must still block crane entry.");
    }

    private static void start(GameTestHelper helper, boolean wall, java.util.function.Consumer<Rig> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Rig rig = new Rig(helper, wall);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(rig.ship.tickCount >= 31,
                    "Wait until after goal registration and immediately before the next arrival observation.")).thenExecute(() -> {
                BlockPos pos = rig.crane.getBlockPos();
                guard(rig.ship, pos);
                helper.assertTrue(rig.ship.getStateMinor(ID.M.CraneState) == 0
                                && rig.ship.getStateMinor(ID.M.NumGrudge) > 0 && !rig.ship.getStateFlag(ID.F.NoFuel)
                                && !rig.ship.isOrderedToSit() && !rig.ship.getIsSitting()
                                && rig.ship.hasGuardDestination() && rig.ship.isGuardedInCurrentDimension()
                                && rig.ship.getGuardedPos(0) == pos.getX()
                                && rig.ship.getGuardedPos(1) == pos.getY() && rig.ship.getGuardedPos(2) == pos.getZ()
                                && rig.ship.getCommandState().movement() instanceof MovementOrder.GuardPosition order
                                && order.position().equals(new CommandPos(pos.getX(), pos.getY(), pos.getZ()))
                                && rig.crane.getOwnerUUID().equals(rig.ship.getOwnerUUID()) && !rig.inCaptureArea(),
                        "Fixture must begin idle, fueled, standing, owned and guarding this crane above capture.");
                verification.accept(rig);
            });
        }, START, END);
    }

    private static void guard(BasicEntityShip ship, BlockPos pos) {
        ship.applyCommandState(new CommandIssuer.Waypoint(), new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                ShipCommandStateAdapter.handle(ship).dimension(), new CommandPos(pos.getX(), pos.getY(), pos.getZ()), false)));
    }

    private static final class Rig {
        private final GameTestHelper helper;
        private final Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
        private final BasicEntityShip ship;
        private final TileEntityCrane crane;

        private Rig(GameTestHelper helper, boolean wall) {
            this.helper = helper;
            GameTestEntities.onCompletion(helper, () -> replaced.forEach((pos, state) ->
                    helper.getLevel().setBlock(pos, state, 3)), "entry terrain cleanup");
            for (int x = 0; x <= 12; x++) {
                for (int z = 0; z <= 6; z++) {
                    int floor = x >= 6 ? 4 : x == 5 ? 3 : 2;
                    for (int y = 0; y <= 9; y++) {
                        boolean boundary = x == 0 || x == 12 || z == 0 || z == 6;
                        put(new BlockPos(x, y, z), y <= floor || boundary || wall && x == 5
                                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            BlockPos relativeCrane = new BlockPos(3, 5, 3);
            put(relativeCrane, ModBlocks.CRANE.get().defaultBlockState());
            put(relativeCrane.north(2), Blocks.CHEST.defaultBlockState());
            crane = (TileEntityCrane) helper.getLevel().getBlockEntity(helper.absolutePos(relativeCrane));
            UUID owner = UUID.randomUUID();
            CompoundTag tag = crane.saveWithFullMetadata();
            tag.putUUID("OwnerUUID", owner);
            tag.putInt("PlayerUID", 12549);
            crane.load(tag);
            crane.setPairedChest(helper.absolutePos(relativeCrane.north(2)));
            crane.setCraneMode(24);
            crane.setActive(true);
            helper.assertTrue(crane.isPaired() && owner.equals(crane.getOwnerUUID()),
                    "Fixture crane must be owned, paired and active.");
            GameTestEntities entities = GameTestEntities.open(helper);
            ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Fixture ship must exist.");
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.setOwnerUUID(owner);
            ship.setPlayerUID(12549);
            ship.setStateMinor(ID.M.NumGrudge, 1000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setInvulnerable(true);
            Vec3 pos = helper.absoluteVec(START);
            ship.moveTo(pos.x, pos.y, pos.z);
            helper.getLevel().addFreshEntity(ship);
            GameTestEntities.removeGoalsEachTick(helper, ship, goal -> goal instanceof ShipWanderGoal);
        }

        private void put(BlockPos relative, BlockState state) {
            BlockPos pos = helper.absolutePos(relative);
            replaced.putIfAbsent(pos, helper.getLevel().getBlockState(pos));
            helper.getLevel().setBlock(pos, state, 3);
        }

        private boolean inCaptureArea() {
            BlockPos pos = crane.getBlockPos();
            return ship.getBoundingBox().intersects(new AABB(pos.getX() - 7D, pos.getY() - 8D, pos.getZ() - 7D,
                    pos.getX() + 7D, pos.getY(), pos.getZ() + 7D));
        }
    }
}
