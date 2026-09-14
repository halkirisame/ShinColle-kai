package com.lulan.shincolle.gametest;

import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameTestEntitiesGameTests {

    private GameTestEntitiesGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void scopeDiscardsAllEntitiesOnNormalExit(GameTestHelper helper) {
        Entity first;
        Entity second;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            first = entities.add(create(helper, EntityType.COW, 1.5D));
            second = entities.add(create(helper, EntityType.ZOMBIE, 2.5D));
        }

        helper.assertTrue(first.isRemoved() && second.isRemoved(),
                "Normal scope exit did not remove every registered entity");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void scopeDiscardsAllEntitiesWhenBodyThrows(GameTestHelper helper) {
        Entity first = null;
        Entity second = null;
        boolean caught = false;
        try {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                first = entities.add(create(helper, EntityType.COW, 1.5D));
                second = entities.add(create(helper, EntityType.ZOMBIE, 2.5D));
                throw new ScopeBodyException();
            }
        } catch (ScopeBodyException expected) {
            caught = true;
        }

        helper.assertTrue(caught, "Scope body exception did not propagate to the test");
        helper.assertTrue(first != null && second != null && first.isRemoved() && second.isRemoved(),
                "Exceptional scope exit did not remove every registered entity");
        helper.succeed();
    }

    private static Entity create(GameTestHelper helper, EntityType<?> type, double x) {
        Entity entity = type.create(helper.getLevel());
        if (entity == null) {
            throw new AssertionError("Failed to create cleanup fixture entity: " + type);
        }
        entity.moveTo(helper.absoluteVec(new Vec3(x, 2D, 1.5D)));
        if (!helper.getLevel().addFreshEntity(entity)) {
            throw new AssertionError("Failed to add cleanup fixture entity: " + type);
        }
        return entity;
    }

    private static final class ScopeBodyException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
