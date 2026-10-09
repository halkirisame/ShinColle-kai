package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFleeGoal;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.ShipAiCompatibilityRules;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Under NEW one flagless goal fires every weapon on one timer, so moving does not stop a ship
 * from firing and a switch of goals does not shorten the wait. Each test records every call of
 * an attack method with the tick, the weapon, the target and which movement goal was running at
 * that moment. The tests use only what existed before this change, so they run against it too.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatFireControlGameTests {
    private static final int LOCK = 20;

    private CombatFireControlGameTests() {
    }

    /** The owner walked away; the ship fires at its target while it follows. */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_follow",
            timeoutTicks = 200)
    public static void followingShipFiresWhileItReturns(GameTestHelper helper) {
        run(helper, 180, s -> {
            s.owner(new Vec3(28.5D, 0D, 2.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            ship.setStateMinor(ID.M.FollowMin, 1);
            ship.setStateMinor(ID.M.FollowMax, 4);
            s.cannons(ship);
            Mob cow = s.cow(new Vec3(8.5D, 0D, 6.5D));
            s.at(LOCK, () -> s.attack(ship, cow));
            s.end(ship, () -> s.expect(ship.calls.stream().anyMatch(c -> c.cannon() && c.moving.equals("follow")),
                    "No shot while following: " + ship.calls));
        });
    }

    /** A badly damaged ship runs to its owner and fires on the way. */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_flee",
            timeoutTicks = 200)
    public static void fleeingShipFires(GameTestHelper helper) {
        run(helper, 180, s -> {
            // far enough that the ship is still running when its aim is ready
            s.owner(new Vec3(40.5D, 0D, 2.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            ship.setStateMinor(ID.M.FleeHP, 50);
            ship.setHealth(ship.getMaxHealth() * 0.1F);
            s.cannons(ship);
            Mob cow = s.cow(new Vec3(2.5D, 0D, 5.5D));
            s.at(LOCK, () -> s.attack(ship, cow));
            // a fleeing ship outruns its aim, so the target keeps pace inside the manual range
            s.every(LOCK, 170, () -> cow.moveTo(ship.getX() + 2D, ship.getY(), ship.getZ() + 3D));
            s.end(ship, () -> {
                s.expect(ship.getHealth() < ship.getMaxHealth() * 0.5F, "Fixture must stay below flee HP");
                s.expect(ship.calls.stream().anyMatch(c -> c.cannon() && c.moving.equals("flee")),
                        "No shot while fleeing: " + ship.calls + " " + ship.probe(cow));
            });
        });
    }

    /** A melee ship following its owner strikes the target beside it without stopping. */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_melee",
            timeoutTicks = 200)
    public static void movingMeleeShipStrikesTheTargetBesideIt(GameTestHelper helper) {
        run(helper, 180, s -> {
            s.owner(new Vec3(30.5D, 0D, 2.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            ship.setStateMinor(ID.M.FollowMin, 1);
            ship.setStateMinor(ID.M.FollowMax, 4);
            ship.setStateFlag(ID.F.UseMelee, true);
            Mob cow = s.cow(new Vec3(0.5D, 0D, 3.7D));
            s.at(LOCK, () -> s.attack(ship, cow));
            // the target keeps beside the ship, inside its reach
            s.every(LOCK, 170, () -> cow.moveTo(ship.getX(), ship.getY(), ship.getZ() + 1.2D));
            s.end(ship, () -> s.expect(ship.calls.stream().anyMatch(c -> c.weapon.equals("melee")
                    && c.moving.equals("follow")), "No strike while following: " + ship.calls));
        });
    }

    /**
     * A guarding ship fires on its way to the guard position and keeps firing there;
     * the arrival must not bring the next shot of the same cannon forward.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_guard",
            timeoutTicks = 300)
    public static void arrivingAtTheGuardPositionKeepsTheCannonWait(GameTestHelper helper) {
        run(helper, 280, s -> {
            s.owner(new Vec3(0.5D, 0D, -6.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            s.cannons(ship);
            ship.setStateFlag(ID.F.UseAmmoHeavy, false);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
            // in range soon after the start and at the guard position
            Mob cow = s.cow(new Vec3(9.5D, 0D, 5.5D));
            s.at(LOCK, () -> {
                BlockPos guard = s.helper.absolutePos(new BlockPos(16, 0, 2));
                var id = s.helper.getLevel().dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(s.owner.getUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(guard.getX(), guard.getY(), guard.getZ()), false)));
                // a position order clears the manual attack, so the attack comes second
                s.attack(ship, cow);
            });
            s.end(ship, () -> {
                List<Call> light = ship.calls.stream().filter(c -> c.weapon.equals("light")).toList();
                int delay = Math.max(5, (int) (ConfigHandler.baseAttackSpeed[1]
                        / Math.max(ship.getAttrs().getAttackSpeed(), 0.01F)) + ConfigHandler.fixedAttackDelay[1]);
                s.expect(light.stream().anyMatch(c -> c.moving.equals("guard")),
                        "No shot while guarding: " + light + " " + ship.probe(cow));
                s.expect(light.stream().anyMatch(c -> !c.moving.equals("guard")),
                        "No shot after arriving: " + light);
                for (int i = 1; i < light.size(); i++) {
                    s.expect(light.get(i).tick - light.get(i - 1).tick >= delay,
                            "Two light shots closer than " + delay + " ticks: " + light);
                }
            });
        });
    }

    /**
     * While the ship still paths toward its target, a new lock turns the next shot to
     * the new target. The lock switches while the ship aims at the first target; the ship is put
     * back every tick, so its path toward the first target does not end. (Attributes set on a ship
     * return to its own within 16 ticks, so no speed is set.)
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_switch",
            timeoutTicks = 200)
    public static void aNewLockMidPathTurnsTheNextShot(GameTestHelper helper) {
        run(helper, 180, s -> {
            s.owner(new Vec3(0.5D, 0D, -3.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            s.cannons(ship);
            ship.setStateFlag(ID.F.UseAmmoHeavy, false);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
            // with melee on, the cannon goal keeps closing in instead of holding in range
            ship.setStateFlag(ID.F.UseMelee, true);
            Mob first = s.cow(new Vec3(6.5D, 0D, 2.5D));
            Mob second = s.cow(new Vec3(0.5D, 0D, 9.5D));
            Vec3 start = s.helper.absoluteVec(new Vec3(0.5D, 0D, 2.5D));
            s.at(LOCK, () -> s.attack(ship, first));
            s.every(LOCK, 170, () -> ship.moveTo(start.x, ship.getY(), start.z));
            int[] switched = {-1};
            s.at(LOCK + 12, () -> {
                s.expect(ship.getEntityTarget() == first, "Fixture must lock the first target: " + ship.probe(first));
                s.expect(ship.calls.isEmpty(), "Fixture must switch before the first shot: " + ship.calls);
                s.attack(ship, second);
                switched[0] = ship.tickCount;
            });
            s.end(ship, () -> {
                s.expect(ship.getEntityTarget() == second, "Fixture must lock the second target");
                List<Call> cannon = ship.calls.stream().filter(Call::cannon).toList();
                s.expect(!cannon.isEmpty(), "No cannon shot after the switch: " + ship.calls + " "
                        + ship.probe(second));
                s.expect(cannon.stream().allMatch(c -> c.target == second),
                        "A shot after the switch hit the old target (switched at " + switched[0] + "): "
                                + ship.calls);
            });
        });
    }

    /** With two enemies in range and the lock unchanged, every shot goes to the lock. */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_steady",
            timeoutTicks = 220)
    public static void twoEnemiesInRangeDoNotSwapTheTarget(GameTestHelper helper) {
        run(helper, 200, s -> {
            s.owner(new Vec3(0.5D, 0D, -3.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            s.cannons(ship);
            Entity a = s.hostile(new Vec3(6.5D, 0D, 0.5D));
            Entity b = s.hostile(new Vec3(6.5D, 0D, 4.5D));
            s.end(ship, () -> {
                List<Call> cannon = ship.calls.stream().filter(Call::cannon).toList();
                s.expect(cannon.size() >= 2, "Too few shots to compare: " + ship.calls);
                Entity locked = ship.getEntityTarget();
                s.expect(locked == a || locked == b, "Fixture must lock one of the enemies: " + locked);
                s.expect(cannon.stream().allMatch(c -> c.target == locked),
                        "The shots changed target without the lock changing: " + ship.calls);
            });
        });
    }

    /**
     * The cannons aim only while they may fire. A ship on the crane keeps its lock but does not
     * aim, so on release it aims afresh and waits one aim time before its first shot, as the range
     * goal that started on release did.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_crane",
            timeoutTicks = 200)
    public static void craneReleaseRestartsTheCannonAim(GameTestHelper helper) {
        run(helper, 160, s -> {
            s.owner(new Vec3(0.5D, 0D, -3.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            s.cannons(ship);
            Mob cow = s.cow(new Vec3(6.5D, 0D, 2.5D));
            s.at(LOCK - 1, () -> crane(ship, 1));
            s.at(LOCK, () -> s.attack(ship, cow));
            int[] released = {-1};
            s.at(LOCK + 60, () -> {
                s.expect(ship.getStateMinor(ID.M.CraneState) > 0, "Fixture must keep the crane");
                s.expect(ship.getEntityTarget() == cow, "Fixture must lock the target on the crane: "
                        + ship.probe(cow));
                s.expect(ship.calls.isEmpty(), "Fired on the crane: " + ship.calls);
                crane(ship, 0);
                released[0] = ship.tickCount;
            });
            s.end(ship, () -> {
                int aim = ShipAiCompatibilityRules.aimTime(ship.getLevel());
                s.expect(aim > 2, "Fixture needs an aim time longer than the tick order: " + aim);
                List<Call> cannon = ship.calls.stream().filter(Call::cannon).toList();
                s.expect(!cannon.isEmpty(), "No cannon shot after the release: " + ship.calls + " "
                        + ship.probe(cow));
                s.expect(cannon.get(0).tick - released[0] >= aim - 1, "Fired " + (cannon.get(0).tick - released[0])
                        + " ticks after the release, before the aim time " + aim + ": " + ship.calls);
            });
        });
    }

    /**
     * The same while the ship still paths toward its target: the crane stops the aim though the path
     * goes on, so on release the ship aims afresh. The ship is put back every tick, so its path toward
     * the target does not end.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_fire_control_crane_path",
            timeoutTicks = 200)
    public static void craneMidPathRestartsTheCannonAim(GameTestHelper helper) {
        run(helper, 170, s -> {
            s.owner(new Vec3(0.5D, 0D, -3.5D));
            Recorder ship = s.ship(new Vec3(0.5D, 0D, 2.5D));
            s.cannons(ship);
            // with melee on, the cannon goal keeps closing in instead of holding in range
            ship.setStateFlag(ID.F.UseMelee, true);
            Mob cow = s.cow(new Vec3(6.5D, 0D, 2.5D));
            Vec3 start = s.helper.absoluteVec(new Vec3(0.5D, 0D, 2.5D));
            s.at(LOCK, () -> s.attack(ship, cow));
            s.every(LOCK, 160, () -> ship.moveTo(start.x, ship.getY(), start.z));
            s.at(LOCK + 10, () -> {
                s.expect(ship.getEntityTarget() == cow, "Fixture must lock the target: " + ship.probe(cow));
                s.expect(!ship.getNavigation().isDone(), "Fixture must still path toward the target: "
                        + ship.probe(cow));
                s.expect(ship.calls.isEmpty(), "Fixture must take the crane before the first shot: " + ship.calls);
                crane(ship, 1);
            });
            int[] released = {-1};
            s.at(LOCK + 70, () -> {
                s.expect(ship.getStateMinor(ID.M.CraneState) > 0, "Fixture must keep the crane");
                s.expect(ship.calls.isEmpty(), "Fired on the crane: " + ship.calls);
                crane(ship, 0);
                released[0] = ship.tickCount;
            });
            s.end(ship, () -> {
                int aim = ShipAiCompatibilityRules.aimTime(ship.getLevel());
                s.expect(aim > 2, "Fixture needs an aim time longer than the tick order: " + aim);
                List<Call> cannon = ship.calls.stream().filter(Call::cannon).toList();
                s.expect(!cannon.isEmpty(), "No cannon shot after the release: " + ship.calls + " "
                        + ship.probe(cow));
                s.expect(cannon.get(0).tick - released[0] >= aim - 1, "Fired " + (cannon.get(0).tick - released[0])
                        + " ticks after the release, before the aim time " + aim + ": " + ship.calls);
            });
        });
    }

    private static void crane(Recorder ship, int state) {
        // a crane change is ignored for 20 ticks after the previous one
        ship.setStateTimer(ID.T.CrandDelay, 0);
        ship.setStateMinor(ID.M.CraneState, state);
    }

    // ---------- fixture ----------

    private static void run(GameTestHelper helper, int end, Consumer<Scene> build) {
        Scene scene = new Scene(helper);
        try {
            build.accept(scene);
        } catch (Throwable failure) {
            scene.close();
            throw failure;
        }
        helper.runAtTickTime(end, () -> {
            try {
                scene.finish.run();
                helper.succeed();
            } finally {
                scene.close();
            }
        });
    }

    private static final class Scene {
        final GameTestHelper helper;
        final ShipAiAuthorityOverride authority;
        final GameTestEntities entities;
        FakePlayer owner;
        boolean ownerAdded;
        Runnable finish = () -> { };

        Scene(GameTestHelper helper) {
            this.helper = helper;
            this.authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
            try {
                this.entities = GameTestEntities.open(helper);
            } catch (RuntimeException | Error failure) {
                this.authority.close();
                throw failure;
            }
        }

        void owner(Vec3 position) {
            this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "fire_control_owner"));
            this.owner.moveTo(this.helper.absoluteVec(position));
            this.helper.getLevel().addNewPlayer(this.owner);
            this.ownerAdded = true;
        }

        Recorder ship(Vec3 position) {
            Recorder ship = this.entities.add(new Recorder(ModEntities.BB_KONGOU.get(), this.helper.getLevel()));
            // a fresh ship has only a few HP; stray damage kills it or stands it up
            ship.setInvulnerable(true);
            ship.setEntitySit(false);
            ship.setStateMinor(ID.M.CraneState, 0);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateFlag(ID.F.PassiveAI, false);
            ship.setStateFlag(ID.F.OnSightChase, false);
            ship.setStateFlag(ID.F.UseMelee, false);
            ship.setStateFlag(ID.F.AtkType_Light, false);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
            ship.setStateFlag(ID.F.UseAmmoLight, false);
            ship.setStateFlag(ID.F.UseAmmoHeavy, false);
            ship.setStateMinor(ID.M.FormatType, 0);
            ship.calcShipAttributes(31, false);
            // raising the attributes leaves the old health, low enough to flee
            ship.setHealth(ship.getMaxHealth());
            ship.setOwnerUUID(this.owner.getUUID());
            ship.moveTo(this.helper.absoluteVec(position));
            this.expect(this.helper.getLevel().addFreshEntity(ship), "failed to add ship");
            return ship;
        }

        void cannons(Recorder ship) {
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.AtkType_Heavy, true);
            ship.setStateFlag(ID.F.UseAmmoLight, true);
            ship.setStateFlag(ID.F.UseAmmoHeavy, true);
            ship.setAmmoLight(1_000);
            ship.setAmmoHeavy(1_000);
        }

        Mob cow(Vec3 position) {
            Mob cow = this.entities.add(EntityType.COW.create(this.helper.getLevel()));
            this.expect(cow != null, "failed to create cow");
            cow.setNoAi(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5_000D);
            cow.setHealth(5_000F);
            cow.moveTo(this.helper.absoluteVec(position));
            this.expect(this.helper.getLevel().addFreshEntity(cow), "failed to add cow");
            return cow;
        }

        Entity hostile(Vec3 position) {
            BasicEntityShipHostile target = this.entities.add(
                    ModEntities.BB_KIRISHIMA_MOB.get().create(this.helper.getLevel()));
            this.expect(target != null, "failed to create hostile");
            target.setNoAi(true);
            target.setInvulnerable(true);
            target.setPersistenceRequired();
            target.moveTo(this.helper.absoluteVec(position));
            this.expect(this.helper.getLevel().addFreshEntity(target), "failed to add hostile");
            return target;
        }

        void attack(Recorder ship, Entity target) {
            ship.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(target))));
        }

        void at(int tick, Runnable body) {
            this.helper.runAtTickTime(tick, () -> this.guard(body));
        }

        void every(int from, int to, Runnable body) {
            for (int tick = from; tick < to; tick++) this.at(tick, body);
        }

        /** The final check; the calls are logged first, as the evidence for the change note and review. */
        void end(Recorder ship, Runnable check) {
            this.finish = () -> {
                LogHelper.info("Fire control calls: " + ship.calls);
                check.run();
            };
        }

        void expect(boolean condition, String message) {
            this.helper.assertTrue(condition, message);
        }

        void guard(Runnable body) {
            try {
                body.run();
            } catch (Throwable failure) {
                this.close();
                throw failure;
            }
        }

        void close() {
            try {
                if (this.ownerAdded) {
                    this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                    this.ownerAdded = false;
                }
                this.entities.close();
            } finally {
                this.authority.close();
            }
        }
    }

    /** One attack call: the ship's tick, the weapon, the target, the movement goal running then. */
    private record Call(int tick, String weapon, Entity target, String moving) {
        boolean cannon() {
            return this.weapon.equals("light") || this.weapon.equals("heavy");
        }

        @Override
        public String toString() {
            return this.tick + ":" + this.weapon + "@" + (this.target == null ? "-" : this.target.getId())
                    + (this.moving.isEmpty() ? "" : "/" + this.moving);
        }
    }

    private static final class Recorder extends EntityBBKongou {
        final List<Call> calls = new ArrayList<>();

        private Recorder(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        private void record(String weapon, Entity target) {
            this.calls.add(new Call(this.tickCount, weapon, target, this.moving()));
        }

        private String moving() {
            if (this.running(ShipFleeGoal.class)) return "flee";
            if (this.running(ShipGuardingGoal.class)) return "guard";
            if (this.running(ShipFollowOwnerGoal.class)) return "follow";
            return "";
        }

        String probe(Entity target) {
            List<String> running = this.goalSelector.getAvailableGoals().stream().filter(w -> w.isRunning())
                    .map(w -> w.getGoal().getClass().getSimpleName()).toList();
            return "[entityTarget=" + this.getEntityTarget() + " dist=" + Math.sqrt(this.distanceToSqr(target))
                    + " range=" + this.getAttrs().getAttackRange() + " hp=" + this.getHealth() + "/"
                    + this.getMaxHealth() + " running=" + running + "]";
        }

        private boolean running(Class<? extends Goal> type) {
            return this.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.isRunning() && type.isInstance(wrapped.getGoal()));
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.record("light", target);
            return super.attackEntityWithAmmo(target);
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) {
            this.record("heavy", target);
            return super.attackEntityWithHeavyAmmo(target);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            this.record("melee", target);
            return super.doHurtTarget(target);
        }
    }
}
