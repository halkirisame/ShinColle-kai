package com.lulan.shincolle.entity;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.EquipAmmo;
import com.lulan.shincolle.item.IShipEffectItem;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/** Regression coverage for legacy BasicEquip attack-effect compatibility. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegacyBasicEquipEffectsGameTests {

    private LegacyBasicEquipEffectsGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyEquipEffectsCopyItemAttackEffectArrays(GameTestHelper helper) {
        IShipEffectItem source = fixedEffects(new ShipAttackEffect(effectId("poison"), 2, 120, 40));
        Map<ResourceLocation, ShipAttackEffect> target = new HashMap<>();

        LegacyBasicEquipEffects.applyAttackEffects(target, new ItemStack(Items.STICK), source, 0);
        assertEffect(target, effectId("poison"), 2, 120, 40, "fixed item effect");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyEquipEffectsReadEnchantShellPListAndDetachIt(GameTestHelper helper) {
        ItemStack shell = new ItemStack(ModItems.EQUIP_AMMO.get());
        BasicEquip.setEquipMeta(shell, 7);
        CompoundTag effect = new CompoundTag();
        effect.putInt(EquipAmmo.PID, 20);
        effect.putInt(EquipAmmo.PLEVEL, 3);
        effect.putInt(EquipAmmo.PTIME, 240);
        effect.putInt(EquipAmmo.PCHANCE, 35);
        ListTag list = new ListTag();
        list.add(effect);
        shell.getOrCreateTag().put(EquipAmmo.PLIST, list);

        Map<ResourceLocation, ShipAttackEffect> target = new HashMap<>();
        IShipEffectItem ammo = (IShipEffectItem) shell.getItem();
        LegacyBasicEquipEffects.applyAttackEffects(target, shell, ammo, 7);
        assertEffect(target, effectId("wither"), 3, 240, 35, "PList effect");
        target.clear();
        LegacyBasicEquipEffects.applyAttackEffects(target, shell, ammo, 7);
        assertEffect(target, effectId("wither"), 3, 240, 35, "PList effect after recalculation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void nativeRecalculationWiresPListAndClearsItAfterRemoval(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create native equipment test ship");
        }

        ItemStack shell = new ItemStack(ModItems.EQUIP_AMMO.get());
        BasicEquip.setEquipMeta(shell, 7);
        CompoundTag effect = new CompoundTag();
        effect.putInt(EquipAmmo.PID, 20);
        effect.putInt(EquipAmmo.PLEVEL, 2);
        effect.putInt(EquipAmmo.PTIME, 180);
        effect.putInt(EquipAmmo.PCHANCE, 45);
        ListTag list = new ListTag();
        list.add(effect);
        shell.getOrCreateTag().put(EquipAmmo.PLIST, list);

        ship.getCapaShipInventory().setStackInSlot(0, shell);
        ship.calcShipAttributes(2, false);
        assertEffect(ship.getAttackEffectMap(), effectId("wither"), 2, 180, 45,
                "native resolver PList effect");

        ship.getCapaShipInventory().setStackInSlot(0, ItemStack.EMPTY);
        ship.calcShipAttributes(2, false);
        if (ship.getAttackEffectMap().containsKey(effectId("wither"))) {
            throw new AssertionError("Native recalculation retained removed equipment attack effect");
        }
        int level = ship.getLevel();
        assertEffect(ship.getAttackEffectMap(), ShipInnateAttackEffects.WEAKNESS,
                level / 60, 100 + level, Math.min(level, 100), "Kongou innate effect after recalculation");
        if (ship.getAttackEffectMap().size() != 1) {
            throw new AssertionError("Native recalculation retained unexpected attack effects: "
                    + ship.getAttackEffectMap());
        }
        helper.succeed();
    }

    private static IShipEffectItem fixedEffects(ShipAttackEffect source) {
        return new IShipEffectItem() {
            @Override
            public Map<ResourceLocation, ShipAttackEffect> getEffectOnAttack(int meta) {
                return Map.of(source.effectId(), source);
            }

            @Override
            public int getMissileType(int meta) {
                return 0;
            }

            @Override
            public int getMissileMoveType(int meta) {
                return -1;
            }

            @Override
            public int getMissileSpeedLevel(int meta) {
                return 0;
            }
        };
    }

    private static void assertEffect(Map<ResourceLocation, ShipAttackEffect> effects, ResourceLocation id,
                                     int level, int duration,
                                     int chance, String name) {
        ShipAttackEffect value = effects.get(id);
        if (value == null || value.amplifier() != level || value.durationTicks() != duration
                || value.chancePercent() != chance) {
            throw new AssertionError(name + " was not preserved: " + value);
        }
    }

    private static ResourceLocation effectId(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
