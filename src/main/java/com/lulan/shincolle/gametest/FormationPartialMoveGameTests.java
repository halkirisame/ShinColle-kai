package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFormationStateAdapter;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.command.ShipCommandDispatcher;
import com.lulan.shincolle.ai.domain.command.CommandDispatchResult;
import com.lulan.shincolle.ai.domain.command.CommandKind;
import com.lulan.shincolle.ai.domain.command.CommandNotification;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandRejectReason;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.formation.FormationLayoutPlanner;
import com.lulan.shincolle.ai.domain.formation.FormationPattern;
import com.lulan.shincolle.ai.domain.movement.FormationSlot;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FormationPartialMoveGameTests {
    private static final String PREFIX = "chat.shincolle_kai.pointer.command.";

    private FormationPartialMoveGameTests() { }

    private enum Scenario { FAR_LAST, FAR_FIRST, FAR_MIDDLE, MISSING_LAST, INSUFFICIENT, ALL_VALID, NO_FUEL }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_range")
    public static void fiveMembersMoveWithoutDistantSixth(GameTestHelper helper) {
        verifyMovement(helper, Scenario.FAR_LAST, 32001);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_first")
    public static void distantFirstUsesFirstAcceptedFacing(GameTestHelper helper) {
        verifyMovement(helper, Scenario.FAR_FIRST, 32002);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_missing")
    public static void fiveMembersMoveWithoutUnresolvedSixth(GameTestHelper helper) {
        verifyMovement(helper, Scenario.MISSING_LAST, 32003);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_insufficient")
    public static void fourActiveMembersKeepExcludedReasons(GameTestHelper helper) {
        verifyMovement(helper, Scenario.INSUFFICIENT, 32004);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_complete")
    public static void allSixKeepExistingFormationPlacements(GameTestHelper helper) {
        verifyMovement(helper, Scenario.ALL_VALID, 32005);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_fuel")
    public static void fiveFueledMembersMoveAndMarkSixth(GameTestHelper helper) {
        verifyMovement(helper, Scenario.NO_FUEL, 32006);
    }

    private static void verifyMovement(GameTestHelper helper, Scenario scenario, int id) {
        verifyMovement(helper, scenario, id, FormationPattern.DOUBLE_LINE);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_line_middle")
    public static void lineAheadCompactsAfterExcludedThirdSlot(GameTestHelper helper) {
        verifyMovement(helper, Scenario.FAR_MIDDLE, 32007, FormationPattern.LINE_AHEAD);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_echelon_middle")
    public static void echelonCompactsAfterExcludedThirdSlot(GameTestHelper helper) {
        verifyMovement(helper, Scenario.FAR_MIDDLE, 32008, FormationPattern.ECHELON);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_partial_formation_line_first")
    public static void lineAheadStartsWithSecondAfterExcludedFirstSlot(GameTestHelper helper) {
        verifyMovement(helper, Scenario.FAR_FIRST, 32009, FormationPattern.LINE_AHEAD);
    }

    private static void verifyMovement(GameTestHelper helper, Scenario scenario, int id, FormationPattern pattern) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, "partial_" + id, id);
            context.level().addNewPlayer(context.player());
            var online = new FormationGameTestOwner(context.player());
            GameTestEntities.onCompletion(helper, () -> {
                online.close();
                context.level().removePlayerImmediately(context.player(), Entity.RemovalReason.DISCARDED);
            }, "partial formation owner cleanup");
            for (int x = -2; x <= 24; x++) {
                for (int z = -2; z <= 24; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
            List<BasicEntityShip> ships = new ArrayList<>();
            List<Vec3> starts = new ArrayList<>();
            List<CommandPos> expected = new ArrayList<>();
            List<BasicEntityShip> marked = new ArrayList<>();
            for (int slot = 0; slot < 6; slot++) {
                if ((scenario == Scenario.MISSING_LAST || scenario == Scenario.INSUFFICIENT) && slot == 5) {
                    context.capa().setTeamMember(0, slot, id * 100 + slot);
                    context.capa().setTeamSID(0, slot, -1);
                    ships.add(null);
                    continue;
                }
                BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, slot, id * 100 + slot,
                        new Vec3(2.5D + slot * 1.5D, 1D, 2.5D));
                ship.setOwnerUUID(context.player().getUUID());
                ship.calcShipAttributes(31, false);
                ship.setHealth(ship.getMaxHealth());
                ship.setNoGravity(true);
                ship.setInvulnerable(true);
                ship.setStateMinor(ID.M.NumGrudge, 1000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setStateFlag(ID.F.PassiveAI, true);
                ship.setStateFlag(ID.F.PickItem, false);
                GameTestEntities.removeGoalsEachTick(helper, ship, goal -> !(goal instanceof ShipGuardingGoal));
                ships.add(ship);
            }
            context.capa().setFormatID(0, pattern.legacyId());
            CommandDispatchResult[] result = new CommandDispatchResult[1];
            helper.startSequence().thenWaitUntil(() -> {
                for (BasicEntityShip ship : ships) {
                    if (ship != null) helper.assertTrue(ship.tickCount >= 24, "Waiting for ship initialization");
                }
            }).thenExecute(() -> {
                for (int slot = 0; slot < ships.size(); slot++) {
                    BasicEntityShip ship = ships.get(slot);
                    if (ship == null) {
                        starts.add(null);
                        continue;
                    }
                    Vec3 start = helper.absoluteVec(new Vec3(2.5D + slot * 1.5D, 1D, 2.5D));
                    if (excluded(scenario, slot)) {
                        GameTestEntities.removeAllGoalsEachTick(helper, ship);
                        if (scenario == Scenario.NO_FUEL) ship.setStateFlag(ID.F.NoFuel, true);
                        else start = helper.absoluteVec(new Vec3(20.5D, 70D, 10.5D));
                    }
                    ship.moveTo(start.x, start.y, start.z, 0F, 0F);
                    boolean airborne = excluded(scenario, slot) && scenario != Scenario.NO_FUEL;
                    ship.setNoGravity(airborne);
                    ship.setOnGround(!airborne);
                    ship.setDeltaMovement(Vec3.ZERO);
                    ship.getNavigation().stop();
                    starts.add(start);
                    GameTestEntities.assertRegistered(helper, ship);
                    helper.assertTrue(ship.getOwner() == context.player(), "Fixture owner must resolve");
                    helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0
                                    && ship.getStateFlag(ID.F.NoFuel) == (scenario == Scenario.NO_FUEL && slot == 5),
                            "Fixture fuel must match the scenario");
                    helper.assertTrue((ship.distanceToSqr(context.player()) > 4096D) == airborne,
                            "Fixture range must match the scenario at slot " + slot);
                    if (!airborne) {
                        helper.assertTrue(ship.onGround() && !ship.isNoGravity()
                                        && !context.level().getBlockState(ship.blockPosition().below()).isAir(),
                                "Walking fixture must stand on its floor with gravity enabled");
                    }
                }
                BasicEntityShip first = ships.get(scenario == Scenario.FAR_FIRST ? 1 : 0);
                BlockPos target = helper.absolutePos(new BlockPos(14, 1, 10));
                CommandPos anchor = new CommandPos(target.getX(), target.getY(), target.getZ());
                var facing = FormationLayoutPlanner.facing(anchor.x(), anchor.z(), first.getX(), first.getZ());
                int placementIndex = 0;
                for (int slot = 0; slot < ships.size(); slot++) {
                    BasicEntityShip ship = ships.get(slot);
                    if (ship == null) {
                        expected.add(null);
                        continue;
                    }
                    ship.calcShipAttributes(16, false);
                    var active = ShipFormationStateAdapter.active(ship);
                    helper.assertTrue(active.isPresent() && active.get().pattern() == pattern,
                            "Fixture formation must be active before dispatch");
                    helper.assertTrue(active.orElseThrow().slot().index() == slot,
                            "Fixture projection must retain its original slot " + slot);
                    helper.assertTrue(active.orElseThrow().minimumMovement() > 0F
                                    && ship.getAttrs().getMoveSpeed(true) > 0F
                                    && ship.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0D,
                            "Fixture must initialize real ship and formation movement attributes");
                    helper.assertTrue(ship.getHealth() == ship.getMaxHealth(),
                            "Fixture must not select a low-health flee intent");
                    if (excluded(scenario, slot)) expected.add(null);
                    else {
                        FormationSlot placement = pattern.sequential() ? new FormationSlot(placementIndex++)
                                : active.orElseThrow().slot();
                        expected.add(FormationLayoutPlanner.place(pattern, placement, facing, anchor));
                    }
                }
                result[0] = ShipCommandDispatcher.dispatch(context.player(), CommandKind.MOVE,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_FORMATION, 0,
                                target.getX(), target.getY(), target.getZ()},
                        (level, capa, team, slot) -> {
                            var entity = level.getEntity(capa.getTeamSID(team, slot));
                            return entity instanceof BasicEntityShip ship ? ship : null;
                        }, (level, capa, team, slot) -> capa.getTeamMember(team, slot) > 0,
                        (player, noFuel, x, y, z) -> marked.addAll(noFuel));
                helper.assertTrue(result[0] != null, "Dispatch must validate the request");
                int accepted = scenario == Scenario.INSUFFICIENT ? 0 : scenario == Scenario.ALL_VALID ? 6 : 5;
                helper.assertTrue(result[0].accepted().size() == accepted, "Wrong formation participant count");
                helper.assertTrue(result[0].rejected().size() == 6 - accepted, "Wrong formation rejection count");
                for (var rejected : result[0].rejected()) {
                    int slot = rejected.recipient().slot();
                    helper.assertTrue(accepted == 0 || excluded(scenario, slot) || ships.get(slot) == null,
                            "Unexpected rejected formation slot " + slot);
                    CommandRejectReason reason = slot == 5 && (scenario == Scenario.MISSING_LAST
                            || scenario == Scenario.INSUFFICIENT) ? CommandRejectReason.NOT_FOUND
                            : scenario == Scenario.NO_FUEL ? CommandRejectReason.NO_FUEL
                            : excluded(scenario, slot) ? CommandRejectReason.OUT_OF_RANGE
                            : CommandRejectReason.FORMATION_UNSATISFIED;
                    helper.assertTrue(rejected.reason() == reason, "Excluded reason changed at slot " + slot);
                }
                helper.assertTrue(marked.equals(scenario == Scenario.NO_FUEL ? List.of(ships.get(5)) : List.of()),
                        "Fuel marker must receive only the fuel-starved ship");
                var summary = CommandNotification.summarize(result[0], List.of(), true);
                for (var reason : summary.reasons()) {
                    helper.assertTrue(!reason.slots().isEmpty(), "Excluded formation members need visible slots");
                }
                for (int slot = 0; slot < ships.size(); slot++) {
                    BasicEntityShip ship = ships.get(slot);
                    if (ship == null) continue;
                    if (accepted > 0 && !excluded(scenario, slot)) {
                        helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.MoveTo move
                                        && move.position().equals(expected.get(slot)),
                                "Formation placement changed at slot " + slot);
                        helper.assertTrue(ship.getNavigation().getPath() != null,
                                "Accepted ground formation member must have a path at slot " + slot);
                        helper.assertTrue(ShipMovementGate.intent(ship) instanceof MovementIntent.MoveTo,
                                "Accepted formation member must intend to move at slot " + slot);
                    } else helper.assertTrue(!ship.hasGuardDestination(), "Excluded ship received a move");
                }
            }).thenIdle(20).thenExecute(() -> {
                for (int slot = 0; slot < ships.size(); slot++) {
                    BasicEntityShip ship = ships.get(slot);
                    if (ship == null) continue;
                    boolean moves = scenario != Scenario.INSUFFICIENT && !excluded(scenario, slot);
                    helper.assertTrue((ship.position().distanceToSqr(starts.get(slot)) > 0.01D) == moves,
                            "Unexpected actual movement at slot " + slot + ": " + ship.position());
                    helper.assertTrue(context.capa().getTeamMember(0, slot) == id * 100 + slot,
                            "A command must not change team registration");
                }
            }).thenSucceed();
        }, new Vec3(-2D, 1D, -2D), new Vec3(24D, 1D, 24D));
    }

    private static boolean excluded(Scenario scenario, int slot) {
        return scenario == Scenario.FAR_FIRST && slot == 0
                || scenario == Scenario.FAR_MIDDLE && slot == 2
                || (scenario == Scenario.FAR_LAST || scenario == Scenario.NO_FUEL) && slot == 5
                || scenario == Scenario.INSUFFICIENT && slot == 4;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void formationNoticeKeepsConditionAndReasonSlotsTogether(GameTestHelper helper) {
        var summary = new CommandNotification.Summary(List.of(new CommandNotification.ReasonCount(
                CommandRejectReason.OUT_OF_RANGE, 2, List.of(2, 6))), true, false, null);
        try {
            Method method = ShipCommandDispatcher.class.getDeclaredMethod("notificationMessage",
                    CommandNotification.Summary.class, Component.class, boolean.class);
            method.setAccessible(true);
            Component message = (Component) method.invoke(null, summary, null, true);
            helper.assertTrue(message.getContents() instanceof TranslatableContents text
                            && text.getKey().equals(PREFIX + "formation_unsatisfied"), "Condition notice is missing");
            Component reason = message.getSiblings().get(1);
            helper.assertTrue(reason.getContents() instanceof TranslatableContents text
                            && text.getKey().equals(PREFIX + "formation_excluded.out_of_range")
                            && text.getArgs()[0].equals("2・6"), "Visible slot numbers or reason were lost");
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect formation notice: " + error);
        }
    }
}
