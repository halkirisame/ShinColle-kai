package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.destroyer.EntityDestroyerRo;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.IntSupplier;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmotionReactionDispatcherGameTests {

    private static final int MAX_RANDOM_ATTEMPTS = 1_024;

    private EmotionReactionDispatcherGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shockAlwaysDispatchesWithoutChangingCooldown(GameTestHelper helper) {
        ReactionProbeShip ship = new ReactionProbeShip(helper.getLevel());
        ship.setEmotesTick(37);

        for (int attempt = 1; attempt <= 8; attempt++) {
            ship.applyEmotesReaction(6);
            helper.assertTrue(ship.getShockCount() == attempt,
                    "Shock reaction did not dispatch on call " + attempt + '.');
            helper.assertTrue(ship.getEmotesTick() == 37,
                    "Shock reaction changed the emote cooldown. actual=" + ship.getEmotesTick());
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void damagedReactionUsesTenTickInterruptThreshold(GameTestHelper helper) {
        ReactionProbeShip ship = new ReactionProbeShip(helper.getLevel());

        ship.setEmotesTick(10);
        ship.applyEmotesReaction(2);
        helper.assertTrue(ship.getDamagedCount() == 1,
                "Damaged reaction did not dispatch at the inclusive ten-tick threshold.");
        helper.assertTrue(ship.getEmotesTick() == 40,
                "Damaged reaction did not set the forty-tick cooldown.");

        ship.setEmotesTick(11);
        ship.applyEmotesReaction(2);
        helper.assertTrue(ship.getDamagedCount() == 1,
                "Damaged reaction dispatched above the ten-tick threshold.");
        helper.assertTrue(ship.getEmotesTick() == 11,
                "Blocked damaged reaction changed the existing cooldown.");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void probabilisticTypesDispatchWithOriginalCooldowns(GameTestHelper helper) {
        ReactionProbeShip ship = new ReactionProbeShip(helper.getLevel());

        awaitRandomDispatch(helper, ship, 1, 60, ship::getStrangerCount);
        awaitRandomDispatch(helper, ship, 3, 60, ship::getAttackCount);
        awaitRandomDispatch(helper, ship, 4, 20, ship::getIdleCount);
        awaitRandomDispatch(helper, ship, 5, 25, ship::getCommandCount);
        awaitRandomDispatch(helper, ship, 0, 50, ship::getNormalCount);

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void strangerAndAttackDispatchUpdateEmotionState(GameTestHelper helper) {
        ReactionProbeShip ship = new ReactionProbeShip(helper.getLevel());
        ship.setMorale(ID.Morale.L_Excited + 1);

        ship.setStateEmotion(ID.S.Emotion, ID.Emotion.NORMAL, false);
        awaitRandomDispatch(helper, ship, 1, 60, ship::getStrangerCount);
        int strangerEmotion = ship.getStateEmotion(ID.S.Emotion);
        helper.assertTrue(strangerEmotion == ID.Emotion.ANGRY || strangerEmotion == ID.Emotion.O_O,
                "Stranger reaction did not update the emotion state. actual=" + strangerEmotion);

        ship.setStateEmotion(ID.S.Emotion, ID.Emotion.NORMAL, false);
        awaitRandomDispatch(helper, ship, 3, 60, ship::getAttackCount);
        helper.assertTrue(ship.getStateEmotion(ID.S.Emotion) == ID.Emotion.XD,
                "Excited attack reaction did not update the emotion state to XD.");

        helper.succeed();
    }

    private static void awaitRandomDispatch(GameTestHelper helper, ReactionProbeShip ship,
                                            int type, int expectedCooldown, IntSupplier counter) {
        int before = counter.getAsInt();
        for (int attempt = 0; attempt < MAX_RANDOM_ATTEMPTS; attempt++) {
            ship.setEmotesTick(0);
            ship.applyEmotesReaction(type);
            if (counter.getAsInt() > before) {
                helper.assertTrue(ship.getEmotesTick() == expectedCooldown,
                        "Reaction type " + type + " set the wrong cooldown. expected="
                                + expectedCooldown + " actual=" + ship.getEmotesTick());
                return;
            }
        }
        helper.assertTrue(false, "Reaction type " + type + " did not dispatch within "
                + MAX_RANDOM_ATTEMPTS + " attempts.");
    }

    private static final class ReactionProbeShip extends EntityDestroyerRo {
        private int normalCount;
        private int strangerCount;
        private int attackCount;
        private int damagedCount;
        private int idleCount;
        private int commandCount;
        private int shockCount;

        private ReactionProbeShip(Level level) {
            super(ModEntities.DESTROYER_RO.get(), level);
        }

        @Override
        public void reactionNormal() {
            this.normalCount++;
        }

        @Override
        public void reactionStranger() {
            this.strangerCount++;
            super.reactionStranger();
        }

        @Override
        public void reactionAttack() {
            this.attackCount++;
            super.reactionAttack();
        }

        @Override
        public void reactionDamaged() {
            this.damagedCount++;
        }

        @Override
        public void reactionIdle() {
            this.idleCount++;
        }

        @Override
        public void reactionCommand() {
            this.commandCount++;
        }

        @Override
        public void reactionShock() {
            this.shockCount++;
        }

        private int getNormalCount() {
            return this.normalCount;
        }

        private int getStrangerCount() {
            return this.strangerCount;
        }

        private int getAttackCount() {
            return this.attackCount;
        }

        private int getDamagedCount() {
            return this.damagedCount;
        }

        private int getIdleCount() {
            return this.idleCount;
        }

        private int getCommandCount() {
            return this.commandCount;
        }

        private int getShockCount() {
            return this.shockCount;
        }
    }
}
