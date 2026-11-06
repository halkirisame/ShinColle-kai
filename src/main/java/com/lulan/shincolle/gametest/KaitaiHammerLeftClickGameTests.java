package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
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

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void hammerShipDeathEggKeepsLevelAndHatches(GameTestHelper helper) {
        deathEggLevel(helper, false, 10, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void hammerMountDeathEggKeepsLevelAndHatches(GameTestHelper helper) {
        deathEggLevel(helper, true, 10, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void hammerShipDeathEggKeepsMinimumLevelAndHatches(GameTestHelper helper) {
        deathEggLevel(helper, false, 1, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void hammerMountDeathEggKeepsMinimumLevelAndHatches(GameTestHelper helper) {
        deathEggLevel(helper, true, 1, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ordinaryDeathEggKeepsLevelAndHatches(GameTestHelper helper) {
        deathEggLevel(helper, false, 10, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void ordinaryDeathEggKeepsMinimumLevelAndHatches(GameTestHelper helper) {
        deathEggLevel(helper, false, 1, true);
    }

    private static void deathEggLevel(GameTestHelper helper, boolean mounted, int level, boolean ordinaryDamage) {
        int expectedLevel = level;
        Vec3 deathPos = new Vec3(2.5D, 2D, 2.5D);
        Vec3 hatchPos = new Vec3(2.5D, 2D, 5.5D);
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = entities.add(ModEntities.HARBOUR_HIME.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "fixture must create Harbour Hime");
            ship.moveTo(helper.absoluteVec(deathPos));
            ship.setNoGravity(true);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.CanDrop, true);
            ship.setStateMinor(ID.M.ShipLevel, level);
            ship.getAttrs().setAttrsBonus(ID.AttrsBase.HP, 2);
            ship.setStateMinor(ID.M.ExpCurrent, 7);
            ship.setStateFlag(ID.F.IsMarried, true);
            ship.calcShipAttributes(31, true);
            ship.setHealth(ship.getMaxHealth());
            ship.setInvulnerable(false);
            ship.getCapaShipInventory().setStackInSlot(CapaShipInventory.EquipSlots,
                    new ItemStack(Items.DIAMOND, 7));
            GameTestEntities.removeAllGoalsEachTick(helper, ship);
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "fixture ship must be added");
            helper.assertTrue(ship.getLevel() == level && ship.getStateMinor(ID.M.NumGrudge) > 0
                            && !ship.getStateFlag(ID.F.NoFuel) && ship.getStateFlag(ID.F.CanDrop),
                    "fixture must have the requested level, fuel and death drop enabled");

            FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "kaitai_egg_player"));
            int uid = 40_000 + Math.floorMod(ship.getUUID().hashCode(), 100_000);
            player.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing player capability")).setPlayerUID(uid);
            ship.setPlayerUID(uid);
            ship.setOwnerUUID(player.getUUID());
            ship.ownerName = player.getGameProfile().getName();
            player.moveTo(helper.absoluteVec(deathPos).add(5D, 0D, 0D));
            helper.getLevel().addNewPlayer(player);
            GameTestEntities.onCompletion(helper,
                    () -> helper.getLevel().removePlayerImmediately(player,
                            net.minecraft.world.entity.Entity.RemovalReason.DISCARDED),
                    "death egg owner cleanup");
            helper.assertTrue(ship.getOwner() == player && ship.getPlayerUID() == uid
                            && player.getUUID().equals(ship.getOwnerUUID()),
                    "fixture owner must resolve to the registered admiral");
            helper.assertTrue(ship.getHealth() > 0F && !ship.isInvulnerable()
                            && ship.getStateMinor(ID.M.ExpCurrent) == 7 && ship.getStateFlag(ID.F.IsMarried)
                            && ship.getCapaShipInventory().getStackInSlot(CapaShipInventory.EquipSlots)
                                    .is(Items.DIAMOND),
                    "fixture must be alive, vulnerable, married and carry experience and inventory");
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(ship.blockPosition())
                            && helper.getLevel().isPositionEntityTicking(BlockPos.containing(
                                    helper.absoluteVec(hatchPos))),
                    "death and hatch positions must tick");
            GameTestEntities.assertRegistered(helper, ship);
            player.getAbilities().instabuild = false;
            ItemStack hammer = new ItemStack(ModItems.KAITAI_HAMMER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, hammer);

            CompoundTag saved = new CompoundTag();
            CapaShipSavedValues.saveNBTData(saved, ship);
            helper.assertTrue(saved.getIntArray("StateMinor")[ID.M.ShipLevel] == level,
                    "ordinary saving must not apply the death penalty");
            BasicEntityShip ordinaryLoaded = entities.add(ModEntities.HARBOUR_HIME.get().create(helper.getLevel()));
            helper.assertTrue(ordinaryLoaded != null, "ordinary load fixture must exist");
            CapaShipSavedValues.loadNBTData(saved, ordinaryLoaded);
            helper.assertTrue(ordinaryLoaded.getLevel() == level, "ordinary save/load must retain the level");
            if (ordinaryDamage) {
                helper.assertTrue(ship.hurt(ship.damageSources().magic(), ship.getMaxHealth() * 2F),
                        "ordinary lethal damage must be accepted");
            } else if (mounted) {
                BasicEntityMount mount = entities.add(ModEntities.MOUNT_HBH.get().create(helper.getLevel()));
                helper.assertTrue(mount != null, "fixture must create the mount");
                mount.moveTo(ship.position());
                mount.setNoGravity(true);
                GameTestEntities.removeAllGoalsEachTick(helper, mount);
                helper.assertTrue(helper.getLevel().addFreshEntity(mount), "fixture mount must be added");
                mount.setHost(ship);
                helper.assertTrue(ship.startRiding(mount, true) && mount.getHost() == ship,
                        "fixture must resolve the mounted ship");
                player.attack(mount);
            } else {
                player.attack(ship);
            }
            helper.assertTrue(ship.isDeadOrDying(), "the actual attack must kill the ship");
            helper.assertTrue(hammer.getDamageValue() == (ordinaryDamage ? 0 : 1),
                    "only a hammer attack must wear the hammer once");
            // Skip only the animation wait; the world's next entity tick must create the egg.
            ship.deathTime = ConfigHandler.deathTime() - 1;
            helper.startSequence().thenWaitUntil(() ->
                    helper.assertTrue(ship.isRemoved(), "waiting for natural death completion"))
                    .thenExecute(() -> {
                        List<BasicEntityItem> eggs = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                                new AABB(BlockPos.containing(helper.absoluteVec(deathPos))).inflate(3D),
                                item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
                        for (BasicEntityItem egg : eggs) entities.add(egg);
                        helper.assertTrue(eggs.size() == 1, "death must drop exactly one ship egg");
                        ItemStack egg = eggs.get(0).getEntityItem().copy();
                        CompoundTag tag = egg.getTag();
                        helper.assertTrue(tag != null && tag.getIntArray("StateMinor").length > ID.M.ShipLevel,
                                "death egg must contain saved ship state");
                        int eggLevel = tag.getIntArray("StateMinor")[ID.M.ShipLevel];
                        helper.assertTrue(eggLevel == expectedLevel,
                                "death egg level=" + eggLevel + ", expected " + expectedLevel);
                        helper.assertTrue(ship.getLevel() == level,
                                "saving a death egg must not mutate the dying ship's level");
                        helper.assertTrue(tag.getIntArray("StateMinor")[ID.M.ExpCurrent] == 7
                                        && tag.getIntArray("StateMinor")[ID.M.PlayerUID] == uid
                                        && tag.getByteArray("StateFlag")[ID.F.IsMarried] == 1
                                        && player.getUUID().toString().equals(tag.getString("owner"))
                                        && tag.getCompound(CapaShipInventory.InvName)
                                                .equals(ship.getCapaShipInventory().serializeNBT()),
                                "death egg must retain experience, marriage, owner and inventory");
                        eggs.get(0).discard();

                        BlockPos spawn = BlockPos.containing(helper.absoluteVec(hatchPos));
                        BlockPos floor = spawn.below();
                        int cost = expectedLevel / 3;
                        player.experienceLevel = 0;
                        if (cost > 0) {
                            player.giveExperienceLevels(cost - 1);
                            InteractionResult denied = egg.getItem().useOn(new UseOnContext(helper.getLevel(), player,
                                    InteractionHand.MAIN_HAND, egg,
                                    new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
                            helper.assertTrue(denied == InteractionResult.FAIL && egg.getCount() == 1
                                            && player.experienceLevel == cost - 1
                                            && helper.getLevel().getEntitiesOfClass(BasicEntityShip.class,
                                                    new AABB(spawn)).isEmpty(),
                                    "insufficient XP must not consume the egg, XP or spawn a ship");
                        }
                        player.experienceLevel = 0;
                        player.giveExperienceLevels(cost);
                        helper.assertTrue(player.experienceLevel == cost, "fixture must supply the exact XP cost");
                        InteractionResult result = egg.getItem().useOn(new UseOnContext(helper.getLevel(), player,
                                InteractionHand.MAIN_HAND, egg,
                                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
                        helper.assertTrue(result == InteractionResult.CONSUME, "death egg must hatch");
                        helper.assertTrue(egg.isEmpty() && player.experienceLevel == 0,
                                "hatching must consume one egg and the exact required XP levels");
                        List<BasicEntityShip> hatched = helper.getLevel().getEntitiesOfClass(
                                BasicEntityShip.class, new AABB(spawn));
                        for (BasicEntityShip restored : hatched) entities.add(restored);
                        helper.assertTrue(hatched.size() == 1, "egg must restore exactly one ship");
                        BasicEntityShip restored = hatched.get(0);
                        helper.assertTrue(restored.getLevel() == expectedLevel,
                                "hatching must keep the saved level without adjustment");
                        helper.assertTrue(restored.getStateMinor(ID.M.ExpCurrent) == 7
                                        && restored.getStateFlag(ID.F.IsMarried) && restored.getPlayerUID() == uid
                                        && player.getUUID().equals(restored.getOwnerUUID())
                                        && restored.ownerName.equals(ship.ownerName)
                                        && restored.getCapaShipInventory().serializeNBT()
                                                .equals(tag.getCompound(CapaShipInventory.InvName)),
                                "hatching must retain experience, marriage, ownership and inventory");
                        helper.assertTrue(restored.getStateFlag(ID.F.CanDrop),
                                "the restored ship must be allowed to drop its next egg");
                        helper.assertTrue(restored.getAttrs().getAttrsBonus()[ID.AttrsBase.HP] == 2
                                        && Math.abs(restored.getMaxHealth() - ship.getMaxHealth()) < 0.001F,
                                "hatching must recalculate the saved level and HP bonus");
                        helper.assertTrue(restored.getHealth() == restored.getMaxHealth(),
                                "saved death egg must hatch fully healed: " + restored.getHealth()
                                        + "/" + restored.getMaxHealth());
                        helper.succeed();
                    });
        }, deathPos, hatchPos);
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
