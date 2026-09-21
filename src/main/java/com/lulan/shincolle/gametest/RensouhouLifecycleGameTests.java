package com.lulan.shincolle.gametest;

import com.mojang.authlib.GameProfile;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.entity.other.EntityRensouhouMob;
import com.lulan.shincolle.entity.other.EntityRensouhouS;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RensouhouLifecycleGameTests {

    private RensouhouLifecycleGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void initializationRetainsAssignedTargetForEveryVariant(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper);
             FriendlyHost fixture = friendlyHost(helper, entities)) {
            BasicEntityShip friendly = fixture.ship();
            BasicEntityShipHostile hostile = hostileHost(helper, entities);
            Cow target = entities.add(EntityType.COW.create(helper.getLevel()));
            helper.assertTrue(target != null, "Failed to create target");

            EntityRensouhou normal = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
            EntityRensouhouS deep = entities.add(ModEntities.RENSOUHOU_S.get().create(helper.getLevel()));
            EntityRensouhouMob mob = entities.add(ModEntities.RENSOUHOU_MOB.get().create(helper.getLevel()));
            helper.assertTrue(normal != null && deep != null && mob != null, "Failed to create rensouhou variants");

            normal.initAttrs(friendly, target, 0);
            deep.initAttrs(friendly, target, 0);
            mob.initAttrs(hostile, target, 0);

            helper.assertTrue(normal.getTarget() == target, "Normal rensouhou lost its assigned target");
            helper.assertTrue(deep.getTarget() == target, "Deep rensouhou lost its assigned target");
            helper.assertTrue(mob.getTarget() == target, "Hostile rensouhou lost its assigned target");
            helper.succeed();
        }
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public static void assignedTargetIsApproachedAndAttacked(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        FriendlyHost fixture = friendlyHost(helper, entities);
        BasicEntityShip host = fixture.ship();
        host.moveTo(helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D)));
        Cow target = entities.add(EntityType.COW.create(helper.getLevel()));
        EntityRensouhou summon = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
        helper.assertTrue(target != null && summon != null, "Failed to create movement fixture");
        target.moveTo(helper.absoluteVec(new Vec3(10.5D, 2D, 2.5D)));
        target.setNoAi(true);
        target.setInvulnerable(true);
        summon.initAttrs(host, target, 0);
        double initialDistance = summon.distanceToSqr(target);
        helper.assertTrue(helper.getLevel().addFreshEntity(target), "Failed to add target");
        helper.assertTrue(helper.getLevel().addFreshEntity(summon), "Failed to add rensouhou");

        helper.runAtTickTime(100, () -> {
            try (entities; fixture) {
                helper.assertTrue(summon.distanceToSqr(target) < initialDistance - 1D,
                        "Rensouhou did not approach its assigned target");
                helper.assertTrue(summon.getNumAmmoLight() < 6,
                        "Rensouhou approached but never attacked its assigned target");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deadTargetIsReplacedFromHost(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper);
             FriendlyHost fixture = friendlyHost(helper, entities)) {
            BasicEntityShip host = fixture.ship();
            Cow original = entities.add(EntityType.COW.create(helper.getLevel()));
            Cow replacement = entities.add(EntityType.COW.create(helper.getLevel()));
            EntityRensouhou summon = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
            helper.assertTrue(original != null && replacement != null && summon != null,
                    "Failed to create retarget fixture");
            summon.initAttrs(host, original, 0);
            host.setEntityTarget(replacement);
            original.setHealth(0F);

            summon.tick();

            helper.assertTrue(summon.getTarget() == replacement,
                    "Rensouhou did not inherit the host's replacement target");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void targetLossRefundsOnlyUnusedHostAmmo(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper);
             FriendlyHost fixture = friendlyHost(helper, entities)) {
            BasicEntityShip host = fixture.ship();
            Cow target = entities.add(EntityType.COW.create(helper.getLevel()));
            EntityRensouhou summon = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
            helper.assertTrue(target != null && summon != null, "Failed to create refund fixture");
            host.setStateMinor(ID.M.NumAmmoLight, 0);
            host.setEntityTarget(null);
            summon.initAttrs(host, target, 0);
            target.setHealth(0F);

            summon.tick();

            int expected = 4 * host.getAmmoConsumption();
            helper.assertTrue(summon.isRemoved(), "Targetless rensouhou must be removed");
            helper.assertTrue(host.getStateMinor(ID.M.NumAmmoLight) == expected,
                    "Refund must exclude the two bonus shots: expected=" + expected
                            + " actual=" + host.getStateMinor(ID.M.NumAmmoLight));
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void finalShotRemovesSummon(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper);
             FriendlyHost fixture = friendlyHost(helper, entities)) {
            BasicEntityShip host = fixture.ship();
            Cow target = entities.add(EntityType.COW.create(helper.getLevel()));
            EntityRensouhou summon = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
            helper.assertTrue(target != null && summon != null, "Failed to create ammo fixture");
            summon.initAttrs(host, target, 0);
            summon.setNumAmmoLight(1);

            summon.attackTarget(target);

            helper.assertTrue(summon.getNumAmmoLight() == 0, "Final attack must consume the last shot");
            helper.assertTrue(summon.isRemoved(), "Rensouhou must be removed after its final shot");
            helper.succeed();
        }
    }

    private static FriendlyHost friendlyHost(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip host = entities.add(ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()));
        helper.assertTrue(host != null, "Failed to create friendly host");
        ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rensouhou_host"));
        helper.getLevel().addNewPlayer(owner);
        host.tame(owner);
        host.setOwnerUUID(owner.getUUID());
        host.setStateMinor(ID.M.NumGrudge, 100_000);
        host.setStateFlag(ID.F.NoFuel, false);
        host.calcShipAttributes(31, false);
        return new FriendlyHost(host, owner);
    }

    private static BasicEntityShipHostile hostileHost(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShipHostile host = entities.add(ModEntities.DESTROYER_SHIMAKAZE_MOB.get().create(helper.getLevel()));
        helper.assertTrue(host != null, "Failed to create hostile host");
        host.setStateFlag(ID.F.NoFuel, false);
        host.calcShipAttributes(31, false);
        return host;
    }

    private record FriendlyHost(BasicEntityShip ship, ServerPlayer owner) implements AutoCloseable {

        @Override
        public void close() {
            this.owner.serverLevel().removePlayerImmediately(this.owner, RemovalReason.DISCARDED);
        }
    }
}
