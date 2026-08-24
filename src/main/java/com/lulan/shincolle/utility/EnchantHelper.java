package com.lulan.shincolle.utility;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * enchant helper for equip enchantment
 */
public class EnchantHelper {

    // enchant table
    private static final ArrayList<Enchantment[]> EnchantTable = new ArrayList<>();

    // init roll table
    static {

        EnchantTable.add(new Enchantment[]{
                Enchantments.RESPIRATION, Enchantments.THORNS, Enchantments.FROST_WALKER,
                Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
                Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.MOB_LOOTING,
                Enchantments.BLOCK_EFFICIENCY, Enchantments.BLOCK_FORTUNE,
                Enchantments.POWER_ARROWS, Enchantments.PUNCH_ARROWS,
                Enchantments.FLAMING_ARROWS, Enchantments.INFINITY_ARROWS,
                Enchantments.FISHING_LUCK
        });
        EnchantTable.add(new Enchantment[]{
                Enchantments.ALL_DAMAGE_PROTECTION, Enchantments.FIRE_PROTECTION,
                Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION,
                Enchantments.SILK_TOUCH, Enchantments.UNBREAKING, Enchantments.MENDING
        });
        EnchantTable.add(new Enchantment[]{
                Enchantments.FALL_PROTECTION, Enchantments.AQUA_AFFINITY,
                Enchantments.DEPTH_STRIDER, Enchantments.SILK_TOUCH,
                Enchantments.FISHING_SPEED, Enchantments.MENDING
        });
    }

    public EnchantHelper() {
    }

    /**
     * equip enchant type: armor, weapon, misc
     * <p>
     * usable enchant:
     * XXX_protection (max 4, C~R) : +hp
     * protection, fire_protection, blast_protection, projectile_protection
     * damage (max 5, C~UC): +atk (weapon only)
     * sharpness, smite, bane_of_arthropods, power
     * unbreaking (max 3, UC) : +def (armor only)
     * efficiency (max 5, C) : +spd
     * depth_strider (max 3, R) : -weight
     * feather_falling (max 4, UC) : -weight
     * aqua_affinity (max 1, R) : -weight
     * knockback (max 2, UC) : +ran
     * knockback, punch
     * frost_walker (max 2, R) : +cri
     * fire_aspect (max 2, R) : +dhit, +thit
     * fire_aspect, flame
     * lure (max 3, R) : +miss
     * thorns (max 3, VR) : +AA
     * respiration (max 3, R) : +ASM
     * looting (max 3, R) : +xp gain (weapon only)
     * looting, fortune, luck_of_the_sea
     * silk_touch (max 1, VR) : grudge gain (non-weapon only)
     * infinity (max 1, VR) : ammo gain (weapon only)
     * mending (max 1, R) : hp restore effect (non-weapon only)
     * <p>
     * return array: ref: ID.EquipEnch
     * hp, atk, def, spd, mov, range, cri, dhit, thit, miss,
     * aa, asm, dodge, xp gain, grudge gain, ammo gain, -hp delay
     */
    public static ShipAttributeValues calcEnchantEffect(ItemStack stack) {
        ShipAttributeValues.Builder ench = ShipAttributeValues.builder(ShipAttributeLayout.current());
        Map<Enchantment, Integer> enchMap = EnchantmentHelper.getEnchantments(stack);

        for (Map.Entry<Enchantment, Integer> entry : enchMap.entrySet()) {
            Enchantment e = entry.getKey();
            int lv = entry.getValue();
            boolean handled = false;

            // hp - blast_protection (with knockback resist bonus)
            if (e == Enchantments.BLAST_PROTECTION) {
                ench.add(CoreShipAttributes.HP, 0.05F * lv);
                ench.add(CoreShipAttributes.KB, 0.1F * lv);
                handled = true;
            }
            // hp - fire_protection, projectile_protection
            else if (e == Enchantments.FIRE_PROTECTION || e == Enchantments.PROJECTILE_PROTECTION) {
                ench.add(CoreShipAttributes.HP, 0.05F * lv);
                handled = true;
            }
            // hp - protection (base)
            else if (e == Enchantments.ALL_DAMAGE_PROTECTION) {
                ench.add(CoreShipAttributes.HP, 0.1F * lv);
                handled = true;
            }
            // atk - smite, bane_of_arthropods
            else if (e == Enchantments.SMITE || e == Enchantments.BANE_OF_ARTHROPODS) {
                ench.add(CoreShipAttributes.ATK_L, 0.08F * lv);
                handled = true;
            }
            // atk - sharpness, power
            else if (e == Enchantments.SHARPNESS || e == Enchantments.POWER_ARROWS) {
                ench.add(CoreShipAttributes.ATK_L, 0.08F * lv);
                handled = true;
            }
            // def - unbreaking
            else if (e == Enchantments.UNBREAKING) {
                ench.add(CoreShipAttributes.DEF, 0.2F * lv);
                handled = true;
            }
            // spd - efficiency
            else if (e == Enchantments.BLOCK_EFFICIENCY) {
                ench.add(CoreShipAttributes.SPD, 0.1F * lv);
                handled = true;
            }
            // mov - aqua_affinity, depth_strider
            else if (e == Enchantments.AQUA_AFFINITY || e == Enchantments.DEPTH_STRIDER) {
                ench.add(CoreShipAttributes.MOV, 0.05F * lv);
                ench.add(CoreShipAttributes.DODGE, 0.25F * lv);
                handled = true;
            }
            // mov - feather_falling
            else if (e == Enchantments.FALL_PROTECTION) {
                ench.add(CoreShipAttributes.MOV, 0.1F * lv);
                ench.add(CoreShipAttributes.KB, -0.1F * lv);
                handled = true;
            }
            // range - punch, knockback
            else if (e == Enchantments.PUNCH_ARROWS || e == Enchantments.KNOCKBACK) {
                ench.add(CoreShipAttributes.HIT, 0.15F * lv);
                ench.add(CoreShipAttributes.KB, 0.05F * lv);
                handled = true;
            }
            // cri - frost_walker
            else if (e == Enchantments.FROST_WALKER) {
                ench.add(CoreShipAttributes.CRI, 0.25F * lv);
                handled = true;
            }
            // dhit, thit - fire_aspect, flame
            else if (e == Enchantments.FIRE_ASPECT || e == Enchantments.FLAMING_ARROWS) {
                ench.add(CoreShipAttributes.DHIT, 0.25F * lv);
                ench.add(CoreShipAttributes.THIT, 0.25F * lv);
                handled = true;
            }
            // miss - lure
            else if (e == Enchantments.FISHING_SPEED) {
                ench.add(CoreShipAttributes.MISS, 0.25F * lv);
                handled = true;
            }
            // aa - thorns
            else if (e == Enchantments.THORNS) {
                ench.add(CoreShipAttributes.AA, 0.15F * lv);
                handled = true;
            }
            // asm - respiration
            else if (e == Enchantments.RESPIRATION) {
                ench.add(CoreShipAttributes.ASM, 0.15F * lv);
                handled = true;
            }
            // xp gain - looting, fortune, luck_of_the_sea
            else if (e == Enchantments.MOB_LOOTING || e == Enchantments.BLOCK_FORTUNE
                    || e == Enchantments.FISHING_LUCK) {
                ench.add(CoreShipAttributes.XP, 0.25F * lv);
                handled = true;
            }
            // grudge gain - silk_touch
            else if (e == Enchantments.SILK_TOUCH) {
                ench.add(CoreShipAttributes.GRUDGE, 0.25F * lv);
                handled = true;
            }
            // ammo gain - infinity
            else if (e == Enchantments.INFINITY_ARROWS) {
                ench.add(CoreShipAttributes.AMMO, 0.25F * lv);
                handled = true;
            }
            // hp restore - mending
            else if (e == Enchantments.MENDING) {
                ench.add(CoreShipAttributes.HPRES, 0.5F * lv);
                handled = true;
            }

            // for non vanilla enchantment, increase all effect by 1%
            if (!handled) {
                for (net.minecraft.resources.ResourceLocation id : CoreShipAttributes.LEGACY_ORDER) {
                    ench.add(id, 0.01F * lv);
                }
            }
        }

        return ench.build();
    }

