package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.MissileData;
import com.lulan.shincolle.utility.CombatHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.PlayMessages;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubmarineAttackOverridesGameTests {
    private static final Vec3 START = new Vec3(1.5D, 4D, 1.5D);
    private static final Vec3 TARGET = new Vec3(25.5D, 4D, 9.5D);
    private static final int EGG_UID = 869_401;

    private SubmarineAttackOverridesGameTests() { }

    private record Hull(EntityType<? extends BasicEntityShip> type, float speed, float acceleration, int count) { }

    private static List<Hull> hulls() {
        return List.of(new Hull(ModEntities.SS_YO.get(), .3F, .06F, 1),
                new Hull(ModEntities.SS_RO500.get(), .5F, .08F, 1),
                new Hull(ModEntities.SS_U511.get(), .4F, .07F, 1),
                new Hull(ModEntities.SS_HIME.get(), .3F, .06F, 2),
                new Hull(ModEntities.SSNH.get(), .3F, .06F, 2));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void friendlySubmarinesLaunchLightMissilesInsteadOfDirectDamage(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            for (Hull hull : hulls()) {
                BasicEntityShip ship = ship(helper, entities, hull.type());
                ship.setPlayerUID(137);
                for (boolean miss : new boolean[]{false, true}) {
                    random(ship, miss ? 0F : 1F);
                    int before = target.hurts;
                    ship.attackEntityWithAmmo(target);
                    List<EntityAbyssMissile> shots = shots(helper, entities, ship);
                    helper.assertTrue(shots.size() == hull.count(), hull.type() + " light spawn count=" + shots.size());
                    helper.assertTrue(target.hurts == before, "Light torpedo dealt immediate direct damage");
                    for (EntityAbyssMissile shot : shots) {
                        damage(helper, shot, ship.getAttrs().getAttackDamage());
                        helper.assertTrue(shot.life == (hull.count() == 2 ? 160 : 140)
                                && shot.getProjectileType() == ship.getMissileData(1).type
                                && shot.getPlayerUID() == 137, "Light life/type/owner mismatch");
                        shot.discard();
                    }
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void submarineLightProfilesSurviveEquipmentAndRepeatedRecalculation(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            BasicEntityShip control = ship(helper, entities, ModEntities.SS_KA.get());
            for (Hull hull : hulls()) {
                BasicEntityShip ship = ship(helper, entities, hull.type());
                for (int variant : new int[]{-1, 5, 8, -1}) {
                    ItemStack equipment = equipment(variant);
                    ship.getCapaShipInventory().setStackInSlot(0, equipment.copy());
                    control.getCapaShipInventory().setStackInSlot(0, equipment.copy());
                    for (int repeat = 0; repeat < 2; repeat++) {
                        control.calcShipAttributes(31, false);
                        ship.calcShipAttributes(31, false);
                        helper.assertTrue(control.getMissileData(2).type == (variant == 5 ? 5 : variant == 8 ? 3 : 0),
                                "Equipment did not reach the control profile");
                        profiles(helper, ship, control, hull);
                        close(helper, ship.getAttackBaseDamage(0, null), ship.getAttrs().getAttackDamage()
                                * (hull.type() == ModEntities.SSNH.get() ? 1F : .125F), "Submarine melee");
                    }
                }
            }
            BasicEntityShip so = ship(helper, entities, ModEntities.SS_SO.get());
            close(helper, so.getMissileData(1).vel0, .5F, "So must remain unchanged");
            close(helper, control.getMissileData(1).vel0, .5F, "Ka must remain unchanged");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void submarineHimeAndSsnhFireTwoHeavyMissilesForEntityAndBlockAim(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            for (Hull hull : hulls().subList(3, 5)) {
                BasicEntityShip ship = ship(helper, entities, hull.type());
                ship.getMissileData(2).type = 2;
                ship.getMissileData(2).movetype = 0;
                for (boolean block : new boolean[]{false, true}) {
                    int ammo = ship.getStateMinor(ID.M.NumAmmoHeavy);
                    helper.assertTrue(block ? ship.attackEntityWithHeavyAmmo(target.blockPosition())
                            : ship.attackEntityWithHeavyAmmo(target), "Stocked heavy rejected");
                    List<EntityAbyssMissile> shots = shots(helper, entities, ship);
                    helper.assertTrue(shots.size() == 2, "Heavy dual spawn count=" + shots.size() + " block=" + block);
                    helper.assertTrue(ammo - ship.getStateMinor(ID.M.NumAmmoHeavy) == ship.getAmmoConsumption(),
                            "Heavy charged per projectile");
                    for (EntityAbyssMissile shot : shots) {
                        damage(helper, shot, ship.getAttrs().getAttackDamageHeavy());
                        helper.assertTrue(shot.life == 160 && shot.getMissileType() == 2, "Heavy life/type mismatch");
                        close(helper, shot.getY(), (float) ship.getY() + ship.getBbHeight() * .6F, "Dual launch height");
                        shot.discard();
                    }
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void dualMissilesUseRotatedOriginsBeforeTrajectoryCalculation(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            for (Hull hull : hulls().subList(3, 5)) {
                BasicEntityShip ship = ship(helper, entities, hull.type());
                ship.getMissileData(2).movetype = 0;
                for (float yaw : new float[]{0F, 90F}) {
                    ship.yBodyRot = yaw;
                    ship.attackEntityWithHeavyAmmo(target);
                    List<EntityAbyssMissile> shots = shots(helper, entities, ship);
                    helper.assertTrue(shots.size() == 2, "Cannot compare rotated origins: count=" + shots.size());
                    for (EntityAbyssMissile shot : shots) {
                        double side = yaw == 0 ? shot.getX() - ship.getX() : shot.getZ() - ship.getZ();
                        close(helper, Math.abs(side), 1D, "Side offset");
                        close(helper, yaw == 0 ? shot.getZ() : shot.getX(), yaw == 0 ? ship.getZ() : ship.getX(), "Other axis");
                        Vec3 aim = new Vec3(target.getX(), (float) target.getY() + target.getBbHeight() * .1F, target.getZ());
                        Vec3 expected = aim.subtract(shot.position()).normalize().scale(shot.vel0);
                        velocity(helper, shot, expected);
                        shot.discard();
                    }
                    close(helper, shots.get(0).getX() + shots.get(1).getX(), 2D * ship.getX(), "Opposite X");
                    close(helper, shots.get(0).getZ() + shots.get(1).getZ(), 2D * ship.getZ(), "Opposite Z");
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void hostileSubmarinesUseFixedLightTorpedoProfiles(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            for (EntityType<? extends BasicEntityShipHostile> type : List.of(ModEntities.SS_U511_MOB.get(), ModEntities.SS_RO500_MOB.get())) {
                BasicEntityShipHostile ship = entities.add(type.create(helper.getLevel()));
                helper.assertTrue(ship != null, "Hostile creation failed");
                ship.setPos(helper.absoluteVec(START));
                ship.setNoAi(true);
                ship.setShipDepth(0D);
                ship.calcShipAttributes(31, false);
                ship.getMissileData(1).type = 5;
                ship.getMissileData(1).vel0 = 3F;
                ship.getMissileData(1).accY1 = 2F;
                ship.getMissileData(1).accY2 = 2F;
                ship.getMissileData(1).movetype = 4;
                random(ship, 1F);
                int before = target.hurts;
                ship.attackEntityWithAmmo(target);
                List<EntityAbyssMissile> shots = shots(helper, entities, ship);
                helper.assertTrue(shots.size() == 1, "Hostile light spawn count=" + shots.size());
                EntityAbyssMissile shot = shots.get(0);
                helper.assertTrue(target.hurts == before && shot.life == 160 && shot.getMissileType() == 1
                        && shot.getPlayerUID() == ship.getPlayerUID(), "Hostile launch mismatch");
                damage(helper, shot, ship.getAttrs().getAttackDamage());
                close(helper, shot.vel0, .8F, "Fixed hostile speed");
                close(helper, shot.accY1, 1.1F, "Fixed hostile acceleration1");
                close(helper, shot.accY2, 1.1F, "Fixed hostile acceleration2");
                Vec3 aim = new Vec3(target.getX(), (float) target.getY() + target.getBbHeight() * .1F, target.getZ());
                velocity(helper, shot, aim.subtract(shot.position()).normalize().scale(.8F));
                shot.discard();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void torpedoMovementUsesEquipmentAndDepthRatherThanClassName(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            List<IShipAttackBase> hosts = new ArrayList<>();
            hosts.add(ship(helper, entities, ModEntities.SS_YO.get()));
            BasicEntityShipHostile enemy = entities.add(ModEntities.SS_U511_MOB.get().create(helper.getLevel()));
            enemy.setPos(helper.absoluteVec(START));
            enemy.setNoAi(true);
            hosts.add(enemy);
            for (IShipAttackBase host : hosts) {
                Entity entity = (Entity) host;
                for (int explicit : new int[]{-1, 0, 1, 2}) {
                    for (double depth : new double[]{0D, 1D, 3D}) {
                        for (float elevation : new float[]{-1F, 4F}) {
                            if (host instanceof BasicEntityShip friendly) friendly.setShipDepth(depth);
                            else ((BasicEntityShipHostile) host).setShipDepth(depth);
                            close(helper, host.getShipDepth(0), depth, "Depth fixture");
                            host.getMissileData(1).movetype = explicit;
                            target.setPos(helper.absoluteVec(TARGET).add(0D, elevation, 0D));
                            int selected = explicit >= 0 ? explicit : depth > 2 ? 0 : depth > 0 && elevation < 0 ? 2 : 1;
                            helper.assertTrue(CombatHelper.calcMissileMoveType(host, target.getY(), 1) == selected, "Move selection");
                            random(entity, 1F);
                            if (host instanceof BasicEntityShip friendly) friendly.attackEntityWithAmmo(target);
                            else ((BasicEntityShipHostile) host).attackEntityWithAmmo(target);
                            List<EntityAbyssMissile> shots = shots(helper, entities, entity);
                            helper.assertTrue(shots.size() == 1, "Movement test missing projectile");
                            EntityAbyssMissile shot = shots.get(0);
                            EntityAbyssMissile expected = new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel());
                            float launch = (float) entity.getY() + entity.getBbHeight() * (selected == 0 ? .3F : .5F);
                            MissileData md = host.getMissileData(1);
                            expected.initMissile(host, 0, selected, 1F, .15F, launch, target.getX(),
                                    (float) target.getY() + (host instanceof BasicEntityShip ? .1F : target.getBbHeight() * .1F),
                                    target.getZ(), 140, .25F, host instanceof BasicEntityShip ? md.vel0 : .8F,
                                    host instanceof BasicEntityShip ? md.accY1 : 1.1F, host instanceof BasicEntityShip ? md.accY2 : 1.1F);
                            helper.assertTrue(shot.moveType == expected.moveType, "Initialization fallback differs");
                            velocity(helper, shot, new Vec3(expected.velX, expected.velY, expected.velZ));
                            shot.discard();
                        }
                    }
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void savedSubmarinesRebuildProfilesAndDoNotChargePerProjectile(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            BasicEntityShip control = ship(helper, entities, ModEntities.SS_KA.get());
            control.getCapaShipInventory().setStackInSlot(0, equipment(5));
            control.calcShipAttributes(31, false);
            for (Hull hull : hulls()) {
                BasicEntityShip source = ship(helper, entities, hull.type());
                source.getCapaShipInventory().setStackInSlot(0, equipment(5));
                source.calcShipAttributes(31, false);
                CompoundTag saved = ShipConsumptionDefaultsGameTests.savedFixture(source, EGG_UID);
                source.getCapaShipInventory().setStackInSlot(0, equipment(5));
                source.calcShipAttributes(31, false);
                source.addAdditionalSaveData(saved);
                helper.assertTrue(source.getMissileData(2).type == 5 && control.getMissileData(2).type == 5,
                        "Saved equipment fixture did not retain gravity ammunition");
                try {
                    for (boolean egg : new boolean[]{false, true}) {
                        BasicEntityShip restored = egg ? ShipConsumptionDefaultsGameTests.hatch(helper, entities, saved)
                                : ship(helper, entities, hull.type());
                        if (!egg) restored.readAdditionalSaveData(saved);
                        profiles(helper, restored, control, hull);
                        helper.assertTrue(source.getOwnerUUID().equals(restored.getOwnerUUID()), "Restored owner changed");
                        restored.setStateMinor(ID.M.NumGrudge, 100_000);
                        restored.setAmmoLight(10_000);
                        restored.setAmmoHeavy(10_000);
                        restored.setPos(helper.absoluteVec(START));
                        restored.setNoAi(true);
                        restored.setShipDepth(0D);
                        random(restored, 1F);
                        var effectId = BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.MOVEMENT_SLOWDOWN);
                        restored.getAttackEffectMap().put(effectId, new ShipAttackEffect(effectId, 1, 40, 100));
                        helper.assertTrue(!restored.getAttackEffectMap().isEmpty(), "Effect fixture ignored");
                        for (boolean heavy : new boolean[]{false, true}) {
                            int ammoIndex = heavy ? ID.M.NumAmmoHeavy : ID.M.NumAmmoLight;
                            int ammo = restored.getStateMinor(ammoIndex);
                            int grudge = restored.getGrudge();
                            int exp = restored.getStateMinor(ID.M.ExpCurrent);
                            int expectedExp = (int) (ConfigHandler.expGain[heavy ? 2 : 1]
                                    * restored.getAttrs().getAttrsBuffed(ID.Attrs.XP));
                            helper.assertTrue(restored.getLevel() < restored.getShipLevelCap(), "Experience fixture is capped");
                            if (heavy) restored.attackEntityWithHeavyAmmo(target);
                            else restored.attackEntityWithAmmo(target);
                            helper.assertTrue(ammo - restored.getStateMinor(ammoIndex) == restored.getAmmoConsumption(),
                                    "Per-projectile ammo charge");
                            int grudgeCost = ConfigHandler.consumeGrudgeAction[heavy ? 1 : 0];
                            helper.assertTrue(grudge - restored.getGrudge() == grudgeCost, "Incorrect grudge accounting");
                            helper.assertTrue(restored.getStateMinor(ID.M.ExpCurrent) - exp == expectedExp,
                                    "Incorrect experience accounting: " + (restored.getStateMinor(ID.M.ExpCurrent) - exp)
                                            + " != " + expectedExp);
                            List<EntityAbyssMissile> shots = shots(helper, entities, restored);
                            helper.assertTrue(shots.size() == (heavy && hull.count() == 1 ? 1 : hull.count()), "Restored shot count");
                            for (EntityAbyssMissile shot : shots) {
                                helper.assertTrue(shot.getPlayerUID() == restored.getPlayerUID()
                                        && shot.effectMap.equals(restored.getAttackEffectMap())
                                        && shot.effectMap != restored.getAttackEffectMap(), "Owner/effect map not detached");
                                shot.discard();
                            }
                        }
                    }
                } finally {
                    ServerDataManager.removeShipData(EGG_UID);
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void dualMissileTrailDelayReachesZeroAtFourthUpdate(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            for (Hull hull : hulls().subList(3, 5)) {
                BasicEntityShip ship = ship(helper, entities, hull.type());
                for (int entry = 0; entry < 3; entry++) {
                    for (int type : new int[]{0, 2}) {
                        for (float speed : new float[]{.3F, .5F, .8F}) {
                            MissileData md = ship.getMissileData(entry == 0 ? 1 : 2);
                            md.type = type;
                            md.movetype = 0;
                            md.vel0 = speed;
                            if (entry == 0) ship.attackEntityWithAmmo(target);
                            else if (entry == 1) ship.attackEntityWithHeavyAmmo(target);
                            else ship.attackEntityWithHeavyAmmo(target.blockPosition());
                            List<EntityAbyssMissile> shots = shots(helper, entities, ship);
                            helper.assertTrue(!shots.isEmpty(), "No projectile for visual observation");
                            for (EntityAbyssMissile shot : shots) {
                                Vec3 origin = shot.position();
                                Vec3 velocity = new Vec3(shot.velX, shot.velY, shot.velZ);
                                for (int tick = 1; tick <= 4; tick++) {
                                    shot.tickCount++;
                                    shot.tick();
                                    helper.assertTrue(shot.getTrailDelay().remainingTicks() == 4 - tick, "Visual remaining count");
                                    helper.assertTrue(emissionAllowed(shot) == (tick == 4), "Premature trail/head candidate at update " + tick);
                                    helper.assertTrue(!shot.isInvisible() && !shot.isRemoved() && shot.life == 160, "Delay changed model/life");
                                    close(helper, shot.position().distanceTo(origin.add(velocity.scale(tick))), 0D, "Delay changed movement");
                                }
                                shot.discard();
                            }
                        }
                    }
                }
            }
            BasicEntityShip normal = ship(helper, entities, ModEntities.SS_KA.get());
            normal.attackEntityWithHeavyAmmo(target);
            helper.assertTrue(emissionAllowed(shots(helper, entities, normal).get(0)), "Ordinary missile gained a delay");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void missileSpawnSchemaPreservesRemainingDelayForLateTracking(GameTestHelper helper) {
        scene(helper, (entities, target) -> {
            BasicEntityShip ship = ship(helper, entities, ModEntities.SS_HIME.get());
            ship.setPlayerUID(137);
            ship.getMissileData(2).type = 2;
            ship.getMissileData(2).movetype = 3;
            ship.attackEntityWithHeavyAmmo(target);
            EntityAbyssMissile shot = shots(helper, entities, ship).get(0);
            shot.t0 = 7.25D;
            shot.t1 = 11.5D;
            helper.assertTrue(shot.getPlayerUID() == 137 && shot.getMissileType() == 2 && shot.moveType == 3,
                    "Tracking fixture lost owner/type/movement");
            for (int age = 0; age <= 5; age++) {
                if (age > 0) {
                    shot.tickCount++;
                    shot.tick();
                }
                if (age == 2) continue;
                roundTrip(helper, shot, Math.max(4 - age, 0));
            }
            BasicEntityShip ordinary = ship(helper, entities, ModEntities.SS_KA.get());
            ordinary.attackEntityWithHeavyAmmo(target);
            roundTrip(helper, shots(helper, entities, ordinary).get(0), 0);
            FriendlyByteBuf valid = new FriendlyByteBuf(Unpooled.buffer());
            try {
                shot.writeSpawnData(valid);
                byte[] bytes = new byte[valid.readableBytes()];
                valid.getBytes(0, bytes);
                for (int cut = 0; cut < bytes.length; cut++) reject(helper, java.util.Arrays.copyOf(bytes, cut));
                for (int index : new int[]{0, 4, bytes.length - 1}) {
                    byte[] invalid = bytes.clone();
                    invalid[index] = index == 4 ? (byte) 2 : (byte) 9;
                    reject(helper, invalid);
                }
                FriendlyByteBuf negative = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    negative.writeBytes(bytes, 0, bytes.length - 1);
                    negative.writeVarInt(-1);
                    byte[] invalid = new byte[negative.readableBytes()];
                    negative.readBytes(invalid);
                    reject(helper, invalid);
                } finally { negative.release(); }
            } finally { valid.release(); }
            shot.discard();
            ship.attackEntityWithHeavyAmmo(target);
            EntityAbyssMissile delayed = shots(helper, entities, ship).get(0);
            helper.assertTrue(delayed.getTrailDelay().remainingTicks() == 4, "NBT reset fixture has no wait");
            CompoundTag saved = new CompoundTag();
            delayed.addAdditionalSaveData(saved);
            delayed.readAdditionalSaveData(saved);
            roundTrip(helper, delayed, 0);
        });
    }

    private static void roundTrip(GameTestHelper helper, EntityAbyssMissile shot, int remaining) {
        var packet = shot.getAddEntityPacket();
        helper.assertTrue(packet instanceof ClientboundCustomPayloadPacket,
                "Tracking selected " + packet.getClass().getSimpleName() + " instead of Forge SpawnEntity");
        ClientboundCustomPayloadPacket outgoing = (ClientboundCustomPayloadPacket) packet;
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf again = new FriendlyByteBuf(Unpooled.buffer());
        try {
            outgoing.write(wire);
            ClientboundCustomPayloadPacket incoming = new ClientboundCustomPayloadPacket(wire);
            FriendlyByteBuf payload = incoming.getInternalData();
            try {
                helper.assertTrue(incoming.getIdentifier().equals(new ResourceLocation("fml", "play")), "Wrong spawn channel");
                helper.assertTrue(payload.readVarInt() == 0, "Tracking did not encode Forge SpawnEntity");
                PlayMessages.SpawnEntity spawn = PlayMessages.SpawnEntity.decode(payload);
                try {
                    helper.assertTrue(!payload.isReadable(), "Spawn packet has unread bytes");
                    helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.byId(spawn.getTypeId()) == shot.getType()
                            && spawn.getEntityId() == shot.getId() && spawn.getUuid().equals(shot.getUUID()), "Spawn identity changed");
                    close(helper, spawn.getPosX(), shot.getX(), "Spawn X");
                    close(helper, spawn.getPosY(), shot.getY(), "Spawn Y");
                    close(helper, spawn.getPosZ(), shot.getZ(), "Spawn Z");
                    assertSpawnSnapshot(helper, shot, spawn, remaining, again);
                } finally { spawn.getAdditionalData().release(); }
            } finally { payload.release(); }
        } finally { outgoing.getInternalData().release(); wire.release(); again.release(); }
    }

    private static void assertSpawnSnapshot(GameTestHelper helper, EntityAbyssMissile shot,
                                           PlayMessages.SpawnEntity spawn, int remaining, FriendlyByteBuf again) {
        FriendlyByteBuf buffer = spawn.getAdditionalData();
        helper.assertTrue(buffer.getInt(0) == 0x53434D53, "Observed spawn payload has no versioned missile schema");
        byte[] original = new byte[buffer.readableBytes()];
        buffer.getBytes(0, original);
        helper.assertTrue(buffer.readInt() == 0x53434D53 && buffer.readVarInt() == 1, "Magic/schema mismatch");
        helper.assertTrue(buffer.readVarInt() == shot.getMissileType() && buffer.readVarInt() == shot.moveType
                && buffer.readVarInt() == shot.life && buffer.readVarInt() == shot.getPlayerUID(), "Spawn integer fields");
        for (double value : new double[]{shot.velX, shot.velY, shot.velZ, shot.vel0, shot.accY1, shot.accY2, shot.t0, shot.t1}) {
            helper.assertTrue(Double.compare(buffer.readDouble(), value) == 0, "Spawn motion field changed");
        }
        helper.assertTrue(buffer.readVarInt() == remaining && !buffer.isReadable(), "Tracking did not snapshot remaining delay");
        buffer.readerIndex(0);
        // Exercise the receive-side factory and callback without loading client-only classes on a dedicated server.
        Entity received = BuiltInRegistries.ENTITY_TYPE.byId(spawn.getTypeId()).customClientSpawn(spawn, helper.getLevel());
        helper.assertTrue(received instanceof EntityAbyssMissile, "Spawn factory did not create a missile");
        EntityAbyssMissile copy = (EntityAbyssMissile) received;
        copy.readSpawnData(buffer);
        helper.assertTrue(!buffer.isReadable() && copy.getTrailDelay().remainingTicks() == remaining,
                "Receive callback did not restore the delay snapshot");
        helper.assertTrue(copy.tickCount == 0, "Spawn codec changed entity age");
        copy.writeSpawnData(again);
        byte[] restored = new byte[again.readableBytes()];
        again.readBytes(restored);
        helper.assertTrue(java.util.Arrays.equals(original, restored), "Spawn round-trip changed fields");
        helper.assertTrue(emissionAllowed(copy) == (remaining == 0), "Tracking reset the visual wait");
    }

    private static void reject(GameTestHelper helper, byte[] bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        boolean rejected = false;
        try {
            new EntityAbyssMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel()).readSpawnData(buffer);
        } catch (RuntimeException expected) { rejected = true; }
        finally { buffer.release(); }
        helper.assertTrue(rejected, "Malformed spawn payload accepted");
    }

    private static boolean emissionAllowed(EntityAbyssMissile shot) {
        return shot.getTrailDelay().allowsEmission();
    }

    private static void scene(GameTestHelper helper, BiConsumer<GameTestEntities, RecordingTarget> body) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                RecordingTarget target = entities.add(new RecordingTarget(helper.getLevel()));
                target.setPos(helper.absoluteVec(TARGET));
                body.accept(entities, target);
                helper.succeed();
            }
        }, START, TARGET, ShipConsumptionDefaultsGameTests.SPAWN);
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities, EntityType<? extends BasicEntityShip> type) {
        BasicEntityShip ship = entities.add(type.create(helper.getLevel()));
        helper.assertTrue(ship != null, "Ship creation failed");
        ship.setNoAi(true);
        ship.setInvulnerable(true);
        ship.setNoGravity(true);
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setAmmoLight(10_000);
        ship.setAmmoHeavy(10_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setPos(helper.absoluteVec(START));
        ship.setShipDepth(0D);
        ship.setShipLevel(37, false);
        ship.setStateMinor(ID.M.ExpCurrent, 0);
        ship.setStateMinor(ID.M.ExpNext, 1_000_000);
        ship.calcShipAttributes(31, false);
        ship.setHealth(ship.getMaxHealth());
        helper.assertTrue(ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel) && ship.hasAmmoLight() && ship.hasAmmoHeavy(), "Resources ignored");
        helper.assertTrue(ship.getStateMinor(ID.M.ExpNext) == 1_000_000, "Level-up guard fixture ignored");
        random(ship, 1F);
        return ship;
    }

    private static void random(Entity entity, float value) {
        try {
            Field field = Entity.class.getDeclaredField("random");
            field.setAccessible(true);
            field.set(entity, new LegacyRandomSource(42L) {
                @Override public float nextFloat() { return value; }
            });
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("Cannot install random: " + failure);
        }
    }

    private static List<EntityAbyssMissile> shots(GameTestHelper helper, GameTestEntities entities, Entity host) {
        List<EntityAbyssMissile> result = new ArrayList<>();
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof EntityAbyssMissile shot && shot.getHostEntity() == host && !shot.isRemoved()) {
                result.add(entities.add(shot));
            }
        }
        return result;
    }

    private static ItemStack equipment(int variant) {
        if (variant < 0) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ModItems.EQUIP_AMMO.get());
        BasicEquip.setEquipMeta(stack, variant);
        return stack;
    }

    private static void profiles(GameTestHelper helper, BasicEntityShip ship, BasicEntityShip control, Hull hull) {
        for (int channel = 0; channel < 5; channel++) {
            MissileData expected = control.getMissileData(channel);
            MissileData actual = ship.getMissileData(channel);
            helper.assertTrue(actual.type == expected.type && actual.movetype == expected.movetype, "Equipment type/move changed");
            close(helper, actual.vel0, expected.vel0 + (channel == 1 ? hull.speed() : 0F), hull.type() + " speed ch=" + channel);
            close(helper, actual.accY1, expected.accY1 + (channel == 1 ? hull.acceleration() : 0F), "Acceleration 1");
            close(helper, actual.accY2, expected.accY2 + (channel == 1 ? hull.acceleration() : 0F), "Acceleration 2");
        }
    }

    private static void damage(GameTestHelper helper, EntityAbyssMissile shot, float expected) {
        for (int attribute : new int[]{ID.Attrs.ATK_L, ID.Attrs.ATK_H, ID.Attrs.ATK_AL, ID.Attrs.ATK_AH}) {
            close(helper, shot.getAttrs().getAttrsBuffed(attribute), expected, "Projectile damage");
        }
        close(helper, shot.getAttrs().getAttrsBuffed(ID.Attrs.KB), .15F, "Knockback");
    }

    private static void velocity(GameTestHelper helper, EntityAbyssMissile shot, Vec3 expected) {
        close(helper, shot.velX, expected.x, "Velocity X");
        close(helper, shot.velY, expected.y, "Velocity Y");
        close(helper, shot.velZ, expected.z, "Velocity Z");
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 0.0001D, label + ": " + actual + " != " + expected);
    }

    private static final class RecordingTarget extends Cow {
        int hurts;
        RecordingTarget(Level level) { super(EntityType.COW, level); }
        @Override public boolean hurt(DamageSource source, float damage) { this.hurts++; return false; }
    }
}
