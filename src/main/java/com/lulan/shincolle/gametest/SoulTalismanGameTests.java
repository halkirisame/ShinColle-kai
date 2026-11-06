package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.SoulTalismanItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.shipdata.GenericShipSpawner;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoulTalismanGameTests {
    private static final Vec3 SOURCE = new Vec3(2.5, 24, 2.5);
    private static final Vec3 DESTINATION = new Vec3(10.5, 24, 2.5);
    private static final Vec3 FAR = SOURCE.add(70, 0, 0);
    private static final AtomicInteger NEXT_UID = new AtomicInteger(380_000);

    private SoulTalismanGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void singleCaptureWritesSaveBeforeRemovingShip(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            int uid = ship.getShipUID();
            f.capture(ship);
            helper.assertTrue(ship.isRemoved() && SoulTalismanItem.getStoredCount(f.stack()) == 1, "Single capture failed");
            CompoundTag entry = f.stored().getCompound(0);
            int[] state = entry.getCompound("EntityData").getIntArray("StateMinor");
            helper.assertTrue(state[ID.M.ShipUID] == uid && entry.getUUID("Owner").equals(f.owner.getUUID()), "Save identity lost");
            for (String key : List.of("Passengers", "Pos", "Motion", "Rotation", "UUID")) {
                helper.assertTrue(!entry.getCompound("EntityData").contains(key), "Location key retained: " + key);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void otherOwnersAndHostileShipsLeaveTalismanUnchanged(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip other = f.ship();
            RecordingPlayer stranger = f.player();
            other.tame(stranger);
            other.setPlayerUID(capability(stranger).getPlayerUID());
            f.assertRejected(other);
            LivingEntity hostile = f.entities.add(ModEntities.DESTROYER_SHIMAKAZE_MOB.get().create(helper.getLevel()));
            f.assertRejected(hostile);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void foreignFilledTalismanRejectsAdditionButEmptyOneWorks(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            RecordingPlayer receiver = f.player();
            BasicEntityShip ownShip = f.ship();
            ownShip.tame(receiver);
            ownShip.setPlayerUID(capability(receiver).getPlayerUID());
            helper.assertTrue(ownShip.getOwner() == receiver, "Receiver ownership fixture failed");
            receiver.setItemInHand(InteractionHand.MAIN_HAND, f.stack().copy());
            ItemStack before = receiver.getMainHandItem().copy();
            CompoundTag shipBefore = ownShip.saveWithoutId(new CompoundTag());
            ownShip.mobInteract(receiver, InteractionHand.MAIN_HAND);
            helper.assertTrue(!ownShip.isRemoved() && shipBefore.equals(ownShip.saveWithoutId(new CompoundTag()))
                    && ItemStack.matches(before, receiver.getMainHandItem()), "Foreign talisman mixed owners or changed source");
            helper.assertTrue(receiver.lastMessage.getContents() instanceof TranslatableContents text
                    && text.getKey().endsWith(".not_owner"), "Foreign talisman did not report ownership refusal");
            receiver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUL_TALISMAN.get()));
            ownShip.mobInteract(receiver, InteractionHand.MAIN_HAND);
            helper.assertTrue(ownShip.isRemoved() && SoulTalismanItem.isStoredOwner(receiver.getMainHandItem(), receiver),
                    "Empty talisman no longer accepts its user's ship");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void foreignEntryAnywhereInTalismanPreventsFleetAddition(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            f.capture(f.ship());
            RecordingPlayer other = f.player();
            f.stored().getCompound(1).putUUID("Owner", other.getUUID());
            helper.assertTrue(f.stored().getCompound(0).getUUID("Owner").equals(f.owner.getUUID())
                    && !SoulTalismanItem.isStoredOwner(f.stack(), f.owner), "Mixed contents fixture failed");
            List<BasicEntityShip> fleet = f.fleet();
            SoulTalismanItem.setMode(f.stack(), 1);
            ItemStack before = f.stack().copy();
            f.capture(fleet.get(0));
            helper.assertTrue(fleet.stream().noneMatch(Entity::isRemoved) && ItemStack.matches(before, f.stack()),
                    "Fleet mode ignored a foreign entry after the first save");
            f.assertSummary("capture", 0, 6);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fleetCaptureStoresAllSixSlots(GameTestHelper helper) {
        run(helper, f -> {
            List<BasicEntityShip> fleet = f.fleet();
            SoulTalismanItem.setMode(f.stack(), 1);
            f.capture(fleet.get(3));
            helper.assertTrue(fleet.stream().allMatch(Entity::isRemoved) && SoulTalismanItem.getStoredCount(f.stack()) == 6,
                    "Fleet capture did not store all six ships");
            f.assertSummary("capture", 6, 0);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fullTalismanRejectsSeventhShipWithoutLosingAnySave(GameTestHelper helper) {
        run(helper, f -> {
            for (int i = 0; i < 6; i++) f.capture(f.ship());
            helper.assertTrue(SoulTalismanItem.getStoredCount(f.stack()) == 6, "Six-ship fixture failed");
            BasicEntityShip seventh = f.ship();
            ItemStack before = f.stack().copy();
            f.capture(seventh);
            helper.assertTrue(!seventh.isRemoved() && ItemStack.matches(before, f.stack()),
                    "Seventh ship was captured or existing saves changed");
            helper.assertTrue(f.owner.lastMessage.getContents() instanceof TranslatableContents text
                    && text.getKey().endsWith(".full"), "Full talisman did not report capacity refusal");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyOverfilledTalismanCanReleaseAllAndCaptureBelowSix(GameTestHelper helper) {
        run(helper, f -> {
            for (int i = 0; i < 6; i++) f.capture(f.ship());
            CompoundTag legacy = f.stored().getCompound(5).copy();
            legacy.putLong("StoredOrder", 6);
            f.stored().add(legacy);
            helper.assertTrue(SoulTalismanItem.getStoredCount(f.stack()) == 7, "Legacy overfill fixture failed");
            BasicEntityShip next = f.ship();
            ItemStack before = f.stack().copy();
            f.capture(next);
            helper.assertTrue(!next.isRemoved() && ItemStack.matches(before, f.stack()),
                    "Legacy overfill lost data or captured another ship");
            SoulTalismanItem.setMode(f.stack(), SoulTalismanItem.MODE_RELEASE_NEXT);
            f.releaseNext();
            f.releaseNext();
            helper.assertTrue(SoulTalismanItem.getStoredCount(f.stack()) == 5,
                    "Overfilled talisman did not release two ships without truncation");
            SoulTalismanItem.setMode(f.stack(), SoulTalismanItem.MODE_CAPTURE_SINGLE);
            f.capture(next);
            helper.assertTrue(next.isRemoved() && SoulTalismanItem.getStoredCount(f.stack()) == 6,
                    "Legacy talisman did not accept a new ship after reaching five");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sevenDistinctSavedShipsSurviveReloadAndFullRelease(GameTestHelper helper) {
        run(helper, f -> {
            List<Integer> uids = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                BasicEntityShip ship = f.ship();
                uids.add(ship.getShipUID());
                f.capture(ship);
            }
            ItemStack savedSix = f.stack();
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUL_TALISMAN.get()));
            BasicEntityShip seventh = f.ship();
            uids.add(seventh.getShipUID());
            f.capture(seventh);
            CompoundTag seventhSave = f.stored().getCompound(0).copy();
            seventhSave.putLong("StoredOrder", 6);
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, savedSix);
            f.stored().add(seventhSave);
            helper.assertTrue(uids.stream().distinct().count() == 7 && SoulTalismanItem.getStoredCount(f.stack()) == 7,
                    "Seven distinct legacy saves fixture failed");

            ItemStack reloaded = ItemStack.of(f.stack().save(new CompoundTag()));
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
            helper.assertTrue(SoulTalismanItem.getStoredCount(reloaded) == 7, "Reload truncated legacy saves");
            SoulTalismanItem.setMode(reloaded, SoulTalismanItem.MODE_RELEASE_NEXT);
            for (int uid : uids) {
                helper.assertTrue(f.releaseNext().getShipUID() == uid, "Legacy release lost identity or order: " + uid);
            }
            helper.assertTrue(SoulTalismanItem.getStoredCount(reloaded) == 0,
                    "Legacy release did not empty all seven saves");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fullTalismanFleetCaptureReportsAllSixRemaining(GameTestHelper helper) {
        run(helper, f -> {
            for (int i = 0; i < 6; i++) f.capture(f.ship());
            List<BasicEntityShip> fleet = f.fleet();
            ItemStack before = f.stack().copy();
            helper.assertTrue(SoulTalismanItem.getStoredCount(before) == 6, "Full fleet fixture failed");
            SoulTalismanItem.setMode(f.stack(), SoulTalismanItem.MODE_CAPTURE_FLEET);
            SoulTalismanItem.setMode(before, SoulTalismanItem.MODE_CAPTURE_FLEET);
            f.capture(fleet.get(0));
            helper.assertTrue(fleet.stream().noneMatch(Entity::isRemoved) && ItemStack.matches(before, f.stack()),
                    "Full fleet capture removed a ship or modified saved data");
            f.assertSummary("capture", 0, 6);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fleetCaptureLeavesOutOfRangeMemberAndReportsOne(GameTestHelper helper) {
        run(helper, f -> {
            List<BasicEntityShip> fleet = f.fleet();
            fleet.get(5).moveTo(helper.absoluteVec(FAR));
            helper.assertTrue(fleet.get(5).distanceToSqr(f.owner) > 64 * 64, "Range fixture failed");
            SoulTalismanItem.setMode(f.stack(), 1);
            f.capture(fleet.get(0));
            helper.assertTrue(!fleet.get(5).isRemoved() && SoulTalismanItem.getStoredCount(f.stack()) == 5,
                    "Out-of-range ship was stored or nearby ships were skipped");
            f.assertSummary("capture", 5, 1);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fleetCaptureContinuesPastBusyAndMissingMembers(GameTestHelper helper) {
        run(helper, f -> {
            List<BasicEntityShip> fleet = f.fleet();
            RecordingPlayer viewer = f.player();
            viewer.containerMenu = new ContainerShipInventory(1, viewer.getInventory(), fleet.get(1));
            fleet.get(2).discard();
            capability(f.owner).setTeamSID(0, 4, -1);
            SoulTalismanItem.setMode(f.stack(), 1);
            f.capture(fleet.get(1));
            helper.assertTrue(!fleet.get(1).isRemoved() && SoulTalismanItem.getStoredCount(f.stack()) == 4,
                    "Busy/missing members aborted capture or UID fallback failed");
            f.assertSummary("capture", 4, 2);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fleetModeWithoutMembershipCapturesOnlyClickedShip(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            BasicEntityShip neighbor = f.ship();
            SoulTalismanItem.setMode(f.stack(), 1);
            f.capture(ship);
            helper.assertTrue(ship.isRemoved() && !neighbor.isRemoved() && SoulTalismanItem.getStoredCount(f.stack()) == 1,
                    "Unassigned ship did not fall back to single capture");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void capacityRejectionRetainsShipAndPreviouslyStoredSave(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            BasicEntityShip large = f.ship();
            large.getPersistentData().putByteArray("TransportPayload", new byte[SoulTalismanItem.MAX_NBT_BYTES]);
            ItemStack before = f.stack().copy();
            f.capture(large);
            helper.assertTrue(!large.isRemoved() && ItemStack.matches(before, f.stack()), "Full talisman lost ship or prior save");
            helper.assertTrue(f.owner.lastMessage.getContents() instanceof TranslatableContents text
                    && text.getKey().endsWith(".full"), "Capacity refusal did not report full");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void releaseOrderUsesUnassignedThenCurrentFleetSlots(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip a = f.ship();
            BasicEntityShip b = f.ship();
            BasicEntityShip c = f.ship();
            f.assign(1, 0, b);
            f.assign(0, 2, c);
            f.capture(b);
            f.capture(c);
            f.capture(a);
            SoulTalismanItem.setMode(f.stack(), 2);
            for (BasicEntityShip expected : List.of(a, c, b)) {
                helper.assertTrue(f.releaseNext().getShipUID() == expected.getShipUID(), "Release order was not A-C-B");
            }
            helper.assertTrue(!SoulTalismanItem.isFilled(f.stack()), "Sequential release left stored saves");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void releaseOrderReflectsFleetEditsAfterCapture(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip a = f.ship();
            BasicEntityShip b = f.ship();
            BasicEntityShip c = f.ship();
            f.assign(1, 0, b);
            f.assign(0, 2, c);
            f.capture(c);
            f.capture(b);
            f.capture(a);
            CapaTeitoku capa = capability(f.owner);
            capa.setTeamMember(1, 0, 0);
            capa.setTeamMember(0, 2, 0);
            f.assign(0, 0, a);
            f.assign(8, 5, a);
            helper.assertTrue(capa.findTeamOfShip(a.getShipUID()) == 0, "Duplicate fleet fixture failed");
            SoulTalismanItem.setMode(f.stack(), 2);
            for (BasicEntityShip expected : List.of(c, b, a)) {
                helper.assertTrue(f.releaseNext().getShipUID() == expected.getShipUID(), "Live fleet edit/StoredOrder ignored");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void bulkReleaseFindsDistinctSupportedPositions(GameTestHelper helper) {
        run(helper, f -> {
            for (int i = 0; i < 6; i++) f.capture(f.ship());
            f.floor(false);
            SoulTalismanItem.setMode(f.stack(), 3);
            f.use(f.owner);
            List<BasicEntityShip> released = f.released();
            helper.assertTrue(released.size() == 6 && !SoulTalismanItem.isFilled(f.stack()), "Bulk release left ships stored");
            for (int i = 0; i < released.size(); i++) {
                for (int j = i + 1; j < released.size(); j++) {
                    helper.assertTrue(!released.get(i).getBoundingBox().intersects(released.get(j).getBoundingBox()),
                            "Bulk release overlapped ships");
                }
                helper.assertTrue(released.get(i).getY() == f.origin().getY(), "Bulk release ignored the floor");
            }
            f.assertSummary("release", 6, 0);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void bulkReleaseKeepsShipsWithNoVacantSpace(GameTestHelper helper) {
        run(helper, f -> {
            for (int i = 0; i < 3; i++) f.capture(f.ship());
            BlockPos origin = f.origin();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -3, -5), origin.offset(5, 4, 5))) {
                f.block(pos, Blocks.STONE.defaultBlockState());
            }
            f.block(origin, Blocks.AIR.defaultBlockState());
            f.block(origin.above(), Blocks.AIR.defaultBlockState());
            SoulTalismanItem.setMode(f.stack(), 3);
            f.use(f.owner);
            helper.assertTrue(f.released().size() == 1 && SoulTalismanItem.getStoredCount(f.stack()) == 2,
                    "Blocked bulk release consumed stored ships");
            f.assertSummary("release", 1, 2);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void bulkReleaseAcceptsWaterAsSupport(GameTestHelper helper) {
        run(helper, f -> {
            for (int i = 0; i < 3; i++) f.capture(f.ship());
            f.floor(true);
            SoulTalismanItem.setMode(f.stack(), 3);
            f.use(f.owner);
            helper.assertTrue(f.released().size() == 3 && !SoulTalismanItem.isFilled(f.stack()), "Water was not accepted as support");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void roundTripKeepsHealthEquipmentIdentityAndFleet(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            ship.setShipLevel(27, false);
            ship.calcShipAttributes(31, false);
            ship.setHealth(13);
            ItemStack gear = new ItemStack(ModItems.EQUIP_CANNON.get());
            ship.getCapaShipInventory().setStackInSlot(0, gear.copy());
            ship.getCapaShipInventory().setStackInSlot(8, new ItemStack(Items.DIAMOND, 7));
            f.assign(2, 3, ship);
            helper.assertTrue(ship.getHealth() == 13 && ship.getOwner() == f.owner, "HP/owner fixture failed");
            int xp = f.owner.experienceLevel;
            f.capture(ship);
            SoulTalismanItem.setMode(f.stack(), 2);
            BasicEntityShip restored = f.releaseNext();
            restored.updateShipCacheData(true);
            CapaTeitoku capa = capability(f.owner);
            helper.assertTrue(restored.getHealth() == 13 && restored.getLevel() == 27 && f.owner.experienceLevel == xp,
                    "Release applied egg HP/level/experience initialization");
            helper.assertTrue(restored.getShipUID() == ship.getShipUID() && restored.getOwner() == f.owner
                    && !restored.getUUID().equals(ship.getUUID()), "Ship identity/owner changed");
            helper.assertTrue(ItemStack.matches(gear, restored.getCapaShipInventory().getStackInSlot(0))
                    && restored.getCapaShipInventory().getStackInSlot(8).getCount() == 7, "Equipment/cargo changed");
            helper.assertTrue(capa.getNumberOfShip(helper.getLevel(), 2) == 1 && capa.getTeamSID(2, 3) == restored.getId(),
                    "Fleet membership did not survive release");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void modePacketRejectsInvalidValuesAndTouchesOnlyHeldTalisman(GameTestHelper helper) {
        run(helper, f -> {
            for (int mode = 0; mode < 4; mode++) {
                f.modePacket(mode);
                helper.assertTrue(SoulTalismanItem.getMode(f.stack()) == mode, "Valid mode packet ignored");
            }
            for (int mode : new int[]{-1, 4, 5, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                ItemStack before = f.stack().copy();
                f.modePacket(mode);
                helper.assertTrue(ItemStack.matches(before, f.stack()), "Invalid mode changed the talisman");
            }
            ItemStack talisman = f.stack();
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
            ItemStack before = f.stack().copy();
            f.modePacket(1);
            helper.assertTrue(ItemStack.matches(before, f.stack()), "Mode packet wrote to an unrelated item");
            f.owner.setItemInHand(InteractionHand.OFF_HAND, talisman);
            f.modePacket(1);
            helper.assertTrue(SoulTalismanItem.getMode(talisman) == 1, "Offhand talisman did not receive mode");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void failedOrUnauthorizedReleasePreservesStoredShips(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip source = f.ship();
            f.capture(source);
            SoulTalismanItem.setMode(f.stack(), 2);
            ItemStack before = f.stack().copy();
            RecordingPlayer other = f.player();
            other.setItemInHand(InteractionHand.MAIN_HAND, before.copy());
            f.use(other);
            helper.assertTrue(ItemStack.matches(before, other.getMainHandItem()) && f.released().isEmpty(), "Non-owner released ship");
            f.block(f.origin(), Blocks.STONE.defaultBlockState());
            f.use(f.owner);
            helper.assertTrue(ItemStack.matches(before, f.stack()), "Blocked release consumed save");
            f.block(f.origin(), Blocks.AIR.defaultBlockState());
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() instanceof BasicEntityShip ship && ship.getShipUID() == source.getShipUID()) event.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(cancel);
            try {
                f.use(f.owner);
                helper.assertTrue(ItemStack.matches(before, f.stack()) && f.released().isEmpty(), "Cancelled addition consumed save");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void droppedFilledTalismanIsProtectedAndCreativePickupIsLossless(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            SoulTalismanItem item = (SoulTalismanItem) f.stack().getItem();
            ItemStack saved = f.stack().copy();
            BasicEntityItem drop = f.entities.add((BasicEntityItem) item.createEntity(helper.getLevel(), f.owner, saved.copy()));
            helper.assertTrue(item.hasCustomEntity(saved) && !item.hasCustomEntity(new ItemStack(item)) && drop.fireImmune(),
                    "Protected drop hook failed");
            helper.assertTrue(helper.getLevel().addFreshEntity(drop), "Drop fixture failed");
            for (int i = 0; i < 11; i++) drop.tick();
            drop.playerTouch(f.player());
            helper.assertTrue(!drop.isRemoved() && ItemStack.matches(saved, drop.getEntityItem()), "Other player picked up talisman");
            f.owner.getAbilities().instabuild = true;
            for (int i = 0; i < f.owner.getInventory().items.size(); i++) {
                f.owner.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            }
            helper.assertTrue(f.owner.getInventory().getFreeSlot() == -1, "Full inventory fixture failed");
            drop.playerTouch(f.owner);
            helper.assertTrue(!drop.isRemoved() && ItemStack.matches(saved, drop.getEntityItem()), "Creative overflow lost stored ships");
            f.owner.getInventory().setItem(1, ItemStack.EMPTY);
            drop.playerTouch(f.owner);
            helper.assertTrue(drop.isRemoved() && ItemStack.matches(saved, f.owner.getInventory().getItem(1)), "Owner pickup lost save");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void crouchingStillOpensShipMenuWithoutCapture(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            f.owner.setShiftKeyDown(true);
            f.capture(ship);
            helper.assertTrue(!ship.isRemoved() && !SoulTalismanItem.isFilled(f.stack())
                    && f.owner.containerMenu instanceof ContainerShipInventory menu && menu.getShip() == ship,
                    "Crouching talisman replaced ship menu interaction");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericShipUsesSharedDefinitionRestore(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.prepare((BasicEntityShip) GenericShipSpawner.create(helper.getLevel(),
                    ShipDefinitionId.parse("shincolle_kai:kashima"), false, null));
            ship.setHealth(11);
            f.capture(ship);
            SoulTalismanItem.setMode(f.stack(), 2);
            BasicEntityShip restored = f.releaseNext();
            helper.assertTrue(restored instanceof GenericShipEntity generic && generic.getShipId().equals("shincolle_kai:kashima")
                    && restored.getHealth() == 11, "Generic definition/HP lost");
        });
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                verification.accept(fixture);
                helper.succeed();
            }
        }, SOURCE, DESTINATION, FAR);
    }

    private static CapaTeitoku capability(Player player) {
        return player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(IllegalStateException::new);
    }

    private static final class RecordingPlayer extends FakePlayer {
        private Component lastMessage = Component.empty();

        private RecordingPlayer(GameTestHelper helper) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "soul_" + NEXT_UID.getAndIncrement()));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            lastMessage = message;
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final List<RecordingPlayer> players = new ArrayList<>();
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final RecordingPlayer owner;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            entities = GameTestEntities.open(helper);
            owner = player();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUL_TALISMAN.get()));
        }

        private RecordingPlayer player() {
            RecordingPlayer player = new RecordingPlayer(helper);
            player.moveTo(helper.absoluteVec(SOURCE.add(-4, 0, 0)));
            capability(player).setPlayerUID(NEXT_UID.getAndIncrement());
            helper.getLevel().addNewPlayer(player);
            players.add(player);
            return player;
        }

        private ItemStack stack() {
            return owner.getMainHandItem();
        }

        private ListTag stored() {
            return stack().getOrCreateTag().getCompound("SoulTalisman").getList("Ships", Tag.TAG_COMPOUND);
        }

        private BasicEntityShip ship() {
            return prepare(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
        }

        private BasicEntityShip prepare(BasicEntityShip ship) {
            helper.assertTrue(ship != null, "Ship creation failed");
            entities.add(ship);
            ship.moveTo(helper.absoluteVec(SOURCE));
            ship.tame(owner);
            ship.setPlayerUID(capability(owner).getPlayerUID());
            ship.setStateMinor(ID.M.NumGrudge, 1000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setInvulnerable(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship registration failed");
            GameTestEntities.assertRegistered(helper, ship);
            ship.updateShipCacheData(true);
            helper.assertTrue(ship.getOwner() == owner && ship.getShipUID() > 0
                    && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "Owned/fueled fixture failed");
            return ship;
        }

        private void assign(int team, int slot, BasicEntityShip ship) {
            capability(owner).setTeamMember(team, slot, ship.getShipUID());
            capability(owner).setTeamSID(team, slot, ship.getId());
            helper.assertTrue(capability(owner).getTeamMember(team, slot) == ship.getShipUID(), "Fleet fixture failed");
        }

        private List<BasicEntityShip> fleet() {
            List<BasicEntityShip> fleet = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                BasicEntityShip ship = ship();
                assign(0, i, ship);
                fleet.add(ship);
            }
            return fleet;
        }

        private void capture(BasicEntityShip ship) {
            helper.assertTrue(ship.mobInteract(owner, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
                    "Ship interaction did not consume talisman use");
        }

        private void assertRejected(LivingEntity target) {
            CompoundTag before = target.saveWithoutId(new CompoundTag());
            ItemStack beforeStack = stack().copy();
            SoulTalismanItem item = (SoulTalismanItem) stack().getItem();
            item.interactLivingEntity(stack(), owner, target, InteractionHand.MAIN_HAND);
            helper.assertTrue(!target.isRemoved() && before.equals(target.saveWithoutId(new CompoundTag()))
                    && ItemStack.matches(beforeStack, stack()), "Rejected capture changed target or talisman");
        }

        private BlockPos origin() {
            return BlockPos.containing(helper.absoluteVec(DESTINATION));
        }

        private void use(Player player) {
            BlockPos clicked = origin().below();
            ((SoulTalismanItem) ModItems.SOUL_TALISMAN.get()).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
        }

        private List<BasicEntityShip> released() {
            List<BasicEntityShip> found = new ArrayList<>();
            for (Entity entity : helper.getLevel().getAllEntities()) {
                if (entity instanceof BasicEntityShip ship && !ship.isRemoved() && ship.getOwnerUUID() != null
                        && ship.getOwnerUUID().equals(owner.getUUID()) && ship.position().distanceTo(helper.absoluteVec(DESTINATION)) < 8) {
                    found.add(entities.add(ship));
                }
            }
            return found;
        }

        private BasicEntityShip releaseNext() {
            use(owner);
            BasicEntityShip ship = null;
            for (BasicEntityShip candidate : released()) {
                if (candidate.position().distanceTo(helper.absoluteVec(DESTINATION)) < 0.1) ship = candidate;
            }
            helper.assertTrue(ship != null, "Sequential release created no ship at clicked face");
            // Keep earlier releases away from the next release point without changing fleet data.
            ship.moveTo(helper.absoluteVec(SOURCE.add(0, 8, 0)));
            return ship;
        }

        private void assertSummary(String action, int changed, int remaining) {
            helper.assertTrue(owner.lastMessage.getContents() instanceof TranslatableContents, "No translated summary");
            TranslatableContents message = (TranslatableContents) owner.lastMessage.getContents();
            helper.assertTrue(message.getKey().equals("message.shincolle_kai.soul_talisman." + action)
                    && message.getArgs().length == 2 && message.getArgs()[0].equals(changed) && message.getArgs()[1].equals(remaining),
                    "Incorrect fleet summary: " + owner.lastMessage);
        }

        private void modePacket(int mode) {
            try {
                Method handler = C2SGUIInputPacket.class.getDeclaredMethod("handleSyncPlayerItem", ServerPlayer.class);
                handler.setAccessible(true);
                handler.invoke(new C2SGUIInputPacket(C2SGUIInputPacket.SyncPlayerItem, new int[]{owner.getId(), 0, mode}), owner);
            } catch (ReflectiveOperationException error) {
                helper.fail("Could not invoke mode packet: " + error);
            }
        }

        private void block(BlockPos pos, BlockState state) {
            BlockPos key = pos.immutable();
            blocks.putIfAbsent(key, helper.getLevel().getBlockState(key));
            helper.getLevel().setBlock(key, state, 2);
        }

        private void floor(boolean water) {
            BlockPos origin = origin();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -1, -5), origin.offset(5, -1, 5))) {
                block(pos, water ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState());
            }
        }

        @Override
        public void close() {
            released();
            entities.close();
            blocks.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 2));
            for (RecordingPlayer player : players) {
                player.containerMenu = player.inventoryMenu;
                helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        }
    }
}
