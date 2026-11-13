package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenericShipInventoryGameTests {
    private static final ShipDefinitionId SAMPLE = ShipDefinitionId.parse("shincolle_test:sample_ship");
    private static final Vec3 POSITION = new Vec3(3.5D, 3D, 3.5D);

    private GenericShipInventoryGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dormantMenusRejectClicksCloseAndResume(GameTestHelper helper) {
        fixture(helper, f -> {
            var inventory = f.ship.getCapaShipInventory();
            inventory.setStackInSlot(6, new ItemStack(Items.DIAMOND, 3));
            inventory.setStackInSlot(0, new ItemStack(ModItems.EQUIP_CANNON.get()));
            ContainerShipInventory menu = new ContainerShipInventory(1, f.owner.getInventory(), f.ship);
            f.owner.containerMenu = menu;
            helper.assertTrue(menu.stillValid(f.owner), "Active menu fixture must be usable");
            f.sleep();
            CompoundTag before = inventory.serializeNBT();
            List<String> failures = new ArrayList<>();
            if (menu.stillValid(f.owner)) failures.add("Dormant menu remains valid");
            for (ClickType click : ClickType.values()) {
                menu.clicked(6, 0, click, f.owner);
                menu.clicked(0, 0, click, f.owner);
            }
            menu.quickMoveStack(f.owner, 6);
            if (!before.equals(inventory.serializeNBT()) || !menu.getCarried().isEmpty()) {
                failures.add("Dormant click or quick-move changed cargo/equipment");
            }
            f.ship.tick();
            if (f.owner.containerMenu != f.owner.inventoryMenu) failures.add("Open menu did not close");
            if (f.ship.createMenu(2, f.owner.getInventory(), f.owner) != null) failures.add("Dormant menu can be created");
            f.ship.openGUI(f.owner);
            if (f.owner.containerMenu != f.owner.inventoryMenu) failures.add("Dormant direct GUI opens");
            f.wake();
            if (!before.equals(inventory.serializeNBT())) failures.add("Inventory changed after restoration");
            helper.assertTrue(new ContainerShipInventory(3, f.owner.getInventory(), f.ship).stillValid(f.owner),
                    "Restoration must unlock inventory");
            helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dormantCargoRejectsAutomaticInsertionBeforeEntityTick(GameTestHelper helper) {
        fixture(helper, f -> {
            var inventory = f.ship.getCapaShipInventory();
            inventory.setStackInSlot(6, new ItemStack(Items.DIAMOND, 3));
            CompoundTag before = inventory.serializeNBT();
            f.removeDefinition();
            ItemStack incoming = new ItemStack(Items.DIAMOND, 2);
            boolean accepted = inventory.addItemStackToInventory(incoming);
            helper.assertTrue(!accepted && incoming.getCount() == 2 && before.equals(inventory.serializeNBT())
                            && !inventory.canAddItemStackToInventory(incoming) && inventory.getFirstSlotForItem() == -1,
                    "Missing definition must reject insertion before the entity can refresh");
            f.ship.tick();
            helper.assertTrue(f.ship.definitionState().dormant(), "Ship must sleep");
            f.wake();
            helper.assertTrue(before.equals(inventory.serializeNBT()), "Dormancy must preserve cargo");
            helper.assertTrue(inventory.addItemStackToInventory(incoming) && incoming.isEmpty()
                            && inventory.getStackInSlot(6).getCount() == 5,
                    "Active cargo must accept insertion again");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dormantCuriosMenuKeepsRealEquipment(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        fixture(helper, CuriosFixture::check);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dormantCraneRejectsLoadingAndUnloading(GameTestHelper helper) {
        fixture(helper, f -> {
            BlockPos pos = helper.absolutePos(new BlockPos(3, 5, 3));
            helper.getLevel().setBlock(pos, ModBlocks.CRANE.get().defaultBlockState(), 3);
            helper.getLevel().setBlock(pos.east(2), Blocks.CHEST.defaultBlockState(), 3);
            TileEntityCrane crane = (TileEntityCrane) helper.getLevel().getBlockEntity(pos);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos.east(2));
            CompoundTag data = crane.saveWithFullMetadata();
            data.putUUID("OwnerUUID", f.owner.getUUID());
            crane.load(data);
            crane.setPairedChest(pos.east(2));
            crane.setActive(true);
            crane.setCraneMode(24);
            crane.setEnabLoad(false);
            crane.setEnabUnload(false);
            f.ship.setGuardedPos(pos.getX(), pos.getY(), pos.getZ(), helper.getLevel().dimension(), 1);
            f.ship.setStateFlag(ID.F.CanFollow, false);
            f.ship.setStateTimer(ID.T.CrandDelay, 0);
            f.ship.setStateMinor(ID.M.CraneState, 2);
            tickCrane(helper, crane, 80);
            helper.assertTrue(crane.getDockedShip() == f.ship, "Active ship must dock");
            f.ship.getCapaShipInventory().setStackInSlot(6, new ItemStack(Items.DIAMOND, 3));
            chest.setItem(0, new ItemStack(Items.EMERALD, 4));
            CompoundTag before = f.ship.getCapaShipInventory().serializeNBT();
            crane.setEnabLoad(true);
            crane.setEnabUnload(true);
            f.sleep();
            tickCrane(helper, crane, 32);
            helper.assertTrue(before.equals(f.ship.getCapaShipInventory().serializeNBT())
                            && chest.getItem(0).is(Items.EMERALD) && chest.getItem(0).getCount() == 4
                            && chest.getItem(1).isEmpty() && crane.getDockedShip() == null,
                    "Dormant ship must release from crane without loading/unloading");
            f.wake();
            helper.assertTrue(before.equals(f.ship.getCapaShipInventory().serializeNBT()), "Restoration changed crane cargo");
            f.ship.setStateTimer(ID.T.CrandDelay, 0);
            f.ship.setStateMinor(ID.M.CraneState, 2);
            crane.setEnabUnload(false);
            tickCrane(helper, crane, 16);
            helper.assertTrue(crane.getDockedShip() == f.ship && chest.getItem(0).getCount() == 3,
                    "Crane loading must resume after restoration");
        });
    }

    private static void tickCrane(GameTestHelper helper, TileEntityCrane crane, int count) {
        for (int i = 0; i < count; i++) {
            TileEntityCrane.serverTick(helper.getLevel(), crane.getBlockPos(), crane.getBlockState(), crane);
        }
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> test) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Map<ShipDefinitionId, ShipDefinition> original = ShipDefinitionRegistry.server().all();
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dormant_inv"));
                owner.moveTo(helper.absoluteVec(POSITION));
                helper.getLevel().addNewPlayer(owner);
                try {
                    GenericShipEntity ship = entities.add((GenericShipEntity)
                            GenericShipSpawner.create(helper.getLevel(), SAMPLE, false, null));
                    helper.assertTrue(ship != null, "Sample definition must exist");
                    ship.moveTo(helper.absoluteVec(POSITION));
                    ship.tame(owner);
                    ship.setNoAi(true);
                    ship.setNoGravity(true);
                    ship.setInvulnerable(true);
                    ship.setStateMinor(ID.M.NumGrudge, 100_000);
                    ship.setStateFlag(ID.F.NoFuel, false);
                    helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship must enter level");
                    test.accept(new Fixture(helper, entities, owner, ship, original));
                    helper.succeed();
                } finally {
                    owner.closeContainer();
                    helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                }
            } finally {
                ShipDefinitionRegistry.server().reload(() -> original);
            }
        }, POSITION);
    }

    /** Keep optional API types, including synthetic lambda signatures, out of the scanned test holder. */
    private static final class CuriosFixture {
        private static void check(Fixture f) {
            GameTestHelper helper = f.helper;
            ContainerShipInventory menu = new ContainerShipInventory(1, f.owner.getInventory(), f.ship);
            int slot = ContainerShipInventory.VISIBLE_SHIP_SLOTS + 36;
            helper.assertTrue(ShipEquipSlots.slotCount() > 0 && menu.slots.size() > slot, "Curios fixture needs slots");
            var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(f.ship).resolve()
                    .flatMap(inv -> inv.getStacksHandler(ShipEquipSlots.SLOT_ID)).orElseThrow().getStacks();
            ItemStack equipment = new ItemStack(ModItems.EQUIP_CANNON.get());
            helper.assertTrue(handler.isItemValid(0, equipment), "Addon fixture equipment must satisfy Curios validation");
            helper.assertTrue(handler.insertItem(0, equipment.copy(), false).isEmpty(), "Curios must accept fixture equipment");
            helper.assertTrue(ItemStack.matches(menu.getSlot(slot).getItem(), equipment),
                    "Menu must reference real addon-provided Curios slots, not fallback storage");
            f.owner.containerMenu = menu;
            f.sleep();
            menu.clicked(slot, 0, ClickType.PICKUP, f.owner);
            menu.quickMoveStack(f.owner, slot);
            List<String> failures = new ArrayList<>();
            if (!ItemStack.matches(handler.getStackInSlot(0), equipment) || !menu.getCarried().isEmpty()) {
                failures.add("Dormant menu extracted real Curios equipment");
            }
            f.ship.tick();
            if (f.owner.containerMenu != f.owner.inventoryMenu) failures.add("Curios menu did not close");
            f.wake();
            var restored = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(f.ship).resolve()
                    .flatMap(inv -> inv.getStacksHandler(ShipEquipSlots.SLOT_ID)).orElseThrow().getStacks();
            helper.assertTrue(ItemStack.matches(restored.getStackInSlot(0), equipment), "Curios restore changed equipment");
            CompoundTag saved = f.ship.saveWithoutId(new CompoundTag());
            GenericShipEntity reloaded = f.entities.add((GenericShipEntity)
                    GenericShipSpawner.create(helper.getLevel(), SAMPLE, false, null));
            reloaded.load(saved);
            var loaded = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(reloaded).resolve()
                    .flatMap(inv -> inv.getStacksHandler(ShipEquipSlots.SLOT_ID)).orElseThrow().getStacks();
            helper.assertTrue(ItemStack.matches(loaded.getStackInSlot(0), equipment), "Curios world reload changed equipment");
            if (!menu.getCarried().isEmpty()) failures.add("Extracted equipment remains on cursor after restoration");
            helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        }
    }

    private record Fixture(GameTestHelper helper, GameTestEntities entities, FakePlayer owner, GenericShipEntity ship,
                           Map<ShipDefinitionId, ShipDefinition> original) {
        void removeDefinition() {
            Map<ShipDefinitionId, ShipDefinition> missing = new HashMap<>(original);
            missing.remove(SAMPLE);
            ShipDefinitionRegistry.server().reload(() -> missing);
        }

        void sleep() {
            removeDefinition();
            ship.tick();
            helper.assertTrue(ship.definitionState().dormant(), "Missing definition must sleep");
        }

        void wake() {
            ShipDefinitionRegistry.server().reload(() -> original);
            ship.tick();
            helper.assertTrue(!ship.definitionState().dormant(), "Definition must wake ship");
        }
    }
}
