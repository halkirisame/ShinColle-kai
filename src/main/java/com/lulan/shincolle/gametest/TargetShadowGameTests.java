package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.AiRandomSource;
import com.lulan.shincolle.ai.ShipRangeTargetGoal;
import com.lulan.shincolle.ai.TargetShadowComparison;
import com.lulan.shincolle.ai.TargetShadowComparison.Outcome;
import com.lulan.shincolle.ai.domain.AiRandom;
import com.lulan.shincolle.ai.domain.AiRandomStream;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TargetShadowGameTests {
    private TargetShadowGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_disabled_shadow_does_not_observe_or_count")
    public static void disabledShadowDoesNotObserveOrCount(GameTestHelper helper) {
        TargetShadowComparison.setEnabled(false);
        TargetShadowComparison.resetCounters();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Fixture f = new Fixture(helper, entities, false, 1, false);
            injectFailure(() -> { throw new AssertionError("Disabled shadow executed"); });
            check(f.scan(), "Legacy failed to acquire the target");
            check(TargetShadowComparison.metrics().equals(
                            new TargetShadowComparison.Metrics(0, 0, 0, 0, 0, 0, 0, 0)),
                    "OFF changed shadow metrics");
            for (Outcome outcome : Outcome.values()) {
                check(TargetShadowComparison.count(outcome) == 0L, "OFF classified a result");
            }
        } finally {
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_enabled_shadow_matches_friendly_and_hostile_selection")
    public static void enabledShadowMatchesFriendlyAndHostileSelection(GameTestHelper helper) {
        TargetShadowComparison.resetCounters();
        TargetShadowComparison.setEnabled(true);
        try {
            for (boolean hostile : new boolean[]{false, true}) {
                try (GameTestEntities entities = GameTestEntities.open(helper)) {
                    Fixture f = new Fixture(helper, entities, hostile, 1, false);
                    check(f.scan(), "Legacy did not select a target");
                    check(f.source.getTarget() == f.targets.get(0), "Legacy selected the wrong target");
                }
            }
            check(TargetShadowComparison.count(Outcome.MATCH) == 2L, "Both host paths must report MATCH");
            check(TargetShadowComparison.metrics().equals(
                            new TargetShadowComparison.Metrics(2, 2, 2, 2, 2, 0, 0, 0)),
                    "Expected two scans, two extra queries, two raw candidates and two selections");
        } finally {
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_shadow_throwable_cannot_change_legacy_selection")
    public static void shadowThrowableCannotChangeLegacySelection(GameTestHelper helper) {
        TargetShadowComparison.resetCounters();
        TargetShadowComparison.setEnabled(true);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Fixture f = new Fixture(helper, entities, false, 1, false);
            injectFailure(() -> { throw new AssertionError("Intentional shadow failure"); });
            check(f.scan(), "Shadow Throwable escaped or changed legacy success");
            check(f.source.getTarget() == f.targets.get(0), "Shadow error changed target authority");
            check(TargetShadowComparison.metrics().errors() == 1L, "Shadow error was not counted");
            check(TargetShadowComparison.metrics().spatialQueries() == 0L, "Injected failure unexpectedly queried");
        } finally {
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_shadow_replays_draw_without_changing_world_random_sequence")
    public static void shadowReplaysDrawWithoutChangingWorldRandomSequence(GameTestHelper helper) {
        TargetShadowComparison.resetCounters();
        try {
            for (boolean hostile : new boolean[]{false, true}) {
                for (int count : new int[]{0, 1, 2, 3, 5}) {
                    try (GameTestEntities entities = GameTestEntities.open(helper)) {
                        Fixture f = new Fixture(helper, entities, hostile, count, false);
                        for (long seed = 0L; seed < 20L; seed++) {
                            Entity offTarget = null;
                            for (boolean enabled : new boolean[]{false, true}) {
                                TargetShadowComparison.setEnabled(enabled);
                                // Pre-wiring algorithm: zero draws below three candidates, otherwise nextInt(3).
                                RandomSource expected = RandomSource.create(seed);
                                int expectedIndex = count > 2 ? expected.nextInt(3) : 0;
                                helper.getLevel().random.setSeed(seed);
                                check(f.scan() == (count > 0), "Selection availability changed");
                                Entity expectedTarget = count == 0 ? null : f.targets.get(expectedIndex);
                                check(f.source.getTarget() == expectedTarget, "Pre-wiring selection changed");
                                check(helper.getLevel().random.nextLong() == expected.nextLong(),
                                        "World random consumption changed");
                                if (!enabled) {
                                    offTarget = f.source.getTarget();
                                } else {
                                    check(f.source.getTarget() == offTarget, "ON and OFF selected different targets");
                                }
                            }
                        }
                    }
                }
            }
            check(TargetShadowComparison.count(Outcome.MATCH) == 200L, "Shared draws failed to match");
            check(TargetShadowComparison.metrics().equals(
                            new TargetShadowComparison.Metrics(200, 200, 440, 200, 440, 0, 0, 0)),
                    "Expected all seeded comparisons without shadow errors");
            for (Outcome outcome : Outcome.values()) {
                if (outcome != Outcome.MATCH) {
                    check(TargetShadowComparison.count(outcome) == 0L, "Wiring introduced a shadow difference");
                }
            }
        } finally {
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_random_source_client_contract")
    public static void randomSourceRejectsClientAndGoalSkipsClient(GameTestHelper helper) throws ReflectiveOperationException {
        TargetShadowComparison.resetCounters();
        TargetShadowComparison.setEnabled(true);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Fixture f = new Fixture(helper, entities, false, 3, false);
            ShipRangeTargetGoal goal = new ShipRangeTargetGoal((IShipAttackBase) f.source);
            Field side = Level.class.getDeclaredField("isClientSide");
            side.setAccessible(true);
            boolean originalSide = side.getBoolean(helper.getLevel());
            RandomSource expected = RandomSource.create(95L);
            helper.getLevel().random.setSeed(95L);
            // Synchronous fault injection of the exact side predicate; restore before entity cleanup.
            try {
                side.setBoolean(helper.getLevel(), true);
                check(f.source.level().isClientSide, "Client predicate injection failed");
                rejects(() -> AiRandomSource.forEntity(f.source, AiRandomStream.TARGET_SELECTION),
                        "AI random sources require a server level");
                check(!goal.canUse(), "Client goal must safely decline");
                check(f.source.getTarget() == null, "Client goal acquired a target");
                check(TargetShadowComparison.metrics().comparisons() == 0L, "Client goal scanned targets");
            } finally {
                side.setBoolean(helper.getLevel(), originalSide);
            }
            check(goal.canUse(), "Client call must not advance the scan cooldown");
            goal.start();
            check(f.source.getTarget() == f.targets.get(expected.nextInt(3)), "Client call consumed a draw");
            check(helper.getLevel().random.nextLong() == expected.nextLong(), "Unexpected world random consumption");
        } finally {
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_random_source_boundary_contract")
    public static void randomSourceChecksBoundsAndGoalRejectsInvalidDraws(GameTestHelper helper) throws ReflectiveOperationException {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Fixture f = new Fixture(helper, entities, false, 3, false);
            Field randomField = Level.class.getDeclaredField("random");
            randomField.setAccessible(true);
            RandomSource original = helper.getLevel().random;
            AtomicInteger draws = new AtomicInteger();
            int[] result = {0};
            RandomSource invalidSource = (RandomSource) Proxy.newProxyInstance(RandomSource.class.getClassLoader(),
                    new Class<?>[]{RandomSource.class}, (proxy, method, args) -> {
                        if (method.getName().equals("nextInt") && args != null && args.length == 1) {
                            draws.incrementAndGet();
                            return result[0];
                        }
                        return method.invoke(original, args);
                    });
            // No tick/yield occurs while the shared source is replaced; every exit restores it.
            try {
                randomField.set(helper.getLevel(), invalidSource);
                check(helper.getLevel().random == invalidSource, "Random source injection failed");
                AiRandom random = AiRandomSource.forEntity(f.source, AiRandomStream.TARGET_SELECTION);
                for (int bound : new int[]{0, -1, Integer.MIN_VALUE}) {
                    rejects(() -> random.nextBoundedInt(bound), "Bound must be positive");
                }
                check(draws.get() == 0, "Invalid bounds reached the provider");
                check(random.nextBoundedInt(1) == 0, "Valid lower boundary changed");
                result[0] = 2;
                check(random.nextBoundedInt(3) == 2, "Valid upper boundary changed");
                check(draws.get() == 2, "Valid calls must draw exactly once");
                for (int invalid : new int[]{-1, 3, Integer.MAX_VALUE}) {
                    result[0] = invalid;
                    int before = draws.get();
                    rejects(() -> random.nextBoundedInt(3), "Random source returned an out-of-range value");
                    check(draws.get() == before + 1, "Adapter retried or skipped an invalid draw");
                    rejects(f::scan, "Random source returned an out-of-range value");
                    check(draws.get() == before + 2, "Production goal retried or skipped an invalid draw");
                    check(f.source.getTarget() == null, "Invalid draw acquired a target");
                }
            } finally {
                randomField.set(helper.getLevel(), original);
            }
        }
        helper.succeed();
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(message.equals(expected.getMessage()), "Wrong contract rejected the call: " + expected);
            return;
        }
        throw new AssertionError("Expected contract rejection: " + message);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_shadow_adds_one_query_per_scan_and_none_during_cooldown")
    public static void shadowAddsOneQueryPerScanAndNoneDuringCooldown(GameTestHelper helper) {
        TargetShadowComparison.resetCounters();
        TargetShadowComparison.setEnabled(true);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Fixture f = new Fixture(helper, entities, false, 3, false);
            ShipRangeTargetGoal goal = new ShipRangeTargetGoal((IShipAttackBase) f.source);
            for (int i = 0; i < 3; i++) {
                f.source.tickCount += 8;
                check(goal.canUse(), "Eligible scan unexpectedly failed");
                check(!goal.canUse(), "Same-tick cooldown did not hold");
            }
            check(TargetShadowComparison.metrics().equals(
                            new TargetShadowComparison.Metrics(3, 3, 9, 3, 9, 0, 0, 0)),
                    "Expected three scans, exactly three added queries, nine raw candidates, three selections");
        } finally {
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_non_living_only_scan_records_missing_draw_without_consuming_random")
    public static void nonLivingOnlyScanRecordsMissingDrawWithoutConsumingRandom(GameTestHelper helper) {
        TargetShadowComparison.resetCounters();
        TargetShadowComparison.setEnabled(true);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Fixture f = new Fixture(helper, entities, false, 0, false);
            BasicEntityShip source = (BasicEntityShip) f.source;
            source.setStateFlag(ID.F.AntiAir, true);
            for (int i = 0; i < 3; i++) {
                EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
                check(missile != null, "Missile creation failed");
                // -1 is ownerless, not hostile: AA eligibility requires a banned relation.
                missile.setPlayerUID(-2);
                check(TargetHelper.checkIsBanned(source, missile), "Missile fixture must be hostile to the source");
                missile.moveTo(source.getX() + i + 2D, source.getY(), source.getZ());
                check(helper.getLevel().addFreshEntity(missile), "Missile spawn failed");
            }
            RandomSource expected = RandomSource.create(74L);
            helper.getLevel().random.setSeed(74L);
            check(!f.scan(), "Legacy must not acquire a non-Living target");
            check(f.source.getTarget() == null, "Shadow moved target authority");
            check(helper.getLevel().random.nextLong() == expected.nextLong(), "Missing draw consumed world RNG");
            check(TargetShadowComparison.count(Outcome.DRAW_NOT_TAKEN) == 1L, "Missing draw not counted");
            check(TargetShadowComparison.count(Outcome.DIFF_NON_LIVING) == 1L, "non-Living difference not classified");
            check(TargetShadowComparison.metrics().equals(
                            new TargetShadowComparison.Metrics(1, 1, 3, 1, 3, 3, 0, 0)),
                    "No-target legacy scan must still add exactly one query and process three candidates");
        } finally {
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_shadow_classifies_equal_distance_handle_difference")
    public static void shadowClassifiesEqualDistanceHandleDifference(GameTestHelper helper) {
        Vec3 position = Fixture.sourcePosition(helper);
        // addFreshEntity can succeed while a neighbouring chunk's entity section is still HIDDEN.
        // Wait before creating the synchronous fixture or changing the shared shadow state.
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(position)),
                    "Waiting for the source chunk to tick entities");
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(position.add(2D, 0D, 0D))),
                    "Waiting for the equal-distance targets' chunk to tick entities");
        }).thenExecute(() -> verifyEqualDistanceHandleDifference(helper)).thenSucceed();
    }

    private static void verifyEqualDistanceHandleDifference(GameTestHelper helper) {
        TargetShadowComparison.resetCounters();
        TargetShadowComparison.setEnabled(true);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Fixture f = new Fixture(helper, entities, false, 2, true);
            check(f.scan(), "Legacy tie scan failed");
            check(f.source.getTarget() == f.targets.get(0), "Legacy stable tie order changed");
            check(TargetShadowComparison.count(Outcome.DIFF_TIE_BREAK) == 1L, "Handle tie difference not classified");
            check(TargetShadowComparison.count(Outcome.DRAW_NOT_TAKEN) == 0L, "Two candidates must not draw");
        } finally {
            reset();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + "; metrics=" + TargetShadowComparison.metrics());
        }
    }

    private static void injectFailure(Runnable failure) {
        try {
            Field field = TargetShadowComparison.class.getDeclaredField("testFailure");
            field.setAccessible(true);
            field.set(null, failure);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inject shadow failure", e);
        }
    }

    private static void reset() {
        TargetShadowComparison.setEnabled(false);
        injectFailure(null);
    }

    /** Synchronous tests finish and recover entities before yielding to any other test. */
    private static final class Fixture {
        private final Mob source;
        private final List<Mob> targets = new ArrayList<>();

        private Fixture(GameTestHelper helper, GameTestEntities entities, boolean hostile, int count, boolean ties) {
            this.source = entities.add(hostile ? ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel())
                    : ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            check(this.source != null, "Source creation failed");
            Vec3 position = sourcePosition(helper);
            this.source.setNoAi(true);
            this.source.moveTo(position.x, position.y, position.z);
            this.source.tickCount = 100;
            if (this.source instanceof BasicEntityShip ship) {
                ship.setPlayerUID(12345);
                ship.setEntitySit(false);
                ship.setStateFlag(ID.F.OnSightChase, false);
                ship.setStateFlag(ID.F.AntiAir, false);
                ship.setStateFlag(ID.F.AntiSS, false);
                ship.setStateFlag(ID.F.PVPFirst, false);
            }
            check(helper.getLevel().addFreshEntity(this.source), "Source spawn failed");
            for (int i = 0; i < count; i++) {
                Mob target = entities.add(hostile ? ModEntities.BB_KONGOU.get().create(helper.getLevel())
                        : ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                check(target != null, "Target creation failed");
                target.setNoAi(true);
                if (ties) {
                    target.setUUID(new UUID(74L, count - i));
                }
                target.moveTo(position.x + (ties ? 2D : i + 2D), position.y, position.z);
                check(helper.getLevel().addFreshEntity(target), "Target spawn failed");
                this.targets.add(target);
            }
        }

        private static Vec3 sourcePosition(GameTestHelper helper) {
            return helper.absoluteVec(new Vec3(0.5D, 20D, 0.5D));
        }

        private boolean scan() {
            ShipRangeTargetGoal goal = new ShipRangeTargetGoal((IShipAttackBase) this.source);
            boolean selected = goal.canUse();
            if (selected) {
                goal.start();
            }
            return selected;
        }
    }
}
