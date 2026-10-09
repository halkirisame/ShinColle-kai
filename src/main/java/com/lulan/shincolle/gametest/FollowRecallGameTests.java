package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.handler.ShipFollowRecallHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.OwnerPaper;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.LogHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Real entity ticking away from the test structures, with no fixture ticket to mask expiry. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FollowRecallGameTests {
    private static final TicketType<Integer> PROBE = TicketType.create("ship_follow_recall_probe", Integer::compare, 400);
    private static final TicketType<UUID> PREPARE = TicketType.create("ship_follow_recall_prepare", UUID::compareTo);

    private FollowRecallGameTests() { }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_follow_recall_ticket", timeoutTicks = 480)
    public static void timedRegionTicketKeepsEntitiesTickingAndExpires(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(2048.5D, 1D, 2048.5D));
        BlockPos position = BlockPos.containing(at);
        ChunkPos chunk = new ChunkPos(position);
        var level = helper.getLevel();
        var chunks = level.getChunkSource();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) level.getChunk(chunk.x + dx, chunk.z + dz);
        }
        Pig pig = GameTestEntities.open(helper).add(EntityType.PIG.create(level));
        helper.assertTrue(pig != null, "Could not create ticket probe");
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setInvulnerable(true);
        pig.moveTo(at.x, at.y, at.z);
        helper.assertTrue(level.addFreshEntity(pig), "Could not add ticket probe");
        chunks.addRegionTicket(PROBE, chunk, 4, 1);
        long issuedAt = level.getGameTime();
        helper.runAtTickTime(helper.getTick() + 390, () -> {
            helper.assertTrue(level.isPositionEntityTicking(position), "Timed ticket did not keep source entity-ticking");
            helper.assertTrue(pig.tickCount >= 370, "Source body stopped ticking during the hold: " + pig.tickCount);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.assertTrue(level.isPositionEntityTicking(position.offset(dx * 16, 0, dz * 16)),
                            "Recall did not cover the five by five entity-ticking area");
                }
            }
            helper.assertTrue(!level.isPositionEntityTicking(position.offset(48, 0, 0)), "Recall area exceeded five by five");
            LogHelper.info("Follow recall ticket probe: elapsed=" + (level.getGameTime() - issuedAt)
                    + ", entityTicks=" + pig.tickCount);
        });
        helper.runAtTickTime(helper.getTick() + 440, () -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.assertTrue(!level.isPositionEntityTicking(position.offset(dx * 16, 0, dz * 16)),
                            "Timed ticket remained entity-ticking after expiry");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_follow_recall_jump", timeoutTicks = 600)
    public static void ownerJumpRecallsSixFollowersAndTheSourceHoldExpires(GameTestHelper helper) {
        runRecall(helper, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_follow_recall_legacy", timeoutTicks = 600)
    public static void legacyOwnerJumpDoesNotAddARecallTicketOrShortWait(GameTestHelper helper) {
        runRecall(helper, true);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "isolated_follow_recall_transfer", timeoutTicks = 180)
    public static void ownerTransferDiscardsRecallBeforeCachedOwnerRefresh(GameTestHelper helper) {
        ShipAiAuthorityOverride.useUntilComplete(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        var level = helper.getLevel();
        var entities = GameTestEntities.open(helper);
        var ship = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(1.5D, 2D, 1.5D));
        ship.setNoAi(true);
        Vec3 source = ship.position();
        Vec3 destination = source.add(64D, 0D, 0D);
        FakePlayer oldOwner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "recall_previous"));
        FakePlayer newOwner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "recall_current"));
        oldOwner.moveTo(source);
        newOwner.moveTo(source);
        level.addNewPlayer(oldOwner);
        level.addNewPlayer(newOwner);
        var onlineOld = new FormationGameTestOwner(oldOwner);
        var onlineNew = new FormationGameTestOwner(newOwner);
        int oldUid = oldOwner.getId() + 9_000_000, newUid = newOwner.getId() + 9_000_000;
        oldOwner.getCapability(CapaTeitokuProvider.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Missing previous owner capability")).setPlayerUID(oldUid);
        newOwner.getCapability(CapaTeitokuProvider.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Missing current owner capability")).setPlayerUID(newUid);
        ship.setPlayerUID(oldUid);
        ship.setOwnerUUID(oldOwner.getUUID());
        int cooldown = ConfigHandler.shipTeleport[0], distance = ConfigHandler.shipTeleport[1];
        ConfigHandler.shipTeleport[0] = 200;
        ConfigHandler.shipTeleport[1] = 16;
        attachCompletion(helper, () -> {
            ShipFollowRecallHandler.forget(oldOwner.getUUID());
            ShipFollowRecallHandler.forget(newOwner.getUUID());
            onlineOld.close();
            onlineNew.close();
            level.removePlayerImmediately(oldOwner, Entity.RemovalReason.DISCARDED);
            level.removePlayerImmediately(newOwner, Entity.RemovalReason.DISCARDED);
            ConfigHandler.shipTeleport[0] = cooldown;
            ConfigHandler.shipTeleport[1] = distance;
        });
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos floor = BlockPos.containing(destination).offset(x, -1, z);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(floor.above(y), Blocks.AIR.defaultBlockState());
            }
        }
        ShipFollowOwnerGoal[] cached = new ShipFollowOwnerGoal[1];
        long start = helper.getTick() + 24;
        helper.runAtTickTime(start, () -> {
            helper.assertTrue(ServerDataManager.getPlayerByUID(newUid) == newOwner, "Recipient must be online");
            long now = level.getServer().getTickCount();
            ShipFollowRecallHandler.forget(oldOwner.getUUID());
            for (long tick = now - 20; tick < now; tick++) ShipFollowRecallHandler.observe(oldOwner, tick);
            oldOwner.moveTo(destination);
            ShipFollowRecallHandler.observe(oldOwner, now);
            helper.assertTrue(ship.shipMovementExecutor().followRecallRemaining(
                    ShipCommandStateAdapter.handle(oldOwner), now).orElse(-1L) == 100L, "Jump must create a real recall");
        });
        helper.runAtTickTime(start + 99, () -> {
            cached[0] = new ShipFollowOwnerGoal(ship);
            helper.assertTrue(cached[0].canUse(), "Follow goal must resolve the previous owner");
            cached[0].start();
            cached[0].tick();
            helper.assertTrue(ship.distanceToSqr(oldOwner) > 256D, "Recall must still wait before its deadline");
        });
        helper.runAtTickTime(start + 100, () -> {
            oldOwner.moveTo(source);
            ItemStack paper = new ItemStack(ModItems.OWNER_PAPER.get());
            paper.getOrCreateTag().putInt(OwnerPaper.SignIDA, oldUid);
            paper.getOrCreateTag().putInt(OwnerPaper.SignIDB, newUid);
            oldOwner.setItemInHand(InteractionHand.MAIN_HAND, paper);
            oldOwner.setShiftKeyDown(true);
            ship.mobInteract(oldOwner, InteractionHand.MAIN_HAND);
            helper.assertTrue(newOwner.getUUID().equals(ship.getOwnerUUID()) && ship.getPlayerUID() == newUid,
                    "Signed owner paper must transfer both identities");
            oldOwner.moveTo(destination);
            helper.assertTrue(cached[0].canContinueToUse(), "Goal must still be inside its owner refresh interval");
            cached[0].tick();
            helper.assertTrue(ship.distanceToSqr(oldOwner) > 256D, "Transferred ship recalled to the previous owner");
            helper.assertTrue(ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(oldOwner),
                    level.getServer().getTickCount()).isEmpty(), "Invalid recall must be discarded, not merely skipped");
            helper.succeed();
        });
    }

    private static void runRecall(GameTestHelper helper, boolean legacy) {
        Scene scene = new Scene(helper, legacy);
        helper.onEachTick(scene::poll);
    }

    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final boolean legacy;
        private final ShipAiAuthorityOverride authority;
        private final GameTestEntities entities;
        private final UUID holder = UUID.randomUUID();
        private final ChunkPos source;
        private final ChunkPos destination;
        private final Vec3 sourcePoint;
        private final Vec3 destinationPoint;
        private final List<BasicEntityShip> followers = new ArrayList<>();
        private final List<BasicEntityShip> excluded = new ArrayList<>();
        private final List<UUID> identities = new ArrayList<>();
        private final List<Integer> shipIds = new ArrayList<>();
        private final int oldCooldown = ConfigHandler.shipTeleport[0];
        private final int oldDistance = ConfigHandler.shipTeleport[1];
        private FakePlayer owner;
        private Pig sourceProbe;
        private Consumer<TickEvent.ServerTickEvent> observer;
        private long jumpedAt;
        private boolean jumped;
        private boolean created;
        private boolean closed;
        private boolean returned;
        private boolean measured;
        private boolean crossedSourceBoundary;

        Scene(GameTestHelper helper, boolean legacy) {
            this.helper = helper;
            this.legacy = legacy;
            this.authority = ShipAiAuthorityOverride.use(legacy
                    ? ConfigHandler.ShipAiTargetAuthority.LEGACY : ConfigHandler.ShipAiTargetAuthority.NEW);
            this.entities = GameTestEntities.open(helper);
            ConfigHandler.shipTeleport[0] = 200;
            ConfigHandler.shipTeleport[1] = 256;
            Vec3 column = helper.absoluteVec(new Vec3(2048.5D, 0D, 2048.5D));
            column = new Vec3(Math.floor(column.x / 16D) * 16D + 14.5D, column.y, column.z);
            this.source = new ChunkPos(BlockPos.containing(column));
            this.destination = new ChunkPos(BlockPos.containing(column.add(512D, 0D, 0D)));
            load(this.source);
            load(this.destination);
            this.sourcePoint = MovementPlanParityGameTests.ground(helper, helper.relativeVec(column));
            this.destinationPoint = MovementPlanParityGameTests.ground(helper, helper.relativeVec(column.add(512D, 0D, 0D)));
            attachCompletion(helper, this::close);
        }

        private void load(ChunkPos chunk) {
            var level = this.helper.getLevel();
            level.getChunkSource().addRegionTicket(PREPARE, chunk, 2, this.holder);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) level.getChunk(chunk.x + dx, chunk.z + dz);
            }
        }

        private void create() {
            this.created = true;
            var level = this.helper.getLevel();
            this.owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "follow_recall"));
            this.owner.moveTo(this.sourcePoint.x, this.sourcePoint.y, this.sourcePoint.z);
            level.addNewPlayer(this.owner);
            for (int i = 0; i < 6; i++) this.followers.add(ship());
            BasicEntityShip sitting = ship();
            sitting.setEntitySit(true);
            this.excluded.add(sitting);
            BasicEntityShip empty = ship();
            empty.setStateMinor(ID.M.NumGrudge, 0);
            empty.setStateFlag(ID.F.NoFuel, true);
            this.excluded.add(empty);
            BasicEntityShip guarding = ship();
            BlockPos guard = guarding.blockPosition();
            if (this.legacy) {
                guarding.setGuardedPos(guard.getX(), guard.getY(), guard.getZ(), level.dimension(), 1);
                guarding.setStateFlag(ID.F.CanFollow, false);
            } else {
                guarding.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(ShipCommandStateAdapter.handle(guarding).dimension(),
                                new CommandPos(guard.getX(), guard.getY(), guard.getZ()), false)));
            }
            this.excluded.add(guarding);
            BasicEntityShip foreign = ship();
            foreign.setOwnerUUID(UUID.randomUUID());
            this.excluded.add(foreign);
            this.sourceProbe = this.entities.add(EntityType.PIG.create(level));
            this.sourceProbe.setNoAi(true);
            this.sourceProbe.setNoGravity(true);
            this.sourceProbe.setInvulnerable(true);
            this.sourceProbe.moveTo(this.sourcePoint.x, this.sourcePoint.y + 4D, this.sourcePoint.z);
            this.helper.assertTrue(level.addFreshEntity(this.sourceProbe), "Could not add source hold probe");
            this.observer = event -> {
                if (!this.closed && event.phase == TickEvent.Phase.START) {
                    ShipFollowRecallHandler.observe(this.owner, event.getServer().getTickCount());
                }
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

        void poll() {
            if (this.closed) return;
            var level = this.helper.getLevel();
            if (!this.created) {
                if (level.isPositionEntityTicking(BlockPos.containing(this.sourcePoint))
                        && level.isPositionEntityTicking(BlockPos.containing(this.destinationPoint))) create();
                return;
            }
            for (BasicEntityShip ship : this.followers) {
                MovementPlanParityGameTests.selector(ship).getAvailableGoals().stream()
                        .filter(goal -> goal.getGoal() instanceof ShipWanderGoal).toList()
                        .forEach(goal -> MovementPlanParityGameTests.selector(ship).removeGoal(goal.getGoal()));
            }
            if (!this.jumped) {
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
                    if (!this.legacy) this.helper.assertTrue(ShipMovementGate.recallEligible(ship, this.owner), "Follower not eligible");
                }
                if (!this.legacy) {
                    for (BasicEntityShip ship : this.excluded) {
                        this.helper.assertTrue(!ShipMovementGate.recallEligible(ship, this.owner), "Excluded ship remained eligible");
                    }
                }
                this.helper.assertTrue(MovementPlanParityGameTests.selector(this.followers.get(0)).getRunningGoals()
                        .noneMatch(goal -> goal.getGoal() instanceof ShipFollowOwnerGoal), "Fixture requires an inactive follow goal");
                this.jumped = true;
                this.jumpedAt = level.getServer().getTickCount();
                this.owner.moveTo(this.destinationPoint.x, this.destinationPoint.y, this.destinationPoint.z);
                level.getChunkSource().move(this.owner);
                level.getChunkSource().removeRegionTicket(PREPARE, this.source, 2, this.holder);
                return;
            }
            long elapsed = level.getServer().getTickCount() - this.jumpedAt;
            if (!this.legacy && elapsed < 95 && this.followers.stream().anyMatch(ship -> !ship.chunkPosition().equals(this.source))) {
                this.crossedSourceBoundary = true;
            }
            if (!this.legacy && !this.returned && elapsed == 120) {
                BasicEntityShip ship = this.followers.get(0);
                LogHelper.info("Follow recall state: owner=" + this.owner + ", ownerAlive=" + this.owner.isAlive()
                        + ", resolved=" + ship.getOwner() + ", ship=" + ship
                        + ", intent=" + ShipMovementGate.decision(ship)
                        + ", remaining=" + ship.shipMovementExecutor().followRecallRemaining(
                                ShipCommandStateAdapter.handle(this.owner), level.getServer().getTickCount())
                        + ", goals=" + MovementPlanParityGameTests.selector(ship).getRunningGoals()
                                .map(goal -> goal.getGoal().getClass().getSimpleName()).toList()
                        + ", last=" + ship.shipMovementExecutor().last() + ", denial=" + ship.shipMovementExecutor().lastDenials());
            }
            if (!this.returned && !this.legacy && this.followers.stream().allMatch(ship -> ship.distanceToSqr(this.owner) < 16D)) {
                this.returned = true;
                this.helper.assertTrue(this.crossedSourceBoundary, "Fixture did not exercise walking out of the source chunk");
                this.helper.assertTrue(elapsed >= 95 && elapsed <= 115, "Recall did not take about five seconds: " + elapsed);
                for (int i = 0; i < this.followers.size(); i++) {
                    BasicEntityShip ship = this.followers.get(i);
                    this.helper.assertTrue(this.identities.get(i).equals(ship.getUUID()) && this.shipIds.get(i) == ship.getShipUID(),
                            "Recall changed ship identity");
                    this.helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(0).is(Items.COBBLESTONE)
                            && ship.getCapaShipInventory().getStackInSlot(0).getCount() == 17, "Recall changed inventory");
                }
                LogHelper.info("Follow recall: six followers arrived after " + elapsed + " server ticks");
            }
            if (!this.measured && elapsed >= 390) {
                this.measured = true;
                boolean ticking = level.isPositionEntityTicking(BlockPos.containing(this.sourcePoint));
                this.helper.assertTrue(ticking != this.legacy, "Source ticking was not controlled by the recall hold");
                if (!this.legacy) {
                    this.helper.assertTrue(this.returned, "Six followers never returned before hold expiry");
                    this.helper.assertTrue(this.sourceProbe.tickCount >= 410, "Source probe stopped during recall hold");
                }
                for (BasicEntityShip ship : this.excluded) {
                    this.helper.assertTrue(ship.distanceToSqr(this.owner) > 256D, "Excluded ship was recalled");
                }
            }
            if (elapsed >= 440) {
                this.helper.assertTrue(!level.isPositionEntityTicking(BlockPos.containing(this.sourcePoint)), "Recall hold did not expire");
                close();
                this.helper.succeed();
            }
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            if (this.observer != null) MinecraftForge.EVENT_BUS.unregister(this.observer);
            this.helper.getLevel().getChunkSource().removeRegionTicket(PREPARE, this.source, 2, this.holder);
            this.helper.getLevel().getChunkSource().removeRegionTicket(PREPARE, this.destination, 2, this.holder);
            this.entities.close();
            if (this.owner != null) {
                ShipFollowRecallHandler.forget(this.owner.getUUID());
                this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
            }
            ConfigHandler.shipTeleport[0] = this.oldCooldown;
            ConfigHandler.shipTeleport[1] = this.oldDistance;
            this.authority.close();
        }
    }

    private static void attachCompletion(GameTestHelper helper, Runnable cleanup) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                @Override
                public void testStructureLoaded(GameTestInfo info) { }

                @Override
                public void testPassed(GameTestInfo info) { cleanup.run(); }

                @Override
                public void testFailed(GameTestInfo info) { cleanup.run(); }
            });
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot attach recall fixture cleanup", error);
        }
    }
}
