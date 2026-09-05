package com.lulan.shincolle.crafting;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeEnchantRule;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Equipment roll calculator for shipyard construction.
 * <p>
 * Roll system:
 * 1. rollEquipType() - determines equipment category via normal distribution
 * 2. rollEquipsOfTheType() - determines specific equipment within category
 * 3. getItemStackFromId() - converts equip ID to ItemStack
 */
public class EquipCalc {

    /**
     * Roll tables: {equipType, materialMean, modifiedMaterialType}
     */
    private static final List<int[]> EQUIP_SMALL = new ArrayList<>();
    private static final List<int[]> EQUIP_LARGE = new ArrayList<>();
    // Enchantment tables by type: 0=weapon, 1=armor, 2=misc
    // Each array contains vanilla enchantment IDs suitable for that equipment type.
    // In 1.20.1, we use Enchantment registry objects looked up by resource
    // location.
    private static final String[][] ENCHANT_TABLE = {
            // Weapon enchants
            {"minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods",
                    "minecraft:power", "minecraft:knockback", "minecraft:punch",
                    "minecraft:fire_aspect", "minecraft:flame", "minecraft:looting",
                    "minecraft:infinity", "minecraft:respiration"},
            // Armor enchants
            {"minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection",
                    "minecraft:projectile_protection", "minecraft:unbreaking", "minecraft:thorns",
                    "minecraft:mending"},
            // Misc enchants
            {"minecraft:feather_falling", "minecraft:aqua_affinity", "minecraft:depth_strider",
                    "minecraft:efficiency", "minecraft:unbreaking", "minecraft:lure",
                    "minecraft:mending"}
    };

