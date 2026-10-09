package com.lulan.shincolle.utility;

import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.waypoint.WaypointFacts;
import com.lulan.shincolle.ai.domain.waypoint.WaypointLinks;
import com.lulan.shincolle.ai.domain.waypoint.WaypointProgress;
import com.lulan.shincolle.ai.domain.waypoint.WaypointStay;
import com.lulan.shincolle.ai.domain.waypoint.WaypointStep;
import com.lulan.shincolle.ai.domain.waypoint.WaypointTraversal;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipFloating;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper for ship entity movement and navigation.
 * <p>
 * Handles water depth calculation, floating behavior, and
 * custom movement in fluids.
 * <p>
 * Ported from 1.10.2 EntityHelper.
 */
public class EntityHelper {
    private static final double SURFACE_Y_OFFSET = 0.1D;
    private static final double MIN_TRAVEL_VEC_SQR = 1.0E-6D;
    private static final double FLOAT_UP_THRESHOLD = 0.1D;
    private static final double FLOAT_DOWN_THRESHOLD = -0.1D;
    private static final double FLOAT_UP_ACCEL = 0.04D;
    private static final double FLOAT_UP_MAX_VELOCITY = 0.12D;
    private static final double FLOAT_DOWN_ACCEL = 0.02D;
    private static final double FLOAT_DOWN_MAX_VELOCITY = -0.08D;
    private static final double FLOAT_HOVER_DAMPING = 0.8D;
    private static final double WATER_DRAG = 0.8D;
    private static final double COLLISION_BUMP_UP_VELOCITY = 0.3D;
    private static final double GLOBAL_SCAN_LIMIT = 30000000D;
    private static final int SPAWN_OFFSET_MIN = 20;
    private static final int SPAWN_OFFSET_RANGE = 30;
    private static final int BOSS_SPAWN_OFFSET_MIN = 32;
    private static final int BOSS_SPAWN_OFFSET_RANGE = 32;

    /**
     * Update ship navigator - called every tick on server side.
     * <p>
     * Handles:
     * - Water depth calculation for floating
     * - Vertical position adjustment in water
     * - Custom path navigation ticking (water/air pathfinding)
     * - Custom move helper ticking (Y-axis movement)
     */
    public static void updateShipNavigator(BasicEntityShip ship) {
        updateShipDepth(ship);
        updateShipFloating(ship);
    }

    /**
     * Update ship navigator for hostile ships.
     */
    public static void updateShipNavigator(Mob ship) {
        if (ship instanceof IShipFloating floating) {
            updateShipDepth(floating);
            updateShipFloatingGeneric(ship, floating);
        }
    }

    /**
     * Advance a friendly ship from an arrived waypoint to its linked destination.
     * This is called from {@link BasicEntityShip#aiStep()}, not a Goal, so every
     * ship receives the same 16-tick cadence.
     *
     * @return true when the caller must synchronize the flag ship's changed guard target
     */
    public static boolean updateWaypointMove(BasicEntityShip ship) {
        if (ShipMovementGate.active()) {
            return updateWaypointMoveNew(ship);
        }
        if (!ship.hasGuardDestination() || !ship.isGuardedInCurrentDimension()
                || ship.getGuardedEntity() != null || ship.getIsSitting()
                || ship.getIsLeashed() || ship.isPassenger()) {
            return false;
        }

        if (ship.getStateMinor(ID.M.FormatType) > 0 && ship.getStateMinor(ID.M.FormatPos) > 0) {
            return false;
        }

        BlockPos current = new BlockPos(ship.getGuardedPos(0), ship.getGuardedPos(1), ship.getGuardedPos(2));
        if (!(ship.level().getBlockEntity(current) instanceof TileEntityWaypoint waypoint)
                || ship.distanceToSqr(Vec3.atCenterOf(current)) >= 9D) {
            return false;
        }

        int stayMax = Math.max(ship.getWpStayTimeMax(),
                BasicEntityShip.wpStayTime2Ticks(waypoint.getWpStayTime()));
        if (ship.getWpStayTime() < stayMax) {
            ship.setWpStayTime(ship.getWpStayTime() + 16);
            return false;
        }

        ship.setWpStayTime(0);
        BlockPos destination = null;
        if (waypoint.hasNextWaypoint()) {
            BlockPos next = waypoint.getNextWaypoint();
            if (ship.hasLastWaypoint() && next.equals(ship.getLastWaypoint()) && waypoint.hasLastWaypoint()) {
                destination = waypoint.getLastWaypoint();
            } else {
                destination = next;
            }
        }

        setLastWaypointForShipAndPassengers(ship, current);
        if (destination == null) {
            return false;
        }

        applyWaypointDestination(ship, destination);
        return true;
    }

