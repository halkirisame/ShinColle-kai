package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Under NEW a following ship measures its distance to the owner every 32 ticks, not every tick: it
 * keeps walking to where the owner was when it last looked, even if the owner has since come close.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FollowDistanceRefreshGameTests {
    private static final int LIMIT = 400;
    /** The ship looks at its owner again this many ticks after the last look. */
    private static final int LOOK_INTERVAL = 32;
    /** The owner steps in this soon after a look, so the next look is at least this far off. */
    private static final int LOOK_MARGIN = 28;
    /** Ticks after the owner steps in during which the ship must not have looked again. */
    private static final int STALE_WINDOW = 12;
    private static final Vec3 SHIP = new Vec3(2.5D, 0D, 2.5D);
    private static final Vec3 OWNER = new Vec3(22.5D, 0D, 2.5D);
    private static final Vec3[] ROUTE = {new Vec3(2.5D, 2D, 2.5D), new Vec3(8.5D, 2D, 2.5D),
            new Vec3(14.5D, 2D, 2.5D), new Vec3(22.5D, 2D, 2.5D)};

    private FollowDistanceRefreshGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_follow_distance_refresh_stale", timeoutTicks = LIMIT + 40)
    public static void followerKeepsWalkingAfterOwnerStepsInBetweenLooks(GameTestHelper helper) {
        run(helper, false);
    }

    @GameTest(template = "arena", batch = "isolated_follow_distance_refresh_stop", timeoutTicks = LIMIT + 40)
    public static void followerStopsAtNextLookOnceOwnerIsClose(GameTestHelper helper) {
        run(helper, true);
    }

    private static void run(GameTestHelper helper, boolean ownerStaysClose) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper, ownerStaysClose);
            helper.onEachTick(() -> scene.guard(scene::poll));
            helper.runAtTickTime(helper.getTick() + LIMIT, () -> scene.guard(() -> {
                scene.close();
                throw new GameTestAssertException("never reached the checked moment: " + scene.state());
            }));
        }, ROUTE);
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final boolean ownerStaysClose;
        private final ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(
                ConfigHandler.ShipAiTargetAuthority.NEW);
        private final GameTestEntities entities;
        private final int teleportCooldown = ConfigHandler.shipTeleport[0];
        private final BasicEntityShip ship;
        private FakePlayer owner;
        private boolean closed;
        private boolean done;
        private double startX;
        /** The ship's tick when the owner stepped in; -1 before. */
        private int steppedAt = -1;
        private double walkedFrom;

        Scene(GameTestHelper helper, boolean ownerStaysClose) {
            this.helper = helper;
            this.ownerStaysClose = ownerStaysClose;
            this.entities = GameTestEntities.open(helper);
            ConfigHandler.shipTeleport[0] = 1_000;
            this.ship = MovementPlanParityGameTests.friendly(helper, this.entities, SHIP);
            this.startX = this.ship.getX();
        }

        String state() {
            return "steppedAt=" + this.steppedAt + " shipTick=" + this.ship.tickCount + " x=" + this.ship.getX()
                    + " goalRunning=" + this.running();
        }

        void guard(Runnable body) {
            if (this.done) return;
            try {
                body.run();
            } catch (Throwable error) {
                this.close();
                throw error;
            }
        }

        private WrappedGoal goal() {
            return MovementPlanParityGameTests.selector(this.ship).getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.getGoal() instanceof ShipFollowOwnerGoal)
                    .findFirst().orElse(null);
        }

        private boolean running() {
            WrappedGoal goal = this.goal();
            return goal != null && goal.isRunning();
        }

        /** The tick of the ship's next look at its owner, as the follow state holds it. */
        private int nextLookAt(WrappedGoal goal) {
            try {
                Field field = ShipFollowOwnerGoal.class.getDeclaredField("move");
                field.setAccessible(true);
                return ((MovementState.Follow) field.get(goal.getGoal())).ownerResolveAt();
            } catch (ReflectiveOperationException error) {
                throw new GameTestAssertException("Failed to read the follow state: " + error);
            }
        }

        void poll() {
            MovementPlanParityGameTests.selector(this.ship).removeAllGoals(
                    goal -> goal instanceof ShipWanderGoal);
            if (this.owner == null) {
                if (this.ship.tickCount >= 24) {
                    this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                            new GameProfile(UUID.randomUUID(), "follow_refresh_owner"));
                    Vec3 at = MovementPlanParityGameTests.ground(this.helper, OWNER);
                    this.owner.moveTo(at.x, at.y, at.z, 0F, 0F);
                    this.helper.getLevel().addNewPlayer(this.owner);
                    this.ship.setOwnerUUID(this.owner.getUUID());
                }
                return;
            }
            if (this.steppedAt < 0) {
                this.stepInWhenJustLooked();
                return;
            }
            int since = this.ship.tickCount - this.steppedAt;
            if (this.ownerStaysClose) {
                this.stayAhead();
                if (since > 2 && !this.running() && this.ship.getNavigation().isDone()) {
                    this.finish();
                }
                return;
            }
            if (since > 2 && since <= STALE_WINDOW) {
                this.helper.assertTrue(this.running(), "the follow goal ended before the next look: " + this.state());
                this.helper.assertTrue(!this.ship.getNavigation().isDone(),
                        "the ship stopped walking before the next look: " + this.state());
            }
            if (since > STALE_WINDOW) {
                this.helper.assertTrue(this.ship.getX() - this.walkedFrom > 1D,
                        "the ship did not keep walking: " + this.state());
                this.finish();
            }
        }

        /** Once the ship has walked a little and has just looked, the owner steps to just ahead of it. */
        private void stepInWhenJustLooked() {
            WrappedGoal goal = this.goal();
            if (goal == null || !goal.isRunning()) return;
            boolean walked = this.ship.getX() - this.startX >= 2D;
            boolean farFromOwner = this.owner.getX() - this.ship.getX() >= 10D;
            boolean justLooked = this.nextLookAt(goal) - this.ship.tickCount >= LOOK_MARGIN
                    && this.nextLookAt(goal) - this.ship.tickCount <= LOOK_INTERVAL;
            if (walked && farFromOwner && justLooked) {
                this.steppedAt = this.ship.tickCount;
                this.walkedFrom = this.ship.getX();
                this.stayAhead();
            }
        }

        /** The owner stands one block ahead of the ship, well inside the follow distance. */
        private void stayAhead() {
            this.owner.moveTo(this.ship.getX() + 1D, this.ship.getY(), this.ship.getZ(), 0F, 0F);
        }

        private void finish() {
            this.close();
            this.done = true;
            this.helper.succeed();
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            try {
                this.entities.close();
                if (this.owner != null) {
                    this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                }
            } finally {
                ConfigHandler.shipTeleport[0] = this.teleportCooldown;
                this.authority.close();
            }
        }
    }
}
