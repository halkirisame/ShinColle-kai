package com.lulan.shincolle.datagen.server;

import com.lulan.shincolle.loot.AddItemModifier;
import com.lulan.shincolle.loot.InjectLootTableModifier;
import com.lulan.shincolle.loot.SoulTalismanDropModifier;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;

public class ShinColleGlobalLootModifierProvider extends GlobalLootModifierProvider {
    public static final float SOUL_TALISMAN_DROP_CHANCE = 1F / 3000F;

    public ShinColleGlobalLootModifierProvider(PackOutput output) {
        super(output, Reference.MOD_ID);
    }

    @Override
    protected void start() {
        add("add_drop", new AddItemModifier(new LootItemCondition[]{}));
        add("soul_talisman_drop", new SoulTalismanDropModifier(new LootItemCondition[]{}, SOUL_TALISMAN_DROP_CHANCE));

        /*
         * 1.10.2 ChestLootTable:
         * "else if (host.equals(LootTableList.CHESTS_SIMPLE_DUNGEON))"
         * "    addNewPoolToTable(table, 2);"
         * Strongholds likewise shared one source across library, crossing, and corridor.
         */
        addChestInjection("spawn_bonus", BuiltInLootTables.SPAWN_BONUS_CHEST, "spawn_bonus");
        addChestInjection("igloo", BuiltInLootTables.IGLOO_CHEST, "igloo");
        addChestInjection("dungeon", BuiltInLootTables.SIMPLE_DUNGEON, "dungeon");
        addChestInjection("village_weaponsmith", BuiltInLootTables.VILLAGE_WEAPONSMITH,
                "village_blacksmith");
        addChestInjection("mineshaft", BuiltInLootTables.ABANDONED_MINESHAFT, "mineshaft");
        addChestInjection("pyramid", BuiltInLootTables.DESERT_PYRAMID, "pyramid");
        addChestInjection("jungle_temple", BuiltInLootTables.JUNGLE_TEMPLE, "jungle_temple");
        addChestInjection("nether_bridge", BuiltInLootTables.NETHER_BRIDGE, "nether_bridge");
        addChestInjection("stronghold_library", BuiltInLootTables.STRONGHOLD_LIBRARY, "stronghold");
        addChestInjection("stronghold_crossing", BuiltInLootTables.STRONGHOLD_CROSSING, "stronghold");
        addChestInjection("stronghold_corridor", BuiltInLootTables.STRONGHOLD_CORRIDOR, "stronghold");
        addChestInjection("end_city", BuiltInLootTables.END_CITY_TREASURE, "end_city");

        // Structures added after 1.10.2.
        addChestInjection("ancient_city", BuiltInLootTables.ANCIENT_CITY, "stronghold");
        addChestInjection("woodland_mansion", BuiltInLootTables.WOODLAND_MANSION, "stronghold");
        addChestInjection("buried_treasure", BuiltInLootTables.BURIED_TREASURE, "jungle_temple");
        addChestInjection("bastion_treasure", BuiltInLootTables.BASTION_TREASURE, "nether_bridge");
        addChestInjection("shipwreck_treasure", BuiltInLootTables.SHIPWRECK_TREASURE, "ocean_treasure");
        addChestInjection("shipwreck_supply", BuiltInLootTables.SHIPWRECK_SUPPLY, "ocean_supply");
        addChestInjection("underwater_ruin_big", BuiltInLootTables.UNDERWATER_RUIN_BIG, "ocean_supply");
        addChestInjection("underwater_ruin_small", BuiltInLootTables.UNDERWATER_RUIN_SMALL, "ocean_supply");
    }

    private void addChestInjection(String name, ResourceLocation target, String source) {
        LootItemCondition[] conditions = {LootTableIdCondition.builder(target).build()};
        add("inject_" + name, new InjectLootTableModifier(conditions,
                ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "inject/" + source)));
    }
}
