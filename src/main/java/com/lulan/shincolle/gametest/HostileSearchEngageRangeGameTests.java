package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipHostileWanderGoal;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerInazuma;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerInazumaMob;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostileSearchEngageRangeGameTests {
    private HostileSearchEngageRangeGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 260,
            batch = "isolated_hostile_outside_friendly_range")
    public static void hostileDoesNotFireOutsideFriendlyRange(GameTestHelper helper) {
        encounter(helper, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 340,
            batch = "isolated_hostile_approach_return_fire")
    public static void hostileApproachesIntoRangeAndBothShipsFire(GameTestHelper helper) {
        encounter(helper, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 260,
            batch = "isolated_hostile_extended_search")
    public static void hostileAcquiresHoldsAndApproachesAtExtendedRange(GameTestHelper helper) {
        encounter(helper, true, true);
    }

    private static void encounter(GameTestHelper helper, boolean moving, boolean extended) {
        Vec3 relative = new Vec3(0D, 5D, 2560D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            Vec3 origin = helper.absoluteVec(relative);
            if (moving) {
                for (int x = -6; x <= 34; x++) {
                    for (int z = -5; z <= 5; z++) {
                        helper.getLevel().setBlock(BlockPos.containing(origin.add(x, -1D, z)),
                                Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
            CountingHostile hostile = entities.add(new CountingHostile(helper.getLevel(), !moving));
            hostile.initAttrs(3);
            hostile.setInvulnerable(true);
            hostile.setNoGravity(!moving);
            hostile.moveTo(origin);
            helper.assertTrue(helper.getLevel().addFreshEntity(hostile), "Hostile registration failed");
            GameTestEntities.removeGoalsEachTick(helper, hostile, goal -> goal instanceof ShipHostileWanderGoal);
            CountingFriendly friendly = entities.add(new CountingFriendly(helper.getLevel()));
            friendly.setPlayerUID(9502);
            friendly.setStateMinor(ID.M.NumGrudge, 100_000);
            friendly.setStateMinor(ID.M.NumAmmoLight, 100_000);
            friendly.setStateMinor(ID.M.NumAmmoHeavy, 100_000);
            friendly.setStateFlag(ID.F.NoFuel, false);
            friendly.setStateFlag(ID.F.UseAmmoLight, true);
            friendly.setStateFlag(ID.F.UseMelee, false);
            friendly.setShipLevel(1, false);
            friendly.setMorale(3000);
            friendly.calcShipAttributes(31, false);
            friendly.setInvulnerable(true);
            friendly.setHealth(friendly.getMaxHealth());
            friendly.setNoGravity(true);
            friendly.moveTo(origin.add(40D, 0D, 0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(friendly), "Friendly registration failed");
            GameTestEntities.removeGoalsEachTick(helper, friendly, goal -> goal instanceof ShipWanderGoal);
            int[] elapsed = {0};
            int[] held = {0};
            int[] drops = {0};
            boolean[] acquired = {false};
            double[] firstShotDistance = {-1D};
            helper.onEachTick(() -> {
                int tick = elapsed[0]++;
                if (tick < 32) return;
                if (tick == 32) {
                    hostile.moveTo(origin);
                    hostile.getNavigation().stop();
                    friendly.moveTo(origin.add(extended ? 24D : 14D, 0D, 0D));
                    helper.assertTrue(hostile.tickCount >= 24 && friendly.tickCount >= 24,
                            "Ships must finish goal registration");
                    helper.assertTrue(friendly.getStateMinor(ID.M.NumGrudge) > 0
                                    && !friendly.getStateFlag(ID.F.NoFuel) && friendly.hasAmmoLight(),
                            "Friendly needs fuel and ammunition");
                }
                helper.assertTrue(Math.abs(hostile.getAttrs().getAttackRange() - 15.4F) < 0.001F,
                        "Hostile scale-three real range changed");
                helper.assertTrue(helper.getLevel().isPositionEntityTicking(hostile.blockPosition())
                                && helper.getLevel().isPositionEntityTicking(friendly.blockPosition()),
                        "Both encounter positions must remain entity-ticking throughout approach");
                helper.assertTrue(friendly.getAttrs().getAttackRange() >= 9F
                                && friendly.getAttrs().getAttackRange() < 14F,
                        "Friendly must have its normal shorter range: " + friendly.getAttrs().getAttackRange());
                if (tick <= 48 && extended) {
                    helper.assertTrue(friendly.currentTargetLock().isEmpty(),
                            "Friendly automatic search must not acquire beyond its unchanged range");
                }
                if (hostile.getEntityTarget() == friendly) {
                    helper.assertTrue(hostile.currentTargetLock()
                                    .filter(lock -> lock.source() == TargetSource.AUTO).isPresent(),
                            "Hostile must acquire automatically");
                    acquired[0] = true;
                    held[0]++;
                } else if (acquired[0]) drops[0]++;
                if (hostile.shots > 0 && firstShotDistance[0] < 0D) firstShotDistance[0] = hostile.shotDistance;
                helper.assertTrue(hostile.outsideShots == 0,
                        "Hostile fired outside friendly live range: distance=" + hostile.shotDistance
                                + " friendlyRange=" + friendly.getAttrs().getAttackRange());
                if (tick == (extended ? 100 : moving ? 230 : 140)) {
                    LogUtils.getLogger().info(
                            "ENGAGE_RANGE moving={} extended={} held={} drops={} hostileShots={} friendlyShots={} distance={} firstShot={}",
                            moving, extended, held[0], drops[0], hostile.shots, friendly.shots,
                            hostile.distanceTo(friendly), firstShotDistance[0]);
                    helper.assertTrue(held[0] > 20 && drops[0] == 0, "Expected stable extended automatic lock");
                    if (moving) {
                        helper.assertTrue(hostile.getX() > origin.x + 2D, "Hostile must approach using navigation");
                        if (!extended) {
                            helper.assertTrue(hostile.shots > 0 && friendly.shots > 0,
                                    "Both ships must fire after approach: hostile=" + hostile.shots
                                            + " friendly=" + friendly.shots);
                            helper.assertTrue(hostile.distanceTo(friendly) <= friendly.getAttrs().getAttackRange(),
                                    "Combat mover must stop inside friendly range");
                        }
                    } else {
                        helper.assertTrue(hostile.shots == 0, "Stationary hostile must withhold fire");
                    }
                    helper.succeed();
                }
            });
        }, relative, relative.add(16D, 0D, 0D), relative.add(32D, 0D, 0D), relative.add(40D, 0D, 0D));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200,
            batch = "isolated_hostile_search_cost")
    public static void thirtyTwoHostileAutomaticSearchCost(GameTestHelper helper) {
        Vec3 relative = new Vec3(0D, 8D, 3072D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CountingHostile[] ships = new CountingHostile[32];
                ShipTargetAuthorityGoal[] goals = new ShipTargetAuthorityGoal[32];
                Vec3 origin = helper.absoluteVec(relative);
                for (int i = 0; i < ships.length; i++) {
                    ships[i] = entities.add(new CountingHostile(helper.getLevel(), true));
                    ships[i].initAttrs(3);
                    ships[i].setNoAi(true);
                    ships[i].moveTo(origin.add((i % 8) * 0.2D, 0D, (i / 8) * 0.2D));
                    helper.assertTrue(helper.getLevel().addFreshEntity(ships[i]), "Benchmark ship registration failed");
                    goals[i] = new ShipTargetAuthorityGoal(ships[i]);
                }
                for (int i = 0; i < 64; i++) {
                    Entity cow = entities.add(EntityType.COW.create(helper.getLevel()));
                    double angle = i * Math.PI / 32D;
                    cow.moveTo(origin.add(22D * Math.cos(angle), 0D, 22D * Math.sin(angle)));
                    cow.setNoGravity(true);
                    helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Benchmark background registration failed");
                }
                double[] millis = new double[7];
                for (int round = -2; round < millis.length; round++) {
                    long start = System.nanoTime();
                    for (int tick = 0; tick < 256; tick++) {
                        for (int i = 0; i < ships.length; i++) {
                            ships[i].tickCount = 32 + (round + 2) * 256 + tick;
                            goals[i].tick();
                        }
                    }
                    double time = (System.nanoTime() - start) / 256_000_000D;
                    if (round >= 0) millis[round] = time;
                }
                Arrays.sort(millis);
                LogUtils.getLogger().info("SEARCH_COST ships=32 background=64 msPerAuthorityTick={} median={}",
                        Arrays.toString(millis), millis[3]);
                for (ShipTargetAuthorityGoal goal : goals) {
                    helper.assertTrue(goal.currentLock().isEmpty(), "Benchmark must exercise repeated no-target scans");
                }
                helper.succeed();
            }
        }, relative, relative.add(26D, 0D, 26D), relative.add(-26D, 0D, -26D));
    }

    private static final class CountingFriendly extends EntityDestroyerInazuma {
        private int shots;

        private CountingFriendly(Level level) {
            super(ModEntities.DESTROYER_INAZUMA.get(), level);
        }

        @Override
        public void travel(Vec3 movement) { setDeltaMovement(Vec3.ZERO); }

        @Override
        public boolean attackEntityWithAmmo(Entity target) { this.shots++; return true; }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) { this.shots++; return true; }
    }

    private static final class CountingHostile extends EntityDestroyerInazumaMob {
        private final boolean stationary;
        private int shots;
        private int outsideShots;
        private double shotDistance;

        private CountingHostile(Level level, boolean stationary) {
            super(ModEntities.DESTROYER_INAZUMA_MOB.get(), level);
            this.stationary = stationary;
        }

        @Override
        public void travel(Vec3 movement) {
            if (this.stationary) setDeltaMovement(Vec3.ZERO);
            else super.travel(movement);
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.shots++;
            this.shotDistance = distanceTo(target);
            if (target instanceof CountingFriendly friendly
                    && distanceToSqr(target) > Math.pow(friendly.getAttrs().getAttackRange(), 2D)) this.outsideShots++;
            return true;
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) { return attackEntityWithAmmo(target); }
    }
}
