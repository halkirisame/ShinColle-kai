package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.command.ShipCommandDispatcher;
import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.ai.domain.command.CommandDispatchResult;
import com.lulan.shincolle.ai.domain.command.CommandKind;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * The NEW manual attack keeps its command through temporary invisibility but does not
 * re-lock while the target cannot be seen, and it never holds a player with the invulnerable
 * ability. The order enters through {@link ShipCommandDispatcher} and the lock comes from the
 * registered {@link ShipTargetAuthorityGoal}, driven after {@link TargetHelper#updateTarget} as
 * {@code BasicEntityShip#aiStep} does.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipManualTargetLockGameTests {
    private ShipManualTargetLockGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_new_manual_invisible_released_until_visible")
    public static void newInvisibleManualTargetReleasedUntilVisible(GameTestHelper helper) {
        verify(helper, 23601, (h, f) -> {
            Zombie target = f.zombie();
            f.attack(target);
            f.tick(100);
            f.assertManualLock(target, "Manual order did not lock before the target turned invisible");

            target.setInvisible(true);
            f.tick(101);
            f.assertManualLock(target, "The 64 tick grace before the invisible cleanup was shortened");
            f.tick(128);
            h.assertTrue(f.ship.getManualTarget() == target,
                    "Invisibility must not cancel the manual command");
            h.assertTrue(f.goal.currentLock().isEmpty() && f.ship.getTarget() == null,
                    "The invisible manual target was locked again right after the 64 tick cleanup, so the"
                            + " ship keeps firing at it: lock=" + f.goal.currentLock()
                            + " target=" + f.ship.getTarget());
            for (int tick = 130; tick <= 192; tick += 2) {
                f.tick(tick);
                h.assertTrue(f.goal.currentLock().isEmpty() && f.ship.getTarget() == null,
                        "The invisible manual target was locked again at tick " + tick);
            }

            target.setInvisible(false);
            f.tick(194);
            h.assertTrue(f.ship.getManualTarget() == target, "Manual command was lost while invisible");
            f.assertManualLock(target, "Visible manual target did not resume without a new order");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_new_manual_invisible_detected")
    public static void newDetectedInvisibleManualTargetSurvivesCleanup(GameTestHelper helper) {
        verify(helper, 23602, (h, f) -> {
            Zombie target = f.zombie();
            f.attack(target);
            f.tick(100);
            f.assertManualLock(target, "Manual order did not lock");
            target.setInvisible(true);

            f.ship.setStateMinor(ID.M.LevelFlare, 1);
            h.assertTrue(f.ship.getStateMinor(ID.M.LevelFlare) == 1, "Fixture premise: flare level");
            f.tick(128);
            f.assertManualLock(target, "Flare did not keep the invisible manual target at the cleanup");
            f.ship.setStateMinor(ID.M.LevelFlare, 0);

            f.ship.setStateMinor(ID.M.LevelSearchlight, 1);
            h.assertTrue(f.ship.getStateMinor(ID.M.LevelSearchlight) == 1, "Fixture premise: searchlight level");
            f.tick(192);
            f.assertManualLock(target, "Searchlight did not keep the invisible manual target at the cleanup");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_new_manual_invulnerable_player_rejected")
    public static void newAttackOrderRejectsInvulnerablePlayer(GameTestHelper helper) {
        verify(helper, 23603, (h, f) -> {
            ServerPlayer target = f.otherPlayer("23603");
            try {
                target.getAbilities().invulnerable = true;
                CommandDispatchResult result = f.dispatchAttack(target);
                h.assertTrue(result == null && f.ship.getManualTarget() == null,
                        "An attack order on a player with the invulnerable ability was accepted");
                f.tick(100);
                h.assertTrue(f.goal.currentLock().isEmpty() && f.ship.getTarget() == null,
                        "The ship locked a player with the invulnerable ability");
            } finally {
                h.getLevel().removePlayerImmediately(target, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_new_manual_player_turns_invulnerable")
    public static void newManualOrderClearsWhenPlayerTurnsInvulnerable(GameTestHelper helper) {
        verify(helper, 23604, (h, f) -> {
            ServerPlayer target = f.otherPlayer("23604");
            try {
                f.attack(target);
                f.tick(100);
                f.assertManualLock(target, "Manual order on an ordinary non-allied player did not lock");

                target.getAbilities().invulnerable = true;
                f.tick(102);
                h.assertTrue(f.ship.getManualTarget() == null
                                && f.ship.getCommandState().manualAttack().isEmpty(),
                        "The manual command survived the target player turning invulnerable");
                h.assertTrue(f.goal.currentLock().isEmpty() && f.ship.getTarget() == null,
                        "The ship kept aiming at a player with the invulnerable ability: lock="
                                + f.goal.currentLock() + " target=" + f.ship.getTarget());
                for (int tick = 104; tick <= 140; tick += 2) {
                    f.tick(tick);
                    h.assertTrue(f.ship.getTarget() != target,
                            "The ship re-acquired the invulnerable player at tick " + tick);
                }
            } finally {
                h.getLevel().removePlayerImmediately(target, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    /**
     * A waypoint traversal under NEW keeps the manual attack command, but it clears the current target and
     * the lock with it. Measured with a real authority goal and a MANUAL lock before the traversal.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_new_manual_waypoint_traversal")
    public static void newWaypointTraversalAndTheManualLock(GameTestHelper helper) {
        verify(helper, 23605, (h, f) -> {
            Zombie target = f.zombie();
            f.attack(target);
            f.tick(100);
            f.assertManualLock(target, "Fixture: the manual order must hold a lock before the traversal");

            BlockPos a = BlockPos.containing(f.ship.position());
            BlockPos b = a.offset(5, 0, 0);
            WaypointStructureGameTests.place(h.getLevel(), a).setNextWaypoint(b);
            WaypointStructureGameTests.place(h.getLevel(), b);
            f.ship.setGuardedPos(a.getX(), a.getY(), a.getZ(), h.getLevel().dimension(), 1);
            f.ship.setStateFlag(ID.F.CanFollow, false);
            var lockBefore = f.goal.currentLock();
            h.assertTrue(EntityHelper.updateWaypointMove(f.ship), "Fixture: the ship must advance to B");

            String after = f.describe("right after the traversal");
            f.assertManualLock(target, "The traversal broke the manual lock at once: " + after);
            h.assertTrue(f.goal.currentLock().equals(lockBefore) && f.ship.getEntityTarget() == target,
                    "The traversal changed the lock or the target: " + after);
            f.tick(102);
            String next = f.describe("one authority step later");
            f.assertManualLock(target, "The lock was lost on the next step: " + next);
            h.assertTrue(f.goal.currentLock().equals(lockBefore),
                    "The lock was replaced on the next step: " + next + " before=" + lockBefore);
            f.tick(104);
            String later = f.describe("two authority steps later");
            f.assertManualLock(target, "The lock was lost two steps later: " + later);
            LogHelper.info("Waypoint m2: " + after + " | " + next + " | " + later);
            h.assertTrue(f.ship.getCommandState().manualAttack().isPresent() && f.ship.getManualTarget() == target,
                    "The traversal released the manual attack: " + after);
        });
    }

    @FunctionalInterface
    private interface Verification {
        void run(GameTestHelper helper, Fixture fixture);
    }

    private static void verify(GameTestHelper helper, int id, Verification verification) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                    var context = PointerSingleModeGameTests.createContext(helper, "manual_lock_" + id, id)) {
                verification.run(helper, new Fixture(helper, context, id));
                helper.succeed();
            }
        });
    }

    /** One NEW friendly ship with a real target authority goal and an admiral holding the pointer. */
    private static final class Fixture {
        private final GameTestHelper helper;
        private final PointerSingleModeGameTests.TestContext context;
        private final BasicEntityShip ship;
        private final ShipTargetAuthorityGoal goal;

        private Fixture(GameTestHelper helper, PointerSingleModeGameTests.TestContext context, int id) {
            this.helper = helper;
            this.context = context;
            this.ship = PointerSingleModeGameTests.addShip(context, 0, id * 100, new Vec3(4.5D, 2D, 1.5D));
            PointerSingleModeGameTests.select(context.capa(), 0);
            this.ship.setStateMinor(ID.M.NumGrudge, 1000);
            this.ship.setStateFlag(ID.F.NoFuel, false);
            this.ship.setStateFlag(ID.F.PassiveAI, false);
            this.ship.setEntitySit(false);
            this.ship.setStateMinor(ID.M.LevelFlare, 0);
            this.ship.setStateMinor(ID.M.LevelSearchlight, 0);
            rebuildTargets(this.ship);
            helper.assertTrue(this.ship.hasTargetAuthority(), "NEW target authority goal was not installed");
            helper.assertTrue(!this.ship.getStateFlag(ID.F.NoFuel)
                            && this.ship.getStateMinor(ID.M.LevelFlare) == 0
                            && this.ship.getStateMinor(ID.M.LevelSearchlight) == 0,
                    "Fixture premise: fueled ship without detection equipment");
            this.goal = authorityGoal(this.ship);
        }

        private Zombie zombie() {
            return PointerSingleModeGameTests.addTarget(this.context, new Vec3(7.5D, 2D, 1.5D));
        }

        /** A non-allied player owned by nobody in the admiral's team, next to the ship. */
        private ServerPlayer otherPlayer(String suffix) {
            ServerPlayer player = FakePlayerFactory.get(this.helper.getLevel(), new GameProfile(
                    UUID.fromString("6b00b41e-2c24-45a1-9d20-00000" + suffix + "99"), "manual_lock_" + suffix));
            player.getAbilities().invulnerable = false;
            player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                    () -> new AssertionError("Missing player capability")).setPlayerUID(900000 + Integer.parseInt(suffix));
            Vec3 position = this.helper.absoluteVec(new Vec3(7.5D, 2D, 1.5D));
            player.moveTo(position.x, position.y, position.z);
            this.helper.getLevel().addNewPlayer(player);
            GameTestEntities.assertRegistered(this.helper, player);
            return player;
        }

        private CommandDispatchResult dispatchAttack(Entity target) {
            return ShipCommandDispatcher.dispatch(this.context.player(), CommandKind.ATTACK,
                    new int[]{this.context.player().getId(), 0, PointerItem.MODE_SINGLE, target.getId()},
                    (level, capa, team, slot) -> {
                        int uid = capa.getTeamMember(team, slot);
                        var entity = level.getEntity(capa.getTeamSID(team, slot));
                        BasicEntityShip candidate = entity instanceof BasicEntityShip found ? found : null;
                        return candidate != null && candidate.level() == level
                                && candidate.getPlayerUID() == capa.getPlayerUID()
                                && candidate.getStateMinor(ID.M.ShipUID) == uid ? candidate : null;
                    }, (level, capa, team, slot) -> capa.getTeamMember(team, slot) > 0,
                    (player, ships, x, y, z) -> { });
        }

        private void attack(Entity target) {
            CommandDispatchResult result = dispatchAttack(target);
            this.helper.assertTrue(result != null && this.ship.getManualTarget() == target,
                    "Attack order was not accepted for " + target);
        }

        /** One server AI step: the tick cleanup, then the target authority goal. */
        private void tick(int tick) {
            this.ship.tickCount = tick;
            this.ship.getSensing().tick();
            TargetHelper.updateTarget(this.ship);
            this.goal.tick();
        }

        private String describe(String when) {
            return when + ": command=" + this.ship.getCommandState().manualAttack().isPresent()
                    + " manualTarget=" + (this.ship.getManualTarget() != null)
                    + " lock=" + this.goal.currentLock().map(lock -> lock.source().toString()).orElse("none")
                    + " entityTarget=" + (this.ship.getEntityTarget() != null)
                    + " mobTarget=" + (this.ship.getTarget() != null);
        }

        private void assertManualLock(Entity target, String message) {
            Optional<TargetLock> lock = this.goal.currentLock();
            this.helper.assertTrue(lock.isPresent() && lock.get().source() == TargetSource.MANUAL
                            && this.ship.getTarget() == target,
                    message + ": lock=" + lock + " target=" + this.ship.getTarget());
        }
    }

    private static void rebuildTargets(BasicEntityShip ship) {
        try {
            Method clear = BasicEntityShip.class.getDeclaredMethod("clearAITargetTasks");
            clear.setAccessible(true);
            clear.invoke(ship);
            ship.setAITargetList();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot rebuild target goals", e);
        }
    }

    private static ShipTargetAuthorityGoal authorityGoal(Mob ship) {
        try {
            Field field = Mob.class.getDeclaredField("targetSelector");
            field.setAccessible(true);
            GoalSelector selector = (GoalSelector) field.get(ship);
            return selector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(ShipTargetAuthorityGoal.class::isInstance)
                    .map(ShipTargetAuthorityGoal.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("NEW authority goal was not registered"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect target selector", e);
        }
    }
}
