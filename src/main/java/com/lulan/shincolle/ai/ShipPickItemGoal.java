package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.PickItemMovePlanner;
import com.lulan.shincolle.ai.domain.movement.PlannedMove;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.StuckStage;
import com.lulan.shincolle.ai.domain.movement.StuckState;
import com.lulan.shincolle.ai.domain.ShipAiCompatibilityRules;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Item pickup goal.
 * Ported from EntityAIShipPickItem (setMutexBits: 7)
 */
public class ShipPickItemGoal extends Goal {

    private final BasicEntityShip ship;
    private final float pickRangeBase;
    private Entity entItem;
    private int pickDelay;
    private int pickDelayMax;
    private float pickRange;
    private int nextItemScanTick;
    /** NEW: the scan time, in place of {@link #nextItemScanTick}, and the item given up on. */
    private MovementState.PickItem move = PickItemMovePlanner.initial();
    /** NEW: whether the ship is getting anywhere on its way to the item. */
    private StuckState stuck = StuckState.NONE;

    public ShipPickItemGoal(BasicEntityShip ship, float pickRangeBase) {
        this.ship = ship;
        this.pickRangeBase = pickRangeBase;
        this.pickDelay = 0;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));

        updateShipParms();
    }

    @Override
    public boolean canUse() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return false;
        AABB box = this.ship.getBoundingBox().inflate(this.pickRange, this.pickRange * 0.5F + 1.0F, this.pickRange * 1.2F);
        List<ItemEntity> items = this.ship.level().getEntitiesOfClass(ItemEntity.class, box);
        if (ShipMovementGate.active()) {
            items = items.stream().filter(this::choosable).toList();
        }

        if (items.isEmpty()) {
            return false;
        }

        // sitting, riding, disabled, no fuel, crane state active: skip
        // check Flag PickItem
        if (ShipMovementGate.active()) {
            if (!ShipMovementGate.allows(this.ship, MovementActivity.PICK_ITEM)) {
                return false;
            }
        } else if (this.ship.isPassenger() || this.ship.isOrderedToSit() ||
                !this.ship.getStateFlag(ID.F.PickItem) ||
                ShipMovementGate.craneBusy(this.ship) ||
                this.ship.fishHook != null ||
                this.ship.getStateFlag(ID.F.NoFuel)) {
            return false;
        }

        // check inventory space
        return this.ship.getCapaShipInventory().getFirstSlotForItem() >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return false;
        return canUse();
    }

    @Override
    public void start() {
        if (ShipMovementGate.active()) {
            this.move = PickItemMovePlanner.start(this.move, this.ship.tickCount);
            this.stuck = StuckState.NONE;
            this.ship.setPickingItem(true);
            return;
        }
        this.nextItemScanTick = this.ship.tickCount;
    }

    @Override
    public void stop() {
        this.ship.setPickingItem(false);
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return;
        this.pickDelay--;

        // check every 16 ticks
        int now = this.ship.tickCount;
        boolean newAuthority = ShipMovementGate.active();
        boolean scan;
        boolean recovered = false;
        if (newAuthority) {
            recovered = this.recoverNew(now);
            scan = PickItemMovePlanner.scanDue(this.move, now);
            if (scan) this.move = PickItemMovePlanner.scanned(this.move, now);
        } else {
            scan = now >= this.nextItemScanTick;
            if (scan) this.nextItemScanTick = now + 16;
        }
        if (scan) {
            updateShipParms();

            // find nearby items
            this.entItem = getNearbyItemEntity();

            if (this.entItem != null && this.entItem.isAlive()) {
                if (newAuthority) {
                    // a recovery path this tick stands in for the regular one
                    if (!recovered) ShipMovementExecutor.run(this.ship,
                            PickItemMovePlanner.toItem(ShipCommandStateAdapter.handle(this.entItem)));
                } else {
                    ship.getNavigation().moveTo(this.entItem, 1.0D);
                }
            }
        }

        // pick up nearby item
        if (this.pickDelay <= 0 && this.entItem != null) {
            this.pickDelay = this.pickDelayMax;

            // within 3 blocks
            if (this.ship.distanceToSqr(this.entItem) < 9.0D) {
                if (this.entItem instanceof ItemEntity itemEntity) {
                    ItemStack stack = itemEntity.getItem();
                    int count = stack.getCount();

                    if (!itemEntity.hasPickUpDelay()) {
                        ItemStack attemptedStack = stack.copy();
                        boolean picked = this.ship.getCapaShipInventory().addItemStackToInventory(stack);
                        LogHelper.diag("DIAG: pickitem ship=" + this.ship + " item=" + attemptedStack
                                + " result=" + (picked ? "picked" : "inventory_full"));

                        if (newAuthority) {
                            this.move = picked ? PickItemMovePlanner.tookItem(this.move)
                                    : PickItemMovePlanner.failedTake(this.move,
                                            ShipCommandStateAdapter.handle(this.entItem), now);
                        }
                        if (picked) {

                            // play pickup sound
                            this.ship.level().playSound(null,
                                    this.ship.getX(), this.ship.getY(), this.ship.getZ(),
                                    SoundEvents.ITEM_PICKUP, this.ship.getSoundSource(),
                                    (float) ConfigHandler.volumeShip(),
                                    ShipAiCompatibilityRules.pickupSoundPitch(
                                            this.ship.getRandom().nextFloat(), this.ship.getRandom().nextFloat()));

                            // play item voice with the original shared-RNG order and cooldown
                            if (ShipAiCompatibilityRules.tryStartPickupVoiceCooldown(
                                    this.ship.getStateTimer(ID.T.SoundTime), this.ship.getRandom()::nextInt,
                                    cooldown -> this.ship.setStateTimer(ID.T.SoundTime, cooldown))) {
                                this.ship.playVoice(this.ship.getCustomSound(6, this.ship),
                                        (float) ConfigHandler.volumeShip(), 1.0F);
                            }

                            // pickup animation
                            this.ship.take(itemEntity, count);

                            // remove item entity if fully consumed
                            if (stack.isEmpty()) {
                                itemEntity.discard();
                                this.entItem = null;
                            }
                        }
                    } else if (newAuthority) {
                        this.move = PickItemMovePlanner.failedTake(this.move,
                                ShipCommandStateAdapter.handle(this.entItem), now);
                    }
                    if (newAuthority && this.entItem != null && PickItemMovePlanner.untakeable(this.move,
                            ShipCommandStateAdapter.handle(this.entItem), now)) {
                        // in reach for a good while and still not takeable: leave it, so the goal can end
                        this.move = PickItemMovePlanner.abandon(this.move,
                                ShipCommandStateAdapter.handle(this.entItem), now);
                        this.entItem = null;
                    }
                }

                if (newAuthority) {
                    ShipMovementExecutor.run(this.ship, PickItemMovePlanner.reached());
                } else {
                    ship.getNavigation().stop();
                }
            }
        }
    }

    /**
     * NEW: observe the way to the item and, when a stuck window has just closed, re-path, try beside
     * it, or give it up. Returns whether a recovery path was requested.
     */
    private boolean recoverNew(int now) {
        boolean hasItem = this.entItem != null && this.entItem.isAlive();
        this.stuck = StuckDetector.observe(this.stuck, PickItemMovePlanner.travelling(hasItem,
                hasItem ? this.ship.distanceToSqr(this.entItem) : 0D), ShipMovementGate.point(this.ship), now);
        if (!hasItem) return false;
        PlannedMove<MovementState.PickItem> planned = PickItemMovePlanner.recover(this.move, this.stuck,
                ShipCommandStateAdapter.handle(this.entItem), ShipMovementGate.point(this.entItem), now);
        this.move = planned.state();
        ShipMovementExecutor.run(this.ship, planned.plan());
        if (this.stuck.due() == StuckStage.GIVE_UP) {
            this.entItem = null;
            this.stuck = StuckState.NONE;
        }
        return planned.plan().hasPath();
    }

    /** NEW: an item given up on is left alone for a while. */
    private boolean choosable(Entity item) {
        return PickItemMovePlanner.choosable(this.move, ShipCommandStateAdapter.handle(item), this.ship.tickCount);
    }

    private ItemEntity getNearbyItemEntity() {
        AABB box = this.ship.getBoundingBox().inflate(this.pickRange, this.pickRange * 0.5F + 1.0F, this.pickRange);
        List<ItemEntity> items = this.ship.level().getEntitiesOfClass(ItemEntity.class, box);
        if (ShipMovementGate.active()) {
            items = new ArrayList<>(items.stream().filter(this::choosable).toList());
        }

        if (!items.isEmpty()) {
            items.sort(Comparator.comparingDouble(this.ship::distanceToSqr));
            return items.get(0);
        }
        return null;
    }

    private void updateShipParms() {
        float speed = this.ship.getAttrs().getAttackSpeed();
        if (speed < 1.0F)
            speed = 1.0F;

        this.pickDelayMax = (int) (10.0F / speed);

        float tempRange = this.pickRangeBase + this.ship.getStateMinor(ID.M.FollowMax);
        this.pickRange = this.pickRangeBase + this.ship.getAttrs().getAttackRange() * 0.5F;
        this.pickRange = Math.min(tempRange, this.pickRange);
    }
}
