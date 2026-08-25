package com.lulan.shincolle.equip.tinkers;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.api.equipment.ShipEquipmentContext;
import com.lulan.shincolle.api.equipment.ShipEquipmentProvider;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only class in this mod that touches Tinkers' Construct API types.
 * Referenced only from behind a {@code ModList.get().isLoaded("tconstruct")}
 * check - see {@code ShinColle}'s constructor, which registers the single
 * instance below with the canonical ship-equipment provider registry only
 * when Tinkers' is actually present.
 *
 * <p>Rather than requiring a purpose-built item class registered by an
 * addon, this reads *any* Tinkers' tool already in the world - a sword or
 * pickaxe a player forged themselves
 * works as ship equipment the moment it's dropped in the Curios slot, no
 * addon or special item required. Base stats come from the tool's own
 * material-derived {@link ToolStats}; modifiers (including material-derived
 * traits, and anything a mod like TiCEX adds) are read generically below.
 */
public final class ShipTinkersIntegration implements ShipEquipmentProvider {

    public static final ShipTinkersIntegration INSTANCE = new ShipTinkersIntegration();
    public static final ResourceLocation PROVIDER_ID =
            ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "tconstruct_tools");

    private ShipTinkersIntegration() {
    }

    @Override
    public boolean matches(ItemStack stack) {
        return stack.getItem() instanceof IModifiable;
    }

    @Override
    public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
        ItemStack stack = context.stack();
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(context.layout());
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken()) {
            return new ResolvedShipEquipment(result.build(), ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
        }

        // Base stats scale directly with the tool's own material stats, so a
        // tool forged from a stronger material contributes more - with no
        // per-item balancing needed on this mod's side.
        float attackDamage = tool.getStats().get(ToolStats.ATTACK_DAMAGE);
        float attackSpeed = tool.getStats().get(ToolStats.ATTACK_SPEED);
        float durability = tool.getStats().get(ToolStats.DURABILITY);

        result.set(CoreShipAttributes.ATK_L, attackDamage);
        result.set(CoreShipAttributes.ATK_H, attackDamage * 1.5F);
        result.set(CoreShipAttributes.ATK_AL, attackDamage * 0.8F);
        result.set(CoreShipAttributes.ATK_AH, attackDamage * 1.2F);
        result.set(CoreShipAttributes.HP, durability * 0.05F);
        // 4.0 is roughly a vanilla sword's baseline attack speed in Tinkers' units.
        result.set(CoreShipAttributes.SPD, (attackSpeed - 4.0F) * 0.02F);

        applyModifierEffects(result, tool);
        return new ResolvedShipEquipment(result.build(), ResolvedShipEquipment.DEFAULT_COMPATIBILITY,
                collectAttackEffects(tool));
    }

    /** One modifier's display name (e.g. "Sharpness III") paired with its full description, for tooltips. */
    public record ModifierLine(Component name, Component description) {
    }

    /**
     * Lists this tool's modifiers for display (e.g. {@link
     * com.lulan.shincolle.client.gui.GuiShipEquipDetail}), using Tinkers' own
     * display names and level-aware descriptions unmodified.
     */
    public List<ModifierLine> describeModifiers(ItemStack stack) {
        List<ModifierLine> result = new ArrayList<>();
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken()) {
            return result;
        }
        for (ModifierEntry entry : tool.getModifiers().getModifiers()) {
            if (entry.getLevel() > 0) {
                result.add(new ModifierLine(entry.getDisplayName(),
                        entry.getModifier().getDescription(entry.getLevel())));
            }
        }
        return result;
    }

    /**
     * Translates the tool's modifiers (material-derived traits included) into
     * extra ship stats. Unrecognised modifiers - including anything a
     * Tinkers'-adjacent mod adds - still count for a small generic bonus per
     * level, so they're never simply ignored.
     */
    private void applyModifierEffects(ShipAttributeValues.Builder attrs, ToolStack tool) {
        for (ModifierEntry entry : tool.getModifiers().getModifiers()) {
            int level = entry.getLevel();
            if (level <= 0) {
                continue;
            }
            String id = entry.getId().toString();

            switch (id) {
                case "tconstruct:sharpness", "tconstruct:swiftstrike" -> {
                    attrs.add(CoreShipAttributes.ATK_AL, level);
                    attrs.add(CoreShipAttributes.ATK_AH, level * 2F);
                }
                case "tconstruct:piercing", "tconstruct:pierce" ->
                        attrs.add(CoreShipAttributes.ASM, level * 2F);
                case "tconstruct:padded", "tconstruct:lightweight" ->
                        attrs.add(CoreShipAttributes.MOV, level * 0.02F);
                case "tconstruct:heavy" -> {
                    attrs.add(CoreShipAttributes.DEF, level * 0.02F);
                    attrs.add(CoreShipAttributes.MOV, -level * 0.01F);
                }
                case "tconstruct:overforced", "tconstruct:overlord" ->
                        attrs.add(CoreShipAttributes.HP, level * 10F);
                case "tconstruct:depth_protection" ->
                        attrs.add(CoreShipAttributes.DEF, level * 0.03F);
                case "tconstruct:depth_strider" ->
                        attrs.add(CoreShipAttributes.SPD, level * 0.05F);
                case "tconstruct:luck", "tconstruct:looting" ->
                        attrs.add(CoreShipAttributes.CRI, level * 0.02F);
                default -> {
                    // Generic fallback so unknown modifiers still contribute
                    // something proportional to their level.
                    attrs.add(CoreShipAttributes.ATK_AH, level * 0.5F);
                    attrs.add(CoreShipAttributes.HIT, level * 0.05F);
                }
            }
        }
    }

    /**
     * Translates modifiers into on-hit effects for the ship's attacks, written
     * into ShinColle's own {@code AttackEffectMap} (effect id -> {amplifier,
     * duration ticks, percent chance}).
     *
     * <p>That map only carries MobEffects, so a modifier's real Tinkers
     * behaviour can't be reproduced exactly - each is mapped to the closest
     * potion effect instead, scaling with its level.
     */
    private static Map<ResourceLocation, ShipAttackEffect> collectAttackEffects(ToolStack tool) {
        Map<ResourceLocation, ShipAttackEffect> effectMap = new LinkedHashMap<>();
        for (ModifierEntry entry : tool.getModifiers().getModifiers()) {
            int level = entry.getLevel();
            if (level <= 0) {
                continue;
            }
            mergeModifierEffect(effectMap, entry.getId(), level);
        }
        return Map.copyOf(effectMap);
    }

    /** Pure mapping seam shared by the provider and its regression test. */
    static void mergeModifierEffect(Map<ResourceLocation, ShipAttackEffect> effectMap,
                                    ResourceLocation modifierId, int level) {
        if (level <= 0) {
            return;
        }
        switch (modifierId.toString()) {
            case "tconstruct:necrotic", "tconstruct:wither_bone" ->
                    merge(effectMap, "wither", level - 1, 60, 20 + 10 * level);
            case "tconstruct:venom" ->
                    merge(effectMap, "poison", level - 1, 80, 25 + 10 * level);
            case "tconstruct:freezing", "tconstruct:cooling" ->
                    merge(effectMap, "slowness", level - 1, 60, 30 + 10 * level);
            case "tconstruct:severing", "tconstruct:melting" ->
                    merge(effectMap, "weakness", level - 1, 80, 25 + 10 * level);
            case "tconstruct:blindshot" ->
                    merge(effectMap, "blindness", 0, 60, 15 + 5 * level);
            case "tconstruct:fiery", "tconstruct:scorching" ->
                    // No burning effect exists as a potion, so the closest
                    // equivalent is a small instant hit.
                    merge(effectMap, "instant_damage", level - 1, 5, 20 + 10 * level);
            case "tconstruct:magnetic" ->
                    merge(effectMap, "mining_fatigue", level - 1, 60, 20 + 10 * level);
            default -> {
                // Unknown modifiers deliberately add no effect - guessing
                // one would be a balance hazard.
            }
        }
    }

    /** Keeps the strongest entry when several modifiers map to one effect. */
    private static void merge(Map<ResourceLocation, ShipAttackEffect> map, String effectPath,
                              int amplifier, int duration, int chance) {
        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath("minecraft", effectPath);
        ShipAttackEffect existing = map.get(effectId);
        if (existing == null) {
            map.put(effectId, new ShipAttackEffect(effectId, amplifier, duration, Math.min(chance, 100)));
            return;
        }
        map.put(effectId, new ShipAttackEffect(effectId,
                Math.max(existing.amplifier(), amplifier),
                Math.max(existing.durationTicks(), duration),
                Math.min(Math.max(existing.chancePercent(), chance), 100)));
    }

    /**
     * Runs the tool's real Tinkers' melee-hit modifiers with the ship standing
     * in as the attacker, so modifiers behave exactly as they would wielded by
     * a player.
     *
     * <p>Tinkers' own {@code ToolAttackUtil.attackEntity} requires a Player,
     * but {@link ToolAttackContext#attacker} accepts any LivingEntity - which
     * is what makes this possible at all. {@code ship} may be a friendly
     * {@code BasicEntityShip} or a hostile {@code BasicEntityShipHostile}
     * (both are {@code LivingEntity}), or the {@code BasicEntityAirplane}
     * attacking on a carrier's behalf.
     */
    @Override
    public void onShipHit(LivingEntity ship, Entity target, float damageDealt, ItemStack stack) {
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken() || tool.getModifiers().getModifiers().isEmpty()) {
            return;
        }

        ToolAttackContext context;
        try {
            context = ToolAttackContext.attacker(ship)
                    .hand(InteractionHand.MAIN_HAND)
                    .target(target)
                    .build();
        } catch (RuntimeException e) {
            return;
        }

        for (ModifierEntry entry : tool.getModifiers().getModifiers()) {
            try {
                entry.getHook(ModifierHooks.MELEE_HIT).afterMeleeHit(tool, entry, context, damageDealt);
            } catch (RuntimeException e) {
                // Some modifiers assume a Player attacker. One misbehaving
                // modifier must not stop the others from applying.
            }
        }
    }
}
