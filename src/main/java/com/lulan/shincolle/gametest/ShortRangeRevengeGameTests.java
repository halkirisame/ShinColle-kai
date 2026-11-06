package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipHostileWanderGoal;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShortRangeRevengeGameTests {
    private ShortRangeRevengeGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 620,
            batch = "isolated_short_range_real_damage_revenge")
    public static void shortRangeFriendlyReturnsFireAfterRealHostileDamage(GameTestHelper helper) {
        Vec3 relative = new Vec3(0D, 5D, 3584D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            Vec3 origin = helper.absoluteVec(relative);
            for (int x = -6; x <= 46; x++) {
                for (int z = -5; z <= 5; z++) {
                    helper.getLevel().setBlock(BlockPos.containing(origin.add(x, -1D, z)),
                            Blocks.STONE.defaultBlockState(), 3);
                }
            }
            ObservedHostile hostile = entities.add(new ObservedHostile(helper.getLevel()));
            hostile.initAttrs(3);
            hostile.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    "combat fixture health", 900D, AttributeModifier.Operation.ADDITION));
            hostile.setHealth(hostile.getMaxHealth());
            hostile.moveTo(origin);
            helper.assertTrue(helper.getLevel().addFreshEntity(hostile), "Hostile registration failed");
            GameTestEntities.removeGoalsEachTick(helper, hostile, goal -> goal instanceof ShipHostileWanderGoal);

            ObservedFriendly friendly = entities.add(new ObservedFriendly(helper.getLevel()));
            friendly.attacker = hostile;
            friendly.setPlayerUID(9503);
            friendly.setStateMinor(ID.M.NumGrudge, 100_000);
            friendly.setStateMinor(ID.M.NumAmmoLight, 100_000);
            friendly.setStateMinor(ID.M.NumAmmoHeavy, 100_000);
            friendly.setStateFlag(ID.F.NoFuel, false);
            friendly.setStateFlag(ID.F.PassiveAI, true);
            friendly.setStateFlag(ID.F.UseAmmoLight, true);
            friendly.setStateFlag(ID.F.UseAmmoHeavy, false);
            friendly.setStateFlag(ID.F.UseMelee, false);
            friendly.setShipLevel(1, false);
            friendly.setMorale(3000);
            friendly.calcShipAttributes(31, false);
            friendly.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    "combat fixture health", 900D, AttributeModifier.Operation.ADDITION));
            friendly.setHealth(friendly.getMaxHealth());
            friendly.setNoGravity(true);
            friendly.moveTo(origin.add(40D, 0D, 0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(friendly), "Friendly registration failed");
            GameTestEntities.removeGoalsEachTick(helper, friendly, goal -> goal instanceof ShipWanderGoal);

            int[] elapsed = {0};
            int[] firstRevengeTick = {-1};
            int[] held = {0};
            int[] drops = {0};
            helper.onEachTick(() -> {
                int tick = elapsed[0]++;
                helper.assertTrue(friendly.currentTargetLock()
                                .filter(lock -> lock.source() != TargetSource.REVENGE).isEmpty(),
                        "AUTO or MANUAL acquisition cannot prove damage-triggered revenge");
                if (tick < 32) return;
                if (tick == 32) {
                    helper.assertTrue(hostile.tickCount >= 24 && friendly.tickCount >= 24,
                            "Ships must finish goal registration before the encounter");
                    helper.assertTrue(friendly.currentTargetLock().isEmpty() && friendly.incomingHits == 0,
                            "Friendly must start without a target or prior damage");
                    hostile.moveTo(origin);
                    hostile.getNavigation().stop();
                    friendly.moveTo(origin.add(14D, 0D, 0D));
                    helper.assertTrue(friendly.getHealth() >= 900F && hostile.getHealth() >= 900F
                                    && friendly.getHealth() == friendly.getMaxHealth()
                                    && hostile.getHealth() == hostile.getMaxHealth(),
                            "Health modifiers must survive recalculation and both ships must start at full HP");
                    helper.assertTrue(friendly.getMorale() == 3000, "Friendly morale fixture was ignored");
                    LogUtils.getLogger().info("REAL_REVENGE start friendlyRange={} hostileRange={} distance={} friendlyHP={} hostileHP={}",
                            friendly.getAttrs().getAttackRange(), hostile.getAttrs().getAttackRange(),
                            friendly.distanceTo(hostile), friendly.getHealth(), hostile.getHealth());
                }
                if (friendly.getHealth() < 500F) friendly.setHealth(friendly.getMaxHealth());
                helper.assertTrue(friendly.getStateFlag(ID.F.PassiveAI)
                                && friendly.getStateMinor(ID.M.NumGrudge) > 0
                                && !friendly.getStateFlag(ID.F.NoFuel) && friendly.hasAmmoLight()
                                && friendly.getStateFlag(ID.F.UseAmmoLight)
                                && !friendly.getStateFlag(ID.F.UseAmmoHeavy)
                                && !friendly.getStateFlag(ID.F.UseMelee),
                        "Passive friendly needs fuel and light ammunition without melee or heavy fire");
                helper.assertTrue(!friendly.isInvulnerable() && !hostile.isInvulnerable()
                                && friendly.isAlive() && hostile.isAlive()
                                && friendly.getMaxHealth() >= 900F && hostile.getMaxHealth() >= 900F,
                        "Both ships must remain damageable and alive with the asserted HP reserve");
                helper.assertTrue(friendly.getAttrs().getAttackRange() >= 9F
                                && friendly.getAttrs().getAttackRange() < hostile.getAttrs().getAttackRange()
                                && Math.abs(hostile.getAttrs().getAttackRange() - 15.4F) < 0.001F,
                        "Unequipped friendly must retain its normal shorter live range");
                helper.assertTrue(helper.getLevel().isPositionEntityTicking(hostile.blockPosition())
                                && helper.getLevel().isPositionEntityTicking(friendly.blockPosition()),
                        "Both positions must remain entity-ticking throughout the encounter");
                boolean revenge = friendly.currentTargetLock().isPresent() && friendly.getEntityTarget() == hostile;
                if (revenge) {
                    helper.assertTrue(friendly.incomingAttacks > 0 && hostile.lightShots + hostile.heavyShots > 0,
                            "REVENGE must follow acceptance of an actual hostile cannon attack");
                    if (firstRevengeTick[0] < 0) {
                        firstRevengeTick[0] = tick;
                        LogUtils.getLogger().info("REAL_REVENGE acquired tick={} source=REVENGE friendlyTick={} firstHitTick={} HP={}",
                                tick, friendly.tickCount, friendly.firstHitTick, friendly.getHealth());
                    }
                    held[0]++;
                } else if (firstRevengeTick[0] >= 0) {
                    drops[0]++;
                }
                helper.assertTrue(drops[0] == 0, "Friendly lost the damage-triggered revenge target");
                helper.assertTrue(friendly.invalidShots == 0 && hostile.outsideShots == 0,
                        "Every return shot needs an accepted hostile cannon attack, a REVENGE lock and distance within live range");
                if (tick == 460) {
                    LogUtils.getLogger().info("REAL_REVENGE end held={} drops={} incomingHits={} hostileLight={} hostileHeavy={}"
                                    + " hostileLightHits={} friendlyShots={} friendlyHP={} hostileHP={} distance={}"
                                    + " firstReturnDistance={} firstReturnRange={}",
                            held[0], drops[0], friendly.incomingHits, hostile.lightShots, hostile.heavyShots,
                            hostile.lightHits, friendly.shots, friendly.getHealth(), hostile.getHealth(),
                            friendly.distanceTo(hostile), friendly.firstShotDistance, friendly.firstShotRange);
                    helper.assertTrue(hostile.getX() > origin.x + 2D, "Hostile must approach using real navigation");
                    helper.assertTrue(hostile.lightHits > 0 && friendly.incomingHits > 0,
                            "Real hostile light cannon must reduce friendly HP");
                    helper.assertTrue(firstRevengeTick[0] >= 0 && held[0] >= 20 && drops[0] == 0,
                            "Expected stable damage-triggered REVENGE retention");
                    helper.assertTrue(friendly.shots > 0 && friendly.firstShotDistance <= friendly.firstShotRange,
                            "Short-range friendly must execute real return fire within its live range");
                    helper.succeed();
                }
            });
        }, relative, relative.add(16D, 0D, 0D), relative.add(32D, 0D, 0D), relative.add(40D, 0D, 0D));
    }

    private static final class ObservedFriendly extends EntityDestroyerInazuma {
        private Entity attacker;
        private int incomingAttacks;
        private int incomingHits;
        private int firstHitTick = -1;
        private int shots;
        private int invalidShots;
        private double firstShotDistance = -1D;
        private float firstShotRange;

        private ObservedFriendly(Level level) {
            super(ModEntities.DESTROYER_INAZUMA.get(), level);
        }

        @Override
        public void travel(Vec3 movement) { setDeltaMovement(Vec3.ZERO); }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            float before = getHealth();
            boolean result = super.hurt(source, amount);
            if (source.getEntity() == this.attacker && result) {
                this.incomingAttacks++;
            }
            if (source.getEntity() == this.attacker && getHealth() < before) {
                this.incomingHits++;
                if (this.firstHitTick < 0) this.firstHitTick = this.tickCount;
                LogUtils.getLogger().info("REAL_REVENGE damage friendlyTick={} direct={} type={} HP={}->{} distance={}",
                        this.tickCount, source.getDirectEntity() == this.attacker ? "hostile" : "projectile",
                        source.getMsgId(), before, getHealth(), distanceTo(this.attacker));
            }
            return result;
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.shots++;
            double distance = distanceTo(target);
            float range = getAttrs().getAttackRange();
            TargetSource source = currentTargetLock().map(lock -> lock.source()).orElse(null);
            if (this.incomingAttacks == 0 || target != this.attacker || source != TargetSource.REVENGE
                    || distanceToSqr(target) > (double) range * range) this.invalidShots++;
            if (this.shots == 1) {
                this.firstShotDistance = distance;
                this.firstShotRange = range;
            }
            boolean result = super.attackEntityWithAmmo(target);
            LogUtils.getLogger().info("REAL_REVENGE return friendlyTick={} shot={} source={} distance={} liveRange={} result={}",
                    this.tickCount, this.shots, source, distance, range, result);
            return result;
        }
    }

    private static final class ObservedHostile extends EntityDestroyerInazumaMob {
        private int lightShots;
        private int heavyShots;
        private int lightHits;
        private int outsideShots;

        private ObservedHostile(Level level) {
            super(ModEntities.DESTROYER_INAZUMA_MOB.get(), level);
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.lightShots++;
            ObservedFriendly friendly = (ObservedFriendly) target;
            float before = friendly.getHealth();
            if (distanceToSqr(target) > Math.pow(friendly.getAttrs().getAttackRange(), 2D)) this.outsideShots++;
            boolean result = super.attackEntityWithAmmo(target);
            if (friendly.getHealth() < before) this.lightHits++;
            LogUtils.getLogger().info("REAL_REVENGE hostileLight hostileTick={} shot={} distance={} friendlyHP={}->{} result={}",
                    this.tickCount, this.lightShots, distanceTo(target), before, friendly.getHealth(), result);
            return result;
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) {
            this.heavyShots++;
            return super.attackEntityWithHeavyAmmo(target);
        }
    }
}
