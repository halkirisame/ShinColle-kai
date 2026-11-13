package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.InteractHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CuriosAssetSafetyGameTests {
    private CuriosAssetSafetyGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dismantlingReturnsCuriosEquipmentWithNbt(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = fixture(helper, entities);
            ItemStack equipment = new ItemStack(ModItems.EQUIP_ARMOR.get());
            equipment.getOrCreateTag().putString("Fixture", UUID.randomUUID().toString());
            CompoundTag entry = equipment.save(new CompoundTag());
            entry.putByte("Slot", (byte) 0);
            ListTag saved = new ListTag();
            saved.add(entry);
            ShipCuriosIntegration.loadEquipped(ship, saved);
            helper.assertTrue(saved.equals(ShipCuriosIntegration.saveEquipped(ship)), "real slots must hold equipment");
            FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "curios_kaitai"));
            owner.getAbilities().instabuild = false;
            ship.tame(owner);
            InteractHelper.interactKaitaiHammer(ship, owner, new ItemStack(ModItems.KAITAI_HAMMER.get()));
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, ship.getBoundingBox().inflate(2D))
                    .forEach(entities::add);
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    ship.getBoundingBox().inflate(2D), item -> ItemStack.isSameItemSameTags(item.getItem(), equipment));
            drops.forEach(entities::add);
            helper.assertTrue(ship.isRemoved() && drops.size() == 1 && drops.get(0).getItem().getCount() == 1,
                    "dismantling must return the Curios stack exactly once, including item NBT");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void missingCuriosStorageHasNoOperableMenuSlots(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = entities.add(ModEntities.DESTROYER_AKATSUKI.get().create(helper.getLevel()));
            helper.assertTrue(ship != null && ShipEquipSlots.slotCount() > 0, "fixture needs configured slots");
            var inventory = CuriosApi.getCuriosInventory(ship).resolve().orElse(null);
            helper.assertTrue(inventory == null || inventory.getStacksHandler(ShipEquipSlots.SLOT_ID).isEmpty(),
                    "Akatsuki must really lack ship-equip storage");
            List<Slot> slots = ShipCuriosIntegration.buildEquipSlots(ship);
            helper.assertTrue(slots.size() == ShipEquipSlots.slotCount(), "menu index count must stay unchanged");
            for (Slot slot : slots) {
                helper.assertTrue(!slot.isActive() && !slot.mayPlace(new ItemStack(ModItems.EQUIP_ARMOR.get())),
                        "a slot without persistent storage must not be operable");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shorterCuriosStorageKeepsExistingSlotsBacked(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = fixture(helper, entities);
            var stacks = CuriosApi.getCuriosInventory(ship).resolve().orElseThrow()
                    .getStacksHandler(ShipEquipSlots.SLOT_ID).orElseThrow().getStacks();
            helper.assertTrue(ShipEquipSlots.slotCount() > 1 && stacks.getSlots() > 1, "fixture needs multiple slots");
            stacks.shrink(stacks.getSlots() - 1);
            helper.assertTrue(stacks.getSlots() == 1, "real storage must shrink to one slot");
            ItemStack equipment = new ItemStack(ModItems.EQUIP_ARMOR.get());
            stacks.setStackInSlot(0, equipment);
            List<Slot> slots = ShipCuriosIntegration.buildEquipSlots(ship);
            helper.assertTrue(slots.size() == ShipEquipSlots.slotCount() && slots.get(0).isActive()
                            && ItemStack.matches(slots.get(0).getItem(), equipment),
                    "an undersized real handler must not be replaced by temporary storage");
            slots.get(0).set(ItemStack.EMPTY);
            helper.assertTrue(stacks.getStackInSlot(0).isEmpty(), "menu removal must change real storage");
            for (int i = 1; i < slots.size(); i++) {
                helper.assertTrue(!slots.get(i).isActive(), "only missing slots must be disabled");
            }
        }
        helper.succeed();
    }

    private static BasicEntityShip fixture(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "ship fixture must exist");
        ship.moveTo(helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D)));
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        return ship;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void revivalOverflowHonorsSmallerAndZeroRealStorage(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = fixture(helper, entities);
            var stacks = CuriosApi.getCuriosInventory(ship).resolve().orElseThrow()
                    .getStacksHandler(ShipEquipSlots.SLOT_ID).orElseThrow().getStacks();
            helper.assertTrue(stacks.getSlots() > 1 && ShipEquipSlots.slotCount() > 1, "fixture needs multiple slots");
            stacks.shrink(stacks.getSlots() - 1);
            helper.assertTrue(stacks.getSlots() == 1, "real storage must shrink independently of config");
            ItemStack equipment = new ItemStack(ModItems.EQUIP_ARMOR.get());
            equipment.getOrCreateTag().putString("Fixture", UUID.randomUUID().toString());
            CompoundTag entry = equipment.save(new CompoundTag());
            entry.putByte("Slot", (byte) 1);
            ListTag saved = new ListTag();
            saved.add(entry);
            List<ItemStack> overflow = ShipCuriosIntegration.loadEquippedWithOverflow(ship, saved);
            helper.assertTrue(overflow.size() == 1 && ItemStack.matches(overflow.get(0), equipment)
                            && ShipCuriosIntegration.saveEquipped(ship).isEmpty(),
                    "equipment outside real storage must be returned even within configured slots");
            stacks.shrink(1);
            helper.assertTrue(stacks.getSlots() == 0, "real storage must reach zero slots");
            entry.putByte("Slot", (byte) 0);
            overflow = ShipCuriosIntegration.loadEquippedWithOverflow(ship, saved);
            helper.assertTrue(overflow.size() == 1 && ItemStack.matches(overflow.get(0), equipment),
                    "zero real slots must return every saved stack without a bounds error");
        }
        helper.succeed();
    }
}
