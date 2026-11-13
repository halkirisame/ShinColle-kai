package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerAkatsuki;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainingBookExperienceGameTests {

    private TrainingBookExperienceGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "training_book_experience")
    public static void trainingBookUpdatesThresholdBeforeOrdinaryExperience(GameTestHelper helper) {
        Fixture fixture = fixture(helper, 10, false);
        RecordingShip ship = fixture.ship();
        int oldThreshold = ship.getStateMinor(ID.M.ExpNext);
        useBook(helper, fixture, 1);
        int trainedLevel = ship.getLevel();
        int newThreshold = ship.getStateMinor(ID.M.ExpNext);
        helper.assertTrue(trainedLevel >= 15 && trainedLevel <= 20,
                "Book must retain its five through ten level gain");
        helper.assertTrue(newThreshold > oldThreshold, "Book must increase the experience threshold");

        unitExperienceMultiplier(helper, ship);
        ship.addShipExp(2);
        helper.assertTrue(ship.getLevel() == trainedLevel && ship.getStateMinor(ID.M.ExpCurrent) == oldThreshold,
                "Reaching only the old threshold must not gain another level");
        helper.assertTrue(ship.getStateMinor(ID.M.ExpNext) == newThreshold,
                "Ordinary experience below the new threshold must retain that threshold");

        unitExperienceMultiplier(helper, ship);
        ship.addShipExp(newThreshold - oldThreshold + 7);
        helper.assertTrue(ship.getLevel() == trainedLevel + 1 && ship.getStateMinor(ID.M.ExpCurrent) == 7,
                "Reaching the new threshold must gain exactly one level and retain seven surplus experience");
        helper.assertTrue(ship.getStateMinor(ID.M.ExpNext) == ship.getExpNextValue(),
                "Ordinary level gain must keep the next threshold current");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "training_book_experience")
    public static void consecutiveTrainingBooksKeepExperienceAndRefreshEachSync(GameTestHelper helper) {
        Fixture fixture = fixture(helper, 10, false);
        useBook(helper, fixture, 1);
        int firstLevel = fixture.ship().getLevel();
        int firstThreshold = fixture.ship().getStateMinor(ID.M.ExpNext);
        useBook(helper, fixture, 2);
        helper.assertTrue(fixture.ship().getLevel() >= firstLevel + 5
                        && fixture.ship().getLevel() <= firstLevel + 10,
                "The second book must retain its five through ten level gain");
        helper.assertTrue(fixture.ship().getStateMinor(ID.M.ExpNext) > firstThreshold,
                "The second book must refresh the threshold again");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "training_book_experience")
    public static void unmarriedTrainingBookClampKeepsExperienceAndRefreshesSync(GameTestHelper helper) {
        int cap = Math.min(ConfigHandler.maxLevelUnmarried, ConfigHandler.maxLevel);
        Fixture fixture = fixture(helper, cap - 2, false);
        helper.assertTrue(fixture.ship().getShipLevelCap() == cap && cap < ConfigHandler.maxLevel,
                "Unmarried fixture must have its distinct existing natural cap");
        useBook(helper, fixture, 1);
        helper.assertTrue(fixture.ship().getLevel() == cap, "Book must clamp to the unmarried cap");
        useBook(helper, fixture, 2);
        helper.assertTrue(fixture.ship().getLevel() == cap,
                "Another book at the unmarried cap must retain the existing successful no-level-gain behavior");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "training_book_experience")
    public static void marriedTrainingBookClampKeepsExperienceAndRejectsAtAbsoluteCap(GameTestHelper helper) {
        int cap = ConfigHandler.maxLevel;
        Fixture fixture = fixture(helper, cap - 2, true);
        RecordingShip ship = fixture.ship();
        helper.assertTrue(ship.getShipLevelCap() == cap, "Married fixture must have the existing absolute cap");
        useBook(helper, fixture, 1);
        helper.assertTrue(ship.getLevel() == cap, "Book must clamp to the married absolute cap");
        int remainingBooks = fixture.books().getCount();
        helper.assertTrue(ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                "A book at the absolute cap must retain its rejected result");
        helper.assertTrue(fixture.books().getCount() == remainingBooks && ship.minorSyncs.size() == 1,
                "Rejected use must neither consume a book nor add a minor sync");
        helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) == fixture.experience()
                        && ship.getStateMinor(ID.M.ExpNext) == ship.getExpNextValue(),
                "Rejected use must leave current experience and the threshold unchanged");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, int level, boolean married) {
        RecordingShip ship = new RecordingShip(helper.getLevel());
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "training_exp"));
        var capability = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                () -> new IllegalStateException("Player capability fixture"));
        capability.setPlayerUID(71429);
        ship.setPlayerUID(71429);
        ship.setOwnerUUID(player.getUUID());
        ship.setTame(true);
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.IsMarried, married);
        ship.setShipLevel(level, true);
        ship.setExpNext();
        int threshold = ship.getExpNextValue();
        int experience = threshold - 2;
        ship.setStateMinor(ID.M.ExpCurrent, experience);
        ItemStack books = new ItemStack(ModItems.TRAINING_BOOK.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, books);
        helper.assertTrue(ship.getLevel() == level && ship.getStateFlag(ID.F.IsMarried) == married,
                "Fixture must establish its requested level and marriage state");
        helper.assertTrue(TeamHelper.checkSameOwner(ship, player) && player.getUUID().equals(ship.getOwnerUUID())
                        && ship.isTame()
                        && capability.getPlayerUID() == ship.getPlayerUID(),
                "Fixture must establish both UID and UUID ownership");
        helper.assertTrue(!player.getAbilities().instabuild && !player.isShiftKeyDown(),
                "Fixture owner must use survival main-hand interaction");
        helper.assertTrue(threshold > 2 && ship.getStateMinor(ID.M.ExpNext) == threshold
                        && experience > 0 && ship.getStateMinor(ID.M.ExpCurrent) == experience,
                "Fixture must have a positive old threshold and nonzero retained experience two below it");
        helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                "Fixture must have grudge and fuel");
        unitExperienceMultiplier(helper, ship);
        ship.minorSyncs.clear();
        return new Fixture(ship, player, books, experience);
    }

    private static void useBook(GameTestHelper helper, Fixture fixture, int uses) {
        RecordingShip ship = fixture.ship();
        helper.assertTrue(ship.mobInteract(fixture.player(), InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
                "Owner training book interaction must succeed");
        helper.assertTrue(fixture.books().getCount() == 3 - uses, "Each successful use must consume one book");
        helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) == fixture.experience(),
                "Book must preserve current experience exactly");
        helper.assertTrue(ship.getStateMinor(ID.M.ExpNext) == ship.getExpNextValue(),
                "Book must immediately align the server threshold with the displayed denominator");
        helper.assertTrue(ship.minorSyncs.size() == uses, "Each successful book must immediately send one minor sync");
        MinorSync sync = ship.minorSyncs.get(uses - 1);
        helper.assertTrue(sync.level() == ship.getLevel() && sync.experience() == fixture.experience()
                        && sync.threshold() == ship.getExpNextValue(),
                "Minor sync must observe the new level, retained experience and refreshed threshold in order");
    }

    private static void unitExperienceMultiplier(GameTestHelper helper, RecordingShip ship) {
        ship.getAttrs().setAttrsBuffed(ID.Attrs.XP, 1F);
        helper.assertTrue(ship.getAttrs().getAttrsBuffed(ID.Attrs.XP) == 1F,
                "Effective experience multiplier must be one immediately before experience is applied");
    }

    private record Fixture(RecordingShip ship, FakePlayer player, ItemStack books, int experience) {
    }

    private record MinorSync(int level, int experience, int threshold) {
    }

    private static final class RecordingShip extends EntityDestroyerAkatsuki {
        private final List<MinorSync> minorSyncs = new ArrayList<>();

        private RecordingShip(Level level) {
            super(ModEntities.DESTROYER_AKATSUKI.get(), level);
        }

        @Override
        public void sendSyncPacketMinor() {
            minorSyncs.add(new MinorSync(getLevel(), getStateMinor(ID.M.ExpCurrent), getStateMinor(ID.M.ExpNext)));
        }
    }
}