    /**
     * The same traversal, decided by {@link WaypointTraversal}: this gathers the facts, the domain
     * decides, and the result is written back to the fields it came from.
     */
    private static boolean updateWaypointMoveNew(BasicEntityShip ship) {
        CommandPos current = new CommandPos(ship.getGuardedPos(0), ship.getGuardedPos(1), ship.getGuardedPos(2));
        BlockPos currentBlock = new BlockPos(current.x(), current.y(), current.z());
        boolean blockGuard = ship.hasGuardDestination();
        boolean sameDimension = ship.isGuardedInCurrentDimension();
        boolean guardingEntity = ship.getGuardedEntity() != null;
        boolean sitting = ship.getIsSitting();
        BasicEntityMount mount = ship.getVehicle() instanceof BasicEntityMount own ? own : null;
        boolean leashed = ship.getIsLeashed() || mount != null && mount.isLeashed();
        boolean riding = ship.isPassenger();
        boolean formationMember = !com.lulan.shincolle.ai.domain.formation.FormationLayoutPlanner.mayAdvanceRoute(
                ship.formationState().current());
        boolean excluded = !blockGuard || !sameDimension || guardingEntity || sitting || leashed || riding && mount == null
                || formationMember;
        // The block entity is read only for a ship that can take part: reading it loads its chunk.
        TileEntityWaypoint waypoint = excluded ? null
                : ship.level().getBlockEntity(currentBlock) instanceof TileEntityWaypoint tile ? tile : null;
        WaypointLinks links = waypoint == null ? WaypointLinks.NONE : new WaypointLinks(
                waypoint.hasNextWaypoint() ? Optional.of(commandPos(waypoint.getNextWaypoint())) : Optional.empty(),
                waypoint.hasLastWaypoint() ? Optional.of(commandPos(waypoint.getLastWaypoint())) : Optional.empty());
        int elapsed = ship.getWpStayTime();
        BlockPos stayAt = ship.getWpStayAt();
        WaypointProgress progress = new WaypointProgress(
                ship.hasLastWaypoint() ? Optional.of(commandPos(ship.getLastWaypoint())) : Optional.empty(),
                elapsed > 0 && stayAt != null ? Optional.of(new WaypointStay(commandPos(stayAt), elapsed))
                        : Optional.empty());
        WaypointFacts facts = new WaypointFacts(blockGuard, sameDimension, guardingEntity,
                sitting, leashed, riding, mount != null, formationMember, current,
                waypoint != null, links,
                waypoint == null ? 0 : BasicEntityShip.wpStayTime2Ticks(waypoint.getWpStayTime()),
                ship.getWpStayTimeMax(), (mount != null ? mount : ship).distanceToSqr(Vec3.atCenterOf(currentBlock)),
                progress);
        // the guard goal reads this mark, so it never reads the block entity itself
        ship.setPassThroughWaypoint(WaypointTraversal.passThrough(facts));
        WaypointStep step = WaypointTraversal.step(facts);
        ship.recordWaypointCheck(step);

        if (step instanceof WaypointStep.Wait wait) {
            ship.setWpStayTime(wait.next().stay().map(WaypointStay::elapsedTicks).orElse(0));
            ship.setWpStayAt(currentBlock);
            return false;
        }
        if (step instanceof WaypointStep.Hold) {
            ship.setWpStayTime(0);
            ship.setWpStayAt(null);
            setLastWaypointForShipAndPassengers(ship, currentBlock);
            return false;
        }
        if (step instanceof WaypointStep.Advance advance) {
            ship.setWpStayTime(0);
            ship.setWpStayAt(null);
            setLastWaypointForShipAndPassengers(ship, currentBlock);
            applyWaypointDestination(ship,
                    new BlockPos(advance.destination().x(), advance.destination().y(), advance.destination().z()));
            return true;
        }
        return false;
    }

