package com.lulan.shincolle.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Model-height instrument. Undo the complete renderer-entry matrix for EACH vertex before
 * selecting a minimum. Adding camera Y to an already selected view-space minimum is invalid:
 * pitch/roll mix Y with X/Z and can even change which vertex is lowest.
 * Measures geometry relative to the render origin, not terrain or opaque texture coverage.
 * Real-client calibration is still required. Delegated rendering is never caught or retried.
 */
final class LowestVertexTracker implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final Matrix4f inverse;
    private double lowest = Double.POSITIVE_INFINITY;
    private boolean failed;

    LowestVertexTracker(MultiBufferSource delegate, Matrix4f renderEntry) {
        this.delegate = delegate;
        this.inverse = new Matrix4f(renderEntry).invert();
    }

    /** Lowest render-origin-relative Y, or NaN for an empty/failed measurement. */
    double lowest() {
        return this.failed || this.lowest == Double.POSITIVE_INFINITY ? Double.NaN : this.lowest;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        VertexConsumer inner = this.delegate.getBuffer(renderType);
        try {
            return new Tracking(inner);
        } catch (Exception | LinkageError ignored) {
            this.failed = true;
            return inner;
        }
    }

    private final class Tracking implements VertexConsumer {

        private final VertexConsumer inner;

        Tracking(VertexConsumer inner) {
            this.inner = inner;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            try {
                Matrix4f m = LowestVertexTracker.this.inverse;
                double localY = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
                if (!Double.isFinite(localY)) {
                    LowestVertexTracker.this.failed = true;
                } else if (localY < LowestVertexTracker.this.lowest) {
                    LowestVertexTracker.this.lowest = localY;
                }
            } catch (Exception | LinkageError ignored) {
                LowestVertexTracker.this.failed = true;
            }
            this.inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.inner.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            this.inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            this.inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            this.inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            this.inner.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            this.inner.unsetDefaultColor();
        }
    }
}
