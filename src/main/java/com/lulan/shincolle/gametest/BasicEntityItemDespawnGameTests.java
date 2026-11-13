package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BasicEntityItemDespawnGameTests {

    private BasicEntityItemDespawnGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void untaggedShipyardSurvivesDisabledDespawn(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            int previous = ConfigHandler.despawnEgg();
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                ConfigHandler.COMMON.despawnEgg.set(-1);
                helper.assertTrue(ConfigHandler.despawnEgg() == -1, "Despawn disabling fixture was not applied.");
                BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
                helper.getLevel().setBlock(pos, ModBlocks.SMALL_SHIPYARD.get().defaultBlockState(), 3);
                helper.assertTrue(helper.getLevel().destroyBlock(pos, true), "Empty shipyard was not destroyed.");
                List<BasicEntityItem> drops = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                        new AABB(pos).inflate(2), drop -> drop.getEntityItem().is(ModBlocks.SMALL_SHIPYARD.get().asItem()));
                drops.forEach(entities::add);
                helper.assertTrue(drops.size() == 1, "Expected exactly one protected shipyard drop.");
                BasicEntityItem drop = drops.get(0);
                GameTestEntities.assertRegistered(helper, drop);
                helper.assertTrue(!drop.getEntityItem().hasTag(), "Empty shipyard drop unexpectedly has NBT.");
                // ServerLevel increments tickCount before calling an entity's tick.
                for (int age = 1; age <= 12; age++) {
                    drop.tickCount = age;
                    drop.tick();
                    helper.assertTrue(!drop.isRemoved(), "Disabled despawn removed the drop at age " + age);
                }
                drop.tickCount = 1728001;
                drop.tick();
                helper.assertTrue(!drop.isRemoved(), "Disabled despawn removed an old drop.");
                helper.assertTrue(drop.getEntityItem().getCount() == 1 && !drop.getEntityItem().hasTag(),
                        "Disabled despawn must retain the original untagged item.");
            } finally {
                ConfigHandler.COMMON.despawnEgg.set(previous);
            }
            helper.succeed();
        }, new Vec3(2.5, 2.5, 2.5));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void finiteDespawnRetainsBoundaryAndTaggedProtection(GameTestHelper helper) {
        int previous = ConfigHandler.despawnEgg();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (int expiry : new int[] {0, 2}) {
                ConfigHandler.COMMON.despawnEgg.set(expiry);
                helper.assertTrue(ConfigHandler.despawnEgg() == expiry, "Finite despawn fixture was not applied.");
                BasicEntityItem untagged = entities.add(new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(),
                        helper.getLevel(), 0, 0, 0, new ItemStack(ModBlocks.SMALL_SHIPYARD.get())));
                ItemStack taggedStack = new ItemStack(ModBlocks.SMALL_SHIPYARD.get());
                taggedStack.getOrCreateTag().putInt("Fuel", 123);
                BasicEntityItem tagged = entities.add(new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(),
                        helper.getLevel(), 0, 0, 0, taggedStack));
                untagged.tickCount = expiry;
                untagged.tick();
                helper.assertTrue(!untagged.isRemoved(), "Drop expired at the inclusive lifetime boundary.");
                untagged.tickCount = expiry + 1;
                untagged.tick();
                helper.assertTrue(untagged.isRemoved(), "Untagged drop survived beyond a finite lifetime.");
                tagged.tickCount = expiry + 1;
                tagged.tick();
                helper.assertTrue(!tagged.isRemoved() && tagged.getEntityItem().getTag().getInt("Fuel") == 123,
                        "Finite expiry must preserve tagged drops and their contents.");
            }
        } finally {
            ConfigHandler.COMMON.despawnEgg.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void disabledDespawnStillRemovesEmptyStack(GameTestHelper helper) {
        int previous = ConfigHandler.despawnEgg();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ConfigHandler.COMMON.despawnEgg.set(-1);
            helper.assertTrue(ConfigHandler.despawnEgg() == -1, "Despawn disabling fixture was not applied.");
            BasicEntityItem empty = entities.add(new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(), helper.getLevel()));
            helper.assertTrue(empty.getEntityItem().isEmpty(), "Empty-stack fixture was not applied.");
            empty.tickCount = 1;
            empty.tick();
            helper.assertTrue(empty.isRemoved(), "Disabling expiry must not retain invalid empty drops.");
        } finally {
            ConfigHandler.COMMON.despawnEgg.set(previous);
        }
        helper.succeed();
    }
}
