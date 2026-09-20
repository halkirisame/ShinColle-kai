package com.lulan.shincolle.loot;

import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.item.BasicEquip;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Collection;
import java.util.List;

/** Adds the raw result of a separate datapack loot table to a matched table. */
public final class InjectLootTableModifier extends LootModifier {

    public static final Codec<InjectLootTableModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance)
                    .and(ResourceLocation.CODEC.fieldOf("loot_table")
                            .forGetter(InjectLootTableModifier::lootTable))
                    .apply(instance, InjectLootTableModifier::new));

    private final ResourceLocation lootTable;

    public InjectLootTableModifier(LootItemCondition[] conditions, ResourceLocation lootTable) {
        super(conditions);
        this.lootTable = lootTable;
    }

    public ResourceLocation lootTable() {
        return lootTable;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                           LootContext context) {
        if (lootTable.equals(context.getQueriedLootTableId())) {
            return generatedLoot;
        }

        LootTable injectedTable = context.getResolver().getLootTable(lootTable);
        injectedTable.getRandomItemsRaw(context, stack -> {
            applyRandomEquipmentVariant(stack, context);
            generatedLoot.add(stack);
        });
        return generatedLoot;
    }

    private static void applyRandomEquipmentVariant(ItemStack stack, LootContext context) {
        if (!(stack.getItem() instanceof BasicEquip) || stack.hasTag()
                && stack.getTag().contains(BasicEquip.TAG_EQUIP_META)) {
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        List<EquipDefinition> definitions = collectLootCandidates(EquipDataRegistry.server().all(), itemId);
        if (!definitions.isEmpty()) {
            EquipDefinition selected = definitions.get(context.getRandom().nextInt(definitions.size()));
            BasicEquip.setEquipMeta(stack, selected.variant());
        }
    }

    static List<EquipDefinition> collectLootCandidates(Collection<EquipDefinition> definitions,
                                                       ResourceLocation itemId) {
        return definitions.stream()
                .filter(definition -> definition.item().equals(itemId))
                .filter(definition -> definition.availability().canLoot())
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .toList();
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
