package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FormationMoveHoldGameTests {
    private FormationMoveHoldGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyFormationHoldsAfterArrivalAndRepeatingFollows(GameTestHelper helper) {
        verifyFormationMove(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, 12121);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newFormationHoldsAfterArrivalAndRepeatingFollows(GameTestHelper helper) {
        verifyFormationMove(helper, ConfigHandler.ShipAiTargetAuthority.NEW, 12122);
    }

    private static void verifyFormationMove(GameTestHelper helper,
                                            ConfigHandler.ShipAiTargetAuthority authority, int id) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var override = ShipAiAuthorityOverride.use(authority);
                    var context = PointerSingleModeGameTests.createContext(helper, "hold_" + id, id);
                    var online = new FormationGameTestOwner(context.player())) {
                List<BasicEntityShip> ships = new ArrayList<>();
                for (int slot = 0; slot < 5; slot++) {
                    BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, slot, id * 100 + slot,
                            new Vec3(4.5D + slot, 2D, 1.5D));
                    ship.setStateMinor(ID.M.FormatType, 1);
                    ship.setStateMinor(ID.M.NumGrudge, 100);
                    ships.add(ship);
                }
                context.capa().setFormatID(0, 1);
                BlockPos destination = helper.absolutePos(new BlockPos(8, 2, 4));
                int[] values = {context.player().getId(), 0, PointerItem.MODE_FORMATION, 0,
                        destination.getX(), destination.getY(), destination.getZ(), 1};
                send(context, values);
                for (BasicEntityShip ship : ships) {
                    helper.assertTrue(ship.hasGuardDestination(), "Formation did not assign every ship a position");
                    helper.assertTrue(!ship.shouldReleaseGuardOnArrival(),
                            "Formation move must ignore the packet's release-on-arrival value");
                    if (authority == ConfigHandler.ShipAiTargetAuthority.NEW) {
                        helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.MoveTo move
                                        && !move.releaseOnArrival(),
                                "NEW formation must retain a persistent movement order");
                    }
                    BlockPos assigned = new BlockPos(ship.getGuardedPos(0), ship.getGuardedPos(1),
                            ship.getGuardedPos(2));
                    Path completed = new Path(List.of(new Node(assigned.getX(), assigned.getY(), assigned.getZ())),
                            assigned, true);
                    ship.getNavigation().stop();
                    ship.getNavigation().moveTo(completed, 1D);
                    completed.setNextNodeIndex(completed.getNodeCount());
                    new ShipGuardingGoal(ship).canUse();
                    helper.assertTrue(ship.hasGuardDestination() && !ship.getStateFlag(ID.F.CanFollow),
                            "Formation position must survive completion of its reachable path");
                    if (authority == ConfigHandler.ShipAiTargetAuthority.NEW) {
                        helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.MoveTo,
                                "NEW formation movement was lost on arrival");
                    }
                }
                send(context, values);
                for (BasicEntityShip ship : ships) {
                    helper.assertTrue(!ship.hasGuardDestination() && ship.getStateFlag(ID.F.CanFollow),
                            "Repeating a formation move must return every ship to follow");
                    if (authority == ConfigHandler.ShipAiTargetAuthority.NEW) {
                        helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                                "NEW formation repeat must clear the movement order");
                    }
                }
                helper.succeed();
            }
        });
    }

    private static void send(PointerSingleModeGameTests.TestContext context, int[] values) {
        PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove, values),
                "handleSetMove", context.player());
    }
}
