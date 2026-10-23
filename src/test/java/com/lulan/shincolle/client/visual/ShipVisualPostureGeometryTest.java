package com.lulan.shincolle.client.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import com.lulan.shincolle.visualdata.ShipVisualPresentation;
import com.lulan.shincolle.visualdata.ShipVisualRenderMath;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.files;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.reload;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.resources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShipVisualPostureGeometryTest {
    private static final Path ANIMATION = Path.of("src/main/resources/assets/shincolle_kai/animations/kashima.animation.json");

    @Test
    void allRealPosturesKeepVisibleBodyAndSupportAboveGroundAtEveryScaleWithoutEditingBones() throws IOException {
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var animations = JsonParser.parseString(Files.readString(ANIMATION)).getAsJsonObject().getAsJsonObject("animations");
        for (var event : ShipVisualEvent.values()) {
            if (event.isAttack()) continue;
            var clip = animations.getAsJsonObject(visual.definition().clip(event));
            assertTrue(clip.get("loop").getAsBoolean());
            for (int frame = 0; frame <= 20; frame++) {
                var model = new ShipVisualGeoModel(visual);
                var baked = model.getBakedModel(visual.definition().model());
                model.applyPresentation(new ShipVisualPresentation.Expression(0, 0, false), true);
                apply(baked, clip.getAsJsonObject("bones"), frame / 20F);
                float legs = ShipVisualPose.minimumSupportY(baked, Map.of(
                        "leg_left", visual.rig().get("leg_left"), "leg_right", visual.rig().get("leg_right")));
                float body = ShipVisualPose.minimumSupportY(baked, Map.of("leg_left", visual.rig().get("body")));
                float offset = model.postureOffset(true);
                if (frame == 0) System.out.println(event + " legs=" + legs + " body=" + body + " offset=" + offset);
                for (int level = 0; level <= 3; level++) {
                    float scale = ShipVisualRenderMath.scale(visual.definition().scale(), level);
                    assertTrue((legs + offset) * scale >= -0.00001F, event + " support penetrates");
                    assertTrue((body + offset) * scale >= -0.00001F, event + " body penetrates: " + (body + offset));
                }
                assertEquals(offset, model.postureOffset(true));
                assertEquals(0, model.postureOffset(false));
                var root = baked.getBone("root").orElseThrow();
                float rootY = clip.getAsJsonObject("bones").has("root")
                        ? clip.getAsJsonObject("bones").getAsJsonObject("root").getAsJsonArray("position").get(1).getAsFloat() : 0;
                assertEquals(rootY, root.getPosY());
                if (event == ShipVisualEvent.SIT || event == ShipVisualEvent.RIDE) {
                    assertEquals(-0.40454766F, legs, 0.00001F);
                    assertEquals(-legs, offset, 0.00001F, "existing seated support must not change");
                    float caseY = ShipVisualPose.minimumSupportY(baked, Map.of("leg_left", "Case")) + offset;
                    assertTrue(caseY >= 0, "seated case penetrates: " + caseY);
                    if (frame == 0) System.out.println("Corrected Case min=" + caseY);
                    var caseBone = baked.getBone("Case").orElseThrow();
                    float authoredY = caseBone.getPosY();
                    caseBone.setPosY(0);
                    float oldCase = ShipVisualPose.minimumSupportY(baked, Map.of("leg_left", "Case")) + offset;
                    assertTrue(oldCase * 0.46F < -0.09F, "old case must demonstrate the reported penetration");
                    caseBone.setPosY(authoredY);
                    if (frame == 0) System.out.println("Original Case min=" + oldCase + ", world=" + oldCase * 0.46F);
                }
            }
        }
    }

    @Test
    void runtimeAnimationAndPrototypeMatchAndEveryAnimatedBoneExistsInTheActualGeometry() throws IOException {
        Path prototype = Path.of("docs/prototypes/kashima/kashima.animation.json");
        if (Files.exists(prototype)) {
            assertTrue(java.util.Arrays.equals(Files.readAllBytes(ANIMATION), Files.readAllBytes(prototype)));
        }
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var baked = new ShipVisualGeoModel(visual).getBakedModel(visual.definition().model());
        var animations = JsonParser.parseString(Files.readString(ANIMATION)).getAsJsonObject().getAsJsonObject("animations");
        for (var clip : animations.entrySet()) {
            for (String bone : clip.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
                assertTrue(baked.getBone(bone).isPresent(), clip.getKey() + ":" + bone);
            }
        }
    }

    @Test
    void frozenAnimationContentAndConvertedJavaPoseAnglesUseTheApprovedSeatedSignConvention()
            throws IOException, NoSuchAlgorithmException {
        String text = Files.readString(ANIMATION).replace("\r\n", "\n");
        assertEquals("f92ba05d71a70297446ce45c93a5eb1d258bbb66b27f7b4e8a83038459749732",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var animations = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("animations");
        for (String posture : new String[]{"sprint", "caress", "no_fuel"}) {
            var baked = new ShipVisualGeoModel(visual).getBakedModel(visual.definition().model());
            apply(baked, animations.getAsJsonObject("animation.kashima." + posture).getAsJsonObject("bones"), 0);
            double body = posture.equals("sprint") ? 0.1 : posture.equals("caress") ? -0.1047 : 1.4;
            double arm = posture.equals("sprint") ? -3.6 : posture.equals("caress") ? -2.4 : -2.8;
            double spread = posture.equals("sprint") ? 0.87 : posture.equals("caress") ? 0.5 : 0.7;
            assertEquals(-body, baked.getBone("BodyMain").orElseThrow().getRotX(), 0.00001);
            for (String side : new String[]{"Left", "Right"}) {
                var bone = baked.getBone("Arm" + side + "01").orElseThrow();
                assertEquals(-arm, bone.getRotX(), 0.00001);
                assertEquals(0, bone.getRotY(), 0.00001);
                assertEquals(side.equals("Left") ? spread : -spread, bone.getRotZ(), 0.00001);
            }
        }
        assertEquals(animations.getAsJsonObject("animation.kashima.sit").get("bones"),
                animations.getAsJsonObject("animation.kashima.ride").get("bones"));
    }

    public static void apply(software.bernie.geckolib.cache.object.BakedGeoModel baked, JsonObject bones, float time) {
        for (var entry : bones.entrySet()) {
            var bone = baked.getBone(entry.getKey()).orElseThrow();
            var channels = entry.getValue().getAsJsonObject();
            if (channels.has("rotation")) {
                float[] rotation = sample(channels.get("rotation"), time);
                bone.setRotX(bone.getRotX() - rotation[0] * Mth.DEG_TO_RAD);
                bone.setRotY(bone.getRotY() - rotation[1] * Mth.DEG_TO_RAD);
                bone.setRotZ(bone.getRotZ() + rotation[2] * Mth.DEG_TO_RAD);
            }
            if (channels.has("position")) {
                float[] position = sample(channels.get("position"), time);
                bone.setPosX(-position[0]);
                bone.setPosY(position[1]);
                bone.setPosZ(position[2]);
            }
        }
    }

    private static float[] sample(JsonElement channel, float time) {
        if (channel.isJsonArray()) return vector(channel.getAsJsonArray());
        var frames = channel.getAsJsonObject();
        var keys = frames.keySet().stream().map(Float::parseFloat).sorted().toList();
        float previous = keys.get(0);
        for (float next : keys) {
            if (next >= time) {
                float[] a = vector(frames.getAsJsonArray(key(frames, previous)));
                float[] b = vector(frames.getAsJsonArray(key(frames, next)));
                float fraction = next == previous ? 0 : (time - previous) / (next - previous);
                return new float[]{Mth.lerp(fraction, a[0], b[0]), Mth.lerp(fraction, a[1], b[1]), Mth.lerp(fraction, a[2], b[2])};
            }
            previous = next;
        }
        return vector(frames.getAsJsonArray(key(frames, previous)));
    }

    private static String key(JsonObject frames, float time) {
        return frames.keySet().stream().filter(key -> Float.parseFloat(key) == time).findFirst().orElseThrow();
    }

    private static float[] vector(JsonArray array) {
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }
}
