package com.lulan.shincolle.client.visual;

import com.google.gson.JsonParser;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import com.lulan.shincolle.visualdata.ShipVisualPolicy;
import com.lulan.shincolle.visualdata.ShipVisualPresentation;
import com.lulan.shincolle.visualdata.ShipVisualRenderMath;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipVisualReloadTest {
    private static ResourceLocation id(String path) {
        return new ResourceLocation("shincolle_kai", path);
    }

    private static Map<ResourceLocation, byte[]> files() throws IOException {
        return ShipVisualTestResources.files();
    }

    private static ResourceManager resources(Map<ResourceLocation, byte[]> files) {
        return ShipVisualTestResources.resources(files);
    }

    private static void reload(ResourceManager resources) {
        ShipVisualTestResources.reload(resources);
    }

    @Test
    void realGeckoLibReadsKashimaFaceUvsAndAllNineClipsAndIsolatesMutableBones() throws IOException {
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        assertNotSame(ShipVisuals.snapshot().placeholder(), visual);
        assertEquals("Head", visual.rig().get("head"));
        assertEquals(9, visual.clips().size());
        assertTrue(visual.clips().containsKey(ShipVisualEvent.HEAVY_ATTACK));
        var first = new ShipVisualGeoModel(visual).getBakedModel(visual.definition().model());
        var second = new ShipVisualGeoModel(visual).getBakedModel(visual.definition().model());
        assertNotSame(first, second);
        var head = first.getBone("Head").orElseThrow();
        var otherHead = second.getBone("Head").orElseThrow();
        float original = otherHead.getRotX();
        head.setRotX(original + 1);
        assertEquals(original, otherHead.getRotX());
    }

    @Test
    void invalidVisualsAndOversizedBakedGeometryChoosePlaceholderWithoutRejectingGoodVisual() throws IOException {
        var files = files();
        var definition = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")),
                StandardCharsets.UTF_8)).getAsJsonObject();
        definition.addProperty("model", "shincolle_kai:ship_visual/too_many.geo.json");
        files.put(id("shincolle_kai/visuals/too_many.json"), definition.toString().getBytes(StandardCharsets.UTF_8));
        var geometry = JsonParser.parseString(new String(files.get(id("geo/kashima.geo.json")), StandardCharsets.UTF_8));
        var bones = geometry.getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        for (int i = bones.size(); i < 129; i++) bones.add(JsonParser.parseString("{\"name\":\"extra_" + i + "\",\"pivot\":[0,0,0]}"));
        files.put(id("ship_visual/too_many.geo.json"), geometry.toString().getBytes(StandardCharsets.UTF_8));
        files.put(id("shincolle_kai/visuals/bad.json"), "{bad}".getBytes(StandardCharsets.UTF_8));
        reload(resources(files));
        var snapshot = ShipVisuals.snapshot();
        assertEquals(1, snapshot.visuals().size());
        assertSame(snapshot.placeholder(), ShipVisuals.resolve(id("too_many")));
        assertSame(snapshot.placeholder(), ShipVisuals.resolve(id("bad")));
        assertSame(snapshot.placeholder(), ShipVisuals.resolve(id("absent")));
        assertNotSame(snapshot.placeholder(), ShipVisuals.resolve(id("kashima")));
    }

    @Test
    void preparationFailurePreservesEntirePublishedGenerationAndSuccessfulRemovalIsAtomic() throws IOException {
        var files = files();
        reload(resources(files));
        var previous = ShipVisuals.snapshot();
        files.remove(id("ship_visual/placeholder.geo.json"));
        reload(resources(files));
        assertSame(previous, ShipVisuals.snapshot());
        files = files();
        files.remove(id("shincolle_kai/visuals/kashima.json"));
        reload(resources(files));
        assertNotSame(previous, ShipVisuals.snapshot());
        assertSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("kashima")));
        assertEquals(1, previous.visuals().size());
    }

    @Test
    void missingClipsAreIgnoredWhileMissingTextureAndMalformedGeometryRejectOnlyTheirVisual() throws IOException {
        var files = files();
        var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")),
                StandardCharsets.UTF_8)).getAsJsonObject();
        json.getAsJsonObject("animations").addProperty("sit", "missing.sit");
        files.put(id("shincolle_kai/visuals/kashima.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        var noTexture = json.deepCopy();
        noTexture.addProperty("texture", "shincolle_kai:textures/entity/absent.png");
        files.put(id("shincolle_kai/visuals/no_texture.json"), noTexture.toString().getBytes(StandardCharsets.UTF_8));
        var noGeometry = json.deepCopy();
        noGeometry.addProperty("model", "shincolle_kai:ship_visual/malformed.geo.json");
        files.put(id("shincolle_kai/visuals/broken_geo.json"), noGeometry.toString().getBytes(StandardCharsets.UTF_8));
        files.put(id("ship_visual/malformed.geo.json"), "{broken}".getBytes(StandardCharsets.UTF_8));
        reload(resources(files));
        assertEquals(1, ShipVisuals.snapshot().visuals().size());
        assertFalse(ShipVisuals.resolve(id("kashima")).clips().containsKey(ShipVisualEvent.SIT));
        assertSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("no_texture")));
        assertSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("broken_geo")));
    }

    @Test
    void absentPosturesFallThroughAfterReloadAndWarnOncePerVisualAndEvent() throws IOException {
        var files = files();
        var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")),
                StandardCharsets.UTF_8)).getAsJsonObject();
        var order = java.util.List.of(ShipVisualEvent.NO_FUEL, ShipVisualEvent.CARESS, ShipVisualEvent.RIDE,
                ShipVisualEvent.SIT, ShipVisualEvent.SPRINT, ShipVisualEvent.MOVE, ShipVisualEvent.IDLE);
        var input = new com.lulan.shincolle.visualdata.ShipVisualPosture.Input(true, true, true, true, true, 1, true);
        for (int missing = 0; missing <= order.size(); missing++) {
            files.put(id("shincolle_kai/visuals/posture_missing.json"), json.toString().getBytes(StandardCharsets.UTF_8));
            for (int repeat = 0; repeat < 2; repeat++) {
                reload(resources(files));
                var loaded = ShipVisuals.resolve(id("posture_missing"));
                assertNotSame(ShipVisuals.snapshot().placeholder(), loaded);
                assertEquals(missing == order.size() ? java.util.Optional.empty() : java.util.Optional.of(order.get(missing)),
                        com.lulan.shincolle.visualdata.ShipVisualPosture.select(input, loaded.clips().keySet()));
                for (int index = 0; index < missing; index++) {
                    assertTrue(ShipVisuals.warned("clip:shincolle_kai:posture_missing:" + order.get(index).key()));
                }
            }
            if (missing < order.size()) json.getAsJsonObject("animations").addProperty(order.get(missing).key(), "absent.clip");
        }
    }

    @Test
    void expressionsEquipmentAndNormalFallbackAffectOnlyTheirOwnBones() throws IOException {
        var files = files();
        reload(resources(files));
        var visual = ShipVisuals.resolve(id("kashima"));
        for (String role : new String[]{"face", "mouth", "flush", "equipment"}) assertTrue(visual.rig().containsKey(role));
        ShipVisualGeoModel model = new ShipVisualGeoModel(visual);
        var baked = model.getBakedModel(visual.definition().model());
        for (int face = 0; face < 10; face++) {
            for (int mouth = 0; mouth < 6; mouth++) {
                model.applyPresentation(new ShipVisualPresentation.Expression(face, mouth, true), false);
                for (int i = 0; i < 5; i++) {
                    var bone = baked.getBone("Face" + i).orElseThrow();
                    assertEquals(i != face % 5, bone.isHidden());
                    if (!bone.isHidden()) assertEquals(face >= 5 ? 3.14159F : 0, bone.getRotY());
                }
                for (int i = 0; i < 3; i++) {
                    var bone = baked.getBone("Mouth" + i).orElseThrow();
                    assertEquals(i != mouth % 3, bone.isHidden());
                    if (!bone.isHidden()) assertEquals(mouth >= 3 ? 3.14159F : 0, bone.getRotY());
                }
                assertFalse(baked.getBone("Flush0").orElseThrow().isHidden());
                assertFalse(baked.getBone("Flush1").orElseThrow().isHidden());
                assertTrue(baked.getBone("EquipBase").orElseThrow().isHidden());
                assertTrue(baked.getBone("EquipBase").orElseThrow().isHidingChildren());
                assertFalse(baked.getBone("BodyMain").orElseThrow().isHidden());
                assertFalse(baked.getBone("HatBase").orElseThrow().isHidden());
            }
        }
        model.applyPresentation(new ShipVisualPresentation.Expression(0, 0, false), true);
        assertFalse(baked.getBone("EquipBase").orElseThrow().isHidingChildren());
        assertFalse(baked.getBone("EquipBase").orElseThrow().isHidden());
        assertTrue(baked.getBone("Flush0").orElseThrow().isHidden());

        var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        json.remove("expressions");
        files.put(id("shincolle_kai/visuals/kashima.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        reload(resources(files));
        var fallback = ShipVisuals.resolve(id("kashima"));
        model = new ShipVisualGeoModel(fallback);
        baked = model.getBakedModel(fallback.definition().model());
        model.applyPresentation(new ShipVisualPresentation.Expression(9, 5, true), true);
        for (String slot : new String[]{"face", "mouth", "flush"}) {
            var names = fallback.expressions().get(slot);
            for (int i = 0; i < names.size(); i++) {
                var bone = baked.getBone(names.get(i)).orElseThrow();
                assertEquals(slot.equals("flush") || i != 0, bone.isHidden());
                assertEquals(0, bone.getRotY());
            }
        }
    }

    @Test
    void aMissingSlotFallsBackIndependentlyAndMissingNamedBonesRejectOnlyThatVisual() throws IOException {
        for (String omitted : new String[]{"face", "mouth", "flush"}) {
            var files = files();
            var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            json.getAsJsonObject("expressions").remove(omitted);
            files.put(id("shincolle_kai/visuals/kashima.json"), json.toString().getBytes(StandardCharsets.UTF_8));
            reload(resources(files));
            var visual = ShipVisuals.resolve(id("kashima"));
            var model = new ShipVisualGeoModel(visual);
            var baked = model.getBakedModel(visual.definition().model());
            model.applyPresentation(new ShipVisualPresentation.Expression(9, 5, true), true);
            for (String slot : new String[]{"face", "mouth", "flush"}) {
                var names = visual.expressions().get(slot);
                for (int index = 0; index < names.size(); index++) {
                    boolean visible = slot.equals("flush") ? !slot.equals(omitted)
                            : index == (slot.equals(omitted) ? 0 : names.size() - 1);
                    assertEquals(!visible, baked.getBone(names.get(index)).orElseThrow().isHidden());
                }
            }
            json.getAsJsonObject("expressions").add("face", JsonParser.parseString("[\"absent\"]"));
            files.put(id("shincolle_kai/visuals/broken_expression.json"), json.toString().getBytes(StandardCharsets.UTF_8));
            reload(resources(files));
            assertNotSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("kashima")));
            assertSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("broken_expression")));
        }
    }

    @Test
    void seatedClipPenetrationIsMeasuredAndDrawingOffsetLeavesAnimationAndHeadUntouched() throws IOException {
        var files = files();
        var clip = JsonParser.parseString(new String(files.get(id("animations/kashima.animation.json")), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("animation.kashima.sit").getAsJsonObject("bones");
        assertEquals(-27, clip.getAsJsonObject("root").getAsJsonArray("position").get(1).getAsInt());
        assertEquals(-80, clip.getAsJsonObject("LegLeft01").getAsJsonArray("rotation").get(0).getAsInt());
        assertEquals(-80, clip.getAsJsonObject("LegRight01").getAsJsonArray("rotation").get(0).getAsInt());
        reload(resources(files));
        var visual = ShipVisuals.resolve(id("kashima"));
        ShipVisualGeoModel model = new ShipVisualGeoModel(visual);
        var baked = model.getBakedModel(visual.definition().model());
        var root = baked.getBone(visual.rig().get("root")).orElseThrow();
        var left = baked.getBone(visual.rig().get("leg_left")).orElseThrow();
        var right = baked.getBone(visual.rig().get("leg_right")).orElseThrow();
        float initialLeft = left.getRotX();
        float initialRight = right.getRotX();
        assertEquals(0, model.postureOffset(false));
        // Exact static values from the shipped clip, added to the baked rest rotations as GeckoLib does.
        root.setPosY(-27);
        left.setRotX(initialLeft + 80 * Mth.DEG_TO_RAD);
        left.setRotY(left.getRotY() + 8 * Mth.DEG_TO_RAD);
        right.setRotX(initialRight + 80 * Mth.DEG_TO_RAD);
        right.setRotY(right.getRotY() - 8 * Mth.DEG_TO_RAD);
        var skirt = baked.getBone("Skirt01").orElseThrow();
        skirt.setRotX(skirt.getRotX() + 20 * Mth.DEG_TO_RAD);
        var head = baked.getBone("Head").orElseThrow();
        head.setRotY(0.7F);
        float minimum = ShipVisualPose.minimumSupportY(baked, visual.rig());
        assertTrue(minimum < -0.05F, "uncompensated clip must demonstrably penetrate the ground");
        float offset = model.postureOffset(true);
        System.out.println("Seated support min=" + minimum + ", unscaled lift=" + offset + ", world lift=" + offset * 0.46F);
        assertEquals(0, minimum + offset, 0.00001F);
        for (int level = 0; level <= 3; level++) {
            float scale = ShipVisualRenderMath.scale(visual.definition().scale(), level);
            PoseStack poses = new PoseStack();
            poses.scale(scale, scale, scale);
            poses.translate(0, model.postureOffset(true), 0);
            assertEquals(-minimum * scale, poses.last().pose().m31(), 0.00001F);
            assertEquals(0, minimum * scale + poses.last().pose().m31(), 0.00001F);
        }
        assertTrue(ShipVisualPose.minimumSupportY(baked, Map.of("leg_left", "Butt")) + offset >= -0.00001F,
                "the corrected seated hips and skirt must not penetrate either");
        assertEquals(offset, model.postureOffset(true));
        assertEquals(-27, root.getPosY());
        assertEquals(initialLeft + 80 * Mth.DEG_TO_RAD, left.getRotX());
        assertEquals(0.7F, head.getRotY());
        assertEquals(0, model.postureOffset(false));
    }

    @Test
    void glowAndLodResolveDescendantsAndDoNotOverwriteExpressionOrEquipmentVisibility() throws IOException {
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        assertEquals(Set.of("Face0", "Face1", "Face2", "Face3", "Face4", "Mouth0", "Mouth1", "Mouth2", "Flush0", "Flush1"),
                visual.glowBones());
        assertEquals(13, visual.lodBones().size());
        assertTrue(visual.lodBones().containsAll(Set.of("Hair02a2", "TailL04", "TailR04", "HatRibbon")));
        assertFalse(visual.rig().containsKey("held_item_left"));
        var model = new ShipVisualGeoModel(visual);
        var baked = model.getBakedModel(visual.definition().model());
        model.applyPresentation(new ShipVisualPresentation.Expression(0, 0, false), false);
        for (String name : visual.glowBones()) assertEquals(0xF000F0, model.packedLight(baked.getBone(name).orElseThrow(), 7));
        for (String name : new String[]{"BodyMain", "Head", "Hair", "EquipBase", "held_item"}) {
            assertEquals(7, model.packedLight(baked.getBone(name).orElseThrow(), 7));
        }
        for (String name : new String[]{"Face0", "Face1", "Flush0", "EquipBase", "Hair02a2", "TailL04", "held_item"}) {
            var bone = baked.getBone(name).orElseThrow();
            boolean hidden = bone.isHidden();
            boolean childrenHidden = bone.isHidingChildren();
            assertFalse(model.skipBone(bone, false));
            assertEquals(visual.lodBones().contains(name), model.skipBone(bone, true));
            assertFalse(model.skipBone(bone, false));
            assertEquals(hidden, bone.isHidden());
            assertEquals(childrenHidden, bone.isHidingChildren());
        }
        assertTrue(baked.getBone("Face1").orElseThrow().isHidden());
        assertFalse(baked.getBone("Face0").orElseThrow().isHidden());

        var files = files();
        var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        json.add("glow_bones", JsonParser.parseString("[\"Hair02a1\"]"));
        json.getAsJsonObject("lod").add("hide_bones", JsonParser.parseString("[\"ArmRight01\"]"));
        files.put(id("shincolle_kai/visuals/kashima.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        reload(resources(files));
        visual = ShipVisuals.resolve(id("kashima"));
        assertEquals(Set.of("Hair02a1", "Hair02a2"), visual.glowBones());
        assertTrue(visual.lodBones().contains("held_item"));
        model = new ShipVisualGeoModel(visual);
        assertTrue(model.skipBone(model.getBakedModel(visual.definition().model()).getBone("held_item").orElseThrow(), true));
    }

    @Test
    void aMissingGlowOrLodBoneDisablesOnlyThatFeatureAndReloadRestoresIt() throws IOException {
        for (String feature : new String[]{"glow_bones", "lod"}) {
            var files = files();
            var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (feature.equals("glow_bones")) json.add(feature, JsonParser.parseString("[\"Face0\",\"absent\"]"));
            else json.getAsJsonObject("lod").add("hide_bones", JsonParser.parseString("[\"Hair02a1\",\"absent\"]"));
            files.put(id("shincolle_kai/visuals/partial.json"), json.toString().getBytes(StandardCharsets.UTF_8));
            reload(resources(files));
            var partial = ShipVisuals.resolve(id("partial"));
            assertNotSame(ShipVisuals.snapshot().placeholder(), partial);
            assertEquals(feature.equals("glow_bones"), partial.glowBones().isEmpty());
            assertEquals(feature.equals("lod"), partial.lodBones().isEmpty());
            assertEquals(9, partial.clips().size());
            assertEquals(5, partial.expressions().get("face").size());
        }
        reload(resources(files()));
        var restored = ShipVisuals.resolve(id("kashima"));
        assertFalse(restored.glowBones().isEmpty());
        assertFalse(restored.lodBones().isEmpty());
    }

    @Test
    void onlyTheBuiltInFallbackIsExemptFromTheMissingOffHandWarning() throws IOException {
        var files = files();
        var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        files.put(id("shincolle_kai/visuals/addon_ship.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        reload(resources(files));
        assertFalse(ShipVisuals.warned("rig:shincolle_kai:placeholder:held_item_left"));
        assertTrue(ShipVisuals.warned("rig:shincolle_kai:kashima:held_item_left"));
        assertTrue(ShipVisuals.warned("rig:shincolle_kai:addon_ship:held_item_left"));
        assertFalse(ShipVisualPolicy.warnsMissingRole(true, "held_item_left"));
        assertTrue(ShipVisualPolicy.warnsMissingRole(true, "held_item"));
        assertTrue(ShipVisualPolicy.warnsMissingRole(false, "held_item_left"));
    }

    @Test
    void heldLocatorUsesAnimatedWristPivotWithoutApplyingItsRotationOrModelScaleTwice() throws IOException {
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var model = new ShipVisualGeoModel(visual);
        var bone = model.getBakedModel(visual.definition().model()).getBone("held_item").orElseThrow();
        assertEquals("ArmRight02", bone.getParent().getName());
        bone.setRotZ(0.4F);
        for (int level = 0; level <= 3; level++) {
            PoseStack poses = new PoseStack();
            float scale = ShipVisualRenderMath.scale(visual.definition().scale(), level);
            poses.scale(scale, scale, scale);
            parentTransforms(poses, bone);
            var expected = new Vector3f(bone.getPivotX() / 16F, bone.getPivotY() / 16F, bone.getPivotZ() / 16F);
            poses.last().pose().transformPosition(expected);
            ShipVisualHeldItemLayer.attach(poses, bone, visual.definition().heldItem(), false);
            Vector3f actual = poses.last().pose().transformPosition(new Vector3f());
            assertEquals(expected.x, actual.x, 0.00001F);
            assertEquals(expected.y, actual.y, 0.00001F);
            assertEquals(expected.z, actual.z, 0.00001F);
            assertEquals(scale, poses.last().pose().getScale(new Vector3f()).x, 0.00001F);
        }
        assertTrue(ShipVisualHeldItemLayer.visible(bone));
        bone.getParent().setHidden(true);
        assertFalse(ShipVisualHeldItemLayer.visible(bone));
        bone.getParent().setHidden(false);
        bone.getParent().setChildrenHidden(true);
        assertFalse(ShipVisualHeldItemLayer.visible(bone));
    }

    private static void parentTransforms(PoseStack poses, GeoBone bone) {
        if (bone.getParent() != null) parentTransforms(poses, bone.getParent());
        RenderUtils.prepMatrixForBone(poses, bone);
    }
}
