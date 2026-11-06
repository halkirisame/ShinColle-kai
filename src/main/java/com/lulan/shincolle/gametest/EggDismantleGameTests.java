package com.lulan.shincolle.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.crafting.InventoryCraftingFake;
import com.lulan.shincolle.crafting.LargeRecipes;
import com.lulan.shincolle.crafting.RecipeEggDismantle;
import com.lulan.shincolle.crafting.SmallRecipes;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.init.ModRecipes;
import com.lulan.shincolle.item.GenericShipSpawnEgg;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dismantling a construction egg must never consume an egg that holds a saved ship. Every case goes
 * through the real crafting lookup, so the recipe data, the serializer and the recipe class are
 * exercised together.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EggDismantleGameTests {

    private static final Vec3 DEATH_SPAWN = new Vec3(1.5D, 2D, 1.5D);
    private static final ResourceLocation SMALL_ID = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "instant_con_mat");
    private static final ResourceLocation LARGE_ID = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "instant_con_mat_large");
    private static final ResourceLocation PLAIN_ID = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "instant_con_mat_plain");

    private EggDismantleGameTests() {
    }

    // ===== accepted eggs =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void smallConstructionEggReturnsEight(GameTestHelper helper) {
        ItemStack egg = constructionEgg(0);
        ItemStack hammer = hammer(7);
        for (int size : new int[]{2, 3}) {
            int last = size * size - 1;
            ItemStack[] first = {egg.copy(), hammer.copy()};
            assertDismantles(helper, grid(size, size, 0, first[0], last, first[1]), SMALL_ID, 8, first);
            ItemStack[] second = {egg.copy(), hammer.copy()};
            assertDismantles(helper, grid(size, size, last, second[0], 0, second[1]), SMALL_ID, 8, second);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeConstructionEggReturnsEight(GameTestHelper helper) {
        ItemStack egg = constructionEgg(1);
        ItemStack hammer = hammer(3);
        for (int size : new int[]{2, 3}) {
            int last = size * size - 1;
            ItemStack[] stacks = {egg.copy(), hammer.copy()};
            assertDismantles(helper, grid(size, size, 1, stacks[0], last, stacks[1]), LARGE_ID, 8, stacks);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipyardEggsWithShipClassRemainDismantlable(GameTestHelper helper) {
        for (int seed = 0; seed < 24; seed++) {
            ItemStack small = SmallRecipes.calculateResult(16 + seed, 20, 30, 40, true, RandomSource.create(seed));
            assertShipyardEgg(helper, small, 0);
            assertCraft(helper, small, SMALL_ID, 8);

            ItemStack large = LargeRecipes.calculateResult(100 + seed, 150, 200, 250, true, RandomSource.create(seed));
            assertShipyardEgg(helper, large, 1);
            assertCraft(helper, large, LARGE_ID, 8);
        }

        ItemStack named = SmallRecipes.calculateResult(16, 16, 16, 16, true, RandomSource.create(5));
        named.setHoverName(Component.literal("anvil name"));
        helper.assertTrue(named.getTag().contains("display", Tag.TAG_COMPOUND), "the anvil name must be stored");
        assertCraft(helper, named, SMALL_ID, 8);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void plainFriendlyAndHostileEggsReturnEight(GameTestHelper helper) {
        ShipSpawnEgg eggItem = (ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get();
        for (int shipClass : new int[]{ID.ShipClass.DDHibiki, ID.ShipClass.BBRE,
                ShipSpawnEgg.MOB_OFFSET + ID.ShipClass.DDShimakaze}) {
            helper.assertTrue(ShipSpawnEgg.getEntityTypeForClass(shipClass) != null, "fixture class is registered");
            ItemStack egg = eggItem.createStack(shipClass);
            helper.assertTrue(egg.getTag().size() == 1 && egg.getTag().get("ShipClass") instanceof IntTag,
                    "plain egg contains only an exact integer class");
            for (int size : new int[]{2, 3}) {
                ItemStack[] inputs = {egg.copy(), hammer(2)};
                assertDismantles(helper, grid(size, size, 0, inputs[0], size * size - 1, inputs[1]),
                        PLAIN_ID, 8, inputs);
            }
            egg.setHoverName(Component.literal("plain anvil name"));
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("lore"))));
            egg.getTag().getCompound("display").put("Lore", lore);
            assertCraft(helper, egg, PLAIN_ID, 8);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void plainEggSchemaRejectsSavedUnknownAndGenericData(GameTestHelper helper) {
        ItemStack plain = ((ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get()).createStack(ID.ShipClass.BBRE);
        for (String key : new String[]{"StateMinor", "ShipInventory", "owner", "ownername", "FutureKey",
                "Grudge", "Abyssium", "Ammo", "Polymetal", "ShipType", "ForgeCaps"}) {
            ItemStack egg = plain.copy();
            egg.getTag().putInt(key, 0);
            assertNoRecipe(helper, egg, "plain egg with " + key);
        }
        for (Tag badClass : new Tag[]{ByteTag.valueOf((byte) 15), IntTag.valueOf(-1), IntTag.valueOf(100000),
                IntTag.valueOf(ShipSpawnEgg.MOB_OFFSET + ID.ShipClass.BBRE), StringTag.valueOf("15")}) {
            CompoundTag tag = new CompoundTag();
            tag.put("ShipClass", badClass);
            assertNoRecipe(helper, withTag(tag), "plain malformed class " + badClass);
        }
        CompoundTag caps = plain.serializeNBT();
        CompoundTag payload = new CompoundTag();
        payload.putInt("stored", 1);
        caps.put("ForgeCaps", payload);
        assertNoRecipe(helper, ItemStack.of(caps), "plain serialized capabilities");

        ItemStack named = plain.copy();
        named.setHoverName(Component.literal("name"));
        named.getTag().getCompound("display").putInt("color", 1);
        assertNoRecipe(helper, named, "plain unknown display key");
        for (boolean hostile : new boolean[]{false, true}) {
            ItemStack generic = GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), hostile);
            assertNoRecipe(helper, generic, "plain generic egg");
            generic.getTag().putIntArray("StateMinor", new int[0]);
            assertNoRecipe(helper, generic, "saved generic egg");
        }
        helper.succeed();
    }

    // ===== rejected eggs =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedAndInventoryEggsNeverMatch(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                ItemStack dropped = deathEgg(helper, entities);
                helper.assertTrue(dropped.getTag().contains(CAPA_INVENTORY_KEY, Tag.TAG_COMPOUND),
                        "the real death egg must carry the ship inventory");
                assertNoRecipe(helper, dropped, "real death egg");
                for (int buildType = 0; buildType < 2; buildType++) {
                    ItemStack marked = dropped.copy();
                    marked.getTag().putByte("BuildType", (byte) buildType);
                    assertNoRecipe(helper, marked, "real death egg with BuildType " + buildType);
                }

                BasicEntityShip ship = ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel());
                entities.add(ship);
                ship.getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
                CompoundTag saved = new CompoundTag();
                CapaShipSavedValues.saveNBTData(saved, ship);
                saved.putInt("ShipClass", ID.ShipClass.DDHibiki);
                saved.put(CAPA_INVENTORY_KEY, ship.getCapaShipInventory().serializeNBT());
                ItemStack fromCapa = withTag(saved);
                assertNoRecipe(helper, fromCapa, "egg saved by the capability");
                fromCapa.getTag().putByte("BuildType", (byte) 0);
                assertNoRecipe(helper, fromCapa, "egg saved by the capability with a BuildType");

                for (String key : new String[]{"ShinColleCuriosEquip", "ShipExtProps", "ShipInventory",
                        "StateMinor", "StateFlag", "StateEmotion", "AttrsBonus", "owner", "ownername"}) {
                    for (Tag payload : new Tag[]{new ListTag(), new CompoundTag(), new IntArrayTag(new int[0]),
                            StringTag.valueOf(""), IntTag.valueOf(0)}) {
                        CompoundTag tag = plainTag(0);
                        tag.put(key, payload);
                        assertNoRecipe(helper, withTag(tag), "empty or broken " + key + " " + payload.getClass().getSimpleName());
                    }
                }
                helper.succeed();
            } catch (RuntimeException | AssertionError failure) {
                helper.fail("Fixture failed: " + failure);
            }
        }, DEATH_SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void malformedOrUnmarkedEggsNeverMatch(GameTestHelper helper) {
        assertNoRecipe(helper, new ItemStack(ModItems.SHIP_SPAWN_EGG.get()), "no tag");
        assertNoRecipe(helper, withTag(new CompoundTag()), "empty tag");

        CompoundTag shipTypeOnly = new CompoundTag();
        shipTypeOnly.putInt("ShipType", ID.ShipType.DESTROYER);
        assertNoRecipe(helper, withTag(shipTypeOnly), "legacy ShipType only");
        CompoundTag shipTypeAndBuild = plainTag(0);
        shipTypeAndBuild.putInt("ShipType", ID.ShipType.DESTROYER);
        assertNoRecipe(helper, withTag(shipTypeAndBuild), "legacy ShipType next to BuildType");

        for (Tag wrongType : new Tag[]{IntTag.valueOf(0), IntTag.valueOf(1), LongTag.valueOf(0L),
                net.minecraft.nbt.FloatTag.valueOf(1F), StringTag.valueOf("0"), ByteTag.valueOf((byte) -1),
                ByteTag.valueOf((byte) 2), ByteTag.valueOf((byte) 127)}) {
            CompoundTag tag = new CompoundTag();
            tag.put("BuildType", wrongType);
            assertNoRecipe(helper, withTag(tag), "BuildType " + wrongType);
        }

        for (Tag badClass : new Tag[]{ByteTag.valueOf((byte) 4), IntTag.valueOf(-1), IntTag.valueOf(100000),
                IntTag.valueOf(ShipSpawnEgg.MOB_OFFSET + ID.ShipClass.DDShimakaze), StringTag.valueOf("4")}) {
            CompoundTag tag = plainTag(0);
            tag.put("ShipClass", badClass);
            assertNoRecipe(helper, withTag(tag), "ShipClass " + badClass);
        }

        CompoundTag unknownRoot = plainTag(1);
        unknownRoot.putString("FutureKey", "x");
        assertNoRecipe(helper, withTag(unknownRoot), "unknown root key");

        CompoundTag unknownDisplay = plainTag(0);
        CompoundTag display = new CompoundTag();
        display.putInt("color", 5);
        unknownDisplay.put("display", display);
        assertNoRecipe(helper, withTag(unknownDisplay), "unknown display child");

        CompoundTag badName = plainTag(0);
        CompoundTag nameDisplay = new CompoundTag();
        nameDisplay.putInt("Name", 5);
        badName.put("display", nameDisplay);
        assertNoRecipe(helper, withTag(badName), "non-string name");

        CompoundTag badMaterial = plainTag(0);
        badMaterial.putByte("Grudge", (byte) 5);
        assertNoRecipe(helper, withTag(badMaterial), "material of the wrong type");
        CompoundTag negativeMaterial = plainTag(0);
        negativeMaterial.putInt("Ammo", -1);
        assertNoRecipe(helper, withTag(negativeMaterial), "negative material");

        ItemStack withCaps = constructionEgg(0);
        CompoundTag caps = withCaps.serializeNBT();
        CompoundTag payload = new CompoundTag();
        payload.putInt("stored", 1);
        caps.put("ForgeCaps", payload);
        assertNoRecipe(helper, ItemStack.of(caps), "serialized capability payload");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dismantlingRequiresExactlyOneEggSlotAndHammer(GameTestHelper helper) {
        ItemStack egg = constructionEgg(0);
        ItemStack hammer = hammer(0);
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy()), "egg alone");
        assertNoRecipe(helper, grid(3, 3, 0, hammer.copy()), "hammer alone");
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy(), 1, egg.copy(), 2, hammer.copy()), "two eggs");
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy(), 1, hammer.copy(), 2, hammer.copy()), "two hammers");
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy(), 1, hammer.copy(), 8, new ItemStack(Items.STICK)), "extra item");
        ItemStack stackedEgg = egg.copy();
        stackedEgg.setCount(2);
        ItemStack[] stacked = {stackedEgg, hammer.copy()};
        assertDismantles(helper, grid(3, 3, 0, stacked[0], 4, stacked[1]), SMALL_ID, 8, stacked);
        ItemStack stackedHammer = hammer.copy();
        stackedHammer.setCount(2);
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy(), 4, stackedHammer), "hammer stack of two");

        ItemStack[] ok = {egg.copy(), hammer.copy()};
        assertDismantles(helper, grid(3, 3, 4, ok[0], 8, ok[1]), SMALL_ID, 8, ok);
        helper.succeed();
    }

    // ===== hammer and taking the result =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hammerRemainderPreservesDamageAndBreaksAtLimit(GameTestHelper helper) {
        for (int damage : new int[]{0, 7, 18, 19}) {
            ItemStack hammer = hammer(damage);
            hammer.setHoverName(Component.literal("my hammer"));
            ItemStack egg = constructionEgg(0);
            ItemStack hammerBefore = hammer.copy();
            ItemStack eggBefore = egg.copy();
            CraftingContainer grid = grid(2, 2, 1, egg, 2, hammer);
            NonNullList<ItemStack> remaining = helper.getLevel().getRecipeManager()
                    .getRemainingItemsFor(RecipeType.CRAFTING, grid, helper.getLevel());

            helper.assertTrue(remaining.size() == 4, "remainder list covers the grid");
            helper.assertTrue(remaining.get(0).isEmpty() && remaining.get(1).isEmpty(), "the egg leaves no remainder");
            ItemStack left = remaining.get(2);
            if (damage == 19) {
                helper.assertTrue(left.isEmpty(), "the hammer breaks at the limit");
            } else {
                helper.assertTrue(left.is(ModItems.KAITAI_HAMMER.get()) && left.getDamageValue() == damage + 1,
                        "hammer damage " + damage + " became " + left.getDamageValue());
                helper.assertTrue(left.getHoverName().getString().equals("my hammer"), "hammer name is kept");
            }
            helper.assertTrue(ItemStack.matches(hammer, hammerBefore) && ItemStack.matches(egg, eggBefore),
                    "reading the remainder must not change the inputs");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void takingNoResultPreservesSavedEggAndHammer(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Player player = FakePlayerFactory.getMinecraft(helper.getLevel());
                ItemStack saved = deathEgg(helper, entities);
                ItemStack hammer = hammer(4);
                ItemStack savedBefore = saved.copy();
                ItemStack hammerBefore = hammer.copy();

                CraftingMenu menu = new CraftingMenu(77, player.getInventory(),
                        ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
                menu.getSlot(1).set(saved);
                menu.getSlot(2).set(hammer);
                helper.assertTrue(menu.getSlot(0).getItem().isEmpty(),
                        "a saved egg must not produce a result: " + menu.getSlot(0).getItem());
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().isEmpty(), "nothing may be taken");
                helper.assertTrue(ItemStack.matches(menu.getSlot(1).getItem(), savedBefore),
                        "the saved egg must be untouched");
                helper.assertTrue(ItemStack.matches(menu.getSlot(2).getItem(), hammerBefore),
                        "the hammer must be untouched");

                // The same menu does dismantle a plain egg, so the empty result above is the recipe's doing.
                menu.getSlot(1).set(constructionEgg(0));
                ItemStack result = menu.getSlot(0).getItem();
                helper.assertTrue(result.is(ModItems.INSTANT_CON_MAT.get()) && result.getCount() == 8,
                        "a plain egg gives eight: " + result);
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().getCount() == 8, "taking gives eight");
                helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "the plain egg is consumed");
                helper.assertTrue(menu.getSlot(2).getItem().getDamageValue() == 5, "the hammer wears once");
                menu.setCarried(ItemStack.EMPTY);
                helper.succeed();
            } catch (RuntimeException | AssertionError failure) {
                helper.fail("Fixture failed: " + failure);
            }
        }, DEATH_SPAWN);
    }

    // ===== serializer =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void stackedEggCraftingConsumesOnePerResult(GameTestHelper helper) {
        Player player = isolatedPlayer(helper);
        for (ItemStack template : new ItemStack[]{constructionEgg(0), constructionEgg(1),
                ((ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get()).createStack(ID.ShipClass.BBRE)}) {
            player.getInventory().clearContent();
            ItemStack eggs = template.copy();
            eggs.setCount(16);
            ItemStack before = eggs.copy();
            CraftingMenu menu = new CraftingMenu(78, player.getInventory(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
            menu.getSlot(1).set(eggs);
            menu.getSlot(2).set(hammer(0));
            helper.assertTrue(menu.getSlot(0).getItem().getCount() == 8, "stacked eggs must yield eight");
            helper.assertTrue(ItemStack.matches(eggs, before), "preview must not change the egg stack");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(ModItems.INSTANT_CON_MAT.get())
                    && menu.getCarried().getCount() == 8, "one take returns eight");
            helper.assertTrue(menu.getSlot(1).getItem().getCount() == 15
                    && ItemStack.isSameItemSameTags(menu.getSlot(1).getItem(), before), "one take consumes one egg");
            helper.assertTrue(menu.getSlot(2).getItem().getDamageValue() == 1, "one take wears the hammer once");
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "shift crafting consumes the remaining eggs");
            helper.assertTrue(menu.getSlot(2).getItem().getDamageValue() == 16, "shift crafting wears once per egg");
            helper.assertTrue(player.getInventory().countItem(ModItems.INSTANT_CON_MAT.get()) == 15 * 8,
                    "shift crafting must neither lose nor duplicate output");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void eggStorageMergesOnlyMatchingTagsUpToSixteen(GameTestHelper helper) {
        Player player = isolatedPlayer(helper);
        for (ItemStack template : new ItemStack[]{constructionEgg(0), constructionEgg(1),
                ((ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get()).createStack(ID.ShipClass.BBRE),
                GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), false)}) {
            helper.assertTrue(template.getMaxStackSize() == 16, "egg stack limit must be sixteen");
            player.getInventory().clearContent();
            ItemStack first = template.copy();
            first.setCount(15);
            player.getInventory().setItem(0, first);
            ItemStack incoming = template.copy();
            incoming.setCount(4);
            helper.assertTrue(player.getInventory().add(incoming) && incoming.isEmpty(), "inventory accepts eggs");
            helper.assertTrue(player.getInventory().getItem(0).getCount() == 16
                    && player.getInventory().getItem(1).getCount() == 3, "inventory respects sixteen limit");

            ItemStack named = template.copy();
            named.setHoverName(Component.literal("different tag"));
            player.getInventory().add(named);
            helper.assertTrue(player.getInventory().getItem(2).hasCustomHoverName()
                    && player.getInventory().getItem(1).getCount() == 3, "different tags stay separate");
            SimpleContainer chest = new SimpleContainer(27);
            ChestMenu menu = ChestMenu.threeRows(79, player.getInventory(), chest);
            menu.clicked(54, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty() && chest.getItem(0).getCount() == 16,
                    "shift move to chest preserves the full stack");
            ItemStack overflow = HopperBlockEntity.addItem(null, chest, template.copy(), Direction.UP);
            helper.assertTrue(overflow.isEmpty() && chest.getItem(0).getCount() == 16
                    && chest.getItem(1).getCount() == 1, "hopper obeys the item stack limit");
        }
        helper.assertTrue(!ItemStack.isSameItemSameTags(constructionEgg(0), constructionEgg(1)),
                "construction sizes do not merge");
        ItemStack friendly = GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), false);
        ItemStack hostile = GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), true);
        helper.assertTrue(!ItemStack.isSameItemSameTags(friendly, hostile), "generic sides do not merge");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggStacksPreserveDistinctContentsAndNeverCraft(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                ItemStack first = deathEgg(helper, entities);
                BasicEntityShip otherShip = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
                otherShip.setShipLevel(12, false);
                otherShip.getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.EMERALD, 3));
                CompoundTag saved = new CompoundTag();
                CapaShipSavedValues.saveNBTData(saved, otherShip);
                saved.putInt("ShipClass", ID.ShipClass.DDHibiki);
                saved.put(CAPA_INVENTORY_KEY, otherShip.getCapaShipInventory().serializeNBT());
                ItemStack second = withTag(saved);
                helper.assertTrue(!ItemStack.isSameItemSameTags(first, second), "different ships' contents stay separate");
                Player player = isolatedPlayer(helper);
                for (ItemStack item : new ItemStack[]{first.copy(), second.copy()}) player.getInventory().add(item);
                helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), first)
                        && ItemStack.matches(player.getInventory().getItem(1), second), "inventory keeps both payloads");
                SimpleContainer chest = new SimpleContainer(27);
                ChestMenu chestMenu = ChestMenu.threeRows(80, player.getInventory(), chest);
                chestMenu.clicked(54, 0, ClickType.QUICK_MOVE, player);
                chestMenu.clicked(55, 0, ClickType.QUICK_MOVE, player);
                helper.assertTrue(ItemStack.matches(chest.getItem(0), first)
                        && ItemStack.matches(chest.getItem(1), second), "chest shift move keeps distinct payloads");
                ItemStack copy = first.copy();
                copy.setCount(2);
                helper.assertTrue(HopperBlockEntity.addItem(null, chest, copy, Direction.UP).isEmpty(), "hopper inserts copies");
                helper.assertTrue(chest.getItem(0).getCount() == 3
                        && ItemStack.isSameItemSameTags(chest.getItem(0), first)
                        && ItemStack.matches(chest.getItem(1), second), "only identical saved payloads merge");
                helper.assertTrue(ShipSpawnEgg.readStoredInventory(chest.getItem(0))
                        .equipment().get(0).getCount() == 7, "stacked tooltip contents describe one egg, not the stack");
                for (ItemStack egg : new ItemStack[]{first, second, chest.getItem(0)}) {
                    assertNoRecipe(helper, egg, "saved egg, including an identical stack");
                    for (int buildType : new int[]{0, 1}) {
                        ItemStack marked = egg.copy();
                        marked.getTag().putByte("BuildType", (byte) buildType);
                        assertNoRecipe(helper, marked, "saved stack with construction marker");
                    }
                }
                CraftingMenu menu = new CraftingMenu(81, player.getInventory(),
                        ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
                ItemStack savedStack = chest.getItem(0).copy();
                menu.getSlot(1).set(savedStack.copy());
                menu.getSlot(2).set(hammer(3));
                menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                helper.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(menu.getSlot(1).getItem(), savedStack)
                        && menu.getSlot(2).getItem().getDamageValue() == 3, "saved stack cannot be consumed");
                helper.succeed();
            }
        }, DEATH_SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void stackedEggUseSpawnsExactlyOneShip(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Player player = isolatedPlayer(helper);
                player.getAbilities().instabuild = false;
                ShipSpawnEgg eggItem = (ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get();
                for (ItemStack egg : new ItemStack[]{eggItem.createStack(ID.ShipClass.DDHibiki),
                        eggItem.createStack(ShipSpawnEgg.MOB_OFFSET + ID.ShipClass.DDShimakaze),
                        GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), false),
                        GenericShipSpawnEgg.create(ShipDefinitionId.parse("shincolle_kai:kashima"), true)}) {
                    egg.setCount(3);
                    ItemStack before = egg.copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, egg);
                    BlockPos clicked = helper.absolutePos(new BlockPos(1, 1, 1));
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false);
                    AABB area = new AABB(clicked.above()).inflate(0.75D);
                    var previous = helper.getLevel().getEntitiesOfClass(Mob.class, area);
                    try {
                        helper.assertTrue(egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                                "stacked egg must spawn successfully");
                        helper.assertTrue(egg.getCount() == 2 && ItemStack.isSameItemSameTags(egg, before),
                                "spawn consumes exactly one and keeps the remaining payload");
                        var spawned = helper.getLevel().getEntitiesOfClass(Mob.class, area,
                                entity -> !previous.contains(entity));
                        helper.assertTrue(spawned.size() == 1, "one use must create exactly one ship");
                    } finally {
                        helper.getLevel().getEntitiesOfClass(Mob.class, area, entity -> !previous.contains(entity))
                                .forEach(entity -> { entities.add(entity); entity.discard(); });
                    }
                }
                helper.succeed();
            }
        }, DEATH_SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggPickupKeepsPartialStackWhenInventoryIsFull(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                Player player = isolatedPlayer(helper);
                player.getAbilities().instabuild = false;
                ItemStack egg = deathEgg(helper, entities);
                egg.getTag().putString("owner", player.getUUID().toString());
                egg.getTag().remove("ownername");
                for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                }
                ItemStack existing = egg.copy();
                existing.setCount(15);
                player.getInventory().setItem(0, existing);
                ItemStack drop = egg.copy();
                drop.setCount(2);
                BasicEntityItem item = entities.add(ModEntities.BASIC_ENTITY_ITEM.get().create(helper.getLevel()));
                item.setEntityItemStack(drop);
                for (int tick = 0; tick < 10; tick++) item.tick();
                item.playerTouch(player);
                helper.assertTrue(player.getInventory().getItem(0).getCount() == 16, "pickup fills only available room");
                helper.assertTrue(!item.isRemoved() && item.getEntityItem().getCount() == 1
                        && ItemStack.isSameItemSameTags(item.getEntityItem(), egg), "leftover egg and payload stay on ground");
                player.getInventory().setItem(1, ItemStack.EMPTY);
                item.playerTouch(player);
                helper.assertTrue(item.isRemoved() && player.getInventory().getItem(1).getCount() == 1
                        && ItemStack.isSameItemSameTags(player.getInventory().getItem(1), egg), "remaining egg can be picked up later");
                helper.succeed();
            }
        }, DEATH_SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dismantleSerializerRoundTripsAndRejectsBadSize(GameTestHelper helper) {
        RecipeEggDismantle.Serializer serializer = new RecipeEggDismantle.Serializer();
        for (String name : new String[]{"small", "large", "plain"}) {
            JsonObject json = JsonParser.parseString(
                    "{\"type\":\"shincolle_kai:egg_dismantle\",\"category\":\"misc\",\"build_type\":\"" + name + "\"}")
                    .getAsJsonObject();
            RecipeEggDismantle recipe = serializer.fromJson(SMALL_ID, json);
            int expected = 8;
            helper.assertTrue(recipe.getResultItem(helper.getLevel().registryAccess()).getCount() == expected,
                    name + " result count");

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            serializer.toNetwork(buf, recipe);
            RecipeEggDismantle copy = serializer.fromNetwork(SMALL_ID, buf);
            helper.assertTrue(copy.size() == recipe.size() && copy.category() == recipe.category(),
                    name + " must survive the network round trip");
            helper.assertTrue(!buf.isReadable(), "the network form must be read completely");
        }

        for (String bad : new String[]{"{\"build_type\":\"medium\"}", "{\"build_type\":\"\"}", "{}",
                "{\"build_type\":5}", "{\"build_type\":\"SMALL\"}"}) {
            boolean threw = false;
            try {
                serializer.fromJson(SMALL_ID, JsonParser.parseString(bad).getAsJsonObject());
            } catch (RuntimeException expected) {
                threw = true;
            }
            helper.assertTrue(threw, "a load error is required for " + bad);
        }

        for (ResourceLocation id : new ResourceLocation[]{SMALL_ID, LARGE_ID, PLAIN_ID}) {
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded =
                    helper.getLevel().getRecipeManager().byKey(id);
            helper.assertTrue(loaded.isPresent() && loaded.get().getSerializer() == ModRecipes.EGG_DISMANTLE.get(),
                    id + " must be a dismantle recipe");
        }
        helper.succeed();
    }

    // ===== helpers =====

    private static final String CAPA_INVENTORY_KEY = com.lulan.shincolle.capability.CapaShipInventory.InvName;

    private static Player isolatedPlayer(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EggStackFixture"));
    }

    private static ItemStack constructionEgg(int buildType) {
        return ((ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get()).createConstructionStack(buildType);
    }

    private static ItemStack hammer(int damage) {
        ItemStack hammer = new ItemStack(ModItems.KAITAI_HAMMER.get());
        hammer.setDamageValue(damage);
        return hammer;
    }

    private static CompoundTag plainTag(int buildType) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("BuildType", (byte) buildType);
        return tag;
    }

    private static ItemStack withTag(CompoundTag tag) {
        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        egg.setTag(tag);
        return egg;
    }

    /** A grid of the given size; arguments after the size alternate slot index and stack. */
    private static CraftingContainer grid(int width, int height, Object... slotAndStack) {
        InventoryCraftingFake grid = new InventoryCraftingFake(width, height);
        for (int i = 0; i < slotAndStack.length; i += 2) {
            grid.setItem((Integer) slotAndStack[i], (ItemStack) slotAndStack[i + 1]);
        }
        return grid;
    }

    private static Optional<CraftingRecipe> lookup(GameTestHelper helper, CraftingContainer grid) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
    }

    private static void assertNoRecipe(GameTestHelper helper, ItemStack egg, String label) {
        assertNoRecipe(helper, grid(2, 2, 0, egg.copy(), 3, hammer(0)), label);
        assertNoRecipe(helper, grid(3, 3, 4, egg.copy(), 8, hammer(0)), label);
    }

    private static void assertNoRecipe(GameTestHelper helper, CraftingContainer grid, String label) {
        Optional<CraftingRecipe> found = lookup(helper, grid);
        helper.assertTrue(found.isEmpty(), label + " must not craft, but matched "
                + found.map(recipe -> recipe.getId().toString()).orElse(""));
    }

    /** Looks the grid up, checks the recipe and result, and checks the inputs were not changed. */
    private static void assertDismantles(GameTestHelper helper, CraftingContainer grid, ResourceLocation id, int count,
                                         ItemStack[] inputs) {
        ItemStack[] before = new ItemStack[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            before[i] = inputs[i].copy();
        }
        Optional<CraftingRecipe> found = lookup(helper, grid);
        helper.assertTrue(found.isPresent(), "the grid must craft");
        helper.assertTrue(found.get().getId().equals(id), "expected " + id + " but got " + found.get().getId());
        long matching = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(recipe -> recipe.matches(grid, helper.getLevel())).count();
        helper.assertTrue(matching == 1, "the egg must match exactly one recipe, got " + matching);
        ItemStack result = found.get().assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.INSTANT_CON_MAT.get()) && result.getCount() == count,
                "expected " + count + " construction material but got " + result);
        helper.assertTrue(result.getTag() == null, "the result must carry no egg data");
        for (int i = 0; i < inputs.length; i++) {
            helper.assertTrue(ItemStack.matches(inputs[i], before[i]), "assemble changed input " + i);
        }
    }

    private static void assertCraft(GameTestHelper helper, ItemStack egg, ResourceLocation id, int count) {
        ItemStack[] stacks = {egg.copy(), hammer(2)};
        assertDismantles(helper, grid(3, 3, 0, stacks[0], 5, stacks[1]), id, count, stacks);
    }

    private static void assertShipyardEgg(GameTestHelper helper, ItemStack egg, int buildType) {
        CompoundTag tag = egg.getTag();
        helper.assertTrue(tag != null && tag.get("ShipClass") instanceof IntTag
                        && tag.get("BuildType") instanceof ByteTag && tag.getByte("BuildType") == buildType
                        && tag.get("Grudge") instanceof IntTag && tag.get("Abyssium") instanceof IntTag
                        && tag.get("Ammo") instanceof IntTag && tag.get("Polymetal") instanceof IntTag,
                "the shipyard egg does not have the expected shape: " + tag);
    }

    /** Lets a ship holding cargo reach the end of its death and returns the egg it drops. */
    private static ItemStack deathEgg(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
        Vec3 position = helper.absoluteVec(DEATH_SPAWN);
        ship.moveTo(position.x, position.y, position.z, 0F, 0F);
        ship.setNoAi(true);
        ship.getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Could not add the dying ship");
        helper.assertTrue(ship.getStateFlag(ID.F.CanDrop), "The dying ship must be allowed to drop its egg");

        ship.deathTime = ConfigHandler.deathTime() - 1;
        try {
            Method tickDeath = BasicEntityShip.class.getDeclaredMethod("tickDeath");
            tickDeath.setAccessible(true);
            tickDeath.invoke(ship);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not advance the ship's death", failure);
        }
        helper.assertTrue(ship.isRemoved(), "The ship must be gone once its death ends");

        List<BasicEntityItem> eggs = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                new AABB(BlockPos.containing(position)).inflate(1.5D),
                item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
        helper.assertTrue(eggs.size() == 1, "Expected one death egg, found " + eggs.size());
        BasicEntityItem item = entities.add(eggs.get(0));
        ItemStack copy = item.getEntityItem().copy();
        item.discard();
        helper.assertTrue(copy.getTag() != null && copy.getTag().contains("StateMinor", Tag.TAG_INT_ARRAY),
                "The death egg does not hold the ship's saved state");
        return copy;
    }
}