    /**
     * sum of enchantment number
     */
    public static int calcEnchantNumber(ItemStack stack) {
        int number = 0;
        Map<Enchantment, Integer> enchMap = EnchantmentHelper.getEnchantments(stack);

        for (int lv : enchMap.values()) {
            number += lv;
        }

        return number;
    }

    /**
     * apply random enchant to equip by lv and type
     * <p>
     * enchLv: 0:none, 1:40%=1 ench, 2:30%=1 30%=2, 3:30%=1 30%=2 20%=3
     */
    public static void applyRandomEnchantToEquip(ItemStack stack, int enchType, int enchLv) {
        if (stack.isEmpty() || enchLv == 0)
            return;

        // roll #enchant
        Random rand = new Random();
        int enchNum = 0;
        int ranNum = rand.nextInt(10);

        switch (enchLv) {
            case 1:
                enchNum = ranNum > 5 ? 1 : 0;
                break;
            case 2:
                enchNum = ranNum > 6 ? 2 : ranNum > 3 ? 1 : 0;
                break;
            case 3:
                enchNum = ranNum > 7 ? 3 : ranNum > 4 ? 2 : ranNum > 1 ? 1 : 0;
                break;
        }

        if (enchNum <= 0)
            return;

        // roll enchant id
        Enchantment[] enchs = EnchantTable.get(enchType);

        HashMap<Enchantment, Integer> enchmap = new HashMap<>();

        for (int i = 0; i < enchNum; i++) {
            Enchantment ench = enchs[rand.nextInt(enchs.length)];

            // if enchant already exist, lv++
            if (enchmap.containsKey(ench)) {
                int lv = enchmap.get(ench) + 1;
                if (lv > ench.getMaxLevel())
                    lv = ench.getMaxLevel();
                enchmap.replace(ench, lv);
            } else {
                enchmap.put(ench, 1);
            }
        }

        // apply enchant
        EnchantmentHelper.setEnchantments(enchmap, stack);
    }

}
