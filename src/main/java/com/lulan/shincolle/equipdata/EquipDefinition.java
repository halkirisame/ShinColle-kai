package com.lulan.shincolle.equipdata;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A single ship equipment's data, as loaded from a
 * {@code data/<domain>/equipment/*.json} file. Replaces the old {@code
 * Values.EquipAttrsMain}/{@code EquipAttrsMisc} static maps - one JSON file
 * per equipment instead of one line in a 92-entry hardcoded table.
 *
 * @param id               definition ID derived from the datapack file path
 * @param item             registered item used to create the equipment stack
 * @param variant          value stored in the stack's {@code EquipMeta} tag
 * @param equipType        numeric {@link com.lulan.shincolle.reference.ID.EquipType}
 *                         value parsed from the JSON {@code equip_type}
 * @param legacyEquipId    old {@code EquipType + EquipSubID * 100} identifier,
 *                         or {@code null} for definitions that do not expose one
 * @param stats            length {@link com.lulan.shincolle.reference.unitclass.Attrs#AttrsLength},
 *                         indexed by {@link com.lulan.shincolle.reference.ID.Attrs}.
 * @param compatible       ship types this equipment can be worn on -
 *                         {@code "cannon"}, {@code "aircraft"}, or both (the old
 *                         {@code EQUIP_TYPE == 2} "misc, fits either" case).
 * @param enchantType      0=none, 1=weapon, 2=armor, 3=misc - the old {@code
 *                         ENCH_TYPE}, kept numeric since it's only ever used as an
 *                         index/branch, never displayed.
 * @param developMaterial  construction material key: {@code abyss_metal},
 *                         {@code ammo}, {@code abyss_metal_1}, or {@code grudge}.
 * @param developAmount    the old {@code DEVELOP_NUM} (material amount, drives
 *                         which shipyard tier is shown in the tooltip).
 * @param rareMean         the old {@code RARE_MEAN} (roll target for the
 *                         shipyard's weighted-random construction).
 * @param rollType         the old {@code RARE_TYPE} - which {@code EquipType}
 *                         category this rolls under in {@code EquipCalc}'s
 *                         construction tables. Usually equal to {@code equipId %
 *                         100} but kept explicit since nothing guarantees that.
 */
public record EquipDefinition(
        ResourceLocation id,
        ResourceLocation item,
        int variant,
        int equipType,
        Integer legacyEquipId,
        float[] stats,
        List<String> compatible,
        int enchantType,
        String developMaterial,
        int developAmount,
        int rareMean,
        int rollType
) {

    public boolean isCompatibleWith(String tag) {
        return compatible.contains(tag);
    }

    /**
     * The old numeric {@code EQUIP_TYPE} (0=none, 1=cannon, 2=both, 3=aircraft)
     * that {@code compatible} replaced. Kept around because a couple of call
     * sites (currently ship-side compatibility checks) still branch on this
     * numeric form rather than tag membership.
     */
    public int legacyEquipTypeValue() {
        boolean cannon = compatible.contains("cannon");
        boolean aircraft = compatible.contains("aircraft");
        if (cannon && aircraft) {
            return 2;
        }
        if (cannon) {
            return 1;
        }
        if (aircraft) {
            return 3;
        }
        return 0;
    }
}
