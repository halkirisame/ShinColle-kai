package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.combat.AttackHoldReason;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.hime.EntityCarrierWD;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.handler.ConfigHandler;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Real attack, equipment and saved-egg entrypoints, independent of the internal profile API. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipClassAttackOverridesGameTests {
    private static final Vec3 START = new Vec3(2D, 3D, 2D);
    private static final Vec3 TARGET = new Vec3(5D, 3D, 2D);
    private static final int EGG_UID = 7_540_001;

    private ShipClassAttackOverridesGameTests() { }

    private record Values(EntityType<? extends BasicEntityShip> type, float melee, float heavy) { }

    private static List<Values> values() {
        return List.of(new Values(ModEntities.BB_KIRISHIMA.get(), 1.5F, 1F),
                new Values(ModEntities.DESTROYER_IKAZUCHI.get(), 1F, 1F),
                new Values(ModEntities.BB_HIME.get(), 3F, 1F),
                new Values(ModEntities.CV_HIME.get(), 2F, 1F),
                new Values(ModEntities.CV_WD.get(), 1F, 1F),
                new Values(ModEntities.NORTHERN_HIME.get(), 1F, 0.75F),
                new Values(ModEntities.HARBOUR_HIME.get(), 1F, 0.75F),
                new Values(ModEntities.ISOLATED_HIME.get(), 1F, 1F),
                new Values(ModEntities.AIRFIELD_HIME.get(), 1F, 1F),
                new Values(ModEntities.DD_HIME.get(), 1F, 1F),
                new Values(ModEntities.MIDWAY_HIME.get(), 1F, 1F),
                new Values(ModEntities.CA_ATAGO.get(), 0.125F, 1F));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyMeleeAndHeavyMultipliersMatchProfiles(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (Values entry : values()) {
                BasicEntityShip ship = ship(helper, entities, entry.type());
                ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, 8F);
                ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_H, 20F);
                ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_AL, 31F);
                ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_AH, 47F);
                close(helper, ship.getAttrs().getAttackDamage(), 8F, "Light fixture");
                close(helper, ship.getAttrs().getAttackDamageHeavy(), 20F, "Heavy fixture");
                close(helper, ship.getAttackBaseDamage(0, null), 8F * entry.melee(), entry.type() + " melee");
                close(helper, ship.getAttackBaseDamage(1, null), 8F, entry.type() + " light");
                close(helper, ship.getAttackBaseDamage(2, null), 20F * entry.heavy(), entry.type() + " heavy");
                close(helper, ship.getAttackBaseDamage(3, null), 31F, entry.type() + " light aircraft");
                close(helper, ship.getAttackBaseDamage(4, null), 47F, entry.type() + " heavy aircraft");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void missileProfilesReapplyAfterEquipmentRecalculation(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip control = ship(helper, entities, ModEntities.BB_KONGOU.get());
            for (EntityType<? extends BasicEntityShip> type : List.of(ModEntities.BB_HIME.get(),
                    ModEntities.HARBOUR_HIME.get(), ModEntities.ISOLATED_HIME.get(),
                    ModEntities.CA_ATAGO.get(), ModEntities.CV_HIME.get())) {
                BasicEntityShip ship = ship(helper, entities, type);
                for (boolean married : new boolean[]{false, true}) {
                    for (boolean ring : new boolean[]{false, true}) {
                        ship.setStateFlag(ID.F.IsMarried, married);
                        ship.setStateFlag(ID.F.UseRingEffect, ring);
                        helper.assertTrue(ship.getStateFlag(ID.F.IsMarried) == married
                                && ship.getStateFlag(ID.F.UseRingEffect) == ring, "Marriage fixture was ignored");
                        // Empty, specified type, another specified type, then removal; every calculation is repeated.
                        for (int variant : new int[]{-1, 5, 8, -1}) {
                            ItemStack equipment = variant < 0 ? ItemStack.EMPTY : ammo(variant);
                            ship.getCapaShipInventory().setStackInSlot(0, equipment.copy());
                            control.getCapaShipInventory().setStackInSlot(0, equipment.copy());
                            for (int repeat = 0; repeat < 2; repeat++) {
                                control.calcShipAttributes(31, false);
                                ship.calcShipAttributes(31, false);
                                int specified = variant == 5 ? 5 : variant == 8 ? 3 : 0;
                                helper.assertTrue(control.getMissileData(2).type == specified,
                                        "Equipment fixture did not apply its specified missile type");
                                for (int channel = 0; channel < 5; channel++) {
                                    MissileData expected = copy(control.getMissileData(channel));
                                    if (channel == 2 && expected.type == 0) {
                                        if (type == ModEntities.BB_HIME.get()) {
                                            expected.type = 3;
                                            expected.movetype = 1;
                                        } else if (type == ModEntities.HARBOUR_HIME.get()) {
                                            expected.type = 2;
                                            expected.movetype = 0;
                                            expected.vel0 = 3F;
                                            expected.accY1 = 0F;
                                            expected.accY2 = 0F;
                                        } else if (type == ModEntities.ISOLATED_HIME.get()
                                                || type == ModEntities.CA_ATAGO.get() && married && ring) {
                                            expected.type = 5;
                                        }
                                    }
                                    if (channel == 0 && type == ModEntities.CV_HIME.get()) {
                                        expected.vel0 = Math.max(0.8F, expected.vel0);
                                        expected.accY1 = Math.max(1.1F, expected.accY1);
                                        expected.accY2 = Math.max(1.1F, expected.accY2);
                                    }
                                    assertMissile(helper, ship.getMissileData(channel), expected,
                                            type + " channel " + channel + " variant " + variant);
                                }
                            }
                        }
                    }
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void carrierWaterDemonHasLightAndRejectsBothHeavyEntrypoints(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                EntityCarrierWD wd = (EntityCarrierWD) ship(helper, entities, ModEntities.CV_WD.get());
                RecordingTarget target = target(helper, entities);
                helper.assertTrue(wd.getAttackType(ID.F.AtkType_Light) && !wd.getAttackType(ID.F.AtkType_Heavy),
                        "New water demon must have light cannon but no heavy cannon");
                assertHeavyRejected(helper, entities, wd, target);
                wd.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
                helper.assertTrue(wd.attackEntityWithAmmo(target) || !target.hits.isEmpty(), "Light entrypoint failed");
                helper.assertTrue(!target.hits.isEmpty(), "Light entrypoint never called target.hurt");
                helper.succeed();
            }
        }, START, TARGET);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void loadedCarrierWaterDemonRepairsOnlyIntrinsicCapabilities(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = ship(helper, entities, ModEntities.CV_WD.get());
            source.setStateFlag(ID.F.UseAmmoLight, false);
            source.setStateFlag(ID.F.UseAmmoHeavy, false);
            source.setStateFlag(ID.F.AtkType_Light, false);
            source.setStateFlag(ID.F.AtkType_Heavy, true);
            CompoundTag saved = ShipConsumptionDefaultsGameTests.savedFixture(source, EGG_UID);
            for (boolean direct : new boolean[]{false, true}) {
                BasicEntityShip loaded = ship(helper, entities, ModEntities.CV_WD.get());
                // Direct Capa loading has no responsibility for owner UUID or inventory.
                loaded.setOwnerUUID(source.getOwnerUUID());
                loaded.getCapaShipInventory().deserializeNBT(saved.getCompound(CapaShipInventory.InvName));
                if (direct) CapaShipSavedValues.loadNBTData(saved, loaded);
                else loaded.readAdditionalSaveData(saved);
                assertRestored(helper, loaded, source, saved);
                for (CompoundTag partial : partialSaves()) {
                    loaded.setStateFlag(ID.F.AtkType_Light, false);
                    loaded.setStateFlag(ID.F.AtkType_Heavy, true);
                    CapaShipSavedValues.loadNBTData(partial, loaded);
                    assertRestored(helper, loaded, source, saved);
                }
            }
            helper.succeed();
        }
    }

    private static List<CompoundTag> partialSaves() {
        CompoundTag shortSave = new CompoundTag();
        shortSave.putByteArray("StateFlag", new byte[1]);
        CompoundTag flags = new CompoundTag();
        flags.putBoolean("AmmoL", false);
        flags.putBoolean("AmmoH", false);
        CompoundTag props = new CompoundTag();
        props.put("ShipFlags", flags);
        CompoundTag legacy = new CompoundTag();
        legacy.put("ShipExtProps", props);
        return List.of(shortSave, legacy, new CompoundTag());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void savedCarrierWaterDemonEggRestoresCannonCapability(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip source = ship(helper, entities, ModEntities.CV_WD.get());
                source.setStateFlag(ID.F.AtkType_Light, false);
                source.setStateFlag(ID.F.AtkType_Heavy, true);
                source.setStateFlag(ID.F.UseAmmoLight, false);
                source.setStateFlag(ID.F.UseAmmoHeavy, false);
                CompoundTag saved = ShipConsumptionDefaultsGameTests.savedFixture(source, EGG_UID);
                BasicEntityShip restored = ShipConsumptionDefaultsGameTests.hatch(helper, entities, saved);
                helper.assertTrue(restored.getType() == ModEntities.CV_WD.get(), "Egg spawned the wrong class");
                assertRestored(helper, restored, source, saved);
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(EGG_UID);
            }
        }, ShipConsumptionDefaultsGameTests.SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 300,
            batch = "isolated_class_attack_water_demon")
    public static void carrierWaterDemonFireControlFiresLightAndAircraftButNeverHeavy(GameTestHelper helper) {
        naturalWaterDemon(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 300,
            batch = "isolated_class_attack_mounted_water_demon")
    public static void mountedCarrierWaterDemonDelegatesLightAndRejectsHeavy(GameTestHelper helper) {
        naturalWaterDemon(helper, true);
    }

    private static void naturalWaterDemon(GameTestHelper helper, boolean mounted) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper);
            CountingWaterDemon wd = scene.entities.add(new CountingWaterDemon(ModEntities.CV_WD.get(), helper.getLevel()));
            prepare(helper, wd);
            wd.setNoAi(false);
            scene.register(wd, START);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(wd.tickCount >= 24,
                    "Waiting for goal re-registration")).thenExecute(() -> scene.guard(() -> {
                        scene.owner(wd);
                        helper.assertTrue(wd.getNumAircraftLight() > 0 && wd.getNumAircraftHeavy() > 0
                                && wd.getAttackType(ID.F.AtkType_AirLight) && wd.getAttackType(ID.F.AtkType_AirHeavy),
                                "Natural aircraft fixture lacks stocks or capabilities");
                        RecordingTarget target = target(helper, scene.entities);
                        scene.register(target, TARGET);
                        wd.goalSelector.getAvailableGoals().removeIf(w -> w.getGoal() instanceof ShipWanderGoal);
                        Mob fireHost;
                        if (mounted) {
                            BasicEntityMount mount = scene.entities.add(ModEntities.MOUNT_CAWD.get().create(helper.getLevel()));
                            mount.setInvulnerable(true);
                            mount.setNoGravity(true);
                            scene.register(mount, START);
                            mount.setHost(wd);
                            helper.assertTrue(wd.startRiding(mount, true) && mount.getHost() == wd
                                    && wd.getVehicle() == mount, "Mount has no real host/passenger");
                            int lightBefore = wd.lightCalls;
                            wd.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
                            mount.attackEntityWithAmmo(target);
                            helper.assertTrue(wd.lightCalls == lightBefore + 1 && !target.hits.isEmpty(),
                                    "Mount did not delegate light to host");
                            Snapshot before = Snapshot.of(wd);
                            helper.assertTrue(!mount.attackEntityWithHeavyAmmo(target), "Mount accepted heavy cannon");
                            helper.assertTrue(before.equals(Snapshot.of(wd)), "Mount heavy rejection had side effects");
                            wd.heavyCalls = 0;
                            target.hits.clear();
                            fireHost = mount;
                        } else {
                            fireHost = wd;
                        }
                        scene.attack(wd, target);
                        boolean[] aircraft = {false};
                        int lightAtStart = wd.lightCalls;
                        for (int tick = 1; tick <= 160; tick++) {
                            helper.runAfterDelay(tick, () -> scene.guard(() -> {
                                target.setPos(fireHost.position().add(3D, 0D, 0D));
                                target.setDeltaMovement(Vec3.ZERO);
                                wd.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
                                helper.assertTrue(wd.getGrudge() > 0 && !wd.getStateFlag(ID.F.NoFuel)
                                        && wd.hasAmmoLight() && wd.hasAmmoHeavy(), "Natural fixture ran out of resources");
                                for (Entity entity : helper.getLevel().getAllEntities()) {
                                    if (entity instanceof BasicEntityAirplane plane && plane.getHost() == wd) {
                                        scene.entities.add(plane);
                                        aircraft[0] = true;
                                    }
                                    if (entity instanceof EntityAbyssMissile missile && missile.getHostEntity() == wd) {
                                        scene.entities.add(missile);
                                        helper.fail("Water demon spawned a heavy missile");
                                    }
                                }
                                if (wd.currentTargetLock().isPresent()) {
                                    var plan = ShipCombatGate.plan(fireHost, ShipCombatGate.engagement(fireHost));
                                    helper.assertTrue(plan.holds().getOrDefault(WeaponChannel.HEAVY, Set.of())
                                            .contains(AttackHoldReason.NOT_ENABLED), "Heavy plan lacks NOT_ENABLED");
                                }
                            }));
                        }
                        helper.runAfterDelay(161, () -> scene.guard(() -> {
                            helper.assertTrue(wd.currentTargetLock().isPresent(), "Manual lock was not established");
                            helper.assertTrue(wd.lightCalls > lightAtStart && target.hits.stream().anyMatch(h -> h.attacker() == wd),
                                    "Natural FireControl never hurt the target with light cannon");
                            helper.assertTrue(aircraft[0], "Natural FireControl never spawned aircraft");
                            helper.assertTrue(wd.heavyCalls == 0, "Natural FireControl called heavy cannon");
                            helper.succeed();
                        }));
                    }));
        }, START, TARGET);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 300,
            batch = "isolated_class_attack_natural_values")
    public static void restoredValuesReachNaturalCombatWithoutChangingOtherClasses(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Scene scene = new Scene(helper);
            scene.guard(() -> {
                unchanged(helper, scene.entities);
                BasicEntityShip harbour = ship(helper, scene.entities, ModEntities.HARBOUR_HIME.get());
                RecordingTarget dummy = target(helper, scene.entities);
                List<EntityAbyssMissile> missiles = launch(helper, scene.entities, harbour, dummy);
                helper.assertTrue(missiles.size() == 1 && missiles.get(0).getProjectileType() == 2,
                        "Harbour heavy entrypoint did not launch a railgun");
                close(helper, missiles.get(0).getAttrs().getAttackDamageHeavy(),
                        harbour.getAttrs().getAttackDamageHeavy() * 0.75F, "Harbour launched heavy damage");
                close(helper, (float) missiles.get(0).vel0, 3F, "Harbour railgun launch speed");
                missiles.forEach(Entity::discard);
                BasicEntityShip kirishima = ship(helper, scene.entities, ModEntities.BB_KIRISHIMA.get());
                kirishima.setNoAi(false);
                kirishima.setStateFlag(ID.F.UseMelee, true);
                kirishima.setStateFlag(ID.F.UseAmmoLight, false);
                kirishima.setStateFlag(ID.F.UseAmmoHeavy, false);
                scene.register(kirishima, START);
                helper.startSequence().thenWaitUntil(() -> helper.assertTrue(kirishima.tickCount >= 24,
                        "Waiting for melee goal re-registration")).thenExecute(() -> scene.guard(() -> {
                            scene.owner(kirishima);
                            RecordingTarget target = target(helper, scene.entities);
                            scene.register(target, TARGET);
                            kirishima.goalSelector.getAvailableGoals().removeIf(w -> w.getGoal() instanceof ShipWanderGoal);
                            target.setPos(kirishima.position().add(0D, 0D, 1D));
                            scene.attack(kirishima, target);
                            for (int tick = 1; tick <= 100; tick++) {
                                helper.runAfterDelay(tick, () -> scene.guard(() -> {
                                    target.setPos(kirishima.position().add(0D, 0D, 1D));
                                    target.setDeltaMovement(Vec3.ZERO);
                                }));
                            }
                            helper.runAfterDelay(101, () -> scene.guard(() -> {
                                helper.assertTrue(kirishima.currentTargetLock().isPresent(), "Natural melee has no lock");
                                List<Hit> hits = target.hits.stream().filter(h -> h.attacker() == kirishima).toList();
                                helper.assertTrue(!hits.isEmpty(), "Natural melee never called target.hurt");
                                for (Hit hit : hits) close(helper, hit.damage(), hit.light() * 1.5F, "Natural Kirishima melee");
                                helper.succeed();
                            }));
                        }));
            });
        }, START, TARGET);
    }

    private static void unchanged(GameTestHelper helper, GameTestEntities entities) {
        for (EntityType<? extends BasicEntityShip> type : List.of(ModEntities.CA_TAKAO.get(), ModEntities.BB_YAMATO.get(),
                ModEntities.DESTROYER_SHIMAKAZE.get(), ModEntities.BB_TA.get(), ModEntities.BB_KONGOU.get(),
                ModEntities.BB_NAGATO.get(),
                ModEntities.CL_TENRYUU.get(), ModEntities.CL_TATSUTA.get())) {
            BasicEntityShip ship = ship(helper, entities, type);
            float expected = type == ModEntities.CL_TENRYUU.get() || type == ModEntities.CL_TATSUTA.get() ? 2F
                    : type == ModEntities.BB_NAGATO.get() ? 3F : 0.125F;
            close(helper, ship.getAttackBaseDamage(0, null), ship.getAttrs().getAttackDamage() * expected,
                    "Unaffected melee " + type);
        }
        for (EntityType<? extends BasicEntityShipHostile> type : List.of(ModEntities.BB_KIRISHIMA_MOB.get(),
                ModEntities.CL_TENRYUU_MOB.get(), ModEntities.CL_TATSUTA_MOB.get())) {
            BasicEntityShipHostile hostile = entities.add(type.create(helper.getLevel()));
            hostile.setNoAi(true);
            hostile.calcShipAttributes(31, false);
            float expected = type == ModEntities.BB_KIRISHIMA_MOB.get() ? 0.125F : 2F;
            close(helper, hostile.getAttackBaseDamage(0, null), hostile.getAttrs().getAttackDamage() * expected,
                    "Held hostile melee " + type);
        }
        BasicEntityShip normal = ship(helper, entities, ModEntities.BB_KONGOU.get());
        RecordingTarget target = target(helper, entities);
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY)) {
            helper.assertTrue(ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW,
                    "Old configuration must still run NEW");
            List<EntityAbyssMissile> shots = launch(helper, entities, normal, target);
            helper.assertTrue(shots.size() == 1 && shots.get(0).getProjectileType() == 0, "Ordinary heavy shot changed");
            close(helper, shots.get(0).getAttrs().getAttackDamageHeavy(), normal.getAttrs().getAttackDamageHeavy(),
                    "Ordinary heavy damage");
            shots.forEach(Entity::discard);
        }
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities,
                                       EntityType<? extends BasicEntityShip> type) {
        BasicEntityShip ship = entities.add(type.create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create " + type);
        prepare(helper, ship);
        return ship;
    }

    private static void prepare(GameTestHelper helper, BasicEntityShip ship) {
        ship.setNoAi(true);
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
        ship.setStateFlag(ID.F.UseAmmoLight, true);
        ship.setStateFlag(ID.F.UseAmmoHeavy, true);
        ship.setStateFlag(ID.F.UseAirLight, true);
        ship.setStateFlag(ID.F.UseAirHeavy, true);
        ship.calcShipAttributes(31, false);
        ship.setHealth(ship.getMaxHealth());
        helper.assertTrue(ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel)
                && ship.hasAmmoLight() && ship.hasAmmoHeavy(), "Combat resources were ignored");
    }

    private static RecordingTarget target(GameTestHelper helper, GameTestEntities entities) {
        RecordingTarget target = entities.add(new RecordingTarget(EntityType.COW, helper.getLevel()));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setInvulnerable(true);
        target.setPos(helper.absoluteVec(TARGET));
        return target;
    }

    private record Snapshot(List<Integer> minor, int combatTick) {
        static Snapshot of(BasicEntityShip ship) {
            return new Snapshot(Arrays.stream(ship.getStateMinorArray()).boxed().toList(), ship.getCombatTick());
        }
    }

    private static void assertHeavyRejected(GameTestHelper helper, GameTestEntities entities,
                                             BasicEntityShip wd, Entity target) {
        wd.tickCount = 77;
        Snapshot before = Snapshot.of(wd);
        helper.assertTrue(!wd.attackEntityWithHeavyAmmo(target), "Water demon accepted Entity heavy entrypoint");
        helper.assertTrue(before.equals(Snapshot.of(wd)), "Entity rejection changed resources, rewards or combat tick");
        helper.assertTrue(!wd.attackEntityWithHeavyAmmo(target.blockPosition()), "Water demon accepted BlockPos heavy entrypoint");
        helper.assertTrue(before.equals(Snapshot.of(wd)), "BlockPos rejection changed resources, rewards or combat tick");
        helper.assertTrue(ownedMissiles(helper, entities, wd).isEmpty(), "Rejected heavy shot spawned a missile");
    }

    private static List<EntityAbyssMissile> launch(GameTestHelper helper, GameTestEntities entities,
                                                   BasicEntityShip ship, Entity target) {
        helper.assertTrue(ownedMissiles(helper, entities, ship).isEmpty(), "Fixture already owns a missile");
        helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Heavy entrypoint rejected stocked ship");
        return ownedMissiles(helper, entities, ship);
    }

    private static List<EntityAbyssMissile> ownedMissiles(GameTestHelper helper, GameTestEntities entities, Entity host) {
        List<EntityAbyssMissile> missiles = new ArrayList<>();
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof EntityAbyssMissile missile && missile.getHostEntity() == host && !missile.isRemoved()) {
                missiles.add(entities.add(missile));
            }
        }
        return missiles;
    }

    private static void assertRestored(GameTestHelper helper, BasicEntityShip loaded,
                                        BasicEntityShip source, CompoundTag saved) {
        helper.assertTrue(loaded.getAttackType(ID.F.AtkType_Light) && !loaded.getAttackType(ID.F.AtkType_Heavy),
                "Saved water demon capabilities were not repaired");
        helper.assertTrue(!loaded.getStateFlag(ID.F.UseAmmoLight) && !loaded.getStateFlag(ID.F.UseAmmoHeavy),
                "Load changed user cannon preferences");
        byte[] savedFlags = saved.getByteArray("StateFlag");
        for (int index = 0; index < savedFlags.length; index++) {
            if (index == ID.F.AtkType_Light || index == ID.F.AtkType_Heavy) continue;
            helper.assertTrue(loaded.getStateFlag(index) == (savedFlags[index] != 0),
                    "Capability repair changed unrelated flag " + index);
        }
        helper.assertTrue(source.getOwnerUUID().equals(loaded.getOwnerUUID()) && source.ownerName.equals(loaded.ownerName),
                "Load changed original owner");
        for (int index : new int[]{ID.M.ShipUID, ID.M.PlayerUID, ID.M.ShipLevel, ID.M.ExpCurrent,
                ID.M.NumGrudge, ID.M.NumAmmoLight, ID.M.NumAmmoHeavy, ID.M.NumAirLight, ID.M.NumAirHeavy}) {
            helper.assertTrue(loaded.getStateMinor(index) == saved.getIntArray("StateMinor")[index],
                    "Load changed unrelated saved quantity " + index);
        }
        helper.assertTrue(saved.getCompound(CapaShipInventory.InvName).equals(loaded.getCapaShipInventory().serializeNBT()),
                "Load changed equipment or cargo");
    }

    private static ItemStack ammo(int variant) {
        ItemStack stack = new ItemStack(ModItems.EQUIP_AMMO.get());
        BasicEquip.setEquipMeta(stack, variant);
        return stack;
    }

    private static MissileData copy(MissileData source) {
        MissileData data = new MissileData();
        data.type = source.type;
        data.movetype = source.movetype;
        data.vel0 = source.vel0;
        data.accY1 = source.accY1;
        data.accY2 = source.accY2;
        return data;
    }

    private static void assertMissile(GameTestHelper helper, MissileData actual, MissileData expected, String label) {
        helper.assertTrue(actual.type == expected.type && actual.movetype == expected.movetype,
                label + ": type/move " + actual.type + "/" + actual.movetype + " != " + expected.type + "/" + expected.movetype);
        close(helper, actual.vel0, expected.vel0, label + " speed");
        close(helper, actual.accY1, expected.accY1, label + " acceleration 1");
        close(helper, actual.accY2, expected.accY2, label + " acceleration 2");
    }

    private static void close(GameTestHelper helper, float actual, float expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 0.0001F, label + ": " + actual + " != " + expected);
    }

    private record Hit(Entity attacker, float damage, float light) { }

    private static final class RecordingTarget extends Cow {
        final List<Hit> hits = new ArrayList<>();

        RecordingTarget(EntityType<? extends Cow> type, Level level) { super(type, level); }

        @Override
        public boolean hurt(DamageSource source, float damage) {
            if (source.getEntity() instanceof IShipAttackBase ship) {
                this.hits.add(new Hit(source.getEntity(), damage, ship.getAttrs().getAttackDamage()));
            }
            return false;
        }
    }

    private static final class CountingWaterDemon extends EntityCarrierWD {
        int lightCalls;
        int heavyCalls;

        CountingWaterDemon(EntityType<? extends EntityCarrierWD> type, Level level) { super(type, level); }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.lightCalls++;
            return super.attackEntityWithAmmo(target);
        }

        @Override
        public boolean attackEntityWithHeavyAmmo(Entity target) {
            this.heavyCalls++;
            return super.attackEntityWithHeavyAmmo(target);
        }
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestEntities entities;
        FakePlayer owner;

        Scene(GameTestHelper helper) {
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
            this.owner = FakePlayerFactory.get(this.helper.getLevel(), new GameProfile(UUID.randomUUID(), "class_attack_owner"));
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
