package com.lulan.shincolle.client.gui.inventory;

import com.lulan.shincolle.crafting.InventoryCraftingFake;
import com.lulan.shincolle.init.ModMenuTypes;
import com.lulan.shincolle.item.RecipePaper;
import com.lulan.shincolle.item.RecipePaperData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Server-owned recipe editor. Ghost stacks are never returned as real inventory. */
public class ContainerRecipePaper extends AbstractContainerMenu {
    public static final int CRAFTING_SLOT_COUNT = 9;
    public static final int RESULT_SLOT = 9;
    public static final int GHOST_SLOT_COUNT = 10;
    private static final int PREVIEW_REFRESH_TICKS = 20;

    private final SimpleContainer recipeInv = new SimpleContainer(GHOST_SLOT_COUNT);
    private final DataSlot invalidRecipe = DataSlot.standalone();
    private final Player owner;
    private final Level openedLevel;
    private final int heldSlot;
    private final ItemStack hostStack;
    private final boolean displayOnly;
    private long lastPreviewTick;
    private boolean closed;

    public ContainerRecipePaper(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, buf.readInt(), true);
    }

    public ContainerRecipePaper(int containerId, Inventory playerInv, int heldSlot) {
        this(containerId, playerInv, heldSlot, false);
    }

    private ContainerRecipePaper(int containerId, Inventory playerInv, int heldSlot, boolean displayOnly) {
        super(ModMenuTypes.RECIPE_PAPER.get(), containerId);
        if (!displayOnly && !((heldSlot >= 0 && heldSlot < 9) || heldSlot == 40)) {
            throw new IllegalArgumentException("Recipe paper must be opened from a hand slot");
        }
        this.owner = playerInv.player;
        this.openedLevel = owner.level();
        this.heldSlot = heldSlot;
        this.displayOnly = displayOnly;
        this.hostStack = displayOnly ? ItemStack.EMPTY : playerInv.getItem(heldSlot);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotRecipePaper(recipeInv, row * 3 + col, 30 + col * 18, 17 + row * 18));
            }
        }
        addSlot(new SlotRecipePaper(recipeInv, RESULT_SLOT, 124, 35));
        addPlayerInventory(playerInv, 8, 84);
        addDataSlot(invalidRecipe);

        if (!displayOnly) {
            RecipePaperData.Decoded decoded = RecipePaperData.decode(hostStack);
            if (decoded instanceof RecipePaperData.Valid valid) {
                for (int i = 0; i < CRAFTING_SLOT_COUNT; i++) recipeInv.setItem(i, valid.input(i));
            } else {
                invalidRecipe.set(1);
            }
            refreshPreview();
        }
    }

    protected void addPlayerInventory(Inventory inv, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, 9 + row * 9 + col, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, x + col * 18, y + 58));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (displayOnly) return;
        if (!stillValid(player)) {
            closeInvalidMenu();
            return;
        }
        if (slotId >= 0 && slotId < GHOST_SLOT_COUNT) {
            if (slotId < CRAFTING_SLOT_COUNT && clickType == ClickType.PICKUP && (button == 0 || button == 1)) {
                ItemStack carried = getCarried();
                recipeInv.setItem(slotId, button == 1 || carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
                invalidRecipe.set(0);
                refreshPreview();
                List<ItemStack> inputs = new ArrayList<>(CRAFTING_SLOT_COUNT);
                for (int i = 0; i < CRAFTING_SLOT_COUNT; i++) inputs.add(recipeInv.getItem(i));
                RecipePaperData.write(hostStack, inputs, recipeInv.getItem(RESULT_SLOT));
                owner.getInventory().setChanged();
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
        if (!stillValid(player)) closeInvalidMenu();
    }

    private void refreshPreview() {
        ItemStack result = ItemStack.EMPTY;
        if (!hasInvalidRecipe()) {
            InventoryCraftingFake crafting = new InventoryCraftingFake(3, 3);
            for (int i = 0; i < CRAFTING_SLOT_COUNT; i++) crafting.setItem(i, recipeInv.getItem(i).copy());
            result = openedLevel.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, openedLevel)
                    .map(recipe -> recipe.assemble(crafting, openedLevel.registryAccess())).orElse(ItemStack.EMPTY);
        }
        recipeInv.setItem(RESULT_SLOT, result);
        lastPreviewTick = openedLevel.getGameTime();
    }

    @Override
    public void broadcastChanges() {
        if (!displayOnly) {
            if (!stillValid(owner)) {
                closeInvalidMenu();
                return;
            }
            if (openedLevel.getGameTime() - lastPreviewTick >= PREVIEW_REFRESH_TICKS) refreshPreview();
        }
        super.broadcastChanges();
    }

    private void closeInvalidMenu() {
        if (owner.containerMenu == this) owner.closeContainer();
    }

    @Override
    public boolean stillValid(Player player) {
        return displayOnly || (!closed && player == owner && owner.isAlive() && owner.level() == openedLevel
                && owner.getInventory().getItem(heldSlot) == hostStack && hostStack.getItem() instanceof RecipePaper);
    }

    @Override
    public void removed(Player player) {
        closed = true;
        super.removed(player);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof SlotRecipePaper) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof SlotRecipePaper) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public boolean hasInvalidRecipe() {
        return invalidRecipe.get() != 0;
    }

    public SimpleContainer getRecipeInv() {
        return recipeInv;
    }

    public int getHeldSlot() {
        return heldSlot;
    }
}
