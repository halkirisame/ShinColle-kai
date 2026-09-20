package com.lulan.shincolle.loot;

import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("try")
public final class GrudgeDropGameTests {

    private GrudgeDropGameTests() {
    }

    /**
     * A creeper's explosion runs each destroyed block's loot table with the creeper as
     * {@code THIS_ENTITY}. Grudge must not be added there, or blown up dirt drops grudge instead of
     * dirt.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void explodedBlockLootKeepsNoGrudge(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            Creeper creeper = entities.add(helper.spawn(EntityType.CREEPER, 1, 2, 1));

            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, creeper.position())
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, Blocks.DIRT.defaultBlockState())
                    .withParameter(LootContextParams.THIS_ENTITY, creeper)
                    .withParameter(LootContextParams.EXPLOSION_RADIUS, 3.0F)
                    .create(LootContextParamSets.BLOCK);

            ObjectArrayList<ItemStack> loot = applyModifier(params, new ItemStack(Items.DIRT));
            if (countGrudge(loot) != 0) {
                throw new AssertionError("Block loot must not gain grudge, but saw " + countGrudge(loot));
            }
            if (loot.size() != 1 || !loot.get(0).is(Items.DIRT)) {
                throw new AssertionError("Block loot must be left untouched, but saw " + loot);
            }
        }
        helper.succeed();
    }

    /**
     * The 1.10.2 original added grudge to hostile mob death drops; that must still happen.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileMobDeathLootGainsGrudge(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            Creeper creeper = entities.add(helper.spawn(EntityType.CREEPER, 1, 2, 1));

            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, creeper.position())
                    .withParameter(LootContextParams.THIS_ENTITY, creeper)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                    .create(LootContextParamSets.ENTITY);

            ObjectArrayList<ItemStack> loot = applyModifier(params, new ItemStack(Items.GUNPOWDER));
            if (countGrudge(loot) <= 0) {
                throw new AssertionError("Hostile mob death loot must gain grudge, but saw " + loot);
            }
        }
        helper.succeed();
    }

    /**
     * The original drop count rule: the whole part of the rate is certain, the fraction is a chance.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void grudgeCountFollowsConfiguredRate(GameTestHelper helper) {
        assertRate(3.0D, 3, 3);
        assertRate(1.0D, 1, 1);
        // Below 1 the original rolls twice, so up to two grudge can drop.
        assertRate(0.6D, 0, 2);
        assertRate(5.5D, 5, 6);
        assertRate(0.0D, 0, 0);
        helper.succeed();
    }

    private static void assertRate(double rate, int min, int max) {
        for (long seed = 0; seed < 64; seed++) {
            ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
            AddItemModifier.addGrudge(loot, RandomSource.create(seed), rate);
            int count = countGrudge(loot);
            if (count < min || count > max) {
                throw new AssertionError("Rate " + rate + " must drop " + min + ".." + max
                        + " grudge, but seed " + seed + " dropped " + count);
            }
        }
    }

    private static ObjectArrayList<ItemStack> applyModifier(LootParams params, ItemStack vanillaDrop) {
        LootContext context = new LootContext.Builder(params).create(null);
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
        loot.add(vanillaDrop);
        return new AddItemModifier(new LootItemCondition[]{}).apply(loot, context);
    }

    private static int countGrudge(ObjectArrayList<ItemStack> loot) {
        int total = 0;
        for (ItemStack stack : loot) {
            if (stack.is(ModItems.GRUDGE.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
