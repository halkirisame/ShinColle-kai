package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;

/**
 * Regression coverage for hostile ships vanishing on Peaceful.
 * <p>
 * The original extends {@code EntityMob}, whose update kills the entity outright
 * while the difficulty is Peaceful. This port extends {@code Mob} rather than
 * {@code Monster}, and vanilla only overrides {@code shouldDespawnInPeaceful} to
 * {@code true} on {@code Monster} - so registering as {@code MobCategory.MONSTER}
 * was not enough and hostile ships survived Peaceful indefinitely.
 * <p>
 * The difficulty is server-wide, so a GameTest cannot flip it without disturbing
 * every other test. These assertions pin the two properties vanilla's
 * {@code Mob#checkDespawn} consults instead.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostileDespawnGameTests {

    private HostileDespawnGameTests() {
    }

    private static boolean despawnsInPeaceful(Entity entity) {
        try {
            Method m = net.minecraft.world.entity.Mob.class
                    .getDeclaredMethod("m_6785_"); // shouldDespawnInPeaceful
            m.setAccessible(true);
            return (boolean) m.invoke(entity);
        } catch (ReflectiveOperationException srgNotFound) {
            try {
                Method m = net.minecraft.world.entity.Mob.class
                        .getDeclaredMethod("shouldDespawnInPeaceful");
                m.setAccessible(true);
                return (boolean) m.invoke(entity);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("shouldDespawnInPeaceful not reachable", e);
            }
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileShipsDespawnInPeaceful(GameTestHelper helper) {
        Entity hostile = ModEntities.BB_KONGOU_MOB.get().create(helper.getLevel());
        if (!(hostile instanceof BasicEntityShipHostile)) {
            throw new AssertionError("Failed to create hostile ship");
        }
        if (!despawnsInPeaceful(hostile)) {
            throw new AssertionError(
                    "Hostile ships must despawn on Peaceful; the original extends EntityMob");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileShipsRegisterAsMonsters(GameTestHelper helper) {
        // checkDespawn also gates the distance path on the category, so both the
        // category and the peaceful flag have to stay correct together.
        if (ModEntities.BB_KONGOU_MOB.get().getCategory() != MobCategory.MONSTER) {
            throw new AssertionError("Hostile ships must register as MobCategory.MONSTER");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyShipsSurvivePeaceful(GameTestHelper helper) {
        Entity friendly = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (!(friendly instanceof BasicEntityShip)) {
            throw new AssertionError("Failed to create friendly ship");
        }
        if (despawnsInPeaceful(friendly)) {
            throw new AssertionError("Player-side ships must survive Peaceful");
        }
        helper.succeed();
    }
}
