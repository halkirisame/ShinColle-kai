package com.lulan.shincolle.entity;

import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.shipdata.DefinitionBackedShip;
import com.lulan.shincolle.shipdata.GenericShipState;
import com.lulan.shincolle.shipdata.ShipDefinition;
import com.lulan.shincolle.shipdata.ShipDefinitionAttributes;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.lulan.shincolle.utility.BuffHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GenericShipEntity extends BasicEntityShip implements DefinitionBackedShip {
    private final GenericShipState definitionState = new GenericShipState();
    private boolean loadingDefinitionData;
    private com.lulan.shincolle.shipdata.ShipPresentation clientPresentation;

    public GenericShipEntity(EntityType<? extends GenericShipEntity> type, Level level) {
        super(type, level);
        setStateMinor(ID.M.ShipType, ID.ShipType.DESTROYER);
        setStateMinor(ID.M.DamageType, ID.ShipDmgType.DESTROYER);
        setStateMinor(ID.M.NumState, 1);
        setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.DD]);
        setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.DD]);
        StateFlag[ID.F.AtkType_AirLight] = false;
        StateFlag[ID.F.AtkType_AirHeavy] = false;
        StateFlag[ID.F.CanPickItem] = true;
        ModelPos = new float[]{0, 0, 0, 25};
        postInit();
    }

    @Override
    public GenericShipState definitionState() {
        return definitionState;
    }

    @Override
    public void applyParticleAtAttacker(int type, Entity target, Entity target2) {
        super.applyParticleAtAttacker(type, target, target2);
        if (!level().isClientSide() && target != null) {
            ModNetworking.sendToAllTracking(new com.lulan.shincolle.network.S2CShipVisualEventPacket(getId(),
                    com.lulan.shincolle.visualdata.ShipVisualEvent.attack(type)), this);
        }
    }

    public void initialize(ShipDefinitionId id) {
        definitionState.identify(id.toString());
        refreshDefinition();
        setHealth(getMaxHealth());
    }

    private void refreshDefinition() {
        if (definitionState.refresh(() -> super.saveWithoutId(new CompoundTag()), super::load, () -> {
            setStateMinor(ID.M.ShipClass, 0);
            setStateMinor(ID.M.ShipType, definitionState.shipType(false));
            refreshDimensions();
            calcShipAttributes(31, false);
        })) {
            ModNetworking.sendToAllTracking(S2CEntitySyncPacket.syncShipDefinition(this), this);
        }
    }

    @Override
    public int getEquipType() {
        if (level().isClientSide()) {
            var view = definitionState == null ? null : definitionState.presentation(true);
            return view == null ? 1 : view.equipType();
        }
        ShipDefinition definition = definitionState == null ? null : definitionState.definition();
        return definition == null ? 1 : definition.equipType();
    }

    @Override
    protected void setAIList() {
        super.setAIList();
        goalSelector.addGoal(11, new ShipRangeAttackGoal(this));
        goalSelector.addGoal(20, new ShipPickItemGoal(this, 4));
    }

    @Override
    protected void updateRawAttributes() {
        ShipDefinition definition = definitionState.definition();
        if (definition != null) {
            shipAttrs.setAttrsType(ShipDefinitionAttributes.columns(definition.growth()));
            BuffHelper.updateAttrsRaw(shipAttrs, ShipDefinitionAttributes.row(definition), getLevel());
        }
    }

    @Override
    protected net.minecraft.world.item.ItemStack createDeathEggStack() {
        return com.lulan.shincolle.item.GenericShipSpawnEgg.create(ShipDefinitionId.parse(getShipId()), false);
    }

    @Override
    public void checkDespawn() {
        if (!definitionState.dormant()) super.checkDespawn();
    }

    @Override
    public void calcShipAttributes(int flag, boolean sync) {
        if (definitionState != null && definitionState.definition() != null) {
            super.calcShipAttributes(flag, sync);
        }
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            refreshDefinition();
        } else if (clientPresentation != definitionState.presentation(true)) {
            clientPresentation = definitionState.presentation(true);
            refreshDimensions();
        }
        if (!definitionState.dormant()) {
            super.tick();
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        ModNetworking.sendToPlayer(S2CEntitySyncPacket.syncShipDefinition(this), player);
    }

    @Override
    public void rideTick() {
        if (!level().isClientSide()) {
            refreshDefinition();
        }
        // Vanilla positions passengers after tick(), even when tick() was skipped.
        if (!definitionState.dormant()) {
            super.rideTick();
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        definitionState.loaded(nbt);
        loadingDefinitionData = true;
        try {
            super.load(nbt.copy());
            setStateMinor(ID.M.ShipClass, 0);
        } finally {
            loadingDefinitionData = false;
        }
    }

    @Override
    public CompoundTag saveWithoutId(CompoundTag nbt) {
        if (definitionState.savePreserved(nbt)) {
            return nbt;
        }
        return super.saveWithoutId(nbt);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("ShipId", getShipId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        definitionState.identify(nbt.getString("ShipId"));
        super.readAdditionalSaveData(nbt);
        setStateMinor(ID.M.ShipClass, 0);
    }

    @Override
    protected Component getTypeName() {
        return definitionState == null ? super.getTypeName() : definitionState.name(level().isClientSide());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return definitionState == null ? getType().getDimensions()
                : definitionState.dimensions(level().isClientSide(), getScaleLevel());
    }

    @Override
    public double getPassengersRidingOffset() {
        var view = definitionState.presentation(level().isClientSide());
        return (view == null ? 0.9 : view.mountOffset()) * (1 + getScaleLevel() * 0.5);
    }

    @Override
    public byte getShipType() {
        return (byte) (definitionState == null ? ID.ShipType.DESTROYER : definitionState.shipType(level().isClientSide()));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !definitionState.dormant() && super.hurt(source, amount);
    }

    @Override
    public void travel(Vec3 movement) {
        if (!definitionState.dormant()) super.travel(movement);
    }

    private boolean positionFrozen() {
        // Freezing is a server-side authority; a client copy must follow spawn and movement packets.
        return !level().isClientSide() && definitionState != null && definitionState.dormant()
                && !loadingDefinitionData;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (!positionFrozen()) super.move(type, movement);
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (!positionFrozen()) super.setPos(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        // Vanilla passenger teleportation calls moveTo, which bypasses setPos.
        if (!positionFrozen()) super.moveTo(x, y, z, yaw, pitch);
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        // Also covers dismountTo when a vehicle is removed; detaching is still allowed.
        if (!positionFrozen()) super.teleportTo(x, y, z);
    }

    @Override
    public void push(Entity entity) {
        if (!definitionState.dormant()) super.push(entity);
    }

    @Override
    public boolean isPushable() {
        return !definitionState.dormant() && super.isPushable();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return !definitionState.dormant() && definitionState.definition() != null && super.doHurtTarget(target);
    }

    @Override
    public boolean attackEntityWithAmmo(Entity target) {
        return !definitionState.dormant() && definitionState.definition() != null && super.attackEntityWithAmmo(target);
    }

    @Override
    public boolean attackEntityWithHeavyAmmo(Entity target) {
        return !definitionState.dormant() && definitionState.definition() != null && super.attackEntityWithHeavyAmmo(target);
    }

    @Override
    public boolean attackEntityWithHeavyAmmo(net.minecraft.core.BlockPos target) {
        return !definitionState.dormant() && definitionState.definition() != null && super.attackEntityWithHeavyAmmo(target);
    }

    @Override
    public net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,
                                                            net.minecraft.world.InteractionHand hand) {
        return definitionState.dormant() ? net.minecraft.world.InteractionResult.PASS : super.mobInteract(player, hand);
    }

    @Override
    public boolean isCustomNameVisible() {
        return definitionState.dormant() || super.isCustomNameVisible();
    }
}
