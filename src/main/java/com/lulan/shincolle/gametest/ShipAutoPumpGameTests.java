package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipResourceGate;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.utility.ShipFluidTransfer;
import com.lulan.shincolle.utility.TaskHelper;
import com.lulan.shincolle.utility.BlockHelper;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShipAutoPumpGameTests {
    private static ProbeFluidBlock probeBlock;
    private static BlockEntityType<ProbeBlockEntity> probeBlockEntity;

    private ShipAutoPumpGameTests() { }

    /** Test runtime only: no fake fluid or block is registered in a normal client/server. */
    @SubscribeEvent
    public static void registerFixtures(RegisterEvent event) {
        if (System.getProperty("shincolle.gametestLevelSeed") == null) return;
        event.register(ForgeRegistries.Keys.BLOCKS, registration -> {
            probeBlock = new ProbeFluidBlock();
            registration.register(new ResourceLocation(Reference.MOD_ID, "pump_test_liquid"), probeBlock);
        });
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, registration -> {
            probeBlockEntity = BlockEntityType.Builder.of(ProbeBlockEntity::new, probeBlock).build(null);
            registration.register(new ResourceLocation(Reference.MOD_ID, "pump_test_liquid"), probeBlockEntity);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "auto_pump")
    public static void naturalTickFillsBucketFromFiniteSource(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = fixture(helper);
            FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "pump_owner"));
            int uid = owner.getId() + 9_100_000;
            owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing admiral capability")).setPlayerUID(uid);
            owner.getAbilities().mayBuild = true;
            helper.getLevel().addNewPlayer(owner);
            FormationGameTestOwner online = new FormationGameTestOwner(owner);
            GameTestEntities.onCompletion(helper, () -> {
                online.close();
                helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            }, "pump owner cleanup");
            ship.setPlayerUID(uid);
            ship.setOwnerUUID(owner.getUUID());
            BlockPos source = ship.blockPosition().offset(-3, 1, -3);
            helper.startSequence().thenIdle(24).thenExecute(() -> {
                ready(helper, ship);
                helper.assertTrue(ServerDataManager.getPlayerByUID(uid) == owner, "Online UID fixture");
                ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.BUCKET));
                for (var direction : net.minecraft.core.Direction.values()) {
                    place(helper, source.relative(direction), Blocks.STONE.defaultBlockState());
                }
                place(helper, source, Blocks.WATER.defaultBlockState());
            }).thenIdle(70).thenExecute(() -> {
                helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(7).is(Items.WATER_BUCKET),
                        "Natural server tick must fill the cargo bucket");
                helper.assertTrue(helper.getLevel().getBlockState(source).isAir(), "Finite source removed once");
                helper.succeed();
            });
        }, new Vec3(8.5, 4, 8.5));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "auto_pump")
    public static void naturalTickCollectsOrbAndBottlesWithoutOwner(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = fixture(helper);
            helper.startSequence().thenIdle(24).thenExecute(() -> {
                ready(helper, ship);
                helper.assertTrue(ship.getStateMinor(ID.M.XP) == -1, "New ship retains the existing unset XP storage value");
                ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE));
                ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), ship.getX(), ship.getY(), ship.getZ(), 8);
                orb.setNoGravity(true);
                helper.getLevel().addFreshEntity(orb);
                GameTestEntities.onCompletion(helper, orb::discard, "pump orb cleanup");
            }).thenIdle(8).thenExecute(() -> {
                helper.assertTrue(cargoBottles(ship) == 1,
                        "Natural server tick must collect and bottle XP independently of liquid ownership");
                helper.assertTrue(ship.getStateMinor(ID.M.XP) == 0, "Eight XP consumed exactly once");
                helper.succeed();
            });
        }, new Vec3(8.5, 4, 8.5));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_permissions")
    public static void pumpInhibitionsPreserveEveryResource(GameTestHelper helper) {
        runFixture(helper, ship -> {
            ship.tickCount = 64;
            ship.setStateMinor(ID.M.XP, 16);
            ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE, 2));
            List<Consumer<BasicEntityShip>> inhibit = List.of(
                    s -> s.setStateFlag(ID.F.AutoPump, false), s -> s.setEntitySit(true),
                    s -> s.setStateFlag(ID.F.NoFuel, true), s -> s.setHealth(0));
            for (Consumer<BasicEntityShip> change : inhibit) {
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setHealth(ship.getMaxHealth());
                ready(helper, ship);
                change.accept(ship);
                var result = ShipResourceGate.tickPump(ship);
                helper.assertTrue(!result.decision().allowed() && !result.decision().reasons().isEmpty(), "Explained denial");
                helper.assertTrue(ship.getStateMinor(ID.M.XP) == 16
                        && ship.getCapaShipInventory().getStackInSlot(7).getCount() == 2, "Denied pump is inert");
            }
            ship.setHealth(ship.getMaxHealth());
            ship.setStateFlag(ID.F.NoFuel, false);
            ready(helper, ship);
            boolean stopped = BasicEntityShip.stopAI;
            try {
                BasicEntityShip.stopAI = true;
                helper.assertTrue(!ShipResourceGate.tickPump(ship).decision().allowed(), "Stopped AI is inert");
            } finally {
                BasicEntityShip.stopAI = stopped;
            }
            helper.assertTrue(ShipResourceGate.tickPump(ship).decision().allowed(), "Positive enabled control");
            helper.assertTrue(ship.getStateMinor(ID.M.XP) == 8, "Exactly one bottle in one authorized update");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_equipment")
    public static void drumMetadataAndSlotBoundariesAreExact(GameTestHelper helper) {
        runFixture(helper, ship -> {
            var inventory = ship.getCapaShipInventory();
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            for (int slot : new int[]{0, 6, 7}) {
                for (int metadata = 0; metadata <= 2; metadata++) {
                    ItemStack drum = new ItemStack(ModItems.EQUIP_DRUM.get());
                    drum.getOrCreateTag().putInt("EquipMeta", metadata);
                    inventory.setStackInSlot(slot, drum);
                    helper.assertTrue(ShipResourceGate.permission(ship).allowed() == (metadata == 1 && slot <= 6),
                            "Only fluid drum metadata one in slots zero through six: " + slot + "/" + metadata);
                    inventory.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
            ship.setStateFlag(ID.F.IsMarried, true);
            helper.assertTrue(ShipResourceGate.permission(ship).allowed(), "Married transport drum exemption");
            ship.setStateMinor(ID.M.ShipType, ID.ShipType.DESTROYER);
            helper.assertTrue(!ShipResourceGate.permission(ship).allowed(), "Marriage alone does not exempt other ships");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_finite")
    public static void finiteRequiresWholeAmountAcrossIsolatedContainers(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> withHandlers(() -> {
            var inventory = ship.getCapaShipInventory();
            inventory.setStackInSlot(7, tank(900, "normal"));
            int experience = ship.getStateMinor(ID.M.ExpCurrent);
            var denied = ShipResourceGate.tickPump(ship);
            helper.assertTrue(denied.liquid().status() == ShipFluidTransfer.Status.NO_OP, "Nine hundred is insufficient");
            helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.WATER)
                    && amount(inventory.getStackInSlot(7)) == 0
                    && ship.getStateMinor(ID.M.ExpCurrent) == experience, "Insufficient finite transfer is entirely inert");
            inventory.setStackInSlot(7, tank(400, "normal"));
            inventory.setStackInSlot(8, tank(600, "normal"));
            inventory.getStackInSlot(7).getOrCreateTag().putInt("Other", 100);
            inventory.getStackInSlot(7).getOrCreateTag().putBoolean("SameOther", true);
            inventory.getStackInSlot(8).getOrCreateTag().putInt("Other", 50);
            var committed = ShipResourceGate.tickPump(ship);
            helper.assertTrue(committed.liquid().status() == ShipFluidTransfer.Status.COMMITTED, "Four hundred plus six hundred");
            helper.assertTrue(amount(inventory.getStackInSlot(7)) == 400 && amount(inventory.getStackInSlot(8)) == 600,
                    "Subtract only each container's own contribution");
            helper.assertTrue(helper.getLevel().getBlockState(source).isAir(), "One finite source removed");
            int rewarded = ship.getStateMinor(ID.M.ExpCurrent);
            helper.assertTrue(rewarded > experience, "Transport receives its single commit reward");
            helper.assertTrue(ShipResourceGate.tickPump(ship).liquid().status() == ShipFluidTransfer.Status.NO_OP
                    && ship.getStateMinor(ID.M.ExpCurrent) == rewarded, "No second reward after depletion");
        }));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_infinite")
    public static void infiniteAllowsPartialFillAndKeepsSource(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> withHandlers(() -> {
            int old = ConfigHandler.infLiquid[0];
            try {
                ConfigHandler.infLiquid[0] = 1;
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 0 || z != 0) place(helper, source.offset(x, 0, z), Blocks.WATER.defaultBlockState());
                    }
                }
                ship.getCapaShipInventory().setStackInSlot(7, tank(400, "normal"));
                var result = ShipResourceGate.tickPump(ship).liquid();
                helper.assertTrue(result.status() == ShipFluidTransfer.Status.COMMITTED && result.actual() == 400
                        && !result.finiteRemoval(), "Positive partial infinite transfer");
                helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.WATER)
                        && amount(ship.getCapaShipInventory().getStackInSlot(7)) == 400, "Infinite source retained");
            } finally {
                ConfigHandler.infLiquid[0] = old;
            }
        }));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_contract")
    public static void itemContractViolationsNeverPublishStagedChanges(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> withHandlers(() -> {
            for (String mode : List.of("simulate_mutates", "execute_mismatch", "wrong_fluid", "empty", "count", "loses_other")) {
                ItemStack input = tank(1000, mode);
                input.getOrCreateTag().putInt("Other", 100);
                CompoundTag before = input.save(new CompoundTag()).copy();
                ship.getCapaShipInventory().setStackInSlot(7, input);
                int experience = ship.getStateMinor(ID.M.ExpCurrent);
                var result = ShipResourceGate.tickPump(ship).liquid();
                helper.assertTrue(result.status() == ShipFluidTransfer.Status.EXTERNAL_CONTRACT_VIOLATION,
                        "Reject external item contract violation: " + mode + " got " + result);
                helper.assertTrue(before.equals(input.save(new CompoundTag()))
                        && helper.getLevel().getBlockState(source).is(Blocks.WATER)
                        && ship.getStateMinor(ID.M.ExpCurrent) == experience, "No live changes: " + mode);
            }
        }));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_cargo")
    public static void equipmentLockedCargoAndStackedContainersAreUntouched(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> withHandlers(() -> {
            ship.getCapaShipInventory().setStackInSlot(1, tank(1000, "normal"));
            ship.getCapaShipInventory().setStackInSlot(24, tank(1000, "normal"));
            ItemStack stacked = tank(1000, "normal");
            stacked.setCount(2);
            ship.getCapaShipInventory().setStackInSlot(7, stacked);
            ship.setInventoryPageSize(0);
            helper.assertTrue(!ship.getCapaShipInventory().isSlotAvailable(24), "Locked page fixture after equipment recalculation");
            helper.assertTrue(ShipResourceGate.tickPump(ship).liquid().status() == ShipFluidTransfer.Status.NO_OP,
                    "No automatic splitting, equipment filling or locked-page filling");
            helper.assertTrue(amount(stacked) == 0 && stacked.getCount() == 2
                    && helper.getLevel().getBlockState(source).is(Blocks.WATER), "All source resources retained");
        }));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_block_api")
    public static void fluidBlockWithBlockEntityOwnsItsDrainAndStateChange(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> withHandlers(() -> {
            helper.getLevel().setBlock(source, probeBlock.defaultBlockState(), 2);
            ProbeBlockEntity blockEntity = (ProbeBlockEntity) helper.getLevel().getBlockEntity(source);
            helper.assertTrue(blockEntity != null, "Fluid-block block entity fixture");
            blockEntity.amount = 600;
            ship.getCapaShipInventory().setStackInSlot(7, tank(600, "normal"));
            var result = ShipResourceGate.tickPump(ship).liquid();
            helper.assertTrue(result.status() == ShipFluidTransfer.Status.COMMITTED && result.actual() == 600,
                    "IFluidBlock takes priority over LiquidBlock's thousand-millibucket amount");
            helper.assertTrue(blockEntity.amount == 0 && blockEntity.drains == 1
                    && helper.getLevel().getBlockState(source).is(probeBlock), "API-owned state, no second AIR replacement");
            blockEntity.amount = 600;
            blockEntity.drains = 0;
            blockEntity.mismatch = true;
            ship.getCapaShipInventory().setStackInSlot(7, tank(600, "normal"));
            int experience = ship.getStateMinor(ID.M.ExpCurrent);
            var violation = ShipResourceGate.tickPump(ship).liquid();
            helper.assertTrue(violation.status() == ShipFluidTransfer.Status.EXTERNAL_CONTRACT_VIOLATION
                    && blockEntity.drains == 1 && blockEntity.amount == 0
                    && amount(ship.getCapaShipInventory().getStackInSlot(7)) == 0
                    && ship.getStateMinor(ID.M.ExpCurrent) == experience,
                    "Mismatching world API cannot publish cargo/rewards or trigger an unsafe world rollback");
        }));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_no_reward")
    public static void nonTransportAndZeroRewardStillFill(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> {
            int reward = ConfigHandler.expGain[6];
            try {
                ConfigHandler.expGain[6] = 0;
                ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.BUCKET));
                helper.assertTrue(ShipResourceGate.tickPump(ship).liquid().status() == ShipFluidTransfer.Status.COMMITTED,
                        "Zero configured experience does not block the fill");
                helper.getLevel().setBlock(source, Blocks.WATER.defaultBlockState(), 2);
                ConfigHandler.expGain[6] = reward;
                ship.setStateMinor(ID.M.ShipType, ID.ShipType.DESTROYER);
                int experience = ship.getStateMinor(ID.M.ExpCurrent);
                ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.BUCKET));
                helper.assertTrue(ShipResourceGate.tickPump(ship).liquid().status() == ShipFluidTransfer.Status.COMMITTED
                        && ship.getStateMinor(ID.M.ExpCurrent) == experience, "Non-transport fill has no ship experience");
            } finally {
                ConfigHandler.expGain[6] = reward;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_owner_permission")
    public static void liquidEditingPermissionDoesNotRestrictExperience(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> {
            var owner = ServerDataManager.getPlayerByUID(ship.getPlayerUID());
            owner.getAbilities().mayBuild = false;
            ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.BUCKET));
            ship.getCapaShipInventory().setStackInSlot(8, new ItemStack(Items.GLASS_BOTTLE));
            ship.setStateMinor(ID.M.XP, 8);
            var result = ShipResourceGate.tickPump(ship);
            helper.assertTrue(result.liquid().status() == ShipFluidTransfer.Status.NO_OP
                    && result.experience() == TaskHelper.PumpXpResult.COMMITTED, "Editing denial is liquid-only");
            helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.WATER)
                    && cargoBottles(ship) == 1, "Independent XP positive control");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_orb_count")
    public static void mergedOrbCountsAndDistanceBoundaryAreExact(GameTestHelper helper) {
        runFixture(helper, ship -> {
            ship.tickCount = 64;
            ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE, 2));
            ExperienceOrb merged = orb(helper, ship, 2, 3, 3D);
            ExperienceOrb pulled = orb(helper, ship, 1, 1, 3.01D);
            int experience = ship.getStateMinor(ID.M.ExpCurrent);
            ShipResourceGate.tickPump(ship);
            helper.assertTrue(merged.isRemoved() && ship.getStateMinor(ID.M.XP) == 6, "Count three times value two at distance three");
            helper.assertTrue(!pulled.isRemoved() && pulled.getDeltaMovement().x < 0, "Beyond three blocks is pulled, not collected");
            ShipResourceGate.tickPump(ship);
            helper.assertTrue(ship.getStateMinor(ID.M.XP) == 6 && ship.getStateMinor(ID.M.ExpCurrent) == experience,
                    "Discarded orb cannot be collected twice or grant ship experience");
            pulled.discard();
            ship.setStateMinor(ID.M.XP, Integer.MAX_VALUE - 5);
            ExperienceOrb overflowing = orb(helper, ship, 2, 3, 0D);
            ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE));
            TaskHelper.pumpExperience(ship);
            helper.assertTrue(!overflowing.isRemoved(), "Overflowing orb stays alive");
            overflowing.discard();
            ship.setStateMinor(ID.M.XP, 0);
            ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE));
            ExperienceOrb invalid = new ExperienceOrb(helper.getLevel(), ship.getX(), ship.getY(), ship.getZ(), 2) {
                @Override
                public void addAdditionalSaveData(CompoundTag nbt) {
                    super.addAdditionalSaveData(nbt);
                    nbt.putInt("Count", 0);
                }
            };
            helper.assertTrue(helper.getLevel().addFreshEntity(invalid), "Invalid Count boundary fixture");
            GameTestEntities.onCompletion(helper, invalid::discard, "invalid pump orb cleanup");
            TaskHelper.pumpExperience(ship);
            helper.assertTrue(!invalid.isRemoved() && ship.getStateMinor(ID.M.XP) == 0,
                    "Invalid serialized Count cannot delete the orb or alter XP");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_bottle_schedule")
    public static void bottleRemaindersAndFourTickScheduleAreExact(GameTestHelper helper) {
        runFixture(helper, ship -> {
            var inventory = ship.getCapaShipInventory();
            inventory.setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE, 2));
            ship.setStateMinor(ID.M.XP, 7);
            ship.tickCount = 64;
            ShipResourceGate.tickPump(ship);
            helper.assertTrue(ship.getStateMinor(ID.M.XP) == 7, "Seven is insufficient");
            ship.setStateMinor(ID.M.XP, 16);
            ship.tickCount = 65;
            ShipResourceGate.tickPump(ship);
            helper.assertTrue(ship.getStateMinor(ID.M.XP) == 16, "No bottle between four-tick marks");
            ship.tickCount = 68;
            ShipResourceGate.tickPump(ship);
            helper.assertTrue(ship.getStateMinor(ID.M.XP) == 8 && inventory.getStackInSlot(7).getCount() == 1,
                    "One bottle, not two, at a single mark");
            ship.tickCount = 72;
            ShipResourceGate.tickPump(ship);
            helper.assertTrue(ship.getStateMinor(ID.M.XP) == 0 && !inventory.getStackInSlot(7).is(Items.GLASS_BOTTLE),
                    "Next mark consumes the remaining bottle and eight XP");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_full_bottle")
    public static void fullCargoUsesConsumedSlotOrDropsAndSpawnDenialPreservesInputs(GameTestHelper helper) {
        runFixture(helper, ship -> {
            var inventory = ship.getCapaShipInventory();
            for (int slot = 6; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            inventory.setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE));
            ship.setStateMinor(ID.M.XP, 8);
            TaskHelper.pumpExperience(ship);
            helper.assertTrue(inventory.getStackInSlot(7).is(Items.EXPERIENCE_BOTTLE)
                    && ship.getStateMinor(ID.M.XP) == 0, "Consumed last bottle frees a cargo output slot");
            inventory.setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE, 2));
            ship.setStateMinor(ID.M.XP, 8);
            Consumer<EntityJoinLevelEvent> reject = event -> {
                if (event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.EXPERIENCE_BOTTLE)) event.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(reject);
            try {
                TaskHelper.pumpExperience(ship);
                helper.assertTrue(inventory.getStackInSlot(7).getCount() == 2 && ship.getStateMinor(ID.M.XP) == 8,
                        "Rejected output spawn leaves both inputs untouched");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(reject);
            }
            TaskHelper.pumpExperience(ship);
            var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, ship.getBoundingBox().inflate(1D),
                    item -> item.getItem().is(Items.EXPERIENCE_BOTTLE));
            helper.assertTrue(dropped.size() == 1 && inventory.getStackInSlot(7).getCount() == 1
                    && ship.getStateMinor(ID.M.XP) == 0, "Full cargo drops exactly one bottle");
            dropped.forEach(Entity::discard);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_search")
    public static void pumpScanExcludesWaterloggedSolidsAndKeepsOrder(GameTestHelper helper) {
        runFixture(helper, ship -> {
            BlockPos first = ship.blockPosition().offset(-3, 1, -3);
            place(helper, first, Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
            BlockPos flowing = first.offset(0, 0, 1);
            place(helper, flowing, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
            BlockPos second = first.offset(0, 0, 2);
            place(helper, second, Blocks.WATER.defaultBlockState());
            var observation = BlockHelper.findPumpSource(ship);
            helper.assertTrue(observation.source().isPresent(), "Expected source after excluded candidates");
            helper.assertTrue(observation.source().get().position().equals(second),
                    "Skip submerged solids and flowing non-source blocks, preserve scan order");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_lava_depth")
    public static void lavaDepthIsIndependentAndFlowingColumnsCount(GameTestHelper helper) {
        fluidFixture(helper, (ship, source) -> {
            helper.getLevel().setBlock(source, Blocks.LAVA.defaultBlockState(), 2);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || z != 0) place(helper, source.offset(x, 0, z),
                            Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
                }
            }
            ConfigHandler.infLiquid[0] = 0;
            ConfigHandler.infLiquid[1] = 1;
            ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.BUCKET));
            var infinite = ShipResourceGate.tickPump(ship).liquid();
            helper.assertTrue(infinite.status() == ShipFluidTransfer.Status.COMMITTED && !infinite.finiteRemoval()
                    && helper.getLevel().getBlockState(source).is(Blocks.LAVA), "Lava's own positive depth counts flowing columns");
            for (int depth : new int[]{0, -1}) {
                ConfigHandler.infLiquid[0] = 1;
                ConfigHandler.infLiquid[1] = depth;
                helper.getLevel().setBlock(source, Blocks.LAVA.defaultBlockState(), 2);
                ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.BUCKET));
                var finite = ShipResourceGate.tickPump(ship).liquid();
                helper.assertTrue(finite.status() == ShipFluidTransfer.Status.COMMITTED && finite.finiteRemoval()
                        && helper.getLevel().getBlockState(source).isAir(), "Zero/negative lava depth leaves finite pumping enabled");
            }
            BlockPos unloaded = new BlockPos(29_000_000, 64, 29_000_000);
            helper.assertTrue(!helper.getLevel().hasChunkAt(unloaded), "Unloaded chunk fixture");
            helper.assertTrue(ShipFluidTransfer.observe(helper.getLevel(), unloaded).source().isEmpty()
                    && !helper.getLevel().hasChunkAt(unloaded), "Observation must not load chunks");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_compat_input")
    public static void legacyAndNewCompatibilityInputsBothUseNewPumpRules(GameTestHelper helper) {
        runFixture(helper, ship -> {
            for (var authority : new ConfigHandler.ShipAiTargetAuthority[]{ConfigHandler.ShipAiTargetAuthority.LEGACY,
                    ConfigHandler.ShipAiTargetAuthority.NEW}) {
                try (var ignored = ShipAiAuthorityOverride.use(authority)) {
                    helper.assertTrue(ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW,
                            "Compatibility input never restores retired execution");
                    ship.tickCount = 64;
                    ship.setStateMinor(ID.M.XP, 8);
                    ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE));
                    int before = cargoBottles(ship);
                    ShipResourceGate.tickPump(ship);
                    helper.assertTrue(ship.getStateMinor(ID.M.XP) == 0 && cargoBottles(ship) == before + 1,
                            "Same effective NEW pump result for " + authority);
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 240, batch = "pump_persistence")
    public static void sevenXpSurvivesSaveDimensionRoundTripAndRealDeathEgg(GameTestHelper helper) {
        runFixture(helper, source -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            source.setStateMinor(ID.M.XP, 7);
            source.setStateFlag(ID.F.AutoPump, false);
            CompoundTag saved = new CompoundTag();
            source.addAdditionalSaveData(saved);
            BasicEntityShip loaded = entities.add(ModEntities.AP_WA.get().create(helper.getLevel()));
            loaded.readAdditionalSaveData(saved);
            helper.assertTrue(loaded.getStateMinor(ID.M.XP) == 7, "Ordinary save/load retains seven XP");
            Vec3 position = source.position();
            source.discard();
            loaded.moveTo(position);
            helper.assertTrue(helper.getLevel().addFreshEntity(loaded), "Loaded ship added");
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "Nether fixture exists");
            BasicEntityShip outward = travel(helper, entities, loaded, nether, position);
            BasicEntityShip returned = travel(helper, entities, outward, helper.getLevel(), position);
            helper.assertTrue(returned.getStateMinor(ID.M.XP) == 7, "Actual dimension round trip retains seven XP");
            returned.setStateFlag(ID.F.CanDrop, true);
            returned.deathTime = ConfigHandler.deathTime() - 1;
            try {
                Method death = BasicEntityShip.class.getDeclaredMethod("tickDeath");
                death.setAccessible(true);
                death.invoke(returned);
            } catch (ReflectiveOperationException failure) {
                helper.fail("Cannot advance death egg fixture: " + failure);
            }
            var eggs = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                    new AABB(BlockPos.containing(position)).inflate(2D), item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
            helper.assertTrue(eggs.size() == 1, "Exactly one actual death egg");
            ItemStack egg = eggs.get(0).getEntityItem().copy();
            eggs.get(0).discard();
            helper.assertTrue(egg.getTag().getIntArray("StateMinor")[ID.M.XP] == 7, "Death egg retains remainder");
            FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pump_egg_owner"));
            owner.getAbilities().instabuild = true;
            BlockPos floor = BlockPos.containing(position).below();
            InteractionResult used = egg.getItem().useOn(new UseOnContext(helper.getLevel(), owner, InteractionHand.MAIN_HAND,
                    egg, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
            helper.assertTrue(used == InteractionResult.CONSUME, "Actual egg use hatches saved ship");
            var ships = helper.getLevel().getEntitiesOfClass(BasicEntityShip.class, new AABB(BlockPos.containing(position)).inflate(1D));
            helper.assertTrue(ships.size() == 1, "Exactly one revived ship");
            BasicEntityShip revived = entities.add(ships.get(0));
            helper.assertTrue(revived.getStateMinor(ID.M.XP) == 7, "Revived ship retains seven XP");
            revived.setStateFlag(ID.F.AutoPump, true);
            revived.setEntitySit(false);
            revived.setStateFlag(ID.F.NoFuel, false);
            revived.setStateMinor(ID.M.NumGrudge, 10000);
            revived.tickCount = 64;
            revived.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GLASS_BOTTLE));
            orb(helper, revived, 1, 1, 0);
            ShipResourceGate.tickPump(revived);
            helper.assertTrue(cargoBottles(revived) == 1 && revived.getStateMinor(ID.M.XP) == 0,
                    "One more XP after revival yields exactly one bottle");
        });
    }

    private static BasicEntityShip travel(GameTestHelper helper, GameTestEntities entities, BasicEntityShip ship,
                                         ServerLevel destination, Vec3 position) {
        destination.getChunk(BlockPos.containing(position));
        Entity moved = ship.changeDimension(destination, new ITeleporter() {
            @Override
            public PortalInfo getPortalInfo(Entity entity, ServerLevel level, Function<ServerLevel, PortalInfo> vanilla) {
                return new PortalInfo(position, Vec3.ZERO, 0F, 0F);
            }
            @Override
            public Entity placeEntity(Entity entity, ServerLevel from, ServerLevel to, float yaw, Function<Boolean, Entity> reposition) {
                return reposition.apply(false);
            }
        });
        helper.assertTrue(moved instanceof BasicEntityShip && moved != ship && ship.isRemoved() && moved.level() == destination,
                "Real dimension transfer creates the new server entity");
        return (BasicEntityShip) entities.add(moved);
    }

    private static void runFixture(GameTestHelper helper, Consumer<BasicEntityShip> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = fixture(helper);
            helper.startSequence().thenIdle(24).thenExecute(() -> {
                ready(helper, ship);
                verification.accept(ship);
                helper.succeed();
            });
        }, new Vec3(8.5, 4, 8.5));
    }

    private static void fluidFixture(GameTestHelper helper, BiConsumer<BasicEntityShip, BlockPos> verification) {
        runFixture(helper, ship -> {
            FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pump_api_owner"));
            int uid = owner.getId() + 9_200_000;
            owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Missing admiral capability")).setPlayerUID(uid);
            owner.getAbilities().mayBuild = true;
            helper.getLevel().addNewPlayer(owner);
            FormationGameTestOwner online = new FormationGameTestOwner(owner);
            GameTestEntities.onCompletion(helper, () -> {
                online.close();
                helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            }, "pump API owner cleanup");
            ship.setPlayerUID(uid);
            ship.setOwnerUUID(owner.getUUID());
            helper.assertTrue(ServerDataManager.getPlayerByUID(uid) == owner, "Online API fixture owner");
            ship.tickCount = 64;
            BlockPos source = ship.blockPosition().offset(-3, 1, -3);
            place(helper, source, Blocks.WATER.defaultBlockState());
            int water = ConfigHandler.infLiquid[0], lava = ConfigHandler.infLiquid[1];
            try {
                ConfigHandler.infLiquid[0] = 0;
                ConfigHandler.infLiquid[1] = -1;
                verification.accept(ship, source);
            } finally {
                ConfigHandler.infLiquid[0] = water;
                ConfigHandler.infLiquid[1] = lava;
            }
        });
    }

    private static void place(GameTestHelper helper, BlockPos position, BlockState state) {
        BlockState previous = helper.getLevel().getBlockState(position);
        GameTestEntities.onCompletion(helper, () -> helper.getLevel().setBlock(position, previous, 2), "pump block cleanup");
        helper.getLevel().setBlock(position, state, 2);
    }

    private static ExperienceOrb orb(GameTestHelper helper, BasicEntityShip ship, int value, int count, double distance) {
        ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), ship.getX() + distance, ship.getY(), ship.getZ(), value);
        CompoundTag nbt = new CompoundTag();
        orb.addAdditionalSaveData(nbt);
        nbt.putInt("Count", count);
        orb.readAdditionalSaveData(nbt);
        orb.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "Orb fixture added");
        GameTestEntities.onCompletion(helper, orb::discard, "pump counted orb cleanup");
        return orb;
    }

    private static ItemStack tank(int capacity, String mode) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putInt("PumpCapacity", capacity);
        stack.getOrCreateTag().putString("PumpMode", mode);
        return stack;
    }

    private static int amount(ItemStack stack) { return stack.getOrCreateTag().getInt("Pumped"); }

    private static int cargoBottles(BasicEntityShip ship) {
        int count = 0;
        var inventory = ship.getCapaShipInventory();
        for (int slot = 6; slot < inventory.getSlots() && inventory.isSlotAvailable(slot); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(Items.EXPERIENCE_BOTTLE)) count += stack.getCount();
        }
        return count;
    }

    private static void withHandlers(Runnable verification) {
        Consumer<AttachCapabilitiesEvent<ItemStack>> listener = event -> {
            if (event.getObject().is(Items.PAPER)) {
                event.addCapability(new ResourceLocation(Reference.MOD_ID, "pump_test_container"), new ProbeHandler(event.getObject()));
            }
        };
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, listener);
        try {
            verification.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    private static final class ProbeHandler implements IFluidHandlerItem, ICapabilityProvider {
        private final ItemStack stack;
        private final LazyOptional<IFluidHandlerItem> capability = LazyOptional.of(() -> this);
        private ProbeHandler(ItemStack stack) { this.stack = stack; }
        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(capability, this.capability);
        }
        @Override
        public ItemStack getContainer() {
            return stack.getOrCreateTag().getBoolean("EmptyOutput") ? ItemStack.EMPTY : stack;
        }
        @Override
        public int getTanks() { return 2; }
        @Override
        public FluidStack getFluidInTank(int tank) {
            CompoundTag nbt = stack.getOrCreateTag();
            if (tank == 1) return new FluidStack(nbt.getBoolean("SameOther") ? Fluids.WATER : Fluids.LAVA, nbt.getInt("Other"));
            return new FluidStack(nbt.getBoolean("WrongFluid") ? Fluids.LAVA : Fluids.WATER, amount(stack));
        }
        @Override
        public int getTankCapacity(int tank) { return stack.getOrCreateTag().getInt("PumpCapacity"); }
        @Override
        public boolean isFluidValid(int tank, FluidStack resource) { return tank == 0 && resource.getFluid() == Fluids.WATER; }
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            CompoundTag nbt = stack.getOrCreateTag();
            String mode = nbt.getString("PumpMode");
            int accepted = Math.min(resource.getAmount(), Math.max(0, getTankCapacity(0) - amount(stack)));
            if (action.execute() || mode.equals("simulate_mutates")) nbt.putInt("Pumped", amount(stack) + accepted);
            if (action.execute()) {
                if (mode.equals("wrong_fluid")) nbt.putBoolean("WrongFluid", true);
                if (mode.equals("empty")) nbt.putBoolean("EmptyOutput", true);
                if (mode.equals("count")) stack.setCount(2);
                if (mode.equals("loses_other")) nbt.putInt("Other", 0);
                if (mode.equals("execute_mismatch")) return accepted - 1;
            }
            return accepted;
        }
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override
        public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }

    private static final class ProbeFluidBlock extends LiquidBlock implements IFluidBlock, EntityBlock {
        private ProbeFluidBlock() { super(() -> Fluids.WATER, BlockBehaviour.Properties.copy(Blocks.WATER)); }
        @Override
        public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new ProbeBlockEntity(position, state); }
        @Override
        public int place(Level level, BlockPos position, FluidStack stack, FluidAction action) { return 0; }
        @Override
        public boolean canDrain(Level level, BlockPos position) {
            return level.getBlockEntity(position) instanceof ProbeBlockEntity probe && probe.amount > 0;
        }
        @Override
        public FluidStack drain(Level level, BlockPos position, FluidAction action) {
            ProbeBlockEntity blockEntity = (ProbeBlockEntity) level.getBlockEntity(position);
            FluidStack fluid = new FluidStack(Fluids.WATER, blockEntity.amount);
            if (action.execute()) {
                blockEntity.amount = 0;
                blockEntity.drains++;
                blockEntity.setChanged();
                if (blockEntity.mismatch) fluid.shrink(1);
            }
            return fluid;
        }
        @Override
        public float getFilledPercentage(Level level, BlockPos position) { return 1F; }
    }

    private static final class ProbeBlockEntity extends BlockEntity {
        private int amount = 1000;
        private int drains;
        private boolean mismatch;
        private ProbeBlockEntity(BlockPos position, BlockState state) { super(probeBlockEntity, position, state); }
        @Override
        protected void saveAdditional(CompoundTag nbt) {
            super.saveAdditional(nbt);
            nbt.putInt("Amount", amount);
            nbt.putInt("Drains", drains);
            nbt.putBoolean("Mismatch", mismatch);
        }
        @Override
        public void load(CompoundTag nbt) {
            super.load(nbt);
            amount = nbt.getInt("Amount");
            drains = nbt.getInt("Drains");
            mismatch = nbt.getBoolean("Mismatch");
        }
    }

    private static BasicEntityShip fixture(GameTestHelper helper) {
        BasicEntityShip ship = GameTestEntities.open(helper).add(ModEntities.AP_WA.get().create(helper.getLevel()));
        ship.moveTo(helper.absoluteVec(new Vec3(8.5, 4, 8.5)));
        ship.setNoGravity(true);
        ship.setInvulnerable(true);
        ship.setStateMinor(ID.M.NumGrudge, 10000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.AutoPump, false);
        ship.setHealth(ship.getMaxHealth());
        GameTestEntities.removeAllGoalsEachTick(helper, ship);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship spawned");
        return ship;
    }

    private static void ready(GameTestHelper helper, BasicEntityShip ship) {
        ItemStack drum = new ItemStack(ModItems.EQUIP_DRUM.get());
        drum.getOrCreateTag().putInt("EquipMeta", 1);
        ship.getCapaShipInventory().setStackInSlot(0, drum);
        ship.setEntitySit(false);
        ship.setStateFlag(ID.F.AutoPump, true);
        helper.assertTrue(ship.tickCount >= 24 && ship.isAlive() && ship.getHealth() > 0,
                "Healthy ship after goal registration");
        helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "Fuel fixture");
        helper.assertTrue(ship.getStateFlag(ID.F.AutoPump) && !ship.isOrderedToSit(), "Enabled standing pump");
        helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(0).getTag().getInt("EquipMeta") == 1,
                "Fluid drum retained");
    }
}
