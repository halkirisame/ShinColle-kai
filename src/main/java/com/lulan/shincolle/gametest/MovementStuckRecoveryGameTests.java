package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.FollowDestination;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.TeleportDenial;
import com.lulan.shincolle.ai.domain.movement.TeleportRule;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.FormationHelper;
import com.lulan.shincolle.utility.LogHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Under NEW a ship on its way that stops getting anywhere recovers in order: it re-paths, tries a
 * reachable point beside where it was going, and then gives up (picking items, fighting) or, when
 * following or guarding, teleports once it has been stuck for the cooldown and the landing is safe.
 * A ship that is walking never teleports on time alone.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementStuckRecoveryGameTests {
    /** A ship registers its goals again on its 16th tick; nothing may move it before. */
    private static final int RELEASE_AT = 24;
    /** A teleport moves the ship further than this in one tick; walking never does. */
    private static final double JUMP_SQ = 9D;
    private static final Vec3 SHIP = new Vec3(2.5D, 0D, 2.5D);
    private static final Vec3 NEAR = new Vec3(14.5D, 0D, 2.5D);
    private static final Vec3 FAR = new Vec3(22.5D, 0D, 2.5D);
    private static final Vec3 ITEM = new Vec3(8.5D, 0D, 2.5D);
    /** How far ahead of the ship the walking owner is kept. */
    private static final double AHEAD = 6D;
    private static final Vec3[] ROUTE = {new Vec3(2.5D, 2D, 2.5D), new Vec3(14.5D, 2D, 2.5D),
            new Vec3(22.5D, 2D, 2.5D)};
    private static final Vec3[] WALK_ROUTE = {new Vec3(2.5D, 2D, 2.5D), new Vec3(18.5D, 2D, 2.5D),
            new Vec3(34.5D, 2D, 2.5D), new Vec3(50.5D, 2D, 2.5D), new Vec3(66.5D, 2D, 2.5D)};
    private static final Pattern POINT_PATH = Pattern.compile(":moveTo\\((-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+)\\)");

    private MovementStuckRecoveryGameTests() {
    }

    // ---------- P1: time alone never teleports a ship that is walking ----------

    @GameTest(template = "arena", batch = "isolated_movement_stuck_walking_owner", timeoutTicks = 220)
    public static void followerOfWalkingOwnerDoesNotTeleport(GameTestHelper helper) {
        run(helper, 150, WALK_ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(false, 30);
            scene.release = () -> scene.owner(ahead(helper, ship), ship);
            // the owner walks on as fast as the ship does, so it never catches up
            scene.everyTick = () -> {
                if (scene.owner == null) return;
                Vec3 at = ahead(helper, ship);
                scene.owner.moveTo(at.x, at.y, at.z);
            };
        }, scene -> {
            List<String> problems = new ArrayList<>();
            if (!scene.ran) problems.add("the follow goal never ran");
            if (scene.walked() < 16D) problems.add("walked only " + Math.sqrt(scene.walked()) + " blocks");
            if (!scene.jumps.isEmpty()) problems.add("teleported while walking: " + scene.jumps);
            return problems;
        }, ShipFollowOwnerGoal.class);
    }

    // ---------- P2: never into a landing the ship does not fit ----------

    @GameTest(template = "arena", batch = "isolated_movement_stuck_blocked_landing", timeoutTicks = 260)
    public static void teleportSkipsBlockedLanding(GameTestHelper helper) {
        run(helper, 180, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(true, 30);
            helper.assertTrue(ship.getBbHeight() > 1.3F, "fixture needs a ship taller than the owner's cell allows");
            // the owner stands in a one-block cell two blocks high, walled and roofed all round
            Vec3 at = scene.enclose(FAR, 2, true);
            scene.release = () -> scene.owner(at, ship);
        }, scene -> {
            List<String> problems = new ArrayList<>();
            if (!scene.ran) problems.add("the follow goal never ran");
            if (!scene.jumps.isEmpty()) problems.add("teleported into a blocked landing: " + scene.jumps);
            return problems;
        }, ShipFollowOwnerGoal.class);
    }

    // ---------- P3: around, then give up ----------

    @GameTest(template = "arena", batch = "isolated_movement_stuck_detour", timeoutTicks = 320)
    public static void stuckFollowerDetoursAroundUnreachableOwner(GameTestHelper helper) {
        Vec3[] owner = {null};
        run(helper, 240, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(false, 1_000);
            // the owner stands in a one-block cell the ship cannot enter
            owner[0] = scene.enclose(NEAR, 1, false);
            scene.release = () -> scene.owner(owner[0], ship);
        }, scene -> {
            List<String> problems = new ArrayList<>();
            if (!scene.ran) problems.add("the follow goal never ran");
            if (!scene.jumps.isEmpty()) problems.add("teleported: " + scene.jumps);
            boolean detoured = false;
            for (String call : scene.calls) {
                Matcher point = POINT_PATH.matcher(call);
                if (!point.find()) continue;
                double dx = Double.parseDouble(point.group(1)) - owner[0].x;
                double dz = Double.parseDouble(point.group(3)) - owner[0].z;
                if (dx * dx + dz * dz > 1D) detoured = true;
            }
            if (!detoured) problems.add("never tried a point beside the owner");
            return problems;
        }, ShipFollowOwnerGoal.class);
    }

    @GameTest(template = "arena", batch = "isolated_movement_stuck_pick_item", timeoutTicks = 380)
    public static void pickItemGivesUpUnreachableItem(GameTestHelper helper) {
        int duration = 300;
        run(helper, duration, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(false, 1_000);
            ship.setStateFlag(ID.F.PickItem, true);
            // the pick range grows with FollowMax up to half the attack range
            ship.setStateMinor(ID.M.FollowMax, 10);
            helper.assertTrue(ship.getCapaShipInventory().getFirstSlotForItem() >= 0,
                    "fixture must have a free inventory slot");
            // walled off three blocks all round, so the ship never comes within reach
            Vec3 at = scene.enclose(ITEM, 3, false, true);
            scene.release = () -> scene.item(at);
        }, scene -> {
            List<String> problems = new ArrayList<>();
            if (!scene.ran) problems.add("the pick item goal never ran");
            int since = scene.releasedAt + duration - RELEASE_AT - 100;
            List<String> late = scene.calls.stream().filter(call -> call.contains(":moveTo(")
                    && Integer.parseInt(call.substring(0, call.indexOf(':'))) >= since).toList();
            if (!late.isEmpty()) problems.add("still walking to the item in the last 100 ticks: " + late);
            return problems;
        }, ShipPickItemGoal.class);
    }

    // ---------- a stuck ship still teleports ----------

    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_time", timeoutTicks = 380)
    public static void boxedFollowerTeleportsOnceStuck(GameTestHelper helper) {
        run(helper, 300, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(true, 50);
            scene.release = () -> scene.owner(ground(helper, NEAR), ship);
        }, MovementStuckRecoveryGameTests::jumped, ShipFollowOwnerGoal.class);
    }

    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_far", timeoutTicks = 260)
    public static void boxedFollowerTeleportsWhenFar(GameTestHelper helper) {
        run(helper, 180, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(true, 30);
            scene.release = () -> scene.owner(ground(helper, FAR), ship);
        }, MovementStuckRecoveryGameTests::jumped, ShipFollowOwnerGoal.class);
    }

    /** What the ship's follow state holds when the owner comes close, and what happens to the formation. */
    private enum CloseOwner {
        /** Not in a formation: the stored place is the owner as of the last look. */
        NORMAL(0, 0, false, false, false),
        /** In a formation slot: the stored place is a formation place, which can be far from an owner who is near. */
        FORMATION(1, 5, false, true, false),
        /** The formation's flagship, the owner right beside it: the flagship slot as worked out now is reached. */
        FLAGSHIP(1, 0, false, false, false),
        /**
         * The formation's flagship, the owner near but standing where the flagship slot, a rounded block, is
         * not within the minimum range of the ship: the slot is not reached, so the recovery stays.
         */
        FLAGSHIP_ROUNDED(1, 0, false, true, true),
        /** The formation is switched on just after the last look: the stored place is still the owner. */
        NORMAL_TO_FORMATION(0, 5, true, false, false),
        /** The formation is switched off just after the last look: the stored place is still the formation place. */
        FORMATION_TO_NORMAL(1, 5, true, true, false);

        final int formatType;
        final int formatPos;
        /** Whether the formation type is flipped at the moment the owner comes close. */
        final boolean switches;
        /** Whether the stored place is far from the near owner and so time-teleporting is still expected. */
        final boolean teleports;
        /** Whether the owner stands where the flagship's rounded slot is outside the minimum range. */
        final boolean rounded;

        CloseOwner(int formatType, int formatPos, boolean switches, boolean teleports, boolean rounded) {
            this.formatType = formatType;
            this.formatPos = formatPos;
            this.switches = switches;
            this.teleports = teleports;
            this.rounded = rounded;
        }
    }

    /**
     * The owner comes to the ship's side just after the ship has looked at how far away it is, when
     * the ship is a few ticks short of teleporting for having been stuck. The ship still holds the old
     * distance, but the owner is already there: nothing is left to teleport to, so it must not.
     */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_owner_close", timeoutTicks = 320)
    public static void stuckFollowerDoesNotTeleportToOwnerThatCameCloseAfterLastLook(GameTestHelper helper) {
        ownerCameCloseAfterLastLook(helper, CloseOwner.NORMAL);
    }

    /**
     * The same moment for a ship in a formation whose place is still far off: the owner beside it is not
     * the place it was going to, so being stuck for the cooldown still teleports it.
     */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_formation_owner_close", timeoutTicks = 320)
    public static void stuckFormationFollowerStillTeleportsWhenOwnerCameCloseButItsPlaceIsFar(GameTestHelper helper) {
        ownerCameCloseAfterLastLook(helper, CloseOwner.FORMATION);
    }

    /** The flagship slot as worked out for the owner now is within reach of the ship, so it has arrived. */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_flagship_owner_close", timeoutTicks = 320)
    public static void stuckFormationFlagshipDoesNotTeleportToOwnerThatCameCloseAfterLastLook(GameTestHelper helper) {
        ownerCameCloseAfterLastLook(helper, CloseOwner.FLAGSHIP);
    }

    /**
     * The flagship's slot is the owner's block rounded down, so an owner inside the minimum range can have a slot
     * outside it: the ship has not reached its slot, and being stuck for the cooldown still teleports it.
     */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_flagship_rounded_owner_close",
            timeoutTicks = 320)
    public static void stuckFormationFlagshipStillTeleportsWhenOwnerCameCloseButItsRoundedPlaceIsOutside(
            GameTestHelper helper) {
        ownerCameCloseAfterLastLook(helper, CloseOwner.FLAGSHIP_ROUNDED);
    }

    /** A formation switched on after the last look does not change what the stored place stands for. */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_switch_on_owner_close", timeoutTicks = 320)
    public static void stuckFollowerSwitchedIntoFormationDoesNotTeleportToOwnerThatCameClose(GameTestHelper helper) {
        ownerCameCloseAfterLastLook(helper, CloseOwner.NORMAL_TO_FORMATION);
    }

    /** A formation switched off after the last look does not change what the stored place stands for either. */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_switch_off_owner_close", timeoutTicks = 320)
    public static void stuckFollowerSwitchedOutOfFormationStillTeleportsWhenOwnerCameCloseButItsPlaceIsFar(
            GameTestHelper helper) {
        ownerCameCloseAfterLastLook(helper, CloseOwner.FORMATION_TO_NORMAL);
    }

    private static void ownerCameCloseAfterLastLook(GameTestHelper helper, CloseOwner mode) {
        int[] stepped = {-1, 0, Integer.MAX_VALUE};
        Vec3[] spot = {null};
        List<String> timeTeleports = new ArrayList<>();
        List<String> fixture = new ArrayList<>();
        List<String> fixtureProblems = new ArrayList<>();
        run(helper, 220, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(mode.rounded ? 3 : 2, 1_000);
            // every mode declares its slot, whatever the formation type starts as
            ship.setStateMinor(ID.M.FormatType, mode.formatType);
            ship.setStateMinor(ID.M.FormatPos, mode.formatPos);
            helper.assertTrue(ship.getStateMinor(ID.M.FormatType) == mode.formatType,
                    "fixture must hold the formation type " + mode.formatType);
            helper.assertTrue(ship.getStateMinor(ID.M.FormatPos) == mode.formatPos,
                    "fixture must hold the formation slot " + mode.formatPos);
            scene.release = () -> scene.owner(ground(helper, NEAR), ship);
            scene.everyTick = () -> {
                var running = MovementPlanParityGameTests.selector(ship).getRunningGoals()
                        .filter(wrapped -> wrapped.getGoal() instanceof ShipFollowOwnerGoal).findFirst();
                if (running.isEmpty()) return;
                ShipFollowOwnerGoal goal = (ShipFollowOwnerGoal) running.get().getGoal();
                MovementState.Follow move = followState(goal);
                int untilLook = move.ownerResolveAt() - ship.tickCount;
                if (stepped[0] < 0) {
                    // stuck for a few goal ticks, and the ship has only just looked at its owner
                    if (move.timeTimer() >= 4 && untilLook >= 28 && untilLook <= 32) {
                        stepped[0] = ship.tickCount;
                        stepped[1] = move.timeTimer();
                        // three more goal ticks of being stuck and it would teleport, well before the next look
                        ConfigHandler.shipTeleport[0] = move.timeTimer() + 3;
                        MovementPoint self = new MovementPoint(ship.getX(), ship.getY(), ship.getZ());
                        double placeSq = move.destination().distanceSq(self);
                        double minSq = minDistSq(goal);
                        fixture.add("place " + move.destination() + " ship " + ship.position() + " placeSq " + placeSq
                                + " minSq " + minSq);
                        if (placeSq <= minSq) {
                            fixtureProblems.add("the stored place is already inside the minimum range");
                        }
                        spot[0] = nearOwnerSpot(helper, mode, ship, minSq);
                        if (spot[0] == null) {
                            fixtureProblems.add("no spot where the owner is near and the rounded slot is not");
                            spot[0] = new Vec3(ship.getX() + 1D, ship.getY(), ship.getZ());
                        }
                        scene.owner.moveTo(spot[0].x, spot[0].y, spot[0].z, 0F, 0F);
                        if (scene.owner.distanceToSqr(ship) > minSq) {
                            fixtureProblems.add("the owner is not inside the minimum range now");
                        }
                        if (mode.switches) {
                            int flipped = mode.formatType > 0 ? 0 : 1;
                            scene.formationTeam.setFormation(flipped);
                            if (ship.getStateMinor(ID.M.FormatType) != flipped) {
                                fixtureProblems.add("the formation could not be switched");
                            }
                            if (ship.getStateMinor(ID.M.FormatPos) != mode.formatPos) {
                                fixtureProblems.add("the formation slot changed with the switch");
                            }
                        }
                        if (mode.formatType > 0 && !mode.switches && mode.formatPos == 0) {
                            // the flagship slot as the goal works it out for the owner now
                            double[] slot = FormationHelper.getFormationGuardingPos(ship, scene.owner,
                                    move.anchorMemory().x(), move.anchorMemory().z());
                            double slotSq = new MovementPoint(slot[0], slot[1], slot[2]).distanceSq(self);
                            fixture.add("flagship slot now " + slotSq + " owner " + scene.owner.distanceToSqr(ship));
                            if (mode.rounded && slotSq <= minSq) {
                                fixtureProblems.add("the flagship slot as worked out now is inside the minimum range");
                            }
                            if (!mode.rounded && slotSq > minSq) {
                                fixtureProblems.add("the flagship slot as worked out now is outside the minimum range");
                            }
                        }
                    }
                    return;
                }
                Vec3 keep = mode.rounded ? spot[0] : new Vec3(ship.getX() + 1D, ship.getY(), ship.getZ());
                scene.owner.moveTo(keep.x, keep.y, keep.z, 0F, 0F);
                if (ship.tickCount - stepped[0] > 24) return;
                stepped[2] = Math.min(stepped[2], move.timeTimer());
                ship.shipMovementExecutor().last().ifPresent(request -> {
                    if (request.step() instanceof MovementStep.Teleport teleport
                            && teleport.reason() == MovementReason.TELEPORT_TIME
                            && request.tick() >= stepped[0]) {
                        timeTeleports.add(request.tick() + ":" + teleport.reason());
                    }
                });
            };
        }, scene -> {
            List<String> problems = new ArrayList<>();
            if (!scene.ran) problems.add("the follow goal never ran");
            if (stepped[0] < 0) problems.add("the moment was never reached: the ship was not stuck just after a look");
            problems.addAll(fixtureProblems);
            if (mode.teleports) {
                if (timeTeleports.isEmpty()) {
                    problems.add("never teleported by time while its formation place was far: " + fixture);
                }
            } else if (!timeTeleports.isEmpty()) {
                problems.add("teleported by time to an owner one block away: " + timeTeleports + " " + fixture);
            }
            if (stepped[0] >= 0 && stepped[2] >= stepped[1]) {
                problems.add("the stuck time was never reached and started over: at step-in " + stepped[1]
                        + ", lowest after " + stepped[2]);
            }
            return problems;
        }, ShipFollowOwnerGoal.class);
    }

    /**
     * Where the owner stands once it comes close: one block beside the ship, or, for the rounded flagship, a free
     * spot inside the pen whose block, which the flagship slot is rounded down to, is farther than the minimum
     * range from the ship while the owner itself is well inside it. Null when there is none.
     */
    private static Vec3 nearOwnerSpot(GameTestHelper helper, CloseOwner mode, BasicEntityShip ship, double minSq) {
        if (!mode.rounded) {
            return new Vec3(ship.getX() + 1D, ship.getY(), ship.getZ());
        }
        Vec3 best = null;
        double bestGap = 0.5D;
        for (int ix = -25; ix <= 25; ix++) {
            for (int iz = -25; iz <= 25; iz++) {
                double dx = ix / 10D;
                double dz = iz / 10D;
                if (dx * dx + dz * dz > minSq - 0.4D) continue;
                Vec3 spot = new Vec3(ship.getX() + dx, ship.getY(), ship.getZ() + dz);
                BlockPos cell = BlockPos.containing(spot);
                if (!helper.getLevel().getBlockState(cell).isAir()
                        || !helper.getLevel().getBlockState(cell.above()).isAir()) continue;
                double rx = Math.floor(spot.x) - ship.getX();
                double rz = Math.floor(spot.z) - ship.getZ();
                double slotSq = rx * rx + rz * rz;
                if (slotSq - minSq > bestGap) {
                    bestGap = slotSq - minSq;
                    best = spot;
                }
            }
        }
        return best;
    }

    private static double minDistSq(ShipFollowOwnerGoal goal) {
        try {
            Field field = ShipFollowOwnerGoal.class.getDeclaredField("minDistSq");
            field.setAccessible(true);
            return field.getDouble(goal);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to read the follow minimum range: " + error);
        }
    }

    private static MovementState.Follow followState(ShipFollowOwnerGoal goal) {
        try {
            Field field = ShipFollowOwnerGoal.class.getDeclaredField("move");
            field.setAccessible(true);
            return (MovementState.Follow) field.get(goal);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to read the follow state: " + error);
        }
    }

    /** Where the flagship's owner stands when the stuck teleport reaches for the flagship slot. */
    private enum SlotOwner {
        /** Held from before a dimension change, in the other level's unloaded ground. */
        OTHER_DIMENSION,
        /** In the ship's own level, in ground that is not loaded. */
        UNLOADED,
        /** In the ship's own level, in loaded ground. */
        LOADED,
        /** In loaded ground at the edge of a chunk whose neighbour, where the safe-spot search goes, is not loaded. */
        LOADED_EDGE,
        /** In loaded ground, but the ship has just been moved off the flagship's slot. */
        NOT_FLAGSHIP
    }

    /** Where the owner stands for {@link SlotOwner#LOADED_EDGE}: the last block of chunk (500000, 500000) in z. */
    private static final Vec3 EDGE_OWNER = new Vec3(8_000_008.9D, 64D, 8_000_015.9D);

    /**
     * The flagship's slot is worked out by reading the blocks around the owner, which loads the chunk it stands in.
     * The ship still holds the owner it resolved before the owner went through a portal, so on the tick that
     * judges the stuck teleport it must not look anything up in the other level; the teleport itself is asked for
     * and refused as before.
     */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_flagship_other_dimension",
            timeoutTicks = 320)
    public static void stuckFlagshipDoesNotLoadTheWorldOfAnOwnerThatChangedDimension(GameTestHelper helper) {
        flagshipSlotLookup(helper, SlotOwner.OTHER_DIMENSION);
    }

    /** An owner in unloaded ground of the ship's own level is not looked up either: the slot distance is infinite. */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_flagship_unloaded_owner", timeoutTicks = 320)
    public static void stuckFlagshipSlotDistanceIsInfiniteForAnOwnerInUnloadedGround(GameTestHelper helper) {
        flagshipSlotLookup(helper, SlotOwner.UNLOADED);
    }

    /** An owner in loaded ground of the ship's own level still gets its slot measured, as the stuck teleport needs. */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_flagship_loaded_owner", timeoutTicks = 320)
    public static void stuckFlagshipSlotDistanceIsMeasuredForAnOwnerInLoadedGround(GameTestHelper helper) {
        flagshipSlotLookup(helper, SlotOwner.LOADED);
    }

    /**
     * The owner's own chunk is loaded but it stands at its edge, and its own column has no safe spot, so the safe-spot
     * search round it would go on into the next chunk, which is not loaded. The tick that judges the stuck teleport
     * must not load it; the teleport is still asked for, and refused here by the cooldown of the teleport just before.
     */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_flagship_edge_owner", timeoutTicks = 320)
    public static void stuckFlagshipDoesNotLoadTheChunkNextToAnOwnerAtTheEdgeOfLoadedGround(GameTestHelper helper) {
        flagshipSlotLookup(helper, SlotOwner.LOADED_EDGE);
    }

    /** A ship just moved off the flagship's slot does not get the flagship slot measured: it is infinite. */
    @GameTest(template = "arena", batch = "isolated_movement_stuck_follow_flagship_slot_left", timeoutTicks = 320)
    public static void stuckFlagshipSlotDistanceIsInfiniteOnceTheShipIsNoLongerTheFlagship(GameTestHelper helper) {
        flagshipSlotLookup(helper, SlotOwner.NOT_FLAGSHIP);
    }

    private static void flagshipSlotLookup(GameTestHelper helper, SlotOwner mode) {
        int[] stepped = {-1};
        // the loads of the other level are listened for, whatever the loaded chunks show
        ServerLevel[] foreign = {null};
        BlockPos[] spot = {null};
        List<String> foreignLoads = new ArrayList<>();
        List<String> timeTeleports = new ArrayList<>();
        List<String> fixture = new ArrayList<>();
        List<String> fixtureProblems = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        run(helper, 220, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(2, 1_000);
            ship.setStateMinor(ID.M.FormatType, 1);
            ship.setStateMinor(ID.M.FormatPos, 0);
            helper.assertTrue(ship.getStateMinor(ID.M.FormatType) == 1, "fixture must be in a formation");
            helper.assertTrue(ship.getStateMinor(ID.M.FormatPos) == 0, "fixture must be the flagship");
            scene.release = () -> {
                scene.owner(ground(helper, NEAR), ship);
                if (mode == SlotOwner.LOADED_EDGE) {
                    // a teleport that goes through, so that the one judged later is refused by the cooldown before the
                    // executor reads anything round a landing
                    ShipMovementExecutor.run(ship, MovementPlan.of(new MovementStep.Teleport(MovementBody.SELF,
                            new MovementPoint(ship.getX(), ship.getY(), ship.getZ()), MovementReason.TELEPORT_TIME,
                            ShipCommandStateAdapter.handle(ship).dimension())));
                    helper.assertTrue(ship.shipMovementExecutor().lastDenials().isEmpty(),
                            "fixture teleport must go through: " + ship.shipMovementExecutor().lastDenials());
                }
            };
            scene.everyTick = () -> {
                var running = MovementPlanParityGameTests.selector(ship).getRunningGoals()
                        .filter(wrapped -> wrapped.getGoal() instanceof ShipFollowOwnerGoal).findFirst();
                if (running.isEmpty()) return;
                ShipFollowOwnerGoal goal = (ShipFollowOwnerGoal) running.get().getGoal();
                MovementState.Follow move = followState(goal);
                Vec3 beside = new Vec3(ship.getX() + 1D, ship.getY(), ship.getZ());
                if (stepped[0] >= 0) {
                    scene.owner.moveTo(beside.x, beside.y, beside.z, 0F, 0F);
                    return;
                }
                int untilLook = move.ownerResolveAt() - ship.tickCount;
                // stuck for a few goal ticks, and the ship has only just looked at its owner
                if (move.timeTimer() < 4 || untilLook < 28 || untilLook > 32) return;
                stepped[0] = ship.tickCount;
                if (move.destinationKind() != FollowDestination.FLAGSHIP_PLACE) {
                    fixtureProblems.add("the stored place is not the flagship slot: " + move.destinationKind());
                }
                helper.assertTrue(scene.owner.level() == helper.getLevel(), "fixture owner must start in its level");
                scene.owner.moveTo(beside.x, beside.y, beside.z, 0F, 0F);
                switch (mode) {
                    case OTHER_DIMENSION -> {
                        // the next goal tick is the one that judges the stuck teleport
                        ConfigHandler.shipTeleport[0] = move.timeTimer();
                        foreign[0] = helper.getLevel().getServer().getLevel(Level.NETHER);
                        helper.assertTrue(foreign[0] != null, "fixture needs the nether");
                        // the other level is not as deep as the arena: a block below its floor reads as void and loads
                        // nothing, so the owner is moved to where its blocks are
                        Vec3 up = new Vec3(beside.x, 64D, beside.z);
                        helper.assertTrue(!foreign[0].isOutsideBuildHeight(BlockPos.containing(up)),
                                "fixture owner must stand inside the other level");
                        scene.owner.moveTo(up.x, up.y, up.z, 0F, 0F);
                        spot[0] = BlockPos.containing(up);
                        try {
                            scene.owner.setServerLevel(foreign[0]);
                            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(scene.owner,
                                    Level.OVERWORLD, Level.NETHER));
                            if (ownerOf(goal) != scene.owner) {
                                fixtureProblems.add("the goal no longer holds the owner that changed dimension");
                            }
                            if (scene.owner.level() == helper.getLevel()) {
                                fixtureProblems.add("the owner did not change level");
                            }
                            if (!goal.canContinueToUse()) {
                                fixtureProblems.add("the goal stops on its own once the owner has changed dimension");
                            }
                            if (foreign[0].hasChunkAt(spot[0])) {
                                fixtureProblems.add("the owner's chunk in the other level is loaded before the tick");
                            }
                            Consumer<ChunkEvent.Load> watch = load -> {
                                if (load.getLevel() == foreign[0]) foreignLoads.add(load.getChunk().getPos().toString());
                            };
                            MinecraftForge.EVENT_BUS.addListener(watch);
                            try {
                                goal.tick();
                            } finally {
                                MinecraftForge.EVENT_BUS.unregister(watch);
                            }
                        } finally {
                            // the level never ticks it while it is in the other one
                            scene.owner.setServerLevel(helper.getLevel());
                            scene.owner.moveTo(beside.x, beside.y, beside.z, 0F, 0F);
                        }
                        ship.shipMovementExecutor().last().ifPresent(request -> {
                            if (request.step() instanceof MovementStep.Teleport teleport
                                    && teleport.reason() == MovementReason.TELEPORT_TIME
                                    && request.tick() >= stepped[0]) {
                                timeTeleports.add(request.tick() + ":" + teleport.reason());
                            }
                        });
                        if (timeTeleports.isEmpty()) {
                            problems.add("the stuck teleport was never asked for, so the slot lookup was not reached");
                        }
                    }
                    case UNLOADED -> {
                        // not in any level's list of players, so that putting it far away loads nothing
                        FakePlayer far = FakePlayerFactory.get(helper.getLevel(),
                                new GameProfile(UUID.randomUUID(), "flagship_far_owner"));
                        far.moveTo(beside.x + 400_000D, beside.y, beside.z, 0F, 0F);
                        BlockPos farSpot = far.blockPosition();
                        if (helper.getLevel().hasChunkAt(farSpot)) {
                            fixtureProblems.add("the owner's chunk is loaded");
                        }
                        double slotSq = flagshipSlotDistanceSq(goal, far);
                        fixture.add("slot distance " + slotSq);
                        if (slotSq != Double.POSITIVE_INFINITY) {
                            problems.add("the slot distance for an owner in unloaded ground is " + slotSq);
                        }
                        if (helper.getLevel().hasChunkAt(farSpot)) {
                            problems.add("looking up the slot loaded the owner's chunk");
                        }
                    }
                    case LOADED -> {
                        BlockPos ownerSpot = scene.owner.blockPosition();
                        if (!helper.getLevel().hasChunkAt(ownerSpot)) {
                            fixtureProblems.add("the owner's chunk is not loaded");
                        }
                        double slotSq = flagshipSlotDistanceSq(goal, scene.owner);
                        double[] slot = FormationHelper.getFormationGuardingPos(ship, scene.owner,
                                move.anchorMemory().x(), move.anchorMemory().z());
                        double expected = new MovementPoint(slot[0], slot[1], slot[2])
                                .distanceSq(new MovementPoint(ship.getX(), ship.getY(), ship.getZ()));
                        fixture.add("slot distance " + slotSq + " expected " + expected);
                        if (Double.isInfinite(slotSq) || Math.abs(slotSq - expected) > 1.0E-9D) {
                            problems.add("the slot distance is " + slotSq + ", expected " + expected);
                        }
                    }
                    case LOADED_EDGE -> {
                        // the next goal tick is the one that judges the stuck teleport
                        ConfigHandler.shipTeleport[0] = move.timeTimer();
                        ServerLevel level = helper.getLevel();
                        BlockPos ownerBlock = BlockPos.containing(EDGE_OWNER);
                        BlockPos next = ownerBlock.south();
                        helper.assertTrue(!level.isOutsideBuildHeight(ownerBlock), "fixture owner must stand inside");
                        helper.assertTrue((ownerBlock.getZ() >> 4) != (next.getZ() >> 4),
                                "fixture owner must stand at the edge of its chunk");
                        // only the owner's own chunk is loaded, and its column is set through that chunk alone, so
                        // nothing touches the next one: the column has no safe spot, and the search goes on into it
                        LevelChunk chunk = level.getChunk(ownerBlock.getX() >> 4, ownerBlock.getZ() >> 4);
                        List<BlockPos> column = new ArrayList<>();
                        for (int dy = -4; dy <= 4; dy++) column.add(ownerBlock.above(dy));
                        List<BlockState> before = column.stream().map(chunk::getBlockState).toList();
                        List<String> loads = new ArrayList<>();
                        try {
                            for (BlockPos pos : column) chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                            scene.owner.moveTo(EDGE_OWNER.x, EDGE_OWNER.y, EDGE_OWNER.z, 0F, 0F);
                            if (ownerOf(goal) != scene.owner) {
                                fixtureProblems.add("the goal no longer holds the owner");
                            }
                            if (!goal.canContinueToUse()) {
                                fixtureProblems.add("the goal stops on its own once the owner has moved");
                            }
                            if (!level.hasChunkAt(ownerBlock)) {
                                fixtureProblems.add("the owner's chunk is not loaded before the tick");
                            }
                            if (level.hasChunkAt(next)) {
                                fixtureProblems.add("the chunk next to the owner is loaded before the tick");
                            }
                            Consumer<ChunkEvent.Load> watch = load -> {
                                if (load.getLevel() == level) loads.add(load.getChunk().getPos().toString());
                            };
                            MinecraftForge.EVENT_BUS.addListener(watch);
                            try {
                                goal.tick();
                            } finally {
                                MinecraftForge.EVENT_BUS.unregister(watch);
                            }
                        } finally {
                            for (int i = 0; i < column.size(); i++) chunk.setBlockState(column.get(i), before.get(i), false);
                            scene.owner.moveTo(beside.x, beside.y, beside.z, 0F, 0F);
                        }
                        boolean nextLoaded = level.hasChunkAt(next);
                        ship.shipMovementExecutor().last().ifPresent(request -> {
                            if (request.step() instanceof MovementStep.Teleport teleport
                                    && teleport.reason() == MovementReason.TELEPORT_TIME
                                    && request.tick() >= stepped[0]) {
                                timeTeleports.add(request.tick() + ":" + teleport.reason());
                            }
                        });
                        Set<TeleportDenial> denials = ship.shipMovementExecutor().lastDenials();
                        fixture.add("loads " + loads + " next loaded " + nextLoaded + " denials " + denials);
                        if (!loads.isEmpty()) problems.add("the goal's tick loaded chunks: " + loads);
                        if (nextLoaded) problems.add("the goal's tick loaded the chunk next to the owner");
                        if (timeTeleports.isEmpty()) {
                            problems.add("the stuck teleport was never asked for, so the slot lookup was not reached");
                        } else if (!denials.contains(TeleportDenial.COOLDOWN)) {
                            fixtureProblems.add("the stuck teleport was not refused by the cooldown: " + denials);
                        }
                    }
                    case NOT_FLAGSHIP -> {
                        double asFlagship = flagshipSlotDistanceSq(goal, scene.owner);
                        double offTheSlot;
                        scene.formationTeam.setSlot(3);
                        try {
                            if (ship.getStateMinor(ID.M.FormatPos) != 3) {
                                fixtureProblems.add("the ship could not be moved off the flagship's slot");
                            }
                            offTheSlot = flagshipSlotDistanceSq(goal, scene.owner);
                        } finally {
                            scene.formationTeam.setSlot(0);
                        }
                        fixture.add("slot distance as the flagship " + asFlagship + " off the slot " + offTheSlot);
                        if (Double.isInfinite(asFlagship)) {
                            fixtureProblems.add("the slot distance is not measured even as the flagship");
                        }
                        if (offTheSlot != Double.POSITIVE_INFINITY) {
                            problems.add("the slot distance off the flagship's slot is " + offTheSlot);
                        }
                    }
                }
            };
        }, scene -> {
            List<String> all = new ArrayList<>();
            if (!scene.ran) all.add("the follow goal never ran");
            if (stepped[0] < 0) all.add("the moment was never reached: the ship was not stuck just after a look");
            all.addAll(problems);
            if (!foreignLoads.isEmpty()) {
                all.add("the goal's tick loaded chunks of the other level: " + foreignLoads);
            }
            all.addAll(fixtureProblems);
            fixture.add("timeTeleports " + timeTeleports);
            LogHelper.info("Flagship slot lookup (" + mode + "): " + fixture);
            return all;
        }, ShipFollowOwnerGoal.class);
    }

    /** The follow goal's slot distance for {@code owner}, as asked on a tick that judges the stuck teleport. */
    private static double flagshipSlotDistanceSq(ShipFollowOwnerGoal goal, FakePlayer owner) {
        try {
            Field held = ShipFollowOwnerGoal.class.getDeclaredField("owner");
            held.setAccessible(true);
            Object before = held.get(goal);
            Method method = ShipFollowOwnerGoal.class.getDeclaredMethod("flagshipPlaceDistanceSq", TeleportRule.class);
            method.setAccessible(true);
            held.set(goal, owner);
            try {
                return (double) method.invoke(goal, new TeleportRule(true, 0, ConfigHandler.shipTeleport[1]));
            } finally {
                held.set(goal, before);
            }
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to ask the follow goal for the slot distance: " + error);
        }
    }

    private static Object ownerOf(ShipFollowOwnerGoal goal) {
        try {
            Field field = ShipFollowOwnerGoal.class.getDeclaredField("owner");
            field.setAccessible(true);
            return field.get(goal);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to read the follow owner: " + error);
        }
    }

    @GameTest(template = "arena", batch = "isolated_movement_stuck_guard", timeoutTicks = 300)
    public static void boxedGuardTeleportsOnceStuck(GameTestHelper helper) {
        run(helper, 220, ROUTE, scene -> {
            BasicEntityShip ship = scene.ship(true, 50);
            BlockPos guardPos = BlockPos.containing(ground(helper, NEAR));
            scene.release = () -> {
                var id = helper.getLevel().dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(guardPos.getX(), guardPos.getY(), guardPos.getZ()), false)));
            };
        }, MovementStuckRecoveryGameTests::jumped, ShipGuardingGoal.class);
    }

    private static List<String> jumped(Scene scene) {
        List<String> problems = new ArrayList<>();
        if (!scene.ran) problems.add("the watched goal never ran");
        if (scene.jumps.isEmpty()) problems.add("never teleported");
        return problems;
    }

    // ---------- running a scene ----------

    private static void run(GameTestHelper helper, int duration, Vec3[] route, Consumer<Scene> setup,
                            Function<Scene, List<String>> check, Class<?> goalType) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            int start = (int) helper.getTick() + 1;
            Scene[] scene = {null};
            helper.runAtTickTime(start, () -> {
                scene[0] = new Scene(helper, goalType);
                scene[0].guard(() -> setup.accept(scene[0]));
            });
            for (int tick = start + 1; tick < start + duration; tick++) {
                helper.runAtTickTime(tick, () -> scene[0].guard(scene[0]::poll));
            }
            helper.runAtTickTime(start + duration, () -> scene[0].guard(() -> {
                List<String> problems = check.apply(scene[0]);
                String report = scene[0].toString();
                scene[0].close();
                helper.assertTrue(problems.isEmpty(), problems + "\n " + report);
                // the evidence of a passing run, for the change note and review
                LogHelper.info("Movement stuck recovery (" + goalType.getSimpleName() + "): " + report);
                helper.succeed();
            }));
        }, route);
    }

    /** The absolute point standing on the ground at {@code relative}'s column. */
    private static Vec3 ground(GameTestHelper helper, Vec3 relative) {
        return MovementPlanParityGameTests.ground(helper, relative);
    }

    /** On the ground {@link #AHEAD} blocks further along x than the ship. */
    private static Vec3 ahead(GameTestHelper helper, Entity ship) {
        double x = ship.getX() + AHEAD;
        int y = helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x),
                (int) Math.floor(ship.getZ()));
        return new Vec3(x, y, ship.getZ());
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final Class<?> goalType;
        private final ShipAiAuthorityOverride authority;
        private final GameTestEntities entities;
        private final int teleportCooldown = ConfigHandler.shipTeleport[0];
        private final List<String> calls = new ArrayList<>();
        private final List<String> jumps = new ArrayList<>();
        private final List<BlockPos> barriers = new ArrayList<>();
        private Runnable release = () -> { };
        private Runnable everyTick = () -> { };
        private BasicEntityShip ship;
        private FakePlayer owner;
        private FormationGameTestTeam formationTeam;
        private Vec3 origin;
        private Vec3 last;
        private int releasedAt = -1;
        private boolean ran;
        private boolean closed;

        Scene(GameTestHelper helper, Class<?> goalType) {
            this.helper = helper;
            this.goalType = goalType;
            this.authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
            this.entities = GameTestEntities.open(helper);
        }

        BasicEntityShip ship(boolean boxed, int teleportCooldown) {
            return this.ship(boxed ? 2 : 0, teleportCooldown);
        }

        /** {@code penRadius} above 0 rings the ship with barriers that many blocks out, so it cannot leave on foot. */
        BasicEntityShip ship(int penRadius, int teleportCooldown) {
            ConfigHandler.shipTeleport[0] = teleportCooldown;
            this.helper.assertTrue(ConfigHandler.canTeleport(), "fixture needs teleporting enabled");
            BasicEntityShip created = MovementPlanParityGameTests.friendly(this.helper, this.entities, SHIP);
            this.ship = created;
            this.origin = created.position();
            this.last = this.origin;
            MovementPlanParityGameTests.navigation(created).recordCalls(this.calls);
            if (penRadius > 0) {
                // a ring of barriers the ship cannot leave on foot
                BlockPos center = BlockPos.containing(created.position());
                for (int dx = -penRadius; dx <= penRadius; dx++) {
                    for (int dz = -penRadius; dz <= penRadius; dz++) {
                        if (Math.abs(dx) < penRadius && Math.abs(dz) < penRadius) continue;
                        for (int dy = 0; dy < 3; dy++) this.barrier(center.offset(dx, dy, dz));
                    }
                }
            }
            return created;
        }

        Vec3 enclose(Vec3 relative, int radius, boolean roofed) {
            return this.enclose(relative, radius, roofed, false);
        }

        /**
         * Barriers round the column at {@code relative}, {@code radius} blocks out, and returns the
         * ground point at its centre. Solid fills every block but the centre's; otherwise only the
         * outer ring is built. Roofed closes the centre two blocks up.
         */
        Vec3 enclose(Vec3 relative, int radius, boolean roofed, boolean hollow) {
            Vec3 at = ground(this.helper, relative);
            BlockPos center = BlockPos.containing(at);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean ring = Math.abs(dx) == radius || Math.abs(dz) == radius;
                    boolean middle = dx == 0 && dz == 0;
                    for (int dy = 0; dy < 4; dy++) {
                        if (middle) {
                            if (roofed && dy >= 2) this.barrier(center.offset(dx, dy, dz));
                        } else if (ring || !hollow) {
                            if (dy < 3 || roofed) this.barrier(center.offset(dx, dy, dz));
                        }
                    }
                }
            }
            return at;
        }

        private void barrier(BlockPos pos) {
            this.helper.getLevel().setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
            this.barriers.add(pos);
        }

        void owner(Vec3 at, BasicEntityShip ship) {
            this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "movement_stuck_owner"));
            this.owner.moveTo(at.x, at.y, at.z, 0F, 0F);
            this.helper.getLevel().addNewPlayer(this.owner);
            ship.setOwnerUUID(this.owner.getUUID());
            this.formationTeam = new FormationGameTestTeam(this.owner, ship, this.entities);
        }

        void item(Vec3 at) {
            ItemEntity item = this.entities.add(new ItemEntity(this.helper.getLevel(), at.x, at.y, at.z,
                    new ItemStack(Items.STICK)));
            item.setDeltaMovement(Vec3.ZERO);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(item), "failed to add item");
        }

        void guard(Runnable body) {
            try {
                body.run();
            } catch (Throwable error) {
                this.close();
                throw error;
            }
        }

        void poll() {
            // wander draws from the entity random and would walk the ship off on its own
            MovementPlanParityGameTests.selector(this.ship).removeAllGoals(goal -> goal instanceof ShipWanderGoal);
            if (this.releasedAt < 0 && this.ship.tickCount >= RELEASE_AT) {
                this.releasedAt = this.ship.tickCount;
                this.release.run();
            }
            if (this.releasedAt >= 0) this.everyTick.run();
            if (MovementPlanParityGameTests.selector(this.ship).getRunningGoals()
                    .anyMatch(wrapped -> this.goalType.isInstance(wrapped.getGoal()))) {
                this.ran = true;
            }
            Vec3 position = this.ship.position();
            if (position.distanceToSqr(this.last) > JUMP_SQ) {
                this.jumps.add(this.ship.tickCount + ":jump(" + MovementPlanParityGameTests.fmt(position.x) + ","
                        + MovementPlanParityGameTests.fmt(position.y) + ","
                        + MovementPlanParityGameTests.fmt(position.z) + ")");
            }
            this.last = position;
        }

        /** Squared distance from where the ship started, ignoring teleports only when there were none. */
        double walked() {
            return this.ship.position().distanceToSqr(this.origin);
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            try {
                if (this.ship != null) MovementPlanParityGameTests.navigation(this.ship).recordCalls(null);
                if (this.formationTeam != null) this.formationTeam.close();
                this.entities.close();
                if (this.owner != null) {
                    this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                }
                for (BlockPos pos : this.barriers) {
                    this.helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            } finally {
                ConfigHandler.shipTeleport[0] = this.teleportCooldown;
                this.authority.close();
            }
        }

        @Override
        public String toString() {
            return "released=" + this.releasedAt + " calls=" + this.calls + " jumps=" + this.jumps
                    + " at=" + (this.ship == null ? "-" : this.ship.position());
        }
    }
}
