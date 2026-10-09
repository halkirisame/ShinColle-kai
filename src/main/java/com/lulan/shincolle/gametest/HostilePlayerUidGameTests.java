package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ConfigHandler;

import com.lulan.shincolle.ai.ShipRangeTargetGoal;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.entity.battleship.EntityBBKirishimaMob;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityAirplaneTMob;
import com.lulan.shincolle.entity.other.EntityAirplaneZeroMob;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.entity.other.EntityRensouhouMob;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostilePlayerUidGameTests {
    private HostilePlayerUidGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_hostile_uid_inheritance")
    public static void hostileWeaponsAndSummonsHaveFixedUid(GameTestHelper helper) {
        waitForPositions(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                EntityBBKirishimaMob hostile = hostile(helper, entities, 1D);
                EntityAbyssMissile missile = missile(helper, entities, hostile, 4D);
                EntityProjectileBeam beam = entities.add(ModEntities.PROJECTILE_BEAM.get().create(helper.getLevel()));
                helper.assertTrue(beam != null, "Beam creation failed");
                beam.initBeam(hostile, 1D, 0D, 0D, 20F);
                EntityAirplaneTMob takoyaki = entities.add(ModEntities.AIRPLANE_T_MOB.get().create(helper.getLevel()));
                EntityAirplaneZeroMob zero = entities.add(ModEntities.AIRPLANE_ZERO_MOB.get().create(helper.getLevel()));
                EntityRensouhouMob turret = entities.add(ModEntities.RENSOUHOU_MOB.get().create(helper.getLevel()));
                helper.assertTrue(takoyaki != null && zero != null && turret != null,
                        "Hostile summon creation failed");
                helper.assertTrue(hostile.getPlayerUID() == -100 && missile.getPlayerUID() == -100
                                && beam.getPlayerUID() == -100, "Hostile weapon UID was not inherited");
                helper.assertTrue(takoyaki.getPlayerUID() == -100 && zero.getPlayerUID() == -100
                                && turret.getPlayerUID() == -100, "Hostless hostile summon UID changed");
                takoyaki.initAttrs(hostile, null, 1, (float) hostile.getY());
                zero.initAttrs(hostile, null, 1, (float) hostile.getY());
                turret.setHost(hostile);
                helper.assertTrue(takoyaki.getPlayerUID() == -100 && zero.getPlayerUID() == -100
                                && turret.getPlayerUID() == -100, "Hosted hostile summon UID changed");
                helper.succeed();
            }
        }, 1D, 4D);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_hostile_uid_new_owner_target")
    public static void newHostileIgnoresOwnMissileAndAircraft(GameTestHelper helper) {
        waitForPositions(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                AuthorityHostile hostile = entities.add(new AuthorityHostile(
                        ModEntities.BB_KIRISHIMA_MOB.get(), helper.getLevel()));
                hostile.setNoAi(true);
                add(helper, hostile, 1D);
                hostile.setAITargetList();
                missile(helper, entities, hostile, 4D);
                aircraft(helper, entities, hostile, 6D);
                ShipTargetAuthorityGoal goal = goal(hostile, BasicEntityShipHostile.class);
                tick(goal, hostile, 100);
                helper.assertTrue(hostile.getEntityTarget() == null,
                        "NEW hostile acquired its own missile or aircraft");

                BasicEntityShip playerShip = friendly(helper, entities, 20D, 9211);
                EntityAbyssMissile other = missile(helper, entities, playerShip, 8D);
                playerShip.discard();
                tick(goal, hostile, 110);
                helper.assertTrue(hostile.getEntityTarget() == other,
                        "NEW hostile did not acquire another owner's missile");
                helper.succeed();
            }
        }, 1D, 4D, 6D, 8D, 20D);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_hostile_uid_new_anti_air")
    public static void newFriendlyAntiAirRespectsHostileMissile(GameTestHelper helper) {
        waitForPositions(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                AuthorityFriendly friendly = authorityFriendly(helper, entities, 1D, 9212);
                EntityBBKirishimaMob hostile = hostile(helper, entities, 12D);
                EntityAbyssMissile missile = missile(helper, entities, hostile, 4D);
                hostile.discard();
                ShipTargetAuthorityGoal goal = goal(friendly, BasicEntityShip.class);
                tick(goal, friendly, 100);
                helper.assertTrue(friendly.getEntityTarget() == null, "AntiAir OFF acquired hostile missile");
                friendly.setStateFlag(ID.F.AntiAir, true);
                tick(goal, friendly, 110);
                helper.assertTrue(friendly.getEntityTarget() == missile,
                        "AntiAir ON did not acquire hostile-fired missile");
                helper.succeed();
            }
        }, 1D, 4D, 12D);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_hostile_uid_legacy_anti_air")
    public static void legacyFriendlyAntiAirTargetsHostileAircraft(GameTestHelper helper) {
        waitForPositions(helper, () -> {
            try (ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                    GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip friendly = friendly(helper, entities, 1D, 9213);
                friendly.setStateFlag(ID.F.AntiAir, true);
                EntityBBKirishimaMob hostile = hostile(helper, entities, 12D);
                EntityAirplaneTMob plane = aircraft(helper, entities, hostile, 4D);
                hostile.discard();
                ShipRangeTargetGoal legacy = new ShipRangeTargetGoal(friendly);
                if (legacy.canUse()) {
                    legacy.start();
                }
                helper.assertTrue(friendly.getEntityTarget() == plane,
                        "LEGACY AntiAir did not acquire hostile aircraft");
                helper.succeed();
            }
        }, 1D, 4D, 12D);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_hostile_uid_missile_impact")
    public static void hostileMissileSkipsOwnContactAndBlast(GameTestHelper helper) {
        waitForPositions(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                EntityBBKirishimaMob host = hostile(helper, entities, 1D);
                host.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
                EntityBBKirishimaMob ally = hostile(helper, entities, 8D);
                EntityAirplaneTMob plane = aircraft(helper, entities, host, 9D);
                CountingMissile contact = entities.add(new CountingMissile(
                        ModEntities.ABYSS_MISSILE.get(), helper.getLevel()));
                initMissile(helper, contact, host, 8D);
                add(helper, contact, 8D);
                helper.assertTrue(ally.isPickable() && plane.isPickable(),
                        "Hostile contact targets are not pickable");
                helper.assertTrue(helper.getLevel().getEntities(contact,
                        contact.getBoundingBox().inflate(1D, 1.5D, 1D)).contains(ally),
                        "Hostile ship was not in missile contact range");
                contact.tickCount = 6;
                contact.tick();
                helper.assertTrue(contact.impacts == 0, "Hostile missile exploded on allied ship contact");
                contact.moveTo(helper.absoluteVec(new Vec3(9D, 4D, 1D)));
                contact.tick();
                helper.assertTrue(contact.impacts == 0, "Hostile missile exploded on allied aircraft contact");
                contact.discard();

                BasicEntityShip opponent = friendly(helper, entities, 11D, 9214);
                opponent.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
                CountingMissile enemyContact = entities.add(new CountingMissile(
                        ModEntities.ABYSS_MISSILE.get(), helper.getLevel()));
                initMissile(helper, enemyContact, host, 11D);
                add(helper, enemyContact, 11D);
                helper.assertTrue(opponent.isPickable() && helper.getLevel().getEntities(enemyContact,
                        enemyContact.getBoundingBox().inflate(1D, 1.5D, 1D)).contains(opponent),
                        "Other owner's ship was not in missile contact range");
                enemyContact.tickCount = 6;
                enemyContact.tick();
                helper.assertTrue(enemyContact.tickCount > 5,
                        "Missile contact check did not reach the armed tick");
                helper.assertTrue(enemyContact.impacts > 0,
                        "Hostile missile did not explode on player-owned ship contact");
                enemyContact.discard();
                float allyHealth = ally.getHealth();
                float planeHealth = plane.getHealth();
                float opponentHealth = opponent.getHealth();
                CountingMissile blast = entities.add(new CountingMissile(
                        ModEntities.ABYSS_MISSILE.get(), helper.getLevel()));
                initMissile(helper, blast, host, 9D);
                add(helper, blast, 9D);
                blast.triggerImpact();
                helper.assertTrue(ally.getHealth() == allyHealth && plane.getHealth() == planeHealth,
                        "Hostile missile blast damaged an allied ship or aircraft");
                helper.assertTrue(opponent.getHealth() < opponentHealth,
                        "Hostile missile blast did not damage player-owned ship");
                helper.succeed();
            }
        }, 1D, 8D, 9D, 11D);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_hostile_uid_beam_damage")
    public static void hostileBeamProtectsOwnAircraftButDamagesOtherOwner(GameTestHelper helper) {
        waitForPositions(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                EntityBBKirishimaMob host = hostile(helper, entities, 1D);
                EntityAirplaneTMob plane = aircraft(helper, entities, host, 4D);
                plane.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
                plane.setHealth(1000F);
                BasicEntityShip opponent = friendly(helper, entities, 4D, 9215);
                opponent.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
                float planeHealth = plane.getHealth();
                float opponentHealth = opponent.getHealth();
                EntityProjectileBeam beam = entities.add(ModEntities.PROJECTILE_BEAM.get().create(helper.getLevel()));
                helper.assertTrue(beam != null, "Beam creation failed");
                beam.initBeam(host, 1D, 0D, 0D, 100F);
                add(helper, beam, 0D);
                beam.tick();
                helper.assertTrue(plane.getHealth() == planeHealth, "Hostile beam damaged allied aircraft");
                helper.assertTrue(opponent.getHealth() < opponentHealth,
                        "Hostile beam did not damage player-owned ship");
                helper.succeed();
            }
        }, 0D, 1D, 4D);
    }

    private static void waitForPositions(GameTestHelper helper, Runnable verification, double... positions) {
        Vec3[] points = new Vec3[positions.length];
        for (int i = 0; i < positions.length; i++) {
            points[i] = new Vec3(positions[i], 4D, 1D);
        }
        GameTestEntities.whenPositionsTicking(helper, verification, points);
    }

    private static EntityBBKirishimaMob hostile(GameTestHelper helper, GameTestEntities entities, double x) {
        EntityBBKirishimaMob hostile = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        helper.assertTrue(hostile != null, "Hostile creation failed");
        hostile.setNoAi(true);
        add(helper, hostile, x);
        return hostile;
    }

    private static BasicEntityShip friendly(GameTestHelper helper, GameTestEntities entities, double x, int uid) {
        BasicEntityShip friendly = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(friendly != null, "Friendly creation failed");
        friendly.setNoAi(true);
        friendly.setPlayerUID(uid);
        friendly.setStateFlag(ID.F.NoFuel, false);
        friendly.setStateFlag(ID.F.PassiveAI, false);
        add(helper, friendly, x);
        return friendly;
    }

    private static AuthorityFriendly authorityFriendly(
            GameTestHelper helper, GameTestEntities entities, double x, int uid) {
        AuthorityFriendly ship = entities.add(new AuthorityFriendly(ModEntities.BB_KONGOU.get(), helper.getLevel()));
        ship.setNoAi(true);
        ship.setPlayerUID(uid);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PassiveAI, false);
        add(helper, ship, x);
        ship.setAITargetList();
        return ship;
    }

    private static EntityAirplaneTMob aircraft(
            GameTestHelper helper, GameTestEntities entities, BasicEntityShipHostile host, double x) {
        EntityAirplaneTMob plane = entities.add(ModEntities.AIRPLANE_T_MOB.get().create(helper.getLevel()));
        helper.assertTrue(plane != null, "Aircraft creation failed");
        plane.initAttrs(host, null, 1, (float) host.getY());
        plane.setNoAi(true);
        add(helper, plane, x);
        return plane;
    }

    private static EntityAbyssMissile missile(
            GameTestHelper helper, GameTestEntities entities, com.lulan.shincolle.entity.IShipAttackBase host,
            double x) {
        EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
        helper.assertTrue(missile != null, "Missile creation failed");
        initMissile(helper, missile, host, x);
        add(helper, missile, x);
        return missile;
    }

    private static void initMissile(GameTestHelper helper, EntityAbyssMissile missile,
                                    com.lulan.shincolle.entity.IShipAttackBase host, double x) {
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        missile.initMissile(host, 0, 0, 100F, 0F, (float) position.y,
                (float) position.x, (float) position.y, (float) position.z,
                160, 0F, 0F, 0F, 0F);
        missile.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
        missile.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
    }

    private static void add(GameTestHelper helper, Entity entity, double x) {
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        entity.moveTo(position.x, position.y, position.z);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add fixture entity");
        GameTestEntities.assertRegistered(helper, entity);
    }

    private static ShipTargetAuthorityGoal goal(Entity ship, Class<?> type) {
        try {
            Field field = type.getDeclaredField("targetAuthorityGoal");
            field.setAccessible(true);
            return (ShipTargetAuthorityGoal) field.get(ship);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot access authority goal", error);
        }
    }

    private static void install(Entity ship, Class<?> type, ShipTargetAuthorityGoal goal) {
        try {
            Field field = type.getDeclaredField("targetAuthorityGoal");
            field.setAccessible(true);
            field.set(ship, goal);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot install authority goal", error);
        }
    }

    private static void tick(ShipTargetAuthorityGoal goal, Entity ship, int time) {
        ship.tickCount = time;
        TargetHelper.updateTarget((com.lulan.shincolle.entity.IShipAttackBase) ship);
        goal.tick();
    }

    private static final class AuthorityHostile extends EntityBBKirishimaMob {
        private AuthorityHostile(EntityType<? extends EntityBBKirishimaMob> type, Level level) {
            super(type, level);
        }

        @Override
        public void setAITargetList() {
            ShipTargetAuthorityGoal goal = new ShipTargetAuthorityGoal(this);
            install(this, BasicEntityShipHostile.class, goal);
            this.targetSelector.addGoal(1, goal);
        }
    }

    private static final class AuthorityFriendly extends EntityBBKongou {
        private AuthorityFriendly(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        @Override
        public void setAITargetList() {
            ShipTargetAuthorityGoal goal = new ShipTargetAuthorityGoal(this);
            install(this, BasicEntityShip.class, goal);
            this.targetSelector.addGoal(1, goal);
        }
    }

    private static final class CountingMissile extends EntityAbyssMissile {
        private int impacts;

        private CountingMissile(EntityType<? extends EntityAbyssMissile> type, Level level) {
            super(type, level);
        }

        @Override
        protected void onImpact(Entity hit) {
            this.impacts++;
        }

        private void triggerImpact() {
            super.onImpact(null);
        }
    }
}
