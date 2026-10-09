package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.command.ShipCommandDispatcher;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.ai.domain.command.CommandDispatchResult;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandKind;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.TargetHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * A ship near a waypoint the route goes on from is called back to it by the guard goal, but not while it
 * is engaged: the guard goal then keeps the distance the combat allows. The decision is read from the
 * goal's own distances after it has looked at the ship, with a real manual lock for the engagement.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaypointPassThroughGameTests {
    private WaypointPassThroughGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_waypoint_pass_through_engaged")
    public static void engagedShipNearAMiddleWaypointKeepsItsStartDistance(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                    var context = PointerSingleModeGameTests.createContext(helper, "pass_through_engaged", 25601)) {
                BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 2560100,
                        new Vec3(4.5D, 2D, 1.5D));
                PointerSingleModeGameTests.select(context.capa(), 0);
                ship.setStateMinor(ID.M.NumGrudge, 1000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setStateFlag(ID.F.PassiveAI, false);
                ship.setEntitySit(false);
                ship.setStateMinor(ID.M.LevelFlare, 0);
                ship.setStateMinor(ID.M.LevelSearchlight, 0);
                ship.setStateMinor(ID.M.FollowMin, 5);
                ship.setStateMinor(ID.M.FollowMax, 6);
                helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == 5 && ship.getStateMinor(ID.M.FollowMax) == 6
                                && !ship.getStateFlag(ID.F.NoFuel),
                        "Fixture: FollowMin 5, FollowMax 6, a fueled ship");
                rebuildTargets(ship);
                helper.assertTrue(ship.hasTargetAuthority(), "NEW target authority goal was not installed");
                ShipTargetAuthorityGoal authorityGoal = authorityGoal(ship);

                BlockPos middle = BlockPos.containing(ship.position()).offset(-4, 0, 0);
                BlockPos last = middle.offset(0, 0, 3);
                WaypointStructureGameTests.place(helper.getLevel(), middle).setNextWaypoint(last);
                WaypointStructureGameTests.place(helper.getLevel(), last);
                var id = helper.getLevel().dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(
                        new ShipCommand.GuardPosition(new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(middle.getX(), middle.getY(), middle.getZ()), false)));
                // the traversal check marks the waypoint; the ship is outside its arrival range
                EntityHelper.updateWaypointMove(ship);
                helper.assertTrue(ship.passThroughWaypoint().isPresent(), "Fixture: the check must mark the waypoint");
                helper.assertTrue(ship.distanceToSqr(Vec3.atCenterOf(middle)) > 9D,
                        "Fixture: the ship must stand outside the arrival range");

                ShipGuardingGoal guard = new ShipGuardingGoal(ship);
                boolean start = guard.canUse();
                double free = maxDistSq(guard);
                helper.assertTrue(start && free <= 7D,
                        "Control: a ship that is not engaged must start back to the point: canUse=" + start
                                + " maxDistSq=" + free);

                Zombie target = PointerSingleModeGameTests.addTarget(context, new Vec3(7.5D, 2D, 1.5D));
                CommandDispatchResult result = dispatchAttack(context, target);
                helper.assertTrue(result != null && ship.getManualTarget() == target,
                        "Attack order was not accepted for " + target);
                ship.tickCount = 100;
                ship.getSensing().tick();
                TargetHelper.updateTarget(ship);
                authorityGoal.tick();
                Optional<TargetLock> lock = authorityGoal.currentLock();
                helper.assertTrue(lock.isPresent() && lock.get().source() == TargetSource.MANUAL
                                && ship.getTarget() == target,
                        "Fixture: the manual order must hold a lock: lock=" + lock);

                boolean engagedStart = guard.canUse();
                double engaged = maxDistSq(guard);
                helper.assertTrue(!engagedStart && engaged > 30D,
                        "An engaged ship must keep its start distance: canUse=" + engagedStart
                                + " maxDistSq=" + engaged);
                helper.succeed();
            }
        });
    }

    private static double maxDistSq(ShipGuardingGoal guard) {
        try {
            Field field = ShipGuardingGoal.class.getDeclaredField("maxDistSq");
            field.setAccessible(true);
            return field.getDouble(guard);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot read the guard goal's distances", error);
        }
    }

    private static CommandDispatchResult dispatchAttack(PointerSingleModeGameTests.TestContext context, Zombie target) {
        return ShipCommandDispatcher.dispatch(context.player(), CommandKind.ATTACK,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()},
                (level, capa, team, slot) -> {
                    int uid = capa.getTeamMember(team, slot);
                    var entity = level.getEntity(capa.getTeamSID(team, slot));
                    BasicEntityShip candidate = entity instanceof BasicEntityShip found ? found : null;
                    return candidate != null && candidate.level() == level
                            && candidate.getPlayerUID() == capa.getPlayerUID()
                            && candidate.getStateMinor(ID.M.ShipUID) == uid ? candidate : null;
                }, (level, capa, team, slot) -> capa.getTeamMember(team, slot) > 0,
                (player, ships, x, y, z) -> { });
    }

    private static void rebuildTargets(BasicEntityShip ship) {
        try {
            Method clear = BasicEntityShip.class.getDeclaredMethod("clearAITargetTasks");
            clear.setAccessible(true);
            clear.invoke(ship);
            ship.setAITargetList();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot rebuild target goals", e);
        }
    }

    private static ShipTargetAuthorityGoal authorityGoal(Mob ship) {
        try {
            Field field = Mob.class.getDeclaredField("targetSelector");
            field.setAccessible(true);
            GoalSelector selector = (GoalSelector) field.get(ship);
            return selector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(ShipTargetAuthorityGoal.class::isInstance)
                    .map(ShipTargetAuthorityGoal.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("NEW authority goal was not registered"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect target selector", e);
        }
    }
}
