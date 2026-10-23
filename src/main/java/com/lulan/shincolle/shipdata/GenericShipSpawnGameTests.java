package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenericShipSpawnGameTests {
    private GenericShipSpawnGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_ship_client_spawn")
    public static void friendlySpawnPacketPlacesShipBeforeDormantIdentity(GameTestHelper helper) {
        initialPlacement(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_generic_ship_client_spawn")
    public static void hostileSpawnPacketPlacesShipBeforeDormantIdentity(GameTestHelper helper) {
        initialPlacement(helper, true);
    }

    private static void initialPlacement(GameTestHelper helper, boolean hostile) {
        helper.assertTrue(!helper.getLevel().isClientSide(), "Fixture must begin on the server side");
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            var type = hostile ? ModEntities.GENERIC_SHIP_HOSTILE.get() : ModEntities.GENERIC_SHIP.get();
            Mob source = entities.add(type.create(helper.getLevel()));
            helper.assertTrue(source != null, "Registered entity factory failed");
            String missingId = "missing:spawn_" + (hostile ? "hostile" : "friendly");
            ((DefinitionBackedShip) source).definitionState().receive(missingId, 17, true);
            Vec3 target = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
            var spawn = new ClientboundAddEntityPacket(source.getId(), source.getUUID(), target.x, target.y, target.z,
                    0, 0, type, 0, Vec3.ZERO, 0);
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                S2CEntitySyncPacket.syncShipDefinition(source).encode(wire);
                S2CEntitySyncPacket identity = new S2CEntitySyncPacket(wire);
                wire.clear();
                identity.encode(wire);
                helper.assertTrue(wire.readByte() == S2CEntitySyncPacket.SyncShip_Definition, "Wrong identity subtype");
                helper.assertTrue(wire.readInt() == source.getId(), "Wrong identity entity ID");
                byte[] payload = wire.readByteArray(wire.readVarInt());
                helper.assertTrue(!wire.isReadable(), "Unexpected identity envelope data");
                withClientSide(helper, () -> {
                    Mob fresh = entities.add(type.create(helper.getLevel()));
                    helper.assertTrue(fresh != null && fresh.level().isClientSide(), "Client factory fixture failed");
                    GenericShipState state = ((DefinitionBackedShip) fresh).definitionState();
                    helper.assertTrue(state.dormant() && state.shipId().isEmpty(), "Expected pre-identity dormancy");
                    List<String> failures = new ArrayList<>();
                    // The same virtual reconstruction call used by the vanilla client listener.
                    fresh.recreateFromPacket(spawn);
                    checkPosition(fresh, target, "spawn packet", failures);
                    FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
                    try {
                        // Apply the definition identity branch without loading the client singleton.
                        state.receive(data.readUtf(64), data.readVarLong(), data.readBoolean());
                        fresh.refreshDimensions();
                        ((IShipEmotion) fresh).setStateMinor(ID.M.ShipType, state.shipType(true));
                        helper.assertTrue(!data.isReadable(), "Unexpected identity payload data");
                    } finally {
                        data.release();
                    }
                    helper.assertTrue(state.dormant() && state.shipId().equals(missingId) && state.generation() == 17,
                            "Dormant identity was not received");
                    helper.assertTrue(fresh.isCustomNameVisible() && fresh.getName().getString().equals(missingId),
                            "Missing definition name must be available for display");
                    checkPosition(fresh, target, "dormant identity and dimensions", failures);
                    // Every guarded relocation entry point must remain usable by client synchronization.
                    fresh.setPos(target.x + 1, target.y, target.z);
                    checkPosition(fresh, target.add(1, 0, 0), "client setPos", failures);
                    fresh.moveTo(target.x + 2, target.y, target.z, 0, 0);
                    checkPosition(fresh, target.add(2, 0, 0), "client moveTo", failures);
                    fresh.teleportTo(target.x, target.y, target.z);
                    checkPosition(fresh, target, "client teleportTo", failures);
                    helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
                });
            } finally {
                wire.release();
            }
            helper.assertTrue(!helper.getLevel().isClientSide(), "Client predicate was not restored");
            helper.succeed();
        }
    }

    private static void checkPosition(Mob ship, Vec3 expected, String step, List<String> failures) {
        if (!ship.position().equals(expected)) {
            failures.add(step + ": expected " + expected + " but was " + ship.position());
        }
    }

    private static void withClientSide(GameTestHelper helper, Runnable check) {
        try {
            Field side = Level.class.getDeclaredField("isClientSide");
            side.setAccessible(true);
            boolean original = side.getBoolean(helper.getLevel());
            try {
                // Synchronous injection only: no world tick, registration, or cleanup while changed.
                side.setBoolean(helper.getLevel(), true);
                helper.assertTrue(helper.getLevel().isClientSide(), "Client predicate injection failed");
                check.run();
            } finally {
                side.setBoolean(helper.getLevel(), original);
            }
        } catch (ReflectiveOperationException exception) {
            helper.fail("Cannot inject the client side predicate: " + exception.getMessage());
        }
    }
}
