package com.lulan.shincolle.item;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Side-safe presentation of declarative ship-equipment attack effects. */
public final class ShipAttackEffectTooltipFormatter {

    private ShipAttackEffectTooltipFormatter() {
    }

    /** Appends effects in stable identifier order, retaining the ID as a safe fallback label. */
    public static void append(Map<ResourceLocation, ShipAttackEffect> effects, List<Component> tooltip) {
        effects.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> tooltip.add(format(entry.getKey(), entry.getValue())));
    }

    private static Component format(ResourceLocation effectId, ShipAttackEffect attackEffect) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
        String effectName = effect == null
                ? effectId.toString()
                : Component.translatable(effect.getDescriptionId()).getString().trim();
        return Component.translatable("gui.shincolle.equip.enchantshell",
                attackEffect.chancePercent(), effectName, attackEffect.amplifier() + 1,
                attackEffect.durationTicks() / 20);
    }
}
