package com.lulan.shincolle.equip.curios;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.api.equipment.ShipEquipmentResolver;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.equip.ShipEquipmentAttributeMath;
import com.lulan.shincolle.equip.ShipEquipmentInternalEffects;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * All the code in this mod that actually touches Curios API types. Every
 * entry point here is called only from behind a
 * {@code ModList.get().isLoaded("curios")} check at the call site (see
 * {@code BasicEntityShip}, {@code ContainerShipInventory}), so this class is
 * never classloaded when Curios is absent - directly importing a missing
 * mod's classes anywhere reachable throws {@link NoClassDefFoundError} even
 * on paths that are never executed.
 */
public final class ShipCuriosIntegration {

    public static final ResourceLocation STACK_SOURCE_ID =
            ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "curios_ship_slots");

    private ShipCuriosIntegration() {
    }

    /**
     * Floor for the combined movement penalty these slots may impose, so a
     * heavy loadout slows a ship down without immobilising it.
     */
    private static final float MAX_MOVEMENT_PENALTY = -0.08F;

    /**
     * Folds equipment worn in the Curios slot into ShinColle's own equip stat
     * totals. Called at the tail of {@code calcShipAttributesAddEquip()},
     * after ShinColle has already applied its config scale to its own
     * equipment - so contributions here are scaled the same way, separately.
     *
     * <p>Takes the ship as two references to the same object - a
     * {@link LivingEntity} for Curios, an {@link IShipAttackBase} for
     * ShinColle's own stat/effect-map accessors - rather than one
     * {@code BasicEntityShip}, so it works for both friendly ships
     * ({@code BasicEntityShip}) and hostile ones ({@code
     * BasicEntityShipHostile}): the two classes share no common ancestor
     * besides {@code Mob}/{@code IShipAttackBase}.
     */
    public static void applyEquipStats(LivingEntity shipEntity, IShipAttackBase ship) {
        Attrs attrs = ship.getAttrs();
        if (attrs == null) {
            return;
        }
        ShipAttributeValues target = attrs.shipAttributes(ShipAttributeLayer.EQUIPMENT);

        var inventory = CuriosApi.getCuriosInventory(shipEntity).resolve().orElse(null);
        if (inventory == null) {
            return;
        }
        var handler = inventory.getStacksHandler(ShipEquipSlots.SLOT_ID).orElse(null);
        if (handler == null) {
            return;
        }

        var stacks = handler.getStacks();
        int limit = Math.min(stacks.getSlots(), ShipEquipSlots.slotCount());

        // Summed separately so the movement penalty can be capped before it
        // reaches the ship: these slots are additional to ShinColle's own six,
        // and every piece may carry its own movement penalty. Left unchecked,
        // a full loadout could drive MOV below zero and immobilise the ship.
        ShipAttributeValues total = ShipAttributeValues.defaults(
                ShipAttributeLayout.current(), ShipAttributeLayer.EQUIPMENT);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            ResolvedShipEquipment resolved = (shipEntity.level().isClientSide
                    ? ShipEquipmentResolver.resolveDynamicClient(stack)
                    : ShipEquipmentResolver.resolveDynamicServer(stack)).orElse(null);
            if (resolved == null) {
                continue;
            }
            try {
                ShipAttributeValues candidate = ShipEquipmentAttributeMath.add(total, resolved.attributes());
                ShipEquipmentAttributeMath.add(target, ShipEquipmentAttributeMath.scale(candidate));
                total = candidate;
                ShipEquipmentInternalEffects.apply(shipEntity, ship, stack, resolved);
            } catch (RuntimeException ignored) {
                // Reject the whole stack contribution; the canonical resolver has already logged source failures.
            }
        }

        total = ShipEquipmentAttributeMath.withMinimum(total, CoreShipAttributes.MOV, MAX_MOVEMENT_PENALTY);
        attrs.setShipAttributes(ShipAttributeLayer.EQUIPMENT,
                ShipEquipmentAttributeMath.add(target, ShipEquipmentAttributeMath.scale(total)));
    }

    /**
     * The non-empty stacks currently worn in the ship-equip Curios slot, for
     * display purposes (e.g. the ship inventory screen's equipment-traits
     * info page). Order matches the slots themselves.
     */
    public static List<ItemStack> getEquippedStacks(LivingEntity shipEntity) {
        var inventory = CuriosApi.getCuriosInventory(shipEntity).resolve().orElse(null);
        if (inventory == null) {
            return List.of();
        }
        var handler = inventory.getStacksHandler(ShipEquipSlots.SLOT_ID).orElse(null);
        if (handler == null) {
            return List.of();
        }

        var stacks = handler.getStacks();
        int limit = Math.min(stacks.getSlots(), ShipEquipSlots.slotCount());
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * Serialises the ship-equip Curios slot into a tag list, so a dying ship
     * can fold it into its death egg alongside its own inventory.
     *
     * <p>Curios stores these stacks on the entity's capability, which dies
     * with the entity - nothing in ShinColle's own inventory NBT covers them,
     * so without this they were simply gone.
     *
     * @return one compound per occupied slot, each carrying its slot index
     */
    public static ListTag saveEquipped(LivingEntity shipEntity) {
        ListTag list = new ListTag();
        var inventory = CuriosApi.getCuriosInventory(shipEntity).resolve().orElse(null);
        if (inventory == null) {
            return list;
        }
        var handler = inventory.getStacksHandler(ShipEquipSlots.SLOT_ID).orElse(null);
        if (handler == null) {
            return list;
        }

        var stacks = handler.getStacks();
        int limit = Math.min(stacks.getSlots(), ShipEquipSlots.slotCount());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            stack.save(entry);
            list.add(entry);
        }
        return list;
    }

    /**
     * Snapshots and removes the ship-equip stacks before Forge/Curios processes
     * the entity's death drops. Curios empties its own slots during that event;
     * waiting until the delayed death egg is created therefore loses the data.
     * Clearing here also prevents a duplicate world drop when the same stacks
     * are restored from the egg.
     */
    public static ListTag saveAndClearEquipped(LivingEntity shipEntity) {
        ListTag saved = saveEquipped(shipEntity);
        var inventory = CuriosApi.getCuriosInventory(shipEntity).resolve().orElse(null);
        if (inventory == null) {
            return saved;
        }
        var handler = inventory.getStacksHandler(ShipEquipSlots.SLOT_ID).orElse(null);
        if (handler == null) {
            return saved;
        }

        var stacks = handler.getStacks();
        int limit = Math.min(stacks.getSlots(), ShipEquipSlots.slotCount());
        for (int slot = 0; slot < limit; slot++) {
            stacks.setStackInSlot(slot, ItemStack.EMPTY);
        }
        return saved;
    }

    /**
     * Puts stacks saved by {@link #saveEquipped} back into the ship's
     * ship-equip Curios slot.
     *
     * <p>Silently drops entries whose slot no longer exists - the configured
     * slot count can shrink between the ship dying and being respawned.
     */
    public static void loadEquipped(LivingEntity shipEntity, ListTag list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        var inventory = CuriosApi.getCuriosInventory(shipEntity).resolve().orElse(null);
        if (inventory == null) {
            return;
        }
        var handler = inventory.getStacksHandler(ShipEquipSlots.SLOT_ID).orElse(null);
        if (handler == null) {
            return;
        }

        var stacks = handler.getStacks();
        int limit = Math.min(stacks.getSlots(), ShipEquipSlots.slotCount());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot >= limit) {
                continue;
            }
            stacks.setStackInSlot(slot, ItemStack.of(entry));
        }
    }

    /**
     * Builds the slots backed by the ship's Curios inventory, meant to be
     * appended to its menu after ShinColle's own slots. {@code addSlot} is
     * protected on {@link AbstractContainerMenu}, so the caller (in the same
     * package as the menu) adds each returned slot itself.
     *
     * <p>Always returns exactly {@link ShipEquipSlots#slotCount()} slots on
     * both sides so client and server build an identical slot list - a
     * mismatch would desync the menu - falling back to a throwaway handler if
     * the ship has no Curios capability (e.g. the client-side copy).
     */
    public static List<Slot> buildEquipSlots(BasicEntityShip ship) {
        int count = ShipEquipSlots.slotCount();
        if (count <= 0) {
            return List.of();
        }
        IItemHandlerModifiable handler = handlerFor(ship, count);

        List<Slot> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slotIndex = i;
            result.add(new SlotItemHandler(handler, slotIndex, ShipEquipSlots.slotX(), ShipEquipSlots.slotY(slotIndex)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ShipEquipSlots.accepts(stack) && super.mayPlace(stack);
                }
            });
        }
        return result;
    }

    private static IItemHandlerModifiable handlerFor(BasicEntityShip ship, int count) {
        if (ship != null) {
            IItemHandler handler = CuriosApi.getCuriosInventory(ship).resolve()
                    .flatMap(inv -> inv.getStacksHandler(ShipEquipSlots.SLOT_ID))
                    .map(stacksHandler -> (IItemHandler) stacksHandler.getStacks())
                    .orElse(null);
            if (handler instanceof IItemHandlerModifiable modifiable && handler.getSlots() >= count) {
                return modifiable;
            }
        }
        return new ItemStackHandler(count);
    }

    /**
     * ShinColle's own {@code quickMoveStack} only knows its own slot indices,
     * so shift-clicking into or out of the appended slots would misbehave.
     * Blocking the transfer keeps behaviour predictable - drag instead.
     */
    public static boolean isCuriosSlot(Slot slot) {
        return slot instanceof SlotItemHandler && ShipEquipSlots.isOurSlot(slot);
    }
}
