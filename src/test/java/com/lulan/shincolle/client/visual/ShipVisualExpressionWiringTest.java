package com.lulan.shincolle.client.visual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.visualdata.ShipHeadTilt;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationProcessor.QueuedAnimation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.data.EntityModelData;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.files;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.reload;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.resources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises entity reads -> real animate handler -> custom animations -> real baked bones, without an OpenGL context. */
class ShipVisualExpressionWiringTest {
    private static final class Fixture {
        final ShipVisuals.Loaded visual;
        final ShipVisualAnimatable animatable = new ShipVisualAnimatable();
        final ShipVisualGeoModel model;
        final BakedGeoModel baked;
        final PostureTestShip ship;
        final JsonObject animations;
        final AnimationController<ShipVisualAnimatable> controller;
        ShipVisualEvent selected;

        Fixture(Map<ResourceLocation, byte[]> files, boolean noFuel, boolean sitting, boolean sprinting) throws IOException {
            reload(resources(files));
            visual = ShipVisuals.resolve(id("kashima"));
            assertNotSame(ShipVisuals.snapshot().placeholder(), visual);
            model = new ShipVisualGeoModel(visual);
            baked = model.getBakedModel(visual.definition().model());
            ship = (PostureTestShip) PostureTestShips.create(noFuel, sitting, false, sprinting);
            animatable.bind(ship, visual);
            animations = JsonParser.parseString(new String(files.get(id("animations/kashima.animation.json")), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            controller = new AnimationController<>(animatable, state -> software.bernie.geckolib.core.object.PlayState.CONTINUE) {
                @Override public void setAnimation(RawAnimation raw) {
                    selected = visual.clips().entrySet().stream().filter(entry -> entry.getValue() == raw)
                            .map(Map.Entry::getKey).findFirst().orElseThrow();
                    var animation = visual.animations().getAnimation(visual.definition().clip(selected));
                    currentAnimation = new QueuedAnimation(animation, animation.loopType());
                }
            };
        }

        void draw(int tick, float pitch, float partialTick) {
            ship.tickCount = tick;
            var state = new AnimationState<>(animatable, 0, 0, partialTick, false).withController(controller);
            state.setData(DataTickets.ENTITY_MODEL_DATA, new EntityModelData(false, false, 0, pitch));
            animatable.animate(state);
            // Emulate only the engine's authored channels. Unauthored bones retain the last custom write.
            var authored = animations.getAsJsonObject(visual.definition().clip(selected)).getAsJsonObject("bones");
            for (String name : authored.keySet()) {
                var bone = baked.getBone(name).orElseThrow();
                var rest = bone.getInitialSnapshot();
                bone.setRotX(rest.getRotX());
                bone.setRotY(rest.getRotY());
                bone.setRotZ(rest.getRotZ());
            }
            ShipVisualPostureGeometryTest.apply(baked, authored, 0);
            model.setCustomAnimations(animatable, 1, state);
        }

        void face(int face, int mouth, boolean flush) {
            for (int index = 0; index < 5; index++) {
                var bone = baked.getBone("Face" + index).orElseThrow();
                assertEquals(index != face % 5, bone.isHidden());
                if (!bone.isHidden()) assertEquals(face >= 5 ? 3.14159F : 0, bone.getRotY());
            }
            for (int index = 0; index < 3; index++) {
                var bone = baked.getBone("Mouth" + index).orElseThrow();
                assertEquals(index != mouth % 3, bone.isHidden());
                if (!bone.isHidden()) assertEquals(mouth >= 3 ? 3.14159F : 0, bone.getRotY());
            }
            assertEquals(!flush, baked.getBone("Flush0").orElseThrow().isHidden());
        }
    }

    @Test
    void realCustomAnimationUsesUidPhaseTimedEmotionAndClientOnlyOnsetMemory() throws IOException {
        var f = new Fixture(files(), false, false, false);
        f.ship.emotion = ID.Emotion.T_T;
        f.ship.shipUid = 1;
        f.draw(1000, 0, 0);
        f.face(6, 2, false); // (1000 + (1 << 7)) & 255 == 104
        f.draw(1020, 0, 0);
        f.face(6, 2, false);
        f.draw(1080, 0, 0);
        f.face(7, 2, false);
        f.draw(1081, 0, 0);
        assertFalse(f.baked.getBone("Mouth0").orElseThrow().isHidden());
        assertEquals(ID.Emotion.T_T, f.ship.emotion, "expiry must not write back to the entity");
        f.ship.emotion = ID.Emotion.SHY;
        f.draw(1082, 0, 0);
        f.face(8, 0, true);
        f.draw(1162, 0, 0);
        f.face(0, 3, true);
        f.draw(1163, 0, 0);
        assertTrue(f.baked.getBone("Flush0").orElseThrow().isHidden());
        f.ship.emotion = ID.Emotion.T_T;
        f.draw(1200, 0, 0);
        f.face(6, 5, false); // changed emotion starts a fresh duration
        f.draw(10, 0, 0);
        f.face(7, 2, false); // a tick-count rollback also resets age
    }

    @Test
    void realBonesBlinkAndStateFacesFollowTheSamePostureSelectorAsTheClip() throws IOException {
        var idle = new Fixture(files(), false, false, false);
        int uid = 0;
        while (!com.lulan.shincolle.visualdata.ShipVisualExpression.blinkCycle(uid, 2)) uid++;
        idle.ship.shipUid = uid;
        idle.draw(256, 0, 0);
        idle.face(1, 0, false);
        idle.draw(267, 0, 0);
        idle.face(0, 0, false);
        idle.draw(277, 0, 0);
        idle.face(1, 0, false);
        idle.draw(282, 0, 0);
        idle.face(0, 0, false);
        var sprint = new Fixture(files(), false, false, true);
        sprint.draw(100, 0, 0);
        assertEquals(ShipVisualEvent.SPRINT, sprint.selected);
        sprint.face(3, 0, false);
        var caress = new Fixture(files(), false, false, false);
        caress.ship.emotion3 = ID.Emotion3.CARESS;
        caress.draw(100, 0, 0);
        assertEquals(ShipVisualEvent.CARESS, caress.selected);
        caress.face(0, 2, true);
        var noFuel = new Fixture(files(), true, false, true);
        noFuel.ship.emotion3 = ID.Emotion3.CARESS;
        noFuel.draw(100, 0, 0);
        assertEquals(ShipVisualEvent.NO_FUEL, noFuel.selected);
        noFuel.face(4, 2, false);
        var sit = new Fixture(files(), false, true, true);
        sit.ship.emotion = ID.Emotion.BORED;
        sit.draw(100, 0, 0);
        assertEquals(ShipVisualEvent.SIT, sit.selected);
        sit.face(5, 4, false);
        sit.draw(128, 0, 0);
        sit.face(3, 4, false);
    }

    @Test
    void attackFaceBeginsWithTheActualAttackEventAndEndsWithItsClip() throws Exception {
        var f = new Fixture(files(), false, false, false);
        long[] gameTime = {100};
        f.ship.world = clockLevel(gameTime);
        f.ship.attackTick = 40;
        f.draw(100, 0, 0);
        assertEquals(ShipVisualEvent.LIGHT_ATTACK, f.selected);
        f.face(1, 2, false);
        gameTime[0] = 101;
        f.ship.attackTick = 39;
        f.draw(300, 0, 0);
        f.face(3, 0, false);
        gameTime[0] = 301;
        f.draw(301, 0, 0);
        assertEquals(ShipVisualEvent.IDLE, f.selected);
        f.face(0, 0, false);
    }

    @Test
    void headTiltAndHairUseRealCustomAnimationWithNoEntityWritesAndNeverAccumulate() throws IOException {
        var f = new Fixture(files(), false, false, false);
        int uid = 0;
        while (ShipHeadTilt.angle(85, uid, 0) == 0) uid++;
        f.ship.shipUid = uid;
        for (int frame = 0; frame < 100; frame++) {
            f.draw(85, 20, 0.5F);
            var head = f.baked.getBone("Head").orElseThrow();
            assertEquals(20 * net.minecraft.util.Mth.DEG_TO_RAD, head.getRotX(), 0.000001F);
            assertEquals(ShipHeadTilt.angle(85, uid, 0.5F), head.getRotZ());
            assertNotEquals(0, head.getRotZ());
            for (String name : f.visual.definition().headFollow().bones()) {
                var hair = f.baked.getBone(name).orElseThrow();
                assertEquals(hair.getInitialSnapshot().getRotX() - 0.5F * head.getRotX(), hair.getRotX(), 0.000001F, name);
                float coefficient = name.equals("HairR02") ? 1 : 0.5F;
                assertEquals(hair.getInitialSnapshot().getRotZ() - coefficient * head.getRotZ(), hair.getRotZ(), 0.000001F, name);
            }
        }
        f.draw(0, 0, 0);
        for (String name : f.visual.definition().headFollow().bones()) {
            var hair = f.baked.getBone(name).orElseThrow();
            assertEquals(hair.getInitialSnapshot().getRotX(), hair.getRotX());
            assertEquals(hair.getInitialSnapshot().getRotZ(), hair.getRotZ());
        }
        assertEquals(0, f.ship.emotion);
        assertEquals(0, f.ship.getHeadTiltTick());
        var sprint = new Fixture(files(), false, false, true);
        sprint.ship.shipUid = uid;
        for (int frame = 0; frame < 20; frame++) {
            sprint.draw(85, 0, 0.5F);
            var head = sprint.baked.getBone("Head").orElseThrow();
            assertEquals(0.14F, head.getRotX(), 0.00001F);
            assertEquals(ShipHeadTilt.angle(85, uid, 0.5F), head.getRotZ());
        }
    }

    @Test
    void staticClipHairAndHeadRemainStableAtZeroLookAndReturnToRestWhenClipChanges() throws IOException {
        var files = files();
        var animation = JsonParser.parseString(new String(files.get(id("animations/kashima.animation.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        animation.getAsJsonObject("animations").getAsJsonObject("animation.kashima.caress").getAsJsonObject("bones")
                .add("HairR02", JsonParser.parseString("{\"rotation\":[10,0,5]}"));
        files.put(id("animations/kashima.animation.json"), animation.toString().getBytes(StandardCharsets.UTF_8));
        var f = new Fixture(files, false, false, false);
        f.ship.emotion3 = ID.Emotion3.CARESS; // suppresses incidental tilt, leaving a zero Z correction
        for (int frame = 0; frame < 100; frame++) {
            f.draw(100, 0, 0);
            var head = f.baked.getBone("Head").orElseThrow();
            assertEquals(-0.71F, head.getRotX(), 0.00001F);
            assertEquals(0, head.getRotZ());
            var hair = f.baked.getBone("HairR02").orElseThrow();
            assertEquals(hair.getInitialSnapshot().getRotX() - 10 * net.minecraft.util.Mth.DEG_TO_RAD + 0.355F,
                    hair.getRotX(), 0.00001F);
            assertEquals(hair.getInitialSnapshot().getRotZ() + 5 * net.minecraft.util.Mth.DEG_TO_RAD,
                    hair.getRotZ(), 0.000001F);
        }
        f.ship.emotion3 = 0;
        f.draw(0, 0, 0);
        var hair = f.baked.getBone("HairR02").orElseThrow();
        assertEquals(hair.getInitialSnapshot().getRotX(), hair.getRotX());
        assertEquals(hair.getInitialSnapshot().getRotZ(), hair.getRotZ());
    }

    @Test
    void headFollowUnknownGeometryOrUnselectedOverridesRejectOnlyTheirVisual() throws IOException {
        for (String data : new String[]{"{\"bones\":[\"absent\"]}",
                "{\"bones\":[\"Hair01\"],\"overrides\":{\"HairR02\":{\"z\":1}}}"}) {
            var files = files();
            var json = JsonParser.parseString(new String(files.get(id("shincolle_kai/visuals/kashima.json")), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            json.add("head_follow", JsonParser.parseString(data));
            files.put(id("shincolle_kai/visuals/bad_follow.json"), json.toString().getBytes(StandardCharsets.UTF_8));
            reload(resources(files));
            assertSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("bad_follow")));
            assertNotSame(ShipVisuals.snapshot().placeholder(), ShipVisuals.resolve(id("kashima")));
        }
    }

    private static Level clockLevel(long[] time) throws ReflectiveOperationException {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        var level = (ServerLevel) unsafe.allocateInstance(ServerLevel.class);
        Field data = Level.class.getDeclaredField("levelData");
        data.setAccessible(true);
        data.set(level, Proxy.newProxyInstance(WritableLevelData.class.getClassLoader(), new Class<?>[]{WritableLevelData.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGameTime")) return time[0];
                    throw new AssertionError("Unexpected level data access: " + method);
                }));
        assertEquals(time[0], level.getGameTime());
        return level;
    }
}
