package com.lulan.shincolle.loot;

import com.lulan.shincolle.init.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/** Adds an experimental talisman to hostile mob death loot, independently of grudge drops. */
public final class SoulTalismanDropModifier extends LootModifier {
    public static final Codec<SoulTalismanDropModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance)
                    .and(Codec.FLOAT.fieldOf("chance").forGetter(modifier -> modifier.chance))
                    .apply(instance, SoulTalismanDropModifier::new));

    private final float chance;

    public SoulTalismanDropModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                         LootContext context) {
        if (!AddItemModifier.isMobDeathLoot(context)
                || !(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Enemy)) {
            return generatedLoot;
        }

        if (context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModItems.SOUL_TALISMAN.get(), 1));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
