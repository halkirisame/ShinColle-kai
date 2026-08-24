package com.lulan.shincolle.entity;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/** Regression coverage for finite equipment aggregation and scale isolation. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentAggregationGameTests {

    private EquipmentAggregationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAggregationRejectsOnlyTheOverflowingContribution(GameTestHelper helper) {
        float[] target = new float[Attrs.AttrsLength];
        float[] first = new float[Attrs.AttrsLength];
        first[0] = Float.MAX_VALUE;
        BasicEntityShip.addFiniteEquipmentContribution(target, first);

        float[] beforeOverflow = target.clone();
        float[] overflow = new float[Attrs.AttrsLength];
        overflow[0] = Float.MAX_VALUE;
        expectIllegalArgument(() -> BasicEntityShip.addFiniteEquipmentContribution(target, overflow));
        if (!Arrays.equals(beforeOverflow, target)) {
            throw new AssertionError("Overflowing equipment contribution partially changed the target layer");
        }

        float[] later = new float[Attrs.AttrsLength];
        later[1] = 4F;
        BasicEntityShip.addFiniteEquipmentContribution(target, later);
        if (target[0] != Float.MAX_VALUE || target[1] != 4F) {
            throw new AssertionError("A later valid equipment contribution was not applied after overflow rejection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentScaleKeepsOnlyTheOverflowingAxisUnscaled(GameTestHelper helper) {
        float[] values = new float[Attrs.AttrsLength];
        values[0] = Float.MAX_VALUE;
        values[1] = 3F;
        if (BasicEntityShip.applyFiniteEquipmentScale(values, 0, 2D, CoreShipAttributes.HP)) {
            throw new AssertionError("Overflowing equipment scale was accepted");
        }
        if (!BasicEntityShip.applyFiniteEquipmentScale(values, 1, 2D, CoreShipAttributes.ATK_L)) {
            throw new AssertionError("Finite equipment scale was rejected");
        }
        if (values[0] != Float.MAX_VALUE || values[1] != 6F) {
            throw new AssertionError("Equipment scale isolation did not retain/apply the expected axes");
        }
        helper.succeed();
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected non-finite equipment aggregation to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected rejection.
        }
    }
}
