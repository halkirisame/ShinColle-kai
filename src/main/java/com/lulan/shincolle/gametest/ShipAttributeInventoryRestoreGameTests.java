package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.Objects;

/**
 * Regression tests for attribute recalculation after ship inventory NBT restoration.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttributeInventoryRestoreGameTests {

    private static final int HP_EQUIPMENT_VARIANT = 12;

    private ShipAttributeInventoryRestoreGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equippedAttributesAreReadyImmediatelyAfterLoad(GameTestHelper helper) {
        BasicEntityShip source = createShip(helper, "source");
        BasicEntityShip loaded = createShip(helper, "loaded");

        ItemStack cannon = new ItemStack(ModItems.EQUIP_CANNON.get());
        BasicEquip.setEquipMeta(cannon, HP_EQUIPMENT_VARIANT);
        source.getCapaShipInventory().setStackInSlot(0, cannon);
        source.calcShipAttributes(31, false);

        AttrsAdv sourceAttrs = requireAdvancedAttrs(source, "source");
        if (sourceAttrs.getAttrsEquip(ID.Attrs.HP) <= 0F) {
            throw new AssertionError("Regression fixture must have a non-zero equipment HP bonus");
        }

        CompoundTag saved = new CompoundTag();
        source.saveWithoutId(saved);
        loaded.load(saved);

        ItemStack restored = loaded.getCapaShipInventory().getStackInSlot(0);
        if (restored.getItem() != ModItems.EQUIP_CANNON.get()
                || BasicEquip.getEquipMeta(restored) != HP_EQUIPMENT_VARIANT) {
            throw new AssertionError("Native equipment slot was not restored with its variant");
        }

        AttrsAdv loadedAttrs = requireAdvancedAttrs(loaded, "loaded");
        assertArrayEquals(sourceAttrs.getAttrsEquip(), loadedAttrs.getAttrsEquip(), "equipment");
        assertArrayEquals(sourceAttrs.getAttrsBuffed(), loadedAttrs.getAttrsBuffed(), "buffed");
        assertDoubleEquals(sourceAttrs.getAttrsBuffed(ID.Attrs.HP),
                Objects.requireNonNull(loaded.getAttribute(Attributes.MAX_HEALTH)).getBaseValue(),
                "MAX_HEALTH");
        assertDoubleEquals(sourceAttrs.getAttrsBuffed(ID.Attrs.MOV),
                Objects.requireNonNull(loaded.getAttribute(Attributes.MOVEMENT_SPEED)).getBaseValue(),
                "MOVEMENT_SPEED");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unequippedAttributesAreRecalculatedAfterLoad(GameTestHelper helper) {
        BasicEntityShip source = createShip(helper, "unequipped source");
        BasicEntityShip loaded = createShip(helper, "unequipped loaded");
        source.calcShipAttributes(31, false);

        CompoundTag saved = new CompoundTag();
        source.saveWithoutId(saved);
        loaded.load(saved);

        AttrsAdv sourceAttrs = requireAdvancedAttrs(source, "unequipped source");
        AttrsAdv loadedAttrs = requireAdvancedAttrs(loaded, "unequipped loaded");
        assertArrayEquals(sourceAttrs.getAttrsEquip(), loadedAttrs.getAttrsEquip(), "empty equipment");
        assertArrayEquals(sourceAttrs.getAttrsBuffed(), loadedAttrs.getAttrsBuffed(), "empty buffed");
        helper.succeed();
    }

    private static BasicEntityShip createShip(GameTestHelper helper, String name) {
        Entity entity = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create " + name + " ship");
        }
        return ship;
    }

    private static AttrsAdv requireAdvancedAttrs(BasicEntityShip ship, String name) {
        if (!(ship.getAttrs() instanceof AttrsAdv attrs)) {
            throw new AssertionError(name + " ship does not have AttrsAdv");
        }
        return attrs;
    }

    private static void assertArrayEquals(float[] expected, float[] actual, String layer) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(layer + " attributes differ after NBT load");
        }
    }

    private static void assertDoubleEquals(double expected, double actual, String attribute) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(attribute + ": expected " + expected + " but was " + actual);
        }
    }
}
