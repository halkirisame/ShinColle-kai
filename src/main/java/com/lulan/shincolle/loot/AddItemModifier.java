package com.lulan.shincolle.loot;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Adds grudge to mob death loot, reproducing the 1.10.2 {@code EventHandler#onDrop} behaviour.
 *
 * <p>The modifier is registered without loot conditions, so it is offered every loot table in the
 * game. Only mob death loot may be modified: block loot must be left alone, otherwise blocks
 * destroyed by a hostile mob's explosion drop grudge instead of their own drops.</p>
 */
public class AddItemModifier extends LootModifier {
    // CODEC：Java <-> JSONへの変換をするための機能
    public static final Codec<AddItemModifier> CODEC = RecordCodecBuilder.create(
            instance -> codecStart(instance).apply(instance, AddItemModifier::new)
    );

    /**
     * Constructs a LootModifier.
     *
     * @param conditionsIn the ILootConditions that need to be matched before the loot is modified.
     */
    public AddItemModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!isMobDeathLoot(context)) {
            return generatedLoot;
        }

        if (!dropsGrudge(context.getParamOrNull(LootContextParams.THIS_ENTITY))) {
            return generatedLoot;
        }

        addGrudge(generatedLoot, context.getRandom(), ConfigHandler.dropRateGrudge());
        return generatedLoot;
    }

    /**
     * The 1.10.2 original hooked {@code LivingDropsEvent}, so only a mob's own death loot carried
     * grudge. Entity death loot always supplies a damage source; block loot supplies a block state
     * and never a damage source, and it carries the destroying entity as {@code THIS_ENTITY} - which
     * is why an unguarded modifier adds grudge to every block a creeper blows up.
     */
    private static boolean isMobDeathLoot(LootContext context) {
        return context.hasParam(LootContextParams.DAMAGE_SOURCE)
                && !context.hasParam(LootContextParams.BLOCK_STATE);
    }

    /**
     * 1.10.2 condition: {@code host instanceof IMob || EntitySlime || EntityGolem}.
     */
    static boolean dropsGrudge(Entity entity) {
        return entity instanceof Enemy || entity instanceof Slime || entity instanceof AbstractGolem;
    }

    /**
     * Reproduces the original drop count rule: the whole part of the rate drops for certain, and the
     * remaining fraction drops one more by chance.
     *
     * <p>The original rolls twice when the rate is below 1 - once in its {@code else} branch and
     * once in the trailing fraction check - so a rate of 0.6 can yield two grudge. That quirk is
     * kept deliberately; at the default rate of 1.0 both versions drop exactly one.</p>
     */
    static void addGrudge(ObjectArrayList<ItemStack> loot, RandomSource random, double rate) {
        int whole = (int) rate;

        if (whole > 0) {
            loot.add(new ItemStack(ModItems.GRUDGE.get(), whole));
        } else if (random.nextFloat() <= rate) {
            loot.add(new ItemStack(ModItems.GRUDGE.get(), 1));
        }

        if (random.nextFloat() < (rate - whole)) {
            loot.add(new ItemStack(ModItems.GRUDGE.get(), 1));
        }
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

}
