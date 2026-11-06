package com.lulan.shincolle.entity;

import com.lulan.shincolle.ai.registry.GenericShipBehaviors;
import com.lulan.shincolle.ai.registry.GenericShipHost;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.shipdata.DefinitionBackedShip;
import com.lulan.shincolle.shipdata.GenericShipState;
import com.lulan.shincolle.shipdata.ShipDefinition;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.lulan.shincolle.shipdata.HostileVariantRoll;
import com.lulan.shincolle.shipdata.HostileVariants;
import com.lulan.shincolle.shipdata.ShipIndividual;
import com.lulan.shincolle.utility.BuffHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GenericHostileShipEntity extends BasicEntityShipHostile implements DefinitionBackedShip, GenericShipHost {
    private final GenericShipState definitionState = new GenericShipState();
    private boolean loadingDefinitionData;
    private ShipIndividual individual = ShipIndividual.normal(false);
    private ShipIndividual.Presentation individualPresentation = ShipIndividual.Presentation.NORMAL;
    private boolean saveIndividual;
    private com.lulan.shincolle.shipdata.ShipPresentation clientPresentation;

    public GenericHostileShipEntity(EntityType<? extends GenericHostileShipEntity> type, Level level) {
        super(type, level);
        setStateMinor(ID.M.ShipType, ID.ShipType.DESTROYER);
        setStateMinor(ID.M.DamageType, ID.ShipDmgType.DESTROYER);
        setStateMinor(ID.M.NumState, 1);
        StateFlag[ID.F.AtkType_AirLight] = false;
        StateFlag[ID.F.AtkType_AirHeavy] = false;
        StateMinor[ID.M.NumAmmoLight] = 999;
        StateMinor[ID.M.NumAmmoHeavy] = 999;
        StateMinor[ID.M.NumGrudge] = 999;
        setAmmoConsumption(1);
        bossBarColor = BossEvent.BossBarColor.YELLOW;
        postInit();
        dropItem = ItemStack.EMPTY;
    }

    @Override
    public GenericShipState definitionState() {
        return definitionState;
    }

    @Override
    public ItemStack getDropEgg() {
        ShipDefinition definition = definitionState.definition();
        dropItem = definition == null ? ItemStack.EMPTY
                : com.lulan.shincolle.item.GenericShipSpawnEgg.create(definition.id(), false);
        CompoundTag recruitment = individual.recruitmentData();
        if (!dropItem.isEmpty() && recruitment != null) {
            dropItem.getOrCreateTag().put(ShipIndividual.KEY, recruitment);
        }
        return super.getDropEgg();
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
        individual = ShipIndividual.normal(false);
        individualPresentation = ShipIndividual.Presentation.NORMAL;
        saveIndividual = false;
        definitionState.identify(id.toString());
        ShipDefinition definition = definitionState.definition();
        saveIndividual = definition != null && definition.hostileVariants() != HostileVariants.NONE;
        refreshDefinition();
        initAttrs(0);
    }

    public ShipIndividual individual() {
        return individual;
    }

    public ShipIndividual.Presentation individualPresentation() {
        return individualPresentation;
    }

    public void receiveIndividualPresentation(ShipIndividual.Presentation presentation) {
        if (level().isClientSide()) individualPresentation = presentation;
    }

    public void selectVariant(HostileVariants.Variant variant) {
        if (level().isClientSide()) return;
        float ratio = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 0;
        individual = ShipIndividual.selected(variant);
        individualPresentation = individual.presentation();
        saveIndividual = variant != null || definitionState.definition().hostileVariants() != HostileVariants.NONE;
        calcShipAttributes(31, true);
        setHealth(getMaxHealth() * ratio);
        creatBossEvent();
        ModNetworking.sendToAllTracking(S2CEntitySyncPacket.syncShipDefinition(this), this);
    }

    private void rollIndividual() {
        if (!definitionState.dormant() && !individual.rolled()) {
            selectVariant(HostileVariantRoll.select(definitionState.definition().hostileVariants(), random::nextInt));
        }
    }

    private void refreshDefinition() {
        if (definitionState.refresh(() -> super.saveWithoutId(new CompoundTag()), super::load, () -> {
            setStateMinor(ID.M.ShipClass, 0);
            setStateMinor(ID.M.ShipType, definitionState.shipType(false));
            refreshDimensions();
            calcShipAttributes(31, false);
        })) {
            if (!level().isClientSide() && !definitionState.dormant() && definitionState.definition() != null
                    && !goalSelector.getAvailableGoals().isEmpty()) {
                clearAITasks();
                setAIList();
            }
            ModNetworking.sendToAllTracking(S2CEntitySyncPacket.syncShipDefinition(this), this);
            creatBossEvent();
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
        GenericShipBehaviors.install(this, true);
    }

    @Override
    protected boolean usesCannonAttack() {
        return false;
    }

    @Override
    public net.minecraft.world.entity.Mob behaviorEntity() {
        return this;
    }

    @Override
    public net.minecraft.world.entity.ai.goal.GoalSelector behaviorGoals() {
        return goalSelector;
    }

    @Override
    public void installStandardAi() {
        super.setAIList();
    }

    @Override
    protected void updateRawAttributes() {
        ShipDefinition definition = definitionState.definition();
        if (definition != null) {
            BuffHelper.updateAttrsRawHostile(shipAttrs, getScaleLevel(),
                    individual.hostileMultipliers(definition));
        }
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
            rollIndividual();
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
        if (saveIndividual) nbt.put(ShipIndividual.KEY, individual.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        definitionState.identify(nbt.getString("ShipId"));
        individual = ShipIndividual.load(nbt, getShipId());
        individualPresentation = individual.presentation();
        ShipDefinition definition = definitionState.definition();
        saveIndividual = nbt.contains(ShipIndividual.KEY) || (!individual.rolled() && definition != null
                && definition.hostileVariants() != HostileVariants.NONE);
        super.readAdditionalSaveData(nbt);
        setStateMinor(ID.M.ShipClass, 0);
    }

    @Override
    protected Component getTypeName() {
        Component typeName = definitionState == null ? super.getTypeName() : definitionState.name(level().isClientSide());
        String name = individualPresentation == null ? "" : individualPresentation.name();
        return name.isEmpty() ? typeName : Component.translatableWithFallback(name, typeName.getString());
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        creatBossEvent();
    }

    @Override
    public void setSizeWithScaleLevel() {
        refreshDimensions();
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
    public boolean isCustomNameVisible() {
        return definitionState.dormant() || super.isCustomNameVisible();
    }
}
