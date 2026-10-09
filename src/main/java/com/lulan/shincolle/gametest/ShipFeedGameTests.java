package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.InteractHelper;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipFeedGameTests {
    private ShipFeedGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ammoVariantsFeedTheirOwnCaliberAndSyncUpdatedState(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TestShip ship = newShip(helper, entities);
            FakePlayer player = player(helper);
            Item[] variants = {ModItems.AMMO.get(), ModItems.AMMO_1.get(),
                    ModItems.AMMO_2.get(), ModItems.AMMO_3.get()};
            int[] minimum = {30, 270, 15, 135};
            int[] maximum = {39, 359, 19, 179};
            for (int i = 0; i < variants.length; i++) {
                ItemStack stack = new ItemStack(variants[i], 2);
                stack.getOrCreateTag().putInt("ItemMeta", (i + 1) % 4);
                int light = ship.getStateMinor(ID.M.NumAmmoLight);
                int heavy = ship.getStateMinor(ID.M.NumAmmoHeavy);
                int syncs = ship.syncs;
                int morale = ship.getMorale();
                if (!InteractHelper.interactFeed(ship, player, stack) || stack.getCount() != 1) {
                    throw new AssertionError("Ammo variant " + i + " was not consumed once");
                }
                int lightGain = ship.getStateMinor(ID.M.NumAmmoLight) - light;
                int heavyGain = ship.getStateMinor(ID.M.NumAmmoHeavy) - heavy;
                float multiplier = ship.getAttrs().getAttrsBuffed(ID.Attrs.AMMO);
                int low = (int) (minimum[i] * multiplier);
                int high = (int) (maximum[i] * multiplier);
                int gain = i < 2 ? lightGain : heavyGain;
                if (gain < low || gain > high || (i < 2 ? heavyGain : lightGain) != 0) {
                    throw new AssertionError("Ammo variant " + i + " gained light=" + lightGain
                            + ", heavy=" + heavyGain + ", expected " + low + ".." + high);
                }
                if (ship.syncs != syncs + 1) {
                    throw new AssertionError("Successful feed must send one minor sync");
                }
                assertMinorPayload(ship, morale);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rationVariantsCleanDebuffsOnlyForIceCreamIncludingAutoUse(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TestShip ship = newShip(helper, entities);
            FakePlayer player = player(helper);
            Item[] variants = {ModItems.COMBAT_RATION.get(), ModItems.COMBAT_RATION_1.get(),
                    ModItems.COMBAT_RATION_2.get(), ModItems.COMBAT_RATION_3.get(),
                    ModItems.COMBAT_RATION_4.get(), ModItems.COMBAT_RATION_5.get()};
            for (int i = 0; i < variants.length; i++) {
                ship.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
                ItemStack stack = new ItemStack(variants[i], 2);
                stack.getOrCreateTag().putInt("ItemMeta", i >= 4 ? 0 : 4);
                int syncs = ship.syncs;
                if (!InteractHelper.interactFeed(ship, player, stack) || stack.getCount() != 1
                        || ship.hasEffect(MobEffects.POISON) != (i < 4) || ship.syncs != syncs + 1) {
                    throw new AssertionError("Incorrect ration effect or consumption for variant " + i);
                }
                ship.removeEffect(MobEffects.POISON);
            }
            ship.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            ship.getCapaShipInventory().setStackInSlot(6, new ItemStack(ModItems.COMBAT_RATION_5.get()));
            int syncs = ship.syncs;
            ship.feedFromInventory();
            if (ship.hasEffect(MobEffects.POISON) || ship.syncs != syncs + 1
                    || !ship.getCapaShipInventory().getStackInSlot(6).isEmpty()) {
                throw new AssertionError("Automatic ice cream feed did not clean debuffs, sync and consume");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fullShipRejectsFoodWithoutConsumingOrSyncing(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TestShip ship = newShip(helper, entities);
            ship.setFoodSaturation(ship.getFoodSaturationMax());
            ItemStack stack = new ItemStack(ModItems.AMMO_3.get(), 2);
            if (InteractHelper.interactFeed(ship, player(helper), stack) || stack.getCount() != 2
                    || ship.syncs != 0 || ship.getStateMinor(ID.M.NumAmmoHeavy) != 0) {
                throw new AssertionError("Full ship accepted, consumed, or synced rejected food");
            }
            helper.succeed();
        }
    }

    private static TestShip newShip(GameTestHelper helper, GameTestEntities entities) {
        TestShip ship = entities.add(new TestShip(ModEntities.BB_KONGOU.get(), helper.getLevel()));
        ship.setFoodSaturationMax(100);
        ship.setFoodSaturation(0);
        ship.setStateMinor(ID.M.NumAmmoLight, 0);
        ship.setStateMinor(ID.M.NumAmmoHeavy, 0);
        return ship;
    }

    private static FakePlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.fromString("00000000-0000-0000-0000-000000000193"), "ship_feed_test"));
    }

    private static void assertMinorPayload(TestShip ship, int previousMorale) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(ship.lastPayload));
        buf.skipBytes(3 * Integer.BYTES);
        int light = buf.readInt();
        int heavy = buf.readInt();
        int grudge = buf.readInt();
        if (light != ship.getStateMinor(ID.M.NumAmmoLight)
                || heavy != ship.getStateMinor(ID.M.NumAmmoHeavy)
                || grudge != ship.getStateMinor(ID.M.NumGrudge)
                || ship.getMorale() <= previousMorale) {
            throw new AssertionError("Minor packet did not reflect the fed ship state");
        }
    }

    private static final class TestShip extends EntityBBKongou {
        private int syncs;
        private byte[] lastPayload;

        private TestShip(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        @Override
        public void sendSyncPacketMinor() {
            syncs++;
            lastPayload = S2CEntitySyncPacket.syncMinor(this).getPayload();
        }

        private void feedFromInventory() {
            useCombatRation();
        }
    }
}