    private static CommandPos commandPos(BlockPos pos) {
        return new CommandPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void setLastWaypointForShipAndPassengers(BasicEntityShip ship, BlockPos current) {
        ship.setLastWaypoint(current);
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof BasicEntityShip passengerShip) {
                passengerShip.setLastWaypoint(current);
            }
        }
    }

    private static void applyWaypointDestination(BasicEntityShip ship, BlockPos destination) {
        int formationType = ShipCommandStateAdapter.isNew()
                ? com.lulan.shincolle.ai.ShipFormationStateAdapter.active(ship).map(a -> a.pattern().legacyId()).orElse(0)
                : ship.getStateMinor(ID.M.FormatType);
        if (formationType <= 0) {
            applyWaypointGuard(ship, destination);
            return;
        }

        ServerPlayer owner = ServerDataManager.getPlayerByUID(ship.getPlayerUID());
        CapaTeitoku capa = owner == null ? null
                : owner.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        int team = capa == null ? -1 : capa.findTeamOfShip(ship.getShipUID());
        if (team < 0 || !(ship.level() instanceof ServerLevel serverLevel)) {
            applyWaypointGuard(ship, destination);
            return;
        }

        List<BasicEntityShip> ships = new ArrayList<>();
        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            BasicEntityShip member = ServerDataManager.getShipByUID(capa.getTeamMember(team, slot));
            if (member != null && member.isAlive() && member.level() == serverLevel
                    && (ShipCommandStateAdapter.isNew()
                        ? com.lulan.shincolle.ai.ShipFormationStateAdapter.active(member)
                            .map(a -> a.pattern().legacyId() == formationType).orElse(false)
                        : member.getStateMinor(ID.M.FormatType) == formationType)) {
                ships.add(member);
            }
        }
        if (ships.isEmpty()) {
            applyWaypointGuard(ship, destination);
            return;
        }

        boolean[] facing = FormationHelper.getFormationDirection(destination.getX(), destination.getZ(),
                ship.getX(), ship.getZ());
        int[] cursor = {destination.getX(), destination.getY(), destination.getZ()};
        for (BasicEntityShip member : ships) {
            if (ShipCommandStateAdapter.isNew()) {
                var placement = FormationHelper.calculateFormationPlacementNew(member,
                        com.lulan.shincolle.ai.domain.formation.FormationPattern.fromLegacy(formationType).orElseThrow(),
                        new com.lulan.shincolle.ai.domain.formation.FormationLayoutPlanner.Facing(facing[0], facing[1]),
                        new CommandPos(cursor[0], cursor[1], cursor[2]), commandPos(destination), serverLevel);
                int[] placed = placement.position();
                cursor = placement.next();
                applyWaypointGuard(member, new BlockPos(placed[0], placed[1], placed[2]));
            } else {
                switch (formationType) {
                    case 1, 4 -> cursor = FormationHelper.setFormationPosAndApplyGuardPos1(member, formationType,
                            facing[0], facing[1], cursor[0], cursor[1], cursor[2], serverLevel);
                    case 2, 3, 5 -> FormationHelper.setFormationPosAndApplyGuardPos2(member, formationType,
                            facing[0], facing[1], destination.getX(), destination.getY(), destination.getZ(), serverLevel);
                    default -> FormationHelper.applyShipGuard(member, destination.getX(), destination.getY(),
                            destination.getZ(), true);
                }
            }
            if (member != ship && !ShipCommandStateAdapter.isNew()) {
                member.sendSyncPacketGuard();
            }
        }
    }

    private static void applyWaypointGuard(BasicEntityShip ship, BlockPos destination) {
        if (ShipCommandStateAdapter.isNew()) {
            var id = ship.level().dimension().location();
            ship.applyCommandState(new CommandIssuer.Waypoint(), new CommandStateOp.Apply(
                    new ShipCommand.GuardPosition(new DimensionKey(id.getNamespace(), id.getPath()),
                            new CommandPos(destination.getX(), destination.getY(), destination.getZ()), false)));
        } else {
            FormationHelper.applyShipGuard(ship, destination.getX(), destination.getY(), destination.getZ(), true);
        }
    }

    /**
     * Calculate water depth at the ship's position.
     * Sets the ShipDepth value used for floating calculations.
     */
    public static void updateShipDepth(IShipFloating ship) {
        if (!(ship instanceof LivingEntity entity))
            return;

        Level level = entity.level();
        BlockPos pos = getEntityFeetBlockPos(entity);
        // [PORT] 1.10.2 -> 1.20.1: restore legacy depth contract used by
        // ShipFloatingGoal
        // (single-column liquid depth + CanFloatUp flag from top block material).
        BlockState state = level.getBlockState(pos);
        double depth = 0D;

        if (BlockHelper.checkBlockIsLiquid(state)) {
            depth = 1D;
            ship.setStateFlag(ID.F.CanFloatUp, true);

            for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
                BlockState upState = level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));

                if (BlockHelper.checkBlockIsLiquid(upState)) {
                    depth++;
                } else {
                    ship.setStateFlag(ID.F.CanFloatUp, upState.isAir());
                    break;
                }
            }

            depth -= (entity.getY() - Math.floor(entity.getY()));
        } else {
            ship.setStateFlag(ID.F.CanFloatUp, false);
        }

        ship.setShipDepth(depth);
    }

    /**
     * Update ship floating behavior.
     * Ships float on water surface unless sitting or in deep water.
     */
    public static void updateShipFloating(BasicEntityShip ship) {
        if (ship.level().isClientSide())
            return;

        double depth = ship.getShipDepth();
        double floatingDepth = ship.getShipFloatingDepth();

        // not in water, no floating needed
        if (depth <= 0D) {
            ship.setShipFloatingDepth(0D);
            return;
        }

        // sitting ships sink slightly
        if (ship.isOrderedToSit()) {
            ship.setShipFloatingDepth(Math.min(depth, 0.5D));
            return;
        }

        // calculate target floating depth based on water depth
        // ships try to stay at surface level
        BlockPos pos = ship.blockPosition();
        Level level = ship.level();

        // find water surface
        double targetY = findWaterSurfaceY(level, pos);
        double currentY = ship.getY();

        // do not set motion here directly, just update floating depth
        // the actual movement is handled by the ship's travel() method and gravity
        ship.setShipFloatingDepth(targetY - currentY);
    }

    /**
     * Custom movement for ships in fluid.
     * Called from BasicEntityShip.travel() override.
     * <p>
     * Ships move differently in water:
     * - No gravity in water (floating)
     * - Horizontal movement uses move speed attribute
     * - Vertical movement controlled by floating depth
     */
    public static void moveEntityInFluid(BasicEntityShip ship, Vec3 travelVec) {
        moveEntityInFluid(ship, ship, travelVec);
    }

    /**
     * Same water movement for any LivingEntity with IShipFloating, so hostile
     * ships (which do not extend BasicEntityShip) move like ships rather than
     * like vanilla swimmers.
     */
    public static void moveEntityInFluid(LivingEntity ship, IShipFloating floating, Vec3 travelVec) {
        if (!ship.isInWater())
            return;

        double depth = floating.getShipDepth();
        if (depth <= 0D)
            return;

        // [PORT] 1.10.2 -> 1.20.1: restore legacy water horizontal acceleration.
        // ShipMoveHelper controls facing/speed, while travelVec provides forward
        // intent.
        if (travelVec.lengthSqr() > MIN_TRAVEL_VEC_SQR) {
            ship.moveRelative(ship.getSpeed() * 0.4F, travelVec);
        }

        Vec3 motion = ship.getDeltaMovement();
        double floatingDepth = floating.getShipFloatingDepth();

        // vertical adjustment
        double vy = motion.y;
        if (floatingDepth > FLOAT_UP_THRESHOLD) {
            // push up toward surface
            vy = Math.min(vy + FLOAT_UP_ACCEL, FLOAT_UP_MAX_VELOCITY);
        } else if (floatingDepth < FLOAT_DOWN_THRESHOLD) {
            // sink if below target depth
            vy = Math.max(vy - FLOAT_DOWN_ACCEL, FLOAT_DOWN_MAX_VELOCITY);
        } else {
            // hover at surface
            vy *= FLOAT_HOVER_DAMPING;
        }

        // [PORT] 1.10.2 -> 1.20.1: keep the classic "bump up" when colliding in water.
        if (ship.horizontalCollision && ship.level().getFluidState(ship.blockPosition().above()).is(FluidTags.WATER)) {
            vy = Math.max(vy, COLLISION_BUMP_UP_VELOCITY);
        }

        // apply drag in water
        ship.setDeltaMovement(motion.x * WATER_DRAG, vy, motion.z * WATER_DRAG);
    }

    /**
     * Generic floating behavior for any LivingEntity with IShipFloating.
     * Used by hostile ships (which don't extend BasicEntityShip).
     */
    public static void updateShipFloatingGeneric(LivingEntity entity, IShipFloating floating) {
        if (entity.level().isClientSide())
            return;

        double depth = floating.getShipDepth();

        if (depth <= 0D) {
            floating.setShipFloatingDepth(0D);
            return;
        }

        BlockPos pos = entity.blockPosition();
        Level level = entity.level();

        double targetY = findWaterSurfaceY(level, pos);
        double currentY = entity.getY();
        floating.setShipFloatingDepth(targetY - currentY);
    }

    /**
     * Check if entity is in water.
     */
    public static boolean isInWater(LivingEntity entity) {
        return entity.isInWater() || entity.level().getFluidState(entity.blockPosition()).is(FluidTags.WATER);
    }

    /**
     * Get distance squared between two entities.
     */
    public static double getDistanceSq(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Get distance squared between entity and position.
     */
    public static double getDistanceSq(LivingEntity entity, BlockPos pos) {
        double dx = entity.getX() - pos.getX() - 0.5D;
        double dy = entity.getY() - pos.getY();
        double dz = entity.getZ() - pos.getZ() - 0.5D;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Check entity moving type for AA/ASM damage modifier.
     *
     * @return 0: default, 1: air mob, 2: water mob
     */
    public static int checkEntityMovingType(Entity entity) {
        if (entity instanceof IShipAttackBase ship) {
            return switch (ship.getDamageType()) {
                case ID.ShipDmgType.AIRPLANE -> 1;
                case ID.ShipDmgType.SUBMARINE -> 2;
                default -> 0;
            };
        } else if (entity instanceof WaterAnimal || entity instanceof Guardian) {
            return 2;
        } else if (entity instanceof Blaze || entity instanceof WitherBoss ||
                entity instanceof EnderDragon || entity instanceof Bat ||
                entity instanceof FlyingMob) {
            return 1;
        }

        return 0;
    }

    /**
     * Check if entity is standing in liquid.
     */
    public static boolean checkEntityIsInLiquid(Entity entity) {
        BlockPos pos = getEntityFeetBlockPos(entity);
        return BlockHelper.checkBlockIsLiquid(entity.level().getBlockState(pos));
    }

    private static BlockPos getEntityFeetBlockPos(Entity entity) {
        return new BlockPos(
                Mth.floor(entity.getX()),
                (int) entity.getBoundingBox().minY,
                Mth.floor(entity.getZ()));
    }

    private static double findWaterSurfaceY(Level level, BlockPos origin) {
        BlockPos surfacePos = origin;
        while (surfacePos.getY() < level.getMaxBuildHeight()) {
            FluidState above = level.getFluidState(surfacePos.above());
            if (!above.is(FluidTags.WATER)) {
                break;
            }
            surfacePos = surfacePos.above();
        }

        return surfacePos.getY() + SURFACE_Y_OFFSET;
    }

    /**
     * Apply emotes reaction to nearby friendly ships.
     */
    public static void applyShipEmotesAOE(Level level, double x, double y, double z, double range, int emotesType) {
        if (level.isClientSide()) {
            return;
        }

        // [PORT] 1.10.2 -> 1.20.1: restore legacy AOE emote distribution for ships.
        AABB box = new AABB(x - range, y - range, z - range, x + range, y + range, z + range);
        for (BasicEntityShip ship : level.getEntitiesOfClass(BasicEntityShip.class, box)) {
            if (ship.isAlive()) {
                ship.applyEmotesReaction(emotesType);
            }
        }
    }

    /**
     * Apply emotes reaction to nearby hostile ships.
     */
    public static void applyShipEmotesAOEHostile(
            Level level, double x, double y, double z, double range, int emotesType) {
        if (level.isClientSide()) {
            return;
        }

        // [PORT] 1.10.2 -> 1.20.1: restore legacy hostile AOE emote distribution path.
        AABB box = new AABB(x - range, y - range, z - range, x + range, y + range, z + range);
        for (BasicEntityShipHostile ship : level.getEntitiesOfClass(BasicEntityShipHostile.class, box)) {
            if (ship.isAlive()) {
                ship.applyEmotesReaction(emotesType);
            }
        }
    }

    /**
     * Spawn hostile mob ships near a player.
     * <p>
     * [PORT] 1.10.2 -> 1.20.1: restored ring+biome gated periodic hostile fleet
     * spawn behavior used by exploration gameplay.
     */
    public static void spawnMobShip(Player player, CapaTeitoku capa) {
        if (!(player.level() instanceof ServerLevel level) || capa == null) {
            LogHelper.diag("DIAG: spawn mob ship rejected player=" + player.getName().getString()
                    + " reason=notServerOrMissingCapability");
            return;
        }

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            LogHelper.diag("DIAG: spawn mob ship rejected player=" + player.getName().getString()
                    + " reason=peaceful");
            return;
        }

        int[] spawnCfg = ConfigHandler.mobSpawn;
        boolean inSeaBiome = isSeaOrBeachBiome(level, player.blockPosition());
        int loadedHostileShips = countLoadedHostileShips(level);
        String spawnLimit = spawnCfg != null && spawnCfg.length >= 1
                ? Integer.toString(spawnCfg[0]) : "invalid";
        LogHelper.diag("DIAG: spawn mob ship check player=" + player.getName().getString()
                + " checkRing=" + ConfigHandler.checkRing() + " hasRing=" + capa.hasRing()
                + " inSeaBiome=" + inSeaBiome
                + " loadedHostileShips=" + loadedHostileShips + "/" + spawnLimit);

        if (ConfigHandler.checkRing() && !capa.hasRing()) {
            LogHelper.diag("DIAG: spawn mob ship rejected player=" + player.getName().getString()
                    + " reason=ringRequired");
            return;
        }

        if (!inSeaBiome) {
            LogHelper.diag("DIAG: spawn mob ship rejected player=" + player.getName().getString()
                    + " reason=notSeaBiome");
            return;
        }

        if (spawnCfg == null || spawnCfg.length < 5) {
            LogHelper.diag("DIAG: spawn mob ship rejected player=" + player.getName().getString()
                    + " reason=invalidSpawnConfig");
            return;
        }

        if (loadedHostileShips > spawnCfg[0]) {
            LogHelper.diag("DIAG: spawn mob ship rejected player=" + player.getName().getString()
                    + " reason=hostileLimit");
            return;
        }

        RandomSource rng = player.getRandom();
        if (rng.nextInt(100) > spawnCfg[1]) {
            LogHelper.diag("DIAG: spawn mob ship rejected player=" + player.getName().getString()
                    + " reason=randomChance");
            return;
        }

        int blockX = Mth.floor(player.getX());
        int blockZ = Mth.floor(player.getZ());
        int groups = Math.max(1, spawnCfg[2]);
        int loop = 30 + groups * 30;

        while (groups > 0 && loop > 0) {
            loop--;
            int[] spawnXZ = pickSpawnXZ(rng, blockX, blockZ, SPAWN_OFFSET_MIN, SPAWN_OFFSET_RANGE);
            int spawnX = spawnXZ[0];
            int spawnZ = spawnXZ[1];

            int seaTestY = Mth.clamp(level.getSeaLevel() - 2, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            BlockPos seaCheck = new BlockPos(spawnX, seaTestY, spawnZ);
            if (!level.getFluidState(seaCheck).is(FluidTags.WATER)) {
                LogHelper.diag("DIAG: spawn mob ship: no water at " + spawnX + "," + seaTestY + "," + spawnZ
                        + " block=" + level.getBlockState(seaCheck));
                continue;
            }

            groups--;
            int spawnY = findTopWaterHeight(level, spawnX, seaTestY, spawnZ);

            int shipNum = Math.max(1, spawnCfg[3]);
            int range = spawnCfg[4] - spawnCfg[3];
            if (range > 0) {
                shipNum = spawnCfg[3] + rng.nextInt(range + 1);
            }

            for (int i = 0; i < shipNum; i++) {
                spawnRandomHostile(level,
                        spawnX + rng.nextDouble(),
                        spawnY + 0.5D,
                        spawnZ + rng.nextDouble(),
                        rng.nextInt(10) > 7 ? 1 : 0,
                        rng);
            }
        }
    }

    /**
     * Spawn boss fleet near a player when boss cooldown reaches zero.
     * <p>
     * [PORT] 1.10.2 -> 1.20.1: restored random invasion fleet spawns in sea/beach
     * biomes.
     */
    public static void spawnBossShip(Player player, CapaTeitoku capa) {
        if (!(player.level() instanceof ServerLevel level) || capa == null) {
            return;
        }

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }

        boolean inSeaBiome = isSeaOrBeachBiome(level, player.blockPosition());
        if (!inSeaBiome || (ConfigHandler.checkRing() && !capa.hasRing())) {
            return;
        }

        capa.setBossCooldown(capa.getBossCooldown() - 1);

        if (capa.getBossCooldown() > 0) {
            return;
        }

        capa.setBossCooldown(ConfigHandler.bossCooldown());
        RandomSource rng = player.getRandom();
        if (rng.nextInt(4) != 0) {
            return;
        }

        int blockX = Mth.floor(player.getX());
        int blockZ = Mth.floor(player.getZ());

        for (int tries = 0; tries < 20; tries++) {
            int[] spawnXZ = pickSpawnXZ(rng, blockX, blockZ, BOSS_SPAWN_OFFSET_MIN, BOSS_SPAWN_OFFSET_RANGE);
            int spawnX = spawnXZ[0];
            int spawnZ = spawnXZ[1];

            int seaTestY = Mth.clamp(level.getSeaLevel() - 2, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            BlockPos seaCheck = new BlockPos(spawnX, seaTestY, spawnZ);
            if (!level.getFluidState(seaCheck).is(FluidTags.WATER)) {
                continue;
            }

            int spawnY = findTopWaterHeight(level, spawnX, seaTestY, spawnZ);
            AABB checkBossBox = new AABB(
                    spawnX - 48D, spawnY - 48D, spawnZ - 48D,
                    spawnX + 48D, spawnY + 48D, spawnZ + 48D);

            int bossNum = 0;
            for (BasicEntityShipHostile mob : level.getEntitiesOfClass(BasicEntityShipHostile.class, checkBossBox)) {
                if (mob.getScaleLevel() >= 2) {
                    bossNum++;
                }
            }

            if (bossNum >= 2) {
                continue;
            }

            for (int i = 0; i < ConfigHandler.spawnBossNumber(); i++) {
                spawnRandomHostile(level,
                        spawnX + rng.nextInt(3),
                        spawnY + 0.5D,
                        spawnZ + rng.nextInt(3),
                        rng.nextInt(100) > 65 ? 3 : 2,
                        rng);
            }

            for (int i = 0; i < ConfigHandler.spawnMobNumber(); i++) {
                spawnRandomHostile(level,
                        spawnX + rng.nextInt(3),
                        spawnY + 0.5D,
                        spawnZ + rng.nextInt(3),
                        rng.nextInt(2),
                        rng);
            }


            Component text = Component.translatable(
                            rng.nextBoolean() ? "chat.shincolle_kai.bossspawn1" : "chat.shincolle_kai.bossspawn2")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" " + spawnX + " " + spawnY + " " + spawnZ)
                            .withStyle(ChatFormatting.AQUA));
            level.getServer().getPlayerList().broadcastSystemMessage(text, false);


            break;
        }
    }

    private static boolean isSeaOrBeachBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.IS_OCEAN)
                || level.getBiome(pos).is(BiomeTags.IS_BEACH)
                || level.getBiome(pos).is(BiomeTags.IS_RIVER);
    }

    private static int countLoadedHostileShips(ServerLevel level) {
        AABB worldBox = new AABB(
                -GLOBAL_SCAN_LIMIT, level.getMinBuildHeight(), -GLOBAL_SCAN_LIMIT,
                GLOBAL_SCAN_LIMIT, level.getMaxBuildHeight(), GLOBAL_SCAN_LIMIT);
        return level.getEntitiesOfClass(BasicEntityShipHostile.class, worldBox).size();
    }

    private static int[] pickSpawnXZ(RandomSource rng, int blockX, int blockZ, int minOffset, int offsetRange) {
        int offX = rng.nextInt(offsetRange) + minOffset;
        int offZ = rng.nextInt(offsetRange) + minOffset;
        int spawnX;
        int spawnZ = switch (rng.nextInt(4)) {
            case 1 -> {
                spawnX = blockX - offX;
                yield blockZ - offZ;
            }
            case 2 -> {
                spawnX = blockX + offX;
                yield blockZ - offZ;
            }
            case 3 -> {
                spawnX = blockX - offX;
                yield blockZ + offZ;
            }
            default -> {
                spawnX = blockX + offX;
                yield blockZ + offZ;
            }
        };

        return new int[]{spawnX, spawnZ};
    }

    private static int findTopWaterHeight(Level level, int x, int startY, int z) {
        int y = Mth.clamp(startY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        BlockPos pos = new BlockPos(x, y, z);

        while (y < level.getMaxBuildHeight() - 1 && level.getFluidState(pos.above()).is(FluidTags.WATER)) {
            y++;
            pos = pos.above();
        }

        return y;
    }

    private static void spawnRandomHostile(
            ServerLevel level,
            double x,
            double y,
            double z,
            int scaleLevel,
            RandomSource rng) {
        EntityType<? extends BasicEntityShipHostile> type = pickRandomMobShipType(rng);
        BasicEntityShipHostile mob = type.create(level);
        if (mob == null) {
            return;
        }

        mob.initAttrs(scaleLevel);
        mob.moveTo(x, y, z, rng.nextFloat() * 360F, 0F);
        level.addFreshEntity(mob);
    }

    private static EntityType<? extends BasicEntityShipHostile> pickRandomMobShipType(RandomSource rng) {
        int ran = rng.nextInt(100);

        if (ran > 75) {
            switch (rng.nextInt(3)) {
                case 1:
                    return ModEntities.BB_YAMATO_MOB.get();
                case 2:
                    switch (rng.nextInt(4)) {
                        case 1:
                            return ModEntities.BB_HIEI_MOB.get();
                        case 2:
                            return ModEntities.BB_HARUNA_MOB.get();
                        case 3:
                            return ModEntities.BB_KIRISHIMA_MOB.get();
                        default:
                            return ModEntities.BB_KONGOU_MOB.get();
                    }
                default:
                    return ModEntities.BB_NAGATO_MOB.get();
            }
        }

        if (ran > 45) {
            switch (rng.nextInt(3)) {
                case 1:
                case 2:
                    switch (rng.nextInt(4)) {
                        case 1:
                            return ModEntities.CL_TENRYUU_MOB.get();
                        case 2:
                            return ModEntities.CL_TATSUTA_MOB.get();
                        case 3:
                            return ModEntities.CA_ATAGO_MOB.get();
                        default:
                            return ModEntities.CA_TAKAO_MOB.get();
                    }
                default:
                    if (rng.nextInt(2) == 1) {
                        return ModEntities.CV_KAGA_MOB.get();
                    }
                    return ModEntities.CV_AKAGI_MOB.get();
            }
        }

        return switch (rng.nextInt(7)) {
            case 1 -> ModEntities.DESTROYER_HIBIKI_MOB.get();
            case 2 -> ModEntities.DESTROYER_IKAZUCHI_MOB.get();
            case 3 -> ModEntities.DESTROYER_INAZUMA_MOB.get();
            case 4 -> ModEntities.DESTROYER_SHIMAKAZE_MOB.get();
            case 5 -> ModEntities.SS_U511_MOB.get();
            case 6 -> ModEntities.SS_RO500_MOB.get();
            default -> ModEntities.DESTROYER_AKATSUKI_MOB.get();
        };
    }

}
