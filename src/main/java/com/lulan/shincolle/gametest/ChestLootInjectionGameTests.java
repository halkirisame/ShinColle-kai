package com.lulan.shincolle.gametest;

import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChestLootInjectionGameTests {

    private static final List<ResourceLocation> TARGET_TABLES = List.of(
            BuiltInLootTables.SPAWN_BONUS_CHEST,
            BuiltInLootTables.IGLOO_CHEST,
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.VILLAGE_WEAPONSMITH,
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.JUNGLE_TEMPLE,
            BuiltInLootTables.NETHER_BRIDGE,
            BuiltInLootTables.STRONGHOLD_LIBRARY,
            BuiltInLootTables.STRONGHOLD_CROSSING,
            BuiltInLootTables.STRONGHOLD_CORRIDOR,
            BuiltInLootTables.END_CITY_TREASURE
    );

    private ChestLootInjectionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void configuredTreasureTablesReceiveShinColleLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootParams params = chestParams(level);

        for (ResourceLocation tableId : TARGET_TABLES) {
            LootTable table = level.getServer().getLootData().getLootTable(tableId);
            boolean found = false;
            for (long seed = 0; seed < 256 && !found; seed++) {
                found = table.getRandomItems(params, seed).stream()
                        .anyMatch(ChestLootInjectionGameTests::isShinColleItem);
            }
            if (!found) {
                throw new AssertionError("No ShinColle loot was injected into " + tableId);
            }
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unrelatedTreasureTableDoesNotReceiveShinColleLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.SHIPWRECK_MAP);
        LootParams params = chestParams(level);

        for (long seed = 0; seed < 256; seed++) {
            if (table.getRandomItems(params, seed).stream()
                    .anyMatch(ChestLootInjectionGameTests::isShinColleItem)) {
                throw new AssertionError("ShinColle chest loot leaked into " + BuiltInLootTables.SHIPWRECK_MAP);
            }
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void injectedEggsAndEquipmentHaveUsableVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootParams params = chestParams(level);
        LootTable dungeon = level.getServer().getLootData().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        LootTable spawnBonus = level.getServer().getLootData().getLootTable(BuiltInLootTables.SPAWN_BONUS_CHEST);
        LootTable mineshaft = level.getServer().getLootData().getLootTable(BuiltInLootTables.ABANDONED_MINESHAFT);
        Set<String> eggVariants = new HashSet<>();
        Map<ResourceLocation, Set<Integer>> equipmentVariants = new HashMap<>();

        for (long seed = 0; seed < 1024; seed++) {
            for (ItemStack stack : dungeon.getRandomItems(params, seed)) {
                if (stack.getItem() instanceof ShipSpawnEgg) {
                    eggVariants.add(eggVariant(stack));
                }
            }
            for (ItemStack stack : spawnBonus.getRandomItems(params, seed)) {
                if (stack.getItem() instanceof ShipSpawnEgg) {
                    eggVariants.add(eggVariant(stack));
                }
            }

            for (ItemStack stack : mineshaft.getRandomItems(params, seed)) {
                if (stack.getItem() instanceof BasicEquip) {
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (!stack.hasTag() || !stack.getTag().contains(BasicEquip.TAG_EQUIP_META)) {
                        throw new AssertionError("Injected equipment has no EquipMeta: " + itemId);
                    }
                    if (EquipDataRegistry.server().byItemVariant(itemId, BasicEquip.getEquipMeta(stack)) == null) {
                        throw new AssertionError("Injected equipment variant is not registered: " + itemId
                                + "#" + BasicEquip.getEquipMeta(stack));
                    }
                    equipmentVariants.computeIfAbsent(itemId, ignored -> new HashSet<>())
                            .add(BasicEquip.getEquipMeta(stack));
                }
            }
        }

        Set<String> expectedEggVariants = Set.of("build:0", "build:1", "ship:0", "ship:15", "ship:46");
        if (!eggVariants.equals(expectedEggVariants)) {
            throw new AssertionError("Unexpected injected spawn egg variants: expected "
                    + expectedEggVariants + " but saw " + eggVariants);
        }
        for (ResourceLocation itemId : List.of(
                ForgeRegistries.ITEMS.getKey(ModItems.EQUIP_CANNON.get()),
                ForgeRegistries.ITEMS.getKey(ModItems.EQUIP_AIRPLANE.get()),
                ForgeRegistries.ITEMS.getKey(ModItems.EQUIP_TORPEDO.get()))) {
            Set<Integer> variants = equipmentVariants.getOrDefault(itemId, Set.of());
            if (variants.size() < 2) {
                throw new AssertionError("Injected equipment stayed on one variant: " + itemId + " " + variants);
            }
        }
        helper.succeed();
    }

    private static String eggVariant(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BuildType")) {
            return "build:" + tag.getByte("BuildType");
        }
        if (tag != null && tag.contains(ShipSpawnEgg.TAG_SHIP_CLASS)) {
            return "ship:" + tag.getInt(ShipSpawnEgg.TAG_SHIP_CLASS);
        }
        throw new AssertionError("Injected spawn egg has no construction or ship-class variant");
    }

    private static LootParams chestParams(ServerLevel level) {
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, level.getSharedSpawnPos().getCenter())
                .create(LootContextParamSets.CHEST);
    }

    private static boolean isShinColleItem(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null && Reference.MOD_ID.equals(itemId.getNamespace());
    }
}
