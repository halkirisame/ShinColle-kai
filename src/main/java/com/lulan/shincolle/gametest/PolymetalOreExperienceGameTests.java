package com.lulan.shincolle.gametest;

import com.lulan.shincolle.compat.curios.CuriosOreExperienceHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolymetalOreExperienceGameTests {
    private static final Vec3 ORE = new Vec3(1.5D, 2.5D, 1.5D);
    private static final int FORWARDED_EXPERIENCE = 37;

    private PolymetalOreExperienceGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void survivalMiningDropsOneToFourExperience(GameTestHelper helper) {
        run(helper, 0, false, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void fortuneOneMultipliesMiningExperienceByTwo(GameTestHelper helper) {
        run(helper, 1, false, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void fortuneTwoMultipliesMiningExperienceByThree(GameTestHelper helper) {
        run(helper, 2, false, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void fortuneThreeMultipliesMiningExperienceByFour(GameTestHelper helper) {
        run(helper, 3, false, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void silkTouchKeepsOreWithoutExperience(GameTestHelper helper) {
        run(helper, 0, true, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void unsuitablePickaxeDropsNeitherLootNorExperience(GameTestHelper helper) {
        run(helper, 0, false, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void creativeMiningDropsNeitherLootNorExperience(GameTestHelper helper) {
        run(helper, 0, false, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void curiosCompatibilityOnlyChangesUnharvestablePolymetalExperience(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        GameTestEntities.whenPositionsTicking(helper, () -> {
            CuriosOreExperienceHandler handler = new CuriosOreExperienceHandler();
            BlockEvent.BreakEvent unsuitableOre = boundaryEvent(helper, ModBlocks.POLYMETAL_ORE.get(), Items.WOODEN_PICKAXE);
            handler.onBlockBreak(unsuitableOre);
            helper.assertTrue(unsuitableOre.getExpToDrop() == 0 && !unsuitableOre.isCanceled(),
                    "Compatibility must zero unsuitable polymetal XP without canceling mining");

            BlockEvent.BreakEvent suitableOre = boundaryEvent(helper, ModBlocks.POLYMETAL_ORE.get(), Items.DIAMOND_PICKAXE);
            handler.onBlockBreak(suitableOre);
            helper.assertTrue(suitableOre.getExpToDrop() == FORWARDED_EXPERIENCE && !suitableOre.isCanceled(),
                    "Compatibility must preserve the incoming XP for a suitable tool");

            BlockEvent.BreakEvent otherOre = boundaryEvent(helper, Blocks.DIAMOND_ORE, Items.WOODEN_PICKAXE);
            handler.onBlockBreak(otherOre);
            helper.assertTrue(otherOre.getExpToDrop() == FORWARDED_EXPERIENCE && !otherOre.isCanceled(),
                    "Compatibility must preserve other blocks' XP even with an unsuitable tool");
            helper.assertTrue(helper.getLevel().getBlockState(otherOre.getPos()).is(Blocks.DIAMOND_ORE)
                            && otherOre.getPlayer().getMainHandItem().is(Items.WOODEN_PICKAXE),
                    "Compatibility must leave the fixture block and held tool alone");
            helper.succeed();
        }, ORE);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void curiosCompatibilityDoesNotReceiveCanceledBreakEvents(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BlockEvent.BreakEvent event = boundaryEvent(helper, ModBlocks.POLYMETAL_ORE.get(), Items.WOODEN_PICKAXE);
            event.setCanceled(true);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled(), "Compatibility must not clear an existing cancellation");
            event.setCanceled(false);
            helper.assertTrue(event.getExpToDrop() == FORWARDED_EXPERIENCE,
                    "Canceled events must retain stored XP when inspected without the cancellation mask");
            helper.succeed();
        }, ORE);
    }

    private static BlockEvent.BreakEvent boundaryEvent(GameTestHelper helper, Block block, Item pickaxe) {
        BlockPos pos = BlockPos.containing(helper.absoluteVec(ORE));
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ore_boundary"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pickaxe));
        BlockState state = block.defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, state);
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(block)
                        && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                        && player.getMainHandItem().is(pickaxe),
                "Boundary fixture must have its intended block, survival mode and pickaxe");
        helper.assertTrue(ForgeHooks.isCorrectToolForDrops(state, player) == (pickaxe == Items.DIAMOND_PICKAXE),
                "Boundary fixture harvest eligibility must match the selected pickaxe");
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(helper.getLevel(), pos, state, player);
        event.setExpToDrop(FORWARDED_EXPERIENCE);
        helper.assertTrue(event.getExpToDrop() == FORWARDED_EXPERIENCE && !event.isCanceled(),
                "Boundary fixture must start with the seeded incoming XP and no cancellation");
        return event;
    }

    private static void run(GameTestHelper helper, int fortune, boolean silk, boolean unsuitable, boolean creative) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BlockPos pos = BlockPos.containing(helper.absoluteVec(ORE));
            FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ore_miner"));
            player.moveTo(helper.absoluteVec(ORE).add(0D, 0D, 3D));
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "Positive control must use survival mining");
            MiningResult control = mine(helper, entities, player, pos, Blocks.DIAMOND_ORE);
            assertLoot(helper, control, Items.DIAMOND, 1, 1);
            helper.assertTrue(control.experience() >= 3 && control.experience() <= 7,
                    "Positive control must observe actual diamond ore XP, got " + control.experience());
            entities.close();

            ItemStack tool = new ItemStack(unsuitable ? Items.WOODEN_PICKAXE : Items.DIAMOND_PICKAXE);
            if (fortune > 0) tool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
            if (silk) tool.enchant(Enchantments.SILK_TOUCH, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            GameType mode = creative ? GameType.CREATIVE : GameType.SURVIVAL;
            player.setGameMode(mode);
            helper.assertTrue(player.gameMode.getGameModeForPlayer() == mode && player.isCreative() == creative,
                    "Mining mode must match the fixture");
            helper.assertTrue(player.getMainHandItem() == tool
                            && tool.is(unsuitable ? Items.WOODEN_PICKAXE : Items.DIAMOND_PICKAXE)
                            && tool.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE) == fortune
                            && tool.getEnchantmentLevel(Enchantments.SILK_TOUCH) == (silk ? 1 : 0),
                    "Held tool and enchantments must match the fixture");

            MiningResult result = mine(helper, entities, player, pos, ModBlocks.POLYMETAL_ORE.get());
            if (unsuitable || creative) {
                helper.assertTrue(result.items().isEmpty(), "Unsuitable or creative mining must not produce loot");
            } else if (silk) {
                assertLoot(helper, result, ModItems.POLYMETAL_ORE_BLOCK_ITEM.get(), 1, 1);
            } else {
                assertLoot(helper, result, ModItems.POLYMETAL_NODULE.get(), 1, 2 * (fortune + 1));
            }
            if (silk || unsuitable || creative) {
                helper.assertTrue(result.experience() == 0, "Mining must not drop XP, got " + result.experience());
            } else {
                int multiplier = fortune + 1;
                helper.assertTrue(result.experience() >= multiplier && result.experience() <= 4 * multiplier
                                && result.experience() % multiplier == 0,
                        "Polymetal mining XP must be (1..4) * " + multiplier + ", got " + result.experience());
            }
            helper.succeed();
        }, ORE);
    }

    private static MiningResult mine(GameTestHelper helper, GameTestEntities entities, FakePlayer player,
                                     BlockPos pos, Block block) {
        AABB bounds = new AABB(pos).inflate(0.5D);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, bounds).isEmpty()
                        && helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).isEmpty(),
                "Mining observation area must start without other loot or XP");
        BlockState state = block.defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, state);
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(block), "Ore fixture must exist before mining");
        boolean unsuitable = player.getMainHandItem().is(Items.WOODEN_PICKAXE);
        helper.assertTrue(ForgeHooks.isCorrectToolForDrops(state, player) == !unsuitable,
                "Fixture tool harvest eligibility must match its intended case");
        boolean destroyed = player.gameMode.destroyBlock(pos);
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds);
        List<ExperienceOrb> orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, bounds);
        items.forEach(entities::add);
        orbs.forEach(entities::add);
        helper.assertTrue(destroyed && helper.getLevel().getBlockState(pos).isAir(),
                "Server player mining must actually destroy the ore");
        int experience = 0;
        for (ExperienceOrb orb : orbs) {
            CompoundTag saved = new CompoundTag();
            orb.saveWithoutId(saved);
            experience += orb.getValue() * saved.getInt("Count");
        }
        return new MiningResult(experience, items.stream().map(item -> item.getItem().copy()).toList());
    }

    private static void assertLoot(GameTestHelper helper, MiningResult result, Item expected, int min, int max) {
        helper.assertTrue(!result.items().isEmpty() && result.items().stream().allMatch(stack -> stack.is(expected)),
                "Mining must retain the existing loot item " + expected);
        int count = result.items().stream().mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(count >= min && count <= max, "Mining loot count outside existing bounds: " + count);
    }

    private record MiningResult(int experience, List<ItemStack> items) {
    }
}
