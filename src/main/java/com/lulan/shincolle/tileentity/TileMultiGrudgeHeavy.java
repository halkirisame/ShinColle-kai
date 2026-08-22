package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.client.gui.inventory.ContainerLargeShipyard;
import com.lulan.shincolle.crafting.LargeRecipes;
import com.lulan.shincolle.crafting.ShipCalc;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlockEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.IShipResourceItem;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.utility.LogHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Grudge Heavy multiblock structure (Large Shipyard).
 * Handles large ship/equipment building with 4 material types stored as counts.
 * <p>
 * Slot layout (10 slots):
 * 0: Output
 * 1-9: Unified material/fuel input
 * <p>
 * Material storage: Tracks 4 material stock counts internally (grudge,
 * abyssium, ammo, polymetal)
 */
public class TileMultiGrudgeHeavy extends BasicTileInventory implements MenuProvider, ITileFurnace {

    public static final int SLOTS_NUM = 10;
    public static final int SLOT_OUTPUT = 0;
    public static final int SLOT_INPUT_START = 1;
    public static final int SLOT_INPUT_END = 9;
    private static final int POWER_INSTANT = 57600;
    private static final int LAVA_BUCKET_MB = 1000;
    private static final int LAVA_BUCKET_BURN_TIME = 20000;
    private static final int FLUID_TANK_CAPACITY = 16000;
    private static final int MAX_STOCK = 1000000;
    // Config values
    private static int POWER_MAX;
    private static int BUILD_SPEED;
    private static float FUEL_MAGN;

    static {
        reloadConfig();
    }

    /**
     * Build type: 0=none, 1=ship, 2=equip, 3=ship_loop, 4=equip_loop
     */
    private int buildType = 0;
    /**
     * Inventory mode: 0=recycle/add materials, 1=release/extract materials
     */
    private int invMode = 0;
    /**
     * Material selection for output (0-3)
     */
    private int selectMat = 0;
    /**
     * Core block position for multiblock structure
     */
    private BlockPos corePos = BlockPos.ZERO;
    /**
     * Whether this tile has a valid core position
     */
    private boolean hasCorePos = false;
    /**
     * Power consumed in current build cycle
     */
    private int powerConsumed = 0;
    /**
     * Remaining fuel power in storage
     */
    private int powerRemained = 0;
    /**
     * Power goal for current build
     */
    private int powerGoal = 0;
    /**
     * Whether currently active
     */
    private boolean isActive = false;
    /**
     * Sync timer
     */
    private int syncTime = 0;
    /**
     * Material stock counts: [grudge, abyssium, ammo, polymetal]
     */
    private int[] matsStock = new int[4];
    /**
     * Material build requirements: [grudge, abyssium, ammo, polymetal]
     */
    private int[] matsBuild = new int[4];
    private final FluidTank fuelTank = new FluidTank(FLUID_TANK_CAPACITY,
            stack -> stack.getFluid() == Fluids.LAVA) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final LazyOptional<IFluidHandler> fuelTankCapability = LazyOptional.of(() -> fuelTank);
    private final LazyOptional<IItemHandler> itemHandlerCapability =
            LazyOptional.of(ShipyardAutomationItemHandler::new);

    public TileMultiGrudgeHeavy(BlockPos pos, BlockState state) {
        this(ModBlockEntities.GRUDGE_HEAVY_MULTI.get(), pos, state);
    }

