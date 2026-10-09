package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.ShipMovementHost;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.LookReason;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.waypoint.WaypointStep;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.CacheDataShip;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.FormationHelper;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Waypoint traversal and the first steps after a command, now decided in the domain and walked through
 * the executor under NEW. Except where a decision says otherwise, none of it may change what a ship
 * does: each test runs one fixture under LEGACY and under NEW and compares every path request, the guard
 * fields, and the head's aim. A route's path ends at the middle of its block under NEW.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaypointStructureGameTests {
    private WaypointStructureGameTests() {
    }

    // ---------- traversal ----------

    @GameTest(template = "arena", batch = "isolated_waypoint_structure_traversal")
    public static void traversalMatchesAcrossAuthorities(GameTestHelper helper) {
        List<String> legacy = traverse(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
        List<String> now = traverse(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        List<String> expected = legacy.stream().map(WaypointStructureGameTests::routeEndsAtTheMiddle).toList();
        helper.assertTrue(now.equals(expected), "Traversal differs between the authorities:\n LEGACY " + legacy
                + "\n NEW    " + now + "\n EXPECTED " + expected);
        helper.assertTrue(legacy.stream().filter(line -> line.contains("moveTo(")).count() == 2,
                "Fixture must advance exactly twice: " + legacy);
        LogHelper.info("Waypoint traversal parity: " + now);
        helper.succeed();
    }

    private static final Pattern PATH_CALL = Pattern.compile("moveTo\\((-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+)\\)");

    /** What a legacy path request looks like when its end is moved to the middle of the block. */
    private static String routeEndsAtTheMiddle(String line) {
        Matcher match = PATH_CALL.matcher(line);
        if (!match.find()) return line;
        return line.substring(0, match.start()) + String.format(Locale.ROOT, "moveTo(%.3f,%s,%.3f)",
                Double.parseDouble(match.group(1)) + 0.5D, match.group(2), Double.parseDouble(match.group(3)) + 0.5D)
                + line.substring(match.end());
    }

    /** A to B after a 100-tick wait (seven waits, the eighth check moves on), then B back to its last link C. */
    private static List<String> traverse(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos b = helper.absolutePos(new BlockPos(7, 2, 2));
        BlockPos c = helper.absolutePos(new BlockPos(2, 2, 7));
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            TileEntityWaypoint first = place(level, a);
            TileEntityWaypoint second = place(level, b);
            place(level, c);
            first.setNextWaypoint(b);
            first.setWpStayTime(1);
            second.setNextWaypoint(a);
            second.setLastWaypoint(c);

            BasicEntityShip ship = ship(helper, entities, a);
            ship.setGuardedPos(a.getX(), a.getY(), a.getZ(), level.dimension(), 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            List<String> log = new ArrayList<>();
            MovementPlanParityGameTests.navigation(ship).recordCalls(log);
            for (int check = 1; check <= 10; check++) {
                log.add("a" + check + " " + EntityHelper.updateWaypointMove(ship) + " " + state(ship));
            }
            ship.moveTo(b.getX() + 0.5D, b.getY() + 0.5D, b.getZ() + 0.5D);
            for (int check = 1; check <= 3; check++) {
                log.add("b" + check + " " + EntityHelper.updateWaypointMove(ship) + " " + state(ship));
            }
            MovementPlanParityGameTests.navigation(ship).recordCalls(null);

            if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                helper.assertTrue(ship.lastWaypointCheck().isPresent(), "NEW must remember its last check");
                helper.assertTrue(ship.lastWaypointCheck().get().step() instanceof WaypointStep.Skip,
                        "The last check, at C, must be a skip: " + ship.lastWaypointCheck());
                helper.assertTrue(((ShipMovementHost) ship).shipMovementExecutor().last().isPresent()
                                && ((ShipMovementHost) ship).shipMovementExecutor().last().get().step().reason()
                                == MovementReason.WAYPOINT_ADVANCED,
                        "A traversal's path must leave WAYPOINT_ADVANCED in the executor: "
                                + ((ShipMovementHost) ship).shipMovementExecutor().last());
                helper.assertTrue(((ShipMovementHost) ship).shipMovementExecutor().lastLook().isEmpty(),
                        "A traversal must not turn the ship's head: "
                                + ((ShipMovementHost) ship).shipMovementExecutor().lastLook());
            }
            return log;
        }
    }

    // ---------- the stay ----------

    /** A stay begun at one point does not shorten the stay at the next point the ship is sent to. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_stay")
    public static void aStayBegunElsewhereStartsOverAtTheNextPoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos b = helper.absolutePos(new BlockPos(7, 2, 2));
        BlockPos c = helper.absolutePos(new BlockPos(2, 2, 7));
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            place(level, a).setWpStayTime(1);
            TileEntityWaypoint second = place(level, b);
            second.setWpStayTime(1);
            second.setNextWaypoint(c);
            place(level, c);
            BasicEntityShip ship = ship(helper, entities, a);
            guard(level, ship, a);
            for (int check = 1; check <= 3; check++) {
                helper.assertTrue(!EntityHelper.updateWaypointMove(ship), "Fixture: the ship must still be waiting");
            }
            helper.assertTrue(ship.getWpStayTime() == 48, "Fixture: three checks must have counted 48 ticks: "
                    + ship.getWpStayTime());
            guard(level, ship, b);
            ship.moveTo(b.getX() + 0.5D, b.getY() + 0.5D, b.getZ() + 0.5D);
            helper.assertTrue(!EntityHelper.updateWaypointMove(ship), "The ship must wait at the new point");
            helper.assertTrue(ship.getWpStayTime() == 16,
                    "A stay begun at another point must start over: stay=" + ship.getWpStayTime());
        }
        helper.succeed();
    }

    /** A detour through a point that is no waypoint ends the stay: coming back to the first point counts from 0. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_stay")
    public static void aStayStartsOverWhenTheShipIsSentAwayAndBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos plain = helper.absolutePos(new BlockPos(7, 2, 2));
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            place(level, a).setWpStayTime(1);
            BasicEntityShip ship = ship(helper, entities, a);
            guard(level, ship, a);
            for (int check = 1; check <= 3; check++) {
                helper.assertTrue(!EntityHelper.updateWaypointMove(ship), "Fixture: the ship must still be waiting");
            }
            helper.assertTrue(ship.getWpStayTime() == 48, "Fixture: three checks must have counted 48 ticks: "
                    + ship.getWpStayTime());
            guard(level, ship, plain);
            helper.assertTrue(!EntityHelper.updateWaypointMove(ship), "A point that is no waypoint is not waited at");
            guard(level, ship, a);
            helper.assertTrue(!EntityHelper.updateWaypointMove(ship), "The ship must wait at the first point again");
            helper.assertTrue(ship.getWpStayTime() == 16,
                    "A stay interrupted by another order must start over: stay=" + ship.getWpStayTime());
        }
        helper.succeed();
    }

    private static void guard(ServerLevel level, BasicEntityShip ship, BlockPos pos) {
        var id = level.dimension().location();
        ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(
                new ShipCommand.GuardPosition(new DimensionKey(id.getNamespace(), id.getPath()),
                        new CommandPos(pos.getX(), pos.getY(), pos.getZ()), false)));
    }

    // ---------- formation ----------

    /** m3: the formation members' guard targets after the flagship's traversal; this change does not touch them. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_formation")
    public static void formationMembersAreSentTheSameWayUnderEitherAuthority(GameTestHelper helper) {
        String legacy = formation(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, 71);
        String now = formation(helper, ConfigHandler.ShipAiTargetAuthority.NEW, 72);
        helper.assertTrue(now.equals(legacy), "Formation destinations differ:\n LEGACY " + legacy
                + "\n NEW    " + now);
        LogHelper.info("Waypoint formation parity: " + now);
        helper.succeed();
    }

    /** The player list's own list: {@code getPlayers()} is a read-only view, and the owner lookup reads the list. */
    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(PlayerList playerList) {
        try {
            Field field = PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            return (List<ServerPlayer>) field.get(playerList);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not register the test owner", failure);
        }
    }

    private static String formation(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode, int id) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos b = helper.absolutePos(new BlockPos(9, 2, 2));
        List<ServerPlayer> players = onlinePlayers(level.getServer().getPlayerList());
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
             PointerSingleModeGameTests.TestContext context =
                     PointerSingleModeGameTests.createContext(helper, "waypoint_formation_" + id, id)) {
            place(level, a).setNextWaypoint(b);
            place(level, b);
            BasicEntityShip flagship = PointerSingleModeGameTests.addShip(context, 0, 7100 + id,
                    new Vec3(2.5D, 2.5D, 2.5D));
            BasicEntityShip member = PointerSingleModeGameTests.addShip(context, 1, 7200 + id,
                    new Vec3(3.5D, 2.5D, 4.5D));
            List<BasicEntityShip> ships = new ArrayList<>(List.of(flagship, member));
            for (int slot = 2; slot < 5; slot++) {
                ships.add(PointerSingleModeGameTests.addShip(context, slot, 7300 + id * 10 + slot,
                        new Vec3(3.5D + slot, 2.5D, 4.5D)));
            }
            context.capa().setFormatID(0, 1);
            players.add(context.player());
            try {
                for (BasicEntityShip ship : ships) {
                    ServerDataManagerAccess.register(ship);
                    ship.setStateMinor(ID.M.FormatType, 1);
                    ship.setStateMinor(ID.M.FormatPos, ships.indexOf(ship));
                    ship.setStateMinor(ID.M.NumGrudge, 100_000);
                    ship.setStateFlag(ID.F.NoFuel, false);
                }
                flagship.setGuardedPos(a.getX(), a.getY(), a.getZ(), level.dimension(), 1);
                flagship.setStateFlag(ID.F.CanFollow, false);
                helper.assertTrue(EntityHelper.updateWaypointMove(flagship), "The flagship must advance");
                helper.assertTrue(member.getGuardedPos(0) != 0 || member.getGuardedPos(2) != 0,
                        "Fixture: the member must have been sent somewhere: " + guardFields(member));
                return "flagship=" + guardFields(flagship) + " member=" + guardFields(member);
            } finally {
                players.remove(context.player());
                for (BasicEntityShip ship : ships) {
                    ServerDataManagerAccess.unregister(ship);
                }
            }
        }
    }

    // ---------- the first steps after a command ----------

    @GameTest(template = "arena", batch = "isolated_waypoint_structure_command_ground")
    public static void guardCommandWalksTheSameOnTheGround(GameTestHelper helper) {
        commandParity(helper, false, true);
    }

    @GameTest(template = "arena", batch = "isolated_waypoint_structure_command_mounted")
    public static void guardCommandWalksTheSameOnAMount(GameTestHelper helper) {
        commandParity(helper, true, true);
    }

    @GameTest(template = "arena", batch = "isolated_waypoint_structure_move_ground")
    public static void moveCommandWalksTheSameOnTheGround(GameTestHelper helper) {
        commandParity(helper, false, false);
    }

    @GameTest(template = "arena", batch = "isolated_waypoint_structure_move_mounted")
    public static void moveCommandWalksTheSameOnAMount(GameTestHelper helper) {
        commandParity(helper, true, false);
    }

    private static void commandParity(GameTestHelper helper, boolean mounted, boolean guard) {
        String legacy = command(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, mounted, guard);
        String now = command(helper, ConfigHandler.ShipAiTargetAuthority.NEW, mounted, guard);
        helper.assertTrue(now.equals(legacy), "The command's first steps differ:\n LEGACY " + legacy
                + "\n NEW    " + now);
        helper.assertTrue(legacy.contains("moveTo("), "Fixture must request a path: " + legacy);
        LogHelper.info("Command first-step parity (mounted=" + mounted + ", guard=" + guard + "): " + now);
        helper.succeed();
    }

    private static String command(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode, boolean mounted,
                                  boolean guard) {
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 5));
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities, helper.absolutePos(new BlockPos(2, 2, 2)));
            BasicEntityMount mount = null;
            if (mounted) {
                mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
                mount.moveTo(ship.position());
                helper.getLevel().addFreshEntity(mount);
                mount.setHost(ship);
                helper.assertTrue(ship.startRiding(mount, true), "fixture must mount the ship");
            }
            Mob body = mounted ? mount : ship;
            List<String> log = new ArrayList<>();
            MovementPlanParityGameTests.navigation(body).recordCalls(log);
            if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                var id = helper.getLevel().dimension().location();
                DimensionKey dimension = new DimensionKey(id.getNamespace(), id.getPath());
                CommandPos pos = new CommandPos(target.getX(), target.getY(), target.getZ());
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(guard
                        ? new ShipCommand.GuardPosition(dimension, pos, false)
                        : new ShipCommand.Move(dimension, pos, true)));
                var executor = ((ShipMovementHost) ship).shipMovementExecutor();
                helper.assertTrue(executor.last().isPresent()
                                && executor.last().get().step() instanceof MovementStep.PathTo path
                                && path.reason() == MovementReason.COMMAND_APPLIED
                                && path.body() == (mounted ? MovementBody.VEHICLE : MovementBody.SELF),
                        "The executor's last request must be the command's path from the right body: "
                                + executor.last());
                helper.assertTrue(executor.lastLook().isPresent()
                                && executor.lastLook().get().reason() == LookReason.COMMAND_APPLIED,
                        "The head must have been asked to look by COMMAND_APPLIED: " + executor.lastLook());
            } else {
                FormationHelper.applyShipGuard(ship, target.getX(), target.getY(), target.getZ(), true,
                        guard ? 1 : 0);
            }
            MovementPlanParityGameTests.navigation(body).recordCalls(null);
            return String.join(",", log) + " guard=" + guardFields(ship) + " canFollow="
                    + ship.getStateFlag(ID.F.CanFollow) + " sit=" + ship.isOrderedToSit() + " target="
                    + ship.getTarget() + " look=" + body.getLookControl().getWantedX() + ","
                    + body.getLookControl().getWantedY() + "," + body.getLookControl().getWantedZ();
        }
    }

    // ---------- measurement ----------

    /**
     * m1: a ship whose FollowMin is large still reaches a linked waypoint B and moves on to C. The guard goal
     * stops its path at FollowMin plus the ship's width times 0.75 from the point; the traversal counts a ship
     * as arrived within three blocks of the centre. B has a next point, so passing B shows in the guard target.
     */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_m1", timeoutTicks = 260)
    public static void defaultFollowMinTraverses(GameTestHelper helper) {
        traverseWithFollowMin(helper, 2, 9, 1);
    }

    /** FollowMin 4 and 8 with the first layout: both pass the middle point, and a stop short of it is no escape. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_m1_large", timeoutTicks = 260)
    public static void largeFollowMinArrivesAtTheNextWaypoint(GameTestHelper helper) {
        traverseWithFollowMin(helper, 2, 9, 4, 8);
    }

    private static void traverseWithFollowMin(GameTestHelper helper, int firstX, int secondX, int... followMins) {
        ServerLevel level = helper.getLevel();
        ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
        GameTestEntities entities;
        try {
            entities = GameTestEntities.open(helper);
        } catch (RuntimeException | Error failure) {
            authority.close();
            throw failure;
        }
        Runnable close = () -> {
            try {
                entities.close();
            } finally {
                authority.close();
            }
        };
        try {
            BasicEntityShip[] ships = new BasicEntityShip[followMins.length];
            BlockPos[] seconds = new BlockPos[followMins.length];
            BlockPos[] thirds = new BlockPos[followMins.length];
            for (int i = 0; i < followMins.length; i++) {
                BlockPos first = helper.absolutePos(new BlockPos(firstX, 1, 1 + 4 * i));
                seconds[i] = helper.absolutePos(new BlockPos(secondX, 1, 1 + 4 * i));
                place(level, first).setNextWaypoint(seconds[i]);
                BlockPos third = thirds[i] = helper.absolutePos(new BlockPos(firstX, 1, 3 + 4 * i));
                place(level, seconds[i]).setNextWaypoint(third);
                place(level, third);
                ships[i] = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(5.5D, 1D, 2.5D + 4 * i));
                ships[i].setStateMinor(ID.M.FollowMin, followMins[i]);
                ships[i].setStateMinor(ID.M.FollowMax, followMins[i] + 1);
            }
            helper.runAtTickTime(20, () -> step(close, () -> {
                for (int i = 0; i < ships.length; i++) {
                    BlockPos first = helper.absolutePos(new BlockPos(firstX, 1, 1 + 4 * i));
                    var id = level.dimension().location();
                    ships[i].applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                            new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                    new DimensionKey(id.getNamespace(), id.getPath()),
                                    new CommandPos(first.getX(), first.getY(), first.getZ()), false)));
                }
            }));
            helper.runAtTickTime(240, () -> {
                try {
                    StringBuilder report = new StringBuilder();
                    boolean allAdvanced = true;
                    for (int i = 0; i < ships.length; i++) {
                        boolean advanced = ships[i].getGuardedPos(0) == thirds[i].getX()
                                && ships[i].getGuardedPos(2) == thirds[i].getZ();
                        allAdvanced &= advanced;
                        report.append(" FollowMin=").append(followMins[i]).append(" passedB=").append(advanced)
                                .append(" distSq=").append(String.format("%.2f",
                                        ships[i].distanceToSqr(Vec3.atCenterOf(seconds[i]))))
                                .append(" guard=").append(guardFields(ships[i])).append(" last=")
                                .append(ships[i].lastWaypointCheck().map(Object::toString).orElse("none"))
                                .append(';');
                    }
                    LogHelper.info("Waypoint m1:" + report);
                    helper.assertTrue(allAdvanced, "A ship did not advance:" + report);
                    helper.succeed();
                } finally {
                    close.run();
                }
            });
        } catch (Throwable error) {
            close.run();
            throw error;
        }
    }

    /**
     * FollowMin 4 and 5 with the linked points nine blocks apart: the guard goal stops its path short of the
     * arrival range at the middle point, and the route must go on to the last point all the same.
     */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_m1_through", timeoutTicks = 260)
    public static void largeFollowMinPassesTheMiddleWaypointWhateverTheSpacing(GameTestHelper helper) {
        traverseWithFollowMin(helper, 1, 10, 4, 5);
    }

    // ---------- a ship that is near the middle point, or at the last one, without a path ----------

    /**
     * A ship that stands inside the guard goal's start distance but outside the arrival range of a middle
     * point, with no path (a wander step or a knock replaced it), goes back to the point and moves on.
     */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_stand_through", timeoutTicks = 260)
    public static void shipStandingNearTheMiddleWaypointGoesBackToIt(GameTestHelper helper) {
        standNearWaypoint(helper, true, 8, 6);
    }

    /** The last point is not a point to pass: a ship near it keeps standing where it is. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_stand_end", timeoutTicks = 260)
    public static void shipStandingNearTheLastWaypointStaysWhereItIs(GameTestHelper helper) {
        standNearWaypoint(helper, false, 4, 4);
    }

    private static void standNearWaypoint(GameTestHelper helper, boolean linked, int followMin, int blocks) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = Fixture.open(helper);
        try {
            BlockPos middle = helper.absolutePos(new BlockPos(9, 1, 3));
            BlockPos last = helper.absolutePos(new BlockPos(9, 1, 5));
            TileEntityWaypoint block = place(level, middle);
            if (linked) {
                block.setNextWaypoint(last);
                place(level, last);
            }
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, fixture.entities,
                    new Vec3(9.5D - blocks, 1D, 3.5D));
            ship.setStateMinor(ID.M.FollowMin, followMin);
            ship.setStateMinor(ID.M.FollowMax, followMin + 1);
            helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == followMin
                            && ship.getStateMinor(ID.M.FollowMax) == followMin + 1,
                    "Fixture: the ship's FollowMin and FollowMax must be set");
            helper.onEachTick(() -> fixture.step(() -> MovementPlanParityGameTests.selector(ship)
                    .removeAllGoals(goal -> goal instanceof ShipWanderGoal)));
            helper.runAtTickTime(30, () -> fixture.step(() -> {
                var id = level.dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(middle.getX(), middle.getY(), middle.getZ()), false)));
            }));
            helper.runAtTickTime(31, () -> fixture.step(() -> {
                ship.getNavigation().stop();
                helper.assertTrue(ship.distanceToSqr(Vec3.atCenterOf(middle)) > 9D,
                        "Fixture: the ship must stand outside the arrival range");
            }));
            helper.runAtTickTime(240, () -> {
                try {
                    String report = " guard=" + guardFields(ship) + " distSq="
                            + String.format(Locale.ROOT, "%.2f", ship.distanceToSqr(Vec3.atCenterOf(middle)))
                            + " last=" + ship.lastWaypointCheck().map(Object::toString).orElse("none");
                    if (linked) {
                        helper.assertTrue(ship.getGuardedPos(0) == last.getX() && ship.getGuardedPos(2) == last.getZ(),
                                "The ship did not go back to the middle point and on:" + report);
                    } else {
                        helper.assertTrue(ship.getGuardedPos(0) == middle.getX()
                                        && ship.distanceToSqr(Vec3.atCenterOf(middle)) > 9D,
                                "The ship must keep standing near the last point:" + report);
                    }
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (Throwable error) {
            fixture.close();
            throw error;
        }
    }

    /**
     * A ship that picks items up is called away from the middle point to an item eight blocks off. Once it
     * has the item the route must go on, and the guard goal must not pull it back before that.
     */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_pick_through", timeoutTicks = 420)
    public static void pickingShipGoesBackToTheMiddleWaypointAndMovesOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = Fixture.open(helper);
        try {
            BlockPos middle = helper.absolutePos(new BlockPos(2, 1, 3));
            BlockPos last = helper.absolutePos(new BlockPos(2, 1, 5));
            TileEntityWaypoint block = place(level, middle);
            block.setNextWaypoint(last);
            block.setWpStayTime(1);
            place(level, last);
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, fixture.entities,
                    new Vec3(2.5D, 1D, 3.5D));
            ship.setStateFlag(ID.F.PickItem, true);
            ship.setStateMinor(ID.M.FollowMin, 5);
            ship.setStateMinor(ID.M.FollowMax, 6);
            helper.assertTrue(ship.getStateFlag(ID.F.PickItem) && ship.getStateMinor(ID.M.FollowMin) == 5
                            && ship.getStateMinor(ID.M.FollowMax) == 6,
                    "Fixture: the ship must pick items up, with FollowMin 5 and FollowMax 6");
            ItemEntity[] item = new ItemEntity[1];
            helper.onEachTick(() -> fixture.step(() -> MovementPlanParityGameTests.selector(ship)
                    .removeAllGoals(goal -> goal instanceof ShipWanderGoal)));
            helper.runAtTickTime(30, () -> fixture.step(() -> {
                var id = level.dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(middle.getX(), middle.getY(), middle.getZ()), false)));
                Vec3 at = MovementPlanParityGameTests.ground(helper, new Vec3(10.5D, 1D, 3.5D));
                item[0] = fixture.entities.add(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE)));
                item[0].setNoPickUpDelay();
                item[0].setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(level.addFreshEntity(item[0]), "Fixture: the item must be added");
            }));
            helper.runAtTickTime(160, () -> fixture.step(() -> helper.assertTrue(!item[0].isAlive(),
                    "The ship did not pick the item up: guard=" + guardFields(ship) + " distSq="
                            + String.format(Locale.ROOT, "%.2f", ship.distanceToSqr(Vec3.atCenterOf(middle))))));
            helper.runAtTickTime(400, () -> {
                try {
                    helper.assertTrue(ship.getGuardedPos(0) == last.getX() && ship.getGuardedPos(2) == last.getZ(),
                            "The ship did not go back to the middle point and on: guard=" + guardFields(ship)
                                    + " distSq=" + String.format(Locale.ROOT, "%.2f",
                                    ship.distanceToSqr(Vec3.atCenterOf(middle)))
                                    + " last=" + ship.lastWaypointCheck().map(Object::toString).orElse("none"));
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (Throwable error) {
            fixture.close();
            throw error;
        }
    }

    /** A ship with full usable cargo ignores drops and keeps patrolling. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_pick_full", timeoutTicks = 520)
    public static void pickingShipWithFullCargoMovesOnFromTheMiddleWaypoint(GameTestHelper helper) {
        passByAnItemItCannotTake(helper, false, false);
    }

    /** Two items under a permanent pickup delay are both given up on; the route goes on. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_pick_two", timeoutTicks = 820)
    public static void pickingShipMovesOnPastTwoItemsUnderDelay(GameTestHelper helper) {
        passByAnItemItCannotTake(helper, true, true);
    }

    /** An item that stays under a pickup delay is given up on; the route goes on. */
    @GameTest(template = "arena", batch = "isolated_waypoint_structure_pick_delay", timeoutTicks = 520)
    public static void pickingShipMovesOnFromTheMiddleWaypointPastAnItemUnderDelay(GameTestHelper helper) {
        passByAnItemItCannotTake(helper, true, false);
    }

    private static void passByAnItemItCannotTake(GameTestHelper helper, boolean delayed, boolean twoItems) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = Fixture.open(helper);
        try {
            BlockPos middle = helper.absolutePos(new BlockPos(2, 1, 3));
            BlockPos last = helper.absolutePos(new BlockPos(2, 1, 5));
            TileEntityWaypoint block = place(level, middle);
            block.setNextWaypoint(last);
            block.setWpStayTime(1);
            place(level, last);
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, fixture.entities,
                    new Vec3(2.5D, 1D, 3.5D));
            ship.setInventoryPageSize(0);
            ship.setStateFlag(ID.F.PickItem, true);
            ship.setStateMinor(ID.M.FollowMin, 5);
            ship.setStateMinor(ID.M.FollowMax, 6);
            ItemEntity[] item = new ItemEntity[2];
            helper.onEachTick(() -> fixture.step(() -> MovementPlanParityGameTests.selector(ship)
                    .removeAllGoals(goal -> goal instanceof ShipWanderGoal)));
            helper.runAtTickTime(30, () -> fixture.step(() -> {
                helper.assertTrue(ship.tickCount >= 24 && ship.getInventoryPageSize() == 0
                                && ship.getStateMinor(ID.M.FollowMin) == 5 && ship.getStateMinor(ID.M.FollowMax) == 6
                                && !ship.getStateFlag(ID.F.NoFuel) && ship.getStateMinor(ID.M.NumGrudge) > 0,
                        "Fixture: registered, fuelled, no extra pages, FollowMin 5 and FollowMax 6");
                if (!delayed) {
                    for (int slot = 6; slot < 24; slot++) {
                        ship.getCapaShipInventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
                    }
                    helper.assertTrue(!ship.getCapaShipInventory().addItemStackToInventory(new ItemStack(Items.DIAMOND)),
                            "Fixture: the item must not fit into the usable cargo");
                }
                var id = level.dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(middle.getX(), middle.getY(), middle.getZ()), false)));
                Vec3 at = MovementPlanParityGameTests.ground(helper, new Vec3(10.5D, 1D, 3.5D));
                item[0] = fixture.entities.add(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.DIAMOND)));
                if (delayed) item[0].setNeverPickUp();
                else item[0].setNoPickUpDelay();
                item[0].setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(level.addFreshEntity(item[0]), "Fixture: the item must be added");
                if (twoItems) {
                    item[1] = fixture.entities.add(new ItemEntity(level, at.x, at.y, at.z + 0.5D,
                            new ItemStack(Items.EMERALD)));
                    if (delayed) item[1].setNeverPickUp();
                    else item[1].setNoPickUpDelay();
                    item[1].setDeltaMovement(Vec3.ZERO);
                    helper.assertTrue(level.addFreshEntity(item[1]), "Fixture: the second item must be added");
                }
            }));
            helper.runAtTickTime(60, () -> fixture.step(() -> {
                helper.assertTrue(item[0].isAlive(), "An item the ship cannot take must remain in the world");
                if (delayed) {
                    helper.assertTrue(ship.isPickingItem() && ship.distanceToSqr(item[0]) < 9D
                                    && ship.distanceToSqr(Vec3.atCenterOf(middle)) > 9D,
                            "Fixture: the ship must be at the delayed item, outside the point: picking="
                                    + ship.isPickingItem() + " itemSq=" + String.format(Locale.ROOT, "%.2f",
                                    ship.distanceToSqr(item[0])));
                } else {
                    helper.assertTrue(ship.getCapaShipInventory().getFirstSlotForItem() == -1
                                    && !ship.isPickingItem(),
                            "Full usable cargo must not start pickup through an empty locked page");
                }
            }));
            helper.runAtTickTime(twoItems ? 800 : 500, () -> {
                try {
                    helper.assertTrue(ship.getGuardedPos(0) == last.getX() && ship.getGuardedPos(2) == last.getZ(),
                            "An item it cannot take held the route: guard=" + guardFields(ship)
                                    + " picking=" + ship.isPickingItem() + " distSq=" + String.format(Locale.ROOT,
                                    "%.2f", ship.distanceToSqr(Vec3.atCenterOf(middle))));
                    helper.assertTrue(item[0].isAlive() && (!twoItems || item[1].isAlive()),
                            "Items that cannot be taken must remain after the route advances");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (Throwable error) {
            fixture.close();
            throw error;
        }
    }

    /** The entities and the authority a test holds until it ends. */
    private static final class Fixture {
        private final GameTestEntities entities;
        private final ShipAiAuthorityOverride authority;
        private boolean closed;

        private Fixture(GameTestEntities entities, ShipAiAuthorityOverride authority) {
            this.entities = entities;
            this.authority = authority;
        }

        static Fixture open(GameTestHelper helper) {
            ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
            try {
                return new Fixture(GameTestEntities.open(helper), authority);
            } catch (RuntimeException | Error failure) {
                authority.close();
                throw failure;
            }
        }

        void step(Runnable action) {
            try {
                action.run();
            } catch (Throwable error) {
                this.close();
                throw error;
            }
        }

        void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            try {
                this.entities.close();
            } finally {
                this.authority.close();
            }
        }
    }

    private static void step(Runnable close, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            close.run();
            throw error;
        }
    }

    // ---------- fixtures ----------

    static TileEntityWaypoint place(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof TileEntityWaypoint waypoint)) {
            throw new AssertionError("Waypoint block did not create its block entity.");
        }
        return waypoint;
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities, BlockPos at) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "failed to create ship");
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateMinor(ID.M.FormatType, 0);
        ship.moveTo(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "failed to add ship");
        return ship;
    }

    private static String state(BasicEntityShip ship) {
        return "stay=" + ship.getWpStayTime() + " guard=" + guardFields(ship) + " last="
                + (ship.hasLastWaypoint() ? ship.getLastWaypoint().toShortString() : "none");
    }

    private static String guardFields(BasicEntityShip ship) {
        return ship.getGuardedPos(0) + "," + ship.getGuardedPos(1) + "," + ship.getGuardedPos(2) + "/"
                + ship.getGuardedPos(4);
    }
}

final class ServerDataManagerAccess {
    private ServerDataManagerAccess() {
    }

    static void register(BasicEntityShip ship) {
        ServerDataManager.setShipWorldData(ship.getShipUID(), new CacheDataShip(ship.getId(),
                ship.level().dimension(), 0, false, ship.getX(), ship.getY(), ship.getZ(), new CompoundTag()));
    }

    static void unregister(BasicEntityShip ship) {
        ServerDataManager.removeShipData(ship.getShipUID());
    }
}
