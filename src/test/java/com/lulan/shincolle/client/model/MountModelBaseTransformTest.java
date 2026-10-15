package com.lulan.shincolle.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountModelBaseTransformTest {
    // Literal values from upstream 1.10.2 ModelMount<X>.render:
    // {model, scale, translateX, translateY, translateZ}
    private static final Object[][] UPSTREAM = {
            {"AfH", 0.7F, 0F, 1.10F, -0.47F},
            {"BaH", 0.8F, 0F, 0F, 0F},
            {"CaH", 1.1F, 0F, 0.12F, -0.1F},
            {"CaWD", 1.1F, 0F, 0.12F, -0.1F},
            {"HbH", 0.8F, 0F, 1.05F, 0F},
            {"IsH", 0.7F, 0F, 0.7F, 0F},
            {"MiH", 0.97F, 0F, 0.08F, 0F},
            {"SuH", 0.6F, 0F, 1F, 0F},
    };

    @Test
    void mountRenderUsesUpstreamScaleAndTranslate() throws Exception {
        for (Object[] row : UPSTREAM) {
            String name = "ModelMount" + row[0];
            Class<?> type = Class.forName(MountModelBaseTransformTest.class.getPackageName() + "." + name);
            ModelPart root = ((LayerDefinition) type.getMethod("createBodyLayer").invoke(null)).bakeRoot();
            ShipModelBaseAdv<?> model = (ShipModelBaseAdv<?>) type.getConstructor(ModelPart.class).newInstance(root);
            model.prepareMobModel(null, 0F, 0F, 0F);

            assertEquals((Float) row[1], model.getScale(), 0F, name);
            assertEquals((Float) row[3], model.getOffsetY(), 0F, name);
            float scale = (Float) row[1];
            PoseStack expected = new PoseStack();
            expected.scale(scale, scale, scale);
            expected.translate((Float) row[2], (Float) row[3], (Float) row[4]);
            List<Double> expectedVertices = new ArrayList<>();
            List<String> parts = row[0].equals("CaWD")
                    ? List.of("BodyMain", "GlowBodyMain", "GlowBodyMain2") : List.of("BodyMain", "GlowBodyMain");
            for (String part : parts) {
                root.getChild(part).render(expected, sink(expectedVertices), 0, 0);
            }

            List<Double> actualVertices = new ArrayList<>();
            PoseStack actual = new PoseStack();
            model.renderToBuffer(actual, sink(actualVertices), 0, 0, 1F, 1F, 1F, 1F);
            assertTrue(expectedVertices.size() > 0, name);
            assertEquals(expectedVertices.size(), actualVertices.size(), name);
            for (int i = 0; i < expectedVertices.size(); i++) {
                assertEquals(expectedVertices.get(i), actualVertices.get(i), 0.000001, name + " vertex " + i);
            }
        }
    }

    @Test
    void mountPoseTranslationIsAppliedInBothUpstreamScopes() throws Exception {
        // Upstream: RenderLivingBase called setRotationAngles in the outer scope, and render() called it again after
        // scale/translate. BaH translate(0F, 0.2F, 0F); HbH translate(0F, 0.12F, 0F) only while sitting.
        Object[][] cases = {
                {"BaH", 0.8F, 0F, 0F, 0F, 0.2F},
                {"HbH", 0.8F, 0F, 1.05F, 0F, 0.12F},
        };
        for (Object[] row : cases) {
            String name = "ModelMount" + row[0];
            Class<?> type = Class.forName(MountModelBaseTransformTest.class.getPackageName() + "." + name);
            ModelPart root = ((LayerDefinition) type.getMethod("createBodyLayer").invoke(null)).bakeRoot();
            ShipModelBaseAdv<?> model = (ShipModelBaseAdv<?>) type.getConstructor(ModelPart.class).newInstance(root);
            model.prepareMobModel(null, 0F, 0F, 0F);
            float pose = (Float) row[5];
            model.translateLegacyPose(0F, pose, 0F);

            float scale = (Float) row[1];
            PoseStack expected = new PoseStack();
            expected.translate(0F, pose, 0F);
            expected.scale(scale, scale, scale);
            expected.translate((Float) row[2], (Float) row[3], (Float) row[4]);
            expected.translate(0F, pose, 0F);
            List<Double> expectedVertices = new ArrayList<>();
            root.getChild("BodyMain").render(expected, sink(expectedVertices), 0, 0);
            root.getChild("GlowBodyMain").render(expected, sink(expectedVertices), 0, 0);

            List<Double> actualVertices = new ArrayList<>();
            model.renderToBuffer(new PoseStack(), sink(actualVertices), 0, 0, 1F, 1F, 1F, 1F);
            assertTrue(expectedVertices.size() > 0, name);
            assertEquals(expectedVertices.size(), actualVertices.size(), name);
            for (int i = 0; i < expectedVertices.size(); i++) {
                assertEquals(expectedVertices.get(i), actualVertices.get(i), 0.000001, name + " vertex " + i);
            }
        }
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
}
