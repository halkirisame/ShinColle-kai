package com.lulan.shincolle.utility;

import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.task.TaskMovePlanner;
import com.lulan.shincolle.ai.domain.task.TaskMoveRequest;
import com.lulan.shincolle.ai.domain.task.TaskSideMask;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.config.ConfigMining;
import com.lulan.shincolle.crafting.InventoryCraftingFake;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.ShipLevelRules;
import com.lulan.shincolle.entity.other.EntityShipFishingHook;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.RecipePaperData;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.Dist4d;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper for ship tasks: cooking, mining, fishing, crafting, pumping.
 * <p>
 * Ported from 1.10.2 TaskHelper.
 */
public class TaskHelper {

    public TaskHelper() {
    }

    /** NEW: a working ship's walk, planned by the domain and carried out by the movement executor. */
    private static void taskMove(BasicEntityShip host, TaskMoveRequest request) {
        ShipMovementExecutor.run(host, TaskMovePlanner.plan(request, ShipMovementGate.taskMove(host, request)));
    }

    /**
     * Update task, called every 8 ticks.
     * <p>
     * StateMinor[ID.M.Task]: task ID
     * StateMinor[ID.M.TaskSide]: bit flags for sides and options
     */
    public static void onUpdateTask(BasicEntityShip host) {
        if (host.getStateFlag(ID.F.NoFuel) || !host.isAlive())
            return;

        switch (host.getStateMinor(ID.M.Task)) {
            case 1: // cooking
                if (ConfigHandler.enableTask[0])
                    onUpdateCooking(host);
                break;
            case 2: // fishing
                if (ConfigHandler.enableTask[1])
                    onUpdateFishing(host);
                break;
            case 3: // mining
                if (ConfigHandler.enableTask[2])
                    onUpdateMining(host);
                break;
            case 4: // crafting
                if (ConfigHandler.enableTask[3])
                    onUpdateCrafting(host);
                break;
        }
    }

    /**
     * Crafting task:
     * Craft itemstack stored in mainhand slot (slot 22).
     */
    public static void onUpdateCrafting(BasicEntityShip host) {
        if (host == null)
            return;

        CapaShipInventory invShip = host.getCapaShipInventory();
        ItemStack paper = invShip.getStackInSlot(22); // mainhand slot
        if (paper.isEmpty() || paper.getItem() != ModItems.RECIPE_PAPER.get())
            return;

        // check guard position
        // [PORT] 1.10.2 -> 1.20.1: the original used Y <= 0 as an "unset" sentinel, safe
        // when the world floor was Y=0. Y now reaches -64 and the whole deepslate layer
        // is negative, so validity comes from the guard flags instead.
        if (!host.hasGuardDestination())
            return;
        BlockPos pos = new BlockPos(host.getGuardedPos(0), host.getGuardedPos(1), host.getGuardedPos(2));

        // check guard type
        if (host.getGuardedPos(4) != 1)
            return;

        // check dimension
        if (!host.isGuardedInCurrentDimension())
            return;

        // check guard position is waypoint
        BlockEntity te = host.level().getBlockEntity(pos);
        if (!(te instanceof TileEntityWaypoint waypoint))
            return;

        // check waypoint has paired chest
        if (!waypoint.hasPairedChest())
            return;
        pos = waypoint.getPairedChest();

        // check paired chest has inventory capability
        IItemHandler chest = CapaHelper.getCapaInventory(host.level().getBlockEntity(pos), -1);
        if (chest == null)
            return;

        // check distance
        if (host.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 25D) {
            taskMove(host, new TaskMoveRequest.ReturnToWaypoint(new CommandPos(host.getGuardedPos(0),
                    host.getGuardedPos(1), host.getGuardedPos(2))));
            return;
        }

        // check recipe is valid
        InventoryCraftingFake recipe = new InventoryCraftingFake(3, 3);
        RecipePaperData.Decoded decoded = RecipePaperData.decode(paper);
        if (!(decoded instanceof RecipePaperData.Valid valid)) return;
        boolean hasInput = false;
        for (int i = 0; i < 9; i++) {
            ItemStack input = valid.input(i);
            recipe.setItem(i, input);
            hasInput |= !input.isEmpty();
        }
        if (!hasInput) return;
        ItemStack result = host.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, recipe, host.level())
                .map(match -> match.assemble(recipe, host.level().registryAccess())).orElse(ItemStack.EMPTY);
        if (result.isEmpty()) return;

        // start crafting
        int maxCraft = host.getLevel() / 20 + 1;
        int taskSide = host.getStateMinor(ID.M.TaskSide);
        TaskSideMask sideMask = new TaskSideMask(taskSide);
        boolean checkMetadata = sideMask.checkMetadata();
        boolean checkNbt = sideMask.checkNbt();
        InventoryCraftingFake recipeTemp = new InventoryCraftingFake(3, 3);
        boolean canAddExp = false;
        int maxtimes = maxCraft;

