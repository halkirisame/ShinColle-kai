package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipFireControlGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.PathOutcome;
import com.lulan.shincolle.ai.domain.task.TaskMoveRequest;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.mounts.EntityMountCaH;
import com.lulan.shincolle.entity.mounts.EntityMountCaWD;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.BlockHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipFishingDefaultRangeGameTests {
    private ShipFishingDefaultRangeGameTests() { }

    @GameTest(template = "arena", timeoutTicks = 900, batch = "isolated_ship_fishing_default_range")
    public static void mountedCarrierHimeCastsAtSingleFishingSpotWithDefaultFollowRange(GameTestHelper helper) {
        verifyCast(helper, ModEntities.CV_HIME.get(), true, "carrier_hime_mounted", 45301);
    }

    @GameTest(template = "arena", timeoutTicks = 900, batch = "isolated_ship_fishing_default_range")
    public static void mountedCarrierWaterDemonCastsAtSingleFishingSpotWithDefaultFollowRange(GameTestHelper helper) {
        verifyCast(helper, ModEntities.CV_WD.get(), true, "carrier_wd_mounted", 45302);
    }

    @GameTest(template = "arena", timeoutTicks = 900, batch = "isolated_ship_fishing_default_range")
    public static void unmountedCarrierHimeCastsAtSingleFishingSpotWithDefaultFollowRange(GameTestHelper helper) {
        verifyCast(helper, ModEntities.CV_HIME.get(), false, "carrier_hime_unmounted", 45303);
    }

    @GameTest(template = "arena", timeoutTicks = 900, batch = "isolated_ship_fishing_default_range")
    public static void yamatoCastsAtSingleFishingSpotWithDefaultFollowRange(GameTestHelper helper) {
        verifyCast(helper, ModEntities.BB_YAMATO.get(), false, "yamato", 45304);
    }

    @GameTest(template = "arena", timeoutTicks = 900, batch = "isolated_ship_fishing_default_range")
    public static void kongouCastsAtSingleFishingSpotWithDefaultFollowRange(GameTestHelper helper) {
        verifyCast(helper, ModEntities.BB_KONGOU.get(), false, "kongou", 45305);
    }

    @GameTest(template = "arena", timeoutTicks = 300, batch = "isolated_mounted_fishing_flee")
    public static void mountedFishingNeverReplacesTheFleePath(GameTestHelper helper) {
        verifyProtectedMountedFishing(helper, true, 45308);
    }

    @GameTest(template = "arena", timeoutTicks = 300, batch = "isolated_mounted_fishing_combat")
    public static void mountedFishingNeverReplacesTheCarriersCombatPath(GameTestHelper helper) {
        verifyProtectedMountedFishing(helper, false, 45309);
    }

    private static void verifyProtectedMountedFishing(GameTestHelper helper, boolean fleeing, int id) {
        List<Vec3> ticking = new ArrayList<>();
        for (int z = 0; z >= -96; z -= 16) {
            ticking.add(new Vec3(1.5D, 4D, z));
            ticking.add(new Vec3(11.5D, 4D, z));
        }
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, fleeing ? "fishing_flee" : "fishing_combat", id);
            context.level().addNewPlayer(context.player());
            var online = new FormationGameTestOwner(context.player());
            GameTestEntities.onCompletion(helper, online::close, "protected fishing owner lookup");
            GameTestEntities.onCompletion(helper, () -> context.level().removePlayerImmediately(
                    context.player(), Entity.RemovalReason.DISCARDED), "protected fishing admiral");
            context.player().setNoGravity(true);
            context.player().setInvulnerable(true);
            for (int x = 1; x <= 11; x++) {
                for (int z = -96; z <= 12; z++) {
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                    if (x >= 8 && z >= 8 && z <= 11) {
                        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                    }
                }
            }
            BasicEntityShip ship = context.entities().add(ModEntities.CV_HIME.get().create(context.level()));
            helper.assertTrue(ship != null, "Fixture: protected carrier creation");
            ship.setOwnerUUID(context.player().getUUID());
            ship.setPlayerUID(context.capa().getPlayerUID());
            ship.setShipUID(id * 100);
            ship.setInvulnerable(true);
            ship.setShipLevel(50, true);
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PassiveAI, true);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateFlag(ID.F.UseMelee, true);
            ship.setStateFlag(ID.F.UseAirLight, true);
            ship.setAmmoLight(10_000);
            ship.setStateMinor(ID.M.NumAirLight, 6);
            ship.setStateEmotion(ID.S.State, 1, false);
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
            ship.moveTo(helper.absoluteVec(new Vec3(6.5D, 4D, 4.5D)));
            helper.assertTrue(context.level().addFreshEntity(ship), "Fixture: protected carrier registration");
            context.capa().setTeamMember(0, 0, ship.getShipUID());
            context.capa().setTeamSID(0, 0, ship.getId());
            PointerSingleModeGameTests.select(context.capa(), 0);
            ship.updateMountSummon();
            helper.assertTrue(ship.getVehicle() instanceof EntityMountCaH, "Fixture: protected carrier rides its own mount");
            EntityMountCaH mount = context.entities().add((EntityMountCaH) ship.getVehicle());
            mount.setInvulnerable(true);
            // Keep the real movement and target goals; omit projectile delivery so this movement fixture leaves no shots behind.
            GameTestEntities.removeGoalsEachTick(helper, ship,
                    goal -> goal instanceof ShipWanderGoal || goal instanceof ShipFireControlGoal);
            GameTestEntities.removeGoalsEachTick(helper, mount,
                    goal -> goal instanceof ShipWanderGoal || goal instanceof ShipFireControlGoal);
            BlockPos spot = helper.absolutePos(new BlockPos(6, 4, 10));
            Mob[] enemy = {null};
            boolean[] issued = {false};
            int[] enabled = {-1};
            int[] lastSample = {-1};
            int[] samples = {0};
            String[] protectedPath = {null};
            Vec3 start = mount.position();
            helper.onEachTick(() -> {
                if (ship.tickCount < 32) return;
                if (!issued[0]) {
                    helper.assertTrue(!ship.isNoAi() && ship.tickCount > 16, "Fixture: natural AI is registered");
                    helper.assertTrue(ServerDataManager.getPlayerByUID(context.capa().getPlayerUID()) == context.player(),
                            "Fixture: protected movement resolves the admiral through UID lookup");
                    helper.assertTrue(ConfigHandler.enableTask[1], "Fixture: fishing is enabled");
                    helper.assertTrue(BlockHelper.getNearbyLiquid(mount, false, false, 5, 3) != null,
                            "Fixture: depth-three water is near the starting shore");
                    ship.setStateMinor(ID.M.Task, 2);
                    order(context, spot);
                    PointerSingleModeGameTests.assertGuardDestination(helper, ship, spot, "protected fishing spot");
                    helper.assertTrue(!ship.shouldReleaseGuardOnArrival(), "Fixture: SINGLE fishing keeps the spot");
                    // Start work only after the real flee/combat goal has issued its own path.
                    ship.setStateMinor(ID.M.Task, 0);
                    if (fleeing) {
                        ship.setStateMinor(ID.M.FleeHP, 50);
                        ship.setHealth(ship.getMaxHealth() * 0.1F);
                        context.player().moveTo(mount.getX(), mount.getY(), mount.getZ() - 20D, 0F, 0F);
                    } else {
                        enemy[0] = context.entities().add(EntityType.ZOMBIE.create(context.level()));
                        helper.assertTrue(enemy[0] != null, "Fixture: enemy creation");
                        enemy[0].setNoAi(true);
                        enemy[0].setNoGravity(true);
                        enemy[0].setInvulnerable(true);
                        enemy[0].moveTo(mount.getX(), mount.getY(), mount.getZ() - 8D, 0F, 0F);
                        helper.assertTrue(context.level().addFreshEntity(enemy[0]), "Fixture: enemy registration");
                        ship.applyCommandState(new CommandIssuer.Player(context.player().getUUID()),
                                new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(enemy[0]))));
                        helper.assertTrue(mount.getAttackType(ID.F.AtkType_AirLight)
                                        && mount.getStateFlag(ID.F.UseAirLight) && mount.hasAmmoLight() && mount.hasAirLight(),
                                "Fixture: the carrier goal can launch aircraft");
                    }
                    issued[0] = true;
                }
                if (fleeing) {
                    context.player().moveTo(mount.getX(), mount.getY(), mount.getZ() - 14D, 0F, 0F);
                } else {
                    enemy[0].moveTo(mount.getX(), mount.getY(), mount.getZ() - 8D, 0F, 0F);
                }
                var movement = fleeing ? ship.shipMovementExecutor().last() : mount.shipMovementExecutor().last();
                movement.filter(request -> request.outcome() == PathOutcome.ISSUED
                                && (fleeing ? request.step().reason() == MovementReason.FLEE_TO_OWNER
                                : request.step().reason() == MovementReason.COMBAT_TOWARD_TARGET
                                || request.step().reason() == MovementReason.COMBAT_TO_REGION_EDGE))
                        .ifPresent(request -> protectedPath[0] = request.toString());
                if (enabled[0] < 0 && protectedPath[0] != null) {
                    ship.setStateMinor(ID.M.Task, 2);
                    enabled[0] = ship.tickCount;
                }
                if (enabled[0] < 0) {
                    helper.assertTrue(ship.tickCount < 120, "Fixture: protected path was never issued: " + movement);
                    return;
                }
                helper.assertTrue(ship.getVehicle() == mount && mount.getHost() == ship
                                && ship.getStateMinor(ID.M.Task) == 2 && !ship.getStateFlag(ID.F.NoFuel),
                        "Fixture: fueled carrier remains mounted and fishing");
                if (fleeing) {
                    helper.assertTrue(ShipMovementGate.intent(ship) instanceof MovementIntent.Flee,
                            "Fixture: the fishing carrier must be fleeing");
                } else {
                    helper.assertTrue(!ShipCombatGate.engagement(ship).engaged() && ShipCombatGate.engagement(mount).engaged(),
                            "Fixture: mount-only combat must not be mistaken for the riding ship's HoldFire");
                }
                var work = ship.shipMovementExecutor().last();
                helper.assertTrue(work.map(request -> request.step().reason() != MovementReason.TASK_FISHING_SPOT).orElse(true),
                        "Fishing replaced protected movement: fleeing=" + fleeing + ", tick=" + ship.tickCount
                                + ", protected=" + protectedPath[0] + ", work=" + work
                                + ", shipEngaged=" + ShipCombatGate.engagement(ship).engaged()
                                + ", mountEngaged=" + ShipCombatGate.engagement(mount).engaged()
                                + ", path=" + mount.getNavigation().getPath());
                if ((ship.tickCount & 7) == 0 && ship.tickCount > enabled[0] && ship.tickCount != lastSample[0]) {
                    helper.assertTrue(mount.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) > 10D,
                            "Fixture: each work tick must still ask to return to the fishing spot");
                    var reasons = ShipMovementGate.taskMove(ship, new TaskMoveRequest.FishingSpot(
                            new MovementPoint(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D))).reasons();
                    helper.assertTrue(reasons.contains(fleeing ? MovementInhibitReason.FLEEING : MovementInhibitReason.ENGAGED),
                            "The task boundary must retain its protected-movement reason: " + reasons);
                    samples[0]++;
                    lastSample[0] = ship.tickCount;
                }
                if (samples[0] >= (fleeing ? 4 : 3)) {
                    helper.assertTrue(mount.position().distanceToSqr(start) > 0.1D,
                            "Protected movement must actually move the mount");
                    LogUtils.getLogger().info("PROTECTED_MOUNTED_FISHING PASS fleeing={} samples={} tick={}",
                            fleeing, samples[0], ship.tickCount);
                    helper.succeed();
                }
            });
        }, ticking.toArray(Vec3[]::new));
    }

    @GameTest(template = "arena", timeoutTicks = 500, batch = "isolated_mounted_mining_follow")
    public static void mountedMiningNeverReplacesTheOwnersFollowPath(GameTestHelper helper) {
        List<Vec3> ticking = new ArrayList<>();
        for (int z = 0; z >= -160; z -= 16) {
            ticking.add(new Vec3(1.5D, 4D, z));
            ticking.add(new Vec3(10.5D, 4D, z));
        }
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, "mining_follow", 45307);
            context.level().addNewPlayer(context.player());
            var online = new FormationGameTestOwner(context.player());
            GameTestEntities.onCompletion(helper, online::close, "mining owner lookup");
            GameTestEntities.onCompletion(helper, () -> context.level().removePlayerImmediately(
                    context.player(), Entity.RemovalReason.DISCARDED), "mining admiral");
            context.player().setNoGravity(true);
            context.player().setInvulnerable(true);
            for (int x = 1; x <= 10; x++) {
                for (int z = -160; z <= 12; z++) {
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                }
            }
            BasicEntityShip ship = context.entities().add(ModEntities.CV_HIME.get().create(context.level()));
            helper.assertTrue(ship != null, "Fixture: carrier creation");
            ship.setOwnerUUID(context.player().getUUID());
            ship.setPlayerUID(context.capa().getPlayerUID());
            ship.setShipUID(4530700);
            ship.setInvulnerable(true);
            ship.setShipLevel(50, true);
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PassiveAI, true);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateEmotion(ID.S.State, 1, false);
            ship.moveTo(helper.absoluteVec(new Vec3(4.5D, 4D, 5.5D)));
            helper.assertTrue(context.level().addFreshEntity(ship), "Fixture: carrier registration");
            helper.assertTrue(ship.canSummonMounts(), "Fixture: own mount is enabled and fueled");
            ship.updateMountSummon();
            helper.assertTrue(ship.getVehicle() instanceof EntityMountCaH, "Fixture: carrier rides its own mount");
            BasicEntityMount mount = context.entities().add((BasicEntityMount) ship.getVehicle());
            mount.setInvulnerable(true);
            GameTestEntities.removeGoalsEachTick(helper, ship, goal -> goal instanceof ShipWanderGoal);
            GameTestEntities.removeGoalsEachTick(helper, mount, goal -> goal instanceof ShipWanderGoal);
            Vec3 start = mount.position();
            boolean[] issued = {false};
            boolean[] following = {false};
            int[] lastBoundary = {-1};
            int[] boundaries = {0};
            helper.onEachTick(() -> {
                if (ship.tickCount < 32) return;
                if (!issued[0]) {
                    helper.assertTrue(ship.getOwner() == context.player(), "Fixture: registered admiral resolves");
                    helper.assertTrue(ServerDataManager.getPlayerByUID(context.capa().getPlayerUID()) == context.player(),
                            "Fixture: mounted follow resolves the admiral through the production UID lookup");
                    helper.assertTrue(!ship.isNoAi() && ship.tickCount > 16, "Fixture: natural AI is registered");
                    helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                            "Fixture: carrier has the owner-follow order");
                    helper.assertTrue(ConfigHandler.enableTask[2], "Fixture: mining is enabled");
                    ship.setStateMinor(ID.M.Task, 3);
                    ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.IRON_PICKAXE));
                    context.player().moveTo(mount.getX(), mount.getY(), mount.getZ() - 16D, 0F, 0F);
                    issued[0] = true;
                }
                following[0] |= mount.shipMovementExecutor().last()
                        .map(request -> request.step().reason() == MovementReason.FOLLOW_OWNER).orElse(false);
                if (following[0]) {
                    // Keep a real, moving follow target beyond the stop range. Every possible mining draw
                    // stays within the unchanged follow region, so a passing test cannot rely on OUTSIDE_REGION.
                    context.player().moveTo(mount.getX(), mount.getY(), mount.getZ() - 6D, 0F, 0F);
                }
                var work = ship.shipMovementExecutor().last();
                helper.assertTrue(work.map(request -> request.step().reason() != MovementReason.TASK_MINING_SHUFFLE)
                                .orElse(true),
                        "Mining replaced the mounted follow path: tick=" + ship.tickCount + ", work=" + work
                                + ", mountLast=" + mount.shipMovementExecutor().last()
                                + ", navigationTarget=" + (mount.getNavigation().getPath() == null ? null
                                : mount.getNavigation().getPath().getTarget()));
                if ((ship.tickCount & 63) == 0 && lastBoundary[0] != ship.tickCount) {
                    helper.assertTrue(following[0] && mount.getNavigation().isInProgress(),
                            "Fixture: the mount must be actively following at each mining boundary: tick=" + ship.tickCount
                                    + ", following=" + following[0] + ", mountLast=" + mount.shipMovementExecutor().last());
                    helper.assertTrue(ship.getVehicle() == mount && mount.getHost() == ship,
                            "Fixture: the carrier remains on its own mount");
                    helper.assertTrue(ship.getStateMinor(ID.M.Task) == 3
                                    && ship.getCapaShipInventory().getStackInSlot(22).is(Items.IRON_PICKAXE),
                            "Fixture: carrier is mining with a pickaxe");
                    helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel)
                                    && !ShipCombatGate.engagement(ship).engaged(),
                            "Fixture: carrier is fueled and not engaged");
                    Vec3 motion = ship.getDeltaMovement();
                    helper.assertTrue(Math.abs(motion.x) <= 0.1F && Math.abs(motion.z) <= 0.1F && motion.y <= 0.1F,
                            "Fixture: the riding carrier reaches the mining shuffle branch");
                    double radius = ship.getStateMinor(ID.M.FollowMax) + ship.getBbWidth() * 0.75D;
                    for (int dx : new int[]{-4, 4}) {
                        for (int dy : new int[]{-2, 2}) {
                            for (int dz : new int[]{-4, 4}) {
                                helper.assertTrue(context.player().distanceToSqr(ship.getX() + dx,
                                                ship.getY() + dy, ship.getZ() + dz) <= radius * radius,
                                        "Fixture: every possible shuffle destination is inside the follow region");
                            }
                        }
                    }
                    lastBoundary[0] = ship.tickCount;
                    boundaries[0]++;
                }
                if (ship.tickCount >= 352) {
                    helper.assertTrue(boundaries[0] >= 5, "Must cross five natural 64-tick mining boundaries");
                    helper.assertTrue(mount.position().distanceToSqr(start) > 4D,
                            "Following must actually move the mount, not merely retain an idle path");
                    LogUtils.getLogger().info("MOUNTED_MINING_FOLLOW PASS tick={} boundaries={} distanceSq={}",
                            ship.tickCount, boundaries[0], mount.position().distanceToSqr(start));
                    helper.succeed();
                }
            });
        }, ticking.toArray(Vec3[]::new));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void taskBodyUsesOnlyTheShipsOwnMount(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var context = PointerSingleModeGameTests.createContext(helper, "task_body", 45306)) {
                BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 4530600,
                        new Vec3(4.5D, 2D, 4.5D));
                BasicEntityShip other = PointerSingleModeGameTests.addShip(context, 1, 4530601,
                        new Vec3(6.5D, 2D, 4.5D));
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                TaskMoveRequest request = new TaskMoveRequest.FishingSpot(new MovementPoint(0D, 0D, 0D));
                helper.assertTrue(ShipMovementGate.taskBody(ship) == ship, "An unmounted ship works from itself");
                BasicEntityMount mount = context.entities().add(ModEntities.MOUNT_CAH.get().create(context.level()));
                helper.assertTrue(mount != null, "Fixture: mount creation");
                mount.setHost(other);
                mount.moveTo(ship.position());
                helper.assertTrue(context.level().addFreshEntity(mount), "Fixture: mount registration");
                helper.assertTrue(ship.startRiding(mount, true), "Fixture: ride another ship's mount");
                helper.assertTrue(ShipMovementGate.taskBody(ship) == ship, "Another ship's mount is not a working body");
                helper.assertTrue(ShipMovementGate.taskMove(ship, request).reasons().equals(Set.of(MovementInhibitReason.RIDING)),
                        "Another ship's mount must retain RIDING");
                mount.setHost(ship);
                helper.assertTrue(ShipMovementGate.taskBody(ship) == mount, "The ship's own mount is its working body");
                helper.assertTrue(ShipMovementGate.taskMove(ship, request).allowed(), "The own mount permits task movement");
                ship.stopRiding();
                Entity boat = context.entities().add(EntityType.BOAT.create(context.level()));
                helper.assertTrue(boat != null, "Fixture: boat creation");
                boat.moveTo(ship.position());
                helper.assertTrue(context.level().addFreshEntity(boat) && ship.startRiding(boat, true), "Fixture: ride a boat");
                helper.assertTrue(ShipMovementGate.taskBody(ship) == ship, "A boat is not a working body");
                helper.assertTrue(ShipMovementGate.taskMove(ship, request).reasons().equals(Set.of(MovementInhibitReason.RIDING)),
                        "A boat must retain RIDING");
                ship.stopRiding();
                helper.assertTrue(ShipMovementGate.taskBody(ship) == ship, "Dismounting immediately restores the ship body");
                helper.succeed();
            }
        }, new Vec3(4.5D, 2D, 4.5D), new Vec3(6.5D, 2D, 4.5D));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void nearbyLiquidFloorsNegativeYForBothSearchPaths(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Mob host = entities.add(EntityType.PIG.create(helper.getLevel()));
                helper.assertTrue(host != null, "Fixture: liquid-search host");
                host.moveTo(helper.absoluteVec(new Vec3(4.5D, 4.25D, 4.5D)));
                helper.assertTrue(host.getY() < 0D && host.getY() != Mth.floor(host.getY()),
                        "Fixture: negative fractional Y distinguishes floor from truncation");
                BlockPos origin = host.blockPosition();
                BlockPos nearby = origin.offset(1, -2, 0);
                for (int depth = 0; depth < 3; depth++) {
                    helper.getLevel().setBlockAndUpdate(nearby.below(depth), Blocks.WATER.defaultBlockState());
                }
                helper.assertTrue(nearby.equals(BlockHelper.getNearbyLiquid(host, false, false, 1, 3)),
                        "The systematic scan must find a depth-three column at floor(Y)-2");
                helper.assertTrue(nearby.equals(BlockHelper.getNearbyLiquid(host, false, true, 1, 3)),
                        "The random-first scan must retain its systematic floor-based fallback");
                for (int depth = 0; depth < 3; depth++) {
                    helper.getLevel().setBlockAndUpdate(nearby.below(depth), Blocks.AIR.defaultBlockState());
                }
                BlockPos underHost = origin.below();
                for (int depth = 0; depth < 3; depth++) {
                    helper.getLevel().setBlockAndUpdate(underHost.below(depth), Blocks.WATER.defaultBlockState());
                }
                helper.assertTrue(BlockHelper.getNearbyLiquid(host, false, false, 1, 3) == null,
                        "Fixture: only the excluded host column contains water");
                helper.assertTrue(underHost.equals(BlockHelper.getNearbyLiquid(host, true, false, 1, 3)),
                        "The checkHostPos scan must find a depth-three column at floor(Y)-1");
                helper.succeed();
            }
        }, new Vec3(4.5D, 4.25D, 4.5D));
    }

    private static void verifyCast(GameTestHelper helper, EntityType<? extends BasicEntityShip> type,
                                   boolean mounted, String name, int id) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, name, id);
            context.level().addNewPlayer(context.player());
            GameTestEntities.onCompletion(helper, () -> context.level().removePlayerImmediately(
                    context.player(), Entity.RemovalReason.DISCARDED), "fishing admiral");
            context.player().setNoGravity(true);
            context.player().setInvulnerable(true);
            for (int x = 0; x <= 11; x++) {
                for (int z = -13; z <= 11; z++) {
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(new BlockPos(x, y, z), x >= 8 && z >= 0 && z <= 10
                                ? Blocks.WATER : Blocks.STONE);
                    }
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            Vec3 start = helper.absoluteVec(new Vec3(3.5D, 4D, -10.5D));
            BlockPos spot = helper.absolutePos(new BlockPos(6, 4, 5));
            BasicEntityShip ship = context.entities().add(type.create(context.level()));
            helper.assertTrue(ship != null, "Fixture: ship creation");
            ship.setCustomName(Component.literal("fishing_default_" + name));
            ship.setPlayerUID(context.capa().getPlayerUID());
            ship.setShipUID(id * 100);
            ship.setOwnerUUID(context.player().getUUID());
            ship.setInvulnerable(true);
            ship.setShipLevel(50, true);
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PassiveAI, true);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateEmotion(ID.S.State, mounted ? 1 : 0, false);
            ship.setStateMinor(ID.M.Task, 2);
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
            ship.moveTo(start.x, start.y, start.z, 0F, 0F);
            helper.assertTrue(context.level().addFreshEntity(ship), "Fixture: ship registration");
            context.capa().setTeamMember(0, 0, ship.getShipUID());
            context.capa().setTeamSID(0, 0, ship.getId());
            PointerSingleModeGameTests.select(context.capa(), 0);
            GameTestEntities.removeGoalsEachTick(helper, ship, goal -> goal instanceof ShipWanderGoal);
            helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == 3
                            && ship.getStateMinor(ID.M.FollowMax) == 12,
                    "Fixture: constructor defaults must be FollowMin=3, FollowMax=12");
            helper.assertTrue(ship.canSummonMounts() == mounted, "Fixture: mount state switch");
            BasicEntityMount[] vehicle = {null};
            if (mounted) {
                helper.assertTrue(ship.hasShipMounts(), "Fixture: ship supports a mount");
                ship.updateMountSummon();
                helper.assertTrue(ship.isPassenger() && ship.getVehicle() instanceof BasicEntityMount,
                        "Fixture: updateMountSummon must actually mount the ship");
                vehicle[0] = context.entities().add((BasicEntityMount) ship.getVehicle());
                helper.assertTrue(name.startsWith("carrier_hime") ? vehicle[0] instanceof EntityMountCaH
                        : vehicle[0] instanceof EntityMountCaWD, "Fixture: correct hull-specific mount");
                vehicle[0].setInvulnerable(true);
                GameTestEntities.removeGoalsEachTick(helper, vehicle[0], goal -> goal instanceof ShipWanderGoal);
            }
            boolean[] issued = {false};
            String[] initial = {null};
            helper.runAtTickTime(40, () -> {
                helper.assertTrue(ship.tickCount > 16 && !ship.isNoAi(), "Fixture: natural AI is registered");
                helper.assertTrue(ConfigHandler.enableTask[1] && ship.getStateMinor(ID.M.Task) == 2,
                        "Fixture: fishing task is selected and enabled");
                helper.assertTrue(!ship.getStateFlag(ID.F.NoFuel) && ship.getStateMinor(ID.M.NumGrudge) > 0,
                        "Fixture: ship has fuel");
                helper.assertTrue(ship.isPassenger() == mounted, "Fixture: riding state before command");
                Entity body = mounted ? vehicle[0] : ship;
                body.moveTo(start.x, start.y, start.z, 0F, 0F);
                body.setDeltaMovement(Vec3.ZERO);
                ((Mob) body).getNavigation().stop();
                ship.setDeltaMovement(Vec3.ZERO);
                order(context, spot);
                PointerSingleModeGameTests.assertGuardDestination(helper, ship, spot, "default-range fishing spot");
                helper.assertTrue(!ship.shouldReleaseGuardOnArrival(), "Fixture: persistent SINGLE fishing spot");
                Vec3 away = helper.absoluteVec(new Vec3(1.5D, 4D, -11.5D));
                context.player().moveTo(away.x, away.y, away.z, 0F, 0F);
                initial[0] = diagnostic(ship);
                LogUtils.getLogger().info("FISHING_DEFAULT INITIAL {}", initial[0]);
                issued[0] = true;
            });
            helper.onEachTick(() -> {
                if (!issued[0]) return;
                helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == 3
                                && ship.getStateMinor(ID.M.FollowMax) == 12,
                        "Fixture: follow defaults changed during natural ticks");
                helper.assertTrue(ship.isPassenger() == mounted, "Fixture: riding state changed during fishing");
                if (ship.fishHook != null && !ship.fishHook.isRemoved()) {
                    context.entities().add(ship.fishHook);
                    Entity body = ShipMovementGate.taskBody(ship);
                    if (mounted) {
                        helper.assertTrue(body == vehicle[0], "Fishing must use this ship's own mount");
                        helper.assertTrue(body.getY() < 0D, "Fixture: mounted fishing is exercised at negative Y");
                        helper.assertTrue(BlockHelper.getNearbyLiquid(body, false, false, 5, 3) != null,
                                "The mounted working body must find depth-three water at negative Y");
                    }
                    LogUtils.getLogger().info("FISHING_DEFAULT PASS {}", diagnostic(ship));
                    helper.succeed();
                }
            });
            helper.runAtTickTime(800, () -> {
                String end = diagnostic(ship);
                LogUtils.getLogger().info("FISHING_DEFAULT NO_HOOK {}", end);
                helper.assertTrue(ship.fishHook != null && !ship.fishHook.isRemoved(),
                        "Expected a fishing hook after natural ticks; initial={" + initial[0] + "}; final={" + end + "}");
                helper.succeed();
            });
        }, new Vec3(0.5D, 4D, -12.5D), new Vec3(11.5D, 4D, -12.5D),
                new Vec3(0.5D, 4D, -4.5D), new Vec3(11.5D, 4D, -4.5D),
                new Vec3(0.5D, 4D, 4.5D), new Vec3(11.5D, 4D, 4.5D),
                new Vec3(0.5D, 4D, 11.5D), new Vec3(11.5D, 4D, 11.5D));
    }

    private static void order(PointerSingleModeGameTests.TestContext context, BlockPos spot) {
        PointerSingleModeGameTests.invokePacketHandler(new com.lulan.shincolle.network.C2SGUIInputPacket(
                com.lulan.shincolle.network.C2SGUIInputPacket.SetMove,
                new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 1,
                        spot.getX(), spot.getY(), spot.getZ(), 1}), "handleSetMove", context.player());
    }

    private static String diagnostic(BasicEntityShip ship) {
        MovementPoint point = new MovementPoint(ship.getGuardedPos(0) + 0.5D, ship.getGuardedPos(1),
                ship.getGuardedPos(2) + 0.5D);
        Entity body = ShipMovementGate.taskBody(ship);
        double distanceSq = body.distanceToSqr(point.x(), point.y(), point.z());
        Vec3 motion = body.getDeltaMovement();
        BlockPos liquid = BlockHelper.getNearbyLiquid(body, false, false, 5, 3);
        ItemStack rod = ship.getCapaShipInventory().getStackInSlot(22);
        String branch = rod.isEmpty() || rod.getItem() != Items.FISHING_ROD ? "rod"
                : !ship.hasGuardDestination() ? "guard"
                : ship.getGuardedPos(4) != 1 ? "guardType"
                : !ship.isGuardedInCurrentDimension() ? "dimension"
                : distanceSq > 10D ? "distance"
                : Math.abs(motion.x) > 0.1F || Math.abs(motion.z) > 0.1F || motion.y > 0.1F ? "motion"
                : liquid == null ? "water" : "cast";
        Entity vehicle = ship.getVehicle();
        return "name=" + ship.getCustomName().getString() + ", tick=" + ship.tickCount + ", branch=" + branch
                + ", isPassenger=" + ship.isPassenger() + ", ship=" + ship.position()
                + ", vehicle=" + (vehicle == null ? null : vehicle.getClass().getSimpleName() + "@" + vehicle.position())
                + ", guard=(" + point.x() + "," + point.y() + "," + point.z() + "), distanceSq=" + distanceSq
                + ", body=" + body.getClass().getSimpleName() + ", motion=" + motion + ", nearbyLiquid=" + liquid
                + ", floorBodyY=" + Mth.floor(body.getY())
                + ", waterTopY=" + (ship.getGuardedPos(1) - 1)
                + ", follow=" + ship.getStateMinor(ID.M.FollowMin) + "/" + ship.getStateMinor(ID.M.FollowMax)
                + ", width=" + ship.getBbWidth() + ", vehicleWidth=" + (vehicle == null ? null : vehicle.getBbWidth())
                + ", taskMoveReasons=" + ShipMovementGate.taskMove(ship, new TaskMoveRequest.FishingSpot(point)).reasons()
                + ", noFuel=" + ship.getStateFlag(ID.F.NoFuel) + ", grudge=" + ship.getStateMinor(ID.M.NumGrudge)
                + ", task=" + ship.getStateMinor(ID.M.Task) + ", hook=" + ship.fishHook
                + ", path=" + ship.getNavigation().getPath()
                + ", vehiclePath=" + (vehicle instanceof Mob mob ? mob.getNavigation().getPath() : null);
    }
}
