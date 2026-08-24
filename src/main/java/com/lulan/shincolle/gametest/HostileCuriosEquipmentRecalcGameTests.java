package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.ShipInnateAttackEffects;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.MissileData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for hostile Curios equipment state being reset before
 * each recalculation. The test deliberately seeds stale state without a
 * Curios stack, then verifies the recalc removes every equipment-owned part.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostileCuriosEquipmentRecalcGameTests {

    private HostileCuriosEquipmentRecalcGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileEquipmentRecalculationClearsStaleCuriosState(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShipHostile hostile) || hostile.getAttrs() == null) {
            throw new AssertionError("Failed to create hostile ship with attributes");
        }

        hostile.getAttrs().setAttrsEquip(ID.Attrs.ATK_L, 37F);
        ResourceLocation nausea = ResourceLocation.fromNamespaceAndPath("minecraft", "nausea");
        hostile.getAttackEffectMap().put(nausea, new ShipAttackEffect(nausea, 3, 12, 100));
        hostile.setMissileData(2, new MissileData());
        hostile.getMissileData(2).type = 7;
        hostile.getMissileData(2).vel0 = 9F;

        hostile.calcShipAttributesAddEquip();

        for (float value : hostile.getAttrs().getAttrsEquip()) {
            if (value != 0F) {
                throw new AssertionError("Hostile equipment layer retained stale value " + value);
            }
        }
        if (hostile.getAttackEffectMap().containsKey(nausea)) {
            throw new AssertionError("Hostile attack-effect map retained stale Curios effects");
        }
        ShipAttackEffect innate = hostile.getAttackEffectMap().get(ShipInnateAttackEffects.BLINDNESS);
        ShipAttackEffect expectedInnate = new ShipAttackEffect(ShipInnateAttackEffects.BLINDNESS, 0, 40, 25);
        if (hostile.getAttackEffectMap().size() != 1 || !expectedInnate.equals(innate)) {
            throw new AssertionError("Hostile recalculation did not rebuild its intrinsic effect: "
                    + hostile.getAttackEffectMap());
        }
        MissileData missile = hostile.getMissileData(2);
        if (missile.type != 0 || missile.movetype != -1 || missile.vel0 != 0.5F
                || missile.accY1 != 1.04F || missile.accY2 != 1.04F) {
            throw new AssertionError("Hostile missile data retained stale Curios contribution");
        }
        helper.succeed();
    }
}
