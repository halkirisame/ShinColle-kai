package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.client.gui.inventory.ContainerVolCore;
import com.lulan.shincolle.client.gui.inventory.SlotVolCore;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.tileentity.TileEntityVolCore;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VolCoreGameTests {

    private static final BlockPos CORE_POS = new BlockPos(2, 2, 2);

    private VolCoreGameTests() {
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void liquidRecoveryRequiresSubmersionCombatDelayAndRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileEntityVolCore tile = placeWorkingCore(helper);
        BlockPos wetPos = helper.absolutePos(CORE_POS.offset(1, 0, 0));
        BlockPos combatPos = helper.absolutePos(CORE_POS.offset(-1, 0, 0));
        level.setBlock(wetPos, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(combatPos, Blocks.WATER.defaultBlockState(), 3);

        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip wetShip = spawnShip(helper, entities, wetPos);
            BasicEntityShip combatShip = spawnShip(helper, entities, combatPos);
            combatShip.setCombatTick(100);
            BasicEntityShip dryShip = spawnShip(helper, entities, helper.absolutePos(CORE_POS.offset(0, 0, 3)));
            BasicEntityShip distantShip = spawnShip(helper, entities,
                    helper.absolutePos(CORE_POS.offset(7, 0, 0)));
            float wetHealth = wetShip.getHealth();
            float combatHealth = combatShip.getHealth();
            float dryHealth = dryShip.getHealth();
            float distantHealth = distantShip.getHealth();
            int wetMorale = wetShip.getMorale();
            int combatMorale = combatShip.getMorale();
            int dryMorale = dryShip.getMorale();
            int distantMorale = distantShip.getMorale();

            runServerTicks(level, tile, 48);

            assertTrue(wetShip.getHealth() > wetHealth, "Submerged in-range ship did not recover health");
            assertTrue(wetShip.getMorale() == wetMorale + 80,
                    "Submerged in-range ship did not recover 80 morale");
            assertUnchanged(combatShip, combatHealth, combatMorale, "Recently fighting submerged ship");
            assertUnchanged(dryShip, dryHealth, dryMorale, "Dry in-range ship");
            assertUnchanged(distantShip, distantHealth, distantMorale, "Out-of-range ship");
            helper.succeed();
        }
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void dryCoreIgnitesAndDamagesNonShipLivingEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileEntityVolCore tile = placeWorkingCore(helper);

        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Cow cow = entities.add(EntityType.COW.create(level));
            if (cow == null) {
                throw new AssertionError("Failed to create cow");
            }
            cow.moveTo(helper.absoluteVec(new Vec3(4.5D, 2.0D, 2.5D)));
            cow.setNoGravity(true);
            if (!level.addFreshEntity(cow)) {
                throw new AssertionError("Failed to add cow");
            }
            float health = cow.getHealth();

            runServerTicks(level, tile, 48);

            assertTrue(cow.isOnFire(), "Dry Volcano Core did not ignite cow");
            assertTrue(cow.getHealth() < health, "Dry Volcano Core did not damage cow");
            helper.succeed();
        }
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void fuelSlotAndGrudgeBlockPowerMatchLegacyRules(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);

        assertTrue(SlotVolCore.isValidFuel(new ItemStack(ModItems.GRUDGE.get())),
                "Grudge should be valid Volcano Core fuel");
        assertTrue(SlotVolCore.isValidFuel(new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get())),
                "Grudge Block should be valid Volcano Core fuel");
        assertTrue(!SlotVolCore.isValidFuel(new ItemStack(ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get())),
                "Grudge Heavy Block should not be valid Volcano Core fuel");
        assertTrue(!SlotVolCore.isValidFuel(new ItemStack(Items.COAL)),
                "Coal should not be valid Volcano Core fuel");
        assertTrue(!SlotVolCore.isValidFuel(new ItemStack(Items.LAVA_BUCKET)),
                "Lava Bucket should not be valid Volcano Core fuel");

        tile.getInventory().setStackInSlot(0, new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get()));
        runServerTicks(helper.getLevel(), tile, 32);

        int expectedPower = (int) tile.getFuelMagni() * 9;
        assertTrue(tile.getPowerRemained() == expectedPower,
                "Grudge Block expected " + expectedPower + " power but got " + tile.getPowerRemained());
        assertTrue(tile.getInventory().getStackInSlot(0).isEmpty(), "Consumed Grudge Block remained in slot");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void placedCoreRejectsStrangerUseAndRemoval(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer owner = player(helper, "vol_owner", false, false);
        FakePlayer stranger = player(helper, "vol_stranger", false, false);
        placeAs(tile, owner);
        ContainerVolCore menu = new ContainerVolCore(1, stranger.getInventory(), tile);
        helper.assertTrue(!TeamHelper.checkIsAlly(TeamHelper.getPlayerUID(owner), TeamHelper.getPlayerUID(stranger)),
                "Fixture players must not be allied");
        helper.assertTrue(menu.stillValid(owner), "Owner must be allowed to use core");
        helper.assertTrue(!menu.stillValid(stranger), "Stranger must not be allowed to use owned core");
        var originalMenu = stranger.containerMenu;
        tile.getBlockState().use(helper.getLevel(), stranger, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(tile.getBlockPos()), Direction.UP, tile.getBlockPos(), false));
        helper.assertTrue(stranger.containerMenu == originalMenu, "Stranger must not open core screen");
        helper.assertTrue(!removeAs(tile, stranger), "Stranger must not remove owned core");
        helper.assertTrue(helper.getLevel().getBlockEntity(tile.getBlockPos()) == tile,
                "Rejected removal must leave core in world");
        tile.getBlockState().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(tile.getBlockPos()), Direction.UP, tile.getBlockPos(), false));
        helper.assertTrue(owner.containerMenu instanceof ContainerVolCore,
                "Owner interaction must open core menu");
        owner.closeContainer();
        helper.assertTrue(removeAs(tile, owner), "Owner must be allowed to remove core");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void allyMayUseButCannotRemoveCore(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer owner = player(helper, "vol_ally_owner", false, false);
        FakePlayer ally = player(helper, "vol_ally", false, false);
        int ownerUid = TeamHelper.getPlayerUID(owner);
        int allyUid = TeamHelper.getPlayerUID(ally);
        ServerDataManager.setTeamData(new TeamData(ownerUid, "VolOwner", "vol_ally_owner"));
        ServerDataManager.setTeamData(new TeamData(allyUid, "VolAlly", "vol_ally"));
        try {
            ServerDataManager.teamAddAlly(ownerUid, allyUid);
            helper.assertTrue(TeamHelper.checkIsAlly(ownerUid, allyUid), "Fixture alliance was not created");
            placeAs(tile, owner);
            helper.assertTrue(new ContainerVolCore(1, ally.getInventory(), tile).stillValid(ally),
                    "Ally must be allowed to use core");
            helper.assertTrue(!removeAs(tile, ally), "Alliance must not grant removal permission");
            helper.assertTrue(helper.getLevel().getBlockEntity(tile.getBlockPos()) == tile,
                    "Ally removal must leave core in world");
            helper.succeed();
        } finally {
            ServerDataManager.removeTeamData(ownerUid);
            ServerDataManager.removeTeamData(allyUid);
        }
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void ownerPersistenceLegacyAndPrivilegedRemoval(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer owner = player(helper, "vol_saved_owner", false, false);
        FakePlayer stranger = player(helper, "vol_saved_other", false, false);
        placeAs(tile, owner);
        CompoundTag saved = tile.saveWithoutMetadata();
        helper.assertTrue(saved.hasUUID("OwnerUUID") && saved.getUUID("OwnerUUID").equals(owner.getUUID()),
                "Placed owner UUID must be saved");
        helper.assertTrue(saved.getInt("OwnerPlayerUID") == TeamHelper.getPlayerUID(owner),
                "Placed owner UID must be saved");
        helper.getLevel().removeBlock(tile.getBlockPos(), false);
        tile = placeCore(helper);
        tile.load(saved);
        helper.assertTrue(new ContainerVolCore(1, owner.getInventory(), tile).stillValid(owner),
                "Reloaded owner must retain use permission");
        helper.assertTrue(!new ContainerVolCore(2, stranger.getInventory(), tile).stillValid(stranger),
                "Reload must preserve protection");
        for (boolean creative : new boolean[]{false, true}) {
            FakePlayer privileged = player(helper, creative ? "vol_creative" : "vol_operator", !creative, creative);
            helper.assertTrue(new ContainerVolCore(3, privileged.getInventory(), tile).stillValid(privileged),
                    "Operator or creative player must be allowed to use core");
            helper.assertTrue(removeAs(tile, privileged), "Operator or creative player must be allowed to remove core");
            tile = placeCore(helper);
            tile.load(saved);
        }
        saved.remove("OwnerUUID");
        saved.remove("OwnerPlayerUID");
        tile.load(saved);
        ContainerVolCore legacyMenu = new ContainerVolCore(4, stranger.getInventory(), tile);
        helper.assertTrue(legacyMenu.stillValid(stranger), "Legacy ownerless core must be usable");
        helper.assertTrue(legacyMenu.stillValid(owner), "Using ownerless core must not claim it");
        helper.assertTrue(!tile.saveWithoutMetadata().hasUUID("OwnerUUID"), "Ownerless use must not record an owner");
        helper.assertTrue(removeAs(tile, stranger), "Legacy ownerless core must be removable");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void automationAcceptsOnlyFuelOnEveryFaceAndInvalidates(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        IItemHandler expected = tile.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        helper.assertTrue(expected != null, "Unsided item handler must exist");
        Direction[] faces = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST};
        for (Direction face : faces) {
            IItemHandler handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
            helper.assertTrue(handler == expected && handler.getSlots() == 9,
                    "Every face must expose the same nine-slot handler: " + face);
            for (int slot = 0; slot < 9; slot++) {
                ItemStack coal = new ItemStack(Items.COAL, 3);
                helper.assertTrue(!handler.isItemValid(slot, coal)
                                && handler.insertItem(slot, coal, false).getCount() == 3
                                && handler.getStackInSlot(slot).isEmpty(), "Coal must be rejected");
                for (ItemStack fuel : new ItemStack[]{new ItemStack(ModItems.GRUDGE.get(), 2),
                        new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get(), 2)}) {
                    helper.assertTrue(handler.isItemValid(slot, fuel), "Fuel must be valid on every slot");
                    helper.assertTrue(handler.insertItem(slot, fuel, true).isEmpty()
                                    && handler.getStackInSlot(slot).isEmpty(), "Simulated insert must not mutate");
                    helper.assertTrue(handler.insertItem(slot, fuel, false).isEmpty(), "Fuel insert failed");
                    helper.assertTrue(handler.extractItem(slot, 2, true).getCount() == 2
                                    && handler.getStackInSlot(slot).getCount() == 2, "Simulated extract must not mutate");
                    helper.assertTrue(handler.extractItem(slot, 2, false).getCount() == 2
                                    && handler.getStackInSlot(slot).isEmpty(), "All fuel slots must allow extraction");
                }
            }
        }
        var capability = tile.getCapability(ForgeCapabilities.ITEM_HANDLER);
        tile.invalidateCaps();
        helper.assertTrue(!capability.isPresent()
                        && !tile.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                "Invalidated handler must no longer resolve");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core", timeoutTicks = 160)
    public static void hoppersInsertFromAboveAndSideAndExtractBelow(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            TileEntityVolCore tile = placeCore(helper);
            tile.setPowerRemained(tile.getPowerMax());
            BlockPos top = tile.getBlockPos().above();
            BlockPos side = tile.getBlockPos().east();
            helper.getLevel().setBlock(top, Blocks.HOPPER.defaultBlockState(), 3);
            helper.getLevel().setBlock(side, Blocks.HOPPER.defaultBlockState()
                    .setValue(HopperBlock.FACING, Direction.WEST), 3);
            HopperBlockEntity topHopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(top);
            HopperBlockEntity sideHopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(side);
            topHopper.setItem(0, new ItemStack(ModItems.GRUDGE.get()));
            sideHopper.setItem(0, new ItemStack(ModItems.GRUDGE_BLOCK_ITEM.get()));
            helper.startSequence().thenIdle(30).thenExecute(() -> {
                helper.assertTrue(topHopper.isEmpty() && sideHopper.isEmpty(),
                        "Top and side hoppers must transfer fuel to core");
                helper.assertTrue(countFuel(tile, ModItems.GRUDGE.get()) == 1
                                && countFuel(tile, ModItems.GRUDGE_BLOCK_ITEM.get()) == 1,
                        "Transferred unconsumed fuel must remain in core");
                BlockPos bottom = tile.getBlockPos().below();
                helper.getLevel().setBlock(bottom, Blocks.HOPPER.defaultBlockState(), 3);
            }).thenIdle(30).thenExecute(() -> {
                HopperBlockEntity bottom = (HopperBlockEntity) helper.getLevel().getBlockEntity(tile.getBlockPos().below());
                helper.assertTrue(countFuel(tile, ModItems.GRUDGE.get()) == 0
                                && countFuel(tile, ModItems.GRUDGE_BLOCK_ITEM.get()) == 0,
                        "Bottom hopper must extract all unconsumed fuel");
                helper.assertTrue(bottom.countItem(ModItems.GRUDGE.get()) == 1
                                && bottom.countItem(ModItems.GRUDGE_BLOCK_ITEM.get()) == 1,
                        "Bottom hopper must receive the extracted fuel");
                helper.succeed();
            });
        }, Vec3.atCenterOf(CORE_POS));
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void coreHardnessAndEffectiveBlastResistance(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        helper.assertTrue(tile.getBlockState().getDestroySpeed(helper.getLevel(), tile.getBlockPos()) == 6.0F,
                "Core hardness must be 6");
        helper.assertTrue(tile.getBlockState().getBlock().getExplosionResistance() == 360.0F,
                "Core effective blast resistance must be 360");
        helper.assertTrue(!tile.getBlockState().requiresCorrectToolForDrops(),
                "Core must not require a tool for drops");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void menuRejectsRemovedReplacedAndDistantCore(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer player = player(helper, "vol_menu", false, false);
        ContainerVolCore menu = new ContainerVolCore(1, player.getInventory(), tile);
        helper.assertTrue(menu.stillValid(player), "Near intact ownerless core must be usable");
        player.setPos(Vec3.atCenterOf(tile.getBlockPos()).add(8.01D, 0, 0));
        helper.assertTrue(!menu.stillValid(player), "Core beyond eight blocks must be unusable");
        player.setPos(Vec3.atCenterOf(tile.getBlockPos()));
        helper.getLevel().removeBlock(tile.getBlockPos(), false);
        helper.assertTrue(!menu.stillValid(player), "Removed core must invalidate menu");
        TileEntityVolCore replacement = placeCore(helper);
        helper.assertTrue(replacement != tile, "Fixture must create a distinct replacement");
        helper.assertTrue(!menu.stillValid(player), "Replacement must not revive stale menu");
        tile.clearRemoved();
        helper.assertTrue(!tile.isRemoved() && !menu.stillValid(player),
                "A nonremoved stale instance must still fail the identity check");
        helper.assertTrue(new ContainerVolCore(2, player.getInventory(), replacement).stillValid(player),
                "Fresh replacement menu must be valid");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void fuelDisplayTransportsBothIntHalves(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer player = player(helper, "vol_display", false, false);
        int[] maximum = {9600};
        TileEntityVolCore source = new TileEntityVolCore(tile.getBlockPos(), tile.getBlockState()) {
            @Override
            public int getPowerMax() {
                return maximum[0];
            }
        };
        CompoundTag sourceState = source.saveWithoutMetadata();
        sourceState.putBoolean("CanWork", true);
        source.load(sourceState);
        ContainerVolCore server = new ContainerVolCore(1, player.getInventory(), source);
        helper.assertTrue(ContainerVolCore.DATA_COUNT == 6,
                "Fuel remaining and maximum must each have two data slots, plus two flags");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(tile.getBlockPos());
        ContainerVolCore client;
        try {
            client = new ContainerVolCore(2, player.getInventory(), buffer);
        } finally {
            buffer.release();
        }
        server.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu menu, int index, ItemStack stack) {
            }

            @Override
            public void dataChanged(AbstractContainerMenu menu, int index, int value) {
                client.setData(index, (short) value);
            }
        });
        for (int max : new int[]{9600, 40000, 65536, 96000}) {
            maximum[0] = max;
            for (int remaining : new int[]{310, 40000, 65536, 96000}) {
                source.setPowerRemained(remaining);
                source.setBtnActive(remaining == 65536);
                server.broadcastChanges();
                helper.assertTrue(client.getPowerRemained() == remaining && client.getPowerMax() == max,
                        "Short-transport fuel mismatch: remaining=" + remaining + ", max=" + max);
                helper.assertTrue(client.isBtnActive() == source.isBtnActive()
                                && client.isWorking() == source.isWorking(), "Fuel transport must preserve flags");
                if (remaining == 310 && max == 9600) {
                    helper.assertTrue(client.getPowerRemainingScaled(31) == 1,
                            "GUI getter must avoid double rounding at 310/9600");
                    helper.assertTrue(client.getFuelPercent() == 32, "Percentage must use the transported values");
                }
            }
        }
        for (int max : new int[]{0, -1}) {
            maximum[0] = max;
            server.broadcastChanges();
            helper.assertTrue(client.getPowerRemainingScaled(31) == 0 && client.getFuelPercent() == 0,
                    "Nonpositive maximum must produce an empty bar and zero percentage");
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void hotbarNonFuelFallsBackToPlayerInventory(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer player = player(helper, "vol_coal_shift", false, false);
        ContainerVolCore menu = new ContainerVolCore(1, player.getInventory(), tile);
        menu.getSlot(36).set(new ItemStack(Items.COAL, 16));
        helper.assertTrue(!menu.quickMoveStack(player, 36).isEmpty(), "Coal fallback must report a move");
        helper.assertTrue(menu.getSlot(9).getItem().is(Items.COAL)
                        && menu.getSlot(9).getItem().getCount() == 16 && !menu.getSlot(36).hasItem(),
                "Hotbar coal must move to first player inventory slot");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void quickMoveUsesLegacyCoreAndInventoryDirections(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer player = player(helper, "vol_direction", false, false);
        ContainerVolCore menu = new ContainerVolCore(1, player.getInventory(), tile);
        menu.getSlot(0).set(new ItemStack(ModItems.GRUDGE.get(), 16));
        menu.quickMoveStack(player, 0);
        helper.assertTrue(!menu.getSlot(0).hasItem() && menu.getSlot(9).getItem().getCount() == 16,
                "Core fuel must move to first player inventory slot");
        menu.quickMoveStack(player, 9);
        helper.assertTrue(!menu.getSlot(9).hasItem() && menu.getSlot(8).getItem().getCount() == 16,
                "Player inventory fuel must move to last core slot");
        menu.getSlot(9).set(new ItemStack(Items.COAL, 16));
        helper.assertTrue(menu.quickMoveStack(player, 9).isEmpty() && menu.getSlot(9).getItem().getCount() == 16,
                "Player inventory non-fuel must not move");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "vol_core")
    public static void hotbarFuelPrioritizesCoreThenFallsBackWithRemainder(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        FakePlayer player = player(helper, "vol_priority", false, false);
        ContainerVolCore menu = new ContainerVolCore(1, player.getInventory(), tile);
        menu.getSlot(36).set(new ItemStack(ModItems.GRUDGE.get(), 16));
        menu.quickMoveStack(player, 36);
        helper.assertTrue(menu.getSlot(0).getItem().getCount() == 16 && !menu.getSlot(36).hasItem(),
                "Hotbar fuel must start at core slot zero");
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(9).set(new ItemStack(ModItems.GRUDGE.get()));
        menu.getSlot(36).set(new ItemStack(ModItems.GRUDGE.get(), 16));
        menu.quickMoveStack(player, 36);
        helper.assertTrue(menu.getSlot(0).getItem().getCount() == 16
                        && menu.getSlot(9).getItem().getCount() == 1 && !menu.getSlot(36).hasItem(),
                "Existing player fuel stack must not steal core refill");
        menu.getSlot(0).set(new ItemStack(ModItems.GRUDGE.get(), 63));
        for (int slot = 1; slot < 9; slot++) {
            menu.getSlot(slot).set(new ItemStack(ModItems.GRUDGE.get(), 64));
        }
        menu.getSlot(36).set(new ItemStack(ModItems.GRUDGE.get(), 16));
        menu.quickMoveStack(player, 36);
        helper.assertTrue(menu.getSlot(0).getItem().getCount() == 64
                        && menu.getSlot(9).getItem().getCount() == 16 && !menu.getSlot(36).hasItem(),
                "Partial core refill must send all fifteen remaining fuel to inventory");
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper, String name, boolean operator, boolean creative) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean hasPermissions(int level) {
                return operator;
            }
        };
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(CORE_POS)));
        ServerDataManager.updatePlayerID(player);
        int uid = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(IllegalStateException::new)
                .getPlayerUID();
        helper.assertTrue(player.isCreative() == creative && player.hasPermissions(2) == operator
                        && TeamHelper.getPlayerUID(player) == uid && uid > 0,
                "Player fixture must keep requested mode, permission and UID");
        return player;
    }

    private static void placeAs(TileEntityVolCore tile, FakePlayer player) {
        tile.getBlockState().getBlock().setPlacedBy(tile.getLevel(), tile.getBlockPos(), tile.getBlockState(),
                player, new ItemStack(ModBlocks.VOL_CORE.get()));
    }

    private static boolean removeAs(TileEntityVolCore tile, FakePlayer player) {
        return tile.getBlockState().onDestroyedByPlayer(tile.getLevel(), tile.getBlockPos(), player, false,
                tile.getBlockState().getFluidState());
    }

    private static int countFuel(TileEntityVolCore tile, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = tile.getInventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static TileEntityVolCore placeWorkingCore(GameTestHelper helper) {
        TileEntityVolCore tile = placeCore(helper);
        tile.setPowerRemained(1000);
        tile.setBtnActive(true);
        return tile;
    }

    private static TileEntityVolCore placeCore(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(CORE_POS);
        helper.getLevel().setBlock(pos, ModBlocks.VOL_CORE.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof TileEntityVolCore tile)) {
            throw new AssertionError("Volcano Core block entity was not created");
        }
        return tile;
    }

    private static BasicEntityShip spawnShip(GameTestHelper helper, GameTestEntities entities, BlockPos pos) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        if (ship == null) {
            throw new AssertionError("Failed to create friendly ship");
        }
        ship.moveTo(Vec3.atBottomCenterOf(pos));
        ship.setNoGravity(true);
        ship.setHealth(ship.getMaxHealth() * 0.5F);
        ship.setMorale(100);
        ship.tickCount = 200;
        ship.setCombatTick(0);
        if (!helper.getLevel().addFreshEntity(ship)) {
            throw new AssertionError("Failed to add friendly ship");
        }
        return ship;
    }

    private static void runServerTicks(ServerLevel level, TileEntityVolCore tile, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TileEntityVolCore.serverTick(level, tile.getBlockPos(), tile.getBlockState(), tile);
        }
    }

    private static void assertUnchanged(BasicEntityShip ship, float health, int morale, String context) {
        assertTrue(Math.abs(ship.getHealth() - health) < 0.001F, context + " unexpectedly recovered health");
        assertTrue(ship.getMorale() == morale, context + " unexpectedly recovered morale");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
