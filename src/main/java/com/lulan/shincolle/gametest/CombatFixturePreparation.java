package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/** Fixed combat inputs and failure-only state snapshots, without changing the ship's attributes. */
final class CombatFixturePreparation {
    private CombatFixturePreparation() { }

    static GoalSelector selector(Mob mob, String name) {
        try {
            Field field = Mob.class.getDeclaredField(name);
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect " + name + ": " + error);
        }
    }

    static Goal authority(BasicEntityShip ship) {
        return selector(ship, "targetSelector").getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal()).filter(goal -> goal instanceof ShipTargetAuthorityGoal)
                .findFirst().orElse(null);
    }

    /** Hold a real crane destination without changing the fight or a path already being observed. */
    static void holdAtCrane(GameTestHelper helper, BasicEntityShip ship) {
        BlockPos pos = ship.blockPosition().above(2).north(2);
        BlockState previous = ship.level().getBlockState(pos);
        BlockEntity previousTile = ship.level().getBlockEntity(pos);
        CompoundTag previousTag = previousTile == null ? null : previousTile.saveWithFullMetadata();
        ship.level().setBlock(pos, ModBlocks.CRANE.get().defaultBlockState(), 3);
        helper.assertTrue(ship.level().getBlockEntity(pos) instanceof TileEntityCrane,
                "The combat fixture must have a real crane destination.");
        TileEntityCrane crane = (TileEntityCrane) ship.level().getBlockEntity(pos);
        CompoundTag identity = crane.saveWithFullMetadata();
        identity.putInt("PlayerUID", ship.getPlayerUID());
        if (ship.getOwnerUUID() != null) identity.putUUID("OwnerUUID", ship.getOwnerUUID());
        crane.load(identity);
        helper.assertTrue(ship.getOwnerUUID() != null ? ship.getOwnerUUID().equals(crane.getOwnerUUID())
                        : crane.getOwnerUUID() == null && crane.getPlayerUID() > 0 && crane.getPlayerUID() == ship.getPlayerUID(),
                "The waiting crane must belong to the combat fixture's ship owner.");
        GameTestEntities.onCompletion(helper, () -> {
            ship.level().setBlock(pos, previous, 3);
            BlockEntity restored = ship.level().getBlockEntity(pos);
            if (restored != null && previousTag != null) restored.load(previousTag);
        }, "combat crane cleanup");
        Path priorPath = ship.getNavigation().getPath();
        ship.applyCommandState(new CommandIssuer.Waypoint(), new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                ShipCommandStateAdapter.handle(ship).dimension(), new CommandPos(pos.getX(), pos.getY(), pos.getZ()), false)));
        if (priorPath != null && !priorPath.isDone()) {
            ship.getNavigation().moveTo(priorPath, 1D);
            helper.assertTrue(ship.getNavigation().getPath() == priorPath && !priorPath.isDone(),
                    "The combat fixture must retain the path whose aim behavior is being observed.");
        }
        ship.setStateTimer(ID.T.CrandDelay, 0);
        ship.setStateMinor(ID.M.CraneState, 1);
        helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 1 && ship.hasGuardDestination()
                        && ship.getGuardedPos(0) == pos.getX() && ship.getGuardedPos(1) == pos.getY()
                        && ship.getGuardedPos(2) == pos.getZ(),
                "The combat fixture must retain its waiting state and crane guard command.");
    }

    static void releaseCrane(BasicEntityShip ship) {
        ship.applyCommandState(new CommandIssuer.Waypoint(), new CommandStateOp.EndMovement());
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateTimer(ID.T.CraneTime, 0);
    }

    static void pin(GameTestHelper helper, Mob mob) {
        Vec3 position = mob.position();
        Runnable pin = () -> {
            mob.moveTo(position);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setNoGravity(true);
        };
        pin.run();
        Consumer<LivingEvent.LivingTickEvent> listener = event -> {
            if (event.getEntity() == mob) pin.run();
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        GameTestEntities.onCompletion(helper, () -> MinecraftForge.EVENT_BUS.unregister(listener), "combat pin");
        GameTestEntities.removeGoalsEachTick(helper, mob, goal -> goal instanceof ShipWanderGoal);
    }

    static void assertReady(GameTestHelper helper, BasicEntityShip ship, Goal initial) {
        GameTestEntities.assertRegistered(helper, ship);
        helper.assertTrue(ship.tickCount >= 24 && ship.hasTargetAuthority()
                        && authority(ship) != null && authority(ship) != initial,
                "Fixture AI has not re-registered: " + probe(helper, ship, null));
        helper.assertTrue(selector(ship, "goalSelector").getAvailableGoals().stream()
                        .noneMatch(wrapped -> wrapped.getGoal() instanceof ShipWanderGoal),
                "Fixture still has a wander goal: " + probe(helper, ship, null));
    }

    static String probe(GameTestHelper helper, BasicEntityShip ship, Entity target) {
        return "g=" + helper.getTick() + " s=" + ship.tickCount + " pos=" + ship.position()
                + " ticking=" + helper.getLevel().isPositionEntityTicking(ship.blockPosition())
                + " registered=" + (helper.getLevel().getEntity(ship.getUUID()) == ship)
                + " authority=" + ship.hasTargetAuthority() + " lock=" + ship.currentTargetLock()
                + " command=" + ship.getCommandState() + " change=" + ship.getLastCommandStateChange()
                + " manual=" + ship.getManualTarget() + " target=" + ship.getEntityTarget()
                + " fuel=" + ship.getStateMinor(ID.M.NumGrudge) + "/" + ship.getStateFlag(ID.F.NoFuel)
                + " sit=" + ship.getIsSitting() + " crane=" + ship.getStateMinor(ID.M.CraneState)
                + " ammo=" + ship.getAmmoLight() + "/" + ship.getAmmoHeavy()
                + " timing=" + timing(ship)
                + " goals=" + selector(ship, "goalSelector").getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.isRunning())
                        .map(wrapped -> wrapped.getGoal().getClass().getSimpleName()).toList()
                + " targetGoals=" + selector(ship, "targetSelector").getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.isRunning())
                        .map(wrapped -> wrapped.getGoal().getClass().getSimpleName()).toList()
                + (target == null ? "" : " targetS=" + target.tickCount + " targetPos=" + target.position()
                        + " targetRegistered=" + (helper.getLevel().getEntity(target.getUUID()) == target)
                        + " targetTicking=" + helper.getLevel().isPositionEntityTicking(BlockPos.containing(target.position()))
                        + " distSq=" + ship.distanceToSqr(target) + " range=" + ship.getAttrs().getAttackRange()
                        + " los=" + ship.getSensing().hasLineOfSight(target));
    }

    private static Object timing(BasicEntityShip ship) {
        try {
            // The public timing accessor initializes the timer; diagnostics must only observe it.
            Field field = ship.shipCombatState().getClass().getDeclaredField("timing");
            field.setAccessible(true);
            return field.get(ship.shipCombatState());
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect combat timing: " + error);
        }
    }
}
