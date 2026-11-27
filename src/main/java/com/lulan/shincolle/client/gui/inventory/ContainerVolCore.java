package com.lulan.shincolle.client.gui.inventory;

import com.lulan.shincolle.init.ModMenuTypes;
import com.lulan.shincolle.tileentity.TileEntityVolCore;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Container/Menu for the Volcano Core block.
 * 9 fuel input slots (3x3 grid) + player inventory.
 * <p>
 * ContainerData layout:
 * 0-1: remaining fuel (low/high 16 bits)
 * 2-3: maximum fuel (low/high 16 bits)
 * 4: btnActive (0/1)
 * 5: canWork (0/1)
 */
public class ContainerVolCore extends AbstractContainerMenu {

    public static final int INPUT_SLOT_COUNT = 9;

    public static final int DATA_FUEL_LOW = 0;
    public static final int DATA_FUEL_HIGH = 1;
    public static final int DATA_MAX_LOW = 2;
    public static final int DATA_MAX_HIGH = 3;
    public static final int DATA_BTN_ACTIVE = 4;
    public static final int DATA_CAN_WORK = 5;
    public static final int DATA_COUNT = 6;

    private final TileEntityVolCore tile;
    private final ContainerData data;

    /**
     * Client-side constructor
     */
    public ContainerVolCore(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTileFromBuf(playerInv.player, buf), new SimpleContainerData(DATA_COUNT));
    }

    /**
     * Server-side constructor
     */
    public ContainerVolCore(int containerId, Inventory playerInv, TileEntityVolCore tile) {
        this(containerId, playerInv, tile, createTileData(tile));
    }

    private ContainerVolCore(int containerId, Inventory playerInv, TileEntityVolCore tile, ContainerData data) {
        super(ModMenuTypes.VOL_CORE.get(), containerId);
        this.tile = tile;
        this.data = data;

        IItemHandler handler = tile != null ? tile.getInventory() : new ItemStackHandler(INPUT_SLOT_COUNT);

        // Input slots (0-8): 3x3 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotVolCore(handler, row * 3 + col,
                        62 + col * 18, 17 + row * 18));
            }
        }

        // Player inventory
        addPlayerInventory(playerInv, 8, 84);

        addDataSlots(data);
    }

    private static ContainerData createTileData(TileEntityVolCore tile) {
        if (tile == null)
            return new SimpleContainerData(DATA_COUNT);
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_FUEL_LOW -> tile.getPowerRemained() & 0xFFFF;
                    case DATA_FUEL_HIGH -> tile.getPowerRemained() >>> 16;
                    case DATA_MAX_LOW -> tile.getPowerMax() & 0xFFFF;
                    case DATA_MAX_HIGH -> tile.getPowerMax() >>> 16;
                    case DATA_BTN_ACTIVE -> tile.isBtnActive() ? 1 : 0;
                    case DATA_CAN_WORK -> tile.isWorking() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private static TileEntityVolCore getTileFromBuf(Player player, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof TileEntityVolCore t ? t : null;
    }

    protected void addPlayerInventory(Inventory inv, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, 9 + row * 9 + col, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, x + col * 18, y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (tile == null || tile.isRemoved() || tile.getLevel() == null)
            return false;
        BlockPos pos = tile.getBlockPos();
        return tile.getLevel().getBlockEntity(pos) == tile && tile.canUse(player)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index < INPUT_SLOT_COUNT) {
                if (!this.moveItemStackTo(slotStack, INPUT_SLOT_COUNT, INPUT_SLOT_COUNT + 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < INPUT_SLOT_COUNT + 27) {
                if (!this.moveItemStackTo(slotStack, 0, INPUT_SLOT_COUNT, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean moved = this.moveItemStackTo(slotStack, 0, INPUT_SLOT_COUNT, false);
                if (!slotStack.isEmpty()) {
                    moved = this.moveItemStackTo(slotStack, INPUT_SLOT_COUNT, INPUT_SLOT_COUNT + 27, false) || moved;
                }
                if (!moved) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    public TileEntityVolCore getTile() {
        return tile;
    }

    public int getFuelPercent() {
        int maximum = getPowerMax();
        return maximum > 0 ? (int) ((long) getPowerRemained() * 1000 / maximum) : 0;
    }

    public int getPowerRemained() {
        return (data.get(DATA_FUEL_LOW) & 0xFFFF) | ((data.get(DATA_FUEL_HIGH) & 0xFFFF) << 16);
    }

    public int getPowerMax() {
        return (data.get(DATA_MAX_LOW) & 0xFFFF) | ((data.get(DATA_MAX_HIGH) & 0xFFFF) << 16);
    }

    public int getPowerRemainingScaled(int pixels) {
        int maximum = getPowerMax();
        return maximum > 0 ? (int) ((long) getPowerRemained() * pixels / maximum) : 0;
    }

    public boolean isBtnActive() {
        return data.get(DATA_BTN_ACTIVE) != 0;
    }

    public boolean isWorking() {
        return data.get(DATA_CAN_WORK) != 0;
    }
}
