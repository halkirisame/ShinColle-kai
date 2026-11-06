package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.visualdata.ShipVisualPresentation;
import com.lulan.shincolle.visualdata.ShipVisualClock;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import com.lulan.shincolle.visualdata.ShipVisualPosture;
import com.lulan.shincolle.visualdata.ShipVisualExpression;
import com.lulan.shincolle.visualdata.ShipHeadTilt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.InstancedAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

/** One client-only animation instance per entity; it never retains the entity between render calls. */
public final class ShipVisualAnimatable implements GeoAnimatable {
    private static final int MOVEMENT_STOP_GRACE_TICKS = 3;
    private final AnimatableInstanceCache cache = new InstancedAnimatableInstanceCache(this);
    private Mob entity;
    private ShipVisuals.Loaded visual;
    private ResourceLocation texture;
    private boolean redAura;
    private int lastAttackTick;
    private long lastSequence;
    private double attackEnds;
    private ShipVisualEvent attackEvent;
    private boolean attackActive;
    private boolean moving;
    private int lastMovingTick;
    private int lastEmotion = Integer.MIN_VALUE;
    private int emotionStarted;
    private int lastExpressionTick = -1;

    public void bind(Mob entity, ShipVisuals.Loaded visual) {
        this.entity = entity;
        this.visual = visual;
        this.texture = visual.definition().texture();
        this.redAura = false;
    }

    public void bind(Mob entity, ShipVisuals.Loaded visual, ResourceLocation texture, boolean redAura) {
        bind(entity, visual);
        this.texture = texture;
        this.redAura = redAura;
    }

    public ResourceLocation texture() { return texture; }
    public boolean redAura() { return redAura; }

    public void unbind() {
        entity = null;
        texture = null;
        redAura = false;
    }

    public ShipVisuals.Loaded visual() {
        return visual;
    }

    public ShipVisualPresentation.Expression expression() {
        return expression(0);
    }

    public ShipVisualPresentation.Expression expression(float partialTick) {
        if (!(entity instanceof IShipEmotion emotion)) return ShipVisualPresentation.emotion(0, false);
        int current = emotion.getStateEmotion(ID.S.Emotion);
        if (current != lastEmotion || entity.tickCount < lastExpressionTick) {
            lastEmotion = current;
            emotionStarted = entity.tickCount;
        }
        lastExpressionTick = entity.tickCount;
        return ShipVisualExpression.select(new ShipVisualExpression.Input(entity.tickCount,
                emotion.getStateMinor(ID.M.ShipUID), current, entity.tickCount - emotionStarted,
                emotion.getStateMinor(ID.M.Morale) > ID.Morale.L_Happy,
                emotion.getStateEmotion(ID.S.Emotion4) == ID.Emotion.BORED, selectedPosture(), attackActive, partialTick));
    }

    public float headTilt(float partialTick) {
        if (!(entity instanceof IShipEmotion emotion)) return 0;
        var posture = selectedPosture();
        if (posture == ShipVisualEvent.NO_FUEL || posture == ShipVisualEvent.CARESS || emotion.getRidingState() > 0
                || (posture == ShipVisualEvent.SIT || posture == ShipVisualEvent.RIDE)
                && emotion.getStateEmotion(ID.S.Emotion) == ID.Emotion.BORED) return 0;
        return ShipHeadTilt.angle(entity.tickCount, emotion.getStateMinor(ID.M.ShipUID), partialTick);
    }

    private ShipVisualEvent selectedPosture() {
        return ShipVisualPosture.select(posture(moving), visual.clips().keySet()).orElse(null);
    }

    public boolean equipmentVisible() {
        return entity instanceof IShipEmotion emotion
                && ShipVisualPresentation.equipmentVisible(emotion.getStateEmotion(ID.S.State));
    }

    public boolean hasPosture() {
        return entity != null;
    }

    private ShipVisualPosture.Input posture(boolean moving) {
        if (entity instanceof IShipEmotion emotion) {
            return new ShipVisualPosture.Input(emotion.getStateFlag(ID.F.NoFuel),
                    emotion.getStateEmotion(ID.S.Emotion3) == ID.Emotion3.CARESS,
                    emotion.getIsRiding(), emotion.getIsSitting(), emotion.getIsSprinting(),
                    entity.walkAnimation.speed(), moving);
        }
        return new ShipVisualPosture.Input(false, false, false, false, false, 0, moving);
    }

    public BasicEntityShip heldItemShip() {
        return entity instanceof BasicEntityShip ship ? ship : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "presentation", 2, this::animate));
    }

    PlayState animate(AnimationState<ShipVisualAnimatable> state) {
        if (entity == null) return PlayState.STOP;
        if (state.isMoving()) {
            moving = true;
            lastMovingTick = entity.tickCount;
        } else if (entity.tickCount < lastMovingTick
                || (long) entity.tickCount - lastMovingTick >= MOVEMENT_STOP_GRACE_TICKS) {
            moving = false;
        }
        var attack = ShipVisualClient.attacks(entity);
        int attackTick = entity instanceof IShipEmotion emotion ? emotion.getAttackTick() : 0;
        boolean newAttack = false;
        // The standard packet still covers melee and other untyped legacy animation triggers.
        if (attackTick > lastAttackTick) {
            attackEvent = ShipVisualEvent.LIGHT_ATTACK;
            newAttack = true;
        }
        lastAttackTick = attackTick;
        if (attack != null && attack.sequence() != lastSequence) {
            lastSequence = attack.sequence();
            if (entity.level().getGameTime() - attack.gameTime() <= 10) {
                attackEvent = attack.event();
                newAttack = true;
            }
        }
        if (newAttack) {
            var clip = visual.clips().get(attackEvent);
            if (clip != null) {
                double length = visual.animations().getAnimation(visual.definition().clip(attackEvent)).length();
                attackEnds = entity.level().getGameTime() + Math.min(length, 200);
                state.getController().forceAnimationReset();
            } else {
                attackEvent = null;
            }
        }
        attackActive = attackEvent != null && entity.level().getGameTime() < attackEnds;
        ShipVisualEvent event = attackActive ? attackEvent : selectedPosture();
        if (event == null) return PlayState.STOP;
        var clip = visual.clips().get(event);
        if (clip == null) return PlayState.STOP;
        return state.setAndContinue(clip);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object relatedObject) {
        // GeckoLib adds the frame's partial tick itself only for entity animatables, so it is added here.
        return relatedObject instanceof Mob mob
                ? ShipVisualClock.continuousTick(mob.tickCount, Minecraft.getInstance().getFrameTime()) : 0;
    }
}
