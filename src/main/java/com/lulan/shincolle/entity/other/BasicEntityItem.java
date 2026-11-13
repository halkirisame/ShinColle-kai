package com.lulan.shincolle.entity.other;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.SafariNetItem;
import com.lulan.shincolle.item.SavedEggOwnership;
import com.lulan.shincolle.item.SoulTalismanItem;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Custom entity item with:
 * 1. Owner checking for ship spawn eggs
 * 2. Fire proof
 * 3. Can't be pushed
 * 4. Despawn timer for eggs
 * <p>
 * Ported from 1.10.2 BasicEntityItem.
 */
public class BasicEntityItem extends Entity {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(BasicEntityItem.class,
            EntityDataSerializers.ITEM_STACK);

    private int delayBeforeCanPickup;
    private String owner;

    public BasicEntityItem(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = false;
        this.delayBeforeCanPickup = 10;
    }

    public BasicEntityItem(EntityType<?> type, Level level, double x, double y, double z, ItemStack item) {
        this(type, level);
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.setOnGround(true);
        this.delayBeforeCanPickup = 10;
        this.setEntityItemStack(item);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected boolean isFlapping() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    public String getItemOwner() {
        return this.owner;
    }

    public void setItemOwner(String owner) {
        this.owner = owner;
    }

    @Override
    public void tick() {
        super.tick();

        // client side: play portal sound
        if (this.level().isClientSide()) {
            if ((this.tickCount & 31) == 0 && this.random.nextInt(3) == 0) {
                this.level().playLocalSound(
                        this.getX() + 0.5D, this.getY() + 0.5D, this.getZ() + 0.5D,
                        SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                        0.5F, this.random.nextFloat() * 0.4F + 0.8F, false);
            }
        }
        // server side
        else {
            // despawn ship mob egg after configured ticks
            int lifetime = ConfigHandler.despawnEgg();
            if (lifetime >= 0 && this.tickCount > lifetime) {
                ItemStack stack = this.getEntityItem();
                if (!stack.hasTag()) {
                    this.discard();
                }
            }
        }

        // check item validity
        ItemStack item = this.getEntityItem();
        if (item.isEmpty()) {
            this.discard();
            return;
        }

        // slow down motion
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.multiply(0.5D, 0.5D, 0.5D));

        // pick delay
        if (this.delayBeforeCanPickup > 0) {
            --this.delayBeforeCanPickup;
        }
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean isInvisible() {
        return false;
    }

    @Override
    public void setInvisible(boolean invisible) {
        // prevent invisibility
    }

    public ItemStack getEntityItem() {
        return this.entityData.get(DATA_ITEM);
    }

    public void setEntityItemStack(@Nullable ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack != null ? stack : ItemStack.EMPTY);
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide() && this.isAlive()) {
            // check delay
            if (this.delayBeforeCanPickup > 0)
                return;

            ItemStack itemstack = this.getEntityItem();
            if (itemstack.isEmpty())
                return;

            if (SafariNetItem.isFilled(itemstack)) {
                if (!SafariNetItem.isStoredOwner(itemstack, player)
                        || !SafariNetItem.tryStoreFilledNet(player, itemstack)) return;
            }
            else if (SoulTalismanItem.isFilled(itemstack)) {
                if (!SoulTalismanItem.isStoredOwner(itemstack, player)
                        || !SoulTalismanItem.tryStoreFilledTalisman(player, itemstack)) return;
            }

            // is OP
            else if (ServerDataManager.checkOP(player)) {
                storePickedStack(player, itemstack);
            }
            // not OP
            else {
                // ship spawn egg = owner pick only
                if (itemstack.getItem() == ModItems.SHIP_SPAWN_EGG.get()
                        || itemstack.getItem() == ModItems.GENERIC_SHIP_SPAWN_EGG.get()) {
                    if (!SavedEggOwnership.mayPickUp(itemstack, player)) return;
                    storePickedStack(player, itemstack);
                }
                // not ship spawn egg
                else {
                    storePickedStack(player, itemstack);
                }
            }

            // play pick sound
            if (!this.isSilent()) {
                this.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                        0.2F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }

            if (itemstack.isEmpty() || itemstack.getCount() <= 0) {
                this.discard();
            }
        }
    }

    private static void storePickedStack(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild || (!stack.is(ModItems.SHIP_SPAWN_EGG.get())
                && !stack.is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()))) {
            player.getInventory().add(stack);
            return;
        }

        // Inventory.add discards a Creative player's remainder when no room is left.
        var inventory = player.getInventory();
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) slot = inventory.getFreeSlot();
            if (slot < 0) return;
            ItemStack current = inventory.getItem(slot);
            int limit = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
            int moved = Math.min(stack.getCount(), limit - (current.isEmpty() ? 0 : current.getCount()));
            if (moved <= 0) return;
            if (current.isEmpty()) {
                inventory.setItem(slot, stack.copyWithCount(moved));
            } else {
                current.grow(moved);
            }
            inventory.getItem(slot).setPopTime(5);
            stack.shrink(moved);
            inventory.setChanged();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        CompoundTag itemtag = nbt.getCompound("Item");
        this.setEntityItemStack(ItemStack.of(itemtag));

        ItemStack item = this.getEntityItem();
        if (item.isEmpty()) {
            this.discard();
        }

        if (nbt.contains("PickupDelay")) {
            this.delayBeforeCanPickup = nbt.getShort("PickupDelay");
        }

        if (nbt.contains("Owner")) {
            this.owner = nbt.getString("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        ItemStack item = this.getEntityItem();
        if (!item.isEmpty()) {
            nbt.put("Item", item.save(new CompoundTag()));
        }

        if (this.owner != null) {
            nbt.putString("Owner", this.owner);
        }

        nbt.putShort("PickupDelay", (short) this.delayBeforeCanPickup);
    }

    @Override
    public void push(Entity entity) {
        // can't be pushed
    }
}
