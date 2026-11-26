package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.Values;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BookIconGameTests {
    private BookIconGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "book_icons")
    public static void bookIconsPreserveItemVariantsAndEggNames(GameTestHelper helper) {
        Map<Short, ItemStack> icons = Values.getItemIconMap();
        short[] ammo = {ID.Icon.AmmoL, ID.Icon.AmmoLC, ID.Icon.AmmoH, ID.Icon.AmmoHC};
        Item[] ammoItems = {ModItems.AMMO.get(), ModItems.AMMO_1.get(),
                ModItems.AMMO_2.get(), ModItems.AMMO_3.get()};
        short[] tanks = {ID.Icon.Tank0, ID.Icon.Tank1, ID.Icon.Tank2, ID.Icon.Tank3};
        Item[] tankItems = {ModItems.SHIP_TANK.get(), ModItems.SHIP_TANK_1.get(),
                ModItems.SHIP_TANK_2.get(), ModItems.SHIP_TANK_3.get()};
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(icons.get(ammo[i]).is(ammoItems[i]), "Wrong ammo icon variant " + i);
            helper.assertTrue(icons.get(tanks[i]).is(tankItems[i]), "Wrong tank icon variant " + i);
        }
        ItemStack small = icons.get(ID.Icon.SpawnEgg0);
        ItemStack large = icons.get(ID.Icon.SpawnEgg1);
        ItemStack destroyer = icons.get(ID.Icon.SpawnEgg2);
        CompoundTag smallTag = small.getTag();
        CompoundTag largeTag = large.getTag();
        CompoundTag shipTag = destroyer.getTag();
        helper.assertTrue(smallTag != null && smallTag.contains("BuildType", Tag.TAG_BYTE)
                && smallTag.getByte("BuildType") == 0, "Small construction egg needs explicit BuildType 0");
        helper.assertTrue(largeTag != null && largeTag.contains("BuildType", Tag.TAG_BYTE)
                && largeTag.getByte("BuildType") == 1, "Large construction egg needs explicit BuildType 1");
        helper.assertTrue(shipTag != null && shipTag.contains(ShipSpawnEgg.TAG_SHIP_CLASS, Tag.TAG_INT)
                && shipTag.getInt(ShipSpawnEgg.TAG_SHIP_CLASS) == 0, "Destroyer egg needs explicit ShipClass 0");
        helper.assertTrue("item.shincolle_kai.small_egg".equals(small.getItem().getDescriptionId(small)),
                "Small egg display name must describe the construction egg");
        helper.assertTrue("item.shincolle_kai.large_egg".equals(large.getItem().getDescriptionId(large)),
                "Large egg display name must describe the construction egg");
        helper.assertTrue("item.shincolle_kai.ship_egg_2".equals(destroyer.getItem().getDescriptionId(destroyer)),
                "Specific egg display name must describe the destroyer");
        helper.assertTrue(ShipSpawnEgg.getEggIcon(small) == 0 && ShipSpawnEgg.getEggIcon(large) == 1,
                "Construction eggs must use distinct sprites");
        ItemStack enchantedAmmo = icons.get(ID.Icon.AmmoEnchant);
        helper.assertTrue(enchantedAmmo.is(ModItems.EQUIP_AMMO.get())
                && BasicEquip.getEquipMeta(enchantedAmmo) == 7, "Enchanting icon must use equip ammo variant 7");
        helper.succeed();
    }
}
