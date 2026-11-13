package com.lulan.shincolle.loot;

import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

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

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void enabledLootingThreeAddsThreeGrudge(GameTestHelper helper) {
        assertDeathLoot(helper, true, 1.0D, 3, 4, 4);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void disabledLootingKeepsOriginalGrudgeCount(GameTestHelper helper) {
        helper.assertTrue(!ConfigHandler.COMMON.lootingAffectsGrudge.getDefault(),
                "Looting must not affect grudge by default");
        assertDeathLoot(helper, false, 1.0D, 3, 1, 1);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void enabledWithoutLootingKeepsOriginalGrudgeCount(GameTestHelper helper) {
        assertDeathLoot(helper, true, 1.0D, 0, 1, 1);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void enabledLootingAddsToFractionalGrudgeRate(GameTestHelper helper) {
        assertDeathLoot(helper, true, 0.5D, 1, 1, 2);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void enabledLootingKeepsExplodedBlockLootUntouched(GameTestHelper helper) {
        boolean previous = ConfigHandler.COMMON.lootingAffectsGrudge.get();
        try {
            ConfigHandler.COMMON.lootingAffectsGrudge.set(true);
            helper.assertTrue(ConfigHandler.lootingAffectsGrudge(), "Looting option must be enabled");
            explodedBlockLootKeepsNoGrudge(helper);
        } finally {
            ConfigHandler.COMMON.lootingAffectsGrudge.set(previous);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void enabledLootingUsesForgeEventLevel(GameTestHelper helper) {
        assertEventLoot(helper, 5, 6);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void enabledNegativeForgeLootingDoesNotReduceGrudge(GameTestHelper helper) {
        assertEventLoot(helper, -2, 1);
        helper.succeed();
    }

    private static void assertDeathLoot(GameTestHelper helper, boolean enabled, double rate, int lootingLevel,
                                       int min, int max) {
        boolean previousEnabled = ConfigHandler.COMMON.lootingAffectsGrudge.get();
        double previousRate = ConfigHandler.COMMON.dropRateGrudge.get();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ConfigHandler.COMMON.lootingAffectsGrudge.set(enabled);
            ConfigHandler.COMMON.dropRateGrudge.set(rate);
            helper.assertTrue(ConfigHandler.lootingAffectsGrudge() == enabled, "Looting option was not applied");
            helper.assertTrue(ConfigHandler.dropRateGrudge() == rate, "Grudge rate was not applied");
            Creeper victim = entities.add(helper.spawn(EntityType.CREEPER, 1, 2, 1));
            Zombie killer = entities.add(helper.spawn(EntityType.ZOMBIE, 2, 2, 1));
            equipLootingSword(helper, killer, lootingLevel);
            LootParams params = deathParams(helper.getLevel(), victim, killer);
            for (long seed = 1; seed <= 64; seed++) {
                LootContext context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(null);
                ObjectArrayList<ItemStack> loot = new AddItemModifier(new LootItemCondition[]{})
                        .apply(new ObjectArrayList<>(), context);
                int count = countGrudge(loot);
                helper.assertTrue(count >= min && count <= max,
                        "Looting " + lootingLevel + " at rate " + rate + " must drop " + min + ".." + max
                                + " grudge, but seed " + seed + " dropped " + count);
            }
        } finally {
            ConfigHandler.COMMON.lootingAffectsGrudge.set(previousEnabled);
            ConfigHandler.COMMON.dropRateGrudge.set(previousRate);
        }
    }

    private static void assertEventLoot(GameTestHelper helper, int eventLevel, int expected) {
        boolean previousEnabled = ConfigHandler.COMMON.lootingAffectsGrudge.get();
        double previousRate = ConfigHandler.COMMON.dropRateGrudge.get();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ConfigHandler.COMMON.lootingAffectsGrudge.set(true);
            ConfigHandler.COMMON.dropRateGrudge.set(1.0D);
            helper.assertTrue(ConfigHandler.lootingAffectsGrudge(), "Looting option must be enabled");
            helper.assertTrue(ConfigHandler.dropRateGrudge() == 1.0D, "Grudge rate must be 1.0");
            Creeper victim = entities.add(helper.spawn(EntityType.CREEPER, 1, 2, 1));
            Zombie killer = entities.add(helper.spawn(EntityType.ZOMBIE, 2, 2, 1));
            equipLootingSword(helper, killer, 3);
            boolean[] observed = {false};
            Consumer<LootingLevelEvent> listener = event -> {
                if (event.getEntity() == victim) {
                    helper.assertTrue(event.getLootingLevel() == 3, "Forge must start from Looting III");
                    observed[0] = true;
                    event.setLootingLevel(eventLevel);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                ObjectArrayList<ItemStack> loot = applyModifier(deathParams(helper.getLevel(), victim, killer),
                        new ItemStack(Items.GUNPOWDER));
                helper.assertTrue(observed[0], "Modifier must obtain Looting through the Forge event");
                helper.assertTrue(countGrudge(loot) == expected,
                        "Forge Looting " + eventLevel + " must drop " + expected + " grudge, but saw " + loot);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
            }
        } finally {
            ConfigHandler.COMMON.lootingAffectsGrudge.set(previousEnabled);
            ConfigHandler.COMMON.dropRateGrudge.set(previousRate);
        }
    }

    private static void equipLootingSword(GameTestHelper helper, Zombie killer, int level) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        if (level > 0) {
            sword.enchant(Enchantments.MOB_LOOTING, level);
        }
        killer.setItemSlot(EquipmentSlot.MAINHAND, sword);
        helper.assertTrue(EnchantmentHelper.getMobLooting(killer) == level, "Killer's Looting level was not applied");
    }

    private static LootParams deathParams(ServerLevel level, Creeper victim, Zombie killer) {
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.KILLER_ENTITY, killer)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().mobAttack(killer))
                .create(LootContextParamSets.ENTITY);
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
