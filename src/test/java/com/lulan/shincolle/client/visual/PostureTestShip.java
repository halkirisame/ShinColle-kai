package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.reference.ID;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;

import java.lang.reflect.Field;

/** A real Mob that reports only the posture inputs; built without a world so no server or client is needed. */
final class PostureTestShip extends Mob implements IShipEmotion, com.lulan.shincolle.shipdata.DefinitionBackedShip {
    private com.lulan.shincolle.shipdata.GenericShipState definitionState;
    private boolean noFuel;
    private boolean sitting;
    private boolean riding;
    private boolean sprinting;
    int emotion;
    int emotion3;
    int emotion4;
    int shipUid;
    int morale;
    int attackTick;
    net.minecraft.world.level.Level world;

    private PostureTestShip() {
        super(null, null);
    }

    static PostureTestShip build(boolean noFuel, boolean sitting, boolean riding, boolean sprinting) {
        try {
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var ship = (PostureTestShip) unsafe.allocateInstance(PostureTestShip.class);
            Field walk = LivingEntity.class.getField("walkAnimation");
            walk.setAccessible(true);
            walk.set(ship, new WalkAnimationState());
            ship.noFuel = noFuel;
            ship.sitting = sitting;
            ship.riding = riding;
            ship.sprinting = sprinting;
            return ship;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Override public boolean getStateFlag(int flag) { return flag == ID.F.NoFuel && noFuel; }
    @Override public com.lulan.shincolle.shipdata.GenericShipState definitionState() {
        if (definitionState == null) definitionState = new com.lulan.shincolle.shipdata.GenericShipState();
        return definitionState;
    }
    @Override public boolean getIsSitting() { return sitting; }
    @Override public boolean getIsRiding() { return riding; }
    @Override public boolean getIsSprinting() { return sprinting; }
    @Override public int getAttackTick() { return attackTick; }
    @Override public int getStateEmotion(int id) {
        return id == ID.S.Emotion ? emotion : id == ID.S.Emotion3 ? emotion3 : id == ID.S.Emotion4 ? emotion4 : 0;
    }
    @Override public int getStateMinor(int id) { return id == ID.M.ShipUID ? shipUid : id == ID.M.Morale ? morale : 0; }
    @Override public net.minecraft.world.level.Level level() { return world; }
    @Override public int getScaleLevel() { return 0; }

    @Override public void setStateMinor(int state, int par1) { }
    @Override public void setStateFlag(int id, boolean flag) { throw new AssertionError("Renderer wrote a state flag"); }
    @Override public void setUpdateFlag(int id, boolean value) { }
    @Override public boolean getUpdateFlag(int id) { return false; }
    @Override public void setStateEmotion(int id, int value, boolean sync) { throw new AssertionError("Renderer wrote emotion"); }
    @Override public int getStateTimer(int id) { return 0; }
    @Override public void setStateTimer(int id, int value) { }
    @Override public int getFaceTick() { return 0; }
    @Override public void setFaceTick(int par1) { throw new AssertionError("Renderer wrote face tick"); }
    @Override public int getHeadTiltTick() { return 0; }
    @Override public void setHeadTiltTick(int par1) { throw new AssertionError("Renderer wrote head tilt tick"); }
    @Override public void setAttackTick(int par1) { }
    @Override public int getAttackTick2() { return 0; }
    @Override public void setAttackTick2(int par1) { }
    @Override public int getDeathTick() { return 0; }
    @Override public void setDeathTick(int par1) { }
    @Override public float getModelRotate(int par1) { return 0; }
    @Override public void setModelRotate(int par1, float par2) { }
    @Override public int getTickExisted() { return tickCount; }
    @Override public float getSwingTime(float partialTick) { return 0; }
    @Override public boolean getIsSneaking() { return false; }
    @Override public boolean getIsLeashed() { return false; }
    @Override public void setEntitySit(boolean sit) { }
    @Override public int getRidingState() { return 0; }
    @Override public void setRidingState(int state) { }
    @Override public void setScaleLevel(int par1) { }
    @Override public RandomSource getRand() { throw new AssertionError("Renderer consumed entity random"); }
    @Override public double getShipDepth(int type) { return 0; }
}
