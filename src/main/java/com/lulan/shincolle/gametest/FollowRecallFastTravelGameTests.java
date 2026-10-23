package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.FollowRecallRequest;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.handler.ShipFollowRecallHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * Followers left behind by a fast departure, with real chunk unloading. Each test uses its own
 * stretch of the world so a ship saved in an unloaded chunk by one test cannot reach another.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FollowRecallFastTravelGameTests {
    private static final TicketType<UUID> PREPARE = TicketType.create("ship_fast_travel_prepare", UUID::compareTo);
    private static final double NEAR_SQ = 25D;
    private static final double FAR_SQ = 256D;
    /** The recall request lives 400 ticks and its ticket expires then; the chunk is gone well before this. */
    private static final long UNLOADED_BY = 470L;
    private static final long LAND_AT = 480L;

    private FollowRecallFastTravelGameTests() { }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_glide", timeoutTicks = 900)
    public static void glidingOwnerIsRecalledOnlyAfterLandingFromThirteenBlocksPerSecond(GameTestHelper helper) {
        Rig rig = new Rig(helper, 0, 3);
        double speed = 0.65D;
        int glide = 200;
        rig.landingPoint(speed * glide);
        rig.path(elapsed -> elapsed < glide
                ? rig.sourcePoint.add(speed * (elapsed + 1), 40D, 0D)
                : rig.landingPoint(speed * glide), elapsed -> elapsed >= glide);
        helper.onEachTick(() -> rig.poll(elapsed -> {
            if (elapsed == 25) {
                helper.assertTrue(rig.followers.get(0).shipMovementExecutor().followRecallRemaining(
                        ShipCommandStateAdapter.handle(rig.owner), rig.now()).isPresent(),
                        "Thirteen blocks a second must start the recall");
            }
            if (elapsed >= 60 && elapsed < glide) {
                for (BasicEntityShip ship : rig.followers) {
                    helper.assertTrue(ship.distanceToSqr(rig.owner) > FAR_SQ,
                            "A follower was teleported while the owner was still gliding, at " + elapsed);
                }
            }
            if (elapsed >= glide && rig.allFollowersWithin(NEAR_SQ)) {
                rig.assertIdentity();
                rig.assertExcludedFar();
                helper.succeed();
            }
            helper.assertTrue(elapsed < 420, "Followers never came after landing");
        }));
    }

    /**
     * A glide long enough that the ordinary far timer, set short here, runs out many times over. Without the
     * hold the follower would be teleported to the gliding owner and fall out of the air.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_airborne", timeoutTicks = 1100)
    public static void aLongGlideNeverPutsAFollowerInTheAirWhileTheFollowTimerRunsOut(GameTestHelper helper) {
        Rig rig = new Rig(helper, 6, 3);
        double speed = 0.65D;
        int glide = 450;
        rig.landingPoint(speed * glide);
        rig.gliding(elapsed -> elapsed < glide);
        rig.path(elapsed -> elapsed < glide
                ? rig.sourcePoint.add(speed * (elapsed + 1), 40D, 0D)
                : rig.landingPoint(speed * glide), elapsed -> elapsed >= glide);
        helper.onEachTick(() -> rig.poll(elapsed -> {
            if (elapsed == 1) ConfigHandler.shipTeleport[0] = 40;
            if (elapsed >= 30 && elapsed < glide) {
                helper.assertTrue(ShipFollowRecallHandler.isAirborne(rig.owner.getUUID()),
                        "The gliding owner was not seen as airborne at " + elapsed);
                rig.assertNoFollowerWithin(FAR_SQ, "A follower was put beside the gliding owner at " + elapsed);
            }
            if (elapsed >= glide && rig.allFollowersWithin(NEAR_SQ)) {
                helper.assertTrue(!ShipFollowRecallHandler.isAirborne(rig.owner.getUUID()), "Still airborne after landing");
                rig.assertIdentity();
                helper.succeed();
            }
            helper.assertTrue(elapsed < glide + 420, "Followers never came after the glide: " + rig.describe());
        }));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_unloaded", timeoutTicks = 1300)
    public static void unloadedFollowersAreLoadedAndRecalledAfterTheOwnerSettles(GameTestHelper helper) {
        Rig rig = new Rig(helper, 1, 3);
        rig.jumpAndHover();
        long[] arrivedAt = {-1L};
        helper.onEachTick(() -> rig.poll(elapsed -> {
            if (elapsed == UNLOADED_BY) rig.assertSourceUnloaded();
            if (elapsed >= LAND_AT && elapsed < LAND_AT + 400 && elapsed % 20 == 0) rig.trace(elapsed);
            if (arrivedAt[0] < 0 && elapsed > LAND_AT && rig.allFollowersWithin(NEAR_SQ)) {
                rig.assertIdentity();
                rig.assertRestoredFollowers();
                rig.assertExcludedFar();
                rig.reloadSource();
                arrivedAt[0] = elapsed;
            }
            if (arrivedAt[0] >= 0 && elapsed == arrivedAt[0] + 40) {
                // The source chunk is read from disk again: nothing of the recalled followers is left in it.
                helper.assertTrue(helper.getLevel().hasChunk(rig.source.x, rig.source.z), "The source chunk did not load again");
                rig.assertIdentity();
                rig.assertRestoredFollowers();
                helper.assertTrue(rig.allFollowersWithin(NEAR_SQ), "A follower left the owner after the source chunk loaded");
                helper.succeed();
            }
            helper.assertTrue(elapsed < LAND_AT + 500, "Unloaded followers never came after the owner settled: " + rig.trace);
        }));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_logout", timeoutTicks = 1100)
    public static void loggingOutDiscardsTheRosterSoNothingIsLoaded(GameTestHelper helper) {
        Rig rig = new Rig(helper, 3, 3);
        rig.jumpAndHover();
        helper.onEachTick(() -> rig.poll(elapsed -> {
            if (elapsed == UNLOADED_BY) rig.assertSourceUnloaded();
            if (elapsed == UNLOADED_BY + 5) ShipFollowRecallHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(rig.owner));
            if (elapsed == LAND_AT + 250) {
                rig.assertNothingLoaded();
                helper.succeed();
            }
        }));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_dimension", timeoutTicks = 1100)
    public static void changingDimensionDiscardsTheRosterSoNothingIsLoaded(GameTestHelper helper) {
        Rig rig = new Rig(helper, 4, 3);
        rig.jumpAndHover();
        helper.onEachTick(() -> rig.poll(elapsed -> {
            if (elapsed == UNLOADED_BY) rig.assertSourceUnloaded();
            if (elapsed == UNLOADED_BY + 5) {
                ShipFollowRecallHandler.onDimensionChange(new PlayerEvent.PlayerChangedDimensionEvent(rig.owner,
                        helper.getLevel().dimension(), helper.getLevel().dimension()));
            }
            if (elapsed == LAND_AT + 250) {
                rig.assertNothingLoaded();
                helper.succeed();
            }
        }));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_walk", timeoutTicks = 400)
    public static void walkingSpeedDoesNotStartTheRecall(GameTestHelper helper) {
        Rig rig = new Rig(helper, 5, 1);
        double speed = 0.3D;
        rig.path(elapsed -> rig.sourcePoint.add(speed * (elapsed + 1), 0D, 0D), elapsed -> true);
        helper.onEachTick(() -> rig.poll(elapsed -> {
            helper.assertTrue(rig.followers.get(0).shipMovementExecutor().followRecallRemaining(
                    ShipCommandStateAdapter.handle(rig.owner), rig.now()).isEmpty(), "Walking started the recall");
            if (elapsed == 150) helper.succeed();
        }));
    }

    /**
     * A second long teleport inside the first one's four hundred ticks, after the followers have arrived at the first
     * destination. Its chunk is released and the owner goes far enough that nothing keeps it running.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_consecutive_arrived", timeoutTicks = 1000)
    public static void aSecondLongTeleportInsideFourHundredTicksStillBringsFollowersFromTheFirstDestination(GameTestHelper helper) {
        Rig rig = new Rig(helper, 7, 3);
        Vec3 first = rig.landingPoint(512D);
        Vec3 second = rig.landingPoint(1024D);
        ChunkPos firstChunk = new ChunkPos(BlockPos.containing(first));
        long[] secondAt = {-1L};
        long[] arrivedSecondAt = {-1L};
        rig.path(elapsed -> secondAt[0] >= 0 && elapsed >= secondAt[0] ? second : first, elapsed -> true);
        helper.onEachTick(() -> rig.poll(elapsed -> {
            if (secondAt[0] < 0) {
                if (elapsed > 5 && rig.allFollowersWithin(NEAR_SQ)) {
                    helper.assertTrue(elapsed < 300, "The first recall came too late for the second teleport to be inside the hold: " + elapsed);
                    rig.assertIdentity();
                    rig.release(first);
                    secondAt[0] = elapsed + 20;
                }
                helper.assertTrue(elapsed < 300, "Followers never came to the first destination: " + rig.describe());
                return;
            }
            if (elapsed == secondAt[0] + 1) {
                helper.assertTrue(rig.owner.distanceToSqr(second) < 1D, "The owner did not go to the second destination");
            }
            if (elapsed == secondAt[0] + 120) rig.trace(elapsed);
            if (arrivedSecondAt[0] < 0 && elapsed > secondAt[0] + 5 && rig.allFollowersWithin(NEAR_SQ)) {
                rig.assertIdentity();
                rig.assertExcludedFar();
                rig.reload(firstChunk);
                arrivedSecondAt[0] = elapsed;
            }
            if (arrivedSecondAt[0] >= 0 && elapsed == arrivedSecondAt[0] + 40) {
                helper.assertTrue(helper.getLevel().hasChunk(firstChunk.x, firstChunk.z), "The first destination did not load again");
                rig.assertIdentity();
                helper.assertTrue(rig.allFollowersWithin(NEAR_SQ), "A follower left the owner after the first destination loaded");
                helper.succeed();
            }
            helper.assertTrue(elapsed < secondAt[0] + 400, "Followers never came to the second destination; firstDestinationLoaded="
                    + helper.getLevel().hasChunk(firstChunk.x, firstChunk.z) + ", detected="
                    + ShipFollowRecallHandler.isCurrent(rig.owner.getUUID(), rig.tickAt(secondAt[0])) + ": " + rig.describe()
                    + rig.trace);
        }));
    }

    /**
     * A second teleport while the followers are still waiting at the source, close enough that they stand inside the
     * second departure's search area. Their wait is the first one's: neither restarted nor extended.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_fast_travel_consecutive_waiting", timeoutTicks = 600)
    public static void aSecondDepartureWhileFollowersWaitKeepsTheirFirstWait(GameTestHelper helper) {
        Rig rig = new Rig(helper, 10, 3);
        // Fourteen blocks in twenty ticks is a departure, and the followers stay inside the second one's search area.
        Vec3 first = rig.landingPoint(14D);
        Vec3 second = rig.landingPoint(28D);
        long secondAt = 40L;
        long[] detectedAt = {Long.MIN_VALUE};
        int[] checks = {0};
        rig.path(elapsed -> elapsed >= secondAt ? second : first, elapsed -> true);
        helper.onEachTick(() -> rig.poll(elapsed -> {
            if (elapsed == 2) {
                double searched = (helper.getLevel().getServer().getPlayerList().getSimulationDistance() + 1) * 16D;
                helper.assertTrue(Math.abs(rig.sourcePoint.x - first.x) < searched - 1D && Math.abs(rig.sourcePoint.z - first.z) < searched - 1D,
                        "The followers are outside the second departure's search area: " + searched);
                var remaining = rig.followers.get(0).shipMovementExecutor().followRecallRemaining(
                        ShipCommandStateAdapter.handle(rig.owner), rig.now());
                helper.assertTrue(remaining.isPresent() && remaining.getAsLong() > 0L, "The first departure started no wait: " + remaining);
                detectedAt[0] = rig.now() + remaining.getAsLong() - FollowRecallRequest.WAIT_TICKS;
                helper.assertTrue(ShipFollowRecallHandler.isCurrent(rig.owner.getUUID(), detectedAt[0]), "The first departure is not current");
            }
            if (elapsed >= 2 && elapsed <= FollowRecallRequest.WAIT_TICKS - 2) {
                for (BasicEntityShip ship : rig.followers) {
                    var remaining = ship.shipMovementExecutor().followRecallRemaining(
                            ShipCommandStateAdapter.handle(rig.owner), rig.now());
                    helper.assertTrue(remaining.isPresent() && remaining.getAsLong()
                                    == detectedAt[0] + FollowRecallRequest.WAIT_TICKS - rig.now(),
                            "The wait of a follower was dropped or restarted at " + elapsed + ": " + remaining);
                    checks[0]++;
                }
            }
            if (elapsed == secondAt + 1) {
                helper.assertTrue(ShipFollowRecallHandler.isCurrent(rig.owner.getUUID(), detectedAt[0]),
                        "The first departure is no longer current after the second");
                helper.assertTrue(ShipFollowRecallHandler.isCurrent(rig.owner.getUUID(), rig.tickAt(secondAt)),
                        "The second departure was not detected");
            }
            if (elapsed > FollowRecallRequest.WAIT_TICKS && rig.allFollowersWithin(NEAR_SQ)) {
                helper.assertTrue(checks[0] >= 3 * (FollowRecallRequest.WAIT_TICKS - 4), "Waits were checked too few times: " + checks[0]);
                helper.assertTrue(rig.owner.distanceToSqr(second) < 1D, "The followers came to a place other than the second destination");
                rig.assertIdentity();
                rig.assertExcludedFar();
                helper.succeed();
            }
            helper.assertTrue(elapsed < 400, "Followers never came to the second destination: " + rig.describe());
        }));
    }

    private static final class Rig {
        final GameTestHelper helper;
        final int followerCount;
        final GameTestEntities entities;
        final UUID holder = UUID.randomUUID();
        final ChunkPos source;
        final Vec3 sourcePoint;
        final Vec3 column;
        final List<BasicEntityShip> followers = new ArrayList<>();
        final List<BasicEntityShip> excluded = new ArrayList<>();
        final List<UUID> identities = new ArrayList<>();
        final List<Integer> shipIds = new ArrayList<>();
        final int oldCooldown = ConfigHandler.shipTeleport[0];
        final int oldDistance = ConfigHandler.shipTeleport[1];
        final List<ChunkPos> prepared = new ArrayList<>();
        FakePlayer owner;
        private Consumer<TickEvent.ServerTickEvent> observer;
        private LongFunction<Vec3> path = elapsed -> Vec3.ZERO;
        private LongPredicate supported = elapsed -> true;
        private LongPredicate gliding = elapsed -> false;
        private long startedAt = -1L;
        private boolean created;
        private boolean closed;

        Rig(GameTestHelper helper, int lane, int followerCount) {
            this.helper = helper;
            this.followerCount = followerCount;
            this.entities = GameTestEntities.open(helper);
            ConfigHandler.shipTeleport[0] = 200;
            ConfigHandler.shipTeleport[1] = 256;
            Vec3 at = helper.absoluteVec(new Vec3(4096.5D + lane * 1024D, 0D, 4096.5D));
            this.column = new Vec3(Math.floor(at.x / 16D) * 16D + 8.5D, at.y, at.z);
            this.source = new ChunkPos(BlockPos.containing(this.column));
            load(this.source);
            this.sourcePoint = MovementPlanParityGameTests.ground(helper, helper.relativeVec(this.column));
            FollowRecallGameTests.attachCompletion(helper, this::close);
        }

        void path(LongFunction<Vec3> path, LongPredicate supported) {
            this.path = path;
            this.supported = supported;
        }

        /** The owner reports gliding on every tick the predicate holds. */
        void gliding(LongPredicate gliding) {
            this.gliding = gliding;
        }

        /** An instant 512 block jump, the owner kept unsupported until it is time to land. */
        void jumpAndHover() {
            Vec3 landing = landingPoint(512D);
            this.path = elapsed -> landing;
            this.supported = elapsed -> elapsed >= LAND_AT;
            preloadLanding(landing);
        }

        Vec3 landingPoint(double distance) {
            Vec3 at = this.column.add(distance, 0D, 0D);
            // The height of a column that is not loaded reads as the bottom of the world, so load first.
            load(new ChunkPos(BlockPos.containing(at)));
            return MovementPlanParityGameTests.ground(this.helper, this.helper.relativeVec(at));
        }

        private void preloadLanding(Vec3 point) {
            load(new ChunkPos(BlockPos.containing(point)));
        }

        private void load(ChunkPos chunk) {
            if (this.prepared.contains(chunk)) return;
            this.prepared.add(chunk);
            var level = this.helper.getLevel();
            level.getChunkSource().addRegionTicket(PREPARE, chunk, 2, this.holder);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) level.getChunk(chunk.x + dx, chunk.z + dz);
            }
        }

        long now() {
            return this.helper.getLevel().getServer().getTickCount();
        }

        /** The server tick of the given elapsed time of this run. */
        long tickAt(long elapsed) {
            return this.startedAt + elapsed;
        }

        /** Stops holding the chunk of a point the fixture loaded, so it can really unload once nobody is near. */
        void release(Vec3 point) {
            ChunkPos chunk = new ChunkPos(BlockPos.containing(point));
            if (!this.prepared.remove(chunk)) return;
            this.helper.getLevel().getChunkSource().removeRegionTicket(PREPARE, chunk, 2, this.holder);
        }

        /** Loads a chunk again, as another player walking there would. */
        void reload(ChunkPos chunk) {
            var level = this.helper.getLevel();
            level.getChunkSource().addRegionTicket(PREPARE, chunk, 2, this.holder);
            this.prepared.add(chunk);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) level.getChunk(chunk.x + dx, chunk.z + dz);
            }
        }

        private void create() {
            this.created = true;
            var level = this.helper.getLevel();
            this.owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "fast_travel_" + this.column.x));
            this.owner.moveTo(this.sourcePoint.x, this.sourcePoint.y, this.sourcePoint.z);
            this.owner.setOnGround(true);
            level.addNewPlayer(this.owner);
            for (int i = 0; i < this.followerCount; i++) this.followers.add(ship());
            BasicEntityShip sitting = ship();
            sitting.setEntitySit(true);
            this.excluded.add(sitting);
            BasicEntityShip empty = ship();
            empty.setStateMinor(ID.M.NumGrudge, 0);
            empty.setStateFlag(ID.F.NoFuel, true);
            this.excluded.add(empty);
            BasicEntityShip foreign = ship();
            foreign.setOwnerUUID(UUID.randomUUID());
            this.excluded.add(foreign);
            BasicEntityShip guarding = ship();
            BlockPos guard = guarding.blockPosition();
            guarding.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.GuardPosition(ShipCommandStateAdapter.handle(guarding).dimension(),
                            new CommandPos(guard.getX(), guard.getY(), guard.getZ()), false)));
            this.excluded.add(guarding);
            this.observer = event -> {
                if (this.closed || event.phase != TickEvent.Phase.START || this.owner == null) return;
                if (this.startedAt >= 0) {
                    long elapsed = event.getServer().getTickCount() - this.startedAt;
                    Vec3 at = this.path.apply(elapsed);
                    this.owner.moveTo(at.x, at.y, at.z);
                    this.owner.setOnGround(this.supported.test(elapsed));
                    if (this.gliding.test(elapsed)) this.owner.startFallFlying();
                    else this.owner.stopFallFlying();
                    level.getChunkSource().move(this.owner);
                } else {
                    this.owner.setOnGround(true);
                }
                ShipFollowRecallHandler.observe(this.owner, event.getServer().getTickCount());
            };
            MinecraftForge.EVENT_BUS.addListener(this.observer);
        }

        private BasicEntityShip ship() {
            Vec3 relative = this.helper.relativeVec(this.sourcePoint);
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(this.helper, this.entities, relative);
            ship.setOwnerUUID(this.owner.getUUID());
            ship.getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 17));
            return ship;
        }

        /** Runs the checks once the fixture is created and started, with the ticks elapsed since the start. */
        void poll(Consumer<Long> checks) {
            if (this.closed) return;
            var level = this.helper.getLevel();
            if (!this.created) {
                if (level.isPositionEntityTicking(BlockPos.containing(this.sourcePoint))) create();
                return;
            }
            for (BasicEntityShip ship : this.followers) {
                removeWanderGoal(ship);
            }
            // Chunk loading creates a new entity, which registers its goals again at tick 16.
            for (UUID id : this.identities) {
                if (level.getEntity(id) instanceof BasicEntityShip ship) {
                    removeWanderGoal(ship);
                    assertNoWanderGoal(ship);
                }
            }
            if (this.startedAt < 0) {
                for (BasicEntityShip ship : this.followers) {
                    ship.moveTo(this.sourcePoint.x, this.sourcePoint.y, this.sourcePoint.z);
                    ship.setDeltaMovement(Vec3.ZERO);
                    ship.getNavigation().stop();
                }
                if (this.followers.get(0).tickCount < 40) return;
                this.helper.assertTrue(ConfigHandler.canTeleport(), "Fixture requires teleporting enabled");
                for (BasicEntityShip ship : this.followers) {
                    this.identities.add(ship.getUUID());
                    this.shipIds.add(ship.getShipUID());
                    this.helper.assertTrue(ship.getShipUID() > 0, "Fixture needs assigned ship UID");
                    this.helper.assertTrue(ShipMovementGate.recallEligible(ship, this.owner), "Follower not eligible");
                }
                for (BasicEntityShip ship : this.excluded) {
                    this.helper.assertTrue(!ShipMovementGate.recallEligible(ship, this.owner), "Excluded ship remained eligible");
                }
                this.helper.assertTrue(MovementPlanParityGameTests.selector(this.followers.get(0)).getRunningGoals()
                        .noneMatch(goal -> goal.getGoal() instanceof ShipFollowOwnerGoal), "Fixture requires an inactive follow goal");
                for (ChunkPos chunk : this.prepared) {
                    if (!chunk.equals(this.source)) continue;
                    level.getChunkSource().removeRegionTicket(PREPARE, chunk, 2, this.holder);
                }
                this.startedAt = this.now();
                return;
            }
            checks.accept(this.now() - this.startedAt);
        }

        private void removeWanderGoal(BasicEntityShip ship) {
            MovementPlanParityGameTests.selector(ship).getAvailableGoals().stream()
                    .filter(goal -> goal.getGoal() instanceof ShipWanderGoal).toList()
                    .forEach(goal -> MovementPlanParityGameTests.selector(ship).removeGoal(goal.getGoal()));
        }

        private void assertNoWanderGoal(BasicEntityShip ship) {
            this.helper.assertTrue(MovementPlanParityGameTests.selector(ship).getAvailableGoals().stream()
                    .noneMatch(goal -> goal.getGoal() instanceof ShipWanderGoal),
                    "Current follower still has a wander goal: " + ship.getUUID() + ", tick=" + ship.tickCount);
        }

        final StringBuilder trace = new StringBuilder();

        void trace(long elapsed) {
            this.trace.append(" || t=").append(elapsed).append(' ').append(describe());
        }

        String describe() {
            var level = this.helper.getLevel();
            StringBuilder text = new StringBuilder("settled=" + ShipFollowRecallHandler.isSettled(this.owner.getUUID())
                    + ", sourceLoaded=" + level.hasChunk(this.source.x, this.source.z) + ", owner=" + this.owner.position());
            for (UUID id : this.identities) {
                Entity found = level.getEntity(id);
                text.append(" [");
                if (found instanceof BasicEntityShip ship) {
                    text.append("alive=").append(ship.isAlive()).append(", uid=").append(ship.getShipUID())
                            .append(", eligible=").append(ShipMovementGate.recallEligible(ship, this.owner))
                            .append(", distSq=").append((int) ship.distanceToSqr(this.owner))
                            .append(", remaining=").append(ship.shipMovementExecutor().followRecallRemaining(
                                    ShipCommandStateAdapter.handle(this.owner), this.now()))
                            .append(", intent=").append(ShipMovementGate.decision(ship));
                } else {
                    text.append(found == null ? "absent" : found.toString());
                }
                text.append("]");
            }
            return text.toString();
        }

        void assertNoFollowerWithin(double distanceSq, String message) {
            var level = this.helper.getLevel();
            for (UUID id : this.identities) {
                Entity found = level.getEntity(id);
                this.helper.assertTrue(found == null || found.distanceToSqr(this.owner) > distanceSq, message);
            }
        }

        /** Loads the chunk the followers left behind, as another player walking there would. */
        void reloadSource() {
            var level = this.helper.getLevel();
            level.getChunkSource().addRegionTicket(PREPARE, this.source, 2, this.holder);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) level.getChunk(this.source.x + dx, this.source.z + dz);
            }
        }

        boolean allFollowersWithin(double distanceSq) {
            var level = this.helper.getLevel();
            for (UUID id : this.identities) {
                Entity found = level.getEntity(id);
                if (found == null || found.distanceToSqr(this.owner) >= distanceSq) return false;
            }
            return true;
        }

        void assertSourceUnloaded() {
            var level = this.helper.getLevel();
            this.helper.assertTrue(!level.hasChunk(this.source.x, this.source.z), "Fixture source chunk was never unloaded");
            for (BasicEntityShip ship : this.followers) {
                this.helper.assertTrue(ship.isRemoved() && ship.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                        "Fixture follower was not unloaded with its chunk: " + ship.getRemovalReason());
            }
        }

        void assertNothingLoaded() {
            var level = this.helper.getLevel();
            this.helper.assertTrue(!level.hasChunk(this.source.x, this.source.z), "The source chunk was loaded again");
            for (UUID id : this.identities) {
                this.helper.assertTrue(level.getEntity(id) == null, "An unloaded follower came back");
            }
        }

        void assertIdentity() {
            var level = this.helper.getLevel();
            for (int i = 0; i < this.identities.size(); i++) {
                UUID id = this.identities.get(i);
                int count = 0;
                for (Entity entity : level.getAllEntities()) if (entity.getUUID().equals(id)) count++;
                this.helper.assertTrue(count == 1, "Follower count for one UUID was " + count);
                BasicEntityShip ship = (BasicEntityShip) level.getEntity(id);
                this.helper.assertTrue(ship != null && ship.getShipUID() == this.shipIds.get(i), "Recall changed the ship UID");
                this.helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(0).is(Items.COBBLESTONE)
                        && ship.getCapaShipInventory().getStackInSlot(0).getCount() == 17, "Recall changed inventory");
            }
        }

        void assertRestoredFollowers() {
            var level = this.helper.getLevel();
            for (int i = 0; i < this.identities.size(); i++) {
                Entity found = level.getEntity(this.identities.get(i));
                this.helper.assertTrue(found instanceof BasicEntityShip && found != this.followers.get(i),
                        "Recalled follower must be a different entity after chunk restoration");
                BasicEntityShip ship = (BasicEntityShip) found;
                this.helper.assertTrue(ship.tickCount > 16, "Restored follower has not re-registered its goals yet");
                assertNoWanderGoal(ship);
            }
        }

        void assertExcludedFar() {
            var level = this.helper.getLevel();
            for (BasicEntityShip ship : this.excluded) {
                Entity found = level.getEntity(ship.getUUID());
                this.helper.assertTrue(found == null || found.distanceToSqr(this.owner) > FAR_SQ, "Excluded ship was recalled");
            }
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            if (this.observer != null) MinecraftForge.EVENT_BUS.unregister(this.observer);
            var level = this.helper.getLevel();
            for (ChunkPos chunk : this.prepared) level.getChunkSource().removeRegionTicket(PREPARE, chunk, 2, this.holder);
            this.entities.close();
            List<UUID> all = new ArrayList<>(this.identities);
            for (BasicEntityShip ship : this.excluded) all.add(ship.getUUID());
            for (UUID id : all) {
                Entity found = level.getEntity(id);
                if (found != null && !found.isRemoved()) found.discard();
            }
            if (this.owner != null) {
                ShipFollowRecallHandler.forget(this.owner.getUUID());
                level.removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
            }
            ConfigHandler.shipTeleport[0] = this.oldCooldown;
            ConfigHandler.shipTeleport[1] = this.oldDistance;
        }
    }
}
