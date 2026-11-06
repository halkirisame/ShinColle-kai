package com.lulan.shincolle.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision;
import com.lulan.shincolle.ai.path.ShipMoveControl;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.TreeSet;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShorePathConnectionGameTests {
    private ShorePathConnectionGameTests() {
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_reaches", timeoutTicks = 500)
    public static void hibikiNormalSearchReachesOneBlockBank(GameTestHelper helper) {
        start(helper, "search", Case.SEARCH);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_move", timeoutTicks = 500)
    public static void hibikiNormalMoveToLandsWithoutPathInjection(GameTestHelper helper) {
        start(helper, "move", Case.MOVE);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_follow", timeoutTicks = 500)
    public static void hibikiNormalFollowLandsWithoutPathInjection(GameTestHelper helper) {
        start(helper, "follow", Case.FOLLOW);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_high", timeoutTicks = 500)
    public static void normalSearchDoesNotConnectTooHighBank(GameTestHelper helper) {
        start(helper, "high", Case.HIGH);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_headroom", timeoutTicks = 500)
    public static void normalSearchRejectsLowBankHeadroom(GameTestHelper helper) {
        start(helper, "headroom", Case.HEADROOM);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_ascent", timeoutTicks = 500)
    public static void normalSearchRejectsBlockedWaterAscent(GameTestHelper helper) {
        start(helper, "ascent", Case.ASCENT);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_fence", timeoutTicks = 500)
    public static void normalSearchDoesNotRelabelFenceAsWalkable(GameTestHelper helper) {
        start(helper, "fence", Case.FENCE);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_malus", timeoutTicks = 500)
    public static void normalSearchPreservesNegativeWalkableMalus(GameTestHelper helper) {
        start(helper, "malus", Case.MALUS);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_water", timeoutTicks = 500)
    public static void normalWaterTargetMovesWithoutLandingLift(GameTestHelper helper) {
        start(helper, "water", Case.WATER);
    }

    @GameTest(template = "shore_path_arena", batch = "shore_path_parity", timeoutTicks = 500)
    public static void ordinaryLandAndSubmergedNeighborsMatchVanilla(GameTestHelper helper) {
        start(helper, "parity", Case.PARITY);
    }

    private enum Case { SEARCH, MOVE, FOLLOW, HIGH, HEADROOM, ASCENT, FENCE, MALUS, WATER, PARITY }

    private static void start(GameTestHelper helper, String name, Case scenario) {
        List<Vec3> route = new ArrayList<>();
        for (int x : new int[]{10, 18, 26, 34, 42, 46}) {
            for (int z : new int[]{0, 4, 8}) route.add(new Vec3(x, 6, z));
        }
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper, name, scenario, route);
            helper.onEachTick(scene::poll);
        }, route.toArray(Vec3[]::new));
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final String name;
        private final Case scenario;
        private final List<Vec3> route;
        private final BasicEntityShip ship;
        private final JsonObject result = new JsonObject();
        private final JsonArray samples = new JsonArray();
        private FakePlayer owner;
        private int commandTick = -1;
        private int previousTick;
        private Vec3 previousPosition;
        private boolean done;
        private boolean sawLift;
        private boolean initialCanReach;

        Scene(GameTestHelper helper, String name, Case scenario, List<Vec3> route) {
            this.helper = helper;
            this.name = name;
            this.scenario = scenario;
            this.route = route;
            int bankTop = scenario == Case.HIGH ? 8 : 7;
            for (int x = 10; x <= 46; x++) {
                for (int z = 0; z <= 8; z++) {
                    for (int y = 4; y <= 12; y++) {
                        boolean wall = x == 10 || x == 46 || z == 0 || z == 8;
                        var block = y == 4 || wall && y <= 9 ? Blocks.GLASS
                                : x >= 27 && y < bankTop ? Blocks.STONE : y == 5 ? Blocks.WATER : Blocks.AIR;
                        if (scenario == Case.HEADROOM && x >= 27 && y == 8) block = Blocks.STONE;
                        if (scenario == Case.ASCENT && x < 27 && y == 7) block = Blocks.STONE;
                        if (scenario == Case.FENCE && x == 27 && y == 7) block = Blocks.OAK_FENCE;
                        helper.setBlock(new BlockPos(x, y, z), block);
                    }
                }
            }
            var entities = GameTestEntities.open(helper);
            this.ship = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            this.ship.setInvulnerable(true);
            this.ship.setEntitySit(false);
            this.ship.setStateMinor(ID.M.NumGrudge, 100_000);
            this.ship.setStateFlag(ID.F.NoFuel, false);
            this.ship.setStateMinor(ID.M.FollowMin, 3);
            this.ship.setStateMinor(ID.M.FollowMax, 6);
            this.ship.setStateMinor(ID.M.FormatType, 0);
            this.ship.setStateMinor(ID.M.FormatPos, 1);
            this.ship.setShipLevel(1, false);
            this.ship.setMorale(3000);
            this.ship.calcShipAttributes(31, false);
            this.ship.setHealth(this.ship.getMaxHealth());
            this.ship.setOwnerUUID(UUID.randomUUID());
            this.ship.moveTo(point(16.5, 5.1, 4.5));
            GameTestEntities.removeGoalsEachTick(helper, this.ship, goal -> goal instanceof ShipWanderGoal);
            helper.assertTrue(helper.getLevel().addFreshEntity(this.ship), "Normal Hibiki must spawn");
            // Assert the static geometry before allowing this fixture to measure navigation.
            helper.assertTrue(helper.getBlockState(new BlockPos(26, 4, 4)).is(Blocks.GLASS)
                            && helper.getBlockState(new BlockPos(26, 5, 4)).is(Blocks.WATER)
                            && helper.getBlockState(new BlockPos(27, bankTop - 1, 4)).is(Blocks.STONE)
                            && bankTop - 6 == (scenario == Case.HIGH ? 2 : 1),
                    "Glass bottom, exactly one water layer and true bank rise must match the fixture");
            this.result.addProperty("scenario", name);
            this.result.addProperty("shoreX", point(27, bankTop, 4).x);
            this.result.addProperty("shoreY", point(27, bankTop, 4).y);
            this.result.addProperty("waterTopY", point(26, 6, 4).y);
            this.result.add("samples", this.samples);
        }

        private Vec3 point(double x, double y, double z) {
            return this.helper.absoluteVec(new Vec3(x, y, z));
        }

        void poll() {
            if (this.done) return;
            if (this.commandTick < 0) {
                if (this.ship.tickCount < 24) return;
                GameTestEntities.assertRegistered(this.helper, this.ship);
                this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "shore_path_owner"));
                this.owner.moveTo(point(41.5, 7, 4.5));
                this.helper.getLevel().addNewPlayer(this.owner);
                GameTestEntities.onCompletion(this.helper, () -> this.helper.getLevel()
                        .removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED), "shore path owner");
                this.ship.setOwnerUUID(this.owner.getUUID());
                this.helper.assertTrue(this.ship.getOwner() == this.owner && this.ship.getLevel() == 1
                                && this.ship.getMorale() == 3000 && !this.ship.getStateFlag(ID.F.NoFuel)
                                && this.ship.getStateMinor(ID.M.NumGrudge) > 0 && this.ship.maxUpStep() == 1
                                && Math.abs(this.ship.getBbWidth() - 0.5) < 0.001
                                && Math.abs(this.ship.getBbHeight() - 1.6) < 0.001
                                && Math.abs(this.ship.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.502) < 0.00001,
                        "Initialized Hibiki, resolved owner, dimensions, step, fuel, morale and MOV must match runtime");
                this.ship.getNavigation().stop();
                this.ship.moveTo(point(16.5, 5.1, 4.5));
                this.ship.setDeltaMovement(Vec3.ZERO);
                this.ship.setYRot(-90);
                if (this.scenario == Case.MALUS) this.ship.setPathfindingMalus(BlockPathTypes.WALKABLE, -1);
                if (this.scenario == Case.PARITY) {
                    for (int x = 11; x <= 26; x++) {
                        for (int z = 1; z <= 7; z++) this.helper.setBlock(new BlockPos(x, 6, z), Blocks.WATER);
                    }
                    this.helper.setBlock(new BlockPos(35, 7, 4), Blocks.STONE);
                    this.helper.setBlock(new BlockPos(37, 7, 4), Blocks.STONE);
                    for (BlockPos local : List.of(new BlockPos(20, 5, 4), new BlockPos(34, 7, 4),
                            new BlockPos(35, 8, 4), new BlockPos(36, 7, 4))) {
                        BlockPos at = this.helper.absolutePos(local);
                        Set<String> expected = neighbors(new AmphibiousNodeEvaluator(false), at);
                        Set<String> actual = neighbors(this.ship.getNavigation().getNodeEvaluator(), at);
                        this.helper.assertTrue(actual.equals(expected), "Ordinary neighbors differ at " + local
                                + ": expected=" + expected + " actual=" + actual);
                    }
                    this.done = true;
                    this.helper.succeed();
                    return;
                }
                BlockPos target = BlockPos.containing(this.scenario == Case.WATER ? point(24, 5, 4)
                        : point(41, this.scenario == Case.HIGH ? 8 : 7, 4));
                Path initial = this.ship.getNavigation().createPath(target, 0);
                this.initialCanReach = initial != null && initial.canReach();
                this.result.add("initialSearch", path(initial));
                if (this.scenario != Case.MOVE && this.scenario != Case.FOLLOW && this.scenario != Case.WATER) {
                    this.done = true;
                    save();
                    this.helper.assertTrue(this.scenario == Case.SEARCH ? this.initialCanReach : !this.initialCanReach,
                            "Normal search expectation: " + this.result.get("initialSearch"));
                    if (this.scenario == Case.SEARCH) this.helper.assertTrue(initial.getEndNode().y == target.getY(),
                            "Reachable bank path must finish at its real surface height");
                    this.helper.succeed();
                    return;
                }
                if (this.scenario != Case.FOLLOW) {
                    var dim = this.helper.getLevel().dimension().location();
                    this.ship.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()), new CommandStateOp.Apply(
                            new ShipCommand.Move(new DimensionKey(dim.getNamespace(), dim.getPath()),
                                    new CommandPos(target.getX(), target.getY(), target.getZ()), false)));
                    this.helper.assertTrue(this.ship.getCommandState().movement() instanceof MovementOrder.MoveTo,
                            "Real MoveTo must be accepted");
                    this.result.add("initialCommandPath", path(this.ship.getNavigation().getPath()));
                } else this.helper.assertTrue(this.ship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "Follow must remain the normal standing order");
                this.commandTick = this.ship.tickCount;
                this.previousTick = this.ship.tickCount;
                this.previousPosition = this.ship.position();
                return;
            }
            this.helper.assertTrue(this.ship.tickCount == this.previousTick + 1, "Only natural entity ticks are allowed");
            this.helper.assertTrue(this.previousPosition.distanceTo(this.ship.position()) < 2 && !teleported(this.ship),
                    "Normal landing must not teleport or jump position");
            this.previousTick = this.ship.tickCount;
            this.previousPosition = this.ship.position();
            for (Vec3 at : this.route) this.helper.assertTrue(this.helper.getLevel().isPositionEntityTicking(
                    BlockPos.containing(this.helper.absoluteVec(at))), "Full route must stay entity ticking");
            var decision = ((ShipMoveControl) this.ship.getMoveControl()).shoreLandingDecision();
            this.sawLift |= decision.action() == ShoreLandingDecision.Action.LIFT;
            JsonObject sample = new JsonObject();
            sample.addProperty("tick", this.ship.tickCount);
            sample.addProperty("x", this.ship.getX());
            sample.addProperty("feetY", this.ship.getBoundingBox().minY);
            sample.addProperty("rearX", this.ship.getBoundingBox().minX);
            sample.addProperty("onGround", this.ship.onGround());
            sample.addProperty("lift", decision.action() == ShoreLandingDecision.Action.LIFT);
            sample.add("path", path(this.ship.getNavigation().getPath()));
            this.samples.add(sample);
            boolean landed = this.ship.getBoundingBox().minY >= point(27, 7, 4).y
                    && this.ship.getBoundingBox().minX >= point(27, 7, 4).x && this.ship.onGround();
            int elapsed = this.ship.tickCount - this.commandTick;
            if ((this.scenario != Case.WATER && landed) || elapsed >= 160) {
                this.done = true;
                this.result.addProperty("commandTick", this.commandTick);
                this.result.addProperty("elapsed", elapsed);
                this.result.addProperty("landed", landed);
                this.result.addProperty("sawLift", this.sawLift);
                save();
                this.helper.assertTrue(this.initialCanReach && (this.scenario == Case.WATER
                                ? !this.sawLift && this.ship.getX() > point(18, 5, 4).x && !landed : landed),
                        "Natural path/landing expectation: " + this.name + " initialReach=" + this.initialCanReach
                                + " elapsed=" + elapsed + " landed=" + landed + " position=" + this.ship.position());
                this.helper.succeed();
            }
        }

        private void save() {
            try {
                java.nio.file.Path directory = java.nio.file.Path.of("build", "shore-path");
                Files.createDirectories(directory);
                Files.writeString(directory.resolve(this.name + ".json"), this.result.toString());
            } catch (IOException error) {
                throw new GameTestAssertException("Cannot save normal path measurement: " + error);
            }
        }

        private Set<String> neighbors(NodeEvaluator evaluator, BlockPos at) {
            evaluator.setCanPassDoors(true);
            BlockPos min = this.helper.absolutePos(new BlockPos(0, 0, 0));
            BlockPos max = this.helper.absolutePos(new BlockPos(47, 15, 9));
            evaluator.prepare(new PathNavigationRegion(this.helper.getLevel(), min, max), this.ship);
            Node from = new Node(at.getX(), at.getY(), at.getZ());
            from.type = evaluator.getBlockPathType(this.helper.getLevel(), at.getX(), at.getY(), at.getZ(), this.ship);
            from.costMalus = this.ship.getPathfindingMalus(from.type);
            Node[] output = new Node[32];
            int count = evaluator.getNeighbors(output, from);
            Set<String> snapshot = new TreeSet<>();
            for (int i = 0; i < count; i++) snapshot.add(output[i].asBlockPos().toShortString()
                    + ":" + output[i].type + ":" + output[i].costMalus + ":" + output[i].closed);
            this.helper.assertTrue(snapshot.size() == count, "Neighbors must not contain duplicates");
            evaluator.done();
            return snapshot;
        }
    }

    private static boolean teleported(BasicEntityShip ship) {
        try {
            Field field = ShipMovementExecutor.class.getDeclaredField("teleported");
            field.setAccessible(true);
            return field.getBoolean(ship.shipMovementExecutor());
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect teleport result: " + error);
        }
    }

    private static JsonObject path(Path path) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("present", path != null);
        if (path == null) return snapshot;
        snapshot.addProperty("canReach", path.canReach());
        snapshot.addProperty("target", path.getTarget().toShortString());
        snapshot.addProperty("end", path.getEndNode() == null ? "none" : path.getEndNode().asBlockPos().toShortString());
        JsonArray nodes = new JsonArray();
        for (int i = 0; i < path.getNodeCount(); i++) nodes.add(path.getNode(i).asBlockPos().toShortString());
        snapshot.add("nodes", nodes);
        return snapshot;
    }
}
