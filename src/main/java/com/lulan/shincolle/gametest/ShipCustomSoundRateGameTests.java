package com.lulan.shincolle.gametest;

import com.lulan.shincolle.config.ConfigSound;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerAkatsuki;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCustomSoundRateGameTests {

    private static final int[] VOICE_IDS = {54, 56, 60, 62};
    private static final float[][] RATES = {
            {0.25F, 0F, 0.25F, 0F, 0.50F, 0F, 0.50F, 0F, 0F},
            {0.50F, 0.50F, 0.50F, 1F, 0F, 0F, 0.50F, 0F, 0F},
            {0.25F, 0.50F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
            {0F, 0.35F, 0F, 0F, 0F, 0F, 0F, 0F, 0F}
    };
    private static final String[] SOUND_NAMES = {
            "ship_idle", "ship_hit", "ship_hurt", "ship_death",
            "ship_marry", "ship_knockback", "ship_item", "ship_feed"
    };

    private ShipCustomSoundRateGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_custom_sound_rate")
    public static void fixedIdleDrawSwitchesInazumaVoice(GameTestHelper helper) {
        SoundShip ship = ship(helper, 54);
        ship.useDraw(0.75F);
        assertSound(helper, ship.getCustomSound(0, ship), "ship_idle");
        helper.assertTrue(ship.voiceRandom.calls == 1, "Rejected custom idle must consume one float");
        ship.useDraw(0.25F);
        assertSound(helper, ship.getCustomSound(0, ship), "ship_idle_56");
        helper.assertTrue(ship.voiceRandom.calls == 1, "Accepted custom idle must consume one float");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_custom_sound_rate")
    public static void allDefaultVoicesUseStrictRateBoundary(GameTestHelper helper) {
        for (int index = 0; index < VOICE_IDS.length; index++) {
            SoundShip ship = ship(helper, VOICE_IDS[index] - 2);
            for (int type = 0; type < SOUND_NAMES.length; type++) {
                float rate = RATES[index][type];
                if (rate > 0F) {
                    ship.useDraw(Math.nextDown(rate));
                    assertSound(helper, ship.getCustomSound(type, ship),
                            SOUND_NAMES[type] + "_" + VOICE_IDS[index]);
                    helper.assertTrue(ship.voiceRandom.calls == 1, "Custom selection must draw once, even at rate 1");
                    if (rate < 1F) {
                        ship.useDraw(rate);
                        assertSound(helper, ship.getCustomSound(type, ship), SOUND_NAMES[type]);
                        helper.assertTrue(ship.voiceRandom.calls == 1, "Equality must reject after one draw");
                    }
                } else {
                    ship.useDraw(0F);
                    assertSound(helper, ship.getCustomSound(type, ship), SOUND_NAMES[type]);
                    helper.assertTrue(ship.voiceRandom.calls == 0, "Zero rate must not draw");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_custom_sound_rate")
    public static void zeroAndThresholdRatesSkipRandomWithRegisteredCandidate(GameTestHelper helper) {
        float[] original = ConfigSound.SOUND_RATE.get(54);
        helper.assertTrue(original != null && original[0] == 0.25F, "Hibiki rate fixture must match defaults");
        float[] rates = original.clone();
        ConfigSound.SOUND_RATE.put(54, rates);
        try {
            SoundShip ship = ship(helper, 52);
            for (float rate : new float[]{0F, 0.01F}) {
                rates[0] = rate;
                helper.assertTrue(ConfigSound.getSoundRate(54, 0) == rate, "Rate fixture must be installed");
                ship.useDraw(0F);
                assertSound(helper, ship.getCustomSound(0, ship), "ship_idle");
                helper.assertTrue(ship.voiceRandom.calls == 0, "Rate at or below 0.01 must not draw");
            }
            rates[0] = 0.0101F;
            ship.useDraw(0F);
            assertSound(helper, ship.getCustomSound(0, ship), "ship_idle_54");
            helper.assertTrue(ship.voiceRandom.calls == 1, "Rate above 0.01 must permit selection");
        } finally {
            ConfigSound.SOUND_RATE.put(54, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_custom_sound_rate")
    public static void missingCustomCandidateOrRateSkipsRandom(GameTestHelper helper) {
        helper.assertTrue(!ConfigSound.SOUND_RATE.containsKey(2), "Missing candidate fixture must have no default rate");
        ConfigSound.SOUND_RATE.put(2, new float[]{0.5F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F});
        try {
            SoundShip ship = ship(helper, 0);
            helper.assertTrue(ConfigSound.getSoundRate(2, 0) == 0.5F, "Missing candidate must have positive rate");
            ship.useDraw(0F);
            assertSound(helper, ship.getCustomSound(0, ship), "ship_idle");
            helper.assertTrue(ship.voiceRandom.calls == 0, "Missing candidate must not consume a float");
        } finally {
            ConfigSound.SOUND_RATE.remove(2);
        }
        SoundShip noRate = ship(helper, 56);
        noRate.useDraw(0F);
        assertSound(helper, noRate.getCustomSound(0, noRate), "ship_idle");
        helper.assertTrue(noRate.voiceRandom.calls == 0, "Missing voice table must not draw");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_custom_sound_rate")
    public static void defaultTimekeepingKeepsEveryHourWithoutRandom(GameTestHelper helper) {
        for (int voiceId : VOICE_IDS) {
            SoundShip ship = ship(helper, voiceId - 2);
            ship.useDraw(0F);
            for (int hour = 0; hour < 24; hour++) {
                assertSound(helper, ship.getCustomSound(hour + 10, ship), "ship_time" + hour);
                helper.assertTrue(ship.voiceRandom.calls == 0, "Default timekeeping must not draw");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_custom_sound_rate")
    public static void shipWrapperAndHurtUseTheVoiceOwnersRandom(GameTestHelper helper) {
        SoundShip receiver = ship(helper, 52);
        SoundShip argument = ship(helper, 54);
        receiver.useDraw(0F);
        argument.useDraw(0.75F);
        assertSound(helper, receiver.getCustomSound(0, argument), "ship_idle");
        helper.assertTrue(receiver.voiceRandom.calls == 0 && argument.voiceRandom.calls == 1,
                "Wrapper must use the argument ship's random, not its receiver's");
        argument.useDraw(0.25F);
        assertSound(helper, argument.hurtSound(), "ship_hurt_56");
        helper.assertTrue(argument.voiceRandom.calls == 1, "Direct hurt lookup must use the owning ship's random");
        argument.useDraw(0.75F);
        assertSound(helper, argument.hurtSound(), "ship_hurt");
        helper.assertTrue(argument.voiceRandom.calls == 1, "Direct hurt rejection must draw once");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_custom_sound_rate")
    public static void ambientSelectionRetainsOuterFrequencyAndMarriageChoice(GameTestHelper helper) {
        boolean heard = false;
        boolean silent = false;
        boolean marriage = false;
        for (boolean married : new boolean[]{false, true}) {
            for (long seed = 0; seed < 32; seed++) {
                SoundShip ship = ship(helper, 52);
                ship.setStateFlag(ID.F.IsMarried, married);
                helper.assertTrue(ship.getStateFlag(ID.F.IsMarried) == married, "Marriage fixture must be installed");
                ship.useDraw(0.75F);
                ship.baseRandom().setSeed(seed);
                RandomSource control = RandomSource.create(seed);
                boolean plays = control.nextInt(10) <= 3;
                boolean marryVoice = plays && married && control.nextInt(5) == 0;
                ship.playAmbientSound();
                helper.assertTrue(ship.sounds.size() == (plays ? 1 : 0), "Outer ambient chance must be unchanged");
                if (plays) {
                    assertSound(helper, ship.sounds.get(0), marryVoice ? "ship_marry" : "ship_idle");
                    helper.assertTrue(ship.voiceRandom.calls == 1, "Selected ambient or marriage voice must draw once");
                    control.nextFloat();
                    control.nextFloat();
                    heard = true;
                    marriage |= marryVoice;
                } else {
                    helper.assertTrue(ship.voiceRandom.calls == 0, "Suppressed ambient must not select a voice");
                    silent = true;
                }
                helper.assertTrue(ship.baseRandom().nextInt() == control.nextInt(),
                        "Outer ambient and marriage draws must keep their original order");
            }
        }
        helper.assertTrue(heard && silent && marriage, "Seeds must exercise idle, silence and marriage");
        helper.succeed();
    }

    private static void assertSound(GameTestHelper helper, SoundEvent sound, String path) {
        helper.assertTrue(sound != null && sound.getLocation().toString().equals(Reference.MOD_ID + ":" + path),
                "Expected " + path + ", got " + (sound == null ? "null" : sound.getLocation()));
    }

    private static SoundShip ship(GameTestHelper helper, int shipClass) {
        SoundShip ship = new SoundShip(helper.getLevel(), shipClass);
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateFlag(ID.F.NoFuel, false);
        helper.assertTrue(ship.getShipClass() == shipClass && !ship.getStateFlag(ID.F.NoFuel)
                && ship.getStateMinor(ID.M.NumGrudge) > 0, "Ship fixture must have its class and fuel");
        return ship;
    }

    private static final class FixedRandom extends LegacyRandomSource {
        private final float draw;
        private int calls;

        private FixedRandom(float draw) {
            super(0L);
            this.draw = draw;
        }

        @Override
        public float nextFloat() {
            calls++;
            return draw;
        }
    }

    private static final class SoundShip extends EntityDestroyerAkatsuki {
        private final short soundShipClass;
        private final List<SoundEvent> sounds = new ArrayList<>();
        private FixedRandom voiceRandom;

        private SoundShip(Level level, int shipClass) {
            super(ModEntities.DESTROYER_AKATSUKI.get(), level);
            this.soundShipClass = (short) shipClass;
        }

        @Override
        public short getShipClass() {
            return soundShipClass;
        }

        @Override
        public RandomSource getRandom() {
            return voiceRandom != null ? voiceRandom : super.getRandom();
        }

        @Override
        public void playVoice(SoundEvent sound, float volume, float pitch) {
            sounds.add(sound);
        }

        private void useDraw(float draw) {
            voiceRandom = new FixedRandom(draw);
        }

        private RandomSource baseRandom() {
            return super.getRandom();
        }

        private SoundEvent hurtSound() {
            return getHurtSound(damageSources().generic());
        }
    }
}
