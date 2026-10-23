package com.lulan.shincolle.client.model;

import com.lulan.shincolle.entity.IShipEmotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipHeldItemModelTest {
    private static final List<String> HANDS = List.of(
            "ModelAirfieldHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBattleshipHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBattleshipNagato|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBattleshipRe|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBattleshipRu|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBattleshipTa|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBattleshipYamato|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBBHaruna|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBBHiei|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBBKirishima|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelBBKongou|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCAHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCarrierAkagi|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCarrierHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCarrierKaga|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCarrierWDemon|BodyMain/ArmRight01/ArmRight02/ArmRight03|BodyMain/ArmLeft01/ArmLeft02/ArmLeft03",
            "ModelCarrierWo|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCruiserAtago|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCruiserTakao|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCruiserTatsuta|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelCruiserTenryuu|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelDestroyerAkatsuki|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelDestroyerHibiki|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelDestroyerHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelDestroyerIkazuchi|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelDestroyerInazuma|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelDestroyerShimakaze|BodyMain/ArmRight|BodyMain/ArmLeft",
            "ModelHarbourHime|BodyMain/ArmRight01/ArmRight02/ArmRight03/ArmRight04/ArmRight05/ArmRight06|"
                    + "BodyMain/ArmLeft01/ArmLeft02/ArmLeft03/ArmLeft04/ArmLeft05/ArmLeft06",
            "ModelHeavyCruiserNe|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelHeavyCruiserRi|BodyMain/ArmRight|BodyMain/ArmLeft",
            "ModelIsolatedHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelMidwayHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelNorthernHime|BodyMain/ArmRight01/ArmRight02/ArmRight03/ArmRight04/ArmRight05|"
                    + "BodyMain/ArmLeft01/ArmLeft02/ArmLeft03/ArmLeft04/ArmLeft05",
            "ModelSSNH|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelSubmHime|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelSubmKa|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelSubmRo500|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelSubmSo|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelSubmU511|BodyMain/ArmRight01/ArmRight02/ArmRight03|BodyMain/ArmLeft01/ArmLeft02/ArmLeft03",
            "ModelSubmYo|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02",
            "ModelTransportWa|BodyMain/ArmRight01/ArmRight02|BodyMain/ArmLeft01/ArmLeft02");

    @Test
    void everyLegacyHandChainReachesTheActualAnimatedParts() throws Exception {
        for (String entry : HANDS) {
            String[] fields = entry.split("\\|");
            Fixture fixture = create(fields[0]);
            for (HumanoidArm side : HumanoidArm.values()) {
                String[] names = fields[side == HumanoidArm.RIGHT ? 1 : 2].split("/");
                ModelPart[] chain = fixture.model().getArmForSide(side);
                assertNotNull(chain, fields[0] + " " + side);
                assertEquals(names.length, chain.length, fields[0] + " " + side);
                ModelPart part = fixture.root();
                for (int i = 0; i < names.length; i++) {
                    part = part.getChild(names[i]);
                    assertSame(part, chain[i], fields[0] + " " + names[i]);
                }
            }
        }
    }

    @Test
    void modelScaleAffectsArmPivotsAsWellAsItemGeometry() throws Exception {
        ShipModelBaseAdv<?> model = create("ModelBBKongou").model();
        model.scale = 0.45F;
        for (HumanoidArm side : HumanoidArm.values()) {
            ModelPart[] chain = model.getArmForSide(side);
            assertNotNull(chain);
            chain[0].yRot = 0.3F;
            chain[1].xRot = 0.4F;
            chain[2].zRot = -0.2F;
            PoseStack expected = new PoseStack();
            // Legacy postRender used the model scale for each pivot, before the final item scale.
            for (ModelPart part : chain) {
                expected.translate(part.x * model.scale / 16F, part.y * model.scale / 16F,
                        part.z * model.scale / 16F);
                expected.mulPose(Axis.ZP.rotation(part.zRot));
                expected.mulPose(Axis.YP.rotation(part.yRot));
                expected.mulPose(Axis.XP.rotation(part.xRot));
            }
            expected.scale(model.scale, model.scale, model.scale);
            PoseStack actual = new PoseStack();
            assertTrue(model.applyHeldItemArmTransform(actual, side));
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 4; column++) {
                    assertEquals(expected.last().pose().get(column, row),
                            actual.last().pose().get(column, row), 0.000001F);
                }
            }
        }
    }

    @Test
    void originalItemOffsetsAndPoseSpecificAdjustmentsAreKept() throws Exception {
        ShipModelBaseAdv<?> kongou = create("ModelBBKongou").model();
        assertArrayEquals(new float[]{0.1F, 0.86F, -0.12F},
                kongou.getHeldItemOffset(input(false, false), HumanoidArm.RIGHT, 0));
        ShipModelBaseAdv<?> northern = create("ModelNorthernHime").model();
        assertArrayEquals(new float[]{0.05F, 0.9F, -0.3F},
                northern.getHeldItemOffset(input(true, false), HumanoidArm.RIGHT, 0));
        assertArrayEquals(new float[]{0.05F, 1.1F, -0.2F},
                northern.getHeldItemOffset(input(false, true), HumanoidArm.RIGHT, 0));
        assertArrayEquals(new float[]{-30F, 30F, -60F},
                northern.getHeldItemRotate(input(true, false), HumanoidArm.RIGHT, 0));
        assertArrayEquals(new float[]{0.07F, 1.02F, -0.05F},
                northern.getHeldItemOffset(input(true, false), HumanoidArm.LEFT, 0));
        assertArrayEquals(new float[]{-0.03F, 0.93F, 0.1F},
                create("ModelSubmRo500").model().getHeldItemOffset(input(true, false), HumanoidArm.RIGHT, 0));
        assertArrayEquals(new float[]{0.06F, 0.81F, -0.1F},
                create("ModelDestroyerIkazuchi").model().getHeldItemOffset(input(false, true), HumanoidArm.RIGHT, 0));
    }

    private static IShipEmotion input(boolean sprinting, boolean riding) {
        return (IShipEmotion) Proxy.newProxyInstance(IShipEmotion.class.getClassLoader(),
                new Class<?>[]{IShipEmotion.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getIsSprinting" -> sprinting;
                    case "getIsRiding" -> riding;
                    default -> throw new AssertionError("Unexpected input " + method.getName());
                });
    }

    private static Fixture create(String name) throws Exception {
        Class<?> type = Class.forName(ShipHeldItemModelTest.class.getPackageName() + "." + name);
        ModelPart root = ((LayerDefinition) type.getMethod("createBodyLayer").invoke(null)).bakeRoot();
        return new Fixture((ShipModelBaseAdv<?>) type.getConstructor(ModelPart.class).newInstance(root), root);
    }

    private record Fixture(ShipModelBaseAdv<?> model, ModelPart root) { }
}
