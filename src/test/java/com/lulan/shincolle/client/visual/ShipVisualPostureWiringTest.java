package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.visualdata.ShipVisualEvent;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.core.animation.AnimationState;

import java.io.IOException;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.files;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.reload;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.resources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real animate handler of a real animatable on real entity input and records the clip it plays. */
class ShipVisualPostureWiringTest {
    private static ShipVisualEvent selected(boolean noFuel, boolean sitting, boolean riding, boolean sprinting,
                                            boolean moving) throws IOException {
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var animatable = new ShipVisualAnimatable();
        animatable.bind(PostureTestShips.create(noFuel, sitting, riding, sprinting), visual);
        var chosen = new software.bernie.geckolib.core.animation.RawAnimation[1];
        var state = new AnimationState<ShipVisualAnimatable>(animatable, 0, 0, 0, moving) {
            @Override
            public software.bernie.geckolib.core.object.PlayState setAndContinue(
                    software.bernie.geckolib.core.animation.RawAnimation animation) {
                chosen[0] = animation;
                return software.bernie.geckolib.core.object.PlayState.CONTINUE;
            }
        };
        animatable.animate(state);
        var raw = chosen[0];
        ShipVisualEvent found = null;
        for (var entry : visual.clips().entrySet()) {
            if (entry.getValue() == raw) {
                assertTrue(found == null, "two events share one clip object");
                found = entry.getKey();
            }
        }
        return found;
    }

    @Test
    void animateHandlerPicksTheNewPosturesFromRealEntityInput() throws IOException {
        // No sitting flag and no movement: only the posture selector can produce no_fuel.
        assertEquals(ShipVisualEvent.NO_FUEL, selected(true, false, false, false, false));
        assertEquals(ShipVisualEvent.RIDE, selected(false, false, true, false, false));
        assertEquals(ShipVisualEvent.SPRINT, selected(false, false, false, true, true));
        assertEquals(ShipVisualEvent.SIT, selected(false, true, false, false, false));
        assertEquals(ShipVisualEvent.MOVE, selected(false, false, false, false, true));
        assertEquals(ShipVisualEvent.IDLE, selected(false, false, false, false, false));
        assertSame(ShipVisualEvent.NO_FUEL, selected(true, true, true, true, true));
    }
}
