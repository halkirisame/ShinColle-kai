package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.GenericShipSpawnEgg;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import io.netty.buffer.Unpooled;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenericShipGameTests {
    private static final ShipDefinitionId SAMPLE = ShipDefinitionId.parse("shincolle_test:sample_ship");
    private static final Vec3 POSITION = new Vec3(1.5, 2, 1.5);

    private GenericShipGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlySampleMatchesBuiltInDestroyerAtSeveralLevels(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity sample = friendly(helper, entities, SAMPLE);
            BasicEntityShip original = entities.add(ModEntities.DESTROYER_RO.get().create(helper.getLevel()));
            helper.assertTrue(sample.getStateMinor(ID.M.NumGrudge) > 0 && !sample.getStateFlag(ID.F.NoFuel),
                    "friendly fixture lacks fuel");
            sample.getAttrs().setAttrsBonus(new byte[]{1, 2, 3, 4, 5, 6});
            original.getAttrs().setAttrsBonus(new byte[]{1, 2, 3, 4, 5, 6});
            for (int level : new int[]{1, 25, 100, 150}) {
                sample.setShipLevel(level, false);
                original.setShipLevel(level, false);
                helper.assertTrue(sample.getLevel() == level && original.getLevel() == level,
                        "level fixture was clamped or ignored: " + level);
                sample.calcShipAttributes(31, false);
                original.calcShipAttributes(31, false);
                helper.assertTrue(Arrays.equals(sample.getAttrs().getAttrsRaw(), original.getAttrs().getAttrsRaw()),
                        "raw base/growth mismatch at level " + level);
                helper.assertTrue(Arrays.equals(sample.getAttrs().getAttrsType(), original.getAttrs().getAttrsType()),
                        "growth-type mismatch at level " + level);
            }
            helper.assertTrue(sample.getShipClass() == 0, "generic ship allocated a numeric class");
            helper.assertTrue(sample.getName().getString().equals("Sample Ship"), "definition name not used");
            helper.assertTrue(sample.getDimensions(sample.getPose()).width == 0.5F, "wrong default width");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileSampleUsesTierTimesNonDefaultModifiers(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = hostile(helper, entities, SAMPLE);
            float[] modifiers = ShipDefinitionAttributes.columns(ShipDefinitionRegistry.server().get(SAMPLE).hostileModifiers());
            helper.assertTrue(modifiers[0] != 1 && modifiers[1] != 1, "hostile fixture uses default modifiers");
            int[] indices = {ID.Attrs.HP, ID.Attrs.ATK_L, ID.Attrs.DEF, ID.Attrs.SPD, ID.Attrs.MOV, ID.Attrs.HIT};
            for (int scale : new int[]{0, 2}) {
                ship.initAttrs(scale);
                helper.assertTrue(ship.getScaleLevel() == scale, "scale fixture was ignored");
                double[] tier = scale == 0 ? ConfigHandler.scaleMobSmall : ConfigHandler.scaleBossSmall;
                for (int i = 0; i < indices.length; i++) {
                    float expected = (float) tier[i] * modifiers[i];
                    helper.assertTrue(ship.getAttrs().getAttrsRaw(indices[i]) == expected,
                            "hostile raw mismatch: scale=" + scale + " column=" + i);
                }
                helper.assertTrue(ship.getAttrs().getAttrsRaw(ID.Attrs.ATK_H) == (float) tier[1] * modifiers[1] * 3,
                        "heavy attack multiplier changed");
            }
            helper.assertTrue(ship.getShipClass() == 0 && ship.getDropEgg().isEmpty(), "numeric class/drop path leaked");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericEntitiesRoundTripIdentityNameLevelAndInventory(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity friendly = friendly(helper, entities, SAMPLE);
            friendly.setCustomName(Component.literal("Saved sample"));
            friendly.setShipLevel(37, false);
            friendly.getCapaShipInventory().setStackInSlot(0, new ItemStack(ModItems.EQUIP_CANNON.get()));
            CompoundTag saved = new CompoundTag();
            helper.assertTrue(friendly.save(saved), "friendly save failed");
            GenericShipEntity restored = (GenericShipEntity) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
            helper.assertTrue(restored != null, "friendly entity type not restored");
            entities.add(restored);
            helper.assertTrue(restored.getShipId().equals(SAMPLE.toString()) && restored.getShipClass() == 0, "identity lost");
            helper.assertTrue(restored.getLevel() == 37 && restored.getName().getString().equals("Saved sample"), "individual state lost");
            helper.assertTrue(restored.getCapaShipInventory().getStackInSlot(0).is(ModItems.EQUIP_CANNON.get()), "inventory lost");
            GenericHostileShipEntity enemy = hostile(helper, entities, SAMPLE);
            enemy.initAttrs(2);
            enemy.setCustomName(Component.literal("Saved enemy"));
            CompoundTag hostileNbt = new CompoundTag();
            helper.assertTrue(enemy.save(hostileNbt), "hostile save failed");
            GenericHostileShipEntity restoredEnemy = (GenericHostileShipEntity) EntityType.loadEntityRecursive(
                    hostileNbt, helper.getLevel(), entity -> entity);
            helper.assertTrue(restoredEnemy != null, "hostile entity type not restored");
            entities.add(restoredEnemy);
            helper.assertTrue(restoredEnemy.getShipId().equals(SAMPLE.toString()) && restoredEnemy.getScaleLevel() == 2
                    && restoredEnemy.getName().getString().equals("Saved enemy"), "hostile state lost");
            BasicEntityShip legacy = entities.add(ModEntities.DESTROYER_RO.get().create(helper.getLevel()));
            helper.assertTrue(!legacy.saveWithoutId(new CompoundTag()).contains("ShipId"), "legacy save format changed");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void missingDefinitionsPreserveEntireNbtForBothEntitiesAndResume(GameTestHelper helper) {
        fixture(helper, entities -> {
            ShipDefinitionRegistry registry = ShipDefinitionRegistry.server();
            Map<ShipDefinitionId, ShipDefinition> original = registry.all();
            ShipDefinitionId id = ShipDefinitionId.parse("shincolle_test:dormant_fixture");
            ShipDefinition definition = copyAs(original.get(SAMPLE), id, null);
            Map<ShipDefinitionId, ShipDefinition> withFixture = new HashMap<>(original);
            withFixture.put(id, definition);
            registry.reload(() -> withFixture);
            try {
                GenericShipEntity active = friendly(helper, entities, id);
                GenericHostileShipEntity hostile = hostile(helper, entities, id);
                CompoundTag allyNbt = active.saveWithoutId(new CompoundTag());
                CompoundTag enemyNbt = hostile.saveWithoutId(new CompoundTag());
                allyNbt.putString("UnknownAddonState", "keep exactly");
                enemyNbt.putString("UnknownAddonState", "keep enemy exactly");
                registry.reload(() -> original);
                GenericShipEntity sleepingAlly = entities.add(ModEntities.GENERIC_SHIP.get().create(helper.getLevel()));
                GenericHostileShipEntity sleepingEnemy = entities.add(ModEntities.GENERIC_SHIP_HOSTILE.get().create(helper.getLevel()));
                sleepingAlly.load(allyNbt);
                sleepingEnemy.load(enemyNbt);
                for (int i = 0; i < 4; i++) {
                    sleepingAlly.tick();
                    sleepingEnemy.tick();
                    sleepingAlly.checkDespawn();
                    sleepingEnemy.checkDespawn();
                }
                helper.assertTrue(sleepingAlly.definitionState().dormant() && sleepingEnemy.definitionState().dormant(), "not dormant");
                helper.assertTrue(allyNbt.equals(sleepingAlly.saveWithoutId(new CompoundTag())), "friendly dormant NBT changed");
                helper.assertTrue(enemyNbt.equals(sleepingEnemy.saveWithoutId(new CompoundTag())), "hostile dormant NBT changed");
                helper.assertTrue(!sleepingAlly.doHurtTarget(hostile) && !sleepingEnemy.attackEntityWithAmmo(active),
                        "dormant entity attacked");
                helper.assertTrue(!sleepingAlly.isRemoved() && !sleepingEnemy.isRemoved(), "dormant entity despawned");
                for (Mob sleeping : new Mob[]{sleepingAlly, sleepingEnemy}) {
                    var vehicle = entities.add(EntityType.BOAT.create(helper.getLevel()));
                    vehicle.setPos(sleeping.getX(), sleeping.getY(), sleeping.getZ());
                    helper.assertTrue(sleeping.startRiding(vehicle, true), "riding fixture was not accepted");
                    Vec3 frozenPosition = sleeping.position();
                    vehicle.setPos(vehicle.getX() + 3, vehicle.getY(), vehicle.getZ() + 3);
                    sleeping.rideTick();
                    helper.assertTrue(sleeping.position().equals(frozenPosition), "dormant passenger moved with vehicle");
                    sleeping.stopRiding();
                }
                registry.reload(() -> withFixture);
                sleepingAlly.tick();
                sleepingEnemy.tick();
                helper.assertTrue(!sleepingAlly.definitionState().dormant() && !sleepingEnemy.definitionState().dormant(), "not resumed");
                helper.assertTrue(sleepingAlly.getShipId().equals(id.toString()) && sleepingEnemy.getShipId().equals(id.toString()), "ID lost");
                for (Mob resumed : new Mob[]{sleepingAlly, sleepingEnemy}) {
                    Vec3 before = resumed.position();
                    resumed.teleportTo(before.x + 2, before.y, before.z);
                    helper.assertTrue(resumed.getX() == before.x + 2, "movement remained frozen after definition reload");
                }
            } finally {
                registry.reload(() -> original);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void reloadUpdatesAttributesAndLossOfLiveDefinitionKeepsSnapshot(GameTestHelper helper) {
        fixture(helper, entities -> {
            ShipDefinitionRegistry registry = ShipDefinitionRegistry.server();
            Map<ShipDefinitionId, ShipDefinition> original = registry.all();
            ShipDefinitionId id = ShipDefinitionId.parse("shincolle_test:reload_fixture");
            Map<ShipDefinitionId, ShipDefinition> fixture = new HashMap<>(original);
            fixture.put(id, copyAs(original.get(SAMPLE), id, 30.0));
            registry.reload(() -> fixture);
            try {
                GenericShipEntity ship = friendly(helper, entities, id);
                float before = ship.getAttrs().getAttrsRaw(ID.Attrs.HP);
                fixture.put(id, copyAs(original.get(SAMPLE), id, 60.0));
                registry.reload(() -> fixture);
                ship.tick();
                helper.assertTrue(Math.abs(ship.getAttrs().getAttrsRaw(ID.Attrs.HP) - before - 30) < 0.0001,
                        "new definition not reflected on next tick");
                registry.reload(() -> original);
                ship.tick();
                CompoundTag frozen = ship.saveWithoutId(new CompoundTag());
                Vec3 position = ship.position();
                for (int i = 0; i < 3; i++) ship.tick();
                helper.assertTrue(ship.definitionState().dormant() && ship.position().equals(position), "live loss did not freeze movement");
                helper.assertTrue(frozen.equals(ship.saveWithoutId(new CompoundTag())), "live-loss snapshot changed");
                CompoundTag worldSave = new CompoundTag();
                helper.assertTrue(ship.save(worldSave), "dormant world save failed");
                helper.assertTrue(worldSave.getString("id").equals("shincolle_kai:generic_ship"), "entity type lost on world save");
                Entity reloaded = EntityType.loadEntityRecursive(worldSave, helper.getLevel(), entity -> entity);
                helper.assertTrue(reloaded instanceof GenericShipEntity, "dormant world entity could not be loaded");
                entities.add(reloaded);
                helper.assertTrue(((GenericShipEntity) reloaded).definitionState().dormant(), "world reload did not stay dormant");
                CompoundTag worldSaveAgain = new CompoundTag();
                helper.assertTrue(reloaded.save(worldSaveAgain) && worldSave.equals(worldSaveAgain), "world NBT changed while dormant");
                registry.reload(() -> fixture);
                ship.tick();
                reloaded.tick();
                helper.assertTrue(!ship.definitionState().dormant() && ship.getShipId().equals(id.toString()), "live ship did not resume");
                helper.assertTrue(!((GenericShipEntity) reloaded).definitionState().dormant(), "world-reloaded ship did not resume");
            } finally {
                registry.reload(() -> original);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void administratorCommandSpawnsBothSidesAndRejectsMissingIds(GameTestHelper helper) {
        fixture(helper, entities -> {
            CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                    .withLevel(helper.getLevel()).withPosition(helper.absoluteVec(POSITION)).withPermission(2).withSuppressedOutput();
            var commands = source.getServer().getCommands();
            String command = "shincolle ship spawn " + SAMPLE;
            try {
                helper.assertTrue(commands.performPrefixedCommand(source.withPermission(1), command) == 0, "unprivileged spawn accepted");
                helper.assertTrue(commands.performPrefixedCommand(source, command) == 1, "friendly command failed");
                helper.assertTrue(commands.performPrefixedCommand(source, command + " hostile") == 1, "hostile command failed");
                helper.assertTrue(commands.performPrefixedCommand(source, "shincolle ship spawn shincolle_test:missing") == 0,
                        "unknown definition accepted");
                var ships = helper.getLevel().getEntitiesOfClass(Mob.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(5),
                        entity -> entity instanceof DefinitionBackedShip ship && ship.getShipId().equals(SAMPLE.toString()));
                ships.forEach(entities::add);
                helper.assertTrue(ships.size() == 2, "command did not add both entities");
            } finally {
                helper.getLevel().getEntitiesOfClass(Mob.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(5),
                        entity -> entity instanceof DefinitionBackedShip).forEach(entities::add);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericEggUsesShipIdAndSpawnsBothSides(GameTestHelper helper) {
        fixture(helper, entities -> {
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "GenericEggFixture"));
            ItemStack previous = player.getMainHandItem();
            try {
                for (boolean enemy : new boolean[]{false, true}) {
                    ItemStack egg = GenericShipSpawnEgg.create(SAMPLE, enemy);
                    helper.assertTrue(egg.getTag().getString("ShipId").equals(SAMPLE.toString())
                            && !egg.getTag().contains("ShipClass"), "egg used numeric class identity");
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, egg);
                    var hit = new net.minecraft.world.phys.BlockHitResult(helper.absoluteVec(POSITION),
                            net.minecraft.core.Direction.UP, helper.absolutePos(new BlockPos(1, 1, 1)), false);
                    var result = egg.useOn(new net.minecraft.world.item.context.UseOnContext(player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                    helper.assertTrue(result.consumesAction() && egg.isEmpty(), "egg did not spawn/consume");
                }
                var ships = helper.getLevel().getEntitiesOfClass(Mob.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(5),
                        entity -> entity instanceof DefinitionBackedShip);
                ships.forEach(entities::add);
                helper.assertTrue(ships.size() == 2 && ships.stream().anyMatch(GenericShipEntity.class::isInstance)
                        && ships.stream().anyMatch(GenericHostileShipEntity.class::isInstance), "egg sides not spawned");
            } finally {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, previous);
                helper.getLevel().getEntitiesOfClass(Mob.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(5),
                        entity -> entity instanceof DefinitionBackedShip).forEach(entities::add);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipIdentityPacketCarriesIdGenerationAndDormancy(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity ship = friendly(helper, entities, SAMPLE);
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                S2CEntitySyncPacket.syncShipDefinition(ship).encode(wire);
                S2CEntitySyncPacket decoded = new S2CEntitySyncPacket(wire);
                wire.clear();
                decoded.encode(wire);
                helper.assertTrue(wire.readByte() == S2CEntitySyncPacket.SyncShip_Definition, "wrong subtype");
                helper.assertTrue(wire.readInt() == ship.getId(), "wrong entity ID");
                int length = wire.readVarInt();
                byte[] payload = wire.readByteArray(length);
                FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
                try {
                    helper.assertTrue(data.readUtf(64).equals(SAMPLE.toString()), "ShipId missing");
                    helper.assertTrue(data.readVarLong() == ShipDefinitionRegistry.server().generation(), "generation missing");
                    helper.assertTrue(!data.readBoolean() && !data.isReadable(), "dormancy/wire mismatch");
                } finally {
                    data.release();
                }
            } finally {
                wire.release();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void standardForgeNegotiationRejectsOldAndAbsentProtocols(GameTestHelper helper) {
        try {
            Field field = ModNetworking.CHANNEL.getClass().getDeclaredField("instance");
            field.setAccessible(true);
            Object instance = field.get(ModNetworking.CHANNEL);
            for (String methodName : new String[]{"tryClientVersionOnServer", "tryServerVersionOnClient"}) {
                Method method = instance.getClass().getDeclaredMethod(methodName, String.class);
                method.setAccessible(true);
                helper.assertTrue((Boolean) method.invoke(instance, "17"), "current protocol rejected");
                for (String old : new String[]{"14", "15", "16", "ABSENT", "ACCEPTVANILLA"}) {
                    helper.assertTrue(!(Boolean) method.invoke(instance, old), "mismatched protocol accepted: " + old);
                }
            }
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not inspect standard Forge protocol negotiation: " + exception);
        }
    }

    private static ShipDefinition copyAs(ShipDefinition source, ShipDefinitionId id, Double hp) {
        Map<String, Double> stats = new HashMap<>(source.baseStats());
        if (hp != null) stats.put("hp", hp);
        return new ShipDefinition(id, source.schema(), source.displayName(), source.shipType(), source.visual(),
                stats, source.growth(), source.equipment(), source.construction(), source.spawn(), source.behavior(),
                source.dimensions(), source.mountOffset(), source.equipType(), source.attackProfile(), source.hostileModifiers());
    }

    private static GenericShipEntity friendly(GameTestHelper helper, GameTestEntities entities, ShipDefinitionId id) {
        return place(helper, entities, (GenericShipEntity) GenericShipSpawner.create(helper.getLevel(), id, false, null));
    }

    private static GenericHostileShipEntity hostile(GameTestHelper helper, GameTestEntities entities, ShipDefinitionId id) {
        return place(helper, entities, (GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), id, true, null));
    }

    private static <T extends Mob> T place(GameTestHelper helper, GameTestEntities entities, T ship) {
        helper.assertTrue(ship != null, "definition spawn failed");
        entities.add(ship);
        Vec3 position = helper.absoluteVec(POSITION);
        ship.moveTo(position.x, position.y, position.z, 0, 0);
        ship.setInvulnerable(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "entity not added");
        GameTestEntities.assertRegistered(helper, ship);
        return ship;
    }

    private static void fixture(GameTestHelper helper, java.util.function.Consumer<GameTestEntities> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                verification.accept(entities);
                helper.succeed();
            }
        }, POSITION);
    }
}
