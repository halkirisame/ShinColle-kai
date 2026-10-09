package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.command.ShipCommandDispatcher;
import com.lulan.shincolle.ai.domain.command.CommandDispatchResult;
import com.lulan.shincolle.ai.domain.command.CommandKind;
import com.lulan.shincolle.ai.domain.command.CommandRejectReason;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.BiConsumer;
import java.lang.reflect.Field;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCommandDispatchGameTests {
    private ShipCommandDispatchGameTests() { }

    private static void verify(GameTestHelper helper, int id,
                               BiConsumer<GameTestHelper, PointerSingleModeGameTests.TestContext> assertion) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                    var context = PointerSingleModeGameTests.createContext(helper, "dispatch_" + id, id)) {
                assertion.accept(helper, context);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newSingleChoosesFirstKnownSelected(GameTestHelper helper) {
        verify(helper, 11001, PointerSingleModeGameTests::verifySingleModeAffectsOnlyLowestSelectedRealShip);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newSingleDoesNotFallBackWhenOutOfRange(GameTestHelper helper) {
        verify(helper, 11002, PointerSingleModeGameTests::verifySingleModeOutOfRangeFirstShipDoesNotFallBack);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newSingleSkipsUnknownSlot(GameTestHelper helper) {
        verify(helper, 11003, PointerSingleModeGameTests::verifySingleModeSkipsSelectedSlotWithoutRealShip);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newGroupIncludesSelectedShips(GameTestHelper helper) {
        verify(helper, 11004, PointerSingleModeGameTests::verifyGroupModeAffectsEverySelectedShip);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newFormationIncludesUnselectedShips(GameTestHelper helper) {
        verify(helper, 11005, PointerSingleModeGameTests::verifyFormationModeAffectsWholeTeamWithoutSelection);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newSingleDoesNotFallBackAcrossDimensions(GameTestHelper helper) {
        verify(helper, 11006, PointerSingleModeGameTests::verifySingleModeOtherDimensionFirstShipDoesNotFallBack);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void groupMoveWorksWithFormationSelected(GameTestHelper helper) {
        verify(helper, 11007, (h, context) -> {
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 1100700,
                    new Vec3(4.5D, 2D, 1.5D));
            PointerSingleModeGameTests.select(context.capa(), 0);
            context.capa().setFormatID(0, 1);
            BlockPos pos = h.absolutePos(new BlockPos(8, 2, 4));
            move(context, PointerItem.MODE_GROUP, pos);
            PointerSingleModeGameTests.assertGuardDestination(h, ship, pos, "selected formation member");
            h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sameCoordinatesInAnotherDimensionSetNewGuard(GameTestHelper helper) {
        verify(helper, 11008, (h, context) -> {
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 1100800,
                    new Vec3(4.5D, 2D, 1.5D));
            PointerSingleModeGameTests.select(context.capa(), 0);
            BlockPos pos = h.absolutePos(new BlockPos(8, 2, 4));
            ship.setGuardedPos(pos.getX(), pos.getY(), pos.getZ(), Level.NETHER, 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            move(context, PointerItem.MODE_SINGLE, pos);
            PointerSingleModeGameTests.assertGuardDestination(h, ship, pos, "other-dimension guard");
            h.assertTrue(ship.getGuardedDimension().equals(Level.OVERWORLD),
                    "Guard destination did not switch to the sender's dimension");
            h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void groupPartialDeliveryRecordsOutOfRange(GameTestHelper helper) {
        verify(helper, 11009, (h, context) -> {
            BasicEntityShip near = PointerSingleModeGameTests.addShip(context, 0, 1100900,
                    new Vec3(4.5D, 2D, 1.5D));
            Vec3 sender = context.player().position();
            BasicEntityShip far = PointerSingleModeGameTests.addShip(context, 1, 1100901,
                    new Vec3(sender.x, sender.y + 65D, sender.z), false);
            PointerSingleModeGameTests.select(context.capa(), 0, 1);
            BlockPos pos = h.absolutePos(new BlockPos(8, 2, 4));
            CommandDispatchResult result = dispatch(context, CommandKind.MOVE,
                    new int[]{context.player().getId(), 0, PointerItem.MODE_GROUP, 1,
                            pos.getX(), pos.getY(), pos.getZ()});
            PointerSingleModeGameTests.assertGuardDestination(h, near, pos, "near group member");
            h.assertTrue(!far.hasGuardDestination() && result.accepted().size() == 1
                            && result.rejected().size() == 1
                            && result.rejected().get(0).reason() == CommandRejectReason.OUT_OF_RANGE,
                    "Partial delivery did not record the distant ship");
            h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void entityGuardRejectsNoFuel(GameTestHelper helper) {
        verify(helper, 11010, (h, context) -> {
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 1101000,
                    new Vec3(4.5D, 2D, 1.5D));
            PointerSingleModeGameTests.select(context.capa(), 0);
            ship.setStateFlag(ID.F.NoFuel, true);
            Zombie target = PointerSingleModeGameTests.addTarget(context, new Vec3(2.5D, 2D, 4.5D));
            CommandDispatchResult result = dispatch(context, CommandKind.GUARD_ENTITY,
                    new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()});
            h.assertTrue(ship.getGuardedEntity() == null && result.accepted().isEmpty()
                            && result.rejected().size() == 1
                            && result.rejected().get(0).reason() == CommandRejectReason.NO_FUEL,
                    "Fuel rejection was not recorded for an entity guard");
            h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_new_command_attack")
    public static void newAttackRemainsManualAuthorityTarget(GameTestHelper helper) {
        var context = PointerSingleModeGameTests.createContext(helper, "dispatch_attack", 11011);
        BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 1101100,
                new Vec3(4.5D, 2D, 1.5D));
        PointerSingleModeGameTests.select(context.capa(), 0);
        Zombie target = PointerSingleModeGameTests.addTarget(context, new Vec3(2.5D, 2D, 4.5D));
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateFlag(ID.F.PassiveAI, true);
        ship.setNoAi(false);
        helper.runAtTickTime(25, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW); context) {
                boolean passive = ship.getStateFlag(ID.F.PassiveAI);
                ship.setStateFlag(ID.F.PassiveAI, !passive);
                ship.setStateFlag(ID.F.PassiveAI, passive);
                helper.assertTrue(ship.hasTargetAuthority(), "NEW target authority goal was not installed");
                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.AttackTarget,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()}),
                        "handleAttackTarget", context.player());
                helper.assertTrue(ship.getManualTarget() == target, "Attack did not set the manual order");
                helper.assertTrue(ship.getTarget() != target, "Attack bypassed the target authority goal");
                Field field = Mob.class.getDeclaredField("targetSelector");
                field.setAccessible(true);
                GoalSelector selector = (GoalSelector) field.get(ship);
                ship.setTarget(null);
                for (int i = 0; i < 4; i++) {
                    ship.tickCount += 2;
                    ship.getSensing().tick();
                    selector.tick();
                }
                helper.assertTrue(ship.getTarget() == target, "Authority tick did not select manual target");
                helper.succeed();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot tick target selector", failure);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newSingleSitTogglesSelectedShipWhenClickingUnselectedTeammate(GameTestHelper helper) {
        verify(helper, 11010, (h, context) -> {
            BasicEntityShip selected = PointerSingleModeGameTests.addShip(context, 1, 2801, new Vec3(4.5D, 2D, 1.5D));
            BasicEntityShip clicked = PointerSingleModeGameTests.addShip(context, 3, 2803, new Vec3(6.5D, 2D, 1.5D));
            PointerSingleModeGameTests.select(context.capa(), 1);
            sit(context, PointerItem.MODE_SINGLE, clicked);
            h.assertTrue(selected.isOrderedToSit() && !clicked.isOrderedToSit(),
                    "First click must seat only the selected ship");
            sit(context, PointerItem.MODE_SINGLE, clicked);
            h.assertTrue(!selected.isOrderedToSit() && !clicked.isOrderedToSit(),
                    "Second click must stand the selected ship up again, based on its own state");
            h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newGroupSitFollowsFirstSelectedShip(GameTestHelper helper) {
        verify(helper, 11011, (h, context) -> {
            BasicEntityShip first = PointerSingleModeGameTests.addShip(context, 0, 2810, new Vec3(4.5D, 2D, 1.5D));
            BasicEntityShip second = PointerSingleModeGameTests.addShip(context, 2, 2812, new Vec3(6.5D, 2D, 1.5D));
            PointerSingleModeGameTests.select(context.capa(), 0, 2);
            sit(context, PointerItem.MODE_SINGLE, first);
            h.assertTrue(first.isOrderedToSit() && !second.isOrderedToSit(), "Fixture must seat only the first ship");
            sit(context, PointerItem.MODE_GROUP, second);
            h.assertTrue(!first.isOrderedToSit() && !second.isOrderedToSit(),
                    "Group sit must set every ship opposite to the first selected ship");
            h.succeed();
        });
    }

    private static void sit(PointerSingleModeGameTests.TestContext context, int mode, BasicEntityShip clicked) {
        PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetSitting,
                new int[]{context.player().getId(), 0, mode, clicked.getId()}),
                "handleSetSitting", context.player());
    }

    private static void move(PointerSingleModeGameTests.TestContext context, int mode, BlockPos pos) {
        PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                new int[]{context.player().getId(), 0, mode, 1, pos.getX(), pos.getY(), pos.getZ()}),
                "handleSetMove", context.player());
    }

    private static CommandDispatchResult dispatch(PointerSingleModeGameTests.TestContext context,
                                                   CommandKind kind, int[] values) {
        return ShipCommandDispatcher.dispatch(context.player(), kind, values,
                (level, capa, team, slot) -> {
                    int uid = capa.getTeamMember(team, slot);
                    var entity = level.getEntity(capa.getTeamSID(team, slot));
                    BasicEntityShip ship = entity instanceof BasicEntityShip candidate ? candidate : null;
                    return ship != null && ship.level() == level && ship.getPlayerUID() == capa.getPlayerUID()
                            && ship.getStateMinor(ID.M.ShipUID) == uid ? ship : null;
                }, (level, capa, team, slot) -> capa.getTeamMember(team, slot) > 0,
                (player, ships, x, y, z) -> { });
    }
}
