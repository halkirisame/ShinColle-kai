package com.lulan.shincolle.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision;
import com.lulan.shincolle.ai.path.ShipMoveControl;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShoreLandingGameTests {
    private ShoreLandingGameTests() {
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_mount_one", timeoutTicks = 420)
    public static void harbourMountLandsOnOneBlockShore(GameTestHelper helper) {
        start(helper, "mount-one", 1, true, false, false, false, 0);
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_mount_three", timeoutTicks = 420)
    public static void harbourMountLandsOnThreeBlockShore(GameTestHelper helper) {
        start(helper, "mount-three", 3, true, false, false, false, 0);
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_body_one", timeoutTicks = 420)
    public static void destroyerLandsOnOneBlockShore(GameTestHelper helper) {
        start(helper, "body-one", 1, false, false, false, false, 0);
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_follow", timeoutTicks = 420)
    public static void destroyerFollowsOwnerOntoShore(GameTestHelper helper) {
        start(helper, "body-follow", 1, false, false, true, false, 0);
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_too_high", timeoutTicks = 420)
    public static void destroyerRejectsTallShoreAndRecovers(GameTestHelper helper) {
        start(helper, "body-too-high", 2, false, false, false, false, 0);
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_hostile_one", timeoutTicks = 420)
    public static void hostileLandsOnOneBlockShore(GameTestHelper helper) {
        start(helper, "hostile-one", 1, false, true, false, false, 0);
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_water_target", timeoutTicks = 420)
    public static void waterTargetDoesNotLiftAlongWall(GameTestHelper helper) {
        start(helper, "water-target", 1, false, false, false, true, 0);
    }

    @GameTest(template = "mount_shore_arena", batch = "shore_hostile_two", timeoutTicks = 420)
    public static void scaledHostileLandsOnTwoBlockShore(GameTestHelper helper) {
        start(helper, "hostile-two", 2, false, true, false, false, 1);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileStepHeightTracksScaleAndSavedScale(GameTestHelper helper) {
        try (var entities = GameTestEntities.open(helper)) {
            StringBuilder failures = new StringBuilder();
            for (int scale = 0; scale <= 3; scale++) {
                var ship = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                ship.setScaleLevel(scale);
                helper.assertTrue(ship.getScaleLevel() == scale, "Scale fixture must be accepted");
                CompoundTag saved = new CompoundTag();
                ship.addAdditionalSaveData(saved);
                var restored = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                restored.readAdditionalSaveData(saved);
                helper.assertTrue(restored.getScaleLevel() == scale, "Saved scale must be restored");
                if (ship.maxUpStep() != 1 + scale || restored.maxUpStep() != 1 + scale) {
                    failures.append("scale=").append(scale).append(" direct=").append(ship.maxUpStep())
                            .append(" saved=").append(restored.maxUpStep()).append("; ");
                }
            }
            helper.assertTrue(failures.isEmpty(), "Hostile step height must be 1 + scale: " + failures);
            helper.succeed();
        }
    }

    private static void start(GameTestHelper helper, String name, int height, boolean mounted, boolean hostile,
                              boolean follow, boolean waterTarget, int scale) {
        List<Vec3> route = new ArrayList<>();
        for (int x : new int[]{0, 8, 16, 24, 26}) {
            for (int z : new int[]{0, 6, 12}) route.add(new Vec3(x, 8, z));
        }
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper, name, height, mounted, hostile, follow, waterTarget, scale, route);
            helper.onEachTick(scene::poll);
        }, route.toArray(Vec3[]::new));
    }

    private static Object field(Class<?> type, Object object, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect " + name + ": " + error);
        }
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final String name;
        private final int height;
        private final boolean follow;
        private final boolean waterTarget;
        private final boolean rejectTall;
        private final List<Vec3> route;
        private final BasicEntityShip ship;
        private final Mob body;
        private final double shoreX;
        private final double shoreY;
        private final double waterY;
        private FakePlayer owner;
        private int commandedAt = -1;
        private int previousTick;
        private Vec3 previousPosition;
        private boolean done;
        private boolean sawRefusal;
        private boolean sawRecovery;
        private double highest;
        private final JsonArray observations = new JsonArray();
        private int suppliedPaths;

        Scene(GameTestHelper helper, String name, int height, boolean mounted, boolean hostile, boolean follow,
              boolean waterTarget, int scale, List<Vec3> route) {
            this.helper = helper;
            this.name = name;
            this.height = height;
            this.follow = follow;
            this.waterTarget = waterTarget;
            this.rejectTall = !hostile && !mounted && height > 1;
            this.route = route;
            for (int x = 0; x <= 26; x++) {
                for (int z = 0; z <= 12; z++) {
                    for (int y = 4; y <= 13; y++) {
                        boolean wall = x == 0 || x == 26 || z == 0 || z == 12;
                        int wallHeight = this.rejectTall ? 6 + height : 7;
                        helper.setBlock(new BlockPos(x, y, z),
                                y == 4 || (wall && y <= wallHeight) || (x >= 12 && y <= 6 + height)
                                        ? Blocks.STONE : y <= 6 ? Blocks.WATER : Blocks.AIR);
                    }
                }
            }
            this.shoreX = helper.absolutePos(new BlockPos(12, 7, 6)).getX();
            this.waterY = helper.absolutePos(new BlockPos(11, 7, 6)).getY();
            this.shoreY = this.waterY + height;
            helper.assertTrue(!this.rejectTall || helper.getBlockState(new BlockPos(11, 6 + height, 0)).is(Blocks.STONE),
                    "Tall shore fixture must not provide a lower perimeter escape");
            var entities = GameTestEntities.open(helper);
            if (hostile) {
                this.ship = null;
                var enemy = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                enemy.setScaleLevel(scale);
                this.body = enemy;
                GameTestEntities.removeGoalsEachTick(helper, enemy, goal -> !(goal instanceof com.lulan.shincolle.ai.ShipFloatingGoal));
            } else {
                this.ship = entities.add(mounted ? ModEntities.HARBOUR_HIME.get().create(helper.getLevel())
                        : ModEntities.DD_HIME.get().create(helper.getLevel()));
                this.ship.setOwnerUUID(UUID.randomUUID());
                this.ship.setInvulnerable(true);
                this.ship.setEntitySit(false);
                this.ship.setStateMinor(ID.M.NumGrudge, 100_000);
                this.ship.setStateFlag(ID.F.NoFuel, false);
                this.ship.setStateEmotion(ID.S.State, mounted ? 1 : 0, false);
                this.ship.setStateFlag(ID.F.PickItem, false);
                this.ship.setStateFlag(ID.F.PassiveAI, true);
                this.ship.setStateMinor(ID.M.FormatType, 0);
                this.ship.setStateMinor(ID.M.FollowMin, 2);
                this.ship.setStateMinor(ID.M.FollowMax, 6);
                this.ship.setMorale(3000);
                this.ship.calcShipAttributes(31, false);
                this.ship.setHealth(this.ship.getMaxHealth());
                if (mounted) {
                    var mount = entities.add(ModEntities.MOUNT_HBH.get().create(helper.getLevel()));
                    mount.setHost(this.ship);
                    this.body = mount;
                } else this.body = this.ship;
                GameTestEntities.removeGoalsEachTick(helper, this.ship, goal -> goal instanceof ShipWanderGoal);
            }
            this.body.setInvulnerable(true);
            this.body.moveTo(helper.absoluteVec(new Vec3(12 - this.body.getBbWidth() / 2.0, 7, 6.5)));
            this.body.setYRot(-90);
            helper.assertTrue(helper.getLevel().addFreshEntity(this.body), "Body must spawn");
            if (mounted) {
                this.ship.moveTo(this.body.position());
                helper.assertTrue(helper.getLevel().addFreshEntity(this.ship), "Rider must spawn");
                helper.assertTrue(this.ship.canSummonMounts() && this.ship.startRiding(this.body, true),
                        "Mount toggle and rider must be established");
            }
        }

        void poll() {
            if (this.done) return;
            if (this.commandedAt < 0) {
                if (this.body.tickCount < 24 || (this.ship != null && this.ship.tickCount < 24)) return;
                GameTestEntities.assertRegistered(this.helper, this.body);
                this.helper.assertTrue(this.body.getMoveControl() instanceof ShipMoveControl && !this.body.isNoAi(),
                        "Body must have active initialized ship movement");
                if (this.ship != null) {
                    this.helper.assertTrue(!this.ship.getStateFlag(ID.F.NoFuel)
                                    && this.ship.getStateMinor(ID.M.NumGrudge) > 0 && this.ship.getMorale() >= ID.Morale.L_Normal
                                    && this.ship.getStateMinor(ID.M.FollowMin) == 2
                                    && this.ship.getStateMinor(ID.M.FollowMax) == 6,
                            "Fuel, morale and follow distances must be accepted");
                }
                if (this.follow) {
                    this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                            new GameProfile(UUID.randomUUID(), "shore_owner"));
                    this.owner.moveTo(this.helper.absoluteVec(new Vec3(21.5, 7 + this.height, 6.5)));
                    this.helper.getLevel().addNewPlayer(this.owner);
                    this.ship.setOwnerUUID(this.owner.getUUID());
                    GameTestEntities.onCompletion(this.helper, () -> this.helper.getLevel()
                            .removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED), "shore owner");
                    this.helper.assertTrue(this.ship.getOwner() == this.owner, "Follow must resolve its owner");
                } else if (this.ship != null) {
                    BlockPos destination = this.helper.absolutePos(this.waterTarget ? new BlockPos(11, 7, 10)
                            : new BlockPos(21, 7 + this.height, 6));
                    var dim = this.helper.getLevel().dimension().location();
                    this.ship.applyCommandState(new CommandIssuer.Player(this.ship.getOwnerUUID()),
                            new CommandStateOp.Apply(new ShipCommand.Move(new DimensionKey(dim.getNamespace(), dim.getPath()),
                                    new CommandPos(destination.getX(), destination.getY(), destination.getZ()), true)));
                    this.helper.assertTrue(this.ship.getCommandState().movement() instanceof MovementOrder.MoveTo,
                            "Move command must be accepted");
                } else {
                    Vec3 target = this.helper.absoluteVec(new Vec3(21.5, 7 + this.height, 6.5));
                    ShipMovementExecutor.run(this.body, MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF,
                            new MovementTarget.Point(new MovementPoint(target.x, target.y, target.z)), 1, MovementReason.WANDER)));
                }
                this.helper.assertTrue(this.follow || this.body.getNavigation().getPath() != null,
                        "The request must produce a path");
                this.supplyExecutionPath();
                this.commandedAt = this.body.tickCount;
                this.previousTick = this.body.tickCount;
                this.previousPosition = this.body.position();
                this.highest = this.body.getY();
                return;
            }
            this.helper.assertTrue(this.body.tickCount == this.previousTick + 1, "Only natural ticks may advance the body");
            this.previousTick = this.body.tickCount;
            this.supplyExecutionPath();
            for (Vec3 point : this.route) this.helper.assertTrue(this.helper.getLevel().isPositionEntityTicking(
                    BlockPos.containing(this.helper.absoluteVec(point))), "Entire movement route must keep ticking");
            if (this.body instanceof BasicEntityMount) this.helper.assertTrue(this.ship.getVehicle() == this.body,
                    "The ship must remain aboard throughout landing");
            this.helper.assertTrue(this.previousPosition.distanceTo(this.body.position()) < 2
                            && !(boolean) field(ShipMovementExecutor.class, executor(), "teleported"),
                    "Landing cannot use teleportation or a position jump");
            this.previousPosition = this.body.position();
            this.highest = Math.max(this.highest, this.body.getY());
            var decision = ((ShipMoveControl) this.body.getMoveControl()).shoreLandingDecision();
                this.sawRefusal |= decision.reasons().contains(this.waterTarget
                        ? ShoreLandingDecision.Reason.TARGET_NOT_ON_LEDGE : ShoreLandingDecision.Reason.LEDGE_TOO_HIGH);
                if (this.waterTarget || this.rejectTall) this.helper.assertTrue(
                        decision.action() != ShoreLandingDecision.Action.LIFT, "Rejected shore must never request lift");
            JsonObject observation = new JsonObject();
            observation.addProperty("tick", this.body.tickCount);
            observation.addProperty("position", this.body.position().toString());
            observation.addProperty("wanted", new Vec3(this.body.getMoveControl().getWantedX(),
                    this.body.getMoveControl().getWantedY(), this.body.getMoveControl().getWantedZ()).toString());
            observation.addProperty("pathDone", this.body.getNavigation().isDone());
            observation.addProperty("collision", this.body.horizontalCollision);
            observation.addProperty("action", decision.action().name());
            observation.addProperty("reasons", decision.reasons().toString());
            this.observations.add(observation);
            var last = executor().last();
            this.sawRecovery |= last.filter(request -> request.step() instanceof MovementStep.PathTo path
                    && (path.reason().name().startsWith("STUCK_") || path.reason().name().contains("DETOUR"))).isPresent();
            boolean landed = this.body.getY() >= this.shoreY - 0.01
                    && this.body.getBoundingBox().minX >= this.shoreX && this.body.onGround();
            boolean negative = this.waterTarget || this.rejectTall;
            if ((!negative && landed) || this.body.tickCount - this.commandedAt >= 160) {
                this.done = true;
                JsonObject result = new JsonObject();
                result.addProperty("bodyTick", this.body.tickCount);
                result.addProperty("commandTick", this.commandedAt);
                result.addProperty("landed", landed);
                result.addProperty("x", this.body.getX());
                result.addProperty("y", this.body.getY());
                result.addProperty("shoreY", this.shoreY);
                result.addProperty("highest", this.highest);
                result.addProperty("onGround", this.body.onGround());
                result.addProperty("horizontalCollision", this.body.horizontalCollision);
                result.addProperty("maxUpStep", this.body.maxUpStep());
                result.addProperty("sawRefusal", this.sawRefusal);
                result.addProperty("sawRecovery", this.sawRecovery);
                result.addProperty("suppliedExecutionPaths", this.suppliedPaths);
                String summary = result.toString();
                result.add("observations", this.observations);
                try {
                    Files.writeString(Path.of("build/shore-" + this.name + ".json"), result.toString());
                } catch (IOException error) {
                    throw new GameTestAssertException("Cannot save shore measurement: " + error);
                }
                this.helper.assertTrue(negative ? this.sawRefusal && (this.waterTarget
                                ? this.highest <= this.waterY + 1.01 : this.sawRecovery) : landed,
                        "Shore expectation not reached: " + summary);
                this.helper.succeed();
            }
        }

        private ShipMovementExecutor executor() {
            if (this.body instanceof BasicEntityMount mount) return mount.shipMovementExecutor();
            if (this.body instanceof BasicEntityShip friendly) return friendly.shipMovementExecutor();
            return ((BasicEntityShipHostile) this.body).shipMovementExecutor();
        }

        private void supplyExecutionPath() {
            if (this.body instanceof BasicEntityMount) return;
            var request = this.executor().last();
            if (request.isEmpty() || !(request.get().step() instanceof MovementStep.PathTo)) return;
            var path = this.body.getNavigation().getPath();
            if (this.rejectTall) this.body.getNavigation().setSpeedModifier(0.1);
            if (this.waterTarget ? this.suppliedPaths > 0 : path != null && path.canReach()) return;
            // Isolate path execution from amphibious path generation's submerged start nodes.
            // Requests still come from the real command/follow/executor, and all ticks remain natural.
            BlockPos front = this.helper.absolutePos(new BlockPos(14, this.waterTarget ? 7 : 7 + this.height, 6));
            BlockPos target = this.helper.absolutePos(this.waterTarget ? new BlockPos(11, 7, 10)
                    : new BlockPos(21, 7 + this.height, 6));
            var supplied = new net.minecraft.world.level.pathfinder.Path(List.of(
                    new Node(front.getX(), front.getY(), front.getZ()),
                    new Node(target.getX(), target.getY(), target.getZ())), target, true);
            double speed = this.rejectTall ? 0.1 : 1;
            this.helper.assertTrue(this.body.getNavigation().moveTo(supplied, speed), "Execution fixture path must be accepted");
            this.suppliedPaths++;
        }
    }
}
