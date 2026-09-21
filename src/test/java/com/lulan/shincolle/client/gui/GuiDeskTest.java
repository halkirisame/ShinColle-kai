package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.reference.ID;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiDeskTest {

    @Test
    void galleryNeutralBasisPreservesHorizontalAndDepthButPointsUpOnScreen() {
        Matrix4f transform = new Matrix4f().scaling(50, 50, -50)
                .rotate(GuiDesk.galleryModelRotation(0, 0));
        assertVector(new Vector3f(50, 0, 0), transform.transformDirection(new Vector3f(1, 0, 0)));
        assertVector(new Vector3f(0, -50, 0), transform.transformDirection(new Vector3f(0, 1, 0)));
        assertVector(new Vector3f(0, 0, 50), transform.transformDirection(new Vector3f(0, 0, 1)));
    }

    @Test
    void galleryDragRotatesModelOnBothAxes() {
        Matrix4f yaw = new Matrix4f().scaling(1, 1, -1)
                .rotate(GuiDesk.galleryModelRotation(90, 0));
        assertVector(new Vector3f(1, 0, 0), yaw.transformDirection(new Vector3f(0, 0, 1)));
        Matrix4f pitch = new Matrix4f().scaling(1, 1, -1)
                .rotate(GuiDesk.galleryModelRotation(0, 60));
        assertVector(new Vector3f(0, -0.5F, (float) Math.sqrt(3) / 2),
                pitch.transformDirection(new Vector3f(0, 1, 0)));
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 0.0001F);
        assertEquals(expected.y, actual.y, 0.0001F);
        assertEquals(expected.z, actual.z, 0.0001F);
    }

    @Test
    void scaledCenteredOriginCentersWidthAtDeskScale() {
        assertEquals(64, GuiDesk.scaledCenteredOrigin(480, 1.25F, 256));
    }

    @Test
    void scaledCenteredOriginCentersHeightAtDeskScale() {
        assertEquals(12, GuiDesk.scaledCenteredOrigin(270, 1.25F, 192));
    }

    @Test
    void scaledCenteredOriginClampsToZeroWhenScreenIsTooSmall() {
        assertEquals(0, GuiDesk.scaledCenteredOrigin(200, 1.25F, 192));
    }

    @Test
    void scaledIconAreaConvertsBookCoordinatesToScreenPixels() {
        Rect2i area = GuiDesk.scaledIconArea(64, 12, 16, 45);

        assertEquals(100, area.getX());
        assertEquals(71, area.getY());
        assertEquals(20, area.getWidth());
        assertEquals(20, area.getHeight());
    }

    @Test
    void galleryEmotionRollAcceptsFriendlyAndHostileShipFamilies() {
        assertTrue(IShipEmotion.class.isAssignableFrom(BasicEntityShip.class));
        assertTrue(IShipEmotion.class.isAssignableFrom(BasicEntityShipHostile.class));
    }

    @Test
    void gallerySitControlTogglesPoseAndPreservesLegacyRollOrder() {
        TestGalleryEntity entity = new TestGalleryEntity();
        SequenceRandom firstRoll = new SequenceRandom(0, 1);

        GuiDesk.handleGalleryModelControl(1, entity, firstRoll, false, ignored -> { });

        assertTrue(entity.getIsSitting());
        assertEquals(ID.Emotion.BORED, entity.getStateEmotion(ID.S.Emotion));
        assertEquals(ID.Emotion.NORMAL, entity.getStateEmotion(ID.S.Emotion4));
        assertIterableEquals(List.of(2, 2), firstRoll.bounds());
        assertIterableEquals(List.of(false, false), entity.emotionSyncs());

        SequenceRandom secondRoll = new SequenceRandom(1, 0);
        GuiDesk.handleGalleryModelControl(1, entity, secondRoll, false, ignored -> { });

        assertFalse(entity.getIsSitting());
        assertEquals(ID.Emotion.NORMAL, entity.getStateEmotion(ID.S.Emotion));
        assertEquals(ID.Emotion.BORED, entity.getStateEmotion(ID.S.Emotion4));
        assertIterableEquals(List.of(2, 2), secondRoll.bounds());
    }

    @Test
    void galleryRunControlTogglesRawSprintingState() {
        TestGalleryEntity entity = new TestGalleryEntity();
        List<Boolean> sprintStates = new ArrayList<>();

        GuiDesk.handleGalleryModelControl(2, entity, new SequenceRandom(), false, sprintStates::add);
        GuiDesk.handleGalleryModelControl(2, entity, new SequenceRandom(), true, sprintStates::add);

        assertIterableEquals(List.of(true, false), sprintStates);
    }

    @Test
    void galleryAttackControlStartsAtFiftyAndRollsFourPhases() {
        TestGalleryEntity entity = new TestGalleryEntity();
        SequenceRandom random = new SequenceRandom(3);

        GuiDesk.handleGalleryModelControl(3, entity, random, false, ignored -> { });

        assertEquals(50, entity.getAttackTick());
        assertEquals(3, entity.getStateEmotion(ID.S.Phase));
        assertIterableEquals(List.of(4), random.bounds());
        assertIterableEquals(List.of(false), entity.emotionSyncs());
    }

    @Test
    void galleryEmotionRollPreservesLegacyBoundsOrderAndTrueBranches() {
        TestGalleryEntity entity = new TestGalleryEntity();
        SequenceRandom random = new SequenceRandom(0, 0, 0);

        GuiDesk.rollGalleryEmotion(entity, random, entity::setShiftKeyDown);

        assertEquals(ID.Emotion.BORED, entity.getStateEmotion(ID.S.Emotion4));
        assertTrue(entity.isShiftKeyDown());
        assertTrue(entity.getStateFlag(ID.F.NoFuel));
        assertIterableEquals(List.of(2, 5, 8), random.bounds());
        assertIterableEquals(List.of(false), entity.emotionSyncs());
    }

    @Test
    void galleryEmotionRollCanSelectPrimaryEmotionLowerBoundary() {
        TestGalleryEntity entity = new TestGalleryEntity();
        SequenceRandom random = new SequenceRandom(1, 4, 7, 0);

        GuiDesk.rollGalleryEmotion(entity, random, entity::setShiftKeyDown);

        assertEquals(ID.Emotion.NORMAL, entity.getStateEmotion(ID.S.Emotion4));
        assertFalse(entity.isShiftKeyDown());
        assertFalse(entity.getStateFlag(ID.F.NoFuel));
        assertEquals(0, entity.getStateEmotion(ID.S.Emotion));
        assertIterableEquals(List.of(2, 5, 8, 10), random.bounds());
        assertIterableEquals(List.of(false, false), entity.emotionSyncs());
    }

    @Test
    void galleryEmotionRollCanSelectPrimaryEmotionUpperBoundary() {
        TestGalleryEntity entity = new TestGalleryEntity();
        SequenceRandom random = new SequenceRandom(1, 1, 7, 9);

        GuiDesk.rollGalleryEmotion(entity, random, entity::setShiftKeyDown);

        assertFalse(entity.isShiftKeyDown());
        assertFalse(entity.getStateFlag(ID.F.NoFuel));
        assertEquals(9, entity.getStateEmotion(ID.S.Emotion));
        assertIterableEquals(List.of(2, 5, 8, 10), random.bounds());
    }

    @Test
    void galleryTickAdvancesEveryClientTickOnlyWhileVisibleAndReplacementStartsAtZero() {
        int originalTickCount = 0;
        originalTickCount = GuiDesk.nextGalleryTickCount(originalTickCount, true);
        assertEquals(1, originalTickCount);
        originalTickCount = GuiDesk.nextGalleryTickCount(originalTickCount, true);
        assertEquals(2, originalTickCount);
        originalTickCount = GuiDesk.nextGalleryTickCount(originalTickCount, false);
        assertEquals(2, originalTickCount);

        int replacementTickCount = 0;
        replacementTickCount = GuiDesk.nextGalleryTickCount(replacementTickCount, true);
        assertEquals(1, replacementTickCount);
    }

    @Test
    void galleryAttackTickCountsDownToZeroOnly() {
        assertEquals(49, GuiDesk.nextGalleryAttackTick(50));
        assertEquals(0, GuiDesk.nextGalleryAttackTick(1));
        assertEquals(0, GuiDesk.nextGalleryAttackTick(0));
        assertEquals(-1, GuiDesk.nextGalleryAttackTick(-1));
    }

    @Test
    void galleryWalkAnimationAcceleratesAndStopsWithoutResidualInterpolation() {
        WalkAnimationState animation = new WalkAnimationState();

        GuiDesk.updateGalleryWalkAnimation(animation, true);
        assertEquals(0.4F, animation.speed(), 0.0001F);
        assertEquals(0.4F, animation.position(), 0.0001F);

        GuiDesk.updateGalleryWalkAnimation(animation, true);
        assertEquals(0.64F, animation.speed(), 0.0001F);
        assertEquals(1.04F, animation.position(), 0.0001F);

        GuiDesk.updateGalleryWalkAnimation(animation, false);
        assertEquals(0F, animation.speed(0F), 0.0001F);
        assertEquals(0F, animation.speed(1F), 0.0001F);
        assertEquals(1.04F, animation.position(), 0.0001F);
    }

    private static final class TestGalleryEntity implements IShipEmotion {
        private final Map<Integer, Integer> emotions = new HashMap<>();
        private final Map<Integer, Boolean> flags = new HashMap<>();
        private final List<Boolean> emotionSyncs = new ArrayList<>();
        private boolean shiftKeyDown;
        private boolean sitting;
        private int attackTick;

        @Override
        public int getStateEmotion(int id) {
            return this.emotions.getOrDefault(id, 0);
        }

        @Override
        public void setStateEmotion(int id, int value, boolean sync) {
            this.emotions.put(id, value);
            this.emotionSyncs.add(sync);
        }

        @Override
        public int getStateMinor(int id) {
            return 0;
        }

        @Override
        public void setStateMinor(int state, int value) {
        }

        @Override
        public boolean getStateFlag(int flag) {
            return this.flags.getOrDefault(flag, false);
        }

        @Override
        public void setStateFlag(int id, boolean flag) {
            this.flags.put(id, flag);
        }

        @Override
        public void setUpdateFlag(int id, boolean value) {
        }

        @Override
        public boolean getUpdateFlag(int id) {
            return false;
        }

        @Override
        public int getStateTimer(int id) {
            return 0;
        }

        @Override
        public void setStateTimer(int id, int value) {
        }

        @Override
        public int getFaceTick() {
            return 0;
        }

        @Override
        public void setFaceTick(int value) {
        }

        @Override
        public int getHeadTiltTick() {
            return 0;
        }

        @Override
        public void setHeadTiltTick(int value) {
        }

        @Override
        public int getAttackTick() {
            return this.attackTick;
        }

        @Override
        public void setAttackTick(int value) {
            this.attackTick = value;
        }

        @Override
        public int getAttackTick2() {
            return 0;
        }

        @Override
        public void setAttackTick2(int value) {
        }

        @Override
        public int getDeathTick() {
            return 0;
        }

        @Override
        public void setDeathTick(int value) {
        }

        @Override
        public float getModelRotate(int id) {
            return 0;
        }

        @Override
        public void setModelRotate(int id, float value) {
        }

        @Override
        public int getTickExisted() {
            return 0;
        }

        @Override
        public float getSwingTime(float partialTick) {
            return 0;
        }

        @Override
        public boolean getIsRiding() {
            return false;
        }

        @Override
        public boolean getIsSprinting() {
            return false;
        }

        @Override
        public boolean getIsSitting() {
            return this.sitting;
        }

        @Override
        public boolean getIsSneaking() {
            return this.shiftKeyDown;
        }

        @Override
        public boolean getIsLeashed() {
            return false;
        }

        @Override
        public void setEntitySit(boolean sit) {
            this.sitting = sit;
        }

        @Override
        public int getRidingState() {
            return 0;
        }

        @Override
        public void setRidingState(int state) {
        }

        @Override
        public int getScaleLevel() {
            return 0;
        }

        @Override
        public void setScaleLevel(int value) {
        }

        @Override
        public RandomSource getRand() {
            return RandomSource.create(0);
        }

        @Override
        public double getShipDepth(int type) {
            return 0;
        }

        private List<Boolean> emotionSyncs() {
            return this.emotionSyncs;
        }

        private boolean isShiftKeyDown() {
            return this.shiftKeyDown;
        }

        private void setShiftKeyDown(boolean value) {
            this.shiftKeyDown = value;
        }
    }

    private static final class SequenceRandom implements RandomSource {
        private final int[] values;
        private final List<Integer> bounds = new ArrayList<>();
        private int index;

        private SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public RandomSource fork() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSeed(long seed) {
        }

        @Override
        public int nextInt() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextInt(int bound) {
            this.bounds.add(bound);
            if (this.index >= this.values.length) {
                throw new AssertionError("Missing test random value for bound " + bound);
            }
            int value = this.values[this.index++];
            if (value < 0 || value >= bound) {
                throw new AssertionError("Value " + value + " is outside bound " + bound);
            }
            return value;
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean nextBoolean() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float nextFloat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double nextDouble() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double nextGaussian() {
            throw new UnsupportedOperationException();
        }

        private List<Integer> bounds() {
            return this.bounds;
        }
    }
}
