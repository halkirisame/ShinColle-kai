package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerAkatsuki;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
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
public final class ShipSoundRecoveryGameTests {

    private ShipSoundRecoveryGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_sound_recovery")
    public static void hurtVoiceWaitsForSharedCooldownAndResumes(GameTestHelper helper) {
        RecordingShip ship = ship(helper);
        ship.setStateTimer(ID.T.SoundTime, 0);
        ship.hurtVoice();
        int cooldown = ship.getStateTimer(ID.T.SoundTime);
        helper.assertTrue(cooldown >= 20 && cooldown <= 49, "Hurt cooldown must be 20 through 49 ticks");
        helper.assertTrue(ship.sounds.size() == 1, "First hurt must emit one voice");
        for (int tick = 0; tick < cooldown; tick++) {
            ship.hurtVoice();
            helper.assertTrue(ship.sounds.size() == 1, "Cooldown must suppress repeated hurt voice");
            ship.advanceSoundTimer();
        }
        helper.assertTrue(ship.getStateTimer(ID.T.SoundTime) == 0, "Existing server timer must expire");
        ship.hurtVoice();
        helper.assertTrue(ship.sounds.size() == 2, "Hurt voice must resume after expiry");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_sound_recovery")
    public static void existingSoundCooldownSuppressesHurtWithoutRandomCalls(GameTestHelper helper) {
        RecordingShip ship = ship(helper);
        ship.setStateTimer(ID.T.SoundTime, 37);
        ship.getRandom().setSeed(12345L);
        RandomSource control = RandomSource.create(12345L);
        ship.hurtVoice();
        helper.assertTrue(ship.sounds.isEmpty(), "Feed or pickup cooldown must also suppress hurt voice");
        helper.assertTrue(ship.getStateTimer(ID.T.SoundTime) == 37, "Suppressed hurt must retain timer");
        helper.assertTrue(ship.getRandom().nextInt() == control.nextInt(), "Suppressed hurt must not consume RNG");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_sound_recovery")
    public static void experienceLevelupRestoresDedicatedSoundBranch(GameTestHelper helper) {
        boolean customObserved = false;
        boolean vanillaObserved = false;
        for (long seed : new long[] {0, 4096, 8192, 12345, 54321, 99999}) {
            RecordingShip ship = ship(helper);
            ship.setShipLevel(1, true);
            ship.setStateMinor(ID.M.ExpCurrent, 0);
            ship.setExpNext();
            float multiplier = ship.getAttrs().getAttrsBuffed(ID.Attrs.XP);
            helper.assertTrue(multiplier > 0, "Experience multiplier fixture must be positive");
            int experience = (int) Math.ceil(ship.getExpNextValue() / multiplier);
            boolean dedicated = RandomSource.create(seed).nextInt(4) != 0;
            ship.getRandom().setSeed(seed);
            ship.addShipExp(experience);
            helper.assertTrue(ship.getLevel() == 2, "Fixture must reach exactly one new level");
            helper.assertTrue(ship.sounds.size() == (dedicated ? 1 : 0), "Dedicated level sound must follow original draw");
            if (dedicated) {
                helper.assertTrue(ship.sounds.get(0).sound() == ModSounds.SHIP_LEVEL.get()
                        && ship.sounds.get(0).volume() == 0.75F && ship.sounds.get(0).pitch() == 1F,
                        "Dedicated level sound must keep event, volume and pitch");
                customObserved = true;
            } else {
                vanillaObserved = true;
            }
        }
        helper.assertTrue(customObserved && vanillaObserved, "Fixture must exercise both sound branches");
        RecordingShip noLevel = ship(helper);
        noLevel.addShipExp(0);
        helper.assertTrue(noLevel.sounds.isEmpty(), "No level gained must not emit dedicated sound");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_sound_recovery")
    public static void trainingBookEmitsDedicatedSoundAndKeepsItsEffect(GameTestHelper helper) {
        RecordingShip ship = ship(helper);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sound_book"));
        var capability = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                () -> new IllegalStateException("Player capability fixture"));
        capability.setPlayerUID(70177);
        ship.setPlayerUID(70177);
        ship.setOwnerUUID(player.getUUID());
        ship.setTame(true);
        ship.setShipLevel(1, true);
        helper.assertTrue(TeamHelper.checkSameOwner(ship, player), "Book owner fixture must match");
        ItemStack book = new ItemStack(ModItems.TRAINING_BOOK.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        helper.assertTrue(ship.mobInteract(player, InteractionHand.MAIN_HAND).consumesAction(), "Book must be accepted");
        helper.assertTrue(ship.getLevel() >= 6 && ship.getLevel() <= 11 && book.getCount() == 1,
                "Book level gain and consumption must remain unchanged");
        helper.assertTrue(ship.sounds.size() == 1 && ship.sounds.get(0).sound() == ModSounds.SHIP_LEVEL.get()
                && ship.sounds.get(0).volume() == 0.75F && ship.sounds.get(0).pitch() == 1F,
                "Book must emit the dedicated level sound once");
        helper.succeed();
    }

    private static RecordingShip ship(GameTestHelper helper) {
        RecordingShip ship = new RecordingShip(helper.getLevel());
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateFlag(ID.F.NoFuel, false);
        return ship;
    }

    private record EmittedSound(SoundEvent sound, float volume, float pitch) {
    }

    private static final class RecordingShip extends EntityDestroyerAkatsuki {
        private final List<EmittedSound> sounds = new ArrayList<>();

        private RecordingShip(Level level) {
            super(ModEntities.DESTROYER_AKATSUKI.get(), level);
        }

        @Override
        public void playVoice(SoundEvent sound, float volume, float pitch) {
            sounds.add(new EmittedSound(sound, volume, pitch));
        }

        private void hurtVoice() {
            playHurtSound(damageSources().generic());
        }

        private void advanceSoundTimer() {
            updateServerTimer();
        }
    }
}
