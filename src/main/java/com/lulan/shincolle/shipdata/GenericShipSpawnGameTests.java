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
    public static void emptyIdentityCannotJoinButMissingNamedDefinitionsRemainDormant(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (var type : List.of(ModEntities.GENERIC_SHIP.get(), ModEntities.GENERIC_SHIP_HOSTILE.get())) {
                Mob empty = entities.add(type.create(helper.getLevel()));
                helper.assertTrue(empty != null, "Registered entity factory failed");
                helper.assertTrue(((DefinitionBackedShip) empty).getShipId().isEmpty(), "Expected empty identity");
                boolean added = helper.getLevel().addFreshEntity(empty);
                helper.assertTrue(!added && helper.getLevel().getEntity(empty.getUUID()) == null,
                        "Empty ShipId entity remained in world at " + empty.position());
                for (boolean explicitEmptyId : new boolean[]{false, true}) {
                    var summonNbt = new net.minecraft.nbt.CompoundTag();
                    var uuid = java.util.UUID.randomUUID();
                    summonNbt.putUUID("UUID", uuid);
                    if (explicitEmptyId) summonNbt.putString("ShipId", "");
                    var source = helper.getLevel().getServer().createCommandSourceStack()
                            .withLevel(helper.getLevel()).withPosition(helper.absoluteVec(new Vec3(2, 2, 2)))
                            .withPermission(4).withSuppressedOutput();
                    String name = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                    source.getServer().getCommands().performPrefixedCommand(source, "summon " + name + " ~ ~ ~ " + summonNbt);
                    var remaining = helper.getLevel().getEntity(uuid);
                    if (remaining != null) entities.add(remaining);
                    helper.assertTrue(remaining == null, "Summon retained a ship without an identity");
                }
                withClientSide(helper, () -> {
                    Mob client = entities.add(type.create(helper.getLevel()));
                    var event = new net.minecraftforge.event.entity.EntityJoinLevelEvent(client, helper.getLevel());
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
                    helper.assertTrue(!event.isCanceled() && !client.isRemoved(), "Client spawn must wait for identity");
                });
                Mob missing = entities.add(type.create(helper.getLevel()));
                var nbt = new net.minecraft.nbt.CompoundTag();
                nbt.putString("ShipId", "missing:preserved_ship");
                missing.load(nbt);
                helper.assertTrue(helper.getLevel().addFreshEntity(missing), "Named dormant ship was rejected");
                helper.assertTrue(((DefinitionBackedShip) missing).definitionState().dormant(), "Expected dormancy");
                helper.assertTrue(missing.saveWithoutId(new net.minecraft.nbt.CompoundTag()).equals(nbt),
                        "Dormant NBT changed");
            }
            helper.succeed();
        }
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
                        var individual = new ShipIndividual.Presentation(data.readUtf(128),
                                data.readEnum(HostileVariants.Rarity.class), data.readUtf(128), data.readBoolean());
                        helper.assertTrue(individual.equals(ShipIndividual.Presentation.NORMAL),
                                "Unselected ship must have normal individual presentation");
                        if (fresh instanceof com.lulan.shincolle.entity.GenericHostileShipEntity enemy) {
                            enemy.receiveIndividualPresentation(individual);
                        }
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
