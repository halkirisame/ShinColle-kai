package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.SafariNetItem;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafariNetGameTests {
    private static final Vec3 SOURCE = new Vec3(2.5, 8, 2.5);
    private static final Vec3 DESTINATION = new Vec3(6.5, 8, 2.5);
    private static final AtomicInteger NEXT_UID = new AtomicInteger(250_000);

    private SafariNetGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void roundTripKeepsShipStateWithoutEggInitialization(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            ship.setShipLevel(27, false);
            ship.setStateMinor(ID.M.Morale, 6312);
            ship.setStateMinor(ID.M.NumGrudge, 1234);
            ship.setStateMinor(ID.M.ExpCurrent, 234);
            ship.setStateMinor(ID.M.NumAmmoLight, 77);
            ship.setStateMinor(ID.M.NumAmmoHeavy, 31);
            ship.setStateMinor(ID.M.Food, 321);
            ship.setStateMinor(ID.M.FormatType, 1);
            ship.setStateMinor(ID.M.FormatPos, 2);
            ship.setStateEmotion(ID.S.Emotion, 3, false);
            ship.setStateFlag(ID.F.IsMarried, true);
            ship.setStateFlag(ID.F.CanDrop, false);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setGuardedPos(12, 73, -25, Level.NETHER, 1);
            ship.setEntitySit(true);
            ship.setCustomName(Component.literal("Transport specimen"));
            ship.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 1));
            ship.getAttrs().getAttrsBonus()[0] = 7;
            ship.setTextureID(1);
            ItemStack equipment = new ItemStack(ModItems.EQUIP_CANNON.get());
            ship.getCapaShipInventory().setStackInSlot(0, equipment.copy());
            ship.getCapaShipInventory().setStackInSlot(8, new ItemStack(Items.DIAMOND, 13));
            ship.calcShipAttributes(31, false);
            float health = Math.min(17F, ship.getMaxHealth() - 1);
            ship.setHealth(health);
            helper.assertTrue(health != 4 && ship.getHealth() == health && ship.isOrderedToSit(), "State fixture failed");
            helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) == 234 && ship.getStateMinor(ID.M.NumAmmoLight) == 77
                    && ship.getStateMinor(ID.M.NumAmmoHeavy) == 31 && ship.getStateMinor(ID.M.Food) == 321
                    && ship.getStateMinor(ID.M.FormatType) == 1 && ship.getStateMinor(ID.M.FormatPos) == 2
                    && ship.getStateEmotion(ID.S.Emotion) == 3, "Extended state fixture was silently ignored");
            int uid = ship.getShipUID();
            int playerUid = ship.getPlayerUID();
            UUID uuid = ship.getUUID();
            int xp = f.owner.experienceLevel;
            f.capture(ship);
            helper.assertTrue(ship.isRemoved() && SafariNetItem.isFilled(f.net()), "Ship was not captured");
            CompoundTag data = f.net().getTag().getCompound("SafariShip").getCompound("EntityData");
            for (String key : List.of("Passengers", "UUID", "Pos", "Motion", "Rotation")) {
                helper.assertTrue(!data.contains(key), "Transport save retained " + key);
            }
            BasicEntityShip restored = f.release();
            helper.assertTrue(restored.getHealth() == health && restored.isOrderedToSit(), "HP/sitting changed");
            helper.assertTrue(restored.getLevel() == 27 && restored.getStateFlag(ID.F.IsMarried), "Level/marriage changed");
            helper.assertTrue(restored.getStateMinor(ID.M.Morale) == 6312
                    && restored.getStateMinor(ID.M.NumGrudge) == 1234, "Morale/resources changed");
            helper.assertTrue(restored.getStateMinor(ID.M.ExpCurrent) == 234
                    && restored.getStateMinor(ID.M.NumAmmoLight) == 77 && restored.getStateMinor(ID.M.NumAmmoHeavy) == 31
                    && restored.getStateMinor(ID.M.Food) == 321 && restored.getStateMinor(ID.M.FormatType) == 1
                    && restored.getStateMinor(ID.M.FormatPos) == 2 && restored.getStateEmotion(ID.S.Emotion) == 3,
                    "Experience/ammunition/food/formation/expression changed");
            helper.assertTrue(restored.getGuardedPos(0) == 12 && restored.getGuardedPos(1) == 73
                    && restored.getGuardedPos(2) == -25 && restored.getGuardedPos(3) == -1
                    && Level.NETHER.equals(restored.getGuardedDimension()), "Guard destination changed");
            helper.assertTrue(ItemStack.matches(restored.getCapaShipInventory().getStackInSlot(0), equipment)
                    && restored.getCapaShipInventory().getStackInSlot(8).getCount() == 13, "Equipment/cargo changed");
            helper.assertTrue(restored.getShipUID() == uid && restored.getPlayerUID() == playerUid
                    && f.owner.getUUID().equals(restored.getOwnerUUID()) && !restored.getUUID().equals(uuid), "Identity changed");
            helper.assertTrue(restored.hasEffect(MobEffects.NIGHT_VISION)
                    && restored.getEffect(MobEffects.NIGHT_VISION).getDuration() == 1200
                    && restored.getAttrs().getAttrsBonus()[0] == 7 && restored.getTextureID() == 1
                    && restored.getName().getString().equals("Transport specimen"), "Extra saved state changed");
            helper.assertTrue(!restored.getStateFlag(ID.F.CanDrop) && f.owner.experienceLevel == xp,
                    "Release applied egg initialization");
            helper.assertTrue(!SafariNetItem.isFilled(f.net()), "Released net was not emptied");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void guardEntityIdentitySurvivesRoundTrip(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setGuardedPos(1, 70, 2, Level.OVERWORLD, 2);
            ship.setGuardedEntity(f.owner);
            helper.assertTrue(f.owner.getUUID().equals(ship.getGuardedEntityUuid()), "Guard fixture failed");
            f.capture(ship);
            BasicEntityShip restored = f.release();
            helper.assertTrue(f.owner.getUUID().equals(restored.getGuardedEntityUuid())
                    && Level.OVERWORLD.equals(restored.getGuardedDimension()), "Guard entity identity lost");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rejectsOtherOwnersShipWithoutChangingEither(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            FakePlayer other = f.player();
            ship.tame(other);
            ship.setPlayerUID(capability(other).getPlayerUID());
            f.assertRejected(ship);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rejectsHostileThroughItemInteraction(GameTestHelper helper) {
        run(helper, f -> {
            LivingEntity hostile = f.entities.add(ModEntities.DESTROYER_SHIMAKAZE_MOB.get().create(helper.getLevel()));
            f.assertRejected(hostile);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rejectsDeadShipWithoutChangingEither(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            ship.setHealth(0);
            f.assertRejected(ship);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rejectsRemovedShipWithoutChangingEither(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            ship.discard();
            f.assertRejected(ship);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rejectsAnotherPlayersOpenMenu(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            FakePlayer viewer = f.player();
            viewer.containerMenu = new ContainerShipInventory(1, viewer.getInventory(), ship);
            helper.assertTrue(((ContainerShipInventory) viewer.containerMenu).getShip() == ship, "Menu fixture failed");
            f.assertRejected(ship);
            viewer.containerMenu = viewer.inventoryMenu;
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void closesOwnersOwnMenuBeforeCapture(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            f.owner.containerMenu = new ContainerShipInventory(1, f.owner.getInventory(), ship);
            f.capture(ship);
            helper.assertTrue(ship.isRemoved() && f.owner.containerMenu == f.owner.inventoryMenu, "Owner menu not closed");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void failedSerializationNeverRemovesShip(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.prepare(new BrokenSaveShip(helper.getLevel(), true));
            ItemStack before = f.net().copy();
            f.capture(ship);
            helper.assertTrue(!ship.isRemoved() && ItemStack.matches(before, f.net()), "Failed save destroyed ship/net");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptySerializationNeverRemovesShip(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.prepare(new BrokenSaveShip(helper.getLevel(), false));
            f.capture(ship);
            helper.assertTrue(!ship.isRemoved() && !SafariNetItem.isFilled(f.net()), "Empty save destroyed ship/net");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void failedSerializationLeavesMountAndPassengersDetached(GameTestHelper helper) {
        verifyFailedSaveDismounts(helper, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptySerializationLeavesMountAndPassengersDetached(GameTestHelper helper) {
        verifyFailedSaveDismounts(helper, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void failedSerializationLeavesHimeDetachedFromPlayer(GameTestHelper helper) {
        verifyFailedSaveDismounts(helper, true, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptySerializationLeavesHimeDetachedFromPlayer(GameTestHelper helper) {
        verifyFailedSaveDismounts(helper, false, true);
    }

    private static void verifyFailedSaveDismounts(GameTestHelper helper, boolean throwOnSave, boolean hime) {
        run(helper, f -> {
            BasicEntityShip ship = f.prepare(hime ? new BrokenSaveHime(helper.getLevel(), throwOnSave)
                    : new BrokenSaveShip(helper.getLevel(), throwOnSave));
            ship.setEntitySit(true);
            ItemStack gear = new ItemStack(ModItems.EQUIP_CANNON.get());
            ship.getCapaShipInventory().setStackInSlot(0, gear.copy());
            ship.calcShipAttributes(31, false);
            float health = Math.min(13F, ship.getMaxHealth() - 1);
            ship.setHealth(health);
            int uid = ship.getShipUID();
            BasicEntityMount mount = null;
            if (hime) {
                helper.assertTrue(ship.startRiding(f.owner, true), "Hime riding fixture failed");
            } else {
                mount = f.entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
                mount.moveTo(ship.position());
                mount.setHost(ship);
                helper.assertTrue(helper.getLevel().addFreshEntity(mount), "Could not add mount");
                helper.assertTrue(ship.startRiding(mount, true), "Ship failed to mount");
            }
            LivingEntity passenger = f.entities.add(EntityType.CHICKEN.create(helper.getLevel()));
            helper.assertTrue(passenger.startRiding(ship, true), "Passenger fixture failed");
            helper.assertTrue(ship.isPassenger() && ship.getPassengers().contains(passenger)
                    && health > 0 && health != 4 && ship.getHealth() == health, "Failed save fixture was not applied");
            ItemStack before = f.net().copy();
            f.capture(ship);
            helper.assertTrue(!ship.isRemoved() && ItemStack.matches(before, f.net()), "Failed save destroyed ship/net");
            helper.assertTrue(!ship.isPassenger() && !passenger.isPassenger() && ship.getPassengers().isEmpty()
                    && f.owner.getPassengers().isEmpty(), "Failed save restored riding relations");
            helper.assertTrue(mount == null || (mount.isRemoved() && mount.getPassengers().isEmpty()),
                    "Failed save recreated or retained the mount");
            helper.assertTrue(ship.getHealth() == health && ship.getShipUID() == uid && ship.isOrderedToSit()
                    && f.owner.getUUID().equals(ship.getOwnerUUID())
                    && ItemStack.matches(gear, ship.getCapaShipInventory().getStackInSlot(0)),
                    "Failed save changed non-riding ship state");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void anotherPlayerCannotReleaseStoredShip(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            ItemStack filled = f.net().copy();
            FakePlayer other = f.player();
            other.setItemInHand(InteractionHand.MAIN_HAND, filled.copy());
            f.use(other);
            helper.assertTrue(ItemStack.matches(filled, other.getMainHandItem()) && f.atDestination() == null,
                    "Another player released ship");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void blockedReleaseKeepsFilledNet(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            ItemStack filled = f.net().copy();
            BlockPos obstruction = BlockPos.containing(helper.absoluteVec(DESTINATION));
            helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            try {
                f.use(f.owner);
                helper.assertTrue(ItemStack.matches(filled, f.net()) && f.atDestination() == null, "Blocked release lost net");
            } finally {
                helper.getLevel().setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unknownTypeKeepsFilledNet(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            f.net().getTag().getCompound("SafariShip").putInt("ShipClass", -999);
            ItemStack filled = f.net().copy();
            f.use(f.owner);
            helper.assertTrue(ItemStack.matches(filled, f.net()) && f.atDestination() == null, "Invalid type consumed net");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void captureClearsMountAndPassengers(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            BasicEntityMount mount = f.entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            mount.moveTo(ship.position());
            mount.setHost(ship);
            helper.assertTrue(helper.getLevel().addFreshEntity(mount), "Could not add mount");
            helper.assertTrue(ship.startRiding(mount, true), "Ship failed to mount");
            LivingEntity passenger = f.entities.add(EntityType.CHICKEN.create(helper.getLevel()));
            helper.assertTrue(passenger.startRiding(ship, true), "Passenger fixture failed");
            f.capture(ship);
            helper.assertTrue(ship.isRemoved() && mount.isRemoved() && !passenger.isPassenger()
                    && ship.getPassengers().isEmpty() && mount.getPassengers().isEmpty(), "Riding remnants survived");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void captureDismountsHimeFromPlayer(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip hime = f.prepare(ModEntities.NORTHERN_HIME.get().create(helper.getLevel()));
            helper.assertTrue(hime.startRiding(f.owner, true), "Hime riding fixture failed");
            f.capture(hime);
            helper.assertTrue(hime.isRemoved() && !hime.isPassenger() && f.owner.getPassengers().isEmpty(),
                    "Hime passenger remained on player");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericShipRoundTripRestoresDefinitionAndIcon(GameTestHelper helper) {
        run(helper, f -> {
            ShipDefinitionId id = ShipDefinitionId.parse("shincolle_kai:kashima");
            BasicEntityShip generic = f.prepare((BasicEntityShip) GenericShipSpawner.create(helper.getLevel(), id, false, null));
            generic.setHealth(11);
            f.capture(generic);
            helper.assertTrue(SafariNetItem.getIcon(f.net()) == 3, "Light cruiser generic icon incorrect");
            BasicEntityShip restored = f.release();
            helper.assertTrue(restored instanceof GenericShipEntity ship && ship.getShipId().equals(id.toString())
                    && restored.getHealth() == 11, "Generic ship identity/HP lost");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void missingGenericDefinitionKeepsFilledNet(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            f.net().getTag().getCompound("SafariShip").putString("ShipId", "missing:no_such_ship");
            ItemStack filled = f.net().copy();
            f.use(f.owner);
            helper.assertTrue(ItemStack.matches(filled, f.net()) && f.atDestination() == null, "Missing definition lost ship");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void releaseResolvesTeamMembershipByShipUid(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            CapaTeitoku capa = capability(f.owner);
            capa.setTeamMember(0, 0, ship.getShipUID());
            capa.setTeamSID(0, 0, ship.getId());
            helper.assertTrue(capa.getNumberOfShip(helper.getLevel(), 0) == 1, "Team fixture did not resolve source");
            f.capture(ship);
            BasicEntityShip restored = f.release();
            restored.updateShipCacheData(true);
            helper.assertTrue(capa.getNumberOfShip(helper.getLevel(), 0) == 1
                    && capa.getTeamSID(0, 0) == restored.getId(), "Team did not resolve new entity by UID");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void curiosSurvivesEntityCapabilityRoundTrip(GameTestHelper helper) {
        if (!ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            ListTag gear = new ListTag();
            CompoundTag entry = new ItemStack(ModItems.EQUIP_CANNON.get()).save(new CompoundTag());
            entry.putByte("Slot", (byte) 2);
            gear.add(entry);
            ShipCuriosIntegration.loadEquipped(ship, gear);
            ListTag before = ShipCuriosIntegration.saveEquipped(ship);
            helper.assertTrue(before.equals(gear), "Curios fixture was silently ignored");
            f.capture(ship);
            BasicEntityShip restored = f.release();
            helper.assertTrue(before.equals(ShipCuriosIntegration.saveEquipped(restored)), "Curios equipment lost");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void stackedNetsCaptureExactlyOneShip(GameTestHelper helper) {
        run(helper, f -> {
            f.net().setCount(3);
            f.capture(f.ship());
            helper.assertTrue(f.net().getCount() == 2 && !SafariNetItem.isFilled(f.net()), "Empty stack not split");
            int filled = 0;
            for (ItemStack stack : f.owner.getInventory().items) {
                if (SafariNetItem.isFilled(stack)) filled += stack.getCount();
            }
            helper.assertTrue(filled == 1, "Expected exactly one filled net");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void droppedFilledNetUsesProtectedOwnerOnlyEntity(GameTestHelper helper) {
        run(helper, f -> {
            f.capture(f.ship());
            SafariNetItem item = (SafariNetItem) ModItems.SAFARI_NET.get();
            helper.assertTrue(item.hasCustomEntity(f.net()) && !item.hasCustomEntity(new ItemStack(item)), "Drop hook incorrect");
            Entity entity = f.entities.add(item.createEntity(helper.getLevel(), f.owner, f.net().copy()));
            helper.assertTrue(entity instanceof BasicEntityItem && entity.fireImmune(), "Filled net drop not protected");
            BasicEntityItem drop = (BasicEntityItem) entity;
            for (int i = 0; i < 11; i++) drop.tick();
            FakePlayer other = f.player();
            drop.playerTouch(other);
            helper.assertTrue(!drop.getEntityItem().isEmpty(), "Other player picked up net");
            drop.playerTouch(f.owner);
            helper.assertTrue(drop.isRemoved(), "Owner could not pick up net");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericIconsCoverApprovedTypeMapping(GameTestHelper helper) throws ReflectiveOperationException {
        Method mapping = SafariNetItem.class.getDeclaredMethod("genericIcon", String.class);
        mapping.setAccessible(true);
        String[] types = {"destroyer", "light_cruiser", "torpedo_cruiser", "heavy_cruiser", "battleship",
                "light_carrier", "standard_carrier", "transport", "submarine", "demon", "hime", "unknown", ""};
        int[] expected = {2, 3, 3, 4, 5, 10, 10, 6, 7, 8, 9, 2, 2};
        for (int i = 0; i < types.length; i++) {
            helper.assertTrue(mapping.invoke(null, types[i]).equals(expected[i]), "Incorrect icon for " + types[i]);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void crouchingNetOpensExistingShipMenuWithoutCapture(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            f.owner.setShiftKeyDown(true);
            f.capture(ship);
            helper.assertTrue(!ship.isRemoved() && !SafariNetItem.isFilled(f.net())
                    && f.owner.containerMenu instanceof ContainerShipInventory menu && menu.getShip() == ship,
                    "Sneaking net interaction did not preserve GUI access");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void clientCaptureNeverWritesNetOrRemovesShip(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            ItemStack before = f.net().copy();
            try {
                Field side = Level.class.getDeclaredField("isClientSide");
                side.setAccessible(true);
                try {
                    side.setBoolean(helper.getLevel(), true);
                    f.capture(ship);
                } finally {
                    side.setBoolean(helper.getLevel(), false);
                }
            } catch (ReflectiveOperationException error) {
                helper.fail("Could not inject client predicate: " + error);
            }
            helper.assertTrue(!ship.isRemoved() && ItemStack.matches(before, f.net()), "Client mutated transport state");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void cancelledEntityAdditionKeepsFilledNet(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip source = f.ship();
            int uid = source.getShipUID();
            f.capture(source);
            ItemStack filled = f.net().copy();
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() instanceof BasicEntityShip ship && ship.getShipUID() == uid) event.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(cancel);
            try {
                f.use(f.owner);
                helper.assertTrue(ItemStack.matches(filled, f.net()) && f.atDestination() == null,
                        "Failed world addition consumed net");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fullInventoryDropsOnlyCapturedNetAsProtectedEntity(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Fixture f = new Fixture(helper);
            GameTestEntities.onCompletion(helper, f::close, "asynchronous net fixture cleanup");
            for (int i = 0; i < f.owner.getInventory().items.size(); i++) {
                f.owner.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            }
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SAFARI_NET.get(), 2));
            f.capture(f.ship());
            helper.assertTrue(f.net().getCount() == 1 && !SafariNetItem.isFilled(f.net()), "Empty net stack changed");
            // Forge queues the replacement entity after cancelling the ordinary item entity's join.
            helper.startSequence().thenWaitUntil(() -> {
                int found = 0;
                for (Entity entity : helper.getLevel().getAllEntities()) {
                    if (entity instanceof BasicEntityItem drop && SafariNetItem.isStoredOwner(drop.getEntityItem(), f.owner)) {
                        helper.assertTrue(SafariNetItem.isFilled(drop.getEntityItem()) && drop.getEntityItem().getCount() == 1,
                                "Dropped net does not contain exactly one ship");
                        found++;
                    }
                }
                helper.assertTrue(found == 1, "Expected exactly one protected net after deferred entity addition, got " + found);
            }).thenSucceed();
        }, SOURCE, DESTINATION, SOURCE.add(-4, 0, 0));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creativeFullInventoryCapturePreservesProtectedNetAndShipData(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Fixture f = new Fixture(helper);
            GameTestEntities.onCompletion(helper, f::close, "creative capture fixture cleanup");
            BasicEntityShip ship = f.ship();
            ship.calcShipAttributes(31, false);
            ship.setHealth(13);
            ship.getCapaShipInventory().setStackInSlot(8, new ItemStack(Items.DIAMOND, 7));
            helper.assertTrue(ship.getHealth() == 13, "Capture HP fixture failed");
            CompoundTag expected = ship.saveWithoutId(new CompoundTag());
            for (String key : List.of("Passengers", "UUID", "Pos", "Motion", "Rotation")) expected.remove(key);
            int uid = ship.getShipUID();
            fillCreativeInventory(helper, f.owner);
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SAFARI_NET.get(), 2));
            helper.assertTrue(f.owner.interactOn(ship, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
                    "Creative capture did not consume interaction");
            helper.assertTrue(ship.isRemoved() && f.net().getCount() == 2 && !SafariNetItem.isFilled(f.net()),
                    "Creative capture lost remaining empty nets or did not remove ship");
            helper.startSequence().thenWaitUntil(() -> {
                List<BasicEntityItem> drops = new ArrayList<>();
                for (Entity entity : helper.getLevel().getAllEntities()) {
                    if (entity instanceof BasicEntityItem drop && SafariNetItem.isStoredOwner(drop.getEntityItem(), f.owner)) {
                        drops.add(drop);
                    }
                }
                helper.assertTrue(drops.size() == 1, "Creative full capture must leave one protected net, got " + drops.size());
                BasicEntityItem drop = drops.get(0);
                ItemStack saved = drop.getEntityItem();
                helper.assertTrue(!drop.isRemoved() && drop.fireImmune() && saved.getCount() == 1
                        && SafariNetItem.isFilled(saved) && drop.position().distanceTo(f.owner.position()) < 2,
                        "Creative full capture lost protected net at owner's feet");
                helper.assertTrue(expected.equals(saved.getTag().getCompound("SafariShip").getCompound("EntityData")),
                        "Creative full capture changed ship save data");
            }).thenExecute(() -> {
                BasicEntityItem drop = null;
                for (Entity entity : helper.getLevel().getAllEntities()) {
                    if (entity instanceof BasicEntityItem candidate
                            && SafariNetItem.isStoredOwner(candidate.getEntityItem(), f.owner)) drop = candidate;
                }
                helper.assertTrue(drop != null, "Protected net disappeared before release");
                f.owner.setItemInHand(InteractionHand.MAIN_HAND, drop.getEntityItem().copy());
                drop.discard();
                BasicEntityShip restored = f.release();
                helper.assertTrue(restored.getShipUID() == uid && restored.getHealth() == 13
                        && restored.getCapaShipInventory().getStackInSlot(8).getCount() == 7,
                        "Dropped creative capture could not restore ship state");
            }).thenSucceed();
        }, SOURCE, DESTINATION, SOURCE.add(-4, 0, 0));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creativeFullInventoryPickupRetainsNetUntilSpaceAvailable(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntityShip ship = f.ship();
            int uid = ship.getShipUID();
            f.capture(ship);
            ItemStack saved = f.net().copy();
            SafariNetItem item = (SafariNetItem) saved.getItem();
            BasicEntityItem drop = f.entities.add((BasicEntityItem) item.createEntity(helper.getLevel(), f.owner, saved.copy()));
            helper.assertTrue(helper.getLevel().addFreshEntity(drop), "Pickup drop registration failed");
            for (int i = 0; i < 11; i++) drop.tick();
            fillCreativeInventory(helper, f.owner);
            drop.playerTouch(f.owner);
            helper.assertTrue(!drop.isRemoved() && ItemStack.matches(saved, drop.getEntityItem()),
                    "Creative full pickup destroyed filled net or its contents");
            f.owner.getInventory().setItem(1, ItemStack.EMPTY);
            drop.playerTouch(f.owner);
            helper.assertTrue(drop.isRemoved() && ItemStack.matches(saved, f.owner.getInventory().getItem(1)),
                    "Creative pickup with a free slot did not preserve exactly one filled net");
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, f.owner.getInventory().removeItemNoUpdate(1));
            helper.assertTrue(f.release().getShipUID() == uid, "Recovered pickup could not release saved ship");
        });
    }

    private static void fillCreativeInventory(GameTestHelper helper, FakePlayer player) {
        player.getAbilities().instabuild = true;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(player.getAbilities().instabuild && player.getInventory().getFreeSlot() == -1,
                "Creative full inventory fixture failed");
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                verification.accept(fixture);
                helper.succeed();
            }
        }, SOURCE, DESTINATION);
    }

    private static CapaTeitoku capability(Player player) {
        return player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(IllegalStateException::new);
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final List<FakePlayer> players = new ArrayList<>();
        private final FakePlayer owner;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.owner = player();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SAFARI_NET.get()));
        }

        private FakePlayer player() {
            int uid = NEXT_UID.getAndIncrement();
            FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "net_" + uid));
            player.moveTo(helper.absoluteVec(SOURCE.add(-4, 0, 0)));
            capability(player).setPlayerUID(uid);
            helper.getLevel().addNewPlayer(player);
            players.add(player);
            return player;
        }

        private ItemStack net() {
            return owner.getMainHandItem();
        }

        private BasicEntityShip ship() {
            return prepare(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
        }

        private BasicEntityShip prepare(BasicEntityShip ship) {
            helper.assertTrue(ship != null, "Ship factory failed");
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
            helper.assertTrue(ship.getShipUID() > 0 && ship.getOwner() == owner && !ship.getStateFlag(ID.F.NoFuel),
                    "Owned/fueled fixture failed");
            return ship;
        }

        private void capture(BasicEntityShip ship) {
            helper.assertTrue(ship.mobInteract(owner, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
                    "Ship interaction did not consume net use");
        }

        private void assertRejected(LivingEntity target) {
            CompoundTag before = target.saveWithoutId(new CompoundTag());
            ItemStack beforeNet = net().copy();
            boolean removed = target.isRemoved();
            SafariNetItem item = (SafariNetItem) net().getItem();
            InteractionResult result = target instanceof BasicEntityShip ship
                    ? ship.mobInteract(owner, InteractionHand.MAIN_HAND)
                    : item.interactLivingEntity(net(), owner, target, InteractionHand.MAIN_HAND);
            helper.assertTrue(result == InteractionResult.SUCCESS && removed == target.isRemoved()
                    && before.equals(target.saveWithoutId(new CompoundTag())) && ItemStack.matches(beforeNet, net()),
                    "Rejected capture changed ship/net");
        }

        private void use(Player player) {
            BlockPos block = BlockPos.containing(helper.absoluteVec(DESTINATION)).below();
            ((SafariNetItem) ModItems.SAFARI_NET.get()).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(block), Direction.UP, block, false)));
        }

        private BasicEntityShip atDestination() {
            Vec3 destination = helper.absoluteVec(DESTINATION);
            for (Entity entity : helper.getLevel().getAllEntities()) {
                if (entity instanceof BasicEntityShip ship && !ship.isRemoved() && ship.position().distanceTo(destination) < 0.1) {
                    return ship;
                }
            }
            return null;
        }

        private BasicEntityShip release() {
            use(owner);
            BasicEntityShip restored = atDestination();
            helper.assertTrue(restored != null, "Release did not create ship at clicked face");
            entities.add(restored);
            return restored;
        }

        @Override
        public void close() {
            List<Entity> drops = new ArrayList<>();
            for (Entity entity : helper.getLevel().getAllEntities()) {
                if (entity instanceof BasicEntityItem drop && SafariNetItem.isStoredOwner(drop.getEntityItem(), owner)) {
                    drops.add(drop);
                }
            }
            drops.forEach(Entity::discard);
            entities.close();
            for (FakePlayer player : players) {
                player.containerMenu = player.inventoryMenu;
                helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        }
    }

    private static final class BrokenSaveShip extends com.lulan.shincolle.entity.destroyer.EntityDestroyerI {
        private final boolean throwOnSave;

        private BrokenSaveShip(Level level, boolean throwOnSave) {
            super(ModEntities.DESTROYER_I.get(), level);
            this.throwOnSave = throwOnSave;
        }

        @Override
        public CompoundTag saveWithoutId(CompoundTag tag) {
            if (throwOnSave) throw new IllegalStateException("Injected serialization failure");
            return new CompoundTag();
        }
    }

    private static final class BrokenSaveHime extends com.lulan.shincolle.entity.hime.EntityNorthernHime {
        private final boolean throwOnSave;

        private BrokenSaveHime(Level level, boolean throwOnSave) {
            super(ModEntities.NORTHERN_HIME.get(), level);
            this.throwOnSave = throwOnSave;
        }

        @Override
        public CompoundTag saveWithoutId(CompoundTag tag) {
            if (throwOnSave) throw new IllegalStateException("Injected serialization failure");
            return new CompoundTag();
        }
    }
}
