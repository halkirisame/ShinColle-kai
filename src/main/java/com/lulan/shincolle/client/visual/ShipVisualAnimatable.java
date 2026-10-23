package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.visualdata.ShipVisualPresentation;
import com.lulan.shincolle.visualdata.ShipVisualClock;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import com.lulan.shincolle.visualdata.ShipVisualPosture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.InstancedAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

/** One client-only animation instance per entity; it never retains the entity between render calls. */
public final class ShipVisualAnimatable implements GeoAnimatable {
    private final AnimatableInstanceCache cache = new InstancedAnimatableInstanceCache(this);
    private Mob entity;
    private ShipVisuals.Loaded visual;
    private int lastAttackTick;
    private long lastSequence;
    private double attackEnds;
    private ShipVisualEvent attackEvent;

    public void bind(Mob entity, ShipVisuals.Loaded visual) {
        this.entity = entity;
        this.visual = visual;
    }

    public void unbind() {
        entity = null;
    }

    public ShipVisuals.Loaded visual() {
        return visual;
    }

    public ShipVisualPresentation.Expression expression() {
        return entity instanceof IShipEmotion emotion ? ShipVisualPresentation.emotion(
                emotion.getStateEmotion(ID.S.Emotion), emotion.getStateMinor(ID.M.Morale) > ID.Morale.L_Happy)
                : ShipVisualPresentation.emotion(0, false);
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
        ShipVisualEvent event = attackEvent != null && entity.level().getGameTime() < attackEnds ? attackEvent :
                ShipVisualPosture.select(posture(state.isMoving()), visual.clips().keySet()).orElse(null);
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
