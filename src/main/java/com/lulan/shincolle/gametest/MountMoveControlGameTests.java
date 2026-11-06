package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.path.ShipMoveControl;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountMoveControlGameTests {
    private MountMoveControlGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void allEightMountsUseTheirOwnShipMoveControlAndThreeBlockStep(GameTestHelper helper) {
        List<EntityType<? extends BasicEntityMount>> types = List.of(
                ModEntities.MOUNT_AFH.get(), ModEntities.MOUNT_BAH.get(), ModEntities.MOUNT_CAH.get(),
                ModEntities.MOUNT_CAWD.get(), ModEntities.MOUNT_HBH.get(), ModEntities.MOUNT_ISH.get(),
                ModEntities.MOUNT_MIH.get(), ModEntities.MOUNT_SUH.get());
        for (int index = 0; index < types.size(); index++) {
            BasicEntityMount mount = types.get(index).create(helper.getLevel());
            helper.assertTrue(mount != null && mount.getMoveControl() instanceof ShipMoveControl,
                    "Mount must install ShipMoveControl: " + types.get(index));
            ShipMoveControl control = mount.getShipMoveControl();
            helper.assertTrue(control == mount.getMoveControl() && control == mount.getShipMoveHelper(),
                    "Both mount accessors must return the installed controller: " + types.get(index));
            helper.assertTrue(mount.maxUpStep() == 3.0F, "Mount step height must be three: " + types.get(index));
            helper.assertTrue((float) readField(ShipMoveControl.class, control, "rotateLimit")
                            == (index < 5 ? 45F : 60F),
                    "Mount turn limit must match its upstream value: " + types.get(index));
            helper.assertTrue((float) readField(ShipMoveControl.class, control, "baseSpeedMultiplier") == 1.0F,
                    "Mount must not inherit the ship body's speed multiplier: " + types.get(index));
        }
        helper.succeed();
    }

    private static Object readField(Class<?> type, Object object, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot inspect " + name + ": " + error);
        }
    }
}
