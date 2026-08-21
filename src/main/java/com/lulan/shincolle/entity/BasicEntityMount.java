package com.lulan.shincolle.entity;

import com.lulan.shincolle.ai.path.ShipMoveControl;
import com.lulan.shincolle.ai.path.ShipNavigation;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import com.lulan.shincolle.reference.unitclass.MissileData;
import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for mount entities.
 * Mount entities are ridden by ships and delegate most state to their host
 * ship.
 */
public abstract class BasicEntityMount extends TamableAnimal
        implements IShipMount, IShipCannonAttack, IShipGuardian, IShipCustomTexture {

    /**
     * key input from player riding this mount
     */
    public int keyPressed;
    public int keyTick;
    private int lastDiagKeys = -1;
    /**
     * host ship entity
     */
    protected BasicEntityShip host;
    @Nullable
    private UUID hostUuid;
    /**
     * Host entity ID received by the client before the host spawn packet.
     * This is client-only transient state and must not be persisted.
     */
    private int pendingClientHostId;
    protected ShipMoveControl shipMoveControl;
    /**
     * mount-specific fields
     */
    protected double shipDepth;
    protected AttrsAdv shipAttrs;
    protected int attackTime, attackTime2;
    protected int startEmotion, startEmotion2;
    protected int revengeTime;
    protected float[] seatPos = new float[]{0F, 0F, 0F};
    protected float[] seatPos2 = new float[]{0F, 0F, 0F};
    protected BasicEntityMount(EntityType<? extends BasicEntityMount> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createMountAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    // ========== Static Attribute Builder ==========

    /**
     * ship navigator
     */
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return ShipNavigation.create(this, level);
    }

    // ========== Host Management ==========

    public BasicEntityShip getHost() {
        return this.host;
    }

    public void setHost(BasicEntityShip ship) {
        if (this.host == ship) {
            return;
        }
        this.host = ship;
        if (host != null) {
            setupAttrs();
            if (!this.level().isClientSide()) {
                this.hostUuid = host.getUUID();
                this.setAIList();
            }
        }
    }

    /**
     * Retains a host ID received before the client has created the host entity.
     */
    public void setClientHostId(int hostId) {
        if (!this.level().isClientSide() || hostId <= 0) {
            return;
        }
        this.pendingClientHostId = hostId;
        this.resolveClientHost();
    }

    private void resolveClientHost() {
        if (!this.level().isClientSide() || this.pendingClientHostId <= 0) {
            return;
        }
        Entity entity = this.level().getEntity(this.pendingClientHostId);
        if (entity instanceof BasicEntityShip ship) {
            if (this.host != ship) {
                this.setHost(ship);
            }
            LogHelper.info("DIAG: mount host resolved client mount=" + this.getId()
                    + " host=" + this.pendingClientHostId);
            this.pendingClientHostId = 0;
        }
    }

    public void setupAttrs() {
        if (this.host == null)
            return;
        this.shipAttrs = AttrsAdv.copyAttrsAdv((AttrsAdv) this.host.getAttrs());

        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(this.host.getMaxHealth() * 0.5D);
        }
        if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(
                    host.getAttrs() != null ? host.getAttrs().getMoveSpeed() : 0.3D);
        }
    }

    public void clearRider() {
        for (Entity rider : this.getPassengers()) {
            if (rider != null)
                rider.stopRiding();
        }
        this.discard();
    }

    public void clearRider2() {
        this.setStateEmotion(ID.S.Emotion, 0, false);
        for (Entity rider : this.getPassengers()) {
            if (rider != null)
                rider.stopRiding();
        }
    }

    public boolean canPassengerSteer() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    // ========== Key Input ==========

    /**
     * Set key input from player and reset decay timer
     */
    public void setMountKeyInput(int key) {
        this.keyPressed = key;
        this.keyTick = 10;
    }

    // ========== AI ==========

    public void clearAITasks() {
        this.goalSelector.removeAllGoals(goal -> true);
    }

    public void setAIList() {
        this.clearAITasks();

        BasicEntityMount self = this;

        // mount follow-host AI: follow the host ship when not ridden
        this.goalSelector.addGoal(1, new Goal() {
            private int pathfindCooldown = 0;

            {
                this.setFlags(EnumSet.of(Flag.MOVE));
            }

            @Override
            public boolean canUse() {
                // only follow host when not being ridden by a player
                if (self.host == null || !self.host.isAlive())
                    return false;
                if (!self.getPassengers().isEmpty())
                    return false;
                return self.distanceToSqr(self.host) > 16.0D; // > 4 blocks away
            }

            @Override
            public boolean canContinueToUse() {
                if (self.host == null || !self.host.isAlive())
                    return false;
                if (!self.getPassengers().isEmpty())
                    return false;
                return self.distanceToSqr(self.host) > 4.0D; // > 2 blocks away
            }

            @Override
            public void tick() {
                if (self.host == null)
                    return;

                self.getLookControl().setLookAt(self.host, 30.0F, 30.0F);

                if (--pathfindCooldown <= 0) {
                    pathfindCooldown = 10;
                    self.getNavigation().moveTo(self.host, 1.0D);
                }

                // teleport to host if too far away (> 32 blocks)
                if (self.distanceToSqr(self.host) > 1024.0D) {
                    LogHelper.info("DIAG: mount recall host=" + self.host + " mount=" + self
                            + " distSq=" + self.distanceToSqr(self.host));
                    self.setPos(self.host.getX(), self.host.getY(), self.host.getZ());
                }
            }

            @Override
            public void stop() {
                self.getNavigation().stop();
            }
        });

    }

    // ========== IShipCannonAttack ==========

    @Override
    public boolean attackEntityWithAmmo(Entity target) {
        if (this.host != null)
            return this.host.attackEntityWithAmmo(target);
        return false;
    }

    @Override
    public boolean attackEntityWithHeavyAmmo(Entity target) {
        if (this.host != null)
            return this.host.attackEntityWithHeavyAmmo(target);
        return false;
    }

    @Override
    public boolean updateSkillAttack(Entity target) {
        return false;
    }

    // ========== Delegated State Methods ==========

    public int getStateMinor(int id) {
        if (this.host != null)
            return this.host.getStateMinor(id);
        return 0;
    }

    public void setStateMinor(int id, int par1) {
        if (this.host != null)
            this.host.setStateMinor(id, par1);
    }

    public boolean getStateFlag(int flag) {
        if (this.host != null)
            return this.host.getStateFlag(flag);
        return false;
    }

    public void setStateFlag(int id, boolean par1) {
        if (this.host != null)
            this.host.setStateFlag(id, par1);
    }

    public int getStateEmotion(int id) {
        if (this.host != null)
            return this.host.getStateEmotion(id);
        return 0;
    }

    public void setStateEmotion(int id, int value, boolean sync) {
        if (this.host != null)
            this.host.setStateEmotion(id, value, sync);
    }

    public int getStateTimer(int id) {
        if (this.host != null)
            return this.host.getStateTimer(id);
        return 0;
    }

    public void setStateTimer(int id, int value) {
        if (this.host != null)
            this.host.setStateTimer(id, value);
    }

    // ========== IShipAttackBase ==========

    public Entity getEntityTarget() {
        if (this.host != null)
            return this.host.getTarget();
        return null;
    }

    public void setEntityTarget(Entity target) {
        if (this.host != null)
            this.host.setTarget(target instanceof LivingEntity living ? living : null);
    }

    public Entity getEntityRevengeTarget() {
        if (this.host != null)
            return this.host.getEntityRevengeTarget();
        return null;
    }

    public void setEntityRevengeTarget(Entity target) {
        if (this.host != null)
            this.host.setEntityRevengeTarget(target);
    }

    public int getEntityRevengeTime() {
        if (this.host != null)
            return this.host.getEntityRevengeTime();
        return 0;
    }

    public void setEntityRevengeTime() {
        this.revengeTime = this.tickCount;
    }

    public int getDamageType() {
        if (this.host != null)
            return this.host.getDamageType();
        return 0;
    }

    public boolean getAttackType(int par1) {
        if (this.host != null)
            return this.host.getAttackType(par1);
        return true;
    }

    public int getAmmoLight() {
        if (this.host != null)
            return this.host.getAmmoLight();
        return 0;
    }

    public void setAmmoLight(int num) {
    }

    public int getAmmoHeavy() {
        if (this.host != null)
            return this.host.getAmmoHeavy();
        return 0;
    }

    public void setAmmoHeavy(int num) {
    }

    public boolean hasAmmoLight() {
        return this.getAmmoLight() > 0;
    }

    public boolean hasAmmoHeavy() {
        return this.getAmmoHeavy() > 0;
    }

    public boolean useAmmoLight() {
        if (this.host != null)
            return this.host.useAmmoLight();
        return false;
    }

    public boolean useAmmoHeavy() {
        if (this.host != null)
            return this.host.useAmmoHeavy();
        return false;
    }

    public int getLevel() {
        if (this.host != null)
            return this.host.getLevel();
        return 0;
    }

    // ========== IShipNavigator ==========

    public ShipMoveControl getShipMoveHelper() {
        return this.shipMoveControl;
    }

    public boolean canFly() {
        return false;
    }

    public boolean isJumping() {
        return this.jumping;
    }

    public float getMoveSpeed() {
        if (this.host != null && this.host.getAttrs() != null) {
            return this.host.getAttrs().getMoveSpeed();
        }
        return 0.3F;
    }

    public float getJumpSpeed() {
        return 1F;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.FAIL;
        }

        if (this.level().isClientSide()) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.POINTER.get()) && PointerItem.getMode(stack) > PointerItem.MODE_FORMATION && this.host != null) {
            if (this.host.getMorale() < (int) (ID.Morale.L_Excited * 1.3F)) {
                this.host.addMorale(ConfigHandler.caressBaseMorale());
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.LEAD)) {
            this.getNavigation().stop();
            return InteractionResult.SUCCESS;
        }

        if (!player.isShiftKeyDown() && this.distanceToSqr(player) < 16D && !TeamHelper.checkIsBanned(this, player)) {
            player.startRiding(this, true);
            this.setStateEmotion(ID.S.Emotion, 1, false);
            this.sendSyncPacket(0);
            return InteractionResult.SUCCESS;
        }

        if (!player.isShiftKeyDown() && TeamHelper.checkSameOwner(player, this.host)) {
            this.host.setEntitySit(!this.host.isOrderedToSit());
            this.jumping = false;
            this.getNavigation().stop();
            this.host.getNavigation().stop();
            this.setTarget(null);
            this.setEntityTarget(null);
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown() && TeamHelper.checkSameOwner(player, this.host)) {
            if (player instanceof ServerPlayer serverPlayer) {
                this.host.openGUI(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        if (!this.isAlive() || passenger == null || passenger.getVehicle() != this) {
            return;
        }

        float[] ridePos;
        if (passenger instanceof BasicEntityShip) {
            ridePos = this.seatPos;
        } else if (passenger instanceof Player) {
            ridePos = this.seatPos2;
        } else {
            callback.accept(passenger, this.getX(), this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset(), this.getZ());
            return;
        }

        float[] rotatedPos = CalcHelper.rotateXZByAxis(ridePos[0], ridePos[2], this.getYRot() * Values.N.DIV_PI_180, 1F);
        callback.accept(passenger, this.getX() + rotatedPos[1],
                this.getY() + ridePos[1] + passenger.getMyRidingOffset(), this.getZ() + rotatedPos[0]);
    }

    // ========== IShipEmotion ==========

    public boolean getIsRiding() {
        return false;
    }

    public boolean getIsSprinting() {
        return false;
    }

    public boolean getIsSitting() {
        if (this.host != null)
            return this.host.getIsSitting();
        return false;
    }

    public boolean getIsSneaking() {
        return false;
    }

    public boolean getIsLeashed() {
        return false;
    }

    public double getShipDepth() {
        return this.shipDepth;
    }

    public void setShipDepth(double par1) {
        this.shipDepth = par1;
    }

    public double getShipDepth(int type) {
        if (type == 2 && this.host != null) {
            return this.host.getShipDepth();
        }
        return this.shipDepth;
    }

    public float getModelRotate(int par1) {
        return 0F;
    }

    public void setModelRotate(int par1, float par2) {
    }

    public int getTickExisted() {
        return this.tickCount;
    }

    public float getSwingTime(float partialTick) {
        return this.getAttackAnim(partialTick);
    }

    public int getFaceTick() {
        return this.startEmotion;
    }

    public void setFaceTick(int par1) {
        this.startEmotion = par1;
    }

    public int getHeadTiltTick() {
        return this.startEmotion2;
    }

    public void setHeadTiltTick(int par1) {
        this.startEmotion2 = par1;
    }

    public int getAttackTick() {
        return this.attackTime;
    }

    public void setAttackTick(int par1) {
        this.attackTime = par1;
    }

    public int getAttackTick2() {
        return this.attackTime2;
    }

    public void setAttackTick2(int par1) {
        this.attackTime2 = par1;
    }

    public int getDeathTick() {
        return this.deathTime;
    }

    public void setDeathTick(int par1) {
        this.deathTime = par1;
    }

    public void setEntitySit(boolean sit) {
        if (this.host != null)
            host.setEntitySit(sit);
    }

    public int getRidingState() {
        return 0;
    }

    public void setRidingState(int state) {
    }

    public int getScaleLevel() {
        return 0;
    }

    public void setScaleLevel(int par1) {
    }

    // ========== IShipOwner ==========

    public RandomSource getRand() {
        return this.random;
    }

    public int getPlayerUID() {
        if (this.host != null)
            return this.host.getPlayerUID();
        return -1;
    }

    public void setPlayerUID(int uid) {
    }

    // ========== IShipAttrs ==========

    public Entity getHostEntity() {
        return this.host;
    }

    public Attrs getAttrs() {
        if (this.host != null)
            return this.host.getAttrs();
        return null;
    }

    public void setAttrs(Attrs data) {
    }

    public HashMap<Integer, Integer> getBuffMap() {
        if (this.host != null)
            return this.host.getBuffMap();
        return new HashMap<>();
    }

    public void setBuffMap(HashMap<Integer, Integer> map) {
    }

    public HashMap<Integer, int[]> getAttackEffectMap() {
        if (this.host != null)
            return this.host.getAttackEffectMap();
        return new HashMap<>();
    }

    public void setAttackEffectMap(HashMap<Integer, int[]> map) {
    }

    public MissileData getMissileData(int type) {
        if (this.host != null)
            return this.host.getMissileData(type);
        return new MissileData();
    }

    // ========== IShipGuardian ==========

    public void setMissileData(int type, MissileData data) {
    }

    @Override
    public Entity getGuardedEntity() {
        if (this.host != null)
            return this.host.getGuardedEntity();
        return null;
    }

    @Override
    public void setGuardedEntity(Entity entity) {
        if (this.host != null)
            this.host.setGuardedEntity(entity);
    }

    @Override
    public int getGuardedPos(int vec) {
        if (this.host != null)
            return this.host.getGuardedPos(vec);
        return -1;
    }

    @Override
    public void setGuardedPos(int x, int y, int z, int dim, int type) {
        if (this.host != null)
            this.host.setGuardedPos(x, y, z, dim, type);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            this.resolveHost();
            this.setNoAi(BasicEntityShip.stopAI);
        } else {
            this.resolveClientHost();
        }
        super.tick();

        if (this.keyTick > 0) {
            boolean diagKeysChanged = this.keyPressed != this.lastDiagKeys;
            if (diagKeysChanged) {
                this.lastDiagKeys = this.keyPressed;
                LogHelper.info("DIAG: mount input side=" + (this.level().isClientSide() ? "client" : "server")
                        + " keys=" + Integer.toBinaryString(this.keyPressed)
                        + " keyTick=" + this.keyTick
                        + " localInstance=" + this.isControlledByLocalInstance()
                        + " rider=" + this.getControllingPassenger()
                        + " host=" + this.host
                        + " noFuel=" + (this.host != null && this.host.getStateFlag(ID.F.NoFuel))
                        + " motion=" + this.getDeltaMovement());
            }

            // [PORT] 1.10.2 -> 1.20.1: ridden-entity physics runs on the
            // controlling player's client and vanilla sends the resulting
            // position back to the server with ServerboundMoveVehiclePacket.
            if (this.isControlledByLocalInstance() && this.host != null
                    && !this.host.getStateFlag(ID.F.NoFuel)) {
                LivingEntity rider = this.getControllingPassenger();
                if (rider instanceof Player) {
                    float pitch = rider.getXRot() * Values.N.DIV_PI_180;
                    float yaw = rider.getYHeadRot() * Values.N.DIV_PI_180;
                    if (diagKeysChanged) {
                        LogHelper.info("DIAG: mount orient yaw=" + yaw + " pitch=" + pitch
                                + " keys=" + Integer.toBinaryString(this.keyPressed)
                                + " yRotBefore=" + this.getYRot()
                                + " motionBefore=" + this.getDeltaMovement());
                    }
                    this.applyMovement(pitch, yaw);
                    if (diagKeysChanged) {
                        LogHelper.info("DIAG: mount orient result motion=" + this.getDeltaMovement());
                    }
                    // Keep the model and movement on the same instantaneous look yaw.
                    this.setYRot(rider.getYHeadRot());
                    this.setYHeadRot(rider.getYHeadRot());
                    this.setYBodyRot(rider.getYHeadRot());
                }
            }
            this.keyTick--;
        }
    }

    private void applyMovement(float pitch, float yaw) {
        final float moveSpeed = this.getMoveSpeed();
        // The ported helper rotates opposite to the 1.10.2 helper. Negating yaw
        // restores the original mount movement vectors without changing other callers.
        final float[] moveZ = CalcHelper.rotateXZByAxis(moveSpeed, 0F, -yaw, 1F);
        final float[] moveX = CalcHelper.rotateXZByAxis(0F, moveSpeed, -yaw, 1F);
        Vec3 motion = this.getDeltaMovement();
        double motionX = motion.x;
        double motionY = motion.y;
        double motionZ = motion.z;

        if ((this.keyPressed & 16) > 0) {
            this.getJumpControl().jump();
            if (this.getShipDepth() > 0) {
                motionY += moveSpeed * 0.1F;
                if (motionY > 1D) {
                    motionY = 1D;
                }
            }
        }

        if (this.onGround() || EntityHelper.checkEntityIsInLiquid(this)) {
            if ((this.keyPressed & 1) > 0) {
                motionX += moveZ[1] * 0.25F;
                if (Math.abs(motionX) > Math.abs(moveZ[1])) {
                    motionX = moveZ[1];
                }
                motionZ += moveZ[0] * 0.25F;
                if (Math.abs(motionZ) > Math.abs(moveZ[0])) {
                    motionZ = moveZ[0];
                }

                if (pitch > 1F) {
                    motionY += -0.1F;
                    if (motionY < -moveSpeed * 0.5F) {
                        motionY = -moveSpeed * 0.5F;
                    }
                } else if (pitch < -1F) {
                    motionY += 0.1F;
                    if (motionY > moveSpeed * 0.5F) {
                        motionY = moveSpeed * 0.5F;
                    }
                }
            }
            if ((this.keyPressed & 2) > 0) {
                motionX -= moveZ[1] * 0.25F;
                if (Math.abs(motionX) > Math.abs(moveZ[1])) {
                    motionX = -moveZ[1];
                }
                motionZ -= moveZ[0] * 0.25F;
                if (Math.abs(motionZ) > Math.abs(moveZ[0])) {
                    motionZ = -moveZ[0];
                }

                if (pitch > 1F) {
                    motionY += 0.1F;
                    if (motionY > moveSpeed * 0.5F) {
                        motionY = moveSpeed * 0.5F;
                    }
                } else if (pitch < -1F) {
                    motionY += -0.1F;
                    if (motionY < -moveSpeed * 0.5F) {
                        motionY = -moveSpeed * 0.5F;
                    }
                }
            }
            if ((this.keyPressed & 4) > 0) {
                motionX += moveX[1] * 0.25F;
                if (Math.abs(motionX) > Math.abs(moveX[1])) {
                    motionX = moveX[1];
                }
                motionZ += moveX[0] * 0.25F;
                if (Math.abs(motionZ) > Math.abs(moveX[0])) {
                    motionZ = moveX[0];
                }
            }
            if ((this.keyPressed & 8) > 0) {
                motionX -= moveX[1] * 0.25F;
                if (Math.abs(motionX) > Math.abs(moveX[1])) {
                    motionX = -moveX[1];
                }
                motionZ -= moveX[0] * 0.25F;
                if (Math.abs(motionZ) > Math.abs(moveX[0])) {
                    motionZ = -moveX[0];
                }
            }
            if (this.horizontalCollision) {
                motionY += 0.4D;
            }
        } else {
            if ((this.keyPressed & 1) > 0) {
                motionX += moveZ[1] * 0.25F;
                if (Math.abs(motionX) > Math.abs(moveZ[1])) {
                    motionX = moveZ[1];
                }
                motionZ += moveZ[0] * 0.25F;
                if (Math.abs(motionZ) > Math.abs(moveZ[0])) {
                    motionZ = moveZ[0];
                }
            }
            if ((this.keyPressed & 2) > 0) {
                motionX -= moveZ[1] * 0.25F;
                if (Math.abs(motionX) > Math.abs(moveZ[1])) {
                    motionX = -moveZ[1];
                }
                motionZ -= moveZ[0] * 0.25F;
                if (Math.abs(motionZ) > Math.abs(moveZ[0])) {
                    motionZ = -moveZ[0];
                }
            }
            if ((this.keyPressed & 4) > 0) {
                motionX += moveX[1] * 0.03125F;
                if (Math.abs(motionX) > Math.abs(moveX[1])) {
                    motionX = moveX[1];
                }
                motionZ += moveX[0] * 0.03125F;
                if (Math.abs(motionZ) > Math.abs(moveX[0])) {
                    motionZ = moveX[0];
                }
            }
            if ((this.keyPressed & 8) > 0) {
                motionX -= moveX[1] * 0.03125F;
                if (Math.abs(motionX) > Math.abs(moveX[1])) {
                    motionX = -moveX[1];
                }
                motionZ -= moveX[0] * 0.03125F;
                if (Math.abs(motionZ) > Math.abs(moveX[0])) {
                    motionZ = -moveX[0];
                }
            }
        }

        this.setDeltaMovement(motionX, motionY, motionZ);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.hostUuid != null) {
            tag.putUUID("HostUUID", this.hostUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.hostUuid = tag.hasUUID("HostUUID") ? tag.getUUID("HostUUID") : null;
    }

    private void resolveHost() {
        if (this.host != null || this.hostUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity entity = serverLevel.getEntity(this.hostUuid);
        if (entity instanceof BasicEntityShip ship) {
            this.setHost(ship);
            LogHelper.info("DIAG: mount host resolved server mount=" + this.getId()
                    + " host=" + ship.getId());
            // The mount may have begun tracking before its saved host reference
            // could be resolved. Re-send the host ID to those tracking clients.
            this.sendSyncPacket(4);
        }
    }

    @Override
    public boolean isGuardedInCurrentDimension() {
        return this.host == null || this.host.isGuardedInCurrentDimension();
    }

    @Override
    public BlockPos getLastWaypoint() {
        if (this.host != null)
            return this.host.getLastWaypoint();
        return BlockPos.ZERO;
    }

    @Override
    public void setLastWaypoint(BlockPos pos) {
        if (this.host != null)
            this.host.setLastWaypoint(pos);
    }

    @Override
    public int getWpStayTime() {
        if (this.host != null)
            return host.getStateTimer(ID.T.WpStayTime);
        return 0;
    }

    @Override
    public void setWpStayTime(int time) {
        if (this.host != null)
            host.setStateTimer(ID.T.WpStayTime, time);
    }

    // ========== IShipFloating ==========

    @Override
    public int getWpStayTimeMax() {
        if (this.host != null)
            return host.getWpStayTimeMax();
        return 0;
    }

    public double getShipFloatingDepth() {
        return 0.3D;
    }

    public void setShipFloatingDepth(double par1) {
    }

    // ========== IShipCustomTexture ==========

    @Override
    public int getTextureID() {
        if (this.host != null)
            return this.host.getTextureID();
        return 0;
    }

    @Override
    public void setTextureID(int id) {
    }

    // ========== Update Flag ==========

    public void setUpdateFlag(int id, boolean value) {
    }

    public boolean getUpdateFlag(int id) {
        return false;
    }

    // ========== Seat Positions ==========

    public float[] getSeatPos() {
        return this.seatPos;
    }

    public void setSeatPos(float[] pos) {
        this.seatPos = pos;
    }

    public float[] getSeatPos2() {
        return this.seatPos2;
    }

    public void setSeatPos2(float[] pos) {
        this.seatPos2 = pos;
    }

    // ========== Network Sync ==========

    /**
     * Send sync packet to tracking clients.
     * type: 0=emotion, 1=motion, 2=rotation, 3=posrot, 4=riders
     */
    public void sendSyncPacket(int type) {
        if (!this.level().isClientSide()) {
            switch (type) {
                case 0:
                    ModNetworking.sendToAllTracking(
                            S2CEntitySyncPacket.syncEmotion(this), this);
                    break;
                case 1:
                    ModNetworking.sendToAllTracking(
                            S2CEntitySyncPacket.syncMotion(this), this);
                    break;
                case 2:
                    ModNetworking.sendToAllTracking(
                            S2CEntitySyncPacket.syncRotation(this), this);
                    break;
                case 3:
                    ModNetworking.sendToAllTracking(
                            S2CEntitySyncPacket.syncPosRot(this), this);
                    break;
                case 4:
                    ModNetworking.sendToAllTracking(
                            S2CEntitySyncPacket.syncRiders(this), this);
                    break;
            }
        }
    }
}
