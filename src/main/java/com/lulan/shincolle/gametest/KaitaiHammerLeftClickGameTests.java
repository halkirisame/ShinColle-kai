package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Left clicking one's own ship with the Kaitai Hammer kills it in one hit and costs one durability
 * outside creative mode; a creative player pays nothing, and a stranger's ship is left alone.
 */
@GameTestHolder(com.lulan.shincolle.reference.Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KaitaiHammerLeftClickGameTests {
    private static final Vec3 SHIP = new Vec3(12.5D, 0D, 2.5D);

    private KaitaiHammerLeftClickGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_kaitai_hammer_owner", timeoutTicks = 100)
    public static void hammerLeftClickKillsOwnShipAndWearsHammer(GameTestHelper helper) {
        run(helper, true, false, true, 1);
    }

    @GameTest(template = "arena", batch = "isolated_kaitai_hammer_creative", timeoutTicks = 100)
    public static void hammerLeftClickInCreativeKillsShipWithoutWear(GameTestHelper helper) {
        run(helper, true, true, true, 0);
    }

    @GameTest(template = "arena", batch = "isolated_kaitai_hammer_stranger", timeoutTicks = 100)
    public static void hammerLeftClickLeavesStrangersShipAlone(GameTestHelper helper) {
        run(helper, false, false, false, 0);
    }

    @GameTest(template = "arena", batch = "isolated_kaitai_mount_owner", timeoutTicks = 100)
    public static void hammerAttackOnOwnMountKillsHostAndWearsHammer(GameTestHelper helper) {
        run(helper, true, false, true, 1, true);
    }

    @GameTest(template = "arena", batch = "isolated_kaitai_mount_creative", timeoutTicks = 100)
    public static void hammerAttackOnMountInCreativeKillsHostWithoutWear(GameTestHelper helper) {
        run(helper, true, true, true, 0, true);
    }

    @GameTest(template = "arena", batch = "isolated_kaitai_mount_stranger", timeoutTicks = 100)
    public static void hammerAttackLeavesStrangersMountAndHostAlone(GameTestHelper helper) {
        run(helper, false, false, false, 0, true);
    }

    @GameTest(template = "arena", batch = "isolated_kaitai_mount_orphan", timeoutTicks = 100)
    public static void hammerAttackOnUnresolvedMountDoesNotHitOrWear(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_HBH.get().create(helper.getLevel()));
            helper.assertTrue(mount != null, "fixture must create a mount");
            mount.moveTo(helper.absoluteVec(SHIP));
            helper.assertTrue(helper.getLevel().addFreshEntity(mount), "fixture mount must be added");
            helper.assertTrue(mount.getHost() == null, "fixture host must be unresolved");
            FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "kaitai_orphan_player"));
            ItemStack hammer = new ItemStack(ModItems.KAITAI_HAMMER.get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hammer);
            float health = mount.getHealth();
            player.attack(mount);
            helper.assertTrue(mount.getHealth() == health, "an unresolved mount must not receive a vanilla hit");
            helper.assertTrue(hammer.getDamageValue() == 0, "an unresolved host must not cost durability");
            helper.succeed();
        }, SHIP);
    }

    private static void run(GameTestHelper helper, boolean owned, boolean creative, boolean expectDead,
                            int expectedWear) {
        run(helper, owned, creative, expectDead, expectedWear, false);
    }

    private static void run(GameTestHelper helper, boolean owned, boolean creative, boolean expectDead,
                            int expectedWear, boolean mounted) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship;
            BasicEntityMount mount = null;
            if (mounted) {
                ship = entities.add(ModEntities.HARBOUR_HIME.get().create(helper.getLevel()));
                helper.assertTrue(ship != null, "fixture must create Harbour Hime");
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setNoAi(true);
                ship.moveTo(helper.absoluteVec(SHIP));
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "fixture ship must be added");
                mount = entities.add(ModEntities.MOUNT_HBH.get().create(helper.getLevel()));
                helper.assertTrue(mount != null, "fixture must create HbH");
                mount.moveTo(ship.position());
                helper.assertTrue(helper.getLevel().addFreshEntity(mount), "fixture mount must be added");
                mount.setHost(ship);
                helper.assertTrue(ship.startRiding(mount, true), "fixture ship must ride its mount");
                helper.assertTrue(mount.getHost() == ship, "fixture mount must resolve its host");
            } else {
                ship = MovementPlanParityGameTests.friendly(helper, entities, SHIP);
            }
            ship.setInvulnerable(false);
            ship.setHealth(ship.getMaxHealth());

            FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "kaitai_hammer_player"));
            int uid = 40_000 + Math.floorMod(ship.getUUID().hashCode(), 100_000);
            player.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing player capability"))
                    .setPlayerUID(owned ? uid : uid + 1);
            ship.setPlayerUID(uid);
            ship.setOwnerUUID(owned ? player.getUUID() : UUID.randomUUID());
            player.getAbilities().instabuild = creative;

            ItemStack hammer = new ItemStack(ModItems.KAITAI_HAMMER.get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hammer);
            helper.assertTrue(hammer.getDamageValue() == 0, "the hammer must start unworn");

            float shipHealth = ship.getHealth();
            if (mounted) {
                float mountHealth = mount.getHealth();
                player.attack(mount);
                helper.assertTrue(mount.getHealth() == mountHealth, "the mount must not receive a vanilla hit");
            } else {
                boolean consumed = hammer.getItem().onLeftClickEntity(hammer, player, ship);
                helper.assertTrue(consumed, "the click on a ship must be consumed so no vanilla hit lands");
            }
            helper.assertTrue(ship.isDeadOrDying() == expectDead,
                    "ship dead=" + ship.isDeadOrDying() + ", expected " + expectDead);
            if (!expectDead) {
                helper.assertTrue(ship.getHealth() == shipHealth, "a stranger's ship must not take damage");
            }
            helper.assertTrue(hammer.getDamageValue() == expectedWear,
                    "hammer wear=" + hammer.getDamageValue() + ", expected " + expectedWear);
            helper.succeed();
        }, SHIP);
    }
}
