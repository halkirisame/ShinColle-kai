package com.lulan.shincolle.client.render;

import com.lulan.shincolle.client.visual.PostureTestShips;
import com.lulan.shincolle.entity.hime.EntityCarrierWD;
import com.lulan.shincolle.entity.mounts.EntityMountCaWD;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountedShipRenderTest {
    @Test
    void vanillaRenderReceivesBodyLeanAndRelativeHeadYawWhileSitting() throws Exception {
        PostureTestShips.create(false, false, false, false);
        var mount = allocate(EntityMountCaWD.class);
        mount.yBodyRotO = 0;
        mount.yBodyRot = 0;
        var ship = allocate(RenderShip.class);
        ship.vehicle = mount;
        var renderer = allocate(RecordingRenderer.class);
        var model = new RecordingModel();
        renderer.useModel(model);
        // Normal, opposite lean, crossing +/-180, and a second passenger/frame.
        for (float[] angles : new float[][]{{0, 14, 0, 35, 0.5F, 7, 10.5F},
                {-14, -14, -35, -35, 1, -14, -21},
                {179, -179, -160, -158, 0.5F, 180, 21},
                {0, 0, 0, 0, 1, 0, 0}}) {
            ship.yBodyRotO = angles[0];
            ship.yBodyRot = angles[1];
            ship.yHeadRotO = angles[2];
            ship.yHeadRot = angles[3];
            PoseStack stack = new PoseStack();
            assertSame(model.stop, assertThrows(StopBeforeGpu.class,
                    () -> renderer.render(ship, 0, angles[4], stack, null, 0)));
            assertTrue(model.riding, "Vanilla sitting posture must remain active");
            assertEquals(0, net.minecraft.util.Mth.wrapDegrees(renderer.bodyYaw - angles[5]), 0.0001);
            assertEquals(angles[6], model.headYaw, 0.0001);
            Matrix4f expected = new Matrix4f().rotateY((float) Math.toRadians(180 - angles[5]));
            assertTrue(expected.equals(renderer.bodyMatrix, 0.00001F), "Body yaw must reach PoseStack");
            assertEquals(0, mount.yBodyRotO, "Restore shared mount even if rendering throws");
            assertEquals(0, mount.yBodyRot);
            assertEquals(angles[0], ship.yBodyRotO);
            assertEquals(angles[1], ship.yBodyRot);
            assertSame(mount, ship.getVehicle());
        }
    }

    @Test
    void dismountedShipAndOtherVehiclesKeepVanillaRotation() throws Exception {
        PostureTestShips.create(false, false, false, false);
        var ship = allocate(RenderShip.class);
        ship.yBodyRotO = ship.yBodyRot = 14;
        ship.yHeadRotO = ship.yHeadRot = 35;
        var renderer = allocate(RecordingRenderer.class);
        var model = new RecordingModel();
        renderer.useModel(model);
        assertThrows(StopBeforeGpu.class, () -> renderer.render(ship, 0, 1, new PoseStack(), null, 0));
        assertEquals(false, model.riding);
        assertEquals(14, renderer.bodyYaw, 0.0001);
        assertEquals(21, model.headYaw, 0.0001);

        var pig = allocate(net.minecraft.world.entity.animal.Pig.class);
        pig.yBodyRotO = pig.yBodyRot = 90;
        ship.vehicle = pig;
        assertThrows(StopBeforeGpu.class, () -> renderer.render(ship, 0, 1, new PoseStack(), null, 0));
        assertTrue(model.riding);
        assertEquals(79, renderer.bodyYaw, 0.0001);
        assertEquals(-44, model.headYaw, 0.0001);
        assertEquals(90, pig.yBodyRotO);
        assertEquals(90, pig.yBodyRot);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RenderShip extends EntityCarrierWD {
        Entity vehicle;
        private RenderShip() { super(null, null); }
        @Override public Entity getVehicle() { return vehicle; }
        @Override public boolean isPassenger() { return vehicle != null; }
        @Override public boolean isBaby() { return false; }
        @Override public boolean isAlive() { return false; }
        @Override public boolean hasCustomName() { return false; }
        @Override public boolean hasPose(Pose pose) { return false; }
        @Override public boolean isAutoSpinAttack() { return false; }
        @Override public int getScaleLevel() { return 0; }
    }

    private static final class RecordingRenderer extends ShipEntityRenderer<RenderShip> {
        float bodyYaw;
        Matrix4f bodyMatrix;
        private RecordingRenderer() { super(null, null, null, 0); }
        void useModel(EntityModel<RenderShip> value) { model = value; }
        @Override protected float getAttackAnim(RenderShip ship, float partialTick) { return 0; }
        @Override protected boolean isShaking(RenderShip ship) { return false; }
        @Override protected void setupRotations(RenderShip ship, PoseStack stack, float age, float yaw, float partialTick) {
            bodyYaw = yaw;
            super.setupRotations(ship, stack, age, yaw, partialTick);
            bodyMatrix = new Matrix4f(stack.last().pose());
        }
    }

    private static final class RecordingModel extends EntityModel<RenderShip> {
        final StopBeforeGpu stop = new StopBeforeGpu();
        float headYaw;
        @Override public void setupAnim(RenderShip entity, float swing, float amount, float age, float yaw, float pitch) {
            headYaw = yaw;
            throw stop;
        }
        @Override public void renderToBuffer(PoseStack stack, VertexConsumer buffer, int light, int overlay,
                                             float red, float green, float blue, float alpha) { }
    }

    private static final class StopBeforeGpu extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
