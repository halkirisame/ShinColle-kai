package com.lulan.shincolle.item;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.ai.registry.ShipTraitRegistries;
import com.lulan.shincolle.shipdata.GenericShipSpawner;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.lulan.shincolle.shipdata.ShipIndividual;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/** Separate from the built-in ships' numeric-class spawn eggs. */
public final class GenericShipSpawnEgg extends Item {
    public GenericShipSpawnEgg() {
        super(new Item.Properties().stacksTo(16));
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
        if (stack.getTag() == null) return refuse(context, "invalid_id");
        ShipDefinitionId id;
        try {
            id = ShipDefinitionId.parse(stack.getTag().getString("ShipId"));
        } catch (IllegalArgumentException exception) {
            return refuse(context, "invalid_id");
        }
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Mob ship = GenericShipSpawner.create(level, id, stack.getTag().getBoolean("Hostile"), context.getPlayer());
        if (ship == null) return refuse(context, "unknown_ship");
        List<ItemStack> overflow = List.of();
        if (ship instanceof com.lulan.shincolle.entity.GenericShipEntity friendly && stack.getTag().contains("StateMinor")) {
            if (!ShipSpawnEgg.canAffordSavedEggXpCost(context.getPlayer(), stack.getTag())) {
                ship.invalidateCaps();
                return InteractionResult.FAIL;
            }
            overflow = ShipSpawnEgg.initShipFromEgg(friendly, stack, context.getPlayer());
            friendly.setStateMinor(com.lulan.shincolle.reference.ID.M.ShipClass, 0);
        }
        if (ship instanceof com.lulan.shincolle.entity.GenericShipEntity friendly) {
            friendly.readIndividual(stack.getTag());
            friendly.calcShipAttributes(31, false);
            friendly.setHealth(friendly.getMaxHealth());
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        ship.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getRotation(), 0);
        if (stack.hasCustomHoverName()) ship.setCustomName(stack.getHoverName());
        if (!level.addFreshEntity(ship)) {
            ship.discard();
            ship.invalidateCaps();
            return InteractionResult.FAIL;
        }
        if (ship instanceof com.lulan.shincolle.entity.GenericShipEntity && stack.getTag().contains("StateMinor")) {
            ShipSpawnEgg.returnRevivalEquipment(ship, context.getPlayer(), overflow);
            ShipSpawnEgg.consumeSavedEggXpCost(context.getPlayer(), stack.getTag());
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.getTag() == null ? "" : stack.getTag().getString("ShipId");
        return id.isEmpty() ? super.getName(stack) : Component.translatable(getDescriptionId(), id);
    }

    private static InteractionResult refuse(UseOnContext context, String reason) {
        if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable("message.shincolle_kai.ship_egg." + reason), true);
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.getTag() == null || !stack.getTag().contains(ShipIndividual.KEY)) return;
        var individual = ShipIndividual.load(stack.getTag(), stack.getTag().getString("ShipId"));
        var view = individual.presentation();
        if (individual.recruitmentData() == null) return;
        if (!view.name().isEmpty()) tooltip.add(Component.translatable(view.name()).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("rarity.shincolle_kai." + view.rarity().name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        for (var trait : individual.traits()) {
            tooltip.add(ShipTraitRegistries.isSelectableTrait(trait)
                    ? Component.translatable("trait.shincolle_kai." + trait.getPath()).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("tooltip.shincolle_kai.ship_egg.inactive_trait", trait.toString())
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
