package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.other.EntityAirplane;
import com.lulan.shincolle.entity.hime.EntityNorthernHime;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("shincolle_kai")
@PrefixGameTestTemplate(false)
public final class AirplaneDeathGameTests {
    private AirplaneDeathGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void shotDownAirplaneFallsForNinetyTicks(GameTestHelper helper) {
        verifyDeath(helper, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void shotDownAirplaneDoesNotRefundAfterOwnerLoss(GameTestHelper helper) {
        verifyDeath(helper, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void shotDownAirplaneDoesNotRefundAfterLifetime(GameTestHelper helper) {
        verifyDeath(helper, false, true);
    }

    private static void verifyDeath(GameTestHelper helper, boolean loseOwner, boolean expire) {
        Vec3 at = new Vec3(1D, 40D, 1D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "air_death_owner"));
            owner.setPos(helper.absoluteVec(at));
            helper.getLevel().addNewPlayer(owner);
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                EntityNorthernHime host = entities.add(ModEntities.NORTHERN_HIME.get().create(helper.getLevel()));
                host.setOwnerUUID(owner.getUUID());
                host.setPos(helper.absoluteVec(new Vec3(1D, 2D, 1D)));
                host.setInvulnerable(true);
                host.setStateMinor(ID.M.NumGrudge, 100_000);
                host.setStateFlag(ID.F.NoFuel, false);
                host.setAmmoLight(100);
                host.setAmmoHeavy(100);
                host.setNumAircraftLight(1);
                helper.assertTrue(helper.getLevel().addFreshEntity(host), "Host was not added");
                helper.assertTrue(host.getHostEntity() == owner && host.getGrudge() > 0
                        && !host.getStateFlag(ID.F.NoFuel), "Host fixture was ignored");

                EntityAirplane plane = entities.add(ModEntities.AIRPLANE.get().create(helper.getLevel()));
                plane.setHost(host);
                plane.setNoAi(true);
                plane.setPos(helper.absoluteVec(at));
                plane.tickCount = expire ? 1799 : 40;
                helper.assertTrue(helper.getLevel().addFreshEntity(plane), "Airplane was not added");
                for (int i = 0; i < 2; i++) {
                    plane.invulnerableTime = 0;
                    plane.hurt(plane.damageSources().generic(), plane.getMaxHealth() * 0.5F);
                }
                helper.assertTrue(plane.isDeadOrDying(), "Damage did not start death");
                if (loseOwner) {
                    host.setOwnerUUID(null);
                    helper.assertTrue(host.getHostEntity() == null, "Owner loss fixture was ignored");
                }
                int ammo = host.getStateMinor(ID.M.NumAmmoLight);
                int heavyAmmo = host.getStateMinor(ID.M.NumAmmoHeavy);
                int light = host.getNumAircraftLight();
                int heavy = host.getNumAircraftHeavy();
                double startY = plane.getY();
                for (int tick = 1; tick <= 90; tick++) {
                    ++plane.tickCount;
                    plane.tick();
                    if (tick < 90) {
                        helper.assertTrue(!plane.isRemoved(), "Airplane removed before death tick 90: " + tick);
                    }
                    if (tick == 30) {
                        helper.assertTrue(plane.getY() < startY, "Shot-down airplane did not fall");
                    }
                    helper.assertTrue(host.getStateMinor(ID.M.NumAmmoLight) == ammo
                            && host.getStateMinor(ID.M.NumAmmoHeavy) == heavyAmmo
                            && host.getNumAircraftLight() == light && host.getNumAircraftHeavy() == heavy,
                            "Shot-down airplane refunded resources at death tick " + tick);
                }
                helper.assertTrue(plane.deathTime == 90 && plane.isRemoved()
                        && plane.getRemovalReason() == Entity.RemovalReason.KILLED, "Death did not end at tick 90");
            } finally {
                helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            }
            helper.succeed();
        }, at);
    }
}
