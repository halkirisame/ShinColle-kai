package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.destroyer.EntityDestroyerRo;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointerCaressStateParityGameTests {

    private static final double EPSILON = 0.000_001D;

    private PointerCaressStateParityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void interactAtCapturesOnlyCaressPointerContact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityDestroyerRo ship = createShip(level);
        FakePlayer player = createPlayer(level, "contact", 1);
        ItemStack pointer = new ItemStack(ModItems.POINTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pointer);
        player.setPos(ship.getX(), ship.getY(), ship.getZ() + 2D);

        PointerItem.setMode(pointer, PointerItem.MODE_FORMATION + 1);
        InteractionResult result = ship.interactAt(player,
                new Vec3(0D, ship.getBbHeight() * 0.75D, 0D), InteractionHand.MAIN_HAND);
        helper.assertTrue(!result.consumesAction(),
                "interactAt consumed the action before mobInteract: " + result);
        helper.assertTrue(ship.getHitHeight() == 75,
                "Caress contact height was not captured. actual=" + ship.getHitHeight());
        helper.assertTrue(ship.getHitAngle() == 90,
                "Caress contact angle was not captured. actual=" + ship.getHitAngle());

        ship.setHitHeight(13);
        ship.setHitAngle(27);
        PointerItem.setMode(pointer, PointerItem.MODE_FORMATION);
        ship.interactAt(player, new Vec3(0D, ship.getBbHeight(), 0D), InteractionHand.MAIN_HAND);
        assertContactUnchanged(helper, ship, "command mode");

        PointerItem.setMode(pointer, PointerItem.MODE_FORMATION + 1);
        player.setShiftKeyDown(true);
        ship.interactAt(player, new Vec3(0D, ship.getBbHeight(), 0D), InteractionHand.MAIN_HAND);
        assertContactUnchanged(helper, ship, "sneaking caress mode");

        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ship.interactAt(player, new Vec3(0D, ship.getBbHeight(), 0D), InteractionHand.MAIN_HAND);
        assertContactUnchanged(helper, ship, "non-pointer interaction");

        player.setItemInHand(InteractionHand.OFF_HAND, pointer);
        ship.interactAt(player, new Vec3(0D, ship.getBbHeight(), 0D), InteractionHand.OFF_HAND);
        assertContactUnchanged(helper, ship, "off-hand pointer interaction");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void caressPoseUsesOriginalUpperBodyGateAndDuration(GameTestHelper helper) {
        EntityDestroyerRo ship = createShip(helper.getLevel());

        for (int hitHeight : new int[]{85, 75, 65, 55}) {
            ship.setStateEmotion(ID.S.Emotion3, ID.Emotion3.NORMAL, false);
            ship.setStateTimer(ID.T.Emotion3Time, 0);
            ship.setHitHeight(hitHeight);
            ship.checkCaressed();
            helper.assertTrue(ship.getStateEmotion(ID.S.Emotion3) == ID.Emotion3.CARESS,
                    "Upper-body contact did not show caress pose. height=" + hitHeight);
            helper.assertTrue(ship.getStateTimer(ID.T.Emotion3Time) == 80,
                    "Caress pose did not use original 80-tick duration. height=" + hitHeight
                            + " actual=" + ship.getStateTimer(ID.T.Emotion3Time));
        }

        ship.setStateEmotion(ID.S.Emotion3, ID.Emotion3.NORMAL, false);
        ship.setStateTimer(ID.T.Emotion3Time, 0);
        ship.setHitHeight(45);
        ship.checkCaressed();
        helper.assertTrue(ship.getStateEmotion(ID.S.Emotion3) == ID.Emotion3.NORMAL,
                "Lower-body contact incorrectly showed the caress pose.");
        helper.assertTrue(ship.getStateTimer(ID.T.Emotion3Time) == 0,
                "Lower-body contact incorrectly started the caress timer.");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pushAiTargetPreservesTargetAndUsesOriginalMotion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityDestroyerRo ship = createShip(level);
        FakePlayer player = createPlayer(level, "push", 2);
        ship.setYRot(0F);
        ship.setAITarget(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = false;

        ship.pushAITarget();

        helper.assertTrue(ship.getAITarget() == player,
                "Pushing replaced or cleared the current AI target.");
        Vec3 motion = player.getDeltaMovement();
        helper.assertTrue(Math.abs(motion.x) < EPSILON,
                "Yaw 0 push changed X motion. actual=" + motion.x);
        helper.assertTrue(Math.abs(motion.y - 0.5D) < EPSILON,
                "Push did not apply original upward motion. actual=" + motion.y);
        helper.assertTrue(Math.abs(motion.z - 0.5D) < EPSILON,
                "Yaw 0 push did not apply original forward motion. actual=" + motion.z);
        helper.assertTrue(player.hurtMarked,
                "Pushed player was not marked for motion synchronization to its own client.");

        helper.succeed();
    }

    private static EntityDestroyerRo createShip(ServerLevel level) {
        EntityDestroyerRo ship = new EntityDestroyerRo(ModEntities.DESTROYER_RO.get(), level);
        ship.setPos(4D, 2D, 4D);
        ship.setYRot(0F);
        return ship;
    }

    private static FakePlayer createPlayer(ServerLevel level, String name, int id) {
        UUID uuid = UUID.fromString(String.format("b870f99c-c96b-4ddb-8510-%012d", id));
        return FakePlayerFactory.get(level, new GameProfile(uuid, "shincolle_caress_" + name));
    }

    private static void assertContactUnchanged(GameTestHelper helper, EntityDestroyerRo ship, String caseName) {
        helper.assertTrue(ship.getHitHeight() == 13 && ship.getHitAngle() == 27,
                caseName + " changed contact state: height=" + ship.getHitHeight()
                        + " angle=" + ship.getHitAngle());
    }
}
