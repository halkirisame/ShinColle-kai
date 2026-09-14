package com.lulan.shincolle.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowestVertexTrackerTest {
    @Test
    void recoversHeightBeforeChoosingLowestAndPreservesEmittedVertices() {
        for (float pitch : new float[]{-1.1F, 0F, 0.8F}) {
            Matrix4f entry = new Matrix4f().rotateX(pitch).rotateY(0.7F).translate(4F, -6F, 8F);
            List<Vector3f> forwarded = new ArrayList<>();
            LowestVertexTracker tracker = new LowestVertexTracker(type -> sink(forwarded), entry);
            VertexConsumer buffer = tracker.getBuffer(null);
            for (Vector3f local : List.of(new Vector3f(0F, 0F, 3F), new Vector3f(1F, 2F, -8F))) {
                Vector3f emitted = entry.transformPosition(new Vector3f(local));
                buffer.vertex(emitted.x, emitted.y, emitted.z).color(1, 2, 3, 4).endVertex();
                assertEquals(emitted, forwarded.get(forwarded.size() - 1));
            }
            assertEquals(0D, tracker.lowest(), 0.00001D, "pitch=" + pitch);
            assertEquals(2, forwarded.size());
        }
    }

    @Test
    void emptyMeasurementIsUnknown() {
        LowestVertexTracker tracker = new LowestVertexTracker(type -> sink(new ArrayList<>()), new Matrix4f());
        assertTrue(Double.isNaN(tracker.lowest()));
    }

    private static VertexConsumer sink(List<Vector3f> vertices) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("vertex") && args.length == 3) {
                        vertices.add(new Vector3f(((Number) args[0]).floatValue(),
                                ((Number) args[1]).floatValue(), ((Number) args[2]).floatValue()));
                    }
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
    }
}
