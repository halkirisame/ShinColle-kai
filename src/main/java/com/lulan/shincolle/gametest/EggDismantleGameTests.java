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
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

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
    public static void largeConstructionEggReturnsSixtyFour(GameTestHelper helper) {
        ItemStack egg = constructionEgg(1);
        ItemStack hammer = hammer(3);
        for (int size : new int[]{2, 3}) {
            int last = size * size - 1;
            ItemStack[] stacks = {egg.copy(), hammer.copy()};
            assertDismantles(helper, grid(size, size, 1, stacks[0], last, stacks[1]), LARGE_ID, 64, stacks);
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
            assertCraft(helper, large, LARGE_ID, 64);
        }

        ItemStack named = SmallRecipes.calculateResult(16, 16, 16, 16, true, RandomSource.create(5));
        named.setHoverName(Component.literal("anvil name"));
        helper.assertTrue(named.getTag().contains("display", Tag.TAG_COMPOUND), "the anvil name must be stored");
        assertCraft(helper, named, SMALL_ID, 8);
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
        assertNoRecipe(helper, ((ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get()).createStack(ID.ShipClass.DDHibiki),
                "specific ship egg");

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
    public static void dismantlingRequiresExactlyOneEggAndHammer(GameTestHelper helper) {
        ItemStack egg = constructionEgg(0);
        ItemStack hammer = hammer(0);
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy()), "egg alone");
        assertNoRecipe(helper, grid(3, 3, 0, hammer.copy()), "hammer alone");
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy(), 1, egg.copy(), 2, hammer.copy()), "two eggs");
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy(), 1, hammer.copy(), 2, hammer.copy()), "two hammers");
        assertNoRecipe(helper, grid(3, 3, 0, egg.copy(), 1, hammer.copy(), 8, new ItemStack(Items.STICK)), "extra item");
        ItemStack stackedEgg = egg.copy();
        stackedEgg.setCount(2);
        assertNoRecipe(helper, grid(3, 3, 0, stackedEgg, 4, hammer.copy()), "egg stack of two");

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
    public static void dismantleSerializerRoundTripsAndRejectsBadSize(GameTestHelper helper) {
        RecipeEggDismantle.Serializer serializer = new RecipeEggDismantle.Serializer();
        for (String name : new String[]{"small", "large"}) {
            JsonObject json = JsonParser.parseString(
                    "{\"type\":\"shincolle_kai:egg_dismantle\",\"category\":\"misc\",\"build_type\":\"" + name + "\"}")
                    .getAsJsonObject();
            RecipeEggDismantle recipe = serializer.fromJson(SMALL_ID, json);
            int expected = name.equals("small") ? 8 : 64;
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

        for (ResourceLocation id : new ResourceLocation[]{SMALL_ID, LARGE_ID}) {
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded =
                    helper.getLevel().getRecipeManager().byKey(id);
            helper.assertTrue(loaded.isPresent() && loaded.get().getSerializer() == ModRecipes.EGG_DISMANTLE.get(),
                    id + " must be a dismantle recipe");
        }
        helper.succeed();
    }

    // ===== helpers =====

    private static final String CAPA_INVENTORY_KEY = com.lulan.shincolle.capability.CapaShipInventory.InvName;

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
