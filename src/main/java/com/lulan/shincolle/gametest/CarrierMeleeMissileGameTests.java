package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipClassAttackGate;
import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.MissileData;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Carrier princess melee entrypoints launch the channel-zero missile instead of touching the target. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarrierMeleeMissileGameTests {
    private static final Vec3 START = new Vec3(2D, 3D, 2D);
    private static final Vec3 NEAR = new Vec3(12D, 3D, 2D);
    private static final Vec3 FAR = new Vec3(26D, 3.5D, 2D);
    private static final int EGG_UID = 7_360_101;

    private CarrierMeleeMissileGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void carrierMeleeLaunchesChannelZeroMissileWithoutContactDamage(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = ship(helper, entities, ModEntities.CV_HIME.get());
                BasicEntityShip control = ship(helper, entities, ModEntities.BB_KONGOU.get());
                RecordingTarget target = target(helper, entities, NEAR);
                ResourceLocation slow = new ResourceLocation("minecraft", "slowness");
                ship.getAttackEffectMap().put(slow, new ShipAttackEffect(slow, 1, 100, 100));
                MissileData md = ship.getMissileData(0);
                md.type = 2;
                md.movetype = 3;
                md.vel0 = 1.5F;
                md.accY1 = 1.3F;
                md.accY2 = 1.2F;
                ship.tickCount = 77;
                control.tickCount = 77;
                int light = ship.getStateMinor(ID.M.NumAmmoLight);
                int heavy = ship.getStateMinor(ID.M.NumAmmoHeavy);
                int airLight = ship.getStateMinor(ID.M.NumAirLight);
                int airHeavy = ship.getStateMinor(ID.M.NumAirHeavy);
                int grudge = ship.getStateMinor(ID.M.NumGrudge);
                int exp = ship.getStateMinor(ID.M.ExpCurrent);
                int morale = ship.getStateMinor(ID.M.Morale);
                int controlExp = control.getStateMinor(ID.M.ExpCurrent);
                int controlMorale = control.getStateMinor(ID.M.Morale);
                float expected = ship.getAttackBaseDamage(0, target);
                close(helper, expected, ship.getAttrs().getAttackDamage() * 2F, "Carrier melee base damage");

                helper.assertTrue(control.doHurtTarget(target) || target.hits.size() == 1, "Ordinary melee did not touch");
                helper.assertTrue(target.hits.size() == 1 && shots(helper, control).isEmpty(),
                        "Ordinary ship melee is no longer a contact hit");
                target.hits.clear();

                helper.assertTrue(ship.doHurtTarget(target), "Carrier melee reported failure");
                helper.assertTrue(target.hits.isEmpty(), "Carrier melee still touched the target");
                List<EntityAbyssMissile> launched = shots(helper, ship);
                helper.assertTrue(launched.size() == 1, "Carrier melee launched " + launched.size() + " missiles");
                EntityAbyssMissile shot = entities.add(launched.get(0));
                close(helper, shot.getAttrs().getAttackDamage(), expected, "Missile damage");
                close(helper, shot.getAttrs().getAttrsBuffed(ID.Attrs.KB), 0F, "Missile knockback");
                helper.assertTrue(shot.life == 100 && shot.moveType == 0 && shot.getMissileType() == 2,
                        "Missile life/move/type " + shot.life + "/" + shot.moveType + "/" + shot.getMissileType());
                close(helper, (float) shot.vel0, 1.5F, "Missile input speed");
                close(helper, (float) shot.accY1, 0F, "Direct acceleration 1");
                close(helper, (float) shot.accY2, 0F, "Direct acceleration 2");
                close(helper, (float) shot.getX(), (float) ship.getX(), "Missile origin X");
                close(helper, (float) shot.getY(), (float) ship.getY() + 1.3F, "Missile origin Y");
                close(helper, (float) shot.getZ(), (float) ship.getZ(), "Missile origin Z");
                Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * .2D, target.getZ())
                        .subtract(shot.position()).normalize().scale(1.5D);
                close(helper, (float) shot.velX, (float) aim.x, "Missile velocity X");
                close(helper, (float) shot.velY, (float) aim.y, "Missile velocity Y");
                close(helper, (float) shot.velZ, (float) aim.z, "Missile velocity Z");
                helper.assertTrue(shot.getHostEntity() == ship && shot.getPlayerUID() == ship.getPlayerUID(), "Missile owner");
                helper.assertTrue(shot.effectMap.equals(ship.getAttackEffectMap()) && shot.effectMap != ship.getAttackEffectMap(),
                        "Missile effect copy");
                helper.assertTrue(shot.getTrailDelay().remainingTicks() == 0, "Melee missile must not delay its trail");

                helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoLight) == light && ship.getStateMinor(ID.M.NumAmmoHeavy) == heavy
                        && ship.getStateMinor(ID.M.NumAirLight) == airLight && ship.getStateMinor(ID.M.NumAirHeavy) == airHeavy
                        && ship.getStateMinor(ID.M.NumGrudge) == grudge, "Melee charged ammo, aircraft or grudge");
                helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) - exp == control.getStateMinor(ID.M.ExpCurrent) - controlExp
                        && ship.getStateMinor(ID.M.Morale) - morale == control.getStateMinor(ID.M.Morale) - controlMorale,
                        "Melee experience/morale were not charged exactly once like ordinary melee");
                helper.assertTrue(ship.getCombatTick() == 77, "Melee did not set the combat tick");
                shot.discard();
                helper.succeed();
            }
        }, START, NEAR);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void carrierMeleeProfileSurvivesEquipmentAndSaveRestoration(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = ship(helper, entities, ModEntities.CV_HIME.get());
                BasicEntityShip control = ship(helper, entities, ModEntities.BB_KONGOU.get());
                RecordingTarget target = target(helper, entities, NEAR);
                assertLaunch(helper, entities, ship, target, 0, .8F, 1.1F, "Minimum profile");

                MissileData md = ship.getMissileData(0);
                md.type = 2;
                md.vel0 = 1.6F;
                md.accY1 = 1.3F;
                md.accY2 = 1.4F;
                ShipClassAttackGate.finalizeEquipment(ship);
                ShipClassAttackGate.finalizeEquipment(ship);
                assertLaunch(helper, entities, ship, target, 2, 1.6F, 1.3F, "Stronger equipment value");
                helper.assertTrue(ship.getMissileData(0).accY2 == 1.4F, "Repeated finalization accumulated");

                ItemStack ammo = new ItemStack(ModItems.EQUIP_AMMO.get());
                BasicEquip.setEquipMeta(ammo, 5);
                for (BasicEntityShip each : List.of(ship, control)) {
                    each.getCapaShipInventory().setStackInSlot(0, ammo.copy());
                    each.calcShipAttributes(31, false);
                    each.calcShipAttributes(31, false);
                }
                helper.assertTrue(ship.getMissileData(2).type == 5, "Equipment fixture did not apply");
                MissileData other0 = control.getMissileData(0);
                assertLaunch(helper, entities, ship, target, other0.type, Math.max(.8F, other0.vel0),
                        Math.max(1.1F, other0.accY1), "After equipment recalculation");
                for (int channel = 1; channel < 5; channel++) {
                    MissileData actual = ship.getMissileData(channel);
                    MissileData other = control.getMissileData(channel);
                    helper.assertTrue(actual.type == other.type && actual.vel0 == other.vel0 && actual.accY1 == other.accY1
                            && actual.accY2 == other.accY2, "Melee profile changed channel " + channel);
                }
                ship.getCapaShipInventory().setStackInSlot(0, ItemStack.EMPTY);
                ship.calcShipAttributes(31, false);
                assertLaunch(helper, entities, ship, target, 0, .8F, 1.1F, "After equipment removal");

                CompoundTag saved = ship.saveWithoutId(new CompoundTag());
                BasicEntityShip loaded = entities.add(ModEntities.CV_HIME.get().create(helper.getLevel()));
                loaded.load(saved);
                loaded.setNoAi(true);
                loaded.setNoGravity(true);
                loaded.setPos(helper.absoluteVec(START));
                loaded.calcShipAttributes(31, false);
                assertLaunch(helper, entities, loaded, target, 0, .8F, 1.1F, "After normal load");

                CompoundTag egg = ShipConsumptionDefaultsGameTests.savedFixture(ship, EGG_UID);
                try {
                    BasicEntityShip restored = ShipConsumptionDefaultsGameTests.hatch(helper, entities, egg);
                    restored.setNoAi(true);
                    restored.setNoGravity(true);
                    restored.setPos(helper.absoluteVec(START));
                    restored.setAmmoHeavy(10_000);
                    assertLaunch(helper, entities, restored, target, 0, .8F, 1.1F, "After egg restoration");
                } finally {
                    ServerDataManager.removeShipData(EGG_UID);
                }

                // The launch input is a snapshot; later host changes do not alter a flying missile.
                ship.calcShipAttributes(31, false);
                helper.assertTrue(ship.doHurtTarget(target), "Snapshot launch failed");
                EntityAbyssMissile shot = entities.add(firstShot(helper, ship));
                float damage = shot.getAttrs().getAttackDamage();
                ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, 999F);
                ship.getMissileData(0).vel0 = 3F;
                close(helper, shot.getAttrs().getAttackDamage(), damage, "Flying missile followed host attributes");
                close(helper, (float) shot.vel0, .8F, "Flying missile followed host speed");
                shot.discard();
                helper.succeed();
            }
        }, START, NEAR);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 420,
            batch = "isolated_carrier_melee_natural")
    public static void carrierNaturalMeleeUsesExistingAuthorityAndTiming(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Natural scene = new Natural(helper);
            BasicEntityShip fresh = ModEntities.CV_HIME.get().create(helper.getLevel());
            helper.assertTrue(!fresh.getStateFlag(ID.F.UseMelee), "UseMelee default must stay off");
            fresh.discard();
            scene.ship = scene.entities.add(ModEntities.CV_HIME.get().create(helper.getLevel()));
            BasicEntityShip ship = scene.ship;
            prepare(helper, ship);
            ship.setNoAi(false);
            ship.setAmmoLight(0);
            ship.setAmmoHeavy(0);
            ship.setStateMinor(ID.M.NumAirLight, 0);
            ship.setStateMinor(ID.M.NumAirHeavy, 0);
            scene.register(ship, START);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ship.tickCount >= 24,
                    "Waiting for goal re-registration")).thenExecute(() -> scene.guard(() -> {
                        scene.owner(ship);
                        scene.target = target(helper, scene.entities, START);
                        scene.target.setPos(helper.absoluteVec(START.add(0D, 0D, 1D)));
                        ship.goalSelector.getAvailableGoals().removeIf(w -> w.getGoal() instanceof ShipWanderGoal);
                        scene.attack(ship, scene.target);
                        for (int tick = 1; tick <= 330; tick++) {
                            int step = tick;
                            helper.runAfterDelay(tick, () -> scene.guard(() -> scene.step(step)));
                        }
                    }));
        }, START, START.add(0D, 0D, 1D));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void carrierMeleeAppliesDamageOnlyAtProjectileImpact(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (int variant = 0; variant < 3; variant++) {
                    BasicEntityShip ship = ship(helper, entities, ModEntities.CV_HIME.get());
                    RecordingTarget target = target(helper, entities, FAR);
                    helper.assertTrue(ship.doHurtTarget(target), "Launch failed variant " + variant);
                    EntityAbyssMissile shot = entities.add(firstShot(helper, ship));
                    helper.assertTrue(target.hits.isEmpty(), "Damage was dealt at launch");
                    int age = 0;
                    while (!shot.isRemoved() && age < 80) {
                        age++;
                        shot.tickCount++;
                        shot.tick();
                        if (age <= 5) helper.assertTrue(target.hits.isEmpty() && !shot.isRemoved(),
                                "Collision started before the fifth tick");
                        if (age == 3 && variant == 1) ship.setEntitySit(true);
                        if (age == 3 && variant == 2) ship.discard();
                        if (age < 20) helper.assertTrue(!shot.isRemoved(), "Host state removed the flying missile at age " + age);
                    }
                    helper.assertTrue(shot.isRemoved(), "Missile never reached its end of life (variant " + variant + ")");
                    helper.assertTrue(target.hits.size() == 1 && target.hits.get(0).attacker() == ship,
                            "Impact damage count=" + target.hits.size() + " variant " + variant);
                    target.discard();
                }
                helper.succeed();
            }
        }, START, FAR);
    }

    private static void assertLaunch(GameTestHelper helper, GameTestEntities entities, BasicEntityShip ship, Entity target,
                                     int type, float speed, float acceleration, String label) {
        MissileData data = ship.getMissileData(0);
        helper.assertTrue(data.type == type && Math.abs(data.vel0 - speed) < .0001F
                && Math.abs(data.accY1 - acceleration) < .0001F, label + ": profile " + data.type + "/" + data.vel0 + "/" + data.accY1);
        helper.assertTrue(ship.doHurtTarget(target), label + ": melee failed");
        List<EntityAbyssMissile> launched = shots(helper, ship);
        helper.assertTrue(launched.size() == 1, label + ": launched " + launched.size());
        EntityAbyssMissile shot = entities.add(launched.get(0));
        helper.assertTrue(shot.getMissileType() == type && Math.abs(shot.vel0 - speed) < .0001D, label + ": missile "
                + shot.getMissileType() + "/" + shot.vel0);
        shot.discard();
    }

    private static EntityAbyssMissile firstShot(GameTestHelper helper, Entity host) {
        List<EntityAbyssMissile> shots = shots(helper, host);
        helper.assertTrue(!shots.isEmpty(), "Melee launched no missile");
        return shots.get(0);
    }

    private static List<EntityAbyssMissile> shots(GameTestHelper helper, Entity host) {
        List<EntityAbyssMissile> shots = new ArrayList<>();
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof EntityAbyssMissile shot && shot.getHostEntity() == host && !shot.isRemoved()) shots.add(shot);
        }
        return shots;
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities, EntityType<? extends BasicEntityShip> type) {
        BasicEntityShip ship = entities.add(type.create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create " + type);
        prepare(helper, ship);
        ship.setNoAi(true);
        ship.setPlayerUID(137);
        ship.setStateMinor(ID.M.ExpNext, 1_000_000);
        ship.setPos(helper.absoluteVec(START));
        return ship;
    }

    private static void prepare(GameTestHelper helper, BasicEntityShip ship) {
        ship.setInvulnerable(true);
        ship.setNoGravity(true);
        ship.setPos(helper.absoluteVec(START));
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setAmmoLight(10_000);
        ship.setAmmoHeavy(10_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PickItem, false);
        ship.setStateFlag(ID.F.UseMelee, false);
        ship.calcShipAttributes(31, false);
        ship.setHealth(ship.getMaxHealth());
        ship.setStateMinor(ID.M.ExpNext, 1_000_000);
        helper.assertTrue(ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel), "Combat resources were ignored");
    }

    private static RecordingTarget target(GameTestHelper helper, GameTestEntities entities, Vec3 position) {
        RecordingTarget target = entities.add(new RecordingTarget(EntityType.COW, helper.getLevel()));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setInvulnerable(true);
        target.setPos(helper.absoluteVec(position));
        helper.assertTrue(helper.getLevel().addFreshEntity(target), "Target was not added");
        return target;
    }

    private static void close(GameTestHelper helper, float actual, float expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 0.0001F, label + ": " + actual + " != " + expected);
    }

    private record Hit(Entity attacker, float damage) { }

    private static final class RecordingTarget extends Cow {
        final List<Hit> hits = new ArrayList<>();

        RecordingTarget(EntityType<? extends Cow> type, Level level) { super(type, level); }

        @Override
        public boolean hurt(DamageSource source, float damage) {
            if (source.getEntity() instanceof IShipAttackBase) this.hits.add(new Hit(source.getEntity(), damage));
            return true;
        }
    }

    /** Natural FireControl run: phases are driven by the real server clock, and the carrier holds no cannon or aircraft stock. */
    private static final class Natural implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestEntities entities;
        final Set<UUID> seen = new HashSet<>();
        BasicEntityShip ship;
        RecordingTarget target;
        FakePlayer owner;
        int spawned;
        int phaseStart;

        Natural(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            try {
                Field field = GameTestHelper.class.getDeclaredField("testInfo");
                field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                    @Override public void testStructureLoaded(GameTestInfo info) { }
                    @Override public void testPassed(GameTestInfo info) { close(); }
                    @Override public void testFailed(GameTestInfo info) { close(); }
                });
            } catch (ReflectiveOperationException failure) {
                throw new GameTestAssertException("Cannot attach scene cleanup: " + failure);
            }
        }

        void register(Entity entity, Vec3 position) {
            entity.setPos(this.helper.absoluteVec(position));
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(entity), "Fixture was not added");
            GameTestEntities.assertRegistered(this.helper, entity);
        }

        void owner(BasicEntityShip ship) {
            this.owner = FakePlayerFactory.get(this.helper.getLevel(), new GameProfile(UUID.randomUUID(), "carrier_melee_owner"));
            this.owner.setPos(ship.position());
            this.helper.getLevel().addNewPlayer(this.owner);
            ship.setOwnerUUID(this.owner.getUUID());
            this.helper.assertTrue(ship.getOwner() == this.owner, "Owner fixture is unresolved");
        }

        void attack(BasicEntityShip ship, Entity target) {
            ship.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                    new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(target))));
        }

        void guard(Runnable body) {
            try {
                ShipConsumptionDefaultsGameTests.guarded(this.helper, body);
            } catch (Throwable failure) {
                close();
                throw failure;
            }
        }

        /** Missiles are removed on sight, so only the launch itself is observed and never an impact. */
        int collect() {
            int fresh = 0;
            for (EntityAbyssMissile shot : shots(this.helper, this.ship)) {
                if (this.seen.add(shot.getUUID())) fresh++;
                this.entities.add(shot).discard();
            }
            this.spawned += fresh;
            return fresh;
        }

        void keepClose() {
            this.target.setPos(this.ship.position().add(0D, 0D, 1D));
            this.target.setDeltaMovement(Vec3.ZERO);
        }

        void step(int tick) {
            this.keepClose();
            int fresh = collect();
            int[] stock = {this.ship.getStateMinor(ID.M.NumAmmoLight), this.ship.getStateMinor(ID.M.NumAmmoHeavy),
                    this.ship.getStateMinor(ID.M.NumAirLight), this.ship.getStateMinor(ID.M.NumAirHeavy)};
            if (fresh > 0) this.helper.assertTrue(stock[0] == 0 && stock[1] == 0,
                    "Natural melee needed or consumed cannon stock: " + java.util.Arrays.toString(stock));
            this.helper.assertTrue(this.target.hits.isEmpty(), "Natural melee touched the target at tick " + tick);
            if (tick <= 80) {
                this.helper.assertTrue(fresh == 0, "Natural melee fired with UseMelee off at tick " + tick);
                if (tick == 80) {
                    this.helper.assertTrue(this.ship.currentTargetLock().isPresent(), "Manual lock was not established");
                    this.ship.setStateFlag(ID.F.UseMelee, true);
                }
            } else if (tick <= 160) {
                if (tick == 160) this.helper.assertTrue(this.spawned >= 1 && this.ship.currentTargetLock().isPresent(),
                        "Natural melee never launched with UseMelee on, lock="
                        + this.ship.currentTargetLock() + " plan=" + ShipCombatGate.plan(this.ship, ShipCombatGate.engagement(this.ship)));
                if (tick == 160) {
                    this.phaseStart = this.spawned;
                    this.ship.setEntitySit(true);
                }
            } else if (tick <= 240) {
                if (tick > 163) this.helper.assertTrue(fresh == 0, "Sitting carrier launched at tick " + tick);
                if (tick == 240) {
                    this.ship.setEntitySit(false);
                    this.ship.applyCommandState(new CommandIssuer.Player(this.owner.getUUID()),
                            new CommandStateOp.Apply(new ShipCommand.CancelAttack()));
                }
            } else if (tick > 246) {
                this.helper.assertTrue(fresh == 0, "Carrier without a lock launched at tick " + tick);
                if (tick == 330) {
                    this.helper.assertTrue(this.ship.currentTargetLock().isEmpty(), "Cancel kept the lock");
                    this.helper.succeed();
                }
            }
        }

        @Override
        public void close() {
            if (this.owner != null) {
                this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                this.owner = null;
            }
            this.entities.close();
        }
    }
}
