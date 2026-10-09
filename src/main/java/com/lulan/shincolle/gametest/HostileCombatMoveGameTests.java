package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * Hostile ships carry no movement intent, so their attack goals close in on and stop for their
 * target exactly as under LEGACY, whichever authority is configured.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostileCombatMoveGameTests {
    private static final int SELECTOR_TICKS = 4;
    private static final Vec3 FAR = new Vec3(1.5D, 2D, 3.5D);

    private HostileCombatMoveGameTests() {
    }

    @GameTest(template = "arena")
    public static void hostileCannonClosesInUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, true, HostileCombatMoveGameTests::cannon);
    }

    @GameTest(template = "arena")
    public static void hostileCannonStopsInRangeUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, false, HostileCombatMoveGameTests::cannon);
    }

    @GameTest(template = "arena")
    public static void hostileCarrierClosesInUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, true, HostileCombatMoveGameTests::carrier);
    }

    @GameTest(template = "arena")
    public static void hostileCarrierStopsInRangeUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, false, HostileCombatMoveGameTests::carrier);
    }

    /**
     * With melee on, the goal never holds and must path toward the target; with melee off and the
     * target in range and sight, it must stop a path already under way.
     */
    private static void verify(GameTestHelper helper, boolean melee, Function<GameTestHelper, Setup> factory) {
        boolean legacy = run(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, melee, factory);
        helper.assertTrue(legacy == melee, "Fixture: LEGACY " + (melee ? "did not close in" : "did not stop"));
        boolean now = run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, melee, factory);
        helper.assertTrue(now == legacy, "NEW differs from LEGACY: navigating=" + now + " legacy=" + legacy);
        helper.succeed();
    }

    /** Whether the host is still navigating after its attack goal ran a few ticks. */
    private static boolean run(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority authority,
                               boolean melee, Function<GameTestHelper, Setup> factory) {
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(authority);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            Setup setup = factory.apply(helper);
            Mob ship = entities.add(setup.ship());
            // the attack range is 0 until the attributes are worked out, as on spawn
            ((BasicEntityShipHostile) ship).calcShipAttributes(31, false);
            moveTo(helper, ship, new Vec3(1.5D, 2D, 1.5D));
            setup.flags().set(ID.F.UseMelee, melee);

            Zombie target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            helper.assertTrue(target != null, "failed to create target");
            target.setNoAi(true);
            target.setInvulnerable(true);
            moveTo(helper, target, new Vec3(7.5D, 2D, 1.5D));
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "failed to add target");
            ship.setTarget(target);
            helper.assertTrue(((com.lulan.shincolle.entity.IShipAttackBase) ship).getEntityTarget() == target,
                    "Fixture must give the ship its target");

            GoalSelector selector = extractSelector(ship, "goalSelector");
            selector.removeAllGoals(existing -> true);
            extractSelector(ship, "targetSelector").removeAllGoals(existing -> true);
            selector.addGoal(1, setup.goal());
            if (!melee) {
                Vec3 far = helper.absoluteVec(FAR);
                helper.assertTrue(ship.getNavigation().moveTo(far.x, far.y, far.z, 1.0D),
                        "Fixture must start a path to stop");
            }
            for (int tick = 0; tick < SELECTOR_TICKS; tick++) {
                ship.tickCount++;
                ship.getSensing().tick();
                selector.tick();
            }
            helper.assertTrue(selector.getAvailableGoals().stream().anyMatch(g -> g.isRunning()),
                    "Fixture: the attack goal did not run under " + authority);
            return ship.getNavigation().isInProgress();
        }
    }

    private static Setup cannon(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        if (!(entity instanceof IShipCannonAttack host) || !(entity instanceof Mob mob)) {
            throw new AssertionError("Failed to create a hostile cannon ship.");
        }
        host.setStateFlag(ID.F.OnSightChase, false);
        host.setStateFlag(ID.F.NoFuel, false);
        host.setAmmoLight(100_000);
        host.setStateFlag(ID.F.AtkType_Light, true);
        host.setStateFlag(ID.F.AtkType_Heavy, false);
        host.setStateFlag(ID.F.UseAmmoLight, true);
        host.setStateFlag(ID.F.UseAmmoHeavy, false);
        return new Setup(mob, new ShipRangeAttackGoal(host), host::setStateFlag);
    }

    private static Setup carrier(GameTestHelper helper) {
        Entity entity = ModEntities.CV_AKAGI_MOB.get().create(helper.getLevel());
        if (!(entity instanceof IShipAircraftAttack host) || !(entity instanceof Mob mob)) {
            throw new AssertionError("Failed to create a hostile carrier.");
        }
        host.setStateFlag(ID.F.OnSightChase, false);
        host.setStateFlag(ID.F.NoFuel, false);
        host.setAmmoLight(100_000);
        host.setNumAircraftLight(6);
        host.setStateFlag(ID.F.AtkType_AirLight, true);
        host.setStateFlag(ID.F.AtkType_AirHeavy, false);
        host.setStateFlag(ID.F.UseAirLight, true);
        host.setStateFlag(ID.F.UseAirHeavy, false);
        return new Setup(mob, new ShipCarrierAttackGoal(host), host::setStateFlag);
    }

    private interface Flags {
        void set(int flag, boolean value);
    }

    private record Setup(Mob ship, Goal goal, Flags flags) {
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relativePos) {
        Vec3 absolute = helper.absoluteVec(relativePos);
        entity.moveTo(absolute.x, absolute.y, absolute.z, 0F, 0F);
    }

    private static GoalSelector extractSelector(Mob mob, String fieldName) {
        try {
            Field field = Mob.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            if (field.get(mob) instanceof GoalSelector selector) {
                return selector;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect Mob." + fieldName + '.', e);
        }
        throw new AssertionError("Failed to resolve " + fieldName + '.');
    }
}
