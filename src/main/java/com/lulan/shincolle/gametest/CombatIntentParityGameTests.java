package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipActionGate;
import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipProjectile;
import com.lulan.shincolle.entity.battleship.EntityBBKirishimaMob;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.entity.carrier.EntityCarrierAkagi;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Under NEW the attack goals read whether they may fire, and at what, from the combat gate; this
 * must not change when they fire. Each test runs one fixture under LEGACY and then under NEW, both
 * spawned at the start of their phase, and compares:
 * <ul>
 *   <li>every call of an attack method, by weapon, recorded by overriding the method;</li>
 *   <li>the ticks on which each watched attack goal starts and stops (the goal registered when
 *       the ship's AI starts up, not the one registered with the entity).</li>
 * </ul>
 * Ticks count from the ship's first tick with a target after its AI started up. When the target
 * is acquired belongs to the target goals, which differ between the authorities by design and are
 * outside the gate; the report shows that tick for both.
 * Under NEW every tick also checks that the gate engages exactly when the conditions it replaced
 * (firing allowed, not sitting, not on a ship mount, a live target from the entity target) hold,
 * and at that same target.
 * <p>
 * Under NEW the fire control goal now fires on the ship's one timer. With no other goal cutting
 * in, that timer counts as the attack goals did, so the calls still match. The mount is the
 * exception: the rider's target authority is made again when its AI starts up, leaving the lock
 * empty for a tick; the old mount goal kept shooting its remembered target, while NEW takes the
 * returning lock as a new engagement and aims again. There the weapons and the gaps between the
 * calls must match, counted from the first call.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatIntentParityGameTests {
    private static final int PHASE = 220;
    private static final int OWNER_UID = 8401;

    private CombatIntentParityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_combat_intent_cannon",
            timeoutTicks = 2 * PHASE + 20)
    public static void cannonFiresOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, CombatIntentParityGameTests::cannon);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_combat_intent_carrier",
            timeoutTicks = 2 * PHASE + 20)
    public static void carrierLaunchesOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, CombatIntentParityGameTests::carrier);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_combat_intent_melee",
            timeoutTicks = 2 * PHASE + 20)
    public static void meleeStrikesOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, CombatIntentParityGameTests::melee);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_combat_intent_hostile",
            timeoutTicks = 2 * PHASE + 20)
    public static void hostileCannonFiresOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, CombatIntentParityGameTests::hostileCannon);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_combat_intent_hold",
            timeoutTicks = 2 * PHASE + 20)
    public static void holdFireConditionsMatchUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, CombatIntentParityGameTests::holdFire);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_combat_intent_mount",
            timeoutTicks = 2 * PHASE + 20)
    public static void mountFiresForItsRiderOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, CombatIntentParityGameTests::mounted, true);
    }

    private static void verify(GameTestHelper helper, Scenario scenario) {
        verify(helper, scenario, false);
    }

    private static void verify(GameTestHelper helper, Scenario scenario, boolean fromFirstCall) {
        Outcome legacy = new Outcome();
        Outcome now = new Outcome();
        phase(helper, 1, ConfigHandler.ShipAiTargetAuthority.LEGACY, scenario, legacy);
        phase(helper, 2 + PHASE, ConfigHandler.ShipAiTargetAuthority.NEW, scenario, now);
        // runAtTickTime does not order two runnables of one tick, so no two stages share a tick
        helper.runAtTickTime(3 + 2 * PHASE, () -> {
            String report = "\n LEGACY " + legacy + "\n NEW    " + now;
            helper.assertTrue(legacy.problems.isEmpty() && now.problems.isEmpty(), "Fixture problems:" + report);
            helper.assertTrue(fromFirstCall ? fromFirst(now.calls).equals(fromFirst(legacy.calls))
                    : now.calls.equals(legacy.calls), "Attack calls differ:" + report);
            helper.assertTrue(now.goals.equals(legacy.goals), "Attack goals differ:" + report);
            // the evidence of a passing run, for the change note and review
            LogHelper.info("Combat intent parity:" + report);
            helper.succeed();
        });
    }

    private static void phase(GameTestHelper helper, int start, ConfigHandler.ShipAiTargetAuthority mode,
                              Scenario scenario, Outcome result) {
        Phase[] phase = {null};
        helper.runAtTickTime(start, () -> phase[0] = Phase.open(helper, mode, scenario));
        for (int tick = start + 1; tick < start + PHASE; tick++) {
            helper.runAtTickTime(tick, () -> phase[0].guard(phase[0]::poll));
        }
        helper.runAtTickTime(start + PHASE, () -> phase[0].guard(() -> {
            phase[0].finish(mode, result);
            phase[0].close();
        }));
    }

    // ---------- fixtures ----------

    private static Fixture cannon(GameTestHelper helper, GameTestEntities entities) {
        RecordingKongou ship = friendly(helper, entities, new RecordingKongou(ModEntities.BB_KONGOU.get(),
                helper.getLevel()), new Vec3(1.5D, 2D, 1.5D));
        cannonFlags(ship);
        hostileTarget(helper, entities, new Vec3(7.5D, 2D, 1.5D));
        return new Fixture(List.of(new Watched("cannon", ship, ShipRangeAttackGoal.class)),
                outcome -> fired(outcome, "cannon", ":light"));
    }

    private static Fixture carrier(GameTestHelper helper, GameTestEntities entities) {
        RecordingAkagi ship = friendly(helper, entities, new RecordingAkagi(ModEntities.CV_AKAGI.get(),
                helper.getLevel()), new Vec3(1.5D, 2D, 1.5D));
        ship.setStateFlag(ID.F.AtkType_AirLight, true);
        ship.setStateFlag(ID.F.AtkType_AirHeavy, true);
        ship.setStateFlag(ID.F.UseAirLight, true);
        ship.setStateFlag(ID.F.UseAirHeavy, true);
        ship.setAmmoLight(1_000);
        ship.setAmmoHeavy(1_000);
        ((IShipAircraftAttack) ship).setNumAircraftLight(20);
        ((IShipAircraftAttack) ship).setNumAircraftHeavy(20);
        hostileTarget(helper, entities, new Vec3(9.5D, 2D, 1.5D));
        return new Fixture(List.of(new Watched("carrier", ship, ShipCarrierAttackGoal.class)),
                outcome -> fired(outcome, "carrier", ":air"));
    }

    private static Fixture melee(GameTestHelper helper, GameTestEntities entities) {
        RecordingKongou ship = friendly(helper, entities, new RecordingKongou(ModEntities.BB_KONGOU.get(),
                helper.getLevel()), new Vec3(1.5D, 2D, 1.5D));
        ship.setStateFlag(ID.F.UseMelee, true);
        hostileTarget(helper, entities, new Vec3(3.5D, 2D, 1.5D));
        return new Fixture(List.of(new Watched("melee", ship, ShipAttackOnCollideGoal.class)),
                outcome -> fired(outcome, "melee", ":melee"));
    }

    private static Fixture hostileCannon(GameTestHelper helper, GameTestEntities entities) {
        RecordingKirishima ship = entities.add(new RecordingKirishima(ModEntities.BB_KIRISHIMA_MOB.get(),
                helper.getLevel()));
        ship.setNoAi(false);
        ship.setInvulnerable(true);
        ship.setPersistenceRequired();
        ship.calcShipAttributes(31, false);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.OnSightChase, false);
        ship.setStateFlag(ID.F.UseMelee, false);
        ship.setStateFlag(ID.F.AtkType_Light, true);
        ship.setStateFlag(ID.F.AtkType_Heavy, true);
        ship.setStateFlag(ID.F.UseAmmoLight, true);
        ship.setStateFlag(ID.F.UseAmmoHeavy, true);
        ship.setAmmoLight(1_000);
        ship.setAmmoHeavy(1_000);
        ship.getAttrs().setAttrsBuffed(ID.Attrs.MOV, 0F);
        add(helper, ship, new Vec3(1.5D, 2D, 1.5D));
        BasicEntityShip target = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(target != null, "failed to create target ship");
        target.setNoAi(true);
        target.setInvulnerable(true);
        target.setPlayerUID(OWNER_UID);
        add(helper, target, new Vec3(7.5D, 2D, 1.5D));
        return new Fixture(List.of(new Watched("hostile", ship, ShipRangeAttackGoal.class)),
                outcome -> fired(outcome, "hostile", ":light"));
    }

    /**
     * Every reason to hold fire that a lone ship can have, each on its own ship, beside one ship
     * free to fire. The free ship stands apart with its own target.
     */
    private static Fixture holdFire(GameTestHelper helper, GameTestEntities entities) {
        List<Watched> watched = new ArrayList<>();
        String[] names = {"sitting", "crane", "noFuel", "free"};
        double[] xs = {0.5D, 3.5D, 6.5D, 21.5D};
        for (int i = 0; i < names.length; i++) {
            RecordingKongou ship = friendly(helper, entities, new RecordingKongou(ModEntities.BB_KONGOU.get(),
                    helper.getLevel()), new Vec3(xs[i], 2D, 1.5D));
            cannonFlags(ship);
            switch (names[i]) {
                case "sitting" -> ship.setEntitySit(true);
                case "crane" -> {
                    ship.setStateTimer(ID.T.CrandDelay, 0);
                    ship.setStateMinor(ID.M.CraneState, 1);
                }
                case "noFuel" -> {
                    ship.setStateMinor(ID.M.NumGrudge, 0);
                    ship.decrGrudgeNum(0);
                }
                case "free" -> hostileTarget(helper, entities, new Vec3(xs[i], 2D, 7.5D));
                default -> { }
            }
            watched.add(new Watched(names[i], ship, ShipRangeAttackGoal.class));
        }
        hostileTarget(helper, entities, new Vec3(3.5D, 2D, 7.5D));
        return new Fixture(watched, outcome -> {
            List<String> problems = new ArrayList<>(fired(outcome, "free", ":light"));
            for (String name : List.of("sitting", "crane", "noFuel")) {
                if (!outcome.calls.getOrDefault(name, List.of()).isEmpty()) problems.add(name + " ship fired");
                if (!outcome.goals.getOrDefault(name, List.of()).isEmpty()) problems.add(name + " ship's goal ran");
            }
            if (!((BasicEntityShip) watched.get(0).mob()).getIsSitting()) problems.add("the sitting ship stood up");
            if (((BasicEntityShip) watched.get(1).mob()).getStateMinor(ID.M.CraneState) <= 0) {
                problems.add("the crane state was cleared");
            }
            if (!((BasicEntityShip) watched.get(2).mob()).getStateFlag(ID.F.NoFuel)) {
                problems.add("the dry ship was refuelled");
            }
            return problems;
        });
    }

    /**
     * A ship on a ship mount: its own cannon goal must not run, and the mount fires for it. The
     * mount's shots reach the ship's attack methods, which record that the ship was riding.
     */
    private static Fixture mounted(GameTestHelper helper, GameTestEntities entities) {
        RecordingKongou ship = friendly(helper, entities, new RecordingKongou(ModEntities.BB_KONGOU.get(),
                helper.getLevel()), new Vec3(1.5D, 2D, 1.5D));
        cannonFlags(ship);
        BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
        helper.assertTrue(mount != null, "failed to create mount");
        mount.setInvulnerable(true);
        add(helper, mount, new Vec3(1.5D, 2D, 1.5D));
        mount.setHost(ship);
        helper.assertTrue(ship.startRiding(mount, true), "ship could not ride its mount");
        WrappedGoal initialRiderGoal = goal(ship, ShipRangeAttackGoal.class);
        boolean[] targetAdded = {false};
        Runnable prepareCombat = () -> {
            if (targetAdded[0] || ship.tickCount < 24 || mount.tickCount < 24) return;
            WrappedGoal riderGoal = goal(ship, ShipRangeAttackGoal.class);
            WrappedGoal mountGoal = goal(mount, ShipRangeAttackGoal.class);
            helper.assertTrue(riderGoal != null && riderGoal != initialRiderGoal,
                    "Fixture: rider AI did not finish registering before combat");
            helper.assertTrue(ship.getEntityTarget() == null && mountGoal != null && !mountGoal.isRunning(),
                    "Fixture: mounted combat began before the target was placed");
            hostileTarget(helper, entities, new Vec3(7.5D, 2D, 1.5D));
            targetAdded[0] = true;
        };
        return new Fixture(List.of(new Watched("rider", ship, ShipRangeAttackGoal.class),
                new Watched("mount", mount, ShipRangeAttackGoal.class, "rider")), outcome -> {
            List<String> problems = new ArrayList<>();
            Integer acquired = outcome.acquired.get("rider");
            if (!targetAdded[0] || acquired == null || acquired < 24) {
                problems.add("mounted target was not acquired after AI initialization");
            }
            List<String> calls = outcome.calls.getOrDefault("rider", List.of());
            if (calls.isEmpty()) problems.add("the mount never fired");
            if (calls.stream().anyMatch(call -> !call.endsWith(":viaMount"))) problems.add("the rider fired itself");
            if (!outcome.goals.getOrDefault("rider", List.of()).isEmpty()) problems.add("the rider's goal ran");
            if (ship.dismount != null) problems.add("the rider left its mount at " + ship.dismount);
            return problems;
        }, prepareCombat);
    }

    /** Each ship's calls with their ticks counted from its first call. */
    private static Map<String, List<String>> fromFirst(Map<String, List<String>> calls) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        calls.forEach((name, list) -> {
            int first = list.isEmpty() ? 0 : Integer.parseInt(list.get(0).substring(0, list.get(0).indexOf(':')));
            out.put(name, list.stream().map(call -> {
                int colon = call.indexOf(':');
                return (Integer.parseInt(call.substring(0, colon)) - first) + call.substring(colon);
            }).toList());
        });
        return out;
    }

    private static List<String> fired(Outcome outcome, String name, String weapon) {
        boolean any = outcome.calls.getOrDefault(name, List.of()).stream().anyMatch(call -> call.contains(weapon));
        return any ? List.of() : List.of(name + " never called " + weapon);
    }

    private static <T extends BasicEntityShip> T friendly(GameTestHelper helper, GameTestEntities entities, T ship,
                                                          Vec3 position) {
        entities.add(ship);
        ship.setPlayerUID(OWNER_UID);
        // A fresh ship has only a few HP; stray damage kills it or stands it up
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
        ship.getAttrs().setAttrsBuffed(ID.Attrs.MOV, 0F);
        add(helper, ship, position);
        return ship;
    }

    private static void cannonFlags(BasicEntityShip ship) {
        ship.setStateFlag(ID.F.AtkType_Light, true);
        ship.setStateFlag(ID.F.AtkType_Heavy, true);
        ship.setStateFlag(ID.F.UseAmmoLight, true);
        ship.setStateFlag(ID.F.UseAmmoHeavy, true);
        ship.setAmmoLight(1_000);
        ship.setAmmoHeavy(1_000);
    }

    private static void hostileTarget(GameTestHelper helper, GameTestEntities entities, Vec3 position) {
        BasicEntityShipHostile target = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        helper.assertTrue(target != null, "failed to create target");
        target.setNoAi(true);
        target.setInvulnerable(true);
        target.setPersistenceRequired();
        add(helper, target, position);
    }

    private static void add(GameTestHelper helper, Entity entity, Vec3 relative) {
        Vec3 absolute = helper.absoluteVec(relative);
        entity.moveTo(absolute.x, absolute.y, absolute.z, 0F, 0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "failed to add " + entity);
    }

    // ---------- recording entities ----------

    private interface Recording {
        List<String> calls();
    }

    private static String record(Entity self, String weapon) {
        return self.tickCount + ":" + weapon + (self.getVehicle() instanceof BasicEntityMount ? ":viaMount" : "");
    }

    private static String stack() {
        return Arrays.stream(new Throwable().getStackTrace()).skip(1).limit(14)
                .map(StackTraceElement::toString).collect(Collectors.joining(" <- "));
    }

    private static final class RecordingKongou extends EntityBBKongou implements Recording {
        private final List<String> calls = new ArrayList<>();
        private String dismount;

        private RecordingKongou(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        @Override
        public List<String> calls() {
            return this.calls;
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.calls.add(record(this, "light"));
            return super.attackEntityWithAmmo(target);
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) {
            this.calls.add(record(this, "heavy"));
            return super.attackEntityWithHeavyAmmo(target);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            this.calls.add(record(this, "melee"));
            return super.doHurtTarget(target);
        }

        @Override
        public void removeVehicle() {
            if (this.dismount == null && this.getVehicle() != null && !this.level().isClientSide()) {
                this.dismount = this.tickCount + " " + stack();
            }
            super.removeVehicle();
        }
    }

    private static final class RecordingAkagi extends EntityCarrierAkagi implements Recording {
        private final List<String> calls = new ArrayList<>();

        private RecordingAkagi(EntityType<? extends EntityCarrierAkagi> type, Level level) {
            super(type, level);
        }

        @Override
        public List<String> calls() {
            return this.calls;
        }

        @Override
        public boolean attackEntityWithAircraft(Entity target) {
            this.calls.add(record(this, "airLight"));
            return super.attackEntityWithAircraft(target);
        }

        @Override
        public boolean attackEntityWithHeavyAircraft(Entity target) {
            this.calls.add(record(this, "airHeavy"));
            return super.attackEntityWithHeavyAircraft(target);
        }
    }

    private static final class RecordingKirishima extends EntityBBKirishimaMob implements Recording {
        private final List<String> calls = new ArrayList<>();

        private RecordingKirishima(EntityType<? extends EntityBBKirishimaMob> type, Level level) {
            super(type, level);
        }

        @Override
        public List<String> calls() {
            return this.calls;
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.calls.add(record(this, "light"));
            return super.attackEntityWithAmmo(target);
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) {
            this.calls.add(record(this, "heavy"));
            return super.attackEntityWithHeavyAmmo(target);
        }
    }

    // ---------- recording ----------

    private interface Scenario {
        Fixture create(GameTestHelper helper, GameTestEntities entities);
    }

    private interface Check {
        List<String> problems(Outcome outcome);
    }

    /** {@code anchor} names the watched ship whose target acquisition the ticks count from. */
    private record Watched(String name, Mob mob, Class<?> goalType, String anchor) {
        Watched(String name, Mob mob, Class<?> goalType) {
            this(name, mob, goalType, name);
        }
    }

    private record Fixture(List<Watched> watched, Check check, Runnable prepare) {
        Fixture(List<Watched> watched, Check check) {
            this(watched, check, () -> { });
        }
    }

    private record Sample(int tick, WrappedGoal goal, boolean hasTarget) {
    }

    private static final class Outcome {
        private final Map<String, List<String>> calls = new LinkedHashMap<>();
        private final Map<String, List<String>> goals = new LinkedHashMap<>();
        private final List<String> problems = new ArrayList<>();
        private final Map<String, Integer> acquired = new LinkedHashMap<>();
        private String gateChecks = "-";

        @Override
        public String toString() {
            return "acquired=" + this.acquired + " calls=" + this.calls + " goals=" + this.goals
                    + " gate=" + this.gateChecks + " problems=" + this.problems;
        }
    }

    private static final class Phase {
        private final GameTestHelper helper;
        private final ShipAiAuthorityOverride authority;
        private final GameTestEntities entities;
        private final Fixture fixture;
        private final Map<String, List<String>> goals = new HashMap<>();
        private final Map<String, Boolean> running = new HashMap<>();
        private final Map<String, WrappedGoal> instances = new HashMap<>();
        private final Map<String, List<Sample>> samples = new HashMap<>();
        private final List<String> gateMismatches = new ArrayList<>();
        private int gateEngaged;
        private int gateHolding;
        private boolean closed;

        private Phase(GameTestHelper helper, ShipAiAuthorityOverride authority, GameTestEntities entities,
                      Fixture fixture) {
            this.helper = helper;
            this.authority = authority;
            this.entities = entities;
            this.fixture = fixture;
        }

        static Phase open(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode, Scenario scenario) {
            ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(mode);
            GameTestEntities entities = null;
            try {
                entities = GameTestEntities.open(helper);
                return new Phase(helper, authority, entities, scenario.create(helper, entities));
            } catch (RuntimeException | Error failure) {
                try {
                    if (entities != null) entities.close();
                } finally {
                    authority.close();
                }
                throw failure;
            }
        }

        void guard(Runnable body) {
            try {
                body.run();
            } catch (Throwable error) {
                close();
                throw error;
            }
        }

        void poll() {
            this.fixture.prepare().run();
            for (Watched watched : this.fixture.watched()) {
                Mob mob = watched.mob();
                WrappedGoal goal = goal(mob, watched.goalType());
                if (goal != null && this.instances.put(watched.name(), goal) != goal) {
                    // The ship registers its goals again once its AI starts up; record the goal
                    // that stays, not the one registered with the entity.
                    this.goals.remove(watched.name());
                    this.running.remove(watched.name());
                }
                this.samples.computeIfAbsent(watched.name(), name -> new ArrayList<>()).add(
                        new Sample(mob.tickCount, goal, ((IShipAttackBase) mob).getEntityTarget() != null));
                boolean isRunning = goal != null && goal.isRunning();
                Boolean was = this.running.put(watched.name(), isRunning);
                if (was != null && was != isRunning || was == null && isRunning) {
                    this.goals.computeIfAbsent(watched.name(), name -> new ArrayList<>())
                            .add(mob.tickCount + (isRunning ? ":start" : ":stop"));
                }
                if (ShipCombatGate.active(mob)) checkGate(watched.name(), mob);
            }
        }

        /** The gate against the conditions the NEW attack goals checked before it. */
        private void checkGate(String name, Mob mob) {
            IShipAttackBase base = (IShipAttackBase) mob;
            Entity target = base.getEntityTarget();
            boolean before = !ShipActionGate.blocked(mob, ActionKind.FIRING) && !base.getIsSitting()
                    && !(base.getIsRiding() && mob.getVehicle() instanceof BasicEntityMount)
                    && target != null && target.isAlive();
            ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(mob);
            if (engagement.engaged()) this.gateEngaged++;
            else this.gateHolding++;
            if (before != engagement.engaged() || before && engagement.target() != target) {
                if (this.gateMismatches.size() < 5) {
                    this.gateMismatches.add(name + "@" + mob.tickCount + " before=" + before + " gate="
                            + engagement.intent());
                }
            }
        }

        void finish(ConfigHandler.ShipAiTargetAuthority mode, Outcome result) {
            for (Watched watched : this.fixture.watched()) {
                Integer anchor = acquired(watched.anchor());
                if (watched.anchor().equals(watched.name())) result.acquired.put(watched.name(), anchor);
                int base = anchor == null ? 0 : anchor;
                if (watched.mob() instanceof Recording recording) {
                    result.calls.put(watched.name(), relative(recording.calls(), base));
                }
                result.goals.put(watched.name(), relative(this.goals.getOrDefault(watched.name(), List.of()), base));
            }
            result.problems.addAll(this.fixture.check().problems(result));
            if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                result.gateChecks = "engaged=" + this.gateEngaged + " holding=" + this.gateHolding;
                if (this.gateEngaged + this.gateHolding == 0) result.problems.add("the gate was never active");
                result.problems.addAll(this.gateMismatches);
            }
        }

        /** The first tick on which the goal that stays is registered and the ship has a target. */
        private Integer acquired(String name) {
            WrappedGoal last = this.instances.get(name);
            return this.samples.getOrDefault(name, List.of()).stream()
                    .filter(sample -> sample.goal() == last && last != null && sample.hasTarget())
                    .map(Sample::tick).findFirst().orElse(null);
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            try {
                this.entities.close();
                // aircraft and shells outlive the phase; clear them before the next authority
                AABB area = new AABB(this.helper.absolutePos(BlockPos.ZERO)).inflate(40D);
                for (Entity leftover : this.helper.getLevel().getEntitiesOfClass(Entity.class, area,
                        entity -> entity instanceof BasicEntityAirplane || entity instanceof IShipProjectile
                                || entity instanceof Projectile)) {
                    leftover.discard();
                }
            } finally {
                this.authority.close();
            }
        }
    }

    /** Rewrites each "tick:rest" entry to count from {@code base}. */
    private static List<String> relative(List<String> entries, int base) {
        return entries.stream().map(entry -> {
            int colon = entry.indexOf(':');
            return (Integer.parseInt(entry.substring(0, colon)) - base) + entry.substring(colon);
        }).toList();
    }

    private static WrappedGoal goal(Mob mob, Class<?> goalType) {
        return selector(mob).getAvailableGoals().stream()
                .filter(wrapped -> goalType.isInstance(wrapped.getGoal()))
                .findFirst().orElse(null);
    }

    private static GoalSelector selector(Mob mob) {
        try {
            Field field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to inspect goalSelector: " + error);
        }
    }
}