    static {
        // Small build table
        EQUIP_SMALL.add(new int[]{ID.EquipType.ARMOR_LO, 80, 1});
        EQUIP_SMALL.add(new int[]{ID.EquipType.FLARE_LO, 80, 2});
        EQUIP_SMALL.add(new int[]{ID.EquipType.SEARCHLIGHT_LO, 80, 0});
        EQUIP_SMALL.add(new int[]{ID.EquipType.COMPASS_LO, 90, 0});
        EQUIP_SMALL.add(new int[]{ID.EquipType.GUN_LO, 100, 2});
        EQUIP_SMALL.add(new int[]{ID.EquipType.DRUM_LO, 120, 1});
        EQUIP_SMALL.add(new int[]{ID.EquipType.AMMO_LO, 120, 2});
        EQUIP_SMALL.add(new int[]{ID.EquipType.CANNON_SI, 128, 2});
        EQUIP_SMALL.add(new int[]{ID.EquipType.TORPEDO_LO, 160, 2});
        EQUIP_SMALL.add(new int[]{ID.EquipType.RADAR_LO, 200, 0});
        EQUIP_SMALL.add(new int[]{ID.EquipType.AIR_R_LO, 256, 3});
        EQUIP_SMALL.add(new int[]{ID.EquipType.CANNON_TW_LO, 320, 2});

        // Large build table
        EQUIP_LARGE.add(new int[]{ID.EquipType.ARMOR_HI, 500, 1});
        EQUIP_LARGE.add(new int[]{ID.EquipType.GUN_HI, 800, 2});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AMMO_HI, 1000, 2});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AIR_R_HI, 1000, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.TORPEDO_HI, 1200, 2});
        EQUIP_LARGE.add(new int[]{ID.EquipType.TURBINE_LO, 1400, 0});
        EQUIP_LARGE.add(new int[]{ID.EquipType.CANNON_TW_HI, 1600, 2});
        EQUIP_LARGE.add(new int[]{ID.EquipType.RADAR_HI, 2000, 0});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AIR_T_LO, 2400, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AIR_F_LO, 2400, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AIR_B_LO, 2400, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.CATAPULT_LO, 2800, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.TURBINE_HI, 3200, 0});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AIR_T_HI, 3800, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AIR_F_HI, 3800, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.AIR_B_HI, 3800, 3});
        EQUIP_LARGE.add(new int[]{ID.EquipType.CANNON_TR, 4400, 2});
        EQUIP_LARGE.add(new int[]{ID.EquipType.CATAPULT_HI, 5000, 3});
    }

    /**
     * Roll equipment type by total amount of materials.
     *
     * @param buildType 0=small, 1=large
     * @param matAmount int[4] of {grudge, abyssium, ammo, polymetal}
     * @return equipment type ID, or -1 if roll fails
     */
    public static int rollEquipType(int buildType, int[] matAmount, RandomSource random) {
        List<int[]> eqList = buildType == 0 ? EQUIP_SMALL : EQUIP_LARGE;
        int totalMats = matAmount[0] + matAmount[1] + matAmount[2] + matAmount[3];

        // Build probability map: equipType -> probability
        Map<Integer, Float> probList = new HashMap<>();

        for (int[] entry : eqList) {
            int meanNew;
            // Reduce mean by specific material amount
            if (entry[2] >= 0 && entry[2] <= 3) {
                meanNew = entry[1] - matAmount[entry[2]];
            } else {
                meanNew = entry[1];
            }

            // Distance from mean
            int meanDist = Mth.abs(totalMats - meanNew);

            // Scale small build to large resolution (256 range -> 4000 range)
            if (buildType == 0) {
                meanDist = (int) (meanDist * 15.625F);
            }

            float prob = CalcHelper.getNormDist(meanDist);
            probList.put(entry[0], prob);
            LogHelper.debug("DEBUG: roll equip type: ID " + entry[0] +
                    " MEAN(ORG) " + entry[1] + " MEAN(NEW) " + meanNew +
                    " MD " + meanDist + " PR " + prob);
        }

        Integer result = weightedRoll(probList, random);
        return result == null ? -1 : result;
    }

    /**
     * Roll a specific equipment of the given type.
     *
     * @param type      equipment type
     * @param totalMats total material amount
     * @param buildType 0=small, 1=large
     * @return resulting ItemStack, or ItemStack.EMPTY
     */
    public static ItemStack rollEquipsOfTheType(int type, int totalMats, int buildType, RandomSource random) {
        if (type == -1)
            return ItemStack.EMPTY;

        Map<ResourceLocation, Float> equipList = new HashMap<>();

        // Find all equipment of this type from the datapack-driven equipment data
        for (EquipDefinition def : collectDevelopableCandidates(EquipDataRegistry.server().all(), type)) {
            int totalMat = totalMats;
            // Scale small build resolution
            if (buildType == 0) {
                totalMat = (int) (totalMats * 15.625F);
            }

            int meanDist = Mth.abs(totalMat - def.rareMean());
            float prob = CalcHelper.getNormDist(meanDist);
            equipList.put(def.id(), prob);
            LogHelper.debug("DEBUG: roll equip: ID " + def.id() +
                    " MEAN " + def.rareMean() +
                    " MD " + meanDist + " PR " + prob);
        }

        ResourceLocation rollResult = weightedRoll(equipList, random);

        // Calculate enchant level based on total materials
        int enchLv = 0;
        if (buildType == 0) { // small: max 256
            if (totalMats > 220)
                enchLv = 3;
            else if (totalMats > 200)
                enchLv = 2;
            else if (totalMats > 180)
                enchLv = 1;
        } else { // large: max 4000
            if (totalMats > 3500)
                enchLv = 3;
            else if (totalMats > 3000)
                enchLv = 2;
            else if (totalMats > 2000)
                enchLv = 1;
        }

        return getItemStackFromId(rollResult, enchLv);
    }

    static List<EquipDefinition> collectDevelopableCandidates(Collection<EquipDefinition> definitions, int type) {
        return definitions.stream()
                .filter(definition -> definition.rollType() == type)
                .filter(definition -> definition.availability().canDevelop())
                .toList();
    }

    /**
     * Weighted random roll from probability map.
     *
     * @return the key that was rolled, or {@code null}
     */
    private static <T> T weightedRoll(Map<T, Float> probList, RandomSource random) {
        float totalProb = 0F;
        for (float p : probList.values()) {
            totalProb += p;
        }

        if (totalProb <= 0F)
            return null;

        float roll = random.nextFloat() * totalProb;
        float sumProb = 0.0125F; // small offset to prevent float comparison bug

        for (Map.Entry<T, Float> entry : probList.entrySet()) {
            sumProb += entry.getValue();
            if (sumProb > roll) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Convert a datapack equipment ID to an ItemStack.
     */
    private static ItemStack getItemStackFromId(ResourceLocation definitionId, int enchLv) {
        if (definitionId == null) {
            return ItemStack.EMPTY;
        }
        EquipDefinition definition = EquipDataRegistry.server().get(definitionId);
        if (definition == null) {
            ShinColle.LOGGER.warn("No ship equipment definition for {}; returning an empty stack", definitionId);
            return ItemStack.EMPTY;
        }
        return createItemStack(definition, enchLv);
    }

    /** Create an equipment stack from one loaded or test definition. */
    public static ItemStack createItemStack(EquipDefinition definition, int enchLv) {
        if (!ForgeRegistries.ITEMS.containsKey(definition.item())) {
            ShinColle.LOGGER.warn("Ship equipment {} references unregistered item {}; returning an empty stack",
                    definition.id(), definition.item());
            return ItemStack.EMPTY;
        }
        Item registeredItem = ForgeRegistries.ITEMS.getValue(definition.item());
        if (registeredItem == null) {
            ShinColle.LOGGER.warn("Ship equipment {} could not resolve registered item {}; returning an empty stack",
                    definition.id(), definition.item());
            return ItemStack.EMPTY;
        }
        ItemStack item = new ItemStack(registeredItem);
        BasicEquip.setEquipMeta(item, definition.variant());
        int enchType = definition.enchantType() - 1;

        // Apply random enchantments based on enchant level
        if (enchLv > 0) {
            applyRandomEnchantToEquip(item, enchType, enchLv);
        }

        LogHelper.debug("DEBUG: equip calc: get itemstack: definition=" + definition.id()
                + " item=" + definition.item() + " variant=" + definition.variant()
                + " enchLv=" + enchLv + " stack=" + item);
        return item;
    }

    /**
     * Apply random enchantments to equipment based on level and type.
     * <p>
     * enchLv determines the number of enchantments rolled:
     * 1: 40% chance of 1 enchant
     * 2: 30% chance of 1, 30% chance of 2
     * 3: 30% chance of 1, 30% chance of 2, 20% chance of 3
     *
     * @param stack    the equipment ItemStack to enchant
     * @param enchType 0=weapon, 1=armor, 2=misc
     * @param enchLv   enchantment level (1-3)
     */
    private static void applyRandomEnchantToEquip(ItemStack stack, int enchType, int enchLv) {
        if (stack.isEmpty() || enchLv <= 0)
            return;
        if (enchType < 0 || enchType >= ENCHANT_TABLE.length)
            return;

        RandomSource rand = RandomSource.create();
        int enchNum = 0;
        int ranNum = rand.nextInt(10);

        enchNum = switch (enchLv) {
            case 1 -> ranNum > 5 ? 1 : 0;
            case 2 -> ranNum > 6 ? 2 : ranNum > 3 ? 1 : 0;
            case 3 -> ranNum > 7 ? 3 : ranNum > 4 ? 2 : ranNum > 1 ? 1 : 0;
            default -> enchNum;
        };

        if (enchNum <= 0)
            return;

        String[] enchIds = ENCHANT_TABLE[enchType];
        java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchMap = new java.util.HashMap<>();

        for (int i = 0; i < enchNum; i++) {
            String enchId = enchIds[rand.nextInt(enchIds.length)];
            net.minecraft.resources.ResourceLocation enchRL = new net.minecraft.resources.ResourceLocation(enchId);
            net.minecraft.world.item.enchantment.Enchantment ench = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS
                    .getValue(enchRL);

            if (ench != null) {
                if (enchMap.containsKey(ench)) {
                    // Same enchant rolled again: increase level (capped at max)
                    int lv = enchMap.get(ench) + 1;
                    if (lv > ench.getMaxLevel())
                        lv = ench.getMaxLevel();
                    enchMap.put(ench, lv);
                } else {
                    enchMap.put(ench, 1);
                }
            }
        }

        // Apply enchantments to the item
        net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(enchMap, stack);
    }

    /**
     * Calculate equip stats with enchantment effect.
     *
     * @param enchantType enchant category (1=weapon, 2=armor, 3=misc)
     * @param raw       raw equipment stats
     * @param enchant   enchantment effect values
     * @return enchanted equipment stats
     */
    public static ShipAttributeValues calcEquipStatWithEnchant(int enchantType, ShipAttributeValues raw,
                                                                ShipAttributeValues enchant) {
        ShipAttributeLayout rawLayout = raw.layout();
        ShipAttributeLayout enchantLayout = enchant.layout();
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(rawLayout);
        for (ResourceLocation id : rawLayout.ids()) {
            ShipAttributeType type = rawLayout.type(id);
            ResourceLocation effectId = type.enchantEffectSource(id);
            float effect = enchantLayout.indexOf(effectId) < 0 ? 0F : enchant.get(effectId);
            float value = applyEnchantRule(type.enchantRule(), enchantType, raw.get(id), effect);
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Equipment enchant result is non-finite for " + id);
            }
            result.set(id, value);
        }

        boolean hasEnchantEffect = enchant.asMap().values().stream().anyMatch(value -> value != 0F);
        if (hasEnchantEffect) {
            if (enchantType == 1) {
                LogHelper.diag("DIAG: enchant apply enchantType=1 target=attack atkBonus="
                        + getOrZero(enchant, CoreShipAttributes.ATK_L));
            } else if (enchantType == 2) {
                LogHelper.diag("DIAG: enchant apply enchantType=2 target=defense defBonus="
                        + getOrZero(enchant, CoreShipAttributes.DEF));
            } else {
                LogHelper.diag("DIAG: enchant apply enchantType=" + enchantType + " target=other");
            }
        }

        return result.build();
    }

    private static float getOrZero(ShipAttributeValues values, ResourceLocation id) {
        return values.layout().indexOf(id) < 0 ? 0F : values.get(id);
    }

    private static float applyEnchantRule(ShipAttributeEnchantRule rule, int enchantType,
                                          float raw, float effect) {
        return switch (rule) {
            case NONE -> raw;
            case MULTIPLY -> raw * (1F + effect);
            case WEAPON_MULTIPLY -> enchantType == 1 ? raw * (1F + effect) : raw;
            case ARMOR_MULTIPLY -> enchantType == 2 ? raw * (1F + effect) : raw;
            case SIGNED_MULTIPLY -> raw < 0F
                    ? raw * Math.max(0F, 1F - effect)
                    : raw * (1F + effect);
            case WEAPON_ADDITIVE -> enchantType == 1 ? raw + effect : raw;
            case NON_WEAPON_ADDITIVE -> enchantType == 1 ? raw : raw + effect;
        };
    }
}
