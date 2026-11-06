package com.lulan.shincolle.item;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Reusable transport of an owned ship's ordinary entity save, without egg initialization. */
public class SafariNetItem extends BasicItem {
    private static final String CONTENTS = "SafariShip";
    private static final String DATA = "EntityData";
    private static final String OWNER = "Owner";

    public SafariNetItem() {
        super(new Properties());
    }

    public static boolean isFilled(ItemStack stack) {
        return stack.getItem() instanceof SafariNetItem && stack.hasTag()
                && stack.getTag().contains(CONTENTS, Tag.TAG_COMPOUND);
    }

    public static boolean isStoredOwner(ItemStack stack, Player player) {
        CompoundTag contents = contents(stack);
        return contents.hasUUID(OWNER) && contents.getUUID(OWNER).equals(player.getUUID());
    }

    private static CompoundTag contents(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getCompound(CONTENTS) : new CompoundTag();
    }

    public static int getIcon(ItemStack stack) {
        return isFilled(stack) ? contents(stack).getInt("Icon") : 0;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return isFilled(stack) ? 1 : super.getMaxStackSize(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return player.isShiftKeyDown() ? InteractionResult.PASS : capture(player, target, hand);
    }

    public InteractionResult capture(Player player, LivingEntity target, InteractionHand hand) {
        if (target.level().isClientSide()) return InteractionResult.SUCCESS;
        String rejection = ShipTransport.captureRejection(player, target);
        if (rejection != null) return refuse(player, rejection);
        BasicEntityShip ship = (BasicEntityShip) target;
        ItemStack stack = player.getItemInHand(hand);
        if (isFilled(stack)) return refuse(player, "filled");
        ShipTransport.detach(player, ship);

        ItemStack filled = stack.copyWithCount(1);
        try {
            CompoundTag stored = ShipTransport.save(player, ship);
            if (stored == null) return refuse(player, "save_failed");
            filled.getOrCreateTag().put(CONTENTS, stored.copy());
            if (!stored.equals(contents(filled))) return refuse(player, "save_failed");
        } catch (RuntimeException error) {
            return refuse(player, "save_failed");
        }

        if (stack.getCount() == 1) {
            player.setItemInHand(hand, filled);
        } else {
            if (!tryStoreFilledNet(player, filled)) {
                BasicEntityItem drop = new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(), ship.level(),
                        player.getX(), player.getY(), player.getZ(), filled);
                if (!ship.level().addFreshEntity(drop)) return refuse(player, "save_failed");
            }
            stack.shrink(1);
        }
        // Both the read-back check and placement succeed before the live ship is removed.
        ship.discard();
        return InteractionResult.SUCCESS;
    }

    /** Moves only stored nets into real inventory space, without creative overflow deletion. */
    public static boolean tryStoreFilledNet(Player player, ItemStack stack) {
        if (stack.isEmpty() || !isFilled(stack)) return false;
        return ShipTransport.storeInInventory(player, stack);
    }

    private static int genericIcon(String type) {
        return ShipTransport.genericIcon(type);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!isFilled(stack)) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        if (!isStoredOwner(stack, player)) return refuse(player, "not_owner");
        if (!context.getLevel().mayInteract(player, context.getClickedPos())) return InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        String rejection = ShipTransport.release(player, (ServerLevel) context.getLevel(), contents(stack), List.of(pos), false);
        if (rejection != null) return refuse(player, rejection);
        stack.removeTagKey(CONTENTS);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult refuse(Player player, String reason) {
        player.displayClientMessage(Component.translatable("message.shincolle_kai.safari_net." + reason), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return isFilled(stack);
    }

    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        return new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(), level,
                location.getX(), location.getY(), location.getZ(), stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!isFilled(stack)) return;
        CompoundTag stored = contents(stack);
        tooltip.add(Component.literal(stored.getString("Name")).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.shincolle_kai.safari_net.type", stored.getString("TypeName"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.shincolle_kai.eggText").append(" " + stored.getInt("Level"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(stored.getString("OwnerName")).withStyle(ChatFormatting.RED));
    }

    public static ShipEggContents readStoredInventory(ItemStack stack) {
        ItemStack view = new ItemStack(com.lulan.shincolle.init.ModItems.SHIP_SPAWN_EGG.get());
        view.setTag(contents(stack).getCompound(DATA).copy());
        ListTag curios = contents(stack).getList("CuriosEquipment", Tag.TAG_COMPOUND);
        if (!curios.isEmpty()) view.getOrCreateTag().put(BasicEntityShip.CURIOS_EGG_TAG, curios.copy());
        return ShipSpawnEgg.readStoredInventory(view);
    }
}
