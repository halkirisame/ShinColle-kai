package com.lulan.shincolle.client.gui;

import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiDeskTest {

    @Test
    void galleryNeutralBasisPreservesHorizontalAndDepthButPointsUpOnScreen() {
        Matrix4f transform = new Matrix4f().scaling(50, 50, -50)
                .rotate(GuiDesk.galleryModelRotation(0, 0));
        assertVector(new Vector3f(50, 0, 0), transform.transformDirection(new Vector3f(1, 0, 0)));
        assertVector(new Vector3f(0, -50, 0), transform.transformDirection(new Vector3f(0, 1, 0)));
        assertVector(new Vector3f(0, 0, 50), transform.transformDirection(new Vector3f(0, 0, 1)));
    }

    @Test
    void galleryDragRotatesModelOnBothAxes() {
        Matrix4f yaw = new Matrix4f().scaling(1, 1, -1)
                .rotate(GuiDesk.galleryModelRotation(90, 0));
        assertVector(new Vector3f(1, 0, 0), yaw.transformDirection(new Vector3f(0, 0, 1)));
        Matrix4f pitch = new Matrix4f().scaling(1, 1, -1)
                .rotate(GuiDesk.galleryModelRotation(0, 60));
        assertVector(new Vector3f(0, -0.5F, (float) Math.sqrt(3) / 2),
                pitch.transformDirection(new Vector3f(0, 1, 0)));
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 0.0001F);
        assertEquals(expected.y, actual.y, 0.0001F);
        assertEquals(expected.z, actual.z, 0.0001F);
    }

    @Test
    void scaledCenteredOriginCentersWidthAtDeskScale() {
        assertEquals(64, GuiDesk.scaledCenteredOrigin(480, 1.25F, 256));
    }

    @Test
    void scaledCenteredOriginCentersHeightAtDeskScale() {
        assertEquals(12, GuiDesk.scaledCenteredOrigin(270, 1.25F, 192));
    }

    @Test
    void scaledCenteredOriginClampsToZeroWhenScreenIsTooSmall() {
        assertEquals(0, GuiDesk.scaledCenteredOrigin(200, 1.25F, 192));
    }

    @Test
    void scaledIconAreaConvertsBookCoordinatesToScreenPixels() {
        Rect2i area = GuiDesk.scaledIconArea(64, 12, 16, 45);

        assertEquals(100, area.getX());
        assertEquals(71, area.getY());
        assertEquals(20, area.getWidth());
        assertEquals(20, area.getHeight());
    }
}
