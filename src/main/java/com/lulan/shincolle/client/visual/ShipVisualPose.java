package com.lulan.shincolle.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.Map;

/** Samples the actual animated support geometry using the same transforms as GeckoLib. */
final class ShipVisualPose {
    private ShipVisualPose() { }

    static float minimumSupportY(BakedGeoModel model, Map<String, String> rig) {
        float minimum = Float.POSITIVE_INFINITY;
        PoseStack poses = new PoseStack();
        Vector3f point = new Vector3f();
        for (String role : new String[]{"leg_left", "leg_right"}) {
            String name = rig.get(role);
            GeoBone leg = name == null ? null : model.getBone(name).orElse(null);
            if (leg == null) continue;
            poses.pushPose();
            ancestors(poses, leg.getParent());
            minimum = Math.min(minimum, minimumY(poses, leg, point));
            poses.popPose();
        }
        return minimum;
    }

    private static void ancestors(PoseStack poses, GeoBone bone) {
        if (bone == null) return;
        ancestors(poses, bone.getParent());
        RenderUtils.prepMatrixForBone(poses, bone);
    }

    private static float minimumY(PoseStack poses, GeoBone bone, Vector3f point) {
        poses.pushPose();
        RenderUtils.prepMatrixForBone(poses, bone);
        float minimum = Float.POSITIVE_INFINITY;
        for (var cube : bone.getCubes()) {
            poses.pushPose();
            RenderUtils.translateToPivotPoint(poses, cube);
            RenderUtils.rotateMatrixAroundCube(poses, cube);
            RenderUtils.translateAwayFromPivotPoint(poses, cube);
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                for (var vertex : quad.vertices()) {
                    poses.last().pose().transformPosition(vertex.position(), point);
                    minimum = Math.min(minimum, point.y);
                }
            }
            poses.popPose();
        }
        for (GeoBone child : bone.getChildBones()) minimum = Math.min(minimum, minimumY(poses, child, point));
        poses.popPose();
        return minimum;
    }
}
