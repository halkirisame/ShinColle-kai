package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.client.gui.inventory.ContainerVolCore;
import com.lulan.shincolle.client.particle.VolCoreClientEffects;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlockEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.BlockHelper;
import com.lulan.shincolle.utility.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Block entity for the Volcano Structure Core block.
 * Consumes fuel to provide AOE healing/buff effects to nearby ships.
 * <p>
 * Slot layout (9 slots): All fuel input slots
 */
public class TileEntityVolCore extends BasicTileInventory implements MenuProvider, ITileFurnace {

    public static final int SLOT_COUNT = 9;
    /**
     * AOE effect range (blocks)
     */
    private static final double EFFECT_RANGE = 6.0D;
    private static final int OUT_OF_COMBAT_TICKS = 128;
    private static final float HEAL_RATE = 0.01F;
    private static final float HEAL_FLAT = 4.0F;
    private static final float MORALE_CAP_RATE = 1.8F;
    private static final int MORALE_GAIN = 80;
    // Config values loaded from ConfigHandler
    private static int POWER_MAX;
    private static int CONSUMED_SPEED;
    private static int FUEL_VALUE;

    static {
        reloadConfig();
    }

    /**
     * Whether the block is able to operate (has fuel)
     */
    private boolean canWork = false;
    /**
     * Whether the activation button is pressed
     */
    private boolean btnActive = false;
    /**
     * Remaining fuel power in storage
     */
    private int remainedPower = 0;
    /**
     * Sync timer
     */
    private int syncTime = 0;
    /**
     * Client-only particle timer
     */
    private int clientTick = 0;

    public TileEntityVolCore(BlockPos pos, BlockState state) {
        this(ModBlockEntities.VOL_CORE.get(), pos, state);
    }

