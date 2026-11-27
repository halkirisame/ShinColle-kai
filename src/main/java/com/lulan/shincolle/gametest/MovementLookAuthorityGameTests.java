package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.LookReason;
import com.lulan.shincolle.ai.domain.movement.LookRequest;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.domain.movement.PathOutcome;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.ShipWatchClosestGoal;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Under NEW a ship that is walking to its admiral or to a guarded point and is engaged with a
 * locked target turns its head to the target, not to where it is walking. Without a target it
 * looks where it always did.
 */
@GameTestHolder(com.lulan.shincolle.reference.Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementLookAuthorityGameTests {
    /** A ship registers its goals again on its 16th tick; nothing may move it before. */
    private static final int RELEASE_AT = 24;
    /** Ticks the ship must have been engaged before its head is judged; a head turns 30 degrees a tick. */
    private static final int SETTLE = 8;
    /** How far the head may be from the bearing of what it walks to: about one tick of its turning. */
    private static final float WALK_OFF = 30F;
    /**
     * The target is behind a ship that faces where it walks, and a head turns only so far from the
     * body, so the closest it gets is what the neck leaves; this much more is allowed on top of it. The
     * body yaw drifts a few degrees as the ship walks, and which ticks its goals run on depends on its entity
     * id, so the head was measured up to 124 degrees off; a head turned to the admiral ahead is 180 off.
     */
    private static final float NECK_SLACK = 30F;
    private static final Vec3 SHIP = new Vec3(12.5D, 0D, 2.5D);
    private static final Vec3 AWAY = new Vec3(62.5D, 0D, 2.5D);
    /** Ticks the watch goal is given to start before the attack order, to show the fixture lets it run. */
    private static final int WATCH_PROOF = 10;
    /** How far ahead of the ship the bystander that the watch goal looks at is kept. */
    private static final double BYSTANDER = 3D;
    /** How far behind the ship the target is kept, and how far ahead the admiral is. */
    private static final double BEHIND = 6D;
    private static final double AHEAD = 20D;
    /** With a ration the admiral must stand within its 8 blocks of the ship. */
    private static final double RATION_AHEAD = 6D;
    /** Sideways offset of the admiral from an idle ship, so a look at the admiral is told from any other. */
    private static final double RATION_SIDE = 5D;
    /** The ration fires every 16 ticks; one more on every other cycle so both parities of the ship's goal tick are hit. */
    private static final int RATION_PERIOD = 16;
    private static final Vec3[] ROUTE = {new Vec3(2.5D, 2D, 2.5D), new Vec3(30.5D, 2D, 2.5D),
            new Vec3(62.5D, 2D, 2.5D)};

    private MovementLookAuthorityGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_look_follow_engaged", timeoutTicks = 220)
    public static void followerEngagedLooksAtTargetNotOwner(GameTestHelper helper) {
        run(helper, true, true, LookReason.ENGAGED_TARGET);
    }

    @GameTest(template = "arena", batch = "isolated_look_guard_engaged", timeoutTicks = 220)
    public static void guardEngagedLooksAtTargetNotDestination(GameTestHelper helper) {
        run(helper, false, true, LookReason.ENGAGED_TARGET);
    }

    @GameTest(template = "arena", batch = "isolated_look_follow_idle", timeoutTicks = 220)
    public static void followerNotEngagedStillLooksAtOwner(GameTestHelper helper) {
        run(helper, true, false, LookReason.FOLLOW_OWNER);
    }

    /**
     * A player stands ahead within the watch goal's 4 blocks while the real watch goal (started every
     * tick it may) runs beside the follow goal: the engaged head must still face the target.
     */
    @GameTest(template = "arena", batch = "isolated_look_follow_engaged_watch", timeoutTicks = 220)
    public static void followerEngagedKeepsLookingAtTargetWhileWatchGoalRuns(GameTestHelper helper) {
        run(helper, true, true, true, LookReason.ENGAGED_TARGET);
    }

    /**
     * The admiral holds a selected combat ration within 8 blocks, on the side the ship walks to, and the
     * ration's periodic look is fired from here; an engaged head must still face the target.
     */
    @GameTest(template = "arena", batch = "isolated_look_follow_engaged_ration", timeoutTicks = 260)
    public static void followerEngagedKeepsLookingAtTargetWhileRationHeld(GameTestHelper helper) {
        run(helper, true, true, false, true, LookReason.ENGAGED_TARGET);
    }

    /** With nothing engaged, the ration still turns the ship's head to the admiral. */
    @GameTest(template = "arena", batch = "isolated_look_guard_idle_ration", timeoutTicks = 260)
    public static void idleShipStillLooksAtAdmiralWhileRationHeld(GameTestHelper helper) {
        run(helper, false, false, false, true, LookReason.GUARD);
    }

    private static void run(GameTestHelper helper, boolean follow, boolean engage, LookReason expected) {
        run(helper, follow, engage, false, false, expected);
    }

    private static void run(GameTestHelper helper, boolean follow, boolean engage, boolean watch,
                            LookReason expected) {
        run(helper, follow, engage, watch, false, expected);
    }

    private static void run(GameTestHelper helper, boolean follow, boolean engage, boolean watch, boolean ration,
                            LookReason expected) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper, follow, engage, watch, ration);
            if (watch) GameTestEntities.onCompletion(helper, scene::close, "watch lane cleanup");
            int start = (int) helper.getTick() + 1;
            helper.runAtTickTime(start, () -> scene.guard(scene::setup));
            for (int tick = start + 1; tick < start + 100; tick++) {
                helper.runAtTickTime(tick, () -> scene.guard(scene::poll));
            }
            helper.runAtTickTime(start + 100, () -> scene.guard(() -> {
                List<String> problems = scene.problems(expected);
                String report = scene.toString();
                scene.close();
                helper.assertTrue(problems.isEmpty(), problems + "\n " + report);
                LogHelper.info("Movement look authority (" + (follow ? "follow" : "guard") + ", engage=" + engage
                        + "): " + report);
                helper.succeed();
            }));
        }, watch ? WatchLane.tickingPositions(helper) : ROUTE);
    }

    /** The yaw a head at {@code from} must have to face {@code to}. */
    private static float bearing(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * (180D / Math.PI)) - 90F;
    }

    /** A wide, empty-sky straightaway used only by the watch/follow scene. */
    private static final class WatchLane {
        private static final int LIFT = 60;
        private static final int BACK = 16;
        private static final int FRONT = 128;
        private static final int HALF_WIDTH = 12;
        private static final int CLEAR_HEIGHT = 4;
        private static final double FORWARD_EPSILON = 0.000001D;
        private static final double MIN_WALK_DISTANCE = 0.1D;
        private final GameTestHelper helper;
        private final BlockPos origin;
        private final Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
        private double previousX;
        private double dx;
        private double maxSide;
        private double overlapDistance;
        private double overlapStartX;
        private int previousTick = -1;
        private int startedAt = -1;
        private int overlapFrom = -1;
        private int overlapTo = -1;
        private int lastJudgedAt = -1;
        private int requestTick = -1;
        private int walking;
        private final List<String> displacements = new ArrayList<>();

        WatchLane(GameTestHelper helper) {
            this.helper = helper;
            this.origin = BlockPos.containing(helper.absoluteVec(SHIP)).above(LIFT);
            this.previousX = this.origin.getX() + 0.5D;
        }

        /** Every chunk intersecting the world-axis-aligned floor, not just its corners. */
        static Vec3[] tickingPositions(GameTestHelper helper) {
            BlockPos origin = BlockPos.containing(helper.absoluteVec(SHIP)).above(LIFT);
            List<Vec3> positions = new ArrayList<>();
            for (int cx = (origin.getX() - BACK) >> 4; cx <= (origin.getX() + FRONT) >> 4; cx++) {
                for (int cz = (origin.getZ() - HALF_WIDTH) >> 4;
                     cz <= (origin.getZ() + HALF_WIDTH) >> 4; cz++) {
                    positions.add(helper.relativeVec(new Vec3(cx * 16 + 8D, origin.getY(), cz * 16 + 8D)));
                }
            }
            return positions.toArray(Vec3[]::new);
        }

        void build() {
            // Check the whole occupied volume before changing any block; never clear another fixture.
            for (BlockPos pos : BlockPos.betweenClosed(this.origin.offset(-BACK, -1, -HALF_WIDTH),
                    this.origin.offset(FRONT, CLEAR_HEIGHT, HALF_WIDTH))) {
                this.helper.assertTrue(this.helper.getLevel().getBlockState(pos).isAir(),
                        "watch lane is not empty at " + pos);
            }
            AABB occupied = new AABB(this.origin.offset(-BACK, -1, -HALF_WIDTH),
                    this.origin.offset(FRONT + 1, CLEAR_HEIGHT + 1, HALF_WIDTH + 1));
            this.helper.assertTrue(this.helper.getLevel().getEntities((Entity) null, occupied).isEmpty(),
                    "watch lane contains another fixture's entities");
            for (BlockPos pos : BlockPos.betweenClosed(this.origin.offset(-BACK, -1, -HALF_WIDTH),
                    this.origin.offset(FRONT, -1, HALF_WIDTH))) {
                BlockPos fixed = pos.immutable();
                this.replaced.put(fixed, this.helper.getLevel().getBlockState(fixed));
                this.helper.getLevel().setBlock(fixed, Blocks.STONE.defaultBlockState(), 2);
            }
        }

        void check(Entity entity) {
            AABB box = entity.getBoundingBox();
            this.helper.assertTrue(box.minX >= this.origin.getX() - BACK + 1D
                            && box.maxX <= this.origin.getX() + FRONT
                            && box.minZ >= this.origin.getZ() - HALF_WIDTH + 1D
                            && box.maxZ <= this.origin.getZ() + HALF_WIDTH,
                    "watch fixture left its flat lane: " + entity.position());
            this.helper.assertTrue(Math.abs(entity.getY() - this.origin.getY()) < 0.001D,
                    "watch fixture left its fixed feet Y: " + entity.position());
            this.helper.assertTrue(this.helper.getLevel().isPositionEntityTicking(entity.blockPosition()),
                    "watch fixture is outside entity-ticking chunks: " + entity.position());
        }

        void checkSpace(BasicEntityShip ship, Entity owner, Entity foe, Entity bystander) {
            for (BlockPos pos : BlockPos.betweenClosed(this.origin.offset(-BACK, -1, -HALF_WIDTH),
                    this.origin.offset(FRONT, CLEAR_HEIGHT, HALF_WIDTH))) {
                boolean floor = pos.getY() == this.origin.getY() - 1;
                BlockState state = this.helper.getLevel().getBlockState(pos);
                this.helper.assertTrue(floor ? state.is(Blocks.STONE) : state.isAir(),
                        "watch lane terrain changed at " + pos + ": " + state);
            }
            AABB occupied = new AABB(this.origin.offset(-BACK, -1, -HALF_WIDTH),
                    this.origin.offset(FRONT + 1, CLEAR_HEIGHT + 1, HALF_WIDTH + 1));
            this.helper.assertTrue(this.helper.getLevel().getEntities(ship, occupied,
                            entity -> entity != owner && entity != foe && entity != bystander).isEmpty(),
                    "another entity entered the watch lane");
            this.check(ship);
            if (owner != null) this.check(owner);
            if (foe != null) this.check(foe);
            if (bystander != null) this.check(bystander);
        }

        void observe(BasicEntityShip ship, Zombie foe, boolean judge) {
            this.check(ship);
            this.dx = ship.getX() - this.previousX;
            double side = Math.abs(ship.getZ() - (this.origin.getZ() + 0.5D));
            this.maxSide = Math.max(this.maxSide, side);
            var request = ship.shipMovementExecutor().last();
            var path = ship.getNavigation().getPath();
            boolean followPath = request.isPresent() && request.get().outcome() == PathOutcome.ISSUED
                    && request.get().step() instanceof MovementStep.PathTo move
                    && move.body() == MovementBody.SELF && move.reason() == MovementReason.FOLLOW_OWNER
                    && move.target() instanceof MovementTarget.Point point
                    && path != null && !path.isDone() && path.canReach()
                    && path.getTarget().equals(BlockPos.containing(point.point().x(), point.point().y(), point.point().z()))
                    && MovementPlanParityGameTests.selector(ship).getRunningGoals()
                            .anyMatch(goal -> goal.getGoal() instanceof ShipFollowOwnerGoal);
            this.requestTick = request.map(value -> value.tick()).orElse(-1);
            boolean forward = this.dx > FORWARD_EPSILON;
            String state = "tick=" + ship.tickCount + " at=" + ship.position() + " dx=" + this.dx
                    + " side=" + side + " request=" + request + " path=" + path + " started=" + this.startedAt;
            this.helper.assertTrue(side < 0.5D, "watch fixture left its straight row: " + state);
            if (this.startedAt < 0 && forward && followPath) this.startedAt = ship.tickCount;
            if (this.startedAt >= 0) {
                this.helper.assertTrue(forward && followPath
                                && (this.previousTick < 0 || ship.tickCount == this.previousTick + 1),
                        "watch fixture lost continuous natural forward walking: " + state);
                boolean sameTarget = foe != null && ShipCombatGate.engagedTarget(ship)
                        .filter(ShipCommandStateAdapter.handle(foe)::equals).isPresent();
                this.helper.assertTrue(this.overlapFrom < 0 || judge && sameTarget,
                        "watch fixture lost its walking/engagement/look overlap: " + state);
                if (judge) {
                    this.helper.assertTrue(sameTarget, "watch fixture engaged a different target: " + state);
                    if (this.overlapFrom < 0) {
                        this.overlapFrom = ship.tickCount;
                        this.overlapStartX = this.previousX;
                    }
                    this.overlapTo = ship.tickCount;
                    this.walking++;
                    this.overlapDistance = ship.getX() - this.overlapStartX;
                    this.displacements.add(ship.tickCount + ":" + this.dx);
                }
            } else {
                this.helper.assertTrue(!forward, "watch fixture moved without its own follow path: " + state);
            }
            if (judge) this.lastJudgedAt = ship.tickCount;
            this.previousX = ship.getX();
            this.previousTick = ship.tickCount;
        }

        void recordLook(BasicEntityShip ship, Zombie foe, float off, boolean wrong) {
            LogHelper.info("Movement watch observation tick=" + ship.tickCount + " dx=" + this.dx
                    + " walking=" + (this.overlapTo == ship.tickCount) + " requestTick=" + this.requestTick
                    + " judge=true engagedSame=" + ShipCombatGate.engagedTarget(ship)
                            .filter(ShipCommandStateAdapter.handle(foe)::equals).isPresent()
                    + " shipX=" + ship.getX() + " foe=" + foe.getUUID() + " foeX=" + foe.getX()
                    + " head=" + ship.yHeadRot + " off=" + off + " wrongHead=" + wrong
                    + " wantedX=" + ship.getLookControl().getWantedX());
        }

        void verify(List<String> problems, int minimumSamples) {
            if (this.walking < minimumSamples || this.overlapDistance < MIN_WALK_DISTANCE
                    || this.overlapTo != this.lastJudgedAt || this.overlapTo != this.previousTick) {
                problems.add("insufficient continuous walking/engagement/look overlap: " + this.report());
            }
        }

        String report() {
            return "started=" + this.startedAt + " overlap=" + this.overlapFrom + ".." + this.overlapTo
                    + " walking=" + this.walking + " netDx=" + this.overlapDistance + " maxSide=" + this.maxSide
                    + " lastJudged=" + this.lastJudgedAt + " lastObserved=" + this.previousTick
                    + " perTickDx=" + this.displacements;
        }

        void close() {
            if (this.replaced.isEmpty()) return;
            int count = this.replaced.size();
            this.replaced.forEach((pos, state) -> this.helper.getLevel().setBlock(pos, state, 2));
            this.replaced.clear();
            LogHelper.info("Movement watch lane restored " + count + " blocks at " + this.origin
                    + "; " + this.report());
        }
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final boolean follow;
        private final boolean engage;
        private final boolean watch;
        private final boolean ration;
        private final ShipAiAuthorityOverride authority;
        private final GameTestEntities entities;
        private final WatchLane lane;
        private final List<String> samples = new ArrayList<>();
        private final List<String> late = new ArrayList<>();
        private BasicEntityShip ship;
        private Zombie foe;
        private FakePlayer owner;
        private FakePlayer bystander;
        private WrappedGoal watching;
        private int watchRan;
        private int engagedEarly;
        private boolean attacked;
        private Vec3 destination;
        private int releasedAt = -1;
        private int engagedFor;
        private int checked;
        private int wrongHead;
        private boolean closed;
        private Optional<LookRequest> lastLook = Optional.empty();
        private int nextRation;
        private int rationCalls;
        private int rationTookHead;
        private int rationMissedAdmiral;

        Scene(GameTestHelper helper, boolean follow, boolean engage, boolean watch, boolean ration) {
            this.helper = helper;
            this.follow = follow;
            this.engage = engage;
            this.watch = watch;
            this.ration = ration;
            this.authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
            this.entities = GameTestEntities.open(helper);
            this.lane = watch ? new WatchLane(helper) : null;
        }

        void setup() {
            if (this.lane != null) this.lane.build();
            this.ship = MovementPlanParityGameTests.friendly(this.helper, this.entities, SHIP);
            if (this.lane != null) this.lane.checkSpace(this.ship, null, null, null);
            if (this.engage && !this.watch) this.spawnFoe();
            this.destination = MovementPlanParityGameTests.ground(this.helper, AWAY);
        }

        private void spawnFoe() {
            this.foe = this.entities.add(EntityType.ZOMBIE.create(this.helper.getLevel()));
            this.foe.setNoAi(true);
            this.foe.setPersistenceRequired();
            this.foe.setInvulnerable(true);
            this.place(this.foe, this.ship.getX() - BEHIND);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(this.foe), "failed to add the target");
        }

        /** Standing at {@code x} in the ship's row, on the fixed floor in the watch scene. */
        private void place(Entity entity, double x) {
            int y = this.lane != null ? this.lane.origin.getY()
                    : this.helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x),
                            (int) Math.floor(this.ship.getZ()));
            entity.moveTo(x, y, this.ship.getZ(), 0F, 0F);
            if (this.lane != null) this.lane.check(entity);
        }

        void release() {
            this.releasedAt = this.ship.tickCount;
            if (this.follow || this.ration) {
                this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "look_authority_owner"));
                this.placeOwner();
                this.helper.getLevel().addNewPlayer(this.owner);
                this.ship.setOwnerUUID(this.owner.getUUID());
                if (this.ration) {
                    // the ration only calls ships whose owner it resolves by player UID
                    int uid = 30_000 + Math.floorMod(this.ship.getUUID().hashCode(), 100_000);
                    this.owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                            .orElseThrow(() -> new IllegalStateException("Missing owner capability"))
                            .setPlayerUID(uid);
                    this.ship.setPlayerUID(uid);
                    this.helper.assertTrue(TeamHelper.checkSameOwner(this.owner, this.ship),
                            "The ration must see the admiral as the ship's owner");
                }
            }
            if (!this.follow) {
                BlockPos pos = BlockPos.containing(this.destination);
                var id = this.helper.getLevel().dimension().location();
                this.ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(pos.getX(), pos.getY(), pos.getZ()), false)));
            }
            if (this.watch) {
                // the product goal, but always willing to start, so no random draw decides the test
                var selector = MovementPlanParityGameTests.selector(this.ship);
                selector.removeAllGoals(goal -> goal instanceof ShipWatchClosestGoal);
                selector.addGoal(25, new ShipWatchClosestGoal(this.ship, Player.class, 4F, 1F));
                this.watching = selector.getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal() instanceof ShipWatchClosestGoal).findFirst().orElse(null);
                this.helper.assertTrue(this.watching != null, "the watch goal was not registered");
                this.bystander = FakePlayerFactory.get(this.helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "look_authority_bystander"));
                this.place(this.bystander, this.ship.getX() + BYSTANDER);
                this.helper.getLevel().addNewPlayer(this.bystander);
            }
            // with a watcher the attack comes later, after the watch goal has shown it can run here
            if (this.engage && !this.watch) this.attack();
        }

        private void placeOwner() {
            if (this.follow) {
                this.place(this.owner, this.ship.getX() + (this.ration ? RATION_AHEAD : AHEAD));
            } else {
                this.place(this.owner, this.ship.getX() + 3D);
                this.owner.setPos(this.owner.getX(), this.owner.getY(), this.ship.getZ() + RATION_SIDE);
            }
        }

        /** Fires the ration's periodic look for the admiral, as the admiral's inventory tick would. */
        private void rationTick() {
            if (this.ship.tickCount < this.nextRation) return;
            this.nextRation = this.ship.tickCount + RATION_PERIOD + (this.rationCalls % 2);
            this.owner.tickCount = 0;
            ItemStack stack = new ItemStack(ModItems.COMBAT_RATION.get());
            stack.getItem().inventoryTick(stack, this.helper.getLevel(), this.owner, 0, true);
            this.rationCalls++;
            boolean onAdmiral = Math.abs(this.ship.getLookControl().getWantedX() - this.owner.getX()) < 0.5D
                    && Math.abs(this.ship.getLookControl().getWantedZ() - this.owner.getZ()) < 0.5D;
            if (this.engage && onAdmiral) this.rationTookHead++;
            if (!this.engage && !onAdmiral) this.rationMissedAdmiral++;
        }

        private void attack() {
            this.attacked = true;
            // with a watcher the target appears only now: the ship picks a target of its own the moment one is near
            if (this.foe == null) this.spawnFoe();
            this.ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(this.foe))));
            this.helper.assertTrue(this.ship.getManualTarget() == this.foe, "the attack order was not taken");
        }

        void poll() {
            // wander draws from the entity random and would walk the ship off on its own
            MovementPlanParityGameTests.selector(this.ship).removeAllGoals(goal -> goal instanceof ShipWanderGoal);
            if (this.lane != null) this.lane.checkSpace(this.ship, this.owner, this.foe, this.bystander);
            if (this.releasedAt < 0) {
                if (this.ship.tickCount >= RELEASE_AT) this.release();
                return;
            }
            // the target and the admiral keep pace with the ship, so it stays in range of the one
            // and far from the other
            if (this.foe != null) this.place(this.foe, this.ship.getX() - BEHIND);
            if (this.owner != null) this.placeOwner();
            if (this.bystander != null) this.place(this.bystander, this.ship.getX() + BYSTANDER);
            if (this.watching != null && !this.attacked) {
                // before the attack: the watch goal must be able to run in this fixture
                if (this.watching.isRunning()) {
                    if (this.lane != null) {
                        this.helper.assertTrue(Math.abs(this.ship.getLookControl().getWantedX()
                                                - this.bystander.getX()) < 1D
                                        && Math.abs(this.ship.getLookControl().getWantedZ()
                                                - this.bystander.getZ()) < 0.001D,
                                "the real watch goal must look at the bystander ahead");
                    }
                    this.watchRan++;
                }
                if (ShipCombatGate.engagedTarget(this.ship).isPresent()) this.engagedEarly++;
                if (this.ship.tickCount >= this.releasedAt + WATCH_PROOF) this.attack();
            }
            boolean engaged = ShipCombatGate.engagedTarget(this.ship).isPresent();
            this.engagedFor = engaged ? this.engagedFor + 1 : 0;
            this.lastLook = this.ship.shipMovementExecutor().lastLook();
            boolean judge = this.engage ? this.engagedFor >= SETTLE : this.ship.tickCount >= this.releasedAt + SETTLE;
            if (this.lane != null) this.lane.observe(this.ship, this.foe, judge);
            if (judge && this.ration) this.rationTick();
            if (!judge) return;
            Vec3 where = this.ship.position();
            Vec3 toward = this.engage ? this.foe.position() : this.follow ? this.owner.position() : this.destination;
            float off = Math.abs(Mth.wrapDegrees(this.ship.yHeadRot - bearing(where, toward)));
            this.checked++;
            // an idle ship with a ration is meant to turn to the admiral, so only its look is judged then
            if (off > this.maxOff() && !(this.ration && !this.engage)) {
                this.wrongHead++;
                if (this.late.size() < 6) {
                    this.late.add(this.ship.tickCount + ":off=" + MovementPlanParityGameTests.fmt(off));
                }
            }
            if (this.lane != null) this.lane.recordLook(this.ship, this.foe, off, off > this.maxOff());
            if (this.samples.size() < 8 && this.checked % 4 == 1) {
                this.samples.add(this.ship.tickCount + ":off=" + MovementPlanParityGameTests.fmt(off) + ":dist="
                        + MovementPlanParityGameTests.fmt(where.distanceTo(toward)));
            }
        }

        private float maxOff() {
            return this.engage ? 180F - this.ship.getMaxHeadYRot() + NECK_SLACK : WALK_OFF;
        }

        List<String> problems(LookReason expected) {
            List<String> problems = new ArrayList<>();
            if (this.releasedAt < 0) problems.add("the ship never got going");
            if (this.checked < 10) problems.add("only " + this.checked + " ticks could be judged");
            if (this.watch && this.watchRan < 3) {
                problems.add("the watch goal ran in only " + this.watchRan
                        + " ticks before the attack (engaged early in " + this.engagedEarly
                        + "), so the fixture proves nothing");
            }
            if (this.ration && this.rationCalls < 4) {
                problems.add("the ration fired only " + this.rationCalls + " times");
            }
            if (this.rationTookHead > 0) {
                problems.add("the ration turned the engaged head to the admiral in " + this.rationTookHead + " of "
                        + this.rationCalls + " calls");
            }
            if (this.rationMissedAdmiral > 0) {
                problems.add("the ration did not turn the idle head to the admiral in " + this.rationMissedAdmiral
                        + " of " + this.rationCalls + " calls");
            }
            if (this.wrongHead > 0) problems.add("the head was more than " + this.maxOff() + " degrees off its "
                    + (this.engage ? "target" : "destination") + " in " + this.wrongHead + " of " + this.checked
                    + " ticks: " + this.late);
            if (this.lastLook.isEmpty() || this.lastLook.get().reason() != expected) {
                problems.add("the last look was " + this.lastLook + ", not " + expected);
            }
            if (this.lane != null) this.lane.verify(problems, 10);
            return problems;
        }

        void guard(Runnable body) {
            try {
                body.run();
            } catch (Throwable error) {
                this.close();
                throw error;
            }
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            try {
                this.entities.close();
                if (this.owner != null) {
                    this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                }
                if (this.bystander != null) {
                    this.helper.getLevel().removePlayerImmediately(this.bystander, Entity.RemovalReason.DISCARDED);
                }
            } finally {
                try {
                    if (this.lane != null) this.lane.close();
                } finally {
                    this.authority.close();
                }
            }
        }

        @Override
        public String toString() {
            return "released=" + this.releasedAt + " judged=" + this.checked + " watchRan=" + this.watchRan
                    + " rationCalls=" + this.rationCalls + " samples=" + this.samples
                    + " lastLook=" + this.lastLook + " at=" + (this.ship == null ? "-" : this.ship.position());
        }
    }
}
