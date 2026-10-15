package com.lulan.shincolle.item;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.TeamHelper;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kaitai Hammer - used to dismantle ship entities for resources.
 * Has durability (20 uses), and can be used in crafting recipes
 * without being consumed (loses 1 durability per craft).
 */
public class KaitaiHammer extends BasicItem {

    public KaitaiHammer() {
        super(new Properties().stacksTo(1).durability(20));
    }

    /**
     * This hammer can be used in crafting recipes as a tool.
     * It is not consumed but loses durability.
     */
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    /**
     * Return a copy of this hammer with +1 damage when used in crafting.
     * If the hammer would break, return empty.
     */
    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack result = stack.copy();
        result.setDamageValue(stack.getDamageValue() + 1);

        if (result.getDamageValue() >= result.getMaxDamage()) {
            return ItemStack.EMPTY;
        }

        result.setCount(1);
        return result;
    }

    /**
     * Left click on one's own ship kills it in one hit (it turns back into an item
     * and loses a level). Costs 1 durability outside creative mode. A ship that
     * belongs to someone else is left alone, but the click is still consumed.
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof BasicEntityMount mount) {
            entity = mount.getHost();
            if (entity == null) {
                return true;
            }
        }

        if (!(entity instanceof BasicEntityShip ship)) {
            return false;
        }

        if (!player.level().isClientSide()
                && (TeamHelper.checkSameOwner(player, ship) || ServerDataManager.checkOP(player))) {
            ship.hurt(player.damageSources().playerAttack(player), ship.getMaxHealth() * 1.1F);

            if (!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
            }
        }

        return true;
    }
}
