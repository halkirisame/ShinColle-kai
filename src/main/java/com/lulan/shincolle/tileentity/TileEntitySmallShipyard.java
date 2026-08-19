package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.block.BlockSmallShipyard;
import com.lulan.shincolle.client.gui.inventory.ContainerSmallShipyard;
import com.lulan.shincolle.crafting.ShipCalc;
import com.lulan.shincolle.crafting.SmallRecipes;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlockEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.IShipResourceItem;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.utility.LogHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Small Shipyard block.
 * Handles ship/equipment building with fuel consumption and progress tracking.
 * <p>
 * Slot layout:
 * 0-4: Unified material/fuel inputs
 * 5: Build output
 */
public class TileEntitySmallShipyard extends BasicTileInventory implements MenuProvider, ITileFurnace {

    public static final int SLOT_COUNT = 6;
    public static final int SLOT_INPUT_START = 0;
    public static final int SLOT_INPUT_END = 4;
    public static final int SLOT_OUTPUT = 5;
    /**
     * Power added per Instant Construction Material
     */
    private static final int POWER_INSTANT = 57600;
    private static final int LAVA_BUCKET_MB = 1000;
    private static final int LAVA_BUCKET_BURN_TIME = 20000;
    private static final int FLUID_TANK_CAPACITY = 16000;
    private static final int MAX_STOCK = 1000000;
    // Config values loaded from ConfigHandler
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
     * Material stock counts: [grudge, abyssium, ammo, polymetal]
     */
    private int[] matsStock = new int[4];
    /**
     * Material amounts selected for the next build.
     */
    private int[] matsBuild = new int[4];
    /**
     * Material selected in the amount controls (0-3).
     */
    private int selectMat = 0;
    /**
     * Whether currently active (for blockstate sync)
     */
    private boolean isActive = false;
    /**
     * Sync timer for periodic updates
     */
    private int syncTime = 0;
    private final FluidTank fuelTank = new FluidTank(FLUID_TANK_CAPACITY,
            stack -> stack.getFluid() == Fluids.LAVA) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final LazyOptional<IFluidHandler> fuelTankCapability = LazyOptional.of(() -> fuelTank);

    public TileEntitySmallShipyard(BlockPos pos, BlockState state) {
        this(ModBlockEntities.SMALL_SHIPYARD.get(), pos, state);
    }

