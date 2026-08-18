package com.lulan.shincolle.equip.curios;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.equip.ShipEquipProvider;
import com.lulan.shincolle.equip.ShipEquipProviders;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.Attrs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
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
        float[] target = attrs.getAttrsEquip();

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
        float[] total = new float[Attrs.AttrsLength];
        for (int i = 0; i < limit; i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            ShipEquipProvider provider = ShipEquipProviders.find(stack);
            if (provider != null) {
                float[] add = provider.computeShipAttrs(stack);
                for (int j = 0; j < Attrs.AttrsLength && j < add.length; j++) {
                    total[j] += add[j];
                }

                // ShinColle clears AttackEffectMap in calcShipAttributesAddEffect(),
                // called earlier in the same recalculation, so entries added
                // here survive until the next recalculation and apply on every hit.
                if (ship.getAttackEffectMap() != null) {
                    provider.applyAttackEffects(ship.getAttackEffectMap(), stack);
                }
            }
        }

        total[ID.Attrs.MOV] = Math.max(total[ID.Attrs.MOV], MAX_MOVEMENT_PENALTY);
        accumulate(target, total);
    }

    private static void accumulate(float[] target, float[] add) {
        for (int i = 0; i < Attrs.AttrsLength && i < add.length; i++) {
            target[i] += add[i] * scaleFor(i);
        }
    }

    /** Mirrors the per-stat config scaling ShinColle applies to its own equipment. */
    private static float scaleFor(int attrIndex) {
        return switch (attrIndex) {
            case ID.Attrs.HP -> (float) ConfigHandler.scaleShip[ID.AttrsBase.HP];
            case ID.Attrs.ATK_L, ID.Attrs.ATK_H, ID.Attrs.ATK_AL, ID.Attrs.ATK_AH ->
                    (float) ConfigHandler.scaleShip[ID.AttrsBase.ATK];
            case ID.Attrs.DEF -> (float) ConfigHandler.scaleShip[ID.AttrsBase.DEF];
            case ID.Attrs.SPD -> (float) ConfigHandler.scaleShip[ID.AttrsBase.SPD];
            case ID.Attrs.MOV -> (float) ConfigHandler.scaleShip[ID.AttrsBase.MOV];
            case ID.Attrs.HIT -> (float) ConfigHandler.scaleShip[ID.AttrsBase.HIT];
            default -> 1F;
        };
    }

    /**
     * Calls {@link IShipEquipment#onShipHit} for each equipped piece after the
     * ship (or one of its carrier aircraft, attacking on its behalf) lands an
     * attack. ShinColle attacks by calling {@code target.hurt(...)} directly,
     * so no other mod's attack pipeline ever runs otherwise - this is the
     * only hook equipment gets to react to the ship's hits at all.
     *
     * @param shipEntity the ship wearing the equipment (its Curios inventory
     *                   is what gets searched, and what's passed to {@code
     *                   onShipHit} as the attacker - see the two-reference
     *                   note on {@link #applyEquipStats})
     */
    public static void runOnHitHooks(LivingEntity shipEntity, Entity target, float damageDealt) {
        if (target == null || shipEntity.level().isClientSide) {
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
        for (int i = 0; i < limit; i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            ShipEquipProvider provider = ShipEquipProviders.find(stack);
            if (provider != null) {
                try {
                    provider.onShipHit(shipEntity, target, damageDealt, stack);
                } catch (RuntimeException e) {
                    // Third-party equipment must never be able to abort the
                    // ship's attack; log and carry on with the rest.
                    ShinColle.LOGGER.warn("[equip] {} threw during onShipHit", stack.getItem(), e);
                }
            }
        }
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
