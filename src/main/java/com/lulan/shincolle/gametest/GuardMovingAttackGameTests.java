package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * While a guarding ship moves and attacks, NEW takes the target from the target authority
 * instead of the goal's own nearby search; LEGACY keeps the search. Under NEW the fire control
 * goal fires on the move, so the target it aims at is inspected instead of the guard goal's.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardMovingAttackGameTests {
    private static final int START = 20;
    private static final int END = 120;

    private GuardMovingAttackGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_guard_moving_attack_new",
            timeoutTicks = END + 20)
    public static void newMovingAttackUsesManualTarget(GameTestHelper helper) {
        run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_guard_moving_attack_passive",
            timeoutTicks = END + 20)
    public static void newPassiveMovingAttackStillUsesManualTarget(GameTestHelper helper) {
        run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, true, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_guard_moving_attack_legacy",
            timeoutTicks = END + 20)
    public static void legacyMovingAttackSearchesNearby(GameTestHelper helper) {
        run(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, false, false);
    }

    private static void run(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode, boolean passive,
                            boolean expectManual) {
        ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(mode);
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
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "failed to create ship");
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.CraneState, 0);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateMinor(ID.M.FormatType, 0);
            ship.calcShipAttributes(31, false);
            ship.setStateFlag(ID.F.UseAmmoLight, true);
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.UseAmmoHeavy, false);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
            ship.setStateFlag(ID.F.UseAirLight, false);
            ship.setStateFlag(ID.F.UseAirHeavy, false);
            ship.setAmmoLight(500);
            ship.moveTo(helper.absoluteVec(new Vec3(0.5D, 2D, 0.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "failed to add ship");
            // Both targets sit beside the path so the ship passes within range while moving.
            Mob cow = target(helper, entities, EntityType.COW, new Vec3(10.5D, 2D, 2.5D));
            // LEGACY searches only every 32 ticks, so its control target waits near the start.
            Mob husk = target(helper, entities, EntityType.HUSK,
                    expectManual ? new Vec3(10.5D, 2D, -2.5D) : new Vec3(3.5D, 2D, -2.5D));

            int[] ammoAtStart = {0};
            boolean[] sawCow = {false};
            Entity[] wrong = {null};
            helper.runAtTickTime(START, () -> step(close, () -> {
                if (passive) ship.setStateFlag(ID.F.PassiveAI, true);
                BlockPos far = helper.absolutePos(new BlockPos(60, 2, 0));
                if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                    var id = helper.getLevel().dimension().location();
                    ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                            new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                    new DimensionKey(id.getNamespace(), id.getPath()),
                                    new CommandPos(far.getX(), far.getY(), far.getZ()), false)));
                } else {
                    ship.setGuardedPos(far.getX(), far.getY(), far.getZ(), helper.getLevel().dimension(), 1);
                    ship.setStateFlag(ID.F.CanFollow, false);
                }
                // A position order clears the manual attack, so the attack comes second.
                if (expectManual) {
                    ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                            new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(cow))));
                }
                ammoAtStart[0] = ship.getStateMinor(ID.M.NumAmmoLight);
            }));
            for (int tick = START + 1; tick < END; tick++) {
                helper.runAtTickTime(tick, () -> step(close, () -> {
                    Entity aimed = mode == ConfigHandler.ShipAiTargetAuthority.NEW
                            ? aimedByFireControl(ship, cow, husk) : movingTarget(ship);
                    if (aimed == cow) sawCow[0] = true;
                    // NEW: always the authority's target. LEGACY: its own search, which never picks the cow.
                    boolean ok = expectManual ? aimed == null || aimed == ship.getEntityTarget() : aimed != cow;
                    if (!ok && wrong[0] == null) wrong[0] = aimed;
                }));
            }
            helper.runAtTickTime(END, () -> {
                try {
                    WrappedGoal guarding = guarding(ship);
                    helper.assertTrue(guarding != null && guarding.isRunning(),
                            "The ship must still be moving to its guard position");
                    helper.assertTrue(wrong[0] == null, "Moving attack aimed at the wrong entity: " + wrong[0]);
                    if (expectManual) {
                        helper.assertTrue(sawCow[0], "Moving attack never aimed at the manual target");
                    }
                    helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoLight) < ammoAtStart[0],
                            "The ship never fired while moving: " + probe(ship, expectManual ? cow : husk));
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

    private static Mob target(GameTestHelper helper, GameTestEntities entities, EntityType<? extends Mob> type,
                              Vec3 position) {
        Mob mob = entities.add(type.create(helper.getLevel()));
        helper.assertTrue(mob != null, "failed to create " + type);
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500D);
        mob.setHealth(500F);
        mob.moveTo(helper.absoluteVec(position));
        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "failed to add " + type);
        return mob;
    }

    private static void step(Runnable close, Runnable body) {
        try {
            body.run();
        } catch (Throwable error) {
            close.run();
            throw error;
        }
    }

    private static WrappedGoal guarding(Mob mob) {
        return selector(mob).getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal() instanceof ShipGuardingGoal)
                .findFirst().orElse(null);
    }

    private static String probe(BasicEntityShip ship, Entity target) {
        StringBuilder out = new StringBuilder("dist=").append(Math.sqrt(ship.distanceToSqr(target)))
                .append(" entityTarget=").append(ship.getEntityTarget());
        WrappedGoal wrapped = guarding(ship);
        for (String name : new String[]{"isMoving", "rangeSq", "onSightTime", "aimTime", "delayTime"}) {
            try {
                Field field = ShipGuardingGoal.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(wrapped.getGoal());
                out.append(' ').append(name).append('=')
                        .append(value instanceof int[] ints ? java.util.Arrays.toString(ints) : value);
            } catch (ReflectiveOperationException error) {
                out.append(' ').append(name).append("=?");
            }
        }
        return out.toString();
    }

    private static Entity aimedByFireControl(BasicEntityShip ship, Entity... candidates) {
        var aimed = ship.shipCombatState().aimTarget();
        if (aimed.isEmpty()) return null;
        for (Entity candidate : candidates) {
            if (ShipCommandStateAdapter.handle(candidate).equals(aimed.get())) return candidate;
        }
        return ship;
    }

    private static Entity movingTarget(Mob mob) {
        WrappedGoal wrapped = guarding(mob);
        if (wrapped == null) return null;
        try {
            Field field = ShipGuardingGoal.class.getDeclaredField("attackTarget");
            field.setAccessible(true);
            return (Entity) field.get(wrapped.getGoal());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Failed to inspect the moving attack target", error);
        }
    }

    private static GoalSelector selector(Mob mob) {
        try {
            Field field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Failed to inspect goalSelector", error);
        }
    }
}
