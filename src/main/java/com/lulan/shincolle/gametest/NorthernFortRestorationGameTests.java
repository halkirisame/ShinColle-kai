package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipAircraftAttackGoal;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.hime.EntityNorthernHime;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityAirplane;
import com.lulan.shincolle.entity.other.EntityAirplaneTakoyaki;
import com.lulan.shincolle.entity.other.EntityFloatingFort;
import com.lulan.shincolle.entity.other.EntityProjectileStatic;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NorthernFortRestorationGameTests {
    private static final Vec3 START = new Vec3(1.5D, 80D, 1.5D);
    private static final float EPSILON = .00001F;

    private NorthernFortRestorationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void northernHeavyEntitySpawnsFortAndAircraftRemainsTakoyaki(GameTestHelper helper) {
        scene(helper, s -> {
            EntityNorthernHime host = s.northern();
            CapturingCow target = s.cow(s.origin.add(6D, 0D, 0D));
            int consumption = host.getAmmoConsumption();

            int ammo = host.getStateMinor(ID.M.NumAmmoHeavy);
            int air = host.getNumAircraftHeavy();
            int mark = s.joined.size();
            helper.assertTrue(host.attackEntityWithHeavyAmmo(target), "Heavy entity attack was rejected");
            helper.assertTrue(s.count(mark, EntityFloatingFort.class) == 1, "Heavy entity input did not spawn one fort");
            helper.assertTrue(s.count(mark, EntityAbyssMissile.class) == 0, "Heavy entity input also spawned a missile");
            helper.assertTrue(ammo - host.getStateMinor(ID.M.NumAmmoHeavy) == consumption, "Fort did not cost one heavy ammo unit");
            helper.assertTrue(host.getNumAircraftHeavy() == air, "Fort launch spent a heavy aircraft slot");

            ammo = host.getStateMinor(ID.M.NumAmmoHeavy);
            mark = s.joined.size();
            helper.assertTrue(host.attackEntityWithHeavyAircraft(target), "Heavy aircraft launch was rejected");
            helper.assertTrue(s.count(mark, EntityAirplaneTakoyaki.class) == 1, "Heavy aircraft was not a takoyaki");
            helper.assertTrue(s.count(mark, EntityFloatingFort.class) == 0, "Heavy aircraft spawned a fort");
            helper.assertTrue(ammo - host.getStateMinor(ID.M.NumAmmoHeavy) == 2 * consumption, "Heavy aircraft cost changed");
            helper.assertTrue(host.getNumAircraftHeavy() == air - 1, "Heavy aircraft slot cost changed");

            mark = s.joined.size();
            helper.assertTrue(host.attackEntityWithAircraft(target), "Light aircraft launch was rejected");
            helper.assertTrue(s.count(mark, EntityAirplane.class) == 1 && s.count(mark, EntityFloatingFort.class) == 0,
                    "Light aircraft is not the ordinary airplane");

            mark = s.joined.size();
            helper.assertTrue(host.attackEntityWithHeavyAmmo(BlockPos.containing(s.origin.add(20D, 0D, 0D))),
                    "Heavy block-position attack was rejected");
            helper.assertTrue(s.count(mark, EntityAbyssMissile.class) == 1 && s.count(mark, EntityFloatingFort.class) == 0,
                    "Block-position heavy input did not stay an ordinary missile");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void floatingFortUsesDetachedCannonAttributeProfile(GameTestHelper helper) {
        scene(helper, s -> {
            EntityNorthernHime host = s.northern();
            CapturingCow target = s.cow(s.origin.add(6D, 0D, 0D));
            host.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, 10F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.ATK_AL, 7F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.ATK_H, 80F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.ATK_AH, 33F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.DEF, 20F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.SPD, 2F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.MOV, .5F);
            helper.assertTrue(host.getAttrs().getAttackDamageHeavy() == 80F && host.getAttrs().getAttackDamageAirHeavy() == 33F,
                    "Host attack fixture was ignored");
            float expectedLight = host.getAttackBaseDamage(1, target);
            float expectedHeavy = host.getAttackBaseDamage(2, target);
            close(helper, expectedHeavy, 60D, "Northern heavy getter");

            EntityFloatingFort fort = s.launch(host, target);
            var attrs = fort.getAttrs();
            close(helper, attrs.getAttackDamageHeavy(), expectedHeavy, "Fort H");
            close(helper, attrs.getAttackDamageAirHeavy(), expectedHeavy, "Fort AH comes from the cannon getter, not host AH");
            close(helper, attrs.getAttackDamage(), expectedLight, "Fort L");
            close(helper, attrs.getAttackDamageAir(), expectedLight, "Fort AL");
            close(helper, fort.getMaxHealth(), host.getMaxHealth() * .1F, "Fort HP");
            close(helper, attrs.getAttrsBuffed(ID.Attrs.DEF), host.getAttrs().getDefense() * .5F, "Fort DEF");
            close(helper, attrs.getAttrsBuffed(ID.Attrs.SPD), host.getAttrs().getAttackSpeed(), "Fort SPD");
            close(helper, attrs.getAttrsBuffed(ID.Attrs.MOV), host.getAttrs().getMoveSpeed() * .1F + .3F, "Fort MOV");
            close(helper, fort.getAttributeValue(Attributes.MOVEMENT_SPEED), host.getAttrs().getMoveSpeed() * .1F + .3F, "Fort entity speed");
            close(helper, attrs.getAttrsBuffed(ID.Attrs.HIT), 6D, "Fort HIT");
            close(helper, attrs.getAttrsBuffed(ID.Attrs.DODGE), host.getAttrs().getAttrsBuffed(ID.Attrs.DODGE) + .3F, "Fort DODGE");
            close(helper, fort.getAttributeValue(Attributes.FOLLOW_RANGE), 64D, "Fort follow range");
            close(helper, fort.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), .5D, "Fort knockback resistance");
            helper.assertTrue(fort.getHealth() == fort.getMaxHealth(), "Fort did not start at full health");
            helper.assertTrue(fort.getHost() == host && fort.getEntityTarget() == target, "Fort host/target");

            host.getAttrs().setAttrsBuffed(ID.Attrs.ATK_H, 1F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.ATK_AH, 2F);
            close(helper, attrs.getAttackDamageHeavy(), expectedHeavy, "Fort H after host change");
            close(helper, attrs.getAttackDamageAirHeavy(), expectedHeavy, "Fort AH after host change");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void floatingFortPursuesAndDetonatesAtOriginalBoundaries(GameTestHelper helper) {
        scene(helper, s -> {
            EntityNorthernHime host = s.northern();
            CapturingCow target = s.cow(s.origin.add(6D, 0D, 0D));
            Vec3 at = s.origin.add(0D, 5D, 0D);

            EntityFloatingFort sample = s.launch(host, target);
            helper.assertTrue(sample.goalSelector.getAvailableGoals().stream()
                    .noneMatch(goal -> goal.getGoal() instanceof ShipAircraftAttackGoal), "Fort kept the circling aircraft goal");
            sample.discard();

            // Detonation distance is measured to the target's feet plus one block, squared, against 6.
            for (double reach : new double[]{Math.sqrt(6D) - .01D, Math.sqrt(6D) + .01D}) {
                s.reset();
                CapturingCow bystander = s.bystander(at);
                EntityFloatingFort fort = s.launch(host, target);
                place(fort, target, at, reach);
                fort.tickCount = 2;
                fort.tick();
                boolean inside = reach < Math.sqrt(6D);
                helper.assertTrue(fort.isRemoved() == inside && bystander.hits == (inside ? 1 : 0),
                        "Detonation at reach " + reach + " removed=" + fort.isRemoved() + " hits=" + bystander.hits);
                fort.discard();
            }

            // The path is asked for every 16th tick while farther than two blocks; no path on the others.
            for (int age : new int[]{15, 16, 17}) {
                EntityFloatingFort fort = s.launch(host, target);
                place(fort, target, at, 9D);
                fort.getNavigation().stop();
                fort.tickCount = age;
                fort.tick();
                helper.assertTrue(!fort.isRemoved(), "Far fort detonated at age " + age);
                helper.assertTrue(fort.getNavigation().isInProgress() == (age == 16), "Path request at age " + age);
                fort.discard();
            }

            // The flight limit is 500 ticks.
            for (int age : new int[]{499, 500}) {
                s.reset();
                CapturingCow bystander = s.bystander(at);
                EntityFloatingFort fort = s.launch(host, target);
                place(fort, target, at, 9D);
                fort.tickCount = age;
                fort.tick();
                helper.assertTrue(fort.isRemoved() == (age == 500) && bystander.hits == (age == 500 ? 1 : 0),
                        "Age limit at " + age + " removed=" + fort.isRemoved() + " hits=" + bystander.hits);
                fort.discard();
            }

            // Losing the target or being sent home detonates.
            s.reset();
            CapturingCow bystander = s.bystander(at);
            EntityFloatingFort lost = s.launch(host, target);
            place(lost, target, at, 9D);
            target.discard();
            lost.tickCount = 3;
            lost.tick();
            helper.assertTrue(lost.isRemoved() && bystander.hits == 1,
                    "Lost target did not detonate once: removed=" + lost.isRemoved() + " hits=" + bystander.hits);

            s.reset();
            CapturingCow bystander2 = s.bystander(at);
            CapturingCow target2 = s.cow(s.origin.add(6D, 0D, 0D));
            EntityFloatingFort home = s.launch(host, target2);
            place(home, target2, at, 9D);
            setBackHome(home);
            home.tickCount = 3;
            home.tick();
            helper.assertTrue(home.isRemoved() && bystander2.hits == 1, "Returning fort did not detonate once");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void floatingFortImpactUsesHostAttributionAndSingleDamagePipeline(GameTestHelper helper) {
        scene(helper, s -> {
            EntityNorthernHime host = s.northern();
            CapturingCow cow = s.cow(s.origin.add(6D, 0D, 0D));
            EntityFloatingFort fort = s.launch(host, cow);
            Vec3 at = s.origin.add(0D, 5D, 0D);
            float snapshot = fort.getAttrs().getAttackDamageHeavy();
            CapturingSquid water = s.add(new CapturingSquid(helper.getLevel()), at.add(1D, 0D, 0D));
            CapturingBoat boat = s.add(new CapturingBoat(helper.getLevel()), at.add(-1D, 0D, 0D));
            place(fort, cow, at, 1D);

            // The host, not the fort's launch-time copy, supplies the AA/ASM bonus and the combat roll.
            host.getAttrs().setAttrsBuffed(ID.Attrs.ASM, 7F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
            host.getAttrs().setAttrsBuffed(ID.Attrs.CRI, 1F);
            fort.getAttrs().setAttrsBuffed(ID.Attrs.ASM, 0F);
            fort.getAttrs().setAttrsBuffed(ID.Attrs.CRI, 0F);
            // Types are read when the fort explodes, not when it was launched.
            host.getMissileData(0).type = 5;
            host.getMissileData(2).type = 0;
            int mark = s.joined.size();
            fort.tickCount = 2;
            fort.tick();
            helper.assertTrue(fort.isRemoved(), "Fort did not detonate");
            helper.assertTrue(s.count(mark, EntityProjectileStatic.class) == 0, "Impact read the melee channel type");
            for (CapturingEntity victim : List.<CapturingEntity>of(cow, water, boat)) {
                helper.assertTrue(victim.hits() == 1, victim + " damage calls=" + victim.hits());
                DamageSource source = victim.source();
                helper.assertTrue(source.is(DamageTypeTags.IS_EXPLOSION), "Not an explosion source: " + source);
                helper.assertTrue(source.getDirectEntity() == fort && source.getEntity() == host, "Source attribution: " + source);
            }
            close(helper, cow.amount(), snapshot * 1.5F, "Land target (host critical roll, no bonus)");
            close(helper, water.amount(), (snapshot + 7F) * 1.5F, "Water target (host ASM)");

            // Re-entering after the end of the flight changes nothing.
            int hits = cow.hits();
            helper.assertTrue(!fort.attackEntityWithHeavyAmmo(cow), "Finished fort reported an impact");
            fort.tick();
            helper.assertTrue(cow.hits() == hits && water.hits() == 1, "Finished fort damaged again");

            // md[2] is read live when the fort explodes.
            host.getMissileData(2).type = 5;
            host.getMissileData(0).type = 0;
            EntityFloatingFort second = s.launch(host, cow);
            place(second, cow, at, 1D);
            mark = s.joined.size();
            second.tickCount = 2;
            second.tick();
            helper.assertTrue(s.count(mark, EntityProjectileStatic.class) == 1, "Impact did not read the heavy channel type");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void floatingFortCleanupNeverResumesOrDuplicatesResources(GameTestHelper helper) {
        scene(helper, s -> {
            EntityNorthernHime host = s.northern();
            CapturingCow target = s.cow(s.origin.add(6D, 0D, 0D));
            Vec3 at = s.origin.add(0D, 5D, 0D);

            // Without a launching ship nothing explodes and nothing is handed back.
            for (int variant = 0; variant < 3; variant++) {
                s.reset();
                CapturingCow bystander = s.bystander(at);
                EntityFloatingFort fort = s.launch(host, target);
                place(fort, target, at, 1D);
                int ammo = host.getStateMinor(ID.M.NumAmmoHeavy);
                int air = host.getNumAircraftHeavy();
                EntityFloatingFort running = fort;
                if (variant == 1) {
                    running = s.entities.add(new EntityFloatingFort(ModEntities.FLOATING_FORT.get(), helper.getLevel()));
                    CompoundTag saved = new CompoundTag();
                    fort.addAdditionalSaveData(saved);
                    running.readAdditionalSaveData(saved);
                    running.setPos(at);
                    fort.discard();
                } else if (variant == 0) {
                    fort.setHost(null);
                }
                EntityNorthernHime lost = host;
                if (variant == 2) {
                    // the launching ship dies while its fortress is in the air
                    fort.discard();
                    lost = s.northern();
                    running = s.launch(lost, target);
                    place(running, target, at, 1D);
                    ammo = lost.getStateMinor(ID.M.NumAmmoHeavy);
                    air = lost.getNumAircraftHeavy();
                    lost.discard();
                }
                running.tickCount = 2;
                running.tick();
                helper.assertTrue(running.isRemoved(), "Orphan fort variant " + variant + " survived");
                helper.assertTrue(bystander.hits == 0, "Orphan fort variant " + variant + " exploded");
                helper.assertTrue(lost.getStateMinor(ID.M.NumAmmoHeavy) == ammo && lost.getNumAircraftHeavy() == air,
                        "Orphan fort variant " + variant + " returned resources");
            }

            // A living ship with a lost target or a recall order still gets one detonation and no refund.
            s.reset();
            CapturingCow bystander = s.bystander(at);
            EntityFloatingFort fort = s.launch(host, target);
            place(fort, target, at, 9D);
            int ammo = host.getStateMinor(ID.M.NumAmmoHeavy);
            int air = host.getNumAircraftHeavy();
            setBackHome(fort);
            fort.tickCount = 2;
            fort.tick();
            helper.assertTrue(fort.isRemoved() && bystander.hits == 1, "Recalled fort detonation");
            helper.assertTrue(host.getStateMinor(ID.M.NumAmmoHeavy) == ammo && host.getNumAircraftHeavy() == air,
                    "Detonated fort refunded resources");

            // The admiral being dead or unresolvable does not end the flight of a fort whose launching ship lives.
            s.reset();
            s.ownerDown(true);
            for (int variant = 0; variant < 4; variant++) {
                if (variant == 3) {
                    s.ownerDown(false);
                    host.setOwnerUUID(UUID.randomUUID());
                    helper.assertTrue(host.getHostEntity() == null, "Unresolvable admiral fixture was ignored");
                } else {
                    helper.assertTrue(host.getHostEntity() != null && !host.getHostEntity().isAlive(),
                            "Dead admiral fixture was ignored");
                }
                CapturingCow near = s.bystander(at);
                CapturingCow far = s.cow(s.origin.add(6D, 0D, 0D));
                EntityFloatingFort flying = s.launch(host, far);
                place(flying, far, at, 20D);
                flying.getNavigation().stop();
                int heavy = host.getStateMinor(ID.M.NumAmmoHeavy);
                int slots = host.getNumAircraftHeavy();
                flying.tickCount = 100;
                flying.tick();
                helper.assertTrue(!flying.isRemoved() && near.hits == 0,
                        "Fort variant " + variant + " ended without its admiral: removed=" + flying.isRemoved());
                if (variant == 0) {
                    flying.tickCount = 500;
                } else if (variant == 1) {
                    far.discard();
                    flying.tickCount = 101;
                } else if (variant == 2) {
                    setBackHome(flying);
                    flying.tickCount = 101;
                } else {
                    place(flying, far, at, 1D);
                    flying.tickCount = 101;
                }
                flying.tick();
                helper.assertTrue(flying.isRemoved() && near.hits == 1,
                        "Fort variant " + variant + " did not detonate once: removed=" + flying.isRemoved() + " hits=" + near.hits);
                helper.assertTrue(host.getStateMinor(ID.M.NumAmmoHeavy) == heavy && host.getNumAircraftHeavy() == slots,
                        "Fort variant " + variant + " refunded resources");
                far.discard();
                s.reset();
            }
            s.ownerDown(false);
            host.setOwnerUUID(s.ownerId());

            // A shortage of ammo rejects the shot before any other effect.
            host.setStateMinor(ID.M.NumAmmoHeavy, host.getAmmoConsumption() - 1);
            int exp = host.getStateMinor(ID.M.ExpCurrent);
            int grudge = host.getStateMinor(ID.M.NumGrudge);
            int morale = host.getMorale();
            int combat = host.getCombatTick();
            int mark = s.joined.size();
            helper.assertTrue(!host.attackEntityWithHeavyAmmo(target), "Heavy attack without ammo succeeded");
            helper.assertTrue(s.joined.size() == mark, "Rejected heavy attack spawned something");
            helper.assertTrue(host.getStateMinor(ID.M.ExpCurrent) == exp && host.getStateMinor(ID.M.NumGrudge) == grudge
                    && host.getMorale() == morale && host.getCombatTick() == combat, "Rejected heavy attack was charged");

            // Ordinary aircraft still refund their slot when they come home.
            host.setStateMinor(ID.M.NumAmmoLight, 10_000);
            host.setNumAircraftLight(4);
            mark = s.joined.size();
            helper.assertTrue(host.attackEntityWithAircraft(target), "Light aircraft launch");
            BasicEntityAirplane plane = s.only(mark, EntityAirplane.class);
            int slots = host.getNumAircraftLight();
            setBackHome(plane);
            plane.setPos(host.getX(), host.getY() + host.getBbHeight(), host.getZ());
            plane.tickCount = 40;
            plane.tick();
            helper.assertTrue(plane.isRemoved() && host.getNumAircraftLight() == slots + 1, "Aircraft refund changed");
        });
    }

    private static void place(EntityFloatingFort fort, Entity target, Vec3 fortAt, double reach) {
        fort.setPos(fortAt);
        fort.setDeltaMovement(Vec3.ZERO);
        // distance to the target's feet plus one block equals the reach along x
        target.setPos(fortAt.x + reach, fortAt.y - 1D, fortAt.z);
    }

    private static void setBackHome(BasicEntityAirplane plane) {
        try {
            Field field = BasicEntityAirplane.class.getDeclaredField("backHome");
            field.setAccessible(true);
            field.setBoolean(plane, true);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON * Math.max(1D, Math.abs(expected)), label + ": " + actual + " != " + expected);
    }

    private static void scene(GameTestHelper helper, Consumer<FortScene> body) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                FortScene scene = new FortScene(helper, entities);
                MinecraftForge.EVENT_BUS.register(scene);
                try {
                    body.accept(scene);
                    helper.succeed();
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(scene);
                    scene.close();
                }
            }
        }, START, START.add(40D, 0D, 0D));
    }

    private interface CapturingEntity {
        int hits();
        float amount();
        DamageSource source();
    }

    private static final class FortScene {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final Vec3 origin;
        final List<Entity> joined = new ArrayList<>();
        private final List<CapturingCow> cows = new ArrayList<>();
        private FakePlayer owner;

        FortScene(GameTestHelper helper, GameTestEntities entities) {
            this.helper = helper;
            this.entities = entities;
            this.origin = helper.absoluteVec(START);
        }

        @SubscribeEvent
        public void fortJoined(EntityJoinLevelEvent event) {
            joined.add(event.getEntity());
            if (event.getEntity() instanceof EntityFloatingFort || event.getEntity() instanceof BasicEntityAirplane
                    || event.getEntity() instanceof EntityAbyssMissile || event.getEntity() instanceof EntityProjectileStatic) {
                entities.add(event.getEntity());
            }
        }

        /** Puts the admiral on the death screen (still listed, no health) or back on their feet. */
        void ownerDown(boolean down) {
            owner.setHealth(down ? 0F : owner.getMaxHealth());
        }

        UUID ownerId() {
            return owner.getUUID();
        }

        void close() {
            if (owner != null) helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }

        /** Drops the leftover bystanders of a previous case. */
        void reset() {
            cows.forEach(Entity::discard);
            cows.clear();
        }

        <T extends Entity> T add(T entity, Vec3 at) {
            entities.add(entity);
            entity.setPos(at);
            entity.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add " + entity);
            return entity;
        }

        CapturingCow cow(Vec3 at) {
            CapturingCow cow = add(new CapturingCow(helper.getLevel()), at);
            cow.setNoAi(true);
            return cow;
        }

        CapturingCow bystander(Vec3 at) {
            CapturingCow cow = cow(at);
            cows.add(cow);
            return cow;
        }

        EntityNorthernHime northern() {
            if (owner == null) {
                owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fort_owner"));
                owner.moveTo(origin.add(0D, 0D, -8D));
                helper.getLevel().addNewPlayer(owner);
            }
            EntityNorthernHime ship = entities.add(new EntityNorthernHime(ModEntities.NORTHERN_HIME.get(), helper.getLevel()));
            helper.assertTrue(ship != null, "Ship creation");
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.setInvulnerable(true);
            ship.setOwnerUUID(owner.getUUID());
            ship.setPos(origin);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setAmmoLight(10_000);
            ship.setAmmoHeavy(10_000);
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.setNumAircraftLight(4);
            ship.setNumAircraftHeavy(3);
            // Experience from the fixture's own launches must not level the ship up: a level-up recalculates
            // every attribute and silently drops the combat roll fixture below.
            ship.setStateMinor(ID.M.ExpNext, 1_000_000);
            ship.setStateMinor(ID.M.ExpCurrent, 0);
            // a missed explosion would look like a missing one
            ship.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
            helper.assertTrue(CombatHelper.calcMissRate(ship, 1F) == 0F, "Combat roll fixture was ignored");
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Could not add the ship");
            helper.assertTrue(ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel) && ship.hasAmmoHeavy()
                    && ship.getNumAircraftHeavy() == 3 && ship.getHostEntity() == owner, "Ship fixture");
            return ship;
        }

        EntityFloatingFort launch(EntityNorthernHime host, Entity target) {
            int mark = joined.size();
            helper.assertTrue(host.attackEntityWithHeavyAmmo(target), "Heavy attack rejected");
            return only(mark, EntityFloatingFort.class);
        }

        int count(int from, Class<? extends Entity> type) {
            return (int) joined.subList(from, joined.size()).stream().filter(type::isInstance).count();
        }

        <T extends Entity> T only(int from, Class<T> type) {
            List<T> found = joined.subList(from, joined.size()).stream().filter(type::isInstance).map(type::cast).toList();
            helper.assertTrue(found.size() == 1, "Expected one " + type.getSimpleName() + ", observed=" + found.size());
            return found.get(0);
        }
    }

    private static final class CapturingCow extends Cow implements CapturingEntity {
        int hits;
        float amount;
        DamageSource source;

        CapturingCow(Level level) { super(EntityType.COW, level); }

        @Override public boolean hurt(DamageSource source, float amount) {
            hits++;
            this.amount = amount;
            this.source = source;
            return true;
        }

        @Override public int hits() { return hits; }
        @Override public float amount() { return amount; }
        @Override public DamageSource source() { return source; }
    }

    private static final class CapturingSquid extends Squid implements CapturingEntity {
        int hits;
        float amount;
        DamageSource source;

        CapturingSquid(Level level) { super(EntityType.SQUID, level); }

        @Override public boolean hurt(DamageSource source, float amount) {
            hits++;
            this.amount = amount;
            this.source = source;
            return true;
        }

        @Override public int hits() { return hits; }
        @Override public float amount() { return amount; }
        @Override public DamageSource source() { return source; }
    }

    private static final class CapturingBoat extends Boat implements CapturingEntity {
        int hits;
        float amount;
        DamageSource source;

        CapturingBoat(Level level) { super(EntityType.BOAT, level); }

        @Override public boolean hurt(DamageSource source, float amount) {
            hits++;
            this.amount = amount;
            this.source = source;
            return true;
        }

        @Override public int hits() { return hits; }
        @Override public float amount() { return amount; }
        @Override public DamageSource source() { return source; }
    }
}