        while (maxCraft > 0) {
            maxtimes--;
            if (maxtimes < 0)
                break;


            // move materials from chest to ship's inventory slot 12~20
            for (int i = 0; i < 9; i++) {
                if (!invShip.getStackInSlot(i + 12).isEmpty()) {
                    recipeTemp.setItem(i, invShip.getStackInSlot(i + 12));
                    continue;
                }

                ItemStack tempStack = recipe.getItem(i);
                if (tempStack.isEmpty()) {
                    recipeTemp.setItem(i, ItemStack.EMPTY);
                    continue;
                }

                // get item from chest
                ItemStack fromChest = getAndRemoveItemFromHandler(chest, tempStack, 1,
                        checkMetadata, checkNbt);
                invShip.setStackInSlot(i + 12, fromChest);
                recipeTemp.setItem(i, invShip.getStackInSlot(i + 12));
            }

            // check recipeTemp valid
            Optional<CraftingRecipe> resultOpt = host.level().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, recipeTemp, host.level());

            if (resultOpt.isPresent()) {
                ItemStack resultTemp = resultOpt.get().assemble(recipeTemp, host.level().registryAccess());

                if (!InventoryHelper.matchTargetItem(resultTemp, result, checkMetadata, checkNbt)) {
                    break;
                }

                NonNullList<ItemStack> remainStacks = NonNullList.create();
                for (ItemStack remain : resultOpt.get().getRemainingItems(recipeTemp)) {
                    remainStacks.add(remain.copy());
                }

                maxCraft--;
                canAddExp = true;

                // move result to chest or drop
                moveItemToHandler(chest, resultTemp);
                if (!resultTemp.isEmpty()) {
                    dropItemOnGround(host, resultTemp);
                }

                // material -1
                for (int i = 0; i < 9; i++) {
                    ItemStack matStack = invShip.getStackInSlot(i + 12);
                    if (!matStack.isEmpty()) {
                        matStack.shrink(1);
                        if (matStack.isEmpty()) {
                            invShip.setStackInSlot(i + 12, ItemStack.EMPTY);
                        }
                    }
                }

                // move remaining items (bucket, bottle...)
                for (ItemStack remain : remainStacks) {
                    if (!remain.isEmpty()) {
                        moveItemToHandler(chest, remain);
                        if (!remain.isEmpty()) {
                            dropItemOnGround(host, remain);
                        }
                    }
                }
            } else {
                break;
            }
        }

        // add exp and consume grudge
        if (canAddExp) {
            host.addShipExp(ConfigHandler.expGainTask[3]);
            host.decrGrudgeNum(ConfigHandler.consumeGrudgeTask[3]);
            host.addMorale(-10);

            host.swing(InteractionHand.MAIN_HAND);

            if (host.getRandom().nextInt(5) == 0) {
                switch (host.getRandom().nextInt(5)) {
                    case 1:
                        host.applyParticleEmotion(2);
                        break;
                    case 2:
                        host.applyParticleEmotion(7);
                        break;
                    case 3:
                        host.applyParticleEmotion(13);
                        break;
                    case 4:
                        host.applyParticleEmotion(30);
                        break;
                    default:
                        host.applyParticleEmotion(21);
                        break;
                }
            }
        }
    }

    /**
     * Mining task:
     * Put pickaxe in mainhand (slot 22).
     * Generate ores per X ticks based on level and height.
     */
    public static void onUpdateMining(BasicEntityShip host) {
        if (host == null)
            return;

        ItemStack pickaxe = host.getCapaShipInventory().getStackInSlot(22);
        if (pickaxe.isEmpty() || !isToolEffective(pickaxe, 0))
            return;

        // check not moving
        Vec3 motion = host.getDeltaMovement();
        if (Math.abs(motion.x) > 0.1F || Math.abs(motion.z) > 0.1F || motion.y > 0.1F) {
            return;
        } else {
            // random move
            if ((host.getTickExisted() & 63) == 0) {
                double x = host.getX() + host.getRandom().nextInt(9) - 4;
                double y = host.getY() + host.getRandom().nextInt(5) - 2;
                double z = host.getZ() + host.getRandom().nextInt(9) - 4;
                taskMove(host, new TaskMoveRequest.MiningShuffle(new MovementPoint(x, y, z)));
                return;
            }
        }

        // swing arm and emotes
        if (host.getRandom().nextInt(5) > 2) {
            host.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

            if (host.getRandom().nextInt(10) > 8) {
                switch (host.getRandom().nextInt(5)) {
                    case 2:
                        host.applyParticleEmotion(11);
                        break;
                    case 3:
                        host.applyParticleEmotion(5);
                        break;
                    case 4:
                        host.applyParticleEmotion(30);
                        break;
                    default:
                        host.applyParticleEmotion(0);
                        break;
                }
            }
        }

        // finish mining
        if ((host.tickCount & 31) == 0
                && host.tickCount - host.getStateTimer(ID.T.TaskTime) > ConfigHandler.tickMining[0]
                + host.getRandom().nextInt(ConfigHandler.tickMining[1])) {
            int stone = 0;
            boolean canMine = false;

            // check nearby solid block > N
            for (int dy = -3; dy < 5 && !canMine; dy++) {
                for (int dx = -3; dx < 4 && !canMine; dx++) {
                    for (int dz = -3; dz < 4; dz++) {
                        BlockPos checkPos = new BlockPos(
                                Mth.floor(host.getX()) + dx,
                                Mth.floor(host.getY()) + dy,
                                Mth.floor(host.getZ()) + dz);
                        BlockState state = host.level().getBlockState(checkPos);

                        if (state.requiresCorrectToolForDrops()) {
                            stone++;
                        }
                        if (stone > 120) {
                            canMine = true;
                            break;
                        }
                    }
                }
            }

            if (!canMine)
                return;

            // generate mining result
            generateMiningResult(host);

            // add exp and consume grudge
            host.addShipExp(ConfigHandler.expGainTask[2]);
            host.decrGrudgeNum(ConfigHandler.consumeGrudgeTask[2]);
            host.addMorale(-200);

            switch (host.getRandom().nextInt(5)) {
                case 1:
                    host.applyParticleEmotion(11);
                    break;
                case 2:
                    host.applyParticleEmotion(14);
                    break;
                case 3:
                    host.applyParticleEmotion(4);
                    break;
                case 4:
                    host.applyParticleEmotion(30);
                    break;
                default:
                    host.applyParticleEmotion(0);
                    break;
            }

            host.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            host.setStateTimer(ID.T.TaskTime, host.tickCount);
        }
    }

    /**
     * Fishing task:
     * Put fishing rod in mainhand (slot 22).
     * Detect water block with depth >= 3 blocks.
     */
    public static void onUpdateFishing(BasicEntityShip host) {
        if (host == null || host.level().isClientSide())
            return;
        if (host.fishHook != null && host.fishHook.isRemoved()) {
            host.fishHook = null;
        }

        ItemStack rod = host.getCapaShipInventory().getStackInSlot(22);
        if (rod.isEmpty() || rod.getItem() != Items.FISHING_ROD)
            return;

        // check guard position
        // [PORT] 1.10.2 -> 1.20.1: the original used Y <= 0 as an "unset" sentinel, safe
        // when the world floor was Y=0. Y now reaches -64 and the whole deepslate layer
        // is negative, so validity comes from the guard flags instead.
        if (!host.hasGuardDestination())
            return;
        BlockPos pos = new BlockPos(host.getGuardedPos(0), host.getGuardedPos(1), host.getGuardedPos(2));
        if (host.getGuardedPos(4) != 1)
            return;

        // check dimension
        if (!host.isGuardedInCurrentDimension())
            return;

        // move to guard point
        if (host.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 10D) {
            taskMove(host, new TaskMoveRequest.FishingSpot(new MovementPoint(pos.getX() + 0.5D, pos.getY(),
                    pos.getZ() + 0.5D)));
            return;
        }

        // not in moving
        Vec3 motion = host.getDeltaMovement();
        if (Math.abs(motion.x) > 0.1F || Math.abs(motion.z) > 0.1F || motion.y > 0.1F)
            return;

        // check water block
        pos = BlockHelper.getNearbyLiquid(host, false, true, 5, 3);
        if (pos == null)
            return;

        // if no hook -> cast fishing rod
        if (host.fishHook == null) {
            host.swing(InteractionHand.MAIN_HAND);

            EntityShipFishingHook hook = new EntityShipFishingHook(ModEntities.FISHING_HOOK.get(), host.level());
            hook.setHost(host);
            hook.setPos(
                    pos.getX() + 0.1D + host.getRandom().nextDouble() * 0.8D,
                    pos.getY() + 1D,
                    pos.getZ() + 0.1D + host.getRandom().nextDouble() * 0.8D);
            if (!host.level().addFreshEntity(hook)) {
                return;
            }
            host.fishHook = hook;

            switch (host.getRandom().nextInt(4)) {
                case 1:
                    host.applyParticleEmotion(14);
                    break;
                case 2:
                    host.applyParticleEmotion(7);
                    break;
                case 3:
                    host.applyParticleEmotion(11);
                    break;
                default:
                    host.applyParticleEmotion(30);
                    break;
            }
            return;
        }

        // hook exists, wait random time
        if (!host.fishHook.isRemoved() && host.fishHook.tickCount > ConfigHandler.tickFishing[0]
                + host.getRandom().nextInt(Math.max(1, ConfigHandler.tickFishing[1]))) {
            generateFishingResult(host);
            host.fishHook.discard();

            host.addShipExp(ConfigHandler.expGainTask[1]);
            host.decrGrudgeNum(ConfigHandler.consumeGrudgeTask[1]);
            host.addMorale(300);

            switch (host.getRandom().nextInt(5)) {
                case 1:
                    host.applyParticleEmotion(1);
                    break;
                case 2:
                    host.applyParticleEmotion(7);
                    break;
                case 3:
                    host.applyParticleEmotion(16);
                    break;
                case 4:
                    host.applyParticleEmotion(30);
                    break;
                default:
                    host.applyParticleEmotion(0);
                    break;
            }

            host.swing(InteractionHand.MAIN_HAND);
        }

        // fishing timeout
        if (host.fishHook != null
                && host.fishHook.tickCount > ConfigHandler.tickFishing[0] + ConfigHandler.tickFishing[1]) {
            host.fishHook.discard();
        }
    }

    /**
     * Cooking task:
     * Smelt itemstack in mainhand (slot 22), optional fuel in offhand (slot 23).
     */
    public static void onUpdateCooking(BasicEntityShip host) {
        if (host == null)
            return;

        CapaShipInventory inv = host.getCapaShipInventory();
        ItemStack mainstack = inv.getStackInSlot(22);
        ItemStack offstack = inv.getStackInSlot(23);
        if (mainstack.isEmpty())
            return;

        // check guard position
        // [PORT] 1.10.2 -> 1.20.1: the original used Y <= 0 as an "unset" sentinel, safe
        // when the world floor was Y=0. Y now reaches -64 and the whole deepslate layer
        // is negative, so validity comes from the guard flags instead.
        if (!host.hasGuardDestination())
            return;
        BlockPos pos = new BlockPos(host.getGuardedPos(0), host.getGuardedPos(1), host.getGuardedPos(2));
        if (host.getGuardedPos(4) != 1)
            return;

        // check dimension
        if (!host.isGuardedInCurrentDimension())
            return;

        // check guard position is waypoint
        BlockEntity te = host.level().getBlockEntity(pos);
        if (!(te instanceof TileEntityWaypoint waypoint))
            return;

        // check waypoint has paired chest
        if (!waypoint.hasPairedChest())
            return;
        pos = waypoint.getPairedChest();

        // check paired chest is a furnace
        te = host.level().getBlockEntity(pos);
        if (!(te instanceof AbstractFurnaceBlockEntity furnace))
            return;

        // check distance
        if (host.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 25D) {
            taskMove(host, new TaskMoveRequest.ReturnToWaypoint(new CommandPos(host.getGuardedPos(0),
                    host.getGuardedPos(1), host.getGuardedPos(2))));
            return;
        }

        // check smelt recipe
        Optional<SmeltingRecipe> smeltRecipe = host.level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(mainstack), host.level());
        if (smeltRecipe.isEmpty())
            return;

        ItemStack resultStack = smeltRecipe.get().assemble(new SimpleContainer(mainstack),
                host.level().registryAccess());
        if (resultStack.isEmpty())
            return;

        int taskSide = host.getStateMinor(ID.M.TaskSide);
        TaskSideMask sideMask = new TaskSideMask(taskSide);
        boolean checkMetadata = sideMask.checkMetadata();
        boolean checkNbt = sideMask.checkNbt();
        List<IItemHandler> inputHandlers = InventoryHelper.getItemHandlersFromSides(furnace, taskSide, 0);
        List<IItemHandler> outputHandlers = InventoryHelper.getItemHandlersFromSides(furnace, taskSide, 1);
        List<IItemHandler> fuelHandlers = InventoryHelper.getItemHandlersFromSides(furnace, taskSide, 2);
        boolean swing = false;

        // Original 1.10.2 TaskHelper.java:654-658:
        // int[] inSlots = InventoryHelper.getSlotsFromSide(furnace, targetStack, taskSide, 0);
        // int[] outSlots = InventoryHelper.getSlotsFromSide(furnace, null, taskSide, 1);
        // int[] fuSlots = InventoryHelper.getSlotsFromSide(furnace, fuelStack, taskSide, 2);
        int targetSlot = findMatchingSlot(inv, mainstack, checkMetadata, checkNbt, 22, 23);
        if (targetSlot >= 0) {
            ItemStack toMove = inv.getStackInSlot(targetSlot);
            if (moveItemToConfiguredInventory(furnace, inputHandlers, taskSide, 0, toMove)) {
                if (toMove.isEmpty())
                    inv.setStackInSlot(targetSlot, ItemStack.EMPTY);
                swing = true;
            }
        }

        // Put fuel through the configured face-scoped handlers.
        if (!offstack.isEmpty()) {
            int fuelSlot = findMatchingSlot(inv, offstack, checkMetadata, checkNbt, 22, 23);
            if (fuelSlot >= 0) {
                ItemStack fuelToMove = inv.getStackInSlot(fuelSlot);
                if (moveItemToConfiguredInventory(furnace, fuelHandlers, taskSide, 2, fuelToMove)) {
                    if (fuelToMove.isEmpty())
                        inv.setStackInSlot(fuelSlot, ItemStack.EMPTY);
                    swing = true;
                }
            }
        }

        // Take one matching output stack through the configured face-scoped handlers.
        if (moveMatchingOutputToShip(outputHandlers, inv, host, resultStack, checkMetadata, checkNbt)) {
            swing = true;

            // add exp and consume grudge
            host.addShipExp(ConfigHandler.expGainTask[0]);
            host.decrGrudgeNum(ConfigHandler.consumeGrudgeTask[0]);
            host.addMorale(100);

            // generate charcoal by fail chance
            float failChance = ShipLevelRules.expeditionFailureChance(host.getLevel(), ConfigHandler.maxLevel);

            if (host.getRandom().nextFloat() < failChance) {
                ItemStack coal = new ItemStack(Items.CHARCOAL, 1);
                ItemEntity entityitem = new ItemEntity(host.level(),
                        pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D, coal);
                entityitem.setDeltaMovement(
                        host.getRandom().nextGaussian() * 0.05D,
                        host.getRandom().nextGaussian() * 0.05D + 0.2D,
                        host.getRandom().nextGaussian() * 0.05D);
                host.level().addFreshEntity(entityitem);
                host.applyEmotesReaction(6);
            } else if (host.getRandom().nextInt(7) == 0) {
                switch (host.getRandom().nextInt(5)) {
                    case 1:
                        host.applyParticleEmotion(1);
                        break;
                    case 2:
                        host.applyParticleEmotion(7);
                        break;
                    case 3:
                        host.applyParticleEmotion(16);
                        break;
                    case 4:
                        host.applyParticleEmotion(30);
                        break;
                    default:
                        host.applyParticleEmotion(0);
                        break;
                    }
            }
        }

        if (swing) {
            host.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Check tool is suitable for task. targetType: 0=pickaxe, 1=shovel, 2=axe
     */
    public static boolean isToolEffective(ItemStack stack, int targetType) {
        if (stack.isEmpty())
            return false;

        return switch (targetType) {
            case 0 -> stack.is(ItemTags.PICKAXES);
            case 1 -> stack.is(ItemTags.SHOVELS);
            case 2 -> stack.is(ItemTags.AXES);
            default -> false;
        };
    }

    /**
     * Generate mining result using ConfigMining loot tables.
     * Filters entries by dimension, biome, ship level, Y position, and tool tier.
     * Performs weighted random selection and applies fortune enchantment bonus.
     */
    @SuppressWarnings("deprecation")
    public static void generateMiningResult(LivingEntity host) {
        if (!(host instanceof BasicEntityShip ship))
            return;

        ItemStack pickaxe = ship.getCapaShipInventory().getStackInSlot(22);
        if (pickaxe.isEmpty())
            return;

        // check ConfigMining is loaded
        if (!ConfigMining.isLoaded()) {
            LogHelper.info("WARN: ConfigMining not loaded, skipping mining result");
            return;
        }

        // get dimension key (strip "minecraft:" prefix to match config format)
        String dimKey = ship.level().dimension().location().getPath(); // "overworld", "the_nether", "the_end"

        // get biome key (strip namespace prefix)
        String biomeKey = ConfigMining.GENERAL_BIOME;
        BlockPos shipPos = ship.blockPosition();
        Holder<net.minecraft.world.level.biome.Biome> biomeHolder = ship.level().getBiome(shipPos);
        Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>> biomeKeyOpt = biomeHolder
                .unwrapKey();
        if (biomeKeyOpt.isPresent()) {
            biomeKey = biomeKeyOpt.get().location().getPath();
        }

        // get entries matching dimension and biome
        List<ConfigMining.ItemEntry> entries = ConfigMining.getEntries(dimKey, biomeKey);
        if (entries.isEmpty())
            return;

        // get ship stats for filtering
        int shipLevel = ship.getLevel();
        int yPos = (int) ship.getY();
        int toolLevel = getPickaxeTier(pickaxe);
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, pickaxe);

        // filter entries by ship level, Y position, and tool tier
        List<ConfigMining.ItemEntry> filtered = new ArrayList<>();
        for (ConfigMining.ItemEntry entry : entries) {
            if (shipLevel >= entry.lvShip() && yPos <= entry.lvHeight() && toolLevel >= entry.lvTool()) {
                filtered.add(entry);
            }
        }

        if (filtered.isEmpty())
            return;

        // build cumulative weight list for weighted random selection
        List<Integer> cumulativeWeights = new ArrayList<>();
        cumulativeWeights.add(filtered.get(0).weight());
        for (int i = 1; i < filtered.size(); i++) {
            cumulativeWeights.add(cumulativeWeights.get(i - 1) + filtered.get(i).weight());
        }

        // roll weighted random
        int totalWeight = cumulativeWeights.get(cumulativeWeights.size() - 1);
        int roll = ship.getRandom().nextInt(totalWeight);
        int selectedIndex = 0;
        for (int i = 0; i < cumulativeWeights.size(); i++) {
            if (roll < cumulativeWeights.get(i)) {
                selectedIndex = i;
                break;
            }
        }

        // get selected entry
        ConfigMining.ItemEntry selected = filtered.get(selectedIndex);

        // look up item by resource location
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.tryParse(selected.itemName()));
        if (item == null || item == Items.AIR)
            return;

        // calculate stack size
        int stackSize = selected.min();
        if (selected.max() > selected.min()) {
            stackSize = selected.min() + ship.getRandom().nextInt(selected.max() - selected.min() + 1);
        }

        // apply fortune enchantment bonus
        if (selected.enchant() > 0F && fortuneLevel > 0) {
            stackSize = (int) (stackSize * (1F + fortuneLevel * selected.enchant()));
        }

        if (stackSize < 1)
            stackSize = 1;

        // create and add item
        ItemStack result = new ItemStack(item, stackSize);
        if (!ship.getCapaShipInventory().addItemStackToInventory(result)) {
            dropItemOnGround(ship, result);
        }
    }

    /**
     * Generate fishing result using vanilla loot tables.
     */
    @SuppressWarnings("deprecation")
    public static void generateFishingResult(LivingEntity host) {
        float luck = 0F;
        ServerLevel serverLevel;

        if (!(host.level() instanceof ServerLevel))
            return;
        serverLevel = (ServerLevel) host.level();

        if (host instanceof BasicEntityShip ship) {
            // get luck from enchantment
            ItemStack mainHand = ship.getCapaShipInventory().getStackInSlot(22);
            ItemStack offHand = ship.getCapaShipInventory().getStackInSlot(23);
            int lv1 = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_LUCK, mainHand);
            int lv2 = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_LUCK, offHand);
            luck = Math.max(lv1, lv2);
            luck += BuffHelper.getPotionLevel(host, 26);

            // add level modifier
            luck += ShipLevelRules.expeditionLuckContribution(ship.getLevel(), ConfigHandler.maxLevel);
        } else if (!(host instanceof IShipAttackBase)) {
            return;
        }

        // get fishing loot table
        LootTable lootTable = serverLevel.getServer().getLootData()
                .getLootTable(BuiltInLootTables.FISHING);

        // The task already checks its deep-water fishing spot. A context-only vanilla
        // bobber lets the table's fishing predicates see that eligibility and its owner.
        // It is never spawned, ticked, or retrieved as a player's bobber.
        FishingHook contextHook = new FishingHook(EntityType.FISHING_BOBBER, serverLevel);
        contextHook.setOwner(host);
        contextHook.setPos(host instanceof BasicEntityShip ship && ship.fishHook != null
                ? ship.fishHook.position() : host.position());
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, contextHook.position())
                .withParameter(LootContextParams.TOOL, host instanceof BasicEntityShip ship
                        ? ship.getCapaShipInventory().getStackInSlot(22)
                        : ItemStack.EMPTY)
                .withParameter(LootContextParams.THIS_ENTITY, contextHook)
                .withParameter(LootContextParams.KILLER_ENTITY, host)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);

        for (ItemStack itemstack : lootTable.getRandomItems(params)) {
            if (host instanceof BasicEntityShip ship) {
                if (!ship.getCapaShipInventory().addItemStackToInventory(itemstack)) {
                    dropItemOnGround(host, itemstack);
                }
            } else {
                dropItemOnGround(host, itemstack);
            }
        }
    }

    /** Compatibility entry; the resource gate owns permission, schedules and success notification. */
    public static void onUpdatePumping(BasicEntityShip ship) {
        com.lulan.shincolle.ai.ShipResourceGate.tickPump(ship);
    }

    public enum PumpXpResult { NO_OP, COMMITTED, EXTERNAL_CONTRACT_VIOLATION }

    /** Executes one authorized XP update. It never grants ship experience or liquid particles. */
    public static PumpXpResult pumpExperience(BasicEntityShip ship) {
        CapaShipInventory inventory = ship.getCapaShipInventory();
        java.util.OptionalInt bottleSlot = java.util.OptionalInt.empty();
        for (int slot = CapaShipInventory.EquipSlots; slot < inventory.getSlots(); slot++) {
            if (!inventory.isSlotAvailable(slot)) break;
            if (inventory.getStackInSlot(slot).is(Items.GLASS_BOTTLE)) {
                bottleSlot = java.util.OptionalInt.of(slot);
                break;
            }
        }
        if (bottleSlot.isEmpty()) return PumpXpResult.NO_OP;
        // Legacy storage starts unset; only non-negative, value-semantic XP enters the resource rules.
        int remainder = Math.max(0, ship.getStateMinor(ID.M.XP));
        boolean changed = false;
        for (ExperienceOrb orb : ship.level().getEntitiesOfClass(ExperienceOrb.class, ship.getBoundingBox().inflate(7D))) {
            if (!orb.isAlive()) continue;
            if (ship.distanceToSqr(orb) > 9D) {
                Dist4d pull = CalcHelper.getDistanceFromA2B(ship, orb);
                orb.push(pull.x * -0.25D, pull.y * -0.25D, pull.z * -0.25D);
                changed = true;
                continue;
            }
            CompoundTag saved = new CompoundTag();
            try {
                orb.addAdditionalSaveData(saved);
            } catch (RuntimeException externalSaveFailure) {
                continue;
            }
            if (!saved.contains("Count", Tag.TAG_INT)) continue;
            var total = com.lulan.shincolle.ai.domain.task.ShipResourceRules.collectedXp(
                    remainder, orb.getValue(), saved.getInt("Count"));
            if (total.isEmpty()) continue;
            remainder = total.getAsInt();
            ship.setStateMinor(ID.M.XP, remainder);
            orb.discard();
            changed = true;
            ship.level().playSound(null, ship.getX(), ship.getY(), ship.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.1F,
                    0.5F * ((ship.getRandom().nextFloat() - ship.getRandom().nextFloat()) * 0.7F + 1.8F));
        }
        if (!com.lulan.shincolle.ai.domain.task.ShipResourceRules.canBottle(remainder)) {
            return changed ? PumpXpResult.COMMITTED : PumpXpResult.NO_OP;
        }
        int inputSlot = bottleSlot.getAsInt();
        ItemStack input = inventory.getStackInSlot(inputSlot);
        ItemStack reduced = input.copy();
        reduced.shrink(1);
        ItemStack output = new ItemStack(Items.EXPERIENCE_BOTTLE);
        java.util.OptionalInt outputSlot = java.util.OptionalInt.empty();
        for (int pass = 0; pass < 2 && outputSlot.isEmpty(); pass++) {
            for (int slot = CapaShipInventory.EquipSlots; slot < inventory.getSlots(); slot++) {
                if (!inventory.isSlotAvailable(slot)) break;
                ItemStack candidate = slot == inputSlot ? reduced : inventory.getStackInSlot(slot);
                boolean accepts = pass == 0 ? ItemStack.isSameItemSameTags(candidate, output)
                        && candidate.getCount() < candidate.getMaxStackSize() : candidate.isEmpty();
                if (accepts) {
                    outputSlot = java.util.OptionalInt.of(slot);
                    if (!candidate.isEmpty()) {
                        output = candidate.copy();
                        output.grow(1);
                    }
                    break;
                }
            }
        }
        if (outputSlot.isEmpty()) {
            ItemEntity dropped = new ItemEntity(ship.level(), ship.getX(), ship.getY(), ship.getZ(), output);
            dropped.setDeltaMovement(ship.getRandom().nextGaussian() * 0.08D,
                    ship.getRandom().nextGaussian() * 0.05D + 0.2D, ship.getRandom().nextGaussian() * 0.08D);
            boolean spawned;
            try {
                spawned = ship.level().addFreshEntity(dropped);
            } catch (RuntimeException externalSpawnFailure) {
                return PumpXpResult.EXTERNAL_CONTRACT_VIOLATION;
            }
            if (!spawned) return changed ? PumpXpResult.COMMITTED : PumpXpResult.NO_OP;
        }
        inventory.setStackInSlot(inputSlot, reduced);
        if (outputSlot.isPresent()) inventory.setStackInSlot(outputSlot.getAsInt(), output);
        ship.setStateMinor(ID.M.XP, remainder - 8);
        return PumpXpResult.COMMITTED;
    }

    // ========== Utility Methods ==========

    /**
     * Get pickaxe tier level from ItemStack.
     * 0=wood/gold, 1=stone, 2=iron, 3=diamond, 4=netherite.
     */
    private static int getPickaxeTier(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof TieredItem tiered))
            return 0;

        net.minecraft.world.item.Tier tier = tiered.getTier();
        if (tier == Tiers.NETHERITE)
            return 4;
        if (tier == Tiers.DIAMOND)
            return 3;
        if (tier == Tiers.IRON)
            return 2;
        if (tier == Tiers.STONE)
            return 1;
        return 0; // WOOD, GOLD, or unknown
    }

    /**
     * Get dimension key string from numeric ID (for legacy compatibility).
     */
    private static String getDimensionKey(int dimId) {
        return switch (dimId) {
            case -1 -> "minecraft:the_nether";
            case 0 -> "minecraft:overworld";
            case 1 -> "minecraft:the_end";
            default -> "minecraft:overworld";
        };
    }

    /**
     * Find slot in ship inventory matching target item, excluding specific slots.
     */
    private static int findMatchingSlot(CapaShipInventory inv, ItemStack target, int... excludeSlots) {
        return findMatchingSlot(inv, target, false, false, excludeSlots);
    }

    /**
     * Find a TaskSide-configured item match, excluding specific slots.
     */
    private static int findMatchingSlot(CapaShipInventory inv, ItemStack target,
                                        boolean checkMetadata, boolean checkNbt, int... excludeSlots) {
        for (int i = 0; i < inv.getSlots(); i++) {
            boolean excluded = false;
            for (int ex : excludeSlots) {
                if (i == ex) {
                    excluded = true;
                    break;
                }
            }
            if (excluded)
                continue;

            ItemStack stack = inv.getStackInSlot(i);
            if (InventoryHelper.matchTargetItem(stack, target, checkMetadata, checkNbt)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if item exists in ship inventory within slot range.
     */
    private static boolean checkItemInShipInventory(CapaShipInventory inv, net.minecraft.world.item.Item item,
                                                    int startSlot, int endSlot) {
        for (int i = startSlot; i <= endSlot && i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).getItem() == item) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if item with specific EquipMeta exists in ship inventory within slot
     * range.
     * In the original 1.10.2 code, this was done via item damage/meta values.
     * In 1.20.1, equipment variants are stored in the "EquipMeta" NBT tag.
     *
     * @param inv          the ship inventory to search
     * @param item         the item type to match
     * @param requiredMeta the EquipMeta NBT value to match
     * @param startSlot    the first slot index to check (inclusive)
     * @param endSlot      the last slot index to check (inclusive)
     * @return true if a matching item with the correct meta is found
     */
    private static boolean checkItemWithMetaInShipInventory(CapaShipInventory inv, net.minecraft.world.item.Item item,
                                                            int requiredMeta, int startSlot, int endSlot) {
        for (int i = startSlot; i <= endSlot && i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() == item) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("EquipMeta") && tag.getInt("EquipMeta") == requiredMeta) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get and remove matching item from IItemHandler.
     */
    private static ItemStack getAndRemoveItemFromHandler(IItemHandler handler, ItemStack target, int amount,
                                                         boolean checkMetadata, boolean checkNbt) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (InventoryHelper.matchTargetItem(stack, target, checkMetadata, checkNbt)) {
                return handler.extractItem(i, amount, false);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Move item stack into IItemHandler, returns remainder.
     */
    private static void moveItemToHandler(IItemHandler handler, ItemStack stack) {
        for (int i = 0; i < handler.getSlots() && !stack.isEmpty(); i++) {
            ItemStack offered = stack.copy();
            ItemStack remainder = handler.insertItem(i, offered, false);
            int inserted = offered.getCount() - remainder.getCount();
            if (inserted > 0) {
                stack.shrink(inserted);
            }
        }
    }

    private static boolean moveItemToHandlers(List<IItemHandler> handlers, ItemStack stack) {
        int oldCount = stack.getCount();
        for (IItemHandler handler : handlers) {
            moveItemToHandler(handler, stack);
            if (stack.isEmpty())
                break;
        }
        return stack.getCount() < oldCount;
    }

    private static boolean moveItemToConfiguredInventory(BlockEntity provider, List<IItemHandler> handlers,
                                                         int taskSide, int type, ItemStack stack) {
        if (provider instanceof WorldlyContainer container) {
            return InventoryHelper.insertItemThroughTaskSides(container, stack, taskSide, type);
        }
        return moveItemToHandlers(handlers, stack);
    }

    private static boolean moveMatchingOutputToShip(List<IItemHandler> handlers, CapaShipInventory inventory,
                                                    BasicEntityShip host, ItemStack target,
                                                    boolean checkMetadata, boolean checkNbt) {
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack visible = handler.getStackInSlot(slot);
                if (!InventoryHelper.matchTargetItem(visible, target, checkMetadata, checkNbt))
                    continue;

                ItemStack simulated = handler.extractItem(slot, visible.getCount(), true);
                if (simulated.isEmpty() || !inventory.canAddItemStackToInventory(simulated))
                    continue;

                ItemStack extracted = handler.extractItem(slot, simulated.getCount(), false);
                if (!extracted.isEmpty() && inventory.addItemStackToInventory(extracted)) {
                    return true;
                }
                if (!extracted.isEmpty()) {
                    dropItemOnGround(host, extracted);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Drop item on ground near entity.
     */
    public static void dropItemOnGround(LivingEntity host, ItemStack stack) {
        if (stack.isEmpty() || host.level().isClientSide())
            return;

        ItemEntity item = new ItemEntity(host.level(), host.getX(), host.getY(), host.getZ(), stack.copy());
        item.setDeltaMovement(
                host.getRandom().nextGaussian() * 0.08D,
                host.getRandom().nextGaussian() * 0.05D + 0.2D,
                host.getRandom().nextGaussian() * 0.08D);
        host.level().addFreshEntity(item);
        stack.setCount(0);
    }
}
