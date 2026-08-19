package com.lulan.shincolle.client.gui.inventory;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModMenuTypes;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CShipyardStockPacket;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Arrays;

/**
 * Container/Menu for Small Shipyard block.
 * 5 unified material/fuel slots + 1 output slot = 6 shipyard slots + player
 * inventory.
 * <p>
 * ContainerData layout (synced server->client as scaled values):
 * 0: fuelPercent (0-1000, representing 0%-100.0% fuel)
 * 1: buildPercent (0-1000, representing 0%-100.0% progress)
 * 2: buildType (0=none, 1=ship, 2=equip, 3=ship_loop, 4=equip_loop)
 * 3: buildTimeSeconds (remaining seconds)
 * 4-7: matsBuild[0-3]
 * 8: selectMat
 * Material stock uses S2CShipyardStockPacket to preserve full int values.
 */
public class ContainerSmallShipyard extends AbstractContainerMenu {

    public static final int INPUT_SLOT_COUNT = 5;
    public static final int OUTPUT_SLOT = 5;
    public static final int SHIPYARD_SLOT_COUNT = 6;

    public static final int DATA_FUEL_PERCENT = 0;
    public static final int DATA_BUILD_PERCENT = 1;
    public static final int DATA_BUILD_TYPE = 2;
    public static final int DATA_BUILD_TIME = 3;
    public static final int DATA_BUILD_BASE = 4;
    public static final int DATA_SELECT_MAT = 8;
    public static final int DATA_COUNT = 9;

    private final TileEntitySmallShipyard tile;
    private final ContainerData data;
    private final Player player;
    private final int[] syncedMatsStock = new int[4];
    private final int[] lastSentMatsStock = {
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

    /**
     * Client-side constructor (from network) - uses SimpleContainerData for
     * receiving synced values
     */
    public ContainerSmallShipyard(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTileFromBuf(playerInv.player, buf), new SimpleContainerData(DATA_COUNT));
    }

    /**
     * Server-side constructor - creates ContainerData that reads from tile entity
     */
    public ContainerSmallShipyard(int containerId, Inventory playerInv, TileEntitySmallShipyard tile) {
        this(containerId, playerInv, tile, createTileData(tile));
    }

    private ContainerSmallShipyard(int containerId, Inventory playerInv, TileEntitySmallShipyard tile,
                                   ContainerData data) {
        super(ModMenuTypes.SMALL_SHIPYARD.get(), containerId);
        this.tile = tile;
        this.data = data;
        this.player = playerInv.player;

        IItemHandler handler = tile != null ? tile.getInventory() : new ItemStackHandler(SHIPYARD_SLOT_COUNT);

        // Existing slot positions are preserved; all five are now unified inputs.
        addSlot(new SlotSmallShipyard(handler, 0, 33, 29, false, tile));
        addSlot(new SlotSmallShipyard(handler, 1, 53, 29, false, tile));
        addSlot(new SlotSmallShipyard(handler, 2, 73, 29, false, tile));
        addSlot(new SlotSmallShipyard(handler, 3, 93, 29, false, tile));
        addSlot(new SlotSmallShipyard(handler, 4, 8, 53, false, tile));

        // Output slot (5): no item placement
        addSlot(new SlotSmallShipyard(handler, OUTPUT_SLOT, 134, 44, true, tile));

        // Player inventory
        addPlayerInventory(playerInv, 8, 87);

        addDataSlots(data);
    }

    private static ContainerData createTileData(TileEntitySmallShipyard tile) {
        if (tile == null)
            return new SimpleContainerData(DATA_COUNT);
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_FUEL_PERCENT -> tile.getPowerMax() > 0
                            ? (int) ((long) tile.getPowerRemained() * 1000 / tile.getPowerMax())
                            : 0;
                    case DATA_BUILD_PERCENT -> tile.getPowerGoal() > 0
                            ? (int) ((long) tile.getPowerConsumed() * 1000 / tile.getPowerGoal())
                            : 0;
                    case DATA_BUILD_TYPE -> tile.getBuildType();
                    case DATA_BUILD_TIME -> {
                        int goal = tile.getPowerGoal();
                        int consumed = tile.getPowerConsumed();
                        if (goal <= 0)
                            yield 0;
                        int buildSpeed = (int) ConfigHandler.tileShipyardSmall[1];
                        if (buildSpeed <= 0)
                            yield 0;
                        yield Math.max(0, (goal - consumed) / buildSpeed / 20);
                    }
                    case DATA_BUILD_BASE, DATA_BUILD_BASE + 1,
                            DATA_BUILD_BASE + 2, DATA_BUILD_BASE + 3 ->
                            tile.getMatBuild(index - DATA_BUILD_BASE);
                    case DATA_SELECT_MAT -> tile.getSelectMat();
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

    private static TileEntitySmallShipyard getTileFromBuf(Player player, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof TileEntitySmallShipyard t ? t : null;
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
        if (tile == null)
            return false;
        BlockPos pos = tile.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index < SHIPYARD_SLOT_COUNT) {
                if (!this.moveItemStackTo(slotStack, SHIPYARD_SLOT_COUNT, SHIPYARD_SLOT_COUNT + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, 0, OUTPUT_SLOT, false)) {
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

    public TileEntitySmallShipyard getTile() {
        return tile;
    }

    public int getFuelPercent() {
        return data.get(DATA_FUEL_PERCENT);
    }

    public int getBuildPercent() {
        return data.get(DATA_BUILD_PERCENT);
    }

    public int getBuildType() {
        return data.get(DATA_BUILD_TYPE);
    }

    public int getBuildTimeSeconds() {
        return data.get(DATA_BUILD_TIME);
    }

    public int getMatStock(int index) {
        return index >= 0 && index < syncedMatsStock.length ? syncedMatsStock[index] : 0;
    }

    public int getMatBuild(int index) {
        return index >= 0 && index < 4 ? data.get(DATA_BUILD_BASE + index) : 0;
    }

    public int getSelectMat() {
        return data.get(DATA_SELECT_MAT);
    }

    public void setMatStockFromServer(int[] stocks) {
        if (stocks == null || stocks.length != syncedMatsStock.length) {
            return;
        }
        System.arraycopy(stocks, 0, syncedMatsStock, 0, syncedMatsStock.length);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer) || tile == null) {
            return;
        }
        int[] currentStock = new int[4];
        for (int i = 0; i < currentStock.length; i++) {
            currentStock[i] = tile.getMatStock(i);
        }
        if (!Arrays.equals(currentStock, lastSentMatsStock)) {
            ModNetworking.sendToPlayer(new S2CShipyardStockPacket(containerId, currentStock), serverPlayer);
            System.arraycopy(currentStock, 0, lastSentMatsStock, 0, currentStock.length);
        }
    }
}
