package com.lulan.shincolle.item;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.shipdata.GenericShipSpawner;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Separate from the built-in ships' numeric-class spawn eggs. */
public final class GenericShipSpawnEgg extends Item {
    public GenericShipSpawnEgg() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack create(ShipDefinitionId id, boolean hostile) {
        ItemStack stack = new ItemStack(ModItems.GENERIC_SHIP_SPAWN_EGG.get());
        stack.getOrCreateTag().putString("ShipId", id.toString());
        stack.getOrCreateTag().putBoolean("Hostile", hostile);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (stack.getTag() == null) return InteractionResult.FAIL;
        ShipDefinitionId id;
        try {
            id = ShipDefinitionId.parse(stack.getTag().getString("ShipId"));
        } catch (IllegalArgumentException exception) {
            return InteractionResult.FAIL;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Mob ship = GenericShipSpawner.create(level, id, stack.getTag().getBoolean("Hostile"), context.getPlayer());
        if (ship == null) return InteractionResult.FAIL;
        if (ship instanceof com.lulan.shincolle.entity.GenericShipEntity friendly && stack.getTag().contains("StateMinor")) {
            if (!ShipSpawnEgg.consumeSavedEggXpCost(context.getPlayer(), stack.getTag())) return InteractionResult.FAIL;
            ShipSpawnEgg.initShipFromEgg(friendly, stack, context.getPlayer());
            friendly.setStateMinor(com.lulan.shincolle.reference.ID.M.ShipClass, 0);
            friendly.calcShipAttributes(31, false);
            friendly.setHealth(friendly.getMaxHealth());
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        ship.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getRotation(), 0);
        if (stack.hasCustomHoverName()) ship.setCustomName(stack.getHoverName());
        if (!level.addFreshEntity(ship)) return InteractionResult.FAIL;
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.getTag() == null ? "" : stack.getTag().getString("ShipId");
        return id.isEmpty() ? super.getName(stack) : Component.translatable(getDescriptionId(), id);
    }
}