    public TileEntitySmallShipyard(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, SLOT_COUNT);
    }

    public static void reloadConfig() {
        double[] cfg = ConfigHandler.tileShipyardSmall;
        POWER_MAX = (int) cfg[0];
        BUILD_SPEED = (int) cfg[1];
        FUEL_MAGN = (float) cfg[2];
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEntitySmallShipyard tile) {
        tile.tickServer();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.shincolle.small_shipyard");
    }

    // ==================== ITileFurnace ====================

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerSmallShipyard(containerId, playerInv, this);
    }

    @Override
    public int getPowerConsumed() {
        return powerConsumed;
    }

    @Override
    public void setPowerConsumed(int value) {
        this.powerConsumed = value;
    }

    @Override
    public int getPowerGoal() {
        return powerGoal;
    }

    @Override
    public void setPowerGoal(int value) {
        this.powerGoal = value;
    }

    @Override
    public int getPowerRemained() {
        return powerRemained;
    }

    @Override
    public void setPowerRemained(int value) {
        this.powerRemained = value;
    }

    @Override
    public int getPowerMax() {
        return POWER_MAX;
    }

    @Override
    public void setPowerMax(int value) {
        // Max is config-defined, not settable
    }

    // ==================== Build Logic ====================

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
            // A partially completed ship build must not be converted into an
            // equipment build (or vice versa) by changing the GUI mode.
            this.powerConsumed = 0;
            this.powerGoal = 0;
        }
        this.buildType = normalizedType;
        setChanged();
    }

    public int getSelectMat() {
        return selectMat;
    }

    public void setSelectMat(int mat) {
        this.selectMat = Math.max(0, Math.min(mat, 3));
        setChanged();
    }

    public int getMatStock(int index) {
        return index >= 0 && index < matsStock.length ? matsStock[index] : 0;
    }

    public void setMatStock(int index, int value) {
        if (index >= 0 && index < matsStock.length) {
            matsStock[index] = Math.max(0, Math.min(value, MAX_STOCK));
            setChanged();
        }
    }

    public int getMatBuild(int index) {
        return index >= 0 && index < matsBuild.length ? matsBuild[index] : 0;
    }

    public void setMatBuild(int index, int value) {
        if (index >= 0 && index < matsBuild.length) {
            matsBuild[index] = Math.max(0, Math.min(value, SmallRecipes.MAX_MATERIAL));
            setChanged();
        }
    }

    /**
     * Whether the shipyard has fuel and can build
     */
    public boolean isBuilding() {
        return canBuild() && (hasRemainedPower() || hasInstantConstructionMaterial());
    }

    /**
     * Whether there is enough fuel for at least one tick
     */
    public boolean hasRemainedPower() {
        return powerRemained >= BUILD_SPEED;
    }

    /**
     * Whether the build can proceed (has goal and output slot is empty)
     */
    public boolean canBuild() {
        if (buildType == 0)
            return false;

        // Check output slot is empty
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (!output.isEmpty())
            return false;

        if (powerGoal <= 0
                || !SmallRecipes.isValidInput(matsBuild[0], matsBuild[1], matsBuild[2], matsBuild[3])) {
            return false;
        }
        for (int i = 0; i < matsStock.length; i++) {
            if (matsStock[i] < matsBuild[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculate power goal from current material counts
     */
    private void calcPowerGoal() {
        int grudge = matsBuild[0];
        int abyssium = matsBuild[1];
        int ammo = matsBuild[2];
        int polymetal = matsBuild[3];

        if (SmallRecipes.isValidInput(grudge, abyssium, ammo, polymetal)) {
            int totalMats = grudge + abyssium + ammo + polymetal;
            powerGoal = SmallRecipes.calculateFuelCost(totalMats);
        } else {
            powerGoal = 0;
        }
    }

    /**
     * Scan unified input slots in the required priority order: ship egg,
     * resource item, then fuel.
     */
    private void processInputSlots() {
        for (int slot = SLOT_INPUT_START; slot <= SLOT_INPUT_END; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof ShipSpawnEgg) {
                if (recycleShipSpawnEgg(stack)) {
                    stack.shrink(1);
                    setChanged();
                }
            } else if (stack.getItem() instanceof IShipResourceItem resource) {
                if (addResourceItem(resource, stack.getDamageValue())) {
                    stack.shrink(1);
                    setChanged();
                }
            } else if (!stack.is(ModItems.INSTANT_CON_MAT.get())) {
                consumeFuelItem(slot, stack);
            }
        }
    }

    private boolean addResourceItem(IShipResourceItem resource, int damageValue) {
        int[] addMats = resource.getResourceValue(damageValue);
        if (ConfigHandler.easyMode()) {
            for (int i = 0; i < addMats.length; i++) {
                addMats[i] *= 10;
            }
        }
        for (int i = 0; i < matsStock.length; i++) {
            if (addMats[i] < 0 || matsStock[i] > MAX_STOCK - addMats[i]) {
                return false;
            }
        }
        for (int i = 0; i < matsStock.length; i++) {
            matsStock[i] += addMats[i];
        }
        return true;
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
     * Handle build completion - generate output item
     */
    private void buildComplete() {
        int grudge = matsBuild[0];
        int abyssium = matsBuild[1];
        int ammo = matsBuild[2];
        int polymetal = matsBuild[3];

        boolean buildShip = (buildType == 1 || buildType == 3);
        assert level != null;
        ItemStack result = SmallRecipes.calculateResult(grudge, abyssium, ammo, polymetal, buildShip, level.random);

        if (!result.isEmpty()) {
            // Place result in output slot
            inventory.setStackInSlot(SLOT_OUTPUT, result);

            for (int i = 0; i < matsStock.length; i++) {
                matsStock[i] = Math.max(0, matsStock[i] - matsBuild[i]);
            }

            LogHelper.debug("SMALL SHIPYARD: build complete, result=" + result);
        }

        powerConsumed = 0;
        powerGoal = 0;

        // For non-loop builds, reset build type
        if (buildType == 1 || buildType == 2) {
            buildType = 0;
            matsBuild = new int[4];
        } else if (buildType == 3 || buildType == 4) {
            for (int i = 0; i < matsStock.length; i++) {
                if (matsStock[i] < matsBuild[i]) {
                    buildType = 0;
                    matsBuild = new int[4];
                    break;
                }
            }
        }
        // Loop builds (3, 4) continue automatically
    }

    /**
     * Get remaining build time as formatted string
     */
    public String getBuildTimeString() {
        if (powerGoal <= 0 || BUILD_SPEED <= 0)
            return "0:00";
        int remainTicks = (powerGoal - powerConsumed) / BUILD_SPEED;
        int seconds = remainTicks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Get fuel bar scale for GUI rendering
     */
    public int getPowerRemainingScaled(int pixels) {
        if (POWER_MAX <= 0)
            return 0;
        return powerRemained * pixels / POWER_MAX;
    }

    // ==================== Slot Validation ====================

    /**
     * Get build progress scale for GUI rendering
     */
    public int getBuildProgressScaled(int pixels) {
        if (powerGoal <= 0)
            return 0;
        return powerConsumed * pixels / powerGoal;
    }

    // ==================== Tick Logic ====================

    /**
     * Check if the given item is valid for the specified slot
     */
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < SLOT_INPUT_START || slot > SLOT_INPUT_END)
            return false;
        return stack.getItem() instanceof ShipSpawnEgg
                || stack.getItem() instanceof IShipResourceItem
                || stack.is(ModItems.INSTANT_CON_MAT.get())
                || ForgeHooks.getBurnTime(stack, null) > 0;
    }

    private void tickServer() {
        boolean sendUpdate = false;
        syncTime++;

        processInputSlots();

        // Update power goal based on selected material amounts
        if (buildType != 0) {
            calcPowerGoal();
        } else {
            powerGoal = 0;
        }

        decrFluidFuel();

        // Process building
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
                // An instant construction item replaces this tick's normal
                // fuel use; consuming both lost stored fuel unnecessarily.
                powerRemained -= BUILD_SPEED;
                powerConsumed += BUILD_SPEED;
            }

            // Check for build completion
            if (powerGoal > 0 && powerConsumed >= powerGoal) {
                buildComplete();
                sendUpdate = true;
            }
        } else if (!canBuild()) {
            // Reset progress if build is no longer possible
            powerConsumed = 0;
        }

        // Detect state change for blockstate update
        boolean nowActive = isBuilding();
        if (isActive != nowActive) {
            isActive = nowActive;
            assert this.level != null;
            BlockSmallShipyard.updateBlockState(isActive, this.level, this.worldPosition);
            sendUpdate = true;
        }

        // Periodic or change-triggered sync
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
        tag.putInt("PowerConsumed", powerConsumed);
        tag.putInt("PowerRemained", powerRemained);
        tag.putInt("PowerGoal", powerGoal);
        tag.putInt("SelectMat", selectMat);
        tag.putIntArray("MatsStock", matsStock);
        tag.putIntArray("MatsBuild", matsBuild);
        tag.putBoolean("Active", isActive);
        tag.put("FuelFluid", fuelTank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        buildType = tag.getInt("BuildType");
        powerConsumed = tag.getInt("PowerConsumed");
        powerRemained = tag.getInt("PowerRemained");
        powerGoal = tag.getInt("PowerGoal");
        selectMat = Math.max(0, Math.min(tag.getInt("SelectMat"), 3));
        if (tag.contains("MatsStock")) {
            int[] storedStock = tag.getIntArray("MatsStock");
            if (storedStock.length == 4) {
                for (int i = 0; i < storedStock.length; i++) {
                    matsStock[i] = Math.max(0, Math.min(storedStock[i], MAX_STOCK));
                }
            }
        }
        if (tag.contains("MatsBuild")) {
            int[] storedBuild = tag.getIntArray("MatsBuild");
            if (storedBuild.length == 4) {
                for (int i = 0; i < storedBuild.length; i++) {
                    matsBuild[i] = Math.max(0, Math.min(storedBuild[i], SmallRecipes.MAX_MATERIAL));
                }
            }
        }
        isActive = tag.getBoolean("Active");
        if (tag.contains("FuelFluid")) {
            fuelTank.readFromNBT(tag.getCompound("FuelFluid"));
        }
    }

    private boolean recycleShipSpawnEgg(ItemStack stack) {
        ItemStack[] recycledItems = ShipCalc.getKaitaiItems(ShipSpawnEgg.getShipClass(stack));
        int[] totalMats = new int[4];

        for (ItemStack recycled : recycledItems) {
            if (recycled.isEmpty() || !(recycled.getItem() instanceof IShipResourceItem resource)) {
                return false;
            }
            int[] resourceValue = resource.getResourceValue(recycled.getDamageValue());
            for (int i = 0; i < totalMats.length; i++) {
                totalMats[i] += resourceValue[i] * recycled.getCount();
            }
        }

        if (ConfigHandler.easyMode()) {
            for (int i = 0; i < totalMats.length; i++) {
                totalMats[i] *= 10;
            }
        }
        for (int i = 0; i < totalMats.length; i++) {
            if (totalMats[i] < 0 || matsStock[i] > MAX_STOCK - totalMats[i]) {
                return false;
            }
        }
        for (int i = 0; i < totalMats.length; i++) {
            matsStock[i] += totalMats[i];
        }
        return true;
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

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return fuelTankCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fuelTankCapability.invalidate();
    }
}
