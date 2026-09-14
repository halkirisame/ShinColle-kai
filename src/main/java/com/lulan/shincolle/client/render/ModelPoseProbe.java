package com.lulan.shincolle.client.render;

import com.lulan.shincolle.client.model.ShipModelBaseAdv;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.ModelFloatLog;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Removable TASK-106 diagnostic. Delete this class, LowestVertexTracker and its test,
 * and replace the single trace call with its super.render lambda body to remove it.
 * The geometry minimum includes equipment, held items and layers; it is not a sole-contact
 * measurement. Use a level floor and an empty hand, and calibrate with grounded Shimakaze.
 */
public final class ModelPoseProbe {
    private static final Map<BasicEntityShip, Integer> LAST_TICK = new WeakHashMap<>();

    private ModelPoseProbe() {
    }

    /** Render exactly once; any instrumentation failure falls back to ordinary rendering. */
    public static void trace(BasicEntityShip entity, PoseStack stack, MultiBufferSource buffers,
                             EntityModel<?> model, Consumer<MultiBufferSource> render) {
        LowestVertexTracker tracker = null;
        try {
            if (LogHelper.diagEnabled()) {
                Integer last = LAST_TICK.get(entity);
                if (last == null || entity.tickCount - last >= 20 || entity.tickCount < last) {
                    LAST_TICK.put(entity, entity.tickCount);
                    tracker = new LowestVertexTracker(buffers, stack.last().pose());
                }
            }
        } catch (Exception | LinkageError ignored) {
            // A measuring instrument must not interrupt the game.
        }
        render.accept(tracker == null ? buffers : tracker);
        if (tracker != null) {
            try {
                report(entity, model, tracker.lowest());
            } catch (Exception | LinkageError ignored) {
                // Do not send diagnostic failures to latest.log.
            }
        }
    }

    private static void report(BasicEntityShip entity, EntityModel<?> model, double bottom)
            throws IllegalAccessException {
        StringBuilder line = new StringBuilder("probe-v2 calibration=PENDING scope=whole_renderer");
        line.append(" type=").append(entity.getType().getDescriptionId())
                .append(" uuid=").append(entity.getUUID()).append(" tick=").append(entity.tickCount)
                .append(" model=").append(model.getClass().getSimpleName())
                .append(" bottomFromRenderOrigin=").append(bottom)
                .append(" sitting=").append(entity.getIsSitting())
                .append(" riding=").append(entity.getIsRiding())
                .append(" hasPassengers=").append(entity.isVehicle())
                .append(" vehicle=").append(entity.getVehicle() == null ? "none"
                        : entity.getVehicle().getType().getDescriptionId())
                .append(" riderType=").append(entity instanceof IShipRiderType rider ? rider.getRiderType() : -1)
                .append(" noFuel=").append(entity.getStateFlag(ID.F.NoFuel))
                .append(" emotion=").append(entity.getStateEmotion(ID.S.Emotion))
                .append(" attackTick=").append(entity.getAttackTick())
                .append(" sneak=").append(entity.getIsSneaking())
                .append(" sprint=").append(entity.getIsSprinting())
                .append(" depth0=").append(entity.getShipDepth(0))
                .append(" depth1=").append(entity.getShipDepth(1))
                .append(" scaleLevel=").append(entity.getScaleLevel());
        if (model instanceof ShipModelBaseAdv<?> ship) {
            line.append(" fieldScale=").append(ship.getScale())
                    .append(" fieldOffsetY=").append(ship.getOffsetY());
        }
        // These names belong to our own model classes, not obfuscated Minecraft internals.
        for (Field field : model.getClass().getDeclaredFields()) {
            String name = field.getName();
            if ((name.equals("BodyMain") || name.equals("Butt") || name.startsWith("Leg")
                    || name.equals("animOffsetY")) && field.trySetAccessible()) {
                Object value = field.get(model);
                if (value instanceof ModelPart part) {
                    line.append(' ').append(name).append(".pivot=")
                            .append(part.x).append(',').append(part.y).append(',').append(part.z)
                            .append(' ').append(name).append(".rot=")
                            .append(part.xRot).append(',').append(part.yRot).append(',').append(part.zRot);
                } else if (value instanceof Number) {
                    line.append(' ').append(name).append('=').append(value);
                }
            }
        }
        ModelFloatLog.log(line.toString());
    }
}
