package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.LookReason;
import com.lulan.shincolle.ai.domain.movement.LookRequest;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.ShipWatchClosestGoal;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Under NEW a ship that is walking to its admiral or to a guarded point and is engaged with a
 * locked target turns its head to the target, not to where it is walking. Without a target it
 * looks where it always did.
 */
@GameTestHolder(com.lulan.shincolle.reference.Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementLookAuthorityGameTests {
    /** A ship registers its goals again on its 16th tick; nothing may move it before. */
    private static final int RELEASE_AT = 24;
    /** Ticks the ship must have been engaged before its head is judged; a head turns 30 degrees a tick. */
    private static final int SETTLE = 8;
    /** How far the head may be from the bearing of what it walks to: about one tick of its turning. */
    private static final float WALK_OFF = 30F;
    /**
     * The target is behind a ship that faces where it walks, and a head turns only so far from the
     * body, so the closest it gets is what the neck leaves; this much more is allowed on top of it.
     */
    private static final float NECK_SLACK = 15F;
    private static final Vec3 SHIP = new Vec3(12.5D, 0D, 2.5D);
    private static final Vec3 AWAY = new Vec3(62.5D, 0D, 2.5D);
    /** Ticks the watch goal is given to start before the attack order, to show the fixture lets it run. */
    private static final int WATCH_PROOF = 10;
    /** How far ahead of the ship the bystander that the watch goal looks at is kept. */
    private static final double BYSTANDER = 3D;
    /** How far behind the ship the target is kept, and how far ahead the admiral is. */
    private static final double BEHIND = 6D;
    private static final double AHEAD = 20D;
    /** With a ration the admiral must stand within its 8 blocks of the ship. */
    private static final double RATION_AHEAD = 6D;
    /** Sideways offset of the admiral from an idle ship, so a look at the admiral is told from any other. */
    private static final double RATION_SIDE = 5D;
    /** The ration fires every 16 ticks; one more on every other cycle so both parities of the ship's goal tick are hit. */
    private static final int RATION_PERIOD = 16;
    private static final Vec3[] ROUTE = {new Vec3(2.5D, 2D, 2.5D), new Vec3(30.5D, 2D, 2.5D),
            new Vec3(62.5D, 2D, 2.5D)};

    private MovementLookAuthorityGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_look_follow_engaged", timeoutTicks = 220)
    public static void followerEngagedLooksAtTargetNotOwner(GameTestHelper helper) {
        run(helper, true, true, LookReason.ENGAGED_TARGET);
    }

    @GameTest(template = "arena", batch = "isolated_look_guard_engaged", timeoutTicks = 220)
    public static void guardEngagedLooksAtTargetNotDestination(GameTestHelper helper) {
        run(helper, false, true, LookReason.ENGAGED_TARGET);
    }

    @GameTest(template = "arena", batch = "isolated_look_follow_idle", timeoutTicks = 220)
    public static void followerNotEngagedStillLooksAtOwner(GameTestHelper helper) {
        run(helper, true, false, LookReason.FOLLOW_OWNER);
    }

    /**
     * A player stands ahead within the watch goal's 4 blocks while the real watch goal (started every
     * tick it may) runs beside the follow goal: the engaged head must still face the target.
     */
    @GameTest(template = "arena", batch = "isolated_look_follow_engaged_watch", timeoutTicks = 220)
    public static void followerEngagedKeepsLookingAtTargetWhileWatchGoalRuns(GameTestHelper helper) {
        run(helper, true, true, true, LookReason.ENGAGED_TARGET);
    }

    /**
     * The admiral holds a selected combat ration within 8 blocks, on the side the ship walks to, and the
     * ration's periodic look is fired from here; an engaged head must still face the target.
     */
    @GameTest(template = "arena", batch = "isolated_look_follow_engaged_ration", timeoutTicks = 260)
    public static void followerEngagedKeepsLookingAtTargetWhileRationHeld(GameTestHelper helper) {
        run(helper, true, true, false, true, LookReason.ENGAGED_TARGET);
    }

    /** With nothing engaged, the ration still turns the ship's head to the admiral. */
    @GameTest(template = "arena", batch = "isolated_look_guard_idle_ration", timeoutTicks = 260)
    public static void idleShipStillLooksAtAdmiralWhileRationHeld(GameTestHelper helper) {
        run(helper, false, false, false, true, LookReason.GUARD);
    }

    private static void run(GameTestHelper helper, boolean follow, boolean engage, LookReason expected) {
        run(helper, follow, engage, false, false, expected);
    }

    private static void run(GameTestHelper helper, boolean follow, boolean engage, boolean watch,
                            LookReason expected) {
        run(helper, follow, engage, watch, false, expected);
    }

    private static void run(GameTestHelper helper, boolean follow, boolean engage, boolean watch, boolean ration,
                            LookReason expected) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper, follow, engage, watch, ration);
            int start = (int) helper.getTick() + 1;
            helper.runAtTickTime(start, () -> scene.guard(scene::setup));
            for (int tick = start + 1; tick < start + 100; tick++) {
                helper.runAtTickTime(tick, () -> scene.guard(scene::poll));
            }
            helper.runAtTickTime(start + 100, () -> scene.guard(() -> {
                List<String> problems = scene.problems(expected);
                String report = scene.toString();
                scene.close();
                helper.assertTrue(problems.isEmpty(), problems + "\n " + report);
                LogHelper.info("Movement look authority (" + (follow ? "follow" : "guard") + ", engage=" + engage
                        + "): " + report);
                helper.succeed();
            }));
        }, ROUTE);
    }

    /** The yaw a head at {@code from} must have to face {@code to}. */
    private static float bearing(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * (180D / Math.PI)) - 90F;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final boolean follow;
        private final boolean engage;
        private final boolean watch;
        private final boolean ration;
        private final ShipAiAuthorityOverride authority;
        private final GameTestEntities entities;
        private final List<String> samples = new ArrayList<>();
        private final List<String> late = new ArrayList<>();
        private BasicEntityShip ship;
        private Zombie foe;
        private FakePlayer owner;
        private FakePlayer bystander;
        private WrappedGoal watching;
        private int watchRan;
        private int engagedEarly;
        private boolean attacked;
        private Vec3 destination;
        private int releasedAt = -1;
        private int engagedFor;
        private int checked;
        private int wrongHead;
        private boolean closed;
        private Optional<LookRequest> lastLook = Optional.empty();
        private int nextRation;
        private int rationCalls;
        private int rationTookHead;
        private int rationMissedAdmiral;

        Scene(GameTestHelper helper, boolean follow, boolean engage, boolean watch, boolean ration) {
            this.helper = helper;
            this.follow = follow;
            this.engage = engage;
            this.watch = watch;
            this.ration = ration;
            this.authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
            this.entities = GameTestEntities.open(helper);
        }

        void setup() {
            this.ship = MovementPlanParityGameTests.friendly(this.helper, this.entities, SHIP);
            if (this.engage && !this.watch) this.spawnFoe();
            this.destination = MovementPlanParityGameTests.ground(this.helper, AWAY);
        }

        private void spawnFoe() {
            this.foe = this.entities.add(EntityType.ZOMBIE.create(this.helper.getLevel()));
            this.foe.setNoAi(true);
            this.foe.setPersistenceRequired();
            this.foe.setInvulnerable(true);
            this.place(this.foe, this.ship.getX() - BEHIND);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(this.foe), "failed to add the target");
        }

        /** Standing on the ground at {@code x} in the ship's row. */
        private void place(Entity entity, double x) {
            int y = this.helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x),
                    (int) Math.floor(this.ship.getZ()));
            entity.moveTo(x, y, this.ship.getZ(), 0F, 0F);
        }

        void release() {
            this.releasedAt = this.ship.tickCount;
            if (this.follow || this.ration) {
                this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "look_authority_owner"));
                this.placeOwner();
                this.helper.getLevel().addNewPlayer(this.owner);
                this.ship.setOwnerUUID(this.owner.getUUID());
                if (this.ration) {
                    // the ration only calls ships whose owner it resolves by player UID
                    int uid = 30_000 + Math.floorMod(this.ship.getUUID().hashCode(), 100_000);
                    this.owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                            .orElseThrow(() -> new IllegalStateException("Missing owner capability"))
                            .setPlayerUID(uid);
                    this.ship.setPlayerUID(uid);
                    this.helper.assertTrue(TeamHelper.checkSameOwner(this.owner, this.ship),
                            "The ration must see the admiral as the ship's owner");
                }
            }
            if (!this.follow) {
                BlockPos pos = BlockPos.containing(this.destination);
                var id = this.helper.getLevel().dimension().location();
                this.ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(pos.getX(), pos.getY(), pos.getZ()), false)));
            }
            if (this.watch) {
                // the product goal, but always willing to start, so no random draw decides the test
                var selector = MovementPlanParityGameTests.selector(this.ship);
                selector.removeAllGoals(goal -> goal instanceof ShipWatchClosestGoal);
                selector.addGoal(25, new ShipWatchClosestGoal(this.ship, Player.class, 4F, 1F));
                this.watching = selector.getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal() instanceof ShipWatchClosestGoal).findFirst().orElse(null);
                this.helper.assertTrue(this.watching != null, "the watch goal was not registered");
                this.bystander = FakePlayerFactory.get(this.helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "look_authority_bystander"));
                this.place(this.bystander, this.ship.getX() + BYSTANDER);
                this.helper.getLevel().addNewPlayer(this.bystander);
            }
            // with a watcher the attack comes later, after the watch goal has shown it can run here
            if (this.engage && !this.watch) this.attack();
        }

        private void placeOwner() {
            if (this.follow) {
                this.place(this.owner, this.ship.getX() + (this.ration ? RATION_AHEAD : AHEAD));
            } else {
                this.place(this.owner, this.ship.getX() + 3D);
                this.owner.setPos(this.owner.getX(), this.owner.getY(), this.ship.getZ() + RATION_SIDE);
            }
        }

        /** Fires the ration's periodic look for the admiral, as the admiral's inventory tick would. */
        private void rationTick() {
            if (this.ship.tickCount < this.nextRation) return;
            this.nextRation = this.ship.tickCount + RATION_PERIOD + (this.rationCalls % 2);
            this.owner.tickCount = 0;
            ItemStack stack = new ItemStack(ModItems.COMBAT_RATION.get());
            stack.getItem().inventoryTick(stack, this.helper.getLevel(), this.owner, 0, true);
            this.rationCalls++;
            boolean onAdmiral = Math.abs(this.ship.getLookControl().getWantedX() - this.owner.getX()) < 0.5D
                    && Math.abs(this.ship.getLookControl().getWantedZ() - this.owner.getZ()) < 0.5D;
            if (this.engage && onAdmiral) this.rationTookHead++;
            if (!this.engage && !onAdmiral) this.rationMissedAdmiral++;
        }

        private void attack() {
            this.attacked = true;
            // with a watcher the target appears only now: the ship picks a target of its own the moment one is near
            if (this.foe == null) this.spawnFoe();
            this.ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(this.foe))));
            this.helper.assertTrue(this.ship.getManualTarget() == this.foe, "the attack order was not taken");
        }

        void poll() {
            // wander draws from the entity random and would walk the ship off on its own
            MovementPlanParityGameTests.selector(this.ship).removeAllGoals(goal -> goal instanceof ShipWanderGoal);
            if (this.releasedAt < 0) {
                if (this.ship.tickCount >= RELEASE_AT) this.release();
                return;
            }
            // the target and the admiral keep pace with the ship, so it stays in range of the one
            // and far from the other
            if (this.foe != null) this.place(this.foe, this.ship.getX() - BEHIND);
            if (this.owner != null) this.placeOwner();
            if (this.bystander != null) this.place(this.bystander, this.ship.getX() + BYSTANDER);
            if (this.watching != null && !this.attacked) {
                // before the attack: the watch goal must be able to run in this fixture
                if (this.watching.isRunning()) this.watchRan++;
                if (ShipCombatGate.engagedTarget(this.ship).isPresent()) this.engagedEarly++;
                if (this.ship.tickCount >= this.releasedAt + WATCH_PROOF) this.attack();
            }
            boolean engaged = ShipCombatGate.engagedTarget(this.ship).isPresent();
            this.engagedFor = engaged ? this.engagedFor + 1 : 0;
            this.lastLook = this.ship.shipMovementExecutor().lastLook();
            boolean judge = this.engage ? this.engagedFor >= SETTLE : this.ship.tickCount >= this.releasedAt + SETTLE;
            if (judge && this.ration) this.rationTick();
            if (!judge) return;
            Vec3 where = this.ship.position();
            Vec3 toward = this.engage ? this.foe.position() : this.follow ? this.owner.position() : this.destination;
            float off = Math.abs(Mth.wrapDegrees(this.ship.yHeadRot - bearing(where, toward)));
            this.checked++;
            // an idle ship with a ration is meant to turn to the admiral, so only its look is judged then
            if (off > this.maxOff() && !(this.ration && !this.engage)) {
                this.wrongHead++;
                if (this.late.size() < 6) {
                    this.late.add(this.ship.tickCount + ":off=" + MovementPlanParityGameTests.fmt(off));
                }
            }
            if (this.samples.size() < 8 && this.checked % 4 == 1) {
                this.samples.add(this.ship.tickCount + ":off=" + MovementPlanParityGameTests.fmt(off) + ":dist="
                        + MovementPlanParityGameTests.fmt(where.distanceTo(toward)));
            }
        }

        private float maxOff() {
            return this.engage ? 180F - this.ship.getMaxHeadYRot() + NECK_SLACK : WALK_OFF;
        }

        List<String> problems(LookReason expected) {
            List<String> problems = new ArrayList<>();
            if (this.releasedAt < 0) problems.add("the ship never got going");
            if (this.checked < 10) problems.add("only " + this.checked + " ticks could be judged");
            if (this.watch && this.watchRan < 3) {
                problems.add("the watch goal ran in only " + this.watchRan
                        + " ticks before the attack (engaged early in " + this.engagedEarly
                        + "), so the fixture proves nothing");
            }
            if (this.ration && this.rationCalls < 4) {
                problems.add("the ration fired only " + this.rationCalls + " times");
            }
            if (this.rationTookHead > 0) {
                problems.add("the ration turned the engaged head to the admiral in " + this.rationTookHead + " of "
                        + this.rationCalls + " calls");
            }
            if (this.rationMissedAdmiral > 0) {
                problems.add("the ration did not turn the idle head to the admiral in " + this.rationMissedAdmiral
                        + " of " + this.rationCalls + " calls");
            }
            if (this.wrongHead > 0) problems.add("the head was more than " + this.maxOff() + " degrees off its "
                    + (this.engage ? "target" : "destination") + " in " + this.wrongHead + " of " + this.checked
                    + " ticks: " + this.late);
            if (this.lastLook.isEmpty() || this.lastLook.get().reason() != expected) {
                problems.add("the last look was " + this.lastLook + ", not " + expected);
            }
            return problems;
        }

        void guard(Runnable body) {
            try {
                body.run();
            } catch (Throwable error) {
                this.close();
                throw error;
            }
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            try {
                this.entities.close();
                if (this.owner != null) {
                    this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                }
                if (this.bystander != null) {
                    this.helper.getLevel().removePlayerImmediately(this.bystander, Entity.RemovalReason.DISCARDED);
                }
            } finally {
                this.authority.close();
            }
        }

        @Override
        public String toString() {
            return "released=" + this.releasedAt + " judged=" + this.checked + " watchRan=" + this.watchRan
                    + " rationCalls=" + this.rationCalls + " samples=" + this.samples
                    + " lastLook=" + this.lastLook + " at=" + (this.ship == null ? "-" : this.ship.position());
        }
    }
}
