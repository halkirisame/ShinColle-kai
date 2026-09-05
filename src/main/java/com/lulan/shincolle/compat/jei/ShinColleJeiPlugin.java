package com.lulan.shincolle.compat.jei;

import com.lulan.shincolle.client.gui.GuiShipInventory;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.equipdata.ClientEquipData;
import com.lulan.shincolle.equipdata.EquipmentAvailabilityStacks;
import com.lulan.shincolle.equipdata.EquipmentIngredientDiff;
import com.lulan.shincolle.equipdata.EquipmentSubtypeKeys;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells JEI to keep its item list panel clear of the third-party equipment
 * strip appended past the right edge of the ship inventory screen (see
 * {@code com.lulan.shincolle.equip}) - without this, JEI's panel and the
 * equip slots draw on top of each other.
 *
 * <p>JEI discovers this class itself via classpath scanning for
 * {@link JeiPlugin}, so it is never referenced from - and never loaded by -
 * any other class in this mod. That keeps JEI, like Curios, a true optional
 * dependency: absent JEI, this class simply never gets instantiated.
 */
@JeiPlugin
public class ShinColleJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_ID = new ResourceLocation(Reference.MOD_ID, "jei_plugin");
    private final Runnable equipmentSyncListener = this::synchronizeHiddenEquipment;
    private Map<String, ItemStack> trackedHiddenEquipment = Map.of();
    private IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        ClientEquipData.addInstallListener(equipmentSyncListener);
        synchronizeHiddenEquipment();
    }

    @Override
    public void onRuntimeUnavailable() {
        ClientEquipData.removeInstallListener(equipmentSyncListener);
        trackedHiddenEquipment = Map.of();
        runtime = null;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof BasicEquip) {
                registration.registerSubtypeInterpreter(item, (stack, context) ->
                        EquipmentSubtypeKeys.subtypeKey(BasicEquip.getEquipMeta(stack),
                                context == UidContext.Ingredient));
            }
        }
    }

    private void synchronizeHiddenEquipment() {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            return;
        }

        IIngredientManager manager = currentRuntime.getIngredientManager();
        IIngredientHelper<ItemStack> helper = manager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        Map<String, ItemStack> desiredHiddenEquipment = new LinkedHashMap<>();
        for (ItemStack stack : EquipmentAvailabilityStacks.hiddenStacks(ClientEquipData.current())) {
            if (jeiSeparatesVariant(helper, stack)) {
                desiredHiddenEquipment.putIfAbsent(helper.getUniqueId(stack, UidContext.Ingredient), stack);
            }
        }

        EquipmentIngredientDiff diff = EquipmentIngredientDiff.diff(trackedHiddenEquipment.keySet(),
                desiredHiddenEquipment.keySet());
        if (!diff.toAdd().isEmpty()) {
            manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacksFor(diff.toAdd(), trackedHiddenEquipment));
        }
        if (!diff.toRemove().isEmpty()) {
            manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                    stacksFor(diff.toRemove(), desiredHiddenEquipment));
        }
        trackedHiddenEquipment = desiredHiddenEquipment;
    }

    private static List<ItemStack> stacksFor(List<String> uids, Map<String, ItemStack> equipmentByUid) {
        return uids.stream().map(equipmentByUid::get).toList();
    }

    /**
     * Whether JEI lists this equipment variant as its own entry.
     *
     * <p>JEI derives an ingredient's identity from subtype interpreters registered through
     * {@code IModPlugin#registerItemSubtypes}. Compare this variant to a different variant of
     * the same item so variant 0 is not mistaken for a bare stack. If an interpreter is
     * overridden or stops applying, the matching UIDs keep the removal from deleting every
     * variant of that item.
     */
    private static boolean jeiSeparatesVariant(IIngredientHelper<ItemStack> helper, ItemStack stack) {
        String variantUid = helper.getUniqueId(stack, UidContext.Ingredient);
        ItemStack probe = new ItemStack(stack.getItem());
        BasicEquip.setEquipMeta(probe, EquipmentSubtypeKeys.probeVariant(BasicEquip.getEquipMeta(stack)));
        String probeUid = helper.getUniqueId(probe, UidContext.Ingredient);
        return !variantUid.equals(probeUid);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GuiShipInventory.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(GuiShipInventory screen) {
                if (!ModList.get().isLoaded("curios")) {
                    return Collections.emptyList();
                }

                int count = ShipEquipSlots.slotCount();
                if (count <= 0) {
                    return Collections.emptyList();
                }

                // Mirrors the panel geometry GuiShipInventory itself draws:
                // a 26px-wide strip starting just past the 256px main screen,
                // one cap above/below plus one 18px row per slot.
                int inset = 4;
                int cap = 4;
                int row = 18;
                int x = screen.getGuiLeft() + ShipEquipSlots.slotX() - inset;
                int y = screen.getGuiTop() + ShipEquipSlots.slotY(0) - cap;
                int width = ShipEquipSlots.PANEL_WIDTH;
                int height = cap + count * row + cap;

                // Also exclude the "view traits" button drawn just below the
                // panel (see GuiShipInventory#equipDetailButtonRelBounds) -
                // JEI was drawing its item list right on top of it.
                int[] buttonBounds = GuiShipInventory.equipDetailButtonRelBounds();
                int buttonX = screen.getGuiLeft() + buttonBounds[0];
                int buttonY = screen.getGuiTop() + buttonBounds[1];

                // The "AI settings" button needs no entry here: unlike the equip
                // panel it sits inside the 256px main screen rect, which JEI
                // already avoids on its own.
                List<Rect2i> areas = new ArrayList<>();
                areas.add(new Rect2i(x, y, width, height));
                areas.add(new Rect2i(buttonX, buttonY, buttonBounds[2], buttonBounds[3]));
                return areas;
            }
        });
    }
}