    public TileMultiGrudgeHeavy(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, SLOTS_NUM);
    }

    public static void reloadConfig() {
        double[] cfg = ConfigHandler.tileShipyardLarge;
        POWER_MAX = (int) cfg[0];
        BUILD_SPEED = (int) cfg[1];
        FUEL_MAGN = (float) cfg[2];
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileMultiGrudgeHeavy tile) {
        tile.tickServer();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.shincolle.large_shipyard");
    }

    // ==================== ITileFurnace ====================

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerLargeShipyard(containerId, playerInv, this);
    }

    @Override
    public int getPowerConsumed() {
        return powerConsumed;
    }

    @Override
    public void setPowerConsumed(int v) {
        this.powerConsumed = v;
    }

    @Override
    public int getPowerGoal() {
        return powerGoal;
    }

    @Override
    public void setPowerGoal(int v) {
        this.powerGoal = v;
    }

    @Override
    public int getPowerRemained() {
        return powerRemained;
    }

    @Override
    public void setPowerRemained(int v) {
        this.powerRemained = v;
    }

    @Override
    public int getPowerMax() {
        return POWER_MAX;
    }

    @Override
    public void setPowerMax(int v) {
    }

    // ==================== Material Management ====================

    @Override
    public float getFuelMagni() {
        return FUEL_MAGN;
    }

    public int getBuildType() {
        return buildType;
    }

    public void setBuildType(int type) {
        int normalizedType = Math.max(0, Math.min(type, 4));
        if (this.buildType != normalizedType) {
            this.powerConsumed = 0;
            this.powerGoal = 0;
        }
        this.buildType = normalizedType;
        setChanged();
    }

    public int getInvMode() {
        return invMode;
    }

    public void setInvMode(int mode) {
        this.invMode = mode;
        setChanged();
    }

    public int getSelectMat() {
        return selectMat;
    }

    public void setSelectMat(int mat) {
        this.selectMat = mat;
        setChanged();
    }

    public int getMatStock(int index) {
        return index >= 0 && index < 4 ? matsStock[index] : 0;
    }

    public void setMatStock(int index, int value) {
        if (index >= 0 && index < 4) {
            matsStock[index] = Math.max(0, Math.min(value, MAX_STOCK));
            setChanged();
        }
    }

    public int getMatBuild(int index) {
        return index >= 0 && index < 4 ? matsBuild[index] : 0;
    }

    // ==================== Multiblock ====================

    public void setMatBuild(int index, int value) {
        if (index >= 0 && index < 4) {
            matsBuild[index] = Math.max(0, Math.min(value, LargeRecipes.MAX_MATERIAL));
            setChanged();
        }
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    public void setCorePos(BlockPos pos) {
        this.corePos = pos;
        this.hasCorePos = true;
        setChanged();
    }

    public boolean hasCorePos() {
        return hasCorePos;
    }

    public void resetCorePos() {
        this.corePos = BlockPos.ZERO;
        this.hasCorePos = false;
        setChanged();
    }

    // ==================== Build Logic ====================

    @Override
    public AABB getRenderBoundingBox() {
        // [PORT] 1.10.2 -> 1.20.1: keep large-shipyard model visible when the core
        // block is just outside frustum by expanding BE render bounds to structure
        // size.
        BlockPos pos = this.getBlockPos();
        return new AABB(pos.offset(-2, -3, -2), pos.offset(3, 3, 3));
    }

    public boolean isBuilding() {
        return canBuild() && (hasRemainedPower() || hasInstantConstructionMaterial());
    }

    public boolean hasRemainedPower() {
        return powerRemained >= BUILD_SPEED;
    }

    public boolean canBuild() {
        if (buildType == 0)
            return false;
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (!output.isEmpty() || powerGoal <= 0
                || !LargeRecipes.isValidInput(matsBuild[0], matsBuild[1], matsBuild[2], matsBuild[3]))
            return false;

        // Verify stock is sufficient for build requirements
        for (int i = 0; i < 4; i++) {
            if (matsStock[i] < matsBuild[i])
                return false;
        }
        return true;
    }

    public int getPowerRemainingScaled(int pixels) {
        if (POWER_MAX <= 0)
            return 0;
        return powerRemained * pixels / POWER_MAX;
    }

    public int getBuildProgressScaled(int pixels) {
        if (powerGoal <= 0)
            return 0;
        return powerConsumed * pixels / powerGoal;
    }

    public String getBuildTimeString() {
        if (powerGoal <= 0 || BUILD_SPEED <= 0)
            return "0:00";
        int remainTicks = (powerGoal - powerConsumed) / BUILD_SPEED;
        int seconds = remainTicks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Consume one lava bucket's worth of fluid with the same power value as a
     * lava bucket placed in the existing fuel inventory slot.
     */
    private void decrFluidFuel() {
        if (powerRemained >= POWER_MAX || fuelTank.getFluidAmount() < LAVA_BUCKET_MB) {
            return;
        }

        int fuelValue = (int) (LAVA_BUCKET_BURN_TIME * FUEL_MAGN);
        if (fuelValue > 0 && powerRemained + fuelValue <= POWER_MAX) {
            fuelTank.drain(LAVA_BUCKET_MB, IFluidHandler.FluidAction.EXECUTE);
            powerRemained += fuelValue;
            setChanged();
        }
    }

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < SLOT_INPUT_START || slot > SLOT_INPUT_END) {
            return false;
        }
        return stack.getItem() instanceof ShipSpawnEgg
                || stack.getItem() instanceof IShipResourceItem
                || stack.is(ModItems.INSTANT_CON_MAT.get())
                || ForgeHooks.getBurnTime(stack, null) > 0;
    }

    private boolean hasInstantConstructionMaterial() {
        return findInstantConstructionSlot() >= 0;
    }

    private int findInstantConstructionSlot() {
        for (int slot = SLOT_INPUT_START; slot <= SLOT_INPUT_END; slot++) {
            if (inventory.getStackInSlot(slot).is(ModItems.INSTANT_CON_MAT.get())) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Recycle input items into material stock
     */
    private void processInputSlots() {
        for (int slot = SLOT_INPUT_START; slot <= SLOT_INPUT_END; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
                continue;

            if (stack.getItem() instanceof ShipSpawnEgg) {
                if (invMode == 0 && recycleShipSpawnEgg(stack)) {
                    stack.shrink(1);
                    setChanged();
                }
            } else if (stack.getItem() instanceof IShipResourceItem resource) {
                if (invMode == 0) {
                    int[] addMats = resource.getResourceValue(stack);
                    if (ConfigHandler.easyMode()) {
                        for (int k = 0; k < 4; k++) addMats[k] *= 10;
                    }
                    boolean canAdd = true;
                    for (int k = 0; k < 4; k++) {
                        if (addMats[k] < 0 || matsStock[k] > MAX_STOCK - addMats[k]) {
                            canAdd = false;
                            break;
                        }
                    }
                    if (!canAdd) continue;
                    for (int k = 0; k < 4; k++) {
                        matsStock[k] += addMats[k];
                    }
                    stack.shrink(1);
                    setChanged();
                }
            } else if (!stack.is(ModItems.INSTANT_CON_MAT.get())) {
                consumeFuelItem(slot, stack);
            }
        }
    }

    private void consumeFuelItem(int slot, ItemStack fuelStack) {
        if (powerRemained >= POWER_MAX) {
            return;
        }
        int burnTime = ForgeHooks.getBurnTime(fuelStack, null);
        int fuelValue = burnTime > 0 ? (int) (burnTime * FUEL_MAGN) : 0;
        if (fuelValue <= 0 || powerRemained + fuelValue > POWER_MAX) {
            return;
        }

        ItemStack containerStack = fuelStack.getCraftingRemainingItem();
        if (!containerStack.isEmpty() && fuelStack.getCount() > 1) {
            return;
        }

        fuelStack.shrink(1);
        powerRemained += fuelValue;
        if (fuelStack.isEmpty()) {
            inventory.setStackInSlot(slot, containerStack.isEmpty() ? ItemStack.EMPTY : containerStack.copy());
        }
        setChanged();
    }

    // ==================== Tick Logic ====================

    private void buildComplete() {
        boolean buildShip = (buildType == 1 || buildType == 3);
        assert level != null;
        ItemStack result = LargeRecipes.calculateResult(
                matsBuild[0], matsBuild[1], matsBuild[2], matsBuild[3],
                buildShip, level.random);

        if (result.isEmpty()) {
            // Do not consume the selected stock if a recipe implementation
            // declines to create an output after validation.
            powerConsumed = 0;
            powerGoal = 0;
            if (buildType == 1 || buildType == 2) {
                buildType = 0;
            }
            setChanged();
            return;
        }
        inventory.setStackInSlot(SLOT_OUTPUT, result);
        LogHelper.debug("LARGE SHIPYARD: build complete, result=" + result);

        // Deduct consumed materials from stock
        for (int i = 0; i < 4; i++) {
            matsStock[i] -= matsBuild[i];
            if (matsStock[i] < 0)
                matsStock[i] = 0;
        }

        powerConsumed = 0;
        powerGoal = 0;

        if (buildType == 1 || buildType == 2) {
            // Single build: reset build type and requirements
            buildType = 0;
            matsBuild = new int[4];
        } else if (buildType == 3 || buildType == 4) {
            // Loop build: check if stock is sufficient for another cycle
            boolean canLoop = true;
            for (int i = 0; i < 4; i++) {
                if (matsStock[i] < matsBuild[i]) {
                    canLoop = false;
                    break;
                }
            }
            if (!canLoop) {
                // Insufficient materials for next loop cycle, stop building
                buildType = 0;
                matsBuild = new int[4];
            }
        }

        setChanged();
    }

    private void tickServer() {
        boolean sendUpdate = false;
        syncTime++;

        if (buildType != 0 && LargeRecipes.isValidInput(matsBuild[0], matsBuild[1], matsBuild[2], matsBuild[3])) {
            int totalMats = matsBuild[0] + matsBuild[1] + matsBuild[2] + matsBuild[3];
            powerGoal = LargeRecipes.calculateFuelCost(totalMats);
        } else {
            powerGoal = 0;
        }

        processInputSlots();
        decrFluidFuel();

        if (canBuild()) {
            int instantSlot = findInstantConstructionSlot();
            if (instantSlot >= 0) {
                ItemStack instantStack = inventory.getStackInSlot(instantSlot);
                instantStack.shrink(1);
                if (instantStack.isEmpty()) {
                    inventory.setStackInSlot(instantSlot, ItemStack.EMPTY);
                }
                powerConsumed += POWER_INSTANT;
            } else if (powerRemained >= BUILD_SPEED) {
                powerRemained -= BUILD_SPEED;
                powerConsumed += BUILD_SPEED;
            }

            if (powerGoal > 0 && powerConsumed >= powerGoal) {
                buildComplete();
                sendUpdate = true;
            }
        } else if (!canBuild()) {
            powerConsumed = 0;
        }

        boolean nowActive = isBuilding();
        if (isActive != nowActive) {
            isActive = nowActive;
            sendUpdate = true;
        }

        if (sendUpdate || syncTime > 12000) {
            syncTime = 0;
            setChanged();
        }
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildType", buildType);
        tag.putInt("InvMode", invMode);
        tag.putInt("SelectMat", selectMat);
        tag.putInt("PowerConsumed", powerConsumed);
        tag.putInt("PowerRemained", powerRemained);
        tag.putInt("PowerGoal", powerGoal);
        tag.putBoolean("Active", isActive);
        tag.putIntArray("MatsStock", matsStock);
        tag.putIntArray("MatsBuild", matsBuild);
        tag.put("FuelFluid", fuelTank.writeToNBT(new CompoundTag()));
        if (hasCorePos) {
            tag.putInt("CoreX", corePos.getX());
            tag.putInt("CoreY", corePos.getY());
            tag.putInt("CoreZ", corePos.getZ());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        buildType = tag.getInt("BuildType");
        invMode = tag.getInt("InvMode");
        selectMat = tag.getInt("SelectMat");
        powerConsumed = tag.getInt("PowerConsumed");
        powerRemained = tag.getInt("PowerRemained");
        powerGoal = tag.getInt("PowerGoal");
        isActive = tag.getBoolean("Active");
        if (tag.contains("MatsStock")) {
            int[] arr = tag.getIntArray("MatsStock");
            if (arr.length == 4)
                matsStock = arr;
        }
        if (tag.contains("MatsBuild")) {
            int[] arr = tag.getIntArray("MatsBuild");
            if (arr.length == 4)
                matsBuild = arr;
        }
        if (tag.contains("FuelFluid")) {
            fuelTank.readFromNBT(tag.getCompound("FuelFluid"));
        }
        if (tag.contains("CoreX")) {
            corePos = new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ"));
            hasCorePos = true;
        }
    }

    /**
     * Converts a ship spawn egg into the resource items yielded by the legacy
     * ship disassembly recipe, then adds their values to this shipyard's stock.
     */
    private boolean recycleShipSpawnEgg(ItemStack stack) {
        ItemStack[] recycledItems = ShipCalc.getKaitaiItems(ShipSpawnEgg.getShipClass(stack));
        int[] totalMats = new int[4];

        for (ItemStack recycled : recycledItems) {
            if (recycled.isEmpty() || !(recycled.getItem() instanceof IShipResourceItem resource)) {
                return false;
            }
            int[] resourceValue = resource.getResourceValue(recycled);
            for (int k = 0; k < 4; k++) {
                totalMats[k] += resourceValue[k] * recycled.getCount();
            }
        }

        if (ConfigHandler.easyMode()) {
            for (int k = 0; k < 4; k++) {
                totalMats[k] *= 10;
            }
        }
        for (int k = 0; k < 4; k++) {
            if (totalMats[k] < 0 || matsStock[k] > MAX_STOCK - totalMats[k]) {
                return false;
            }
        }
        for (int k = 0; k < 4; k++) {
            matsStock[k] += totalMats[k];
        }
        return true;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCapability.cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return fuelTankCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCapability.invalidate();
        fuelTankCapability.invalidate();
    }

    private class ShipyardAutomationItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValidForSlot(slot, stack)) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_OUTPUT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isItemValidForSlot(slot, stack);
        }
    }
}