    public TileEntityVolCore(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, SLOT_COUNT);
    }

    public static void reloadConfig() {
        double[] cfg = ConfigHandler.tileVolCore;
        POWER_MAX = (int) cfg[0];
        CONSUMED_SPEED = (int) cfg[1];
        FUEL_VALUE = (int) cfg[2];
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEntityVolCore tile) {
        tile.tickServer();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileEntityVolCore tile) {
        tile.tickClient(level, pos);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.shincolle_kai.vol_core");
    }

    // ==================== ITileFurnace ====================

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerVolCore(containerId, playerInv, this);
    }

    @Override
    public int getPowerConsumed() {
        return 0;
    }

    @Override
    public void setPowerConsumed(int v) {
    }

    @Override
    public int getPowerGoal() {
        return 0;
    }

    @Override
    public void setPowerGoal(int v) {
    }

    @Override
    public int getPowerRemained() {
        return remainedPower;
    }

    @Override
    public void setPowerRemained(int value) {
        this.remainedPower = value;
    }

    @Override
    public int getPowerMax() {
        return POWER_MAX;
    }

    @Override
    public void setPowerMax(int v) {
    }

    // ==================== State ====================

    @Override
    public float getFuelMagni() {
        return FUEL_VALUE;
    }

    public boolean isWorking() {
        return canWork && btnActive;
    }

    public boolean isBtnActive() {
        return btnActive;
    }

    public void setBtnActive(boolean active) {
        boolean wasWorking = isWorking();
        this.btnActive = active;
        setChanged();
        sendWorkingStateUpdate(wasWorking);
    }

    // ==================== Fuel ====================

    /**
     * Get fuel bar scale for GUI rendering
     */
    public int getPowerRemainingScaled(int pixels) {
        if (POWER_MAX <= 0)
            return 0;
        return remainedPower * pixels / POWER_MAX;
    }

    // ==================== AOE Effects ====================

    /**
     * Consume fuel items from inventory, adding to power storage
     */
    private void decrItemFuel() {
        if (remainedPower >= POWER_MAX)
            return;

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            int fuel = 0;
            if (stack.is(ModItems.GRUDGE.get())) {
                fuel = FUEL_VALUE;
            } else if (stack.is(ModItems.GRUDGE_BLOCK_ITEM.get())) {
                fuel = FUEL_VALUE * 9;
            }

            if (fuel > 0 && remainedPower + fuel < POWER_MAX) {
                stack.shrink(1);
                remainedPower += fuel;
                if (stack.isEmpty()) {
                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
                setChanged();
                return;
            }
        }
    }

    // ==================== Tick Logic ====================

    /**
     * Apply the liquid recovery or dry-area ignition effect.
     */
    private void volcoreFunction() {
        if (level == null)
            return;

        Vec3 center = Vec3.atCenterOf(worldPosition);
        AABB area = createEffectArea(center);

        if (BlockHelper.checkBlockNearbyIsLiquid(level, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), 1)) {
            recoverShipsInLiquid(area);
        } else {
            igniteLivingEntities(area);
        }
    }

    private void recoverShipsInLiquid(AABB area) {
        List<BasicEntityShip> ships = level.getEntitiesOfClass(BasicEntityShip.class, area);
        for (BasicEntityShip ship : ships) {
            if (ship.tickCount - ship.getCombatTick() <= OUT_OF_COMBAT_TICKS
                    || !EntityHelper.checkEntityIsInLiquid(ship)) {
                continue;
            }

            float maxHealth = ship.getMaxHealth();
            if (ship.getHealth() < maxHealth) {
                ship.heal(maxHealth * HEAL_RATE + HEAL_FLAT);
            }
            if (ship.getMorale() < (int) (ID.Morale.L_Excited * MORALE_CAP_RATE)) {
                ship.addMorale(MORALE_GAIN);
            }
        }
    }

    private void igniteLivingEntities(AABB area) {
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area);
        for (LivingEntity entity : entities) {
            if (entity instanceof BasicEntityShip || entity instanceof BasicEntityMount
                    || entity instanceof BasicEntityAirplane || entity instanceof BasicEntityShipHostile) {
                return;
            }

            entity.setSecondsOnFire(2);
            entity.hurt(level.damageSources().inFire(), 4.0F);
            int emote = switch (level.random.nextInt(5)) {
                case 0 -> 12;
                case 1 -> 28;
                case 2 -> 0;
                default -> 2;
            };
            sendEntityEmote(entity, entity.getBbHeight() * 0.75F, emote);
        }
    }

    private void volcoreEmotes() {
        Vec3 center = new Vec3(worldPosition.getX() + 0.5D, worldPosition.getY() + 2.5D,
                worldPosition.getZ() + 0.5D);
        AABB area = createEffectArea(center);
        int emote = switch (level.random.nextInt(5)) {
            case 0 -> 2;
            case 1 -> 30;
            case 2 -> 10;
            default -> 27;
        };

        for (BasicEntityShip ship : level.getEntitiesOfClass(BasicEntityShip.class, area)) {
            ship.applyParticleEmotion(emote);
        }
    }

    private static AABB createEffectArea(Vec3 center) {
        return new AABB(center.x - EFFECT_RANGE, center.y - EFFECT_RANGE, center.z - EFFECT_RANGE,
                center.x + EFFECT_RANGE, center.y + EFFECT_RANGE, center.z + EFFECT_RANGE);
    }

    private static void sendEntityEmote(LivingEntity entity, float height, int emote) {
        int encodedHeight = (int) (height * 100.0F);
        S2CSpawnParticlePacket packet = new S2CSpawnParticlePacket((byte) 36, entity.getId(),
                new byte[]{(byte) (encodedHeight >> 8), (byte) (encodedHeight & 0xFF), 0, (byte) emote});
        ModNetworking.sendToAllTracking(packet, entity);
    }

    private void tickServer() {
        boolean sendUpdate = false;
        syncTime++;

        if ((syncTime & 15) == 0) {
            boolean wasWorking = isWorking();
            boolean hadFuel = canWork;
            canWork = remainedPower >= CONSUMED_SPEED;

            if (hadFuel != canWork) {
                sendUpdate = true;
            }
            sendWorkingStateUpdate(wasWorking);

            if (isWorking()) {
                remainedPower -= CONSUMED_SPEED;
            }

            if ((syncTime & 31) == 0) {
                decrItemFuel();

                if (isWorking()) {
                    volcoreFunction();
                    if ((syncTime & 255) == 0) {
                        volcoreEmotes();
                    }
                }
            }
        }

        if (sendUpdate || syncTime > 12000) {
            syncTime = 0;
            setChanged();
        }
    }

    private void tickClient(Level level, BlockPos pos) {
        clientTick++;
        if ((clientTick & 15) == 0 && isWorking()) {
            VolCoreClientEffects.spawnParticles(level, pos);
        }
    }

    private void sendWorkingStateUpdate(boolean wasWorking) {
        if (wasWorking != isWorking() && level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("CanWork", canWork);
        tag.putBoolean("BtnActive", btnActive);
        tag.putInt("RemainedPower", remainedPower);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        canWork = tag.getBoolean("CanWork");
        btnActive = tag.getBoolean("BtnActive");
        remainedPower = tag.getInt("RemainedPower");
    }

    // ==================== Client-Server Sync ====================

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }
}
