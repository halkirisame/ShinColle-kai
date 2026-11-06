package com.lulan.shincolle.item;

import com.lulan.shincolle.client.gui.inventory.ContainerRecipePaper;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Recipe Paper stores a ghost crafting pattern for ship crafting tasks. */
public class RecipePaper extends BasicItem {
    public RecipePaper() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int heldSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, owner) -> new ContainerRecipePaper(id, inventory, heldSlot),
                    Component.translatable("item.shincolle_kai.recipe_paper")), buf -> buf.writeInt(heldSlot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        RecipePaperData.Decoded decoded = RecipePaperData.decode(stack);
        if (!(decoded instanceof RecipePaperData.Valid valid)) {
            tooltip.add(Component.translatable("gui.shincolle_kai.recipepaper.invalid").withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack result = valid.recordedResult();
        if (!result.isEmpty()) {
            tooltip.add(Component.translatable("gui.shincolle_kai.recipepaper.result")
                    .append(" ").append(result.getHoverName()).append(" x" + result.getCount()).withStyle(ChatFormatting.YELLOW));
        }
        boolean header = false;
        for (int i = 0; i < 9; i++) {
            ItemStack input = valid.input(i);
            if (!input.isEmpty()) {
                if (!header) {
                    tooltip.add(Component.translatable("gui.shincolle_kai.recipepaper.material").withStyle(ChatFormatting.AQUA));
                    header = true;
                }
                tooltip.add(Component.literal("  ").append(input.getHoverName()).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
