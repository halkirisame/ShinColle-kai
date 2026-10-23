package com.lulan.shincolle.visualdata;

/**
 * Head-look composition, independent of Minecraft client state.
 *
 * <p>The renderer keeps the head bone's value between frames, and the animation engine only rewrites a bone
 * that the playing clip animates. Adding the look angle to the bone's current value therefore piles up every
 * frame on a clip that leaves the head alone. The result here is a function of this frame's inputs only.</p>
 */
public final class ShipHeadLook {
    private ShipHeadLook() { }

    /**
     * @param current     the bone angle as found at the start of this frame
     * @param initial     the bone's rest angle from the model
     * @param look        the angle to add for the entity's head direction
     * @param clipDrivesBone whether the playing clip animates this bone, so the engine rewrites it every frame
     * @return the clip's angle plus the look when the clip drives the bone, otherwise the rest angle plus the
     *         look. The clip's own value is never compared with what this method stored earlier, because a
     *         still clip rewrites the same value each frame and a zero look makes that equal to the stored one.
     */
    public static float compose(float current, float initial, float look, boolean clipDrivesBone) {
        return (clipDrivesBone ? current : initial) + look;
    }
}
