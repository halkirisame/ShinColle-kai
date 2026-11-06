package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.client.gui.inventory.ContainerRecipePaper;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.RecipePaperData;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipePaperGameTests {
    private static final Vec3 AT = new Vec3(3.5D, 4D, 3.5D);

    private RecipePaperGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void realUseOpensPaperForEitherHand(GameTestHelper helper) {
        FakePlayer player = player(helper);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
        GameTestEntities.onCompletion(helper, channel::finishAndReleaseAll, "paper connection cleanup");
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack paper = paper();
            player.setItemInHand(hand, paper);
            var result = paper.use(helper.getLevel(), player, hand);
            helper.assertTrue(result.getResult().consumesAction() && player.containerMenu instanceof ContainerRecipePaper,
                    "Actual use must open the registered recipe menu for " + hand);
            ContainerRecipePaper menu = (ContainerRecipePaper) player.containerMenu;
            helper.assertTrue(menu.getHeldSlot() == (hand == InteractionHand.MAIN_HAND ? 0 : 40), "Bind the used hand");
            player.closeContainer();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void menuRestoresSparseInputsAndComputesFourPlanks(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack paper = sparsePaper();
        player.getInventory().setItem(0, paper);
        ContainerRecipePaper menu = menu(player);
        helper.assertTrue(menu.getRecipeInv().getItem(0).is(Items.OAK_LOG), "Sparse input zero must survive restoration");
        helper.assertTrue(menu.getRecipeInv().getItem(9).is(Items.OAK_PLANKS)
                        && menu.getRecipeInv().getItem(9).getCount() == 4, "Server must assemble four planks, not trust cache");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rightClickClearsEvenWithARealCursor(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.getInventory().setItem(0, paper());
        ContainerRecipePaper menu = menu(player);
        menu.setCarried(new ItemStack(Items.OAK_LOG, 7));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getRecipeInv().getItem(0).isEmpty() && menu.getCarried().getCount() == 7,
                "Right click must erase the ghost without consuming or returning real cursor items");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void editsSaveImmediatelyWithoutReplacingHostOrOtherTags(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack paper = paper();
        paper.getOrCreateTag().putString("Label", "keep");
        player.getInventory().setItem(0, paper);
        ContainerRecipePaper menu = menu(player);
        menu.setCarried(new ItemStack(Items.OAK_LOG, 7));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(paper.hasTag() && paper.getTag().contains("Recipe")
                        && paper.getTag().getList("Recipe", 10).size() == 2,
                "Accepted edit must persist sparse input and assembled result before close");
        helper.assertTrue(player.getInventory().getItem(0) == paper && paper.getTag().getString("Label").equals("keep"),
                "Save must retain the original stack reference and unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 320, batch = "paper_natural_crafting")
    public static void menuSavedPaperReachesNaturalShipCrafting(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            FakePlayer owner = player(helper);
            helper.getLevel().addNewPlayer(owner);
            GameTestEntities.onCompletion(helper,
                    () -> helper.getLevel().removePlayerImmediately(owner, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED),
                    "paper owner removal");
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Fixture ship exists");
            ship.setOwnerUUID(owner.getUUID());
            ship.setInvulnerable(true);
            ship.setNoGravity(true);
            ship.setShipLevel(20, true);
            ship.setExpNext();
            ship.setStateMinor(ID.M.ExpCurrent, 0);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.moveTo(helper.absoluteVec(AT));
            helper.getLevel().addFreshEntity(ship);
            GameTestEntities.removeGoalsEachTick(helper, ship,
                    goal -> goal instanceof com.lulan.shincolle.ai.ShipWanderGoal);
            boolean enabled = ConfigHandler.enableTask[3];
            int oldExperience = ConfigHandler.expGainTask[3];
            int oldGrudge = ConfigHandler.consumeGrudgeTask[3];
            ConfigHandler.enableTask[3] = true;
            ConfigHandler.expGainTask[3] = 7;
            ConfigHandler.consumeGrudgeTask[3] = 2;
            GameTestEntities.onCompletion(helper, () -> {
                ConfigHandler.enableTask[3] = enabled;
                ConfigHandler.expGainTask[3] = oldExperience;
                ConfigHandler.consumeGrudgeTask[3] = oldGrudge;
            }, "craft config restore");
            var compatibility = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY);
            GameTestEntities.onCompletion(helper, compatibility::close, "paper compatibility restore");
            CompoundTag[] invalidRaw = new CompoundTag[1];
            int[] reward = new int[1];
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ship.tickCount >= 24, "Wait for goal re-registration"))
                    .thenExecute(() -> {
                        BlockPos waypointPos = helper.absolutePos(new BlockPos(3, 4, 4));
                        BlockPos chestPos = helper.absolutePos(new BlockPos(4, 4, 4));
                        helper.getLevel().setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
                        helper.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                        TileEntityWaypoint waypoint = (TileEntityWaypoint) helper.getLevel().getBlockEntity(waypointPos);
                        waypoint.setPairedChest(chestPos);
                        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
                        chest.setItem(0, new ItemStack(Items.OAK_LOG));
                        owner.moveTo(helper.absoluteVec(AT).add(0, 0, 2));
                        ItemStack paper = paper();
                        owner.getInventory().setItem(0, paper);
                        ContainerRecipePaper menu = menu(owner);
                        menu.setCarried(new ItemStack(Items.OAK_LOG));
                        menu.clicked(0, 0, ClickType.PICKUP, owner);
                        owner.closeContainer();
                        owner.getInventory().setItem(0, ItemStack.EMPTY);
                        ship.getCapaShipInventory().setStackInSlot(22, paper);
                        ship.moveTo(helper.absoluteVec(AT));
                        ship.setDeltaMovement(Vec3.ZERO);
                        var dimension = helper.getLevel().dimension().location();
                        ship.applyCommandState(new com.lulan.shincolle.ai.domain.command.CommandIssuer.Player(owner.getUUID()),
                                new com.lulan.shincolle.ai.domain.command.CommandStateOp.Apply(
                                        new com.lulan.shincolle.ai.domain.command.ShipCommand.GuardPosition(
                                                new com.lulan.shincolle.ai.domain.DimensionKey(dimension.getNamespace(), dimension.getPath()),
                                                new com.lulan.shincolle.ai.domain.command.CommandPos(
                                                        waypointPos.getX(), waypointPos.getY(), waypointPos.getZ()), false)));
                        ship.setStateMinor(ID.M.Task, 4);
                        reward[0] = (int) (7 * ship.getAttrs().getAttrsBuffed(ID.Attrs.XP));
                        helper.assertTrue(reward[0] > 0 && ConfigHandler.expGainTask[3] == 7
                                        && ConfigHandler.consumeGrudgeTask[3] == 2, "Fixture scaled task reward and consumption retained");
                        helper.assertTrue(ship.getOwner() == owner && ship.getStateMinor(ID.M.NumGrudge) > 0
                                        && !ship.getStateFlag(ID.F.NoFuel) && ship.isAlive() && ship.getHealth() > 0 && ship.getLevel() == 20
                                        && ship.getStateMinor(ID.M.Task) == 4
                                        && ship.getStateMinor(ID.M.ExpCurrent) == 0 && ConfigHandler.enableTask[3],
                                "Fixture owner, HP, fuel, level, experience and task setting retained");
                        helper.assertTrue(ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW
                                        && targets(ship).getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof ShipTargetAuthorityGoal),
                                "Compatibility LEGACY input must still register and execute NEW");
                        helper.assertTrue(ship.hasGuardDestination() && ship.getGuardedPos(4) == 1
                                        && ship.isGuardedInCurrentDimension() && waypoint.hasPairedChest()
                                        && ship.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5) <= 25,
                                "Fixture waypoint, paired inventory, dimension and crafting reach");
                        for (int i = 12; i <= 20; i++) helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(i).isEmpty(),
                                "Working slots must start empty");
                    }).thenIdle(24).thenExecute(() -> {
                        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(4, 4, 4)));
                        int planks = 0;
                        int logs = 0;
                        for (int i = 0; i < chest.getContainerSize(); i++) {
                            if (chest.getItem(i).is(Items.OAK_PLANKS)) planks += chest.getItem(i).getCount();
                            if (chest.getItem(i).is(Items.OAK_LOG)) logs += chest.getItem(i).getCount();
                        }
                        helper.assertTrue(planks == 4 && logs == 0, "Natural eight-tick task must turn one log into four planks");
                        helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(22).is(ModItems.RECIPE_PAPER.get()), "Paper remains equipped");
                        helper.assertTrue(craftTotal(helper, ship, Items.OAK_PLANKS) == 4 && craftTotal(helper, ship, Items.OAK_LOG) == 0
                                        && ship.getStateMinor(ID.M.ExpCurrent) == reward[0] && ship.getStateMinor(ID.M.NumGrudge) <= 99_998,
                                "Chest + ship + ground balance and rewards: planks=" + craftTotal(helper, ship, Items.OAK_PLANKS)
                                        + " logs=" + craftTotal(helper, ship, Items.OAK_LOG) + " exp=" + ship.getStateMinor(ID.M.ExpCurrent)
                                        + " expected=" + reward[0] + " grudge=" + ship.getStateMinor(ID.M.NumGrudge));
                        chest.clearContent();
                        ItemStack[] pattern = emptyPattern();
                        pattern[0] = new ItemStack(Items.OAK_PLANKS);
                        pattern[3] = new ItemStack(Items.OAK_PLANKS);
                        editAndEquip(helper, owner, ship, pattern);
                        chest.setItem(0, new ItemStack(Items.OAK_PLANKS));
                    }).thenIdle(24).thenExecute(() -> {
                        helper.assertTrue(craftTotal(helper, ship, Items.STICK) == 0 && craftTotal(helper, ship, Items.OAK_PLANKS) == 1
                                        && ship.getCapaShipInventory().getStackInSlot(12).is(Items.OAK_PLANKS)
                                        && ship.getStateMinor(ID.M.ExpCurrent) == reward[0],
                                "Missing second material retains the first in working slot twelve and grants no reward");
                        craftChest(helper).setItem(0, new ItemStack(Items.OAK_PLANKS));
                    }).thenIdle(24).thenExecute(() -> {
                        helper.assertTrue(craftTotal(helper, ship, Items.STICK) == 4 && craftTotal(helper, ship, Items.OAK_PLANKS) == 0
                                        && ship.getStateMinor(ID.M.ExpCurrent) == reward[0] * 2,
                                "Two-material recipe completes when the missing material arrives");
                        craftChest(helper).clearContent();
                        ItemStack[] pattern = emptyPattern();
                        for (int i = 0; i < 3; i++) pattern[i] = new ItemStack(Items.MILK_BUCKET);
                        pattern[3] = new ItemStack(Items.SUGAR);
                        pattern[4] = new ItemStack(Items.EGG);
                        pattern[5] = new ItemStack(Items.SUGAR);
                        for (int i = 6; i < 9; i++) pattern[i] = new ItemStack(Items.WHEAT);
                        editAndEquip(helper, owner, ship, pattern);
                        for (int i = 0; i < 3; i++) craftChest(helper).setItem(i, new ItemStack(Items.MILK_BUCKET));
                        craftChest(helper).setItem(3, new ItemStack(Items.SUGAR, 2));
                        craftChest(helper).setItem(4, new ItemStack(Items.EGG));
                        craftChest(helper).setItem(5, new ItemStack(Items.WHEAT, 3));
                    }).thenIdle(24).thenExecute(() -> {
                        helper.assertTrue(craftTotal(helper, ship, Items.CAKE) == 1,
                                "Cake output must be one: actual=" + craftTotal(helper, ship, Items.CAKE));
                        for (var material : new net.minecraft.world.item.Item[]{Items.MILK_BUCKET, Items.WHEAT, Items.SUGAR, Items.EGG}) {
                            helper.assertTrue(craftTotal(helper, ship, material) == 0,
                                    "Cake material consumed: " + material + " actual=" + craftTotal(helper, ship, material));
                        }
                        helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) == reward[0] * 3,
                                "Cake reward: actual=" + ship.getStateMinor(ID.M.ExpCurrent) + " expected=" + reward[0] * 3);
                        helper.assertTrue(craftTotal(helper, ship, Items.BUCKET) == 3,
                                "Cake returns three containers: actual=" + craftTotal(helper, ship, Items.BUCKET));
                        ItemStack[] pattern = emptyPattern();
                        pattern[0] = new ItemStack(Items.OAK_LOG);
                        editAndEquip(helper, owner, ship, pattern);
                        for (int i = 0; i < craftChest(helper).getContainerSize(); i++) {
                            craftChest(helper).setItem(i, new ItemStack(Items.STONE, 64));
                        }
                        ship.getCapaShipInventory().setStackInSlot(12, new ItemStack(Items.OAK_LOG));
                    }).thenIdle(24).thenExecute(() -> {
                        helper.assertTrue(craftTotal(helper, ship, Items.OAK_PLANKS) == 4 && craftTotal(helper, ship, Items.OAK_LOG) == 0
                                        && craftTotal(helper, ship, Items.STONE) == 27 * 64
                                        && ship.getStateMinor(ID.M.ExpCurrent) == reward[0] * 4,
                                "A genuinely full chest drops four planks without losing existing contents");
                        ItemStack[] pattern = emptyPattern();
                        for (int i = 0; i < 3; i++) pattern[i] = new ItemStack(Items.MILK_BUCKET);
                        pattern[3] = new ItemStack(Items.SUGAR);
                        pattern[4] = new ItemStack(Items.EGG);
                        pattern[5] = new ItemStack(Items.SUGAR);
                        for (int i = 6; i < 9; i++) pattern[i] = new ItemStack(Items.WHEAT);
                        editAndEquip(helper, owner, ship, pattern);
                        for (int i = 0; i < 9; i++) ship.getCapaShipInventory().setStackInSlot(i + 12, pattern[i].copy());
                    }).thenIdle(24).thenExecute(() -> {
                        helper.assertTrue(craftTotal(helper, ship, Items.CAKE) == 1, "Full chest still produces one cake");
                        helper.assertTrue(craftTotal(helper, ship, Items.BUCKET) == 3, "Full chest retains all three containers");
                        int droppedBuckets = 0;
                        int droppedCakes = 0;
                        for (var drop : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 4, 3))).inflate(12))) {
                            if (drop.getItem().is(Items.BUCKET)) droppedBuckets += drop.getItem().getCount();
                            if (drop.getItem().is(Items.CAKE)) droppedCakes += drop.getItem().getCount();
                        }
                        helper.assertTrue(droppedBuckets == 3 && droppedCakes == 1,
                                "Full chest sends actual cake and container stacks to the existing ground-drop path");
                        helper.assertTrue(craftTotal(helper, ship, Items.STONE) == 27 * 64,
                                "Remainder drop never replaces full chest contents");
                        helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) == reward[0] * 5,
                                "Container drops grant only the existing successful crafting reward");
                        for (int i = 12; i <= 20; i++) helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(i).isEmpty(),
                                "Full-chest cake consumes each working material");
                        ItemStack[] pattern = emptyPattern();
                        pattern[0] = new ItemStack(Items.OAK_LOG);
                        editAndEquip(helper, owner, ship, pattern);
                        ItemStack paper = ship.getCapaShipInventory().getStackInSlot(22);
                        paper.getTag().getList("Recipe", 10).getCompound(1).putString("id", "unknown:result");
                        invalidRaw[0] = paper.getTag().copy();
                        craftChest(helper).setItem(0, new ItemStack(Items.OAK_LOG));
                    }).thenIdle(24).thenExecute(() -> {
                        helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(22).getTag().equals(invalidRaw[0])
                                        && craftTotal(helper, ship, Items.OAK_LOG) == 1 && craftTotal(helper, ship, Items.OAK_PLANKS) == 4
                                        && ship.getStateMinor(ID.M.ExpCurrent) == reward[0] * 5,
                                "Invalid cached result blocks natural crafting without changing raw paper, materials or rewards");
                        helper.succeed();
                    });
        }, AT);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100, batch = "paper_remaining_callback")
    public static void craftingRemainderSnapshotIsIndependentAndSuccessOnly(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var manager = helper.getLevel().getRecipeManager();
            var saved = List.copyOf(manager.getRecipes());
            GameTestEntities.onCompletion(helper, () -> manager.replaceRecipes(saved), "remaining recipe restore");
            boolean enabled = ConfigHandler.enableTask[3];
            int oldExperience = ConfigHandler.expGainTask[3];
            ConfigHandler.enableTask[3] = true;
            ConfigHandler.expGainTask[3] = 7;
            GameTestEntities.onCompletion(helper, () -> {
                ConfigHandler.enableTask[3] = enabled;
                ConfigHandler.expGainTask[3] = oldExperience;
            }, "remaining config restore");
            FakePlayer owner = player(helper);
            helper.getLevel().addNewPlayer(owner);
            GameTestEntities.onCompletion(helper,
                    () -> helper.getLevel().removePlayerImmediately(owner, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED),
                    "remaining owner removal");
            BasicEntityShip ship = GameTestEntities.open(helper).add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            ship.setOwnerUUID(owner.getUUID());
            ship.setNoAi(true);
            ship.setInvulnerable(true);
            ship.setShipLevel(1, true);
            ship.setStateMinor(ID.M.ExpCurrent, 0);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.moveTo(helper.absoluteVec(AT));
            BlockPos waypointPos = helper.absolutePos(new BlockPos(3, 4, 4));
            BlockPos chestPos = helper.absolutePos(new BlockPos(4, 4, 4));
            helper.getLevel().setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
            helper.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            ((TileEntityWaypoint) helper.getLevel().getBlockEntity(waypointPos)).setPairedChest(chestPos);
            var dimension = helper.getLevel().dimension().location();
            ship.applyCommandState(new com.lulan.shincolle.ai.domain.command.CommandIssuer.Player(owner.getUUID()),
                    new com.lulan.shincolle.ai.domain.command.CommandStateOp.Apply(
                            new com.lulan.shincolle.ai.domain.command.ShipCommand.GuardPosition(
                                    new com.lulan.shincolle.ai.domain.DimensionKey(dimension.getNamespace(), dimension.getPath()),
                                    new com.lulan.shincolle.ai.domain.command.CommandPos(
                                            waypointPos.getX(), waypointPos.getY(), waypointPos.getZ()), false)));
            ship.setStateMinor(ID.M.Task, 4);
            var inventory = ship.getCapaShipInventory();
            inventory.setStackInSlot(22, sparsePaper());
            for (int i = 12; i <= 20; i++) helper.assertTrue(inventory.getStackInSlot(i).isEmpty(), "Empty callback working fixture");
            helper.assertTrue(ship.getStateMinor(ID.M.Task) == 4 && ship.getLevel() == 1 && ship.getOwner() == owner
                            && ship.getHealth() > 0 && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel)
                            && ship.getGuardedPos(4) == 1 && ship.isGuardedInCurrentDimension()
                            && ship.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5) <= 25,
                    "Direct task callback fixture is alive, fueled, enabled, single-craft and in reach");
            int[] callbacks = {0};
            boolean[] rejectActual = {true};
            var recipe = new net.minecraft.world.item.crafting.ShapelessRecipe(
                    new net.minecraft.resources.ResourceLocation(Reference.MOD_ID, "paper_remaining_test"), "",
                    net.minecraft.world.item.crafting.CraftingBookCategory.MISC, new ItemStack(Items.BRICK),
                    net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                            net.minecraft.world.item.crafting.Ingredient.of(Items.OAK_LOG))) {
                @Override
                public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer input, net.minecraft.core.RegistryAccess registry) {
                    return new ItemStack(rejectActual[0] && input.getItem(0) == inventory.getStackInSlot(12) ? Items.STONE : Items.BRICK);
                }

                @Override
                public net.minecraft.core.NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer input) {
                    callbacks[0]++;
                    helper.assertTrue(input.getItem(0) == inventory.getStackInSlot(12) && input.getItem(0).getCount() == 1,
                            "Callback receives the live unconsumed actual material, not the paper pattern");
                    helper.assertTrue(craftTotal(helper, ship, Items.BRICK) == 0, "Callback precedes result storage and drop");
                    return net.minecraft.core.NonNullList.of(ItemStack.EMPTY, input.getItem(0));
                }
            };
            manager.replaceRecipes(List.of(recipe));
            com.lulan.shincolle.utility.TaskHelper.onUpdateTask(ship);
            helper.assertTrue(callbacks[0] == 0 && ship.getStateMinor(ID.M.ExpCurrent) == 0,
                    "Missing material never calls the remainder callback or rewards crafting");
            inventory.setStackInSlot(12, new ItemStack(Items.OAK_LOG));
            com.lulan.shincolle.utility.TaskHelper.onUpdateTask(ship);
            helper.assertTrue(callbacks[0] == 0 && inventory.getStackInSlot(12).getCount() == 1
                            && craftTotal(helper, ship, Items.BRICK) == 0 && ship.getStateMinor(ID.M.ExpCurrent) == 0,
                    "Actual result mismatch never calls the callback, consumes material or grants output/reward");
            rejectActual[0] = false;
            com.lulan.shincolle.utility.TaskHelper.onUpdateTask(ship);
            helper.assertTrue(callbacks[0] == 1, "Exactly one remainder callback for one successful craft");
            helper.assertTrue(inventory.getStackInSlot(12).isEmpty() && craftTotal(helper, ship, Items.BRICK) == 1,
                    "Successful craft consumes its material and stores the result");
            helper.assertTrue(craftChest(helper).getItem(0).is(Items.BRICK) && craftTotal(helper, ship, Items.OAK_LOG) == 1,
                    "An aliased remainder survives material shrink as an independently copied stack");
            helper.succeed();
        }, AT);
    }

    private static GoalSelector targets(BasicEntityShip ship) {
        try {
            var field = net.minecraft.world.entity.Mob.class.getDeclaredField("targetSelector");
            field.setAccessible(true);
            return (GoalSelector) field.get(ship);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static ItemStack[] emptyPattern() {
        ItemStack[] pattern = new ItemStack[9];
        java.util.Arrays.fill(pattern, ItemStack.EMPTY);
        return pattern;
    }

    private static void editAndEquip(GameTestHelper helper, FakePlayer owner, BasicEntityShip ship, ItemStack[] inputs) {
        ItemStack paper = ship.getCapaShipInventory().getStackInSlot(22);
        ship.getCapaShipInventory().setStackInSlot(22, ItemStack.EMPTY);
        owner.getInventory().setItem(0, paper);
        ContainerRecipePaper menu = menu(owner);
        for (int i = 0; i < 9; i++) {
            menu.setCarried(inputs[i].copy());
            menu.clicked(i, 0, ClickType.PICKUP, owner);
        }
        owner.closeContainer();
        owner.getInventory().setItem(0, ItemStack.EMPTY);
        CompoundTag saved = paper.save(new CompoundTag());
        ItemStack restored = ItemStack.of(saved);
        helper.assertTrue(ItemStack.matches(paper, restored), "Paper survives standard inventory save/reload");
        ship.getCapaShipInventory().setStackInSlot(22, restored);
        for (int i = 12; i <= 20; i++) helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(i).isEmpty(),
                "Previous recipe must leave all working slots empty before the next fixture");
    }

    private static ChestBlockEntity craftChest(GameTestHelper helper) {
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(4, 4, 4)));
    }

    private static int craftTotal(GameTestHelper helper, BasicEntityShip ship, net.minecraft.world.item.Item item) {
        int count = 0;
        ChestBlockEntity chest = craftChest(helper);
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(item)) count += chest.getItem(i).getCount();
        var inventory = ship.getCapaShipInventory();
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (inventory.getStackInSlot(i).is(item)) count += inventory.getStackInSlot(i).getCount();
        }
        for (var drop : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 4, 3))).inflate(12))) {
            if (drop.getItem().is(item)) count += drop.getItem().getCount();
        }
        return count;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void codecAcceptsSparseUnorderedDenseAndIndependentCopies(GameTestHelper helper) {
        ItemStack empty = paper();
        helper.assertTrue(RecipePaperData.decode(empty) instanceof RecipePaperData.Valid && !empty.hasTag(),
                "Missing Recipe is valid and does not create a tag");
        empty.getOrCreateTag().put("Recipe", new ListTag());
        helper.assertTrue(RecipePaperData.decode(empty) instanceof RecipePaperData.Valid, "Empty END list is valid");
        ListTag dense = new ListTag();
        for (int i = 9; i >= 0; i--) dense.add(entry(i, ItemStack.EMPTY));
        ItemStack paper = paper();
        paper.getOrCreateTag().put("Recipe", dense);
        helper.assertTrue(RecipePaperData.decode(paper) instanceof RecipePaperData.Valid, "Ten unordered explicit standard empties");
        ItemStack log = new ItemStack(Items.OAK_LOG, 63);
        log.getOrCreateTag().putString("Custom", "keep");
        dense.set(9, entry(0, log));
        CompoundTag before = paper.getTag().copy();
        RecipePaperData.Valid valid = (RecipePaperData.Valid) RecipePaperData.decode(paper);
        helper.assertTrue(valid.input(0).getCount() == 1 && valid.input(0).getTag().getString("Custom").equals("keep"),
                "Normalize only input count and preserve item NBT");
        ItemStack copy = valid.input(0);
        copy.setCount(50);
        copy.getOrCreateTag().putString("Custom", "changed");
        helper.assertTrue(valid.input(0).getCount() == 1 && valid.input(0).getTag().getString("Custom").equals("keep")
                        && paper.getTag().equals(before), "Decoded values and raw NBT must be independently owned");
        RecipePaperData.Valid sparse = (RecipePaperData.Valid) RecipePaperData.decode(sparsePaper());
        helper.assertTrue(sparse.input(0).is(Items.OAK_LOG) && sparse.input(8).isEmpty()
                        && sparse.recordedResult().is(Items.DIAMOND) && sparse.recordedResult().getCount() == 64,
                "Sparse records retain a known recorded result only as cache");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void codecRejectsMalformedRecordsAsAWhole(GameTestHelper helper) {
        List<Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.remove("Slot"), tag -> tag.putByte("Slot", (byte) 0), tag -> tag.putInt("Slot", -1),
                tag -> tag.putInt("Slot", 10), tag -> tag.remove("id"), tag -> tag.putInt("id", 1),
                tag -> tag.putString("id", "invalid space:item"), tag -> tag.putString("id", "unknown:absent"),
                tag -> tag.remove("Count"), tag -> tag.putInt("Count", 1), tag -> tag.putByte("Count", (byte) 0),
                tag -> tag.putByte("Count", (byte) -1), tag -> tag.putString("id", "minecraft:air"),
                tag -> tag.putString("tag", "wrong"), tag -> tag.putInt("ForgeCaps", 1));
        for (int i = 0; i < corruptions.size(); i++) {
            ItemStack paper = sparsePaper();
            CompoundTag tag = paper.getTag().getList("Recipe", 10).getCompound(0);
            corruptions.get(i).accept(tag);
            invalid(helper, paper, "stack corruption " + i);
        }
        ItemStack paper = sparsePaper();
        paper.getTag().put("Recipe", IntTag.valueOf(1));
        invalid(helper, paper, "non-list Recipe");
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("wrong"));
        paper.getTag().put("Recipe", strings);
        invalid(helper, paper, "non-compound elements");
        for (int duplicate : new int[]{0, 9}) {
            paper = sparsePaper();
            paper.getTag().getList("Recipe", 10).add(entry(duplicate, new ItemStack(Items.OAK_LOG)));
            invalid(helper, paper, "duplicate " + duplicate);
        }
        paper = sparsePaper();
        paper.getTag().getList("Recipe", 10).getCompound(1).putString("id", "unknown:result");
        invalid(helper, paper, "unknown result must invalidate otherwise valid inputs");
        ListTag eleven = new ListTag();
        for (int i = 0; i < 11; i++) eleven.add(entry(i, ItemStack.EMPTY));
        paper.getTag().put("Recipe", eleven);
        invalid(helper, paper, "eleven entries may not be truncated");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void invalidRawSurvivesViewingClosingAndExplicitlyResetsOnErase(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack paper = sparsePaper();
        paper.getTag().getList("Recipe", 10).getCompound(1).putString("id", "unknown:result");
        paper.getTag().putString("Label", "keep");
        CompoundTag raw = paper.getTag().copy();
        player.getInventory().setItem(0, paper);
        ContainerRecipePaper menu = menu(player);
        helper.assertTrue(menu.hasInvalidRecipe() && menu.getRecipeInv().isEmpty(), "Invalid paper opens an empty invalid editor");
        menu.broadcastChanges();
        menu.slotsChanged(menu.getRecipeInv());
        List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
        paper.getItem().appendHoverText(paper, helper.getLevel(), tooltip, net.minecraft.world.item.TooltipFlag.NORMAL);
        player.closeContainer();
        helper.assertTrue(paper.getTag().equals(raw) && tooltip.size() == 1, "Read, preview, tooltip and close never repair raw NBT");
        menu = menu(player);
        menu.setCarried(new ItemStack(Items.OAK_LOG, 3));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(!menu.hasInvalidRecipe() && paper.getTag().getList("Recipe", 10).isEmpty()
                        && paper.getTag().getString("Label").equals("keep") && menu.getCarried().getCount() == 3,
                "Explicit empty-to-empty right erase replaces the entire invalid Recipe, preserving unrelated tags");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostIdentitySelectionAndClosedLifetimeAreEnforced(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack paper = sparsePaper();
        player.getInventory().setItem(0, paper);
        ContainerRecipePaper menu = menu(player);
        player.getInventory().selected = 2;
        paper.getOrCreateTag().putString("Other", "updated");
        helper.assertTrue(menu.stillValid(player), "Selection changes and ordinary NBT updates do not change the fixed host binding");
        helper.assertTrue(!menu.stillValid(player(helper)), "Only the bound owner can edit");
        ItemStack replacement = paper.copy();
        player.getInventory().setItem(0, replacement);
        helper.assertTrue(!menu.stillValid(player), "Equal NBT is not stack identity");
        CompoundTag original = paper.getTag().copy();
        menu.setCarried(new ItemStack(Items.DIRT, 3));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu && paper.getTag().equals(original)
                        && replacement.getTag().equals(original), "Invalid edit closes without saving into either paper");
        player.getInventory().setItem(0, paper);
        helper.assertTrue(!menu.stillValid(player), "Restoring the old reference cannot revive a closed editor");
        ContainerRecipePaper reopened = menu(player);
        helper.assertTrue(reopened.stillValid(player) && reopened.getRecipeInv().getItem(0).is(Items.OAK_LOG), "Reopen establishes a new lifetime");
        reopened.removed(player);
        CompoundTag before = paper.getTag().copy();
        reopened.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(!reopened.stillValid(player) && paper.getTag().equals(before), "Removed menu cannot accept late edits");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100, batch = "paper_host_moves")
    public static void vanillaHostMovesCloseWithoutReturningGhosts(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            for (int action = 0; action < 5; action++) {
                FakePlayer player = player(helper);
                ItemStack paper = sparsePaper();
                player.getInventory().setItem(0, paper);
                ContainerRecipePaper menu = menu(player);
                CompoundTag before = paper.getTag().copy();
                if (action == 0) menu.clicked(37, 0, ClickType.PICKUP, player);
                if (action == 1) menu.clicked(10, 0, ClickType.SWAP, player);
                if (action == 2) menu.clicked(37, 40, ClickType.SWAP, player);
                if (action == 3) menu.clicked(37, 1, ClickType.THROW, player);
                if (action == 4) {
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    player.getInventory().setItem(8, paper);
                    menu.broadcastChanges();
                }
                helper.assertTrue(player.containerMenu == player.inventoryMenu && !menu.stillValid(player), "Host move closes: " + action);
                helper.assertTrue(paper.getTag().equals(before), "Host move never performs a close-time save: " + action);
                helper.assertTrue(realCount(helper, player, menu, Items.OAK_LOG) == 0
                                && realCount(helper, player, menu, Items.OAK_PLANKS) == 0
                                && realCount(helper, player, menu, Items.DIAMOND) == 0,
                        "Neither input ghosts nor recorded/preview result is returned or dropped");
            }
            helper.succeed();
        }, AT);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100, batch = "paper_real_item_conservation")
    public static void allGhostClickTypesAndInventoryCrossOperationsConserveRealItems(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            FakePlayer player = player(helper);
            player.getInventory().setItem(0, paper());
            ContainerRecipePaper menu = menu(player);
            menu.setCarried(new ItemStack(Items.OAK_LOG, 7));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            CompoundTag recorded = player.getInventory().getItem(0).getTag().copy();
            for (ClickType type : ClickType.values()) {
                if (type != ClickType.PICKUP) {
                    for (int button : new int[]{0, 1, 2, 8, 40}) menu.clicked(0, button, type, player);
                }
                for (int button : new int[]{0, 1, 2, 40}) menu.clicked(9, button, type, player);
            }
            menu.clicked(0, 2, ClickType.PICKUP, player);
            helper.assertTrue(player.getInventory().getItem(0).getTag().equals(recorded)
                            && menu.getRecipeInv().getItem(0).getCount() == 1, "Unsupported actions cannot edit ghosts or result");
            for (int i = 0; i < 10; i++) helper.assertTrue(!menu.getSlot(i).mayPickup(player)
                            && !menu.getSlot(i).mayPlace(menu.getCarried()) && !menu.canDragTo(menu.getSlot(i))
                            && !menu.canTakeItemForPickAll(menu.getCarried(), menu.getSlot(i)), "Ghost traversal exclusions " + i);
            helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "Quick move stays disabled");
            menu.clicked(-999, 0, ClickType.QUICK_CRAFT, player);
            menu.clicked(10, 1, ClickType.QUICK_CRAFT, player);
            menu.clicked(0, 1, ClickType.QUICK_CRAFT, player);
            menu.clicked(-999, 2, ClickType.QUICK_CRAFT, player);
            helper.assertTrue(menu.getCarried().isEmpty() && player.getInventory().getItem(9).getCount() == 7,
                    "Fixture drag traversed an actual inventory slot, but not the ghost");
            menu.clicked(10, 1, ClickType.PICKUP, player);
            menu.clicked(11, 0, ClickType.PICKUP_ALL, player);
            helper.assertTrue(realCount(helper, player, menu, Items.OAK_LOG) == 7
                            && realCount(helper, player, menu, Items.OAK_PLANKS) == 0,
                    "Inventory-started drag and collect cannot create, consume or collect ghost objects");
            helper.assertTrue(menu.getCarried().getCount() == 7 && player.getInventory().getItem(9).isEmpty(),
                    "Collect gathered real items but not the matching input ghost");
            player.closeContainer();
            helper.assertTrue(realCount(helper, player, menu, Items.OAK_LOG) == 7, "Close returns only the real cursor");
            helper.succeed();
        }, AT);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void standardClickHandlerIgnoresPredictedCursorAndClosedContainer(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.getInventory().setItem(0, paper());
        ContainerRecipePaper menu = menu(player);
        menu.setCarried(new ItemStack(Items.OAK_LOG, 5));
        var changed = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ItemStack>();
        changed.put(0, new ItemStack(Items.DIAMOND, 64));
        var forged = new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(menu.containerId,
                menu.getStateId() + 1, 0, 0, ClickType.PICKUP, new ItemStack(Items.DIAMOND, 64), changed);
        player.connection.handleContainerClick(forged);
        helper.assertTrue(menu.getRecipeInv().getItem(0).is(Items.OAK_LOG) && menu.getCarried().is(Items.OAK_LOG)
                        && menu.getCarried().getCount() == 5, "Server handler executes using its real cursor, including a stateId mismatch");
        CompoundTag saved = player.getInventory().getItem(0).getTag().copy();
        player.closeContainer();
        player.connection.handleContainerClick(forged);
        helper.assertTrue(player.getInventory().getItem(0).getTag().equals(saved), "Old containerId cannot edit after close");
        helper.succeed();
    }

    private static CompoundTag entry(int slot, ItemStack stack) {
        CompoundTag entry = stack.save(new CompoundTag());
        entry.putInt("Slot", slot);
        return entry;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100, batch = "paper_recipe_manager_refresh")
    public static void openMenuRefreshesDefinitionsWithinTwentyTicksWithoutSaving(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<net.minecraft.world.item.crafting.Recipe<?>> saved = List.copyOf(manager.getRecipes());
        GameTestEntities.onCompletion(helper, () -> manager.replaceRecipes(saved), "paper recipe manager restore");
        int[] matches = {0};
        var recipe = new net.minecraft.world.item.crafting.ShapelessRecipe(
                new net.minecraft.resources.ResourceLocation(Reference.MOD_ID, "paper_refresh_test"), "",
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC, new ItemStack(Items.OAK_PLANKS, 8),
                net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                        net.minecraft.world.item.crafting.Ingredient.of(Items.OAK_LOG))) {
            @Override
            public boolean matches(net.minecraft.world.inventory.CraftingContainer input, net.minecraft.world.level.Level level) {
                matches[0]++;
                return super.matches(input, level);
            }
        };
        manager.replaceRecipes(List.of(recipe));
        FakePlayer player = player(helper);
        ItemStack paper = sparsePaper();
        player.getInventory().setItem(0, paper);
        CompoundTag raw = paper.getTag().copy();
        ContainerRecipePaper menu = menu(player);
        helper.assertTrue(menu.getRecipeInv().getItem(9).getCount() == 8 && matches[0] == 1,
                "Opening computes from current definitions, not recorded diamond result");
        menu.broadcastChanges();
        menu.broadcastChanges();
        helper.assertTrue(matches[0] == 1, "No periodic scan twice in the calculation tick");
        manager.replaceRecipes(List.of());
        helper.startSequence().thenIdle(19).thenExecute(() -> {
            menu.broadcastChanges();
            helper.assertTrue(menu.getRecipeInv().getItem(9).getCount() == 8, "Refresh interval is twenty server ticks");
        }).thenIdle(1).thenExecute(() -> {
            menu.broadcastChanges();
            helper.assertTrue(menu.getRecipeInv().getItem(9).isEmpty() && menu.getRecipeInv().getItem(0).is(Items.OAK_LOG)
                            && paper.getTag().equals(raw), "Deleted definition clears preview only, retaining input and raw cache");
            manager.replaceRecipes(List.of(recipe));
        }).thenIdle(20).thenExecute(() -> {
            menu.broadcastChanges();
            helper.assertTrue(menu.getRecipeInv().getItem(9).getCount() == 8 && paper.getTag().equals(raw),
                    "Restored definitions refresh on the same manager without rewriting paper");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100, batch = "paper_invalid_refresh")
    public static void invalidPatternAndRawRemainInvalidAcrossRefresh(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack paper = sparsePaper();
        paper.getTag().getList("Recipe", 10).getCompound(1).putString("id", "unknown:result");
        player.getInventory().setItem(0, paper);
        CompoundTag raw = paper.getTag().copy();
        ContainerRecipePaper menu = menu(player);
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            menu.broadcastChanges();
            helper.assertTrue(menu.hasInvalidRecipe() && menu.getRecipeInv().isEmpty() && paper.getTag().equals(raw),
                    "Periodic preview never repairs or silently accepts invalid records");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100, batch = "paper_owner_lifetime")
    public static void deathDimensionAndDisconnectReturnOnlyRealCursor(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            for (int ending = 0; ending < 3; ending++) {
                FakePlayer player = player(helper);
                ItemStack paper = sparsePaper();
                player.getInventory().setItem(0, paper);
                ContainerRecipePaper menu = menu(player);
                int priorDirt = realCount(helper, player, menu, Items.DIRT);
                menu.setCarried(new ItemStack(Items.DIRT, 3));
                CompoundTag raw = paper.getTag().copy();
                if (ending == 0) player.setHealth(0);
                if (ending == 1) {
                    var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                    helper.assertTrue(nether != null, "Fixture dimension exists");
                    player.setServerLevel(nether);
                }
                if (ending == 2) player.disconnect();
                if (ending != 2) {
                    helper.assertTrue(!menu.stillValid(player), "Dead/dimension-changed owner invalidates binding");
                    menu.broadcastChanges();
                } else {
                    player.doCloseContainer();
                }
                if (ending == 1) player.setServerLevel(helper.getLevel());
                helper.assertTrue(!menu.stillValid(player) && menu.getCarried().isEmpty() && paper.getTag().equals(raw),
                        "Lifetime ends without a final save and drains only the actual cursor: " + ending);
                helper.assertTrue(realCount(helper, player, menu, Items.DIRT) == priorDirt + 3,
                        "Exactly the real cursor is returned or dropped: " + ending);
                helper.assertTrue(realCount(helper, player, menu, Items.OAK_LOG) == 0
                                && realCount(helper, player, menu, Items.OAK_PLANKS) == 0, "No input/result ghost becomes real");
            }
            helper.succeed();
        }, AT);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void standardSlotAndDataSynchronizationIsDisplayOnly(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack paper = sparsePaper();
        paper.getTag().getList("Recipe", 10).getCompound(1).putString("id", "unknown:result");
        player.getInventory().setItem(0, paper);
        ContainerRecipePaper server = menu(player);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buffer.writeInt(0);
        ContainerRecipePaper display = new ContainerRecipePaper(1, player(helper).getInventory(), buffer);
        buffer.release();
        server.setSynchronizer(new net.minecraft.world.inventory.ContainerSynchronizer() {
            @Override
            public void sendInitialData(net.minecraft.world.inventory.AbstractContainerMenu source,
                    net.minecraft.core.NonNullList<ItemStack> stacks, ItemStack carried, int[] data) {
                display.initializeContents(source.getStateId(), stacks.stream().map(ItemStack::copy).toList(), carried.copy());
                for (int i = 0; i < data.length; i++) display.setData(i, data[i]);
            }

            @Override
            public void sendSlotChange(net.minecraft.world.inventory.AbstractContainerMenu source, int slot, ItemStack stack) {
                display.setItem(slot, source.getStateId(), stack.copy());
            }

            @Override
            public void sendCarriedChange(net.minecraft.world.inventory.AbstractContainerMenu source, ItemStack stack) {
                display.setCarried(stack.copy());
            }

            @Override
            public void sendDataChange(net.minecraft.world.inventory.AbstractContainerMenu source, int id, int value) {
                display.setData(id, value);
            }
        });
        helper.assertTrue(display.hasInvalidRecipe(), "One standard DataSlot carries invalid state");
        server.setCarried(new ItemStack(Items.OAK_LOG, 3));
        server.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(!display.hasInvalidRecipe() && display.getRecipeInv().getItem(0).getCount() == 1
                        && display.getRecipeInv().getItem(9).getCount() == 4, "Standard slots carry normalized input and full output count");
        CompoundTag raw = paper.getTag().copy();
        display.setCarried(new ItemStack(Items.DIAMOND));
        display.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(paper.getTag().equals(raw), "Display constructor never saves or treats predicted cursor as authority");
        helper.succeed();
    }

    private static void invalid(GameTestHelper helper, ItemStack paper, String reason) {
        CompoundTag raw = paper.getTag().copy();
        helper.assertTrue(RecipePaperData.decode(paper) == RecipePaperData.Invalid.INSTANCE && paper.getTag().equals(raw), reason);
    }

    private static int realCount(GameTestHelper helper, FakePlayer player, ContainerRecipePaper menu, net.minecraft.world.item.Item item) {
        int count = menu.getCarried().is(item) ? menu.getCarried().getCount() : 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        for (var drop : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(12))) {
            if (drop.getItem().is(item)) count += drop.getItem().getCount();
        }
        return count;
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PaperTest"));
        player.moveTo(helper.absoluteVec(AT));
        GameTestEntities.onCompletion(helper, player::closeContainer, "paper cursor cleanup");
        return player;
    }

    private static ContainerRecipePaper menu(FakePlayer player) {
        ContainerRecipePaper menu = new ContainerRecipePaper(1, player.getInventory(), 0);
        player.containerMenu = menu;
        return menu;
    }

    private static ItemStack paper() {
        return new ItemStack(ModItems.RECIPE_PAPER.get());
    }

    private static ItemStack sparsePaper() {
        ItemStack paper = paper();
        ListTag list = new ListTag();
        CompoundTag log = new ItemStack(Items.OAK_LOG).save(new CompoundTag());
        log.putInt("Slot", 0);
        list.add(log);
        CompoundTag result = new ItemStack(Items.DIAMOND, 64).save(new CompoundTag());
        result.putInt("Slot", 9);
        list.add(result);
        paper.getOrCreateTag().put("Recipe", list);
        return paper;
    }
}
