package com.lulan.shincolle.client.model;

import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.entity.IShipFloating;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.reference.ID;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPoseTranslationTest {
    @Test
    void originalDestroyerRenderScopesArePreserved() throws Exception {
        for (String name : List.of("ModelDestroyerI", "ModelDestroyerRo", "ModelDestroyerHa", "ModelDestroyerNi")) {
            Fixture fixture = create(name);
            ShipModelBaseAdv<?> model = fixture.model();
            model.prepareMobModel(null, 0F, 0F, 0F);
            model.applyDeadPose(0F, 0F, 1F, 0F, 0F, input(0, false, false, false));
            model.syncRotationGlowPart();
            PoseStack expected = new PoseStack();
            // Literal render order from upstream 1.10.2 ModelDestroyer{I,Ro,Ha,Ni}.render.
            String body = "Back";
            if (name.equals("ModelDestroyerI")) {
                body = "PBack";
                expected.translate(0F, 0.75F, 0F);
                expected.translate(0F, 0.75F, 0F);
                expected.scale(0.45F, 0.4F, 0.4F);
                expected.mulPose(Axis.YP.rotationDegrees(90F));
            } else if (name.equals("ModelDestroyerRo")) {
                model.scale = 0.45F;
                model.offsetY = 2.1F;
                expected.translate(0F, 0.45F, 0F);
                expected.scale(0.45F, 0.45F, 0.45F);
                expected.translate(0F, 2.1F, 0F);
                expected.translate(0F, 0.45F, 0F);
            } else {
                boolean ha = name.equals("ModelDestroyerHa");
                model.scale = ha ? 0.45F : 0.35F;
                model.offsetY = ha ? 1F : 1.1F;
                expected.translate(0F, ha ? 0.5F : 0.75F, 0F);
                expected.translate(0F, ha ? 1F : 1.1F, 0F);
                expected.scale(model.scale, model.scale, model.scale);
                expected.translate(0F, ha ? 0.5F : 0.75F, 0F);
            }
            List<Double> expectedVertices = new ArrayList<>();
            fixture.root().getChild(body).render(expected, sink(expectedVertices), 0, 0);
            fixture.root().getChild("Glow" + body).render(expected, sink(expectedVertices), 0, 0);
            List<Double> actualVertices = new ArrayList<>();
            PoseStack actual = new PoseStack();
            model.renderToBuffer(actual, sink(actualVertices), 0, 0, 1F, 1F, 1F, 1F);
            assertTrue(expectedVertices.size() > 0, name);
            assertEquals(expectedVertices.size(), actualVertices.size(), name);
            for (int i = 0; i < expectedVertices.size(); i++) {
                assertEquals(expectedVertices.get(i), actualVertices.get(i), 0.000001, name + " vertex " + i);
            }
            assertEquals(0F, actual.last().pose().m31(), "render must restore the caller's matrix");
        }
    }

    @Test
    void hibikiOffsetsAndTranslationResetBetweenEntities() throws Exception {
        Fixture fixture = create("ModelDestroyerHibiki");
        ShipModelBaseAdv<?> model = fixture.model();
        ModelPart butt = fixture.root().getChild("BodyMain").getChild("Butt");
        for (int frame = 0; frame < 100; frame++) {
            model.prepareMobModel(null, 0F, 0F, 0F);
            model.applyNormalPose(0F, 0F, 1F, 0F, 0F, input(0, true, false, false));
            assertEquals(butt.getInitialPose().y - 1.6F, butt.y, 0.000001F);
            assertEquals(0.3F, translationY(model), 0.000001F);
            model.prepareMobModel(null, 0F, 0F, 0F);
            model.applyNormalPose(0F, 0F, 1F, 0F, 0F, input(0, false, false, false));
            assertEquals(butt.getInitialPose().y, butt.y, 0.000001F);
            assertEquals(0F, translationY(model), 0.000001F);
        }
    }

    @Test
    void kongouSittingUsesOriginalSizeBranches() throws Exception {
        ShipModelBaseAdv<?> model = create("ModelBBKongou").model();
        float[] expected = {0.31F, 0.46F, 0.56F, 0.63F};
        for (int size = 0; size < expected.length; size++) {
            model.prepareMobModel(null, 0F, 0F, 0F);
            model.applyNormalPose(0F, 0F, 1F, 0F, 0F, input(size, true, false, false));
            assertEquals(expected[size], translationY(model), 0.000001F);
        }
    }

    @Test
    void ro500DistinguishesGroundAndWaterSitting() throws Exception {
        ShipModelBaseAdv<?> model = create("ModelSubmRo500").model();
        for (boolean bored : new boolean[]{false, true}) {
            model.prepareMobModel(null, 0F, 0F, 0F);
            model.applyNormalPose(0F, 0F, 1F, 0F, 0F, input(0, true, bored, false));
            assertEquals(bored ? 0.43F : 0.41F, translationY(model), 0.000001F);
            model.prepareMobModel(null, 0F, 0F, 0F);
            model.applyNormalPose(0F, 0F, 1F, 0F, 0F, input(0, true, bored, true));
            float bob = net.minecraft.util.Mth.cos(0.08F) * 0.05F + 0.025F;
            assertEquals(bob + (bored ? -0.21F : -0.22F), translationY(model), 0.000001F);
        }
    }

    private static float translationY(ShipModelBaseAdv<?> model) {
        PoseStack pose = new PoseStack();
        model.applyLegacyPoseTranslation(pose);
        return pose.last().pose().m31();
    }

    private static Fixture create(String name) throws Exception {
        Class<?> type = Class.forName(LegacyPoseTranslationTest.class.getPackageName() + "." + name);
        ModelPart root = ((LayerDefinition) type.getMethod("createBodyLayer").invoke(null)).bakeRoot();
        return new Fixture((ShipModelBaseAdv<?>) type.getConstructor(ModelPart.class).newInstance(root), root);
    }

    private static VertexConsumer sink(List<Double> vertices) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("vertex")) {
                        for (int i = 0; i < 3; i++) {
                            vertices.add(((Number) args[i]).doubleValue());
                        }
                    }
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
    }

    private static IShipEmotion input(int size, boolean sitting, boolean bored, boolean water) {
        RandomSource random = RandomSource.create(0);
        return (IShipEmotion) Proxy.newProxyInstance(IShipEmotion.class.getClassLoader(),
                new Class<?>[]{IShipEmotion.class, IShipRiderType.class, IShipFloating.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getScaleLevel" -> size;
                        case "getTickExisted" -> 1;
                        case "getIsSitting" -> sitting;
                        case "getShipDepth" -> water ? 1D : 0D;
                        case "getStateEmotion" -> (int) args[0] == ID.S.Emotion && bored ? (int) ID.Emotion.BORED : 0;
                        case "getRand" -> random;
                        default -> {
                            if (method.getReturnType() == boolean.class) { yield false; }
                            if (method.getReturnType() == int.class) { yield 0; }
                            if (method.getReturnType() == float.class) { yield 0F; }
                            if (method.getReturnType() == double.class) { yield 0D; }
                            yield null;
                        }
                    };
                });
    }

    private record Fixture(ShipModelBaseAdv<?> model, ModelPart root) { }
}
