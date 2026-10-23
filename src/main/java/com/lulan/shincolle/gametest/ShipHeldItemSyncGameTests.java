package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import io.netty.buffer.Unpooled;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipHeldItemSyncGameTests {
    private ShipHeldItemSyncGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void initialTrackingSnapshotIncludesBothInventoryHandsWithoutOpeningTheGui(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        helper.assertTrue(ship != null, "Fixture: create ship without opening a menu");
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        rod.setDamageValue(19);
        rod.getOrCreateTag().putString("HeldSyncFixture", "main rod with tracking-only NBT");
        ItemStack off = new ItemStack(Items.TORCH, 7);
        off.getOrCreateTag().putString("HeldSyncFixture", "off hand independent count and NBT");
        ship.getCapaShipInventory().setStackInSlot(22, rod);
        ship.getCapaShipInventory().setStackInSlot(23, off);
        helper.assertTrue(ship.getMainHandItemShip() == rod && ship.getOffHandItemShip() == off,
                "Fixture: custom inventory hands are the source");
        FriendlyByteBuf expected = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.writeItem(rod);
            expected.writeItem(off);
            byte[] hands = new byte[expected.readableBytes()];
            expected.readBytes(hands);
            S2CEntitySyncPacket.syncAllMisc(ship).encode(wire);
            S2CEntitySyncPacket decoded = new S2CEntitySyncPacket(wire);
            byte[] payload = decoded.getPayload();
            helper.assertTrue(payload.length >= hands.length
                            && Arrays.equals(hands, Arrays.copyOfRange(payload, payload.length - hands.length, payload.length)),
                    "Initial tracking snapshot must carry both inventory hands including damage, count and NBT");
            BasicEntityShip receiver = createShip(helper);
            FriendlyByteBuf received = new FriendlyByteBuf(Unpooled.wrappedBuffer(hands));
            try {
                readHands(receiver, received);
                assertHands(helper, receiver, rod, off);
                helper.assertTrue(receiver.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                                && receiver.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
                        "Display synchronization must leave vanilla combat equipment untouched");
            } finally {
                received.release();
            }
            helper.succeed();
        } finally {
            wire.release();
            expected.release();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void inPlaceChangesAndEmptyHandsProduceIndependentUpdatesWithoutRepeatingUnchangedStacks(
            GameTestHelper helper) {
        BasicEntityShip ship = createShip(helper);
        BasicEntityShip receiver = createShip(helper);
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        ItemStack off = new ItemStack(Items.TORCH, 7);
        ship.getCapaShipInventory().setStackInSlot(22, rod);
        ship.getCapaShipInventory().setStackInSlot(23, off);
        applyUpdate(helper, ship, receiver);
        assertHands(helper, receiver, rod, off);
        helper.assertTrue(pollUpdate(ship) == null, "No repeated update for unchanged hands");

        off.shrink(1);
        helper.assertTrue(off.getCount() == 6, "Fixture: mutate the existing offhand reference");
        applyUpdate(helper, ship, receiver);
        assertHands(helper, receiver, rod, off);

        rod.setDamageValue(23);
        applyUpdate(helper, ship, receiver);
        helper.assertTrue(receiver.getMainHandItemShip().getDamageValue() == 23, "Rod damage change reaches display");
        rod.getOrCreateTag().putString("HeldSyncFixture", "changed in place");
        S2CEntitySyncPacket snapshot = pollUpdate(ship);
        helper.assertTrue(snapshot != null, "NBT-only mutation creates an update");
        rod.getOrCreateTag().putString("HeldSyncFixture", "changed after packet creation");
        applyPacket(receiver, snapshot);
        helper.assertTrue("changed in place".equals(receiver.getMainHandItemShip().getTag().getString("HeldSyncFixture")),
                "Packet holds serialized values rather than the live mutable stack");
        applyUpdate(helper, ship, receiver);
        assertHands(helper, receiver, rod, off);

        ship.getCapaShipInventory().setStackInSlot(22, ItemStack.EMPTY);
        applyUpdate(helper, ship, receiver);
        assertHands(helper, receiver, ItemStack.EMPTY, off);
        ship.getCapaShipInventory().setStackInSlot(23, ItemStack.EMPTY);
        applyUpdate(helper, ship, receiver);
        assertHands(helper, receiver, ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(pollUpdate(ship) == null, "Empty hands do not repeatedly send");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void truncatedSecondHandDoesNotPartiallyReplaceTheFirstHand(GameTestHelper helper) {
        BasicEntityShip ship = createShip(helper);
        ItemStack beforeMain = new ItemStack(Items.STICK, 2);
        ItemStack beforeOff = new ItemStack(Items.TORCH, 3);
        ship.getCapaShipInventory().setStackInSlot(22, beforeMain);
        ship.getCapaShipInventory().setStackInSlot(23, beforeOff);
        FriendlyByteBuf incomplete = new FriendlyByteBuf(Unpooled.buffer());
        try {
            incomplete.writeItem(new ItemStack(Items.FISHING_ROD));
            boolean rejected = false;
            try {
                readHands(ship, incomplete);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "A packet missing the second hand is rejected");
            assertHands(helper, ship, beforeMain, beforeOff);
            helper.succeed();
        } finally {
            incomplete.release();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void naturalServerTicksObserveHandChangesWithoutAMenuOrActiveAi(GameTestHelper helper) {
        Vec3 relative = new Vec3(1, 2, 1);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            BasicEntityShip ship = entities.add(createShip(helper));
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.setInvulnerable(true);
            ship.setPos(helper.absoluteVec(relative));
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ItemStack rod = new ItemStack(Items.FISHING_ROD);
            ship.getCapaShipInventory().setStackInSlot(22, rod);
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Fixture: ticking ship");
            GameTestEntities.assertRegistered(helper, ship);
            helper.runAfterDelay(24, () -> {
                helper.assertTrue(ship.tickCount >= 16, "Fixture: natural server ticks ran");
                helper.assertTrue(pollUpdate(ship) == null,
                        "Natural server ticks have already emitted the initial hand snapshot");
                rod.setDamageValue(27);
            });
            helper.runAfterDelay(48, () -> {
                try (entities) {
                    helper.assertTrue(pollUpdate(ship) == null,
                            "Natural server ticks observe an in-place rod mutation without menu synchronization");
                    helper.succeed();
                }
            });
        }, relative);
    }

    private static BasicEntityShip createShip(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        helper.assertTrue(ship != null, "Fixture: create ship");
        return ship;
    }

    private static void assertHands(GameTestHelper helper, BasicEntityShip ship, ItemStack main, ItemStack off) {
        helper.assertTrue(ItemStack.matches(ship.getMainHandItemShip(), main), "Main hand item/count/NBT matches");
        helper.assertTrue(ItemStack.matches(ship.getOffHandItemShip(), off), "Off hand item/count/NBT matches");
    }

    private static S2CEntitySyncPacket pollUpdate(BasicEntityShip ship) {
        return (S2CEntitySyncPacket) invoke(BasicEntityShip.class, ship, "createHeldItemUpdate", new Class<?>[0]);
    }

    private static void readHands(BasicEntityShip ship, FriendlyByteBuf buf) {
        invoke(S2CEntitySyncPacket.class, null, "readHeldItems",
                new Class<?>[]{FriendlyByteBuf.class, BasicEntityShip.class}, buf, ship);
    }

    private static void applyUpdate(GameTestHelper helper, BasicEntityShip source, BasicEntityShip receiver) {
        S2CEntitySyncPacket update = pollUpdate(source);
        helper.assertTrue(update != null, "Changed hands produce an update");
        helper.assertTrue(update.getType() == S2CEntitySyncPacket.SyncShip_HeldItems, "Hand delta subtype");
        applyPacket(receiver, update);
        helper.assertTrue(pollUpdate(source) == null, "Snapshot copies prevent repeated changes");
    }

    private static void applyPacket(BasicEntityShip ship, S2CEntitySyncPacket packet) {
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf payload = null;
        try {
            packet.encode(wire);
            S2CEntitySyncPacket decoded = new S2CEntitySyncPacket(wire);
            payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(decoded.getPayload()));
            readHands(ship, payload);
        } finally {
            if (payload != null) {
                payload.release();
            }
            wire.release();
        }
    }

    private static Object invoke(Class<?> owner, Object target, String name, Class<?>[] parameters, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
