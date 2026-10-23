package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.visualdata.ShipHeldItemKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The final held-item scale as the real layer applies it, combined with the configured multiplier. */
class ShipVisualHeldItemScaleTest {
    private static Vector3f applied(Class<? extends Item> item, double configured) {
        PoseStack poses = new PoseStack();
        ShipVisualHeldItemLayer.applyItemScale(poses, item, configured);
        return poses.last().pose().getScale(new Vector3f());
    }

    private static void assertScale(float x, float y, float z, Vector3f actual) {
        assertEquals(x, actual.x, 0.00001F);
        assertEquals(y, actual.y, 0.00001F);
        assertEquals(z, actual.z, 0.00001F);
    }

    @Test
    void swordToolBlockAndOtherItemsKeepTheirOwnFactorsOnTopOfTheConfiguredScale() {
        var sword = SwordItem.class;
        var pickaxe = PickaxeItem.class;
        var block = BlockItem.class;
        var other = Item.class;
        for (double configured : new double[]{1, 0.8, 2.5}) {
            float s = (float) configured;
            assertScale(s * 0.5F, s, s, applied(sword, configured));
            assertScale(s * 0.5F, s, s, applied(pickaxe, configured));
            assertScale(s * 0.75F, s * 0.75F, s * 0.75F, applied(block, configured));
            assertScale(s, s, s, applied(other, configured));
        }
    }

    @Test
    void kindsMultiplyTheConfiguredValueWithoutAccumulating() {
        assertEquals(1F, ShipHeldItemKind.OTHER.scaleX(1));
        assertEquals(0.5F, ShipHeldItemKind.TOOL.scaleX(1));
        assertEquals(1F, ShipHeldItemKind.TOOL.scaleY(1));
        assertEquals(0.75F, ShipHeldItemKind.BLOCK.scaleZ(1));
        assertEquals(1.5F, ShipHeldItemKind.BLOCK.scaleX(2));
    }
}
