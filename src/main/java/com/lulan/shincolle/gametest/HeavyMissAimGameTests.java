package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerRo;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.MissileData;
import com.lulan.shincolle.utility.CombatHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A heavy shot that misses moves its aim point, and the missile is then started from that point. These
 * tests fire the real shot methods and compare the missile they launch with one started by hand from
 * the inputs the earlier code gave: the height is a float and is added to in float arithmetic, the
 * horizontal position is a double. The trajectory of a wide target must not change, and a rolled miss
 * must use the three random values in the order side, distance to the side, position along the line of
 * fire.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyMissAimGameTests {
    /** A miss roll that always counts as a miss. */
    private static final float MISS = 0F;
    /** Rolls of three different values, so that any other order gives another aim point. */
    private static final float SIDE = 0.9F;
    private static final float LATERAL = 0.3F;
    private static final float ALONG = 0.8F;

    private HeavyMissAimGameTests() {
    }

    /**
     * A destroyer 4.568326 blocks from a standing ravager (width 1.95, height 2.2), both at height 64, and
     * a miss rolled at 0.5, 0, 0.5: the earlier point is the target itself. The missile goes straight
     * (move type 0) with the earlier float height; a double sum of the height tipped it into a parabola.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_heavy_miss_wide_trajectory")
    public static void heavyMissOfAWideTargetKeepsItsEarlierTrajectory(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verifyWideFriendly(helper),
                new Vec3(0D, 4D, 0D), new Vec3(4.568326D, 4D, 0D));
    }

    private static void verifyWideFriendly(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Vec3 shooterAt = absolute(helper, 4.568326D, 64D, 0D);
            Vec3 targetAt = absolute(helper, 0D, 64D, 0D);
            EntityDestroyerRo ship = friendly(helper, entities, shooterAt);
            Entity ravager = ravager(helper, entities, targetAt);
            ScriptedRandom random = scriptRandom(helper, ship, MISS, 0.5F, 0F, 0.5F);
            MissileData md = ship.getMissileData(2);
            check(helper, md.movetype < 0, "Fixture must use the automatic move type: " + md.movetype);
            check(helper, ship.getShipDepth(0) == 0D, "Fixture must stand on dry ground: " + ship.getShipDepth(0));
            check(helper, ship.getBbHeight() == 1.5F, "Fixture ship height: " + ship.getBbHeight());

            EntityAbyssMissile real = fire(helper, entities, ship, () -> ship.attackEntityWithHeavyAmmo(ravager));
            check(helper, random.drawn.equals(List.of(MISS, 0.5F, 0F, 0.5F)),
                    "The floats drawn in the shot were " + random.drawn);

            // the inputs the earlier code gave: floats throughout, the earlier point from three float sums
            float x = (float) ravager.getX();
            float y = (float) ravager.getY();
            float z = (float) ravager.getZ();
            float aimX = x - 5F + 0.5F * 10F;
            float aimY = y + 0F * 5F;
            float aimZ = z - 5F + 0.5F * 10F;
            EntityAbyssMissile earlier = startedAsBefore(helper, ship, md, aimX, aimY, aimZ, ravager.getBbHeight());

            check(helper, real.moveType == 0, "A straight shot became move type " + real.moveType
                    + " velocity " + real.velX + "," + real.velY + "," + real.velZ);
            check(helper, Math.abs(real.velY + 0.0576215D) < 1.0E-4D, "Vertical start velocity " + real.velY);
            sameTrajectory(helper, "wide target, friendly", real, earlier);
            helper.succeed();
        }
    }

    /**
     * A hostile ship shooting a ravager standing at 64.000004: the height the missile starts toward is the
     * float sum of the target's float height and a tenth of its height. A double sum differs by several
     * millionths of a block there.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_heavy_miss_wide_hostile_height")
    public static void hostileHeavyMissOfAWideTargetKeepsItsEarlierHeight(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verifyWideHostile(helper),
                new Vec3(0D, 4D, 0D), new Vec3(6.5D, 4D, 0D));
    }

    private static void verifyWideHostile(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Vec3 shooterAt = absolute(helper, 6.5D, 64D, 0D);
            Vec3 targetAt = absolute(helper, 0D, 64.000004D, 0D);
            BasicEntityShipHostile ship = hostile(helper, entities, shooterAt);
            Entity ravager = ravager(helper, entities, targetAt);
            ScriptedRandom random = scriptRandom(helper, ship, MISS, 0.5F, 0F, 0.5F);
            MissileData md = ship.getMissileData(2);
            check(helper, ravager.getY() == 64.000004D, "Fixture target height: " + ravager.getY());

            EntityAbyssMissile real = fire(helper, entities, ship, () -> ship.attackEntityWithHeavyAmmo(ravager));
            check(helper, random.drawn.equals(List.of(MISS, 0.5F, 0F, 0.5F)),
                    "The floats drawn in the shot were " + random.drawn);

            float x = (float) ravager.getX();
            float y = (float) ravager.getY() + ravager.getBbHeight() * 0.1F;
            float z = (float) ravager.getZ();
            float aimX = x - 5F + 0.5F * 10F;
            float aimY = y + 0F * 5F;
            float aimZ = z - 5F + 0.5F * 10F;
            EntityAbyssMissile earlier = startedHostileAsBefore(helper, ship, md, ravager, aimX, aimY, aimZ);
            sameTrajectory(helper, "wide target, hostile", real, earlier);
            helper.succeed();
        }
    }

    /**
     * The three random values of a miss are drawn right after the miss roll, as side, distance to the side
     * and position along the line of fire, in every place that fires a heavy shot: a ship at an entity, a
     * ship at a block, and a hostile ship at an entity. Nothing else draws a float from the ship in the
     * shot, so the draws are exactly the miss roll and the three.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_heavy_miss_roll_order")
    public static void heavyMissDrawsItsThreeRollsInOrderAtEveryCallSite(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verifyRollOrder(helper),
                new Vec3(0.5D, 4D, 0.5D), new Vec3(10.5D, 4D, 0.5D));
    }

    private static void verifyRollOrder(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Vec3 shooterAt = helper.absoluteVec(new Vec3(10.5D, 4D, 0.5D));
            Vec3 targetAt = helper.absoluteVec(new Vec3(0.5D, 4D, 0.5D));
            Mob cow = entity(helper, entities, EntityType.COW, targetAt);
            check(helper, cow.getBbWidth() <= 0.98F, "Fixture target must be narrow: " + cow.getBbWidth());

            // a ship at an entity
            EntityDestroyerRo ship = friendly(helper, entities, shooterAt);
            ScriptedRandom random = scriptRandom(helper, ship, MISS, SIDE, LATERAL, ALONG);
            MissileData md = ship.getMissileData(2);
            EntityAbyssMissile real = fire(helper, entities, ship, () -> ship.attackEntityWithHeavyAmmo(cow));
            checkDraws(helper, "ship at an entity", random);
            double[] aim = CombatHelper.calcMissAimPoint(cow.getX(), (float) cow.getY(), cow.getZ(), ship.getX(),
                    ship.getZ(), cow.getBbWidth(), SIDE, LATERAL, ALONG);
            sameTrajectory(helper, "ship at an entity", real,
                    startedAsBefore(helper, ship, md, aim[0], (float) aim[1], aim[2], cow.getBbHeight()));
            otherOrdersDiffer(helper, "ship at an entity", real, order -> {
                double[] other = CombatHelper.calcMissAimPoint(cow.getX(), (float) cow.getY(), cow.getZ(),
                        ship.getX(), ship.getZ(), cow.getBbWidth(), order[0], order[1], order[2]);
                return startedAsBefore(helper, ship, md, other[0], (float) other[1], other[2], cow.getBbHeight());
            });

            // a ship at a block: the target is a corner of the block, not an entity
            EntityDestroyerRo blockShip = friendly(helper, entities, shooterAt);
            ScriptedRandom blockRandom = scriptRandom(helper, blockShip, MISS, SIDE, LATERAL, ALONG);
            BlockPos block = BlockPos.containing(targetAt);
            MissileData blockMd = blockShip.getMissileData(2);
            EntityAbyssMissile realBlock = fire(helper, entities, blockShip,
                    () -> blockShip.attackEntityWithHeavyAmmo(block));
            checkDraws(helper, "ship at a block", blockRandom);
            double[] blockAim = CombatHelper.calcMissAimPoint(block.getX(), (float) block.getY(), block.getZ(),
                    blockShip.getX(), blockShip.getZ(), 0D, SIDE, LATERAL, ALONG);
            sameTrajectory(helper, "ship at a block", realBlock,
                    startedAsBefore(helper, blockShip, blockMd, blockAim[0], (float) blockAim[1], blockAim[2], 1F));
            otherOrdersDiffer(helper, "ship at a block", realBlock, order -> {
                double[] other = CombatHelper.calcMissAimPoint(block.getX(), (float) block.getY(), block.getZ(),
                        blockShip.getX(), blockShip.getZ(), 0D, order[0], order[1], order[2]);
                return startedAsBefore(helper, blockShip, blockMd, other[0], (float) other[1], other[2], 1F);
            });

            // a hostile ship at an entity
            BasicEntityShipHostile enemy = hostile(helper, entities, shooterAt);
            ScriptedRandom enemyRandom = scriptRandom(helper, enemy, MISS, SIDE, LATERAL, ALONG);
            MissileData enemyMd = enemy.getMissileData(2);
            float enemyY = (float) cow.getY() + cow.getBbHeight() * 0.1F;
            EntityAbyssMissile realEnemy = fire(helper, entities, enemy, () -> enemy.attackEntityWithHeavyAmmo(cow));
            checkDraws(helper, "hostile ship at an entity", enemyRandom);
            double[] enemyAim = CombatHelper.calcMissAimPoint(cow.getX(), enemyY, cow.getZ(), enemy.getX(),
                    enemy.getZ(), cow.getBbWidth(), SIDE, LATERAL, ALONG);
            sameTrajectory(helper, "hostile ship at an entity", realEnemy, startedHostileAsBefore(helper, enemy,
                    enemyMd, cow, enemyAim[0], (float) enemyAim[1], enemyAim[2]));
            otherOrdersDiffer(helper, "hostile ship at an entity", realEnemy, order -> {
                double[] other = CombatHelper.calcMissAimPoint(cow.getX(), enemyY, cow.getZ(), enemy.getX(),
                        enemy.getZ(), cow.getBbWidth(), order[0], order[1], order[2]);
                return startedHostileAsBefore(helper, enemy, enemyMd, cow, other[0], (float) other[1], other[2]);
            });
            helper.succeed();
        }
    }

    private static void checkDraws(GameTestHelper helper, String site, ScriptedRandom random) {
        check(helper, random.drawn.equals(List.of(MISS, SIDE, LATERAL, ALONG)),
                site + ": the floats drawn in the shot were " + random.drawn);
    }

    /** Every other order of the three rolls would have started another missile. */
    private static void otherOrdersDiffer(GameTestHelper helper, String site, EntityAbyssMissile real,
                                          java.util.function.Function<float[], EntityAbyssMissile> start) {
        float[][] others = {
                {SIDE, ALONG, LATERAL}, {LATERAL, SIDE, ALONG}, {LATERAL, ALONG, SIDE},
                {ALONG, SIDE, LATERAL}, {ALONG, LATERAL, SIDE}};
        for (float[] order : others) {
            EntityAbyssMissile other = start.apply(order);
            boolean same = Double.compare(other.velX, real.velX) == 0 && Double.compare(other.velY, real.velY) == 0
                    && Double.compare(other.velZ, real.velZ) == 0;
            check(helper, !same, site + ": the order " + order[0] + "," + order[1] + "," + order[2]
                    + " gives the same missile, so the fixture cannot tell the order");
        }
    }

    // ---------- what the earlier code did ----------

    /**
     * The missile a friendly ship started before the aim point went to doubles: the height the missile
     * heads for is a float sum of the aim height and a tenth of the target's height.
     */
    private static EntityAbyssMissile startedAsBefore(GameTestHelper helper, BasicEntityShip ship, MissileData md,
                                                      double aimX, float aimY, double aimZ, float targetHeight) {
        int moveType = CombatHelper.calcMissileMoveType(ship, aimY, 2);
        float launch = (float) ship.getY() + ship.getBbHeight() * 0.5F;
        if (moveType == 0) {
            launch = (float) ship.getY() + ship.getBbHeight() * 0.3F;
        }
        EntityAbyssMissile missile = new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel());
        missile.initMissile(ship, md.type, moveType, 1F, 0.15F, launch, aimX, aimY + targetHeight * 0.1F, aimZ,
                140, 0.25F, md.vel0, md.accY1, md.accY2);
        return missile;
    }

    /** The same for a hostile ship, whose aim height already holds the tenth of the target's height. */
    private static EntityAbyssMissile startedHostileAsBefore(GameTestHelper helper, BasicEntityShipHostile ship,
                                                             MissileData md, Entity target, double aimX,
                                                             float aimY, double aimZ) {
        float launch = (float) ship.getY() + ship.getBbHeight() * 0.5F;
        int moveType = CombatHelper.calcMissileMoveType(ship, target.getY(), 2);
        if (moveType == 0) {
            launch = (float) ship.getY() + ship.getBbHeight() * 0.3F;
        }
        EntityAbyssMissile missile = new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel());
        missile.initMissile(ship, md.type, moveType, 1F, 0.15F, launch, aimX, aimY, aimZ,
                160, 0.25F, md.vel0, md.accY1, md.accY2);
        return missile;
    }

    private static void sameTrajectory(GameTestHelper helper, String what, EntityAbyssMissile real,
                                       EntityAbyssMissile earlier) {
        String values = what + ": real type " + real.moveType + " vel " + real.velX + "," + real.velY + ","
                + real.velZ + " acc " + real.accY1 + "," + real.accY2 + " t " + real.t0 + "," + real.t1
                + " / earlier type " + earlier.moveType + " vel " + earlier.velX + "," + earlier.velY + ","
                + earlier.velZ + " acc " + earlier.accY1 + "," + earlier.accY2 + " t " + earlier.t0 + ","
                + earlier.t1;
        check(helper, real.moveType == earlier.moveType, "move type differs - " + values);
        check(helper, same(real.velX, earlier.velX) && same(real.velY, earlier.velY) && same(real.velZ, earlier.velZ),
                "start velocity differs - " + values);
        check(helper, same(real.accY1, earlier.accY1) && same(real.accY2, earlier.accY2),
                "acceleration differs - " + values);
        check(helper, same(real.t0, earlier.t0) && same(real.t1, earlier.t1), "phase times differ - " + values);
    }

    private static boolean same(double a, double b) {
        return Double.compare(a, b) == 0;
    }

    // ---------- fixture ----------

    /** The one missile the shot added next to the shooter, taken out of the world again. */
    private static EntityAbyssMissile fire(GameTestHelper helper, GameTestEntities entities, Entity shooter,
                                           Runnable shot) {
        AABB around = shooter.getBoundingBox().inflate(4D);
        Set<UUID> before = helper.getLevel().getEntitiesOfClass(EntityAbyssMissile.class, around).stream()
                .map(Entity::getUUID).collect(Collectors.toSet());
        shot.run();
        List<EntityAbyssMissile> added = new ArrayList<>(
                helper.getLevel().getEntitiesOfClass(EntityAbyssMissile.class, around));
        added.removeIf(missile -> before.contains(missile.getUUID()));
        check(helper, added.size() == 1, "The shot must launch exactly one missile: " + added.size());
        return entities.add(added.get(0));
    }

    private static EntityDestroyerRo friendly(GameTestHelper helper, GameTestEntities entities, Vec3 at) {
        EntityDestroyerRo ship = entities.add(new QuietDestroyer(ModEntities.DESTROYER_RO.get(), helper.getLevel()));
        ship.setNoAi(true);
        ship.setNoGravity(true);
        ship.setInvulnerable(true);
        ship.setAmmoHeavy(1_000);
        ship.setShipDepth(0D);
        place(helper, ship, at);
        return ship;
    }

    private static BasicEntityShipHostile hostile(GameTestHelper helper, GameTestEntities entities, Vec3 at) {
        BasicEntityShipHostile ship = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        check(helper, ship != null, "Could not create the hostile ship");
        ship.setNoAi(true);
        ship.setNoGravity(true);
        ship.setInvulnerable(true);
        place(helper, ship, at);
        return ship;
    }

    private static Entity ravager(GameTestHelper helper, GameTestEntities entities, Vec3 at) {
        Mob ravager = entity(helper, entities, EntityType.RAVAGER, at);
        check(helper, ravager.getBbWidth() == 1.95F && ravager.getBbHeight() == 2.2F,
                "Fixture ravager size: " + ravager.getBbWidth() + " x " + ravager.getBbHeight());
        return ravager;
    }

    private static Mob entity(GameTestHelper helper, GameTestEntities entities, EntityType<? extends Mob> type,
                              Vec3 at) {
        Mob mob = entities.add(type.create(helper.getLevel()));
        check(helper, mob != null, "Could not create " + type);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setInvulnerable(true);
        place(helper, mob, at);
        return mob;
    }

    private static void place(GameTestHelper helper, Entity entity, Vec3 at) {
        entity.moveTo(at.x, at.y, at.z);
        check(helper, helper.getLevel().addFreshEntity(entity), "Could not add " + entity.getType());
        check(helper, entity.getY() == at.y && entity.getX() == at.x && entity.getZ() == at.z,
                "Fixture entity is not at " + at + ": " + entity.position());
        GameTestEntities.assertRegistered(helper, entity);
    }

    /** The test's horizontal position, at the given absolute height. */
    private static Vec3 absolute(GameTestHelper helper, double x, double y, double z) {
        Vec3 base = helper.absoluteVec(new Vec3(x, 0D, z));
        return new Vec3(base.x, y, base.z);
    }

    /** Gives the entity a random source that returns the given floats first, and records every float drawn. */
    private static ScriptedRandom scriptRandom(GameTestHelper helper, Entity entity, float... script) {
        ScriptedRandom random = new ScriptedRandom(script);
        try {
            Field field = Entity.class.getDeclaredField("random");
            field.setAccessible(true);
            field.set(entity, random);
            check(helper, field.get(entity) == random, "The scripted random source was not installed");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot give the entity a scripted random source", failure);
        }
        return random;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    /**
     * A friendly ship draws two floats for the pitch of the shot's sound before it rolls the miss. The
     * fixture takes that out, so the floats the ship draws in a shot are the miss roll and the three
     * aim rolls and nothing else.
     */
    private static final class QuietDestroyer extends EntityDestroyerRo {
        QuietDestroyer(EntityType<EntityDestroyerRo> type, Level level) {
            super(type, level);
        }

        @Override
        public float getVoicePitch() {
            return 1F;
        }
    }

    private static final class ScriptedRandom extends LegacyRandomSource {
        private final float[] script;
        final List<Float> drawn = new ArrayList<>();

        ScriptedRandom(float[] script) {
            super(42L);
            this.script = script;
        }

        @Override
        public float nextFloat() {
            float value = this.drawn.size() < this.script.length ? this.script[this.drawn.size()] : super.nextFloat();
            this.drawn.add(value);
            return value;
        }
    }
}
