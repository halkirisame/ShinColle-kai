package com.lulan.shincolle.compat.jei;

import com.lulan.shincolle.client.gui.GuiShipInventory;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.reference.Reference;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
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
