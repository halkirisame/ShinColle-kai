package com.lulan.shincolle.gametest;

import com.google.gson.JsonElement;
import com.lulan.shincolle.datagen.server.ShinColleGlobalLootModifierProvider;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.loot.SoulTalismanDropModifier;
import com.lulan.shincolle.reference.Reference;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoulTalismanDropGameTests {
    private static final float CHANCE = ShinColleGlobalLootModifierProvider.SOUL_TALISMAN_DROP_CHANCE;
    // First nextFloat: 1176 / 2^24, below the default chance. Seed 1 rolls above it.
    private static final long DROP_SEED = 5188L;
    private static final long MISS_SEED = 1L;

    private SoulTalismanDropGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileDeathAddsExactlyOneBelowDefaultChance(GameTestHelper helper) {
        Entity creeper = EntityType.CREEPER.create(helper.getLevel());
        helper.assertTrue(RandomSource.create(DROP_SEED).nextFloat() < CHANCE, "Winning seed must be below chance");
        helper.assertTrue(RandomSource.create(MISS_SEED).nextFloat() > CHANCE, "Losing seed must exceed chance");
        assertDrop(helper, modifier(CHANCE), deathParams(helper, creeper), DROP_SEED, 1);
        assertDrop(helper, modifier(CHANCE), deathParams(helper, creeper), MISS_SEED, 0);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void codecChanceUsesStrictBoundaryAndRoundTrips(GameTestHelper helper) {
        Entity creeper = EntityType.CREEPER.create(helper.getLevel());
        float roll = RandomSource.create(MISS_SEED).nextFloat();
        for (float chance : new float[]{Math.nextDown(roll), roll, Math.nextUp(roll), 0F, 1F}) {
            JsonElement encoded = IGlobalLootModifier.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, modifier(chance))
                    .result().orElseThrow();
            helper.assertTrue(encoded.getAsJsonObject().get("chance").getAsFloat() == chance, "Chance changed in codec");
            IGlobalLootModifier decoded = IGlobalLootModifier.DIRECT_CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
            assertDrop(helper, decoded, deathParams(helper, creeper), MISS_SEED, roll < chance ? 1 : 0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void slimeAndHostileShipAreEligible(GameTestHelper helper) {
        for (Entity entity : List.of(EntityType.SLIME.create(helper.getLevel()),
                ModEntities.DESTROYER_SHIMAKAZE_MOB.get().create(helper.getLevel()))) {
            helper.assertTrue(entity instanceof Enemy, "Eligible fixture must implement Enemy");
            assertDrop(helper, modifier(CHANCE), deathParams(helper, entity), DROP_SEED, 1);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void passiveMobsGolemsAndFriendlyShipsNeverDrop(GameTestHelper helper) {
        for (Entity entity : List.of(EntityType.COW.create(helper.getLevel()), EntityType.IRON_GOLEM.create(helper.getLevel()),
                ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()))) {
            helper.assertTrue(!(entity instanceof Enemy), "Excluded fixture must not implement Enemy");
            assertDrop(helper, modifier(1F), deathParams(helper, entity), DROP_SEED, 0);
            assertDrop(helper, modifier(CHANCE), deathParams(helper, entity), MISS_SEED, 0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void blocksAndNonDeathContextsRemainUntouched(GameTestHelper helper) {
        Entity creeper = EntityType.CREEPER.create(helper.getLevel());
        LootParams.Builder block = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, creeper.position())
                .withParameter(LootContextParams.THIS_ENTITY, creeper)
                .withParameter(LootContextParams.BLOCK_STATE, Blocks.DIRT.defaultBlockState())
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.EXPLOSION_RADIUS, 3F);
        assertDrop(helper, modifier(1F), block.create(LootContextParamSets.BLOCK), DROP_SEED, 0);
        LootContextParamSet mixed = new LootContextParamSet.Builder()
                .required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY)
                .required(LootContextParams.BLOCK_STATE).required(LootContextParams.TOOL)
                .required(LootContextParams.EXPLOSION_RADIUS).required(LootContextParams.DAMAGE_SOURCE).build();
        block.withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().generic());
        assertDrop(helper, modifier(1F), block.create(mixed), DROP_SEED, 0);
        LootParams gift = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, creeper.position())
                .withParameter(LootContextParams.THIS_ENTITY, creeper).create(LootContextParamSets.GIFT);
        assertDrop(helper, modifier(1F), gift, DROP_SEED, 0);
        assertDrop(helper, modifier(1F), new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY), DROP_SEED, 0);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void loadedGlobalModifierAddsTalismanAfterGrudge(GameTestHelper helper) {
        // A positive whole grudge rate consumes one float before the talisman modifier.
        helper.assertTrue(ConfigHandler.dropRateGrudge() >= 1D, "Pipeline fixture requires a whole grudge drop");
        long seed = 6621L;
        RandomSource random = RandomSource.create(seed);
        random.nextFloat();
        helper.assertTrue(random.nextFloat() < CHANCE, "Second pipeline roll must be below chance");
        LootParams params = deathParams(helper, EntityType.CREEPER.create(helper.getLevel()));
        LootContext context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(null);
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
        loot.add(new ItemStack(Items.GUNPOWDER));
        loot = ForgeHooks.modifyLoot(ResourceLocation.withDefaultNamespace("entities/creeper"), loot, context);
        helper.assertTrue(countTalismans(loot) == 1, "Loaded global modifier must drop one talisman");
        helper.assertTrue(loot.stream().anyMatch(stack -> stack.is(ModItems.GRUDGE.get())), "Grudge drop must remain present");
        helper.assertTrue(loot.get(0).is(Items.GUNPOWDER), "Vanilla loot must remain present");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void noLoadedRecipeProducesTalisman(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipes().stream()
                .noneMatch(recipe -> recipe.getResultItem(helper.getLevel().registryAccess()).is(ModItems.SOUL_TALISMAN.get())),
                "No loaded recipe may produce a talisman");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sourceAndPreviewTooltipFollowExistingLines(GameTestHelper helper) {
        List<Component> tooltip = new ArrayList<>();
        ModItems.SOUL_TALISMAN.get().appendHoverText(new ItemStack(ModItems.SOUL_TALISMAN.get()),
                helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
        List<String> keys = List.of("mode0", "count", "controls", "source", "preview");
        helper.assertTrue(tooltip.size() == keys.size(), "Tooltip must keep three existing lines and add exactly two");
        for (int i = 0; i < keys.size(); i++) {
            Component line = tooltip.get(i);
            helper.assertTrue(line.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("tooltip.shincolle_kai.soul_talisman." + keys.get(i)), "Tooltip line order mismatch");
            if (i >= 3) {
                helper.assertTrue(!line.getStyle().isItalic()
                        && line.getStyle().getColor().getValue() == ChatFormatting.DARK_GRAY.getColor(), "Preview style mismatch");
            }
        }
        helper.succeed();
    }

    private static SoulTalismanDropModifier modifier(float chance) {
        return new SoulTalismanDropModifier(new LootItemCondition[]{}, chance);
    }

    private static LootParams deathParams(GameTestHelper helper, Entity entity) {
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().generic())
                .create(LootContextParamSets.ENTITY);
    }

    private static void assertDrop(GameTestHelper helper, IGlobalLootModifier modifier, LootParams params, long seed, int expected) {
        LootContext context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(null);
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
        ItemStack vanilla = new ItemStack(Items.DIRT, 3);
        loot.add(vanilla);
        ObjectArrayList<ItemStack> result = modifier.apply(loot, context);
        helper.assertTrue(result == loot && loot.get(0) == vanilla && vanilla.getCount() == 3, "Existing loot must be untouched");
        helper.assertTrue(countTalismans(loot) == expected && loot.size() == 1 + expected, "Unexpected talisman count");
    }

    private static int countTalismans(ObjectArrayList<ItemStack> loot) {
        return loot.stream().filter(stack -> stack.is(ModItems.SOUL_TALISMAN.get())).mapToInt(ItemStack::getCount).sum();
    }
}
