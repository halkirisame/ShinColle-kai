package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ServerEventHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.BookTitleHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Coverage for the first-login guide-book
 * distribution ({@code ServerEventHandler.giveGuideBookOnFirstLogin}, invoked via
 * reflection since it is package-private in the {@code handler} package) and the
 * chapter-0 title-key fallback ({@code BookTitleHelper.titleKey}).
 * <p>
 * {@code BookTitleHelper} is exercised directly rather than through
 * {@code GuiBook.titleKey}: {@code GuiBook} lives under {@code client.gui} and
 * references client-only types, which can fail to load from a dedicated-server
 * GameTest run.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuideBookFirstLoginGameTests {

    private static final String GUIDE_BOOK_GIVEN_TAG = readGuideBookGivenTag();

    private GuideBookFirstLoginGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void firstLoginGivesOneGuideBookToInventory(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "guidebook_first", 1);

        invokeGiveGuideBook(player, true);

        helper.assertTrue(countGuideBooks(player) == 1,
                "First login should give exactly one guide book.");
        helper.assertTrue(hasGuideBookGivenTag(player),
                "First login should mark the player as having received the guide book.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void secondLoginDoesNotGiveAnotherGuideBook(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "guidebook_second", 2);

        invokeGiveGuideBook(player, true);
        invokeGiveGuideBook(player, true);

        helper.assertTrue(countGuideBooks(player) == 1,
                "A second login must not give a second guide book.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void guideBookMarkSurvivesRespawnViaForgeRestoreFrom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer oldPlayer = fakePlayer(level, "guidebook_respawn_old", 3);
        ServerPlayer newPlayer = fakePlayer(level, "guidebook_respawn_new", 4);

        invokeGiveGuideBook(oldPlayer, true);
        helper.assertTrue(hasGuideBookGivenTag(oldPlayer),
                "Setup failure: old player was not marked before simulating respawn.");

        // ServerPlayer#restoreFrom is the Forge/vanilla data-copy path PlayerList#respawn
        // uses for both death and dimension-change respawns. Its PERSISTED_NBT_TAG copy
        // (net/minecraft/server/level/ServerPlayer.java, `restoreFrom`) sits outside the
        // `keepEverything` branch, so it runs unconditionally; calling it directly here
        // exercises that real Forge/vanilla mechanism rather than re-implementing it.
        newPlayer.restoreFrom(oldPlayer, false);

        helper.assertTrue(hasGuideBookGivenTag(newPlayer),
                "PERSISTED_NBT_TAG guide-book marker did not survive ServerPlayer#restoreFrom.");

        invokeGiveGuideBook(newPlayer, true);
        helper.assertTrue(countGuideBooks(newPlayer) == 0,
                "Respawned player received a second guide book despite the surviving marker.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fullInventoryDropsGuideBookAtFeet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "guidebook_full_inv", 5);
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D));
        player.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        fillInventory(player);

        invokeGiveGuideBook(player, true);

        helper.assertTrue(countGuideBooks(player) == 0,
                "Guide book must not be force-added into a full inventory.");
        helper.assertTrue(hasGuideBookGivenTag(player),
                "Player must still be marked as given even when the book is dropped.");

        AABB nearPlayer = new AABB(pos, pos).inflate(2D);
        boolean droppedBookFound = !level.getEntitiesOfClass(ItemEntity.class, nearPlayer,
                e -> e.getItem().is(ModItems.DESK_ITEM_BOOK.get())).isEmpty();
        helper.assertTrue(droppedBookFound,
                "Guide book was not dropped near the player when the inventory was full.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void disabledConfigGivesNoBookAndNoMarker(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "guidebook_disabled", 6);

        invokeGiveGuideBook(player, false);

        helper.assertTrue(countGuideBooks(player) == 0,
                "No book should be given while giveGuideBookOnFirstJoin is disabled.");
        helper.assertTrue(!hasGuideBookGivenTag(player),
                "No given-marker should be written while the setting is disabled.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void reenablingConfigGivesGuideBookOnNextLogin(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "guidebook_reenable", 7);

        invokeGiveGuideBook(player, false);
        helper.assertTrue(countGuideBooks(player) == 0 && !hasGuideBookGivenTag(player),
                "Setup failure: player already has a book/marker before re-enabling.");

        invokeGiveGuideBook(player, true);
        helper.assertTrue(countGuideBooks(player) == 1,
                "Re-enabling the setting should give the book on the player's next login.");
        helper.assertTrue(hasGuideBookGivenTag(player),
                "Re-enabling the setting should mark the player as given.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void chapterZeroTitleKeyPrefersPerPageKeyWhenPresent(GameTestHelper helper) {
        String key = BookTitleHelper.titleKey(0, 3, hasKey -> true);
        helper.assertTrue("gui.shincolle_kai.book.chap0.title3".equals(key),
                "Chapter 0 should use the per-page title key when it has a translation.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void chapterZeroTitleKeyFallsBackToChapterTitleWhenPerPageMissing(GameTestHelper helper) {
        String key = BookTitleHelper.titleKey(0, 3, hasKey -> false);
        helper.assertTrue("gui.shincolle_kai.book.chap0.title".equals(key),
                "Chapter 0 should fall back to the chapter-wide title key when no per-page "
                        + "translation exists.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void otherChapterTitleKeyAlwaysUsesPerPageKey(GameTestHelper helper) {
        String key = BookTitleHelper.titleKey(1, 5, hasKey -> false);
        helper.assertTrue("gui.shincolle_kai.book.chap1.title5".equals(key),
                "Chapters other than 0 must always use the per-page title key.");
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name, int id) {
        UUID uuid = UUID.fromString(String.format("2b7b6a1a-9f3e-4a2d-8c0e-%012d", id));
        return FakePlayerFactory.get(level, new GameProfile(uuid, name));
    }

    private static void fillInventory(ServerPlayer player) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
    }

    private static int countGuideBooks(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.DESK_ITEM_BOOK.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasGuideBookGivenTag(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(GUIDE_BOOK_GIVEN_TAG);
    }

    private static void invokeGiveGuideBook(ServerPlayer player, boolean enabled) {
        try {
            Method method = ServerEventHandler.class.getDeclaredMethod(
                    "giveGuideBookOnFirstLogin", ServerPlayer.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, player, enabled);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke giveGuideBookOnFirstLogin", e);
        }
    }

    private static String readGuideBookGivenTag() {
        try {
            Field field = ServerEventHandler.class.getDeclaredField("GUIDE_BOOK_GIVEN_TAG");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e.toString());
        }
    }
}
