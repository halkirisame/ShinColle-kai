package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.item.ShipEggContents;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * What a saved ship egg is carrying, handed to the tooltip renderer.
 * <p>
 * One text line per item overflows the screen well before a ship's sixty slots
 * are covered, so these are drawn as a grid of icons instead - equipment on its
 * own row so it reads apart from cargo, the way the ship's own inventory screen
 * separates them.
 */
public record ShipEggContentsTooltip(ShipEggContents contents) implements TooltipComponent {
}
