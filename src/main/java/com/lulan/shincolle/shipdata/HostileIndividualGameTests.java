package com.lulan.shincolle.shipdata;

import com.google.gson.JsonParser;
import com.lulan.shincolle.command.ShipCmdSpawnDefinition;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostileIndividualGameTests {
    private static final ShipDefinitionId SHIP = ShipDefinitionId.parse("shincolle_test:individual");
    private static final Vec3 POSITION = new Vec3(2, 4, 2);
    private static final String VARIANTS = """
            {"normal_weight":0,"variants":[{"id":"flagship","weight":1,"name":"variant.test.flagship",
            "rarity":"epic","traits":["shincolle_kai:sturdy","shincolle_kai:heavy_hitter","shincolle_kai:armored"],
            "hostile_modifiers":{"hp":1.5,"atk":1.5,"def":2.4,"spd":1.2,"mov":0.5,"hit":1.5},
            "visual_variant":"addon:textures/entity/flagship.png","red_aura":true}]}
            """;

    private HostileIndividualGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual_natural", timeoutTicks = 200)
    public static void firstNaturalServerTickRollsOnceAfterScaleAndPreservesHealthRatio(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            Map<ShipDefinitionId, ShipDefinition> before = ShipDefinitionRegistry.server().all();
            restoreOnCompletion(helper, before);
            publish(VARIANTS);
            GenericHostileShipEntity ship = spawn(helper, entities);
            ship.initAttrs(1);
            ship.setHealth(ship.getMaxHealth() / 4);
            CountingRandom random = random(ship);
            helper.assertTrue(!ship.individual().rolled() && ship.getScaleLevel() == 1, "Spawn rolled before final scale");
            helper.assertTrue(ship.getHealth() / ship.getMaxHealth() == 0.25F, "Health fixture ignored");
            boolean[] observed = {false};
            helper.onEachTick(() -> {
                if (ship.tickCount == 0) return;
                if (!observed[0]) {
                    helper.assertTrue(ship.tickCount == 1, "First server tick was not observed");
                    observed[0] = true;
                }
                helper.assertTrue(ship.individual().rolled() && ship.individual().variant().equals("flagship"),
                        "First natural server tick failed to roll");
                helper.assertTrue(random.variantDraws == 1, "Variant did not use exactly one random draw");
                helper.assertTrue(Math.abs(ship.getHealth() / ship.getMaxHealth() - 0.25F) < 0.001F,
                        "Variant healed or lost the initial HP ratio");
                close(helper, ship.getAttrs().getAttrsRaw(ID.Attrs.HP),
                        (float) ConfigHandler.scaleMobLarge[0] * 1.1F * 1.5F, "Roll used pre-spawn scale");
                if (ship.tickCount >= 8) helper.succeed();
            });
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual")
    public static void snapshotSurvivesSaveDormancyRecoveryAndVariantRemoval(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = spawn(helper, entities);
            ship.selectVariant(definition().hostileVariants().get("flagship"));
            ship.setHealth(ship.getMaxHealth() / 3);
            CompoundTag original = ship.individual().save();
            CompoundTag saved = ship.saveWithoutId(new CompoundTag());
            GenericHostileShipEntity loaded = entities.add(ModEntities.GENERIC_SHIP_HOSTILE.get().create(helper.getLevel()));
            loaded.load(saved);
            helper.assertTrue(loaded.individual().save().equals(original), "Load lost snapshot");
            helper.assertTrue(loaded.getHealth() < loaded.getMaxHealth() / 2, "Load healed the ship");
            publish("{\"variants\":[]}");
            loaded.tick();
            helper.assertTrue(loaded.individual().save().equals(original), "Reload rewrote snapshot");
            close(helper, loaded.getAttrs().getAttrsRaw(ID.Attrs.HP), (float) ConfigHandler.scaleMobSmall[0] * 1.1F,
                    "Removed variant still applies encounter multiplier, or removed snapshotted traits");
            helper.assertTrue(loaded.getHealth() < loaded.getMaxHealth(), "Reload healed the ship");
            publish(VARIANTS);
            loaded.tick();
            close(helper, loaded.getAttrs().getAttrsRaw(ID.Attrs.HP), (float) ConfigHandler.scaleMobSmall[0] * 1.1F * 1.5F,
                    "Restored variant did not resume effect");
            publish(VARIANTS.replace("variant.test.flagship", "variant.test.changed").replace("sturdy", "swift"));
            loaded.tick();
            helper.assertTrue(loaded.individual().save().equals(original), "Changed definition rewrote the snapshot");
            close(helper, loaded.getAttrs().getAttrsRaw(ID.Attrs.HP), (float) ConfigHandler.scaleMobSmall[0] * 1.1F * 1.5F,
                    "Changed trait metadata replaced the snapshot");
            ShipDefinitionRegistry.server().reload(Map::of);
            loaded.tick();
            helper.assertTrue(loaded.definitionState().dormant(), "Missing definition did not sleep");
            CompoundTag sleeping = loaded.saveWithoutId(new CompoundTag());
            helper.assertTrue(sleeping.getCompound(ShipIndividual.KEY).equals(original), "Preserved save lost individual");
            GenericHostileShipEntity sleepingLoad = entities.add(ModEntities.GENERIC_SHIP_HOSTILE.get().create(helper.getLevel()));
            sleepingLoad.load(sleeping);
            helper.assertTrue(sleepingLoad.saveWithoutId(new CompoundTag()).getCompound(ShipIndividual.KEY).equals(original),
                    "Dormant load/save lost individual");
            publish(VARIANTS);
            sleepingLoad.tick();
            helper.assertTrue(!sleepingLoad.definitionState().dormant() && sleepingLoad.individual().save().equals(original),
                    "Recovery lost individual");
            // Entity.restoreFrom is also the vanilla cross-dimension copy boundary.
            GenericHostileShipEntity transferred = entities.add(ModEntities.GENERIC_SHIP_HOSTILE.get().create(helper.getLevel()));
            transferred.restoreFrom(sleepingLoad);
            helper.assertTrue(transferred.individual().save().equals(original), "Dimension copy lost individual");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual")
    public static void hostileTraitsAndEncounterApplyBeforeExistingDefenseCap(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = spawn(helper, entities);
            ship.initAttrs(3);
            ship.selectVariant(definition().hostileVariants().get("flagship"));
            double[] base = ConfigHandler.scaleBossLarge;
            int[] indices = {ID.Attrs.HP, ID.Attrs.ATK_L, ID.Attrs.DEF, ID.Attrs.SPD, ID.Attrs.MOV, ID.Attrs.HIT};
            float[] multipliers = {1.1F * 1.5F, 1.1F * 1.5F, 1.1F * 2.4F, 1.2F, 0.5F, 1.5F};
            for (int iteration = 0; iteration < 3; iteration++) {
                ship.calcShipAttributes(31, false);
                for (int i = 0; i < 6; i++) close(helper, ship.getAttrs().getAttrsRaw(indices[i]),
                        (float) base[i] * multipliers[i], "Raw column " + i + " was not rebuilt");
                close(helper, ship.getAttrs().getAttrsRaw(ID.Attrs.ATK_H), (float) base[1] * multipliers[1] * 3,
                        "Heavy attack lost multiplier");
                close(helper, ship.getAttrs().getAttrsRaw(ID.Attrs.ATK_AL), (float) base[1] * multipliers[1],
                        "Air light attack lost multiplier");
                close(helper, ship.getAttrs().getAttrsRaw(ID.Attrs.ATK_AH), (float) base[1] * multipliers[1] * 3,
                        "Air heavy attack lost multiplier");
                helper.assertTrue(ship.getAttrs().getAttrsRaw(ID.Attrs.DEF) > 1, "Defense cap fixture never exceeded 1");
                close(helper, ship.getAttrs().getAttrsBuffed(ID.Attrs.DEF), 0.8F, "Existing defense cap did not apply");
            }
            for (String trait : new String[]{"quick_loader", "swift", "keen_eye"}) {
                var json = JsonParser.parseString("""
                        {"normal_weight":0,"variants":[{"id":"single","weight":1,"traits":["shincolle_kai:%s"]}]}
                        """.formatted(trait));
                publish(json.toString());
                ship.selectVariant(definition().hostileVariants().get("single"));
                int column = trait.equals("quick_loader") ? 3 : trait.equals("swift") ? 4 : 5;
                close(helper, ship.getAttrs().getAttrsRaw(indices[column]), (float) base[column] * 1.1F,
                        "Trait not applied: " + trait);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual")
    public static void oldSaveAndOmittedVariantsDoNotDrawOrChangeSaveShape(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity old = spawn(helper, entities);
            CompoundTag legacy = old.saveWithoutId(new CompoundTag());
            helper.assertTrue(legacy.contains(ShipIndividual.KEY)
                    && !legacy.getCompound(ShipIndividual.KEY).getBoolean("Rolled"), "Pre-tick save lost unrolled marker");
            old.load(legacy);
            helper.assertTrue(!old.individual().rolled(), "Pre-tick load was mistaken for a legacy save");
            CountingRandom freshRandom = random(old);
            old.tick();
            helper.assertTrue(old.individual().variant().equals("flagship") && freshRandom.variantDraws == 1,
                    "Pre-tick save/load prevented the first draw");
            legacy.remove(ShipIndividual.KEY);
            old.load(legacy);
            CountingRandom legacyRandom = random(old);
            old.tick();
            helper.assertTrue(old.individual().rolled() && old.individual().variant().isEmpty()
                    && legacyRandom.variantDraws == 0, "Legacy save was retroactively rolled");
            publish(null);
            GenericHostileShipEntity plain = spawn(helper, entities);
            float[] raw = plain.getAttrs().getAttrsRaw().clone();
            CountingRandom plainRandom = random(plain);
            plain.tick();
            helper.assertTrue(plainRandom.variantDraws == 0 && Arrays.equals(raw, plain.getAttrs().getAttrsRaw()),
                    "Omitted metadata changed random or raw stats");
            helper.assertTrue(!plain.saveWithoutId(new CompoundTag()).contains(ShipIndividual.KEY),
                    "Omitted metadata changed save shape");
            publish("{}");
            GenericHostileShipEntity explicit = spawn(helper, entities);
            explicit.tick();
            helper.assertTrue(explicit.individual().rolled()
                    && explicit.saveWithoutId(new CompoundTag()).getCompound(ShipIndividual.KEY).getBoolean("Rolled"),
                    "Explicit empty table lost its rolled normal marker");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual")
    public static void bareSummonRollsButExplicitNormalAndFutureVersionsDoNot(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = spawn(helper, entities);
            CompoundTag summon = new CompoundTag();
            summon.putString("ShipId", SHIP.toString());
            ship.load(summon);
            CountingRandom random = random(ship);
            ship.tick();
            helper.assertTrue(random.variantDraws == 1 && ship.individual().variant().equals("flagship"),
                    "Bare NBT summon did not roll");
            CompoundTag normal = ship.saveWithoutId(new CompoundTag());
            normal.put(ShipIndividual.KEY, ShipIndividual.normal(true).save());
            ship.load(normal);
            random = random(ship);
            ship.tick();
            helper.assertTrue(random.variantDraws == 0 && ship.individual().variant().isEmpty(), "Normal save rerolled");
            CompoundTag future = new CompoundTag();
            future.putInt("V", 99);
            future.putString("NewField", "keep");
            normal.put(ShipIndividual.KEY, future);
            ship.load(normal);
            random = random(ship);
            ship.tick();
            helper.assertTrue(random.variantDraws == 0 && ship.individualPresentation().equals(ShipIndividual.Presentation.NORMAL),
                    "Future version applied effects");
            helper.assertTrue(ship.saveWithoutId(new CompoundTag()).getCompound(ShipIndividual.KEY).equals(future),
                    "Future payload was destroyed");
            CompoundTag unknown = ShipIndividual.normal(true).save();
            unknown.putString("Variant", "missing");
            net.minecraft.nbt.ListTag traits = new net.minecraft.nbt.ListTag();
            traits.add(net.minecraft.nbt.StringTag.valueOf("addon:missing_trait"));
            unknown.put("Traits", traits);
            normal.put(ShipIndividual.KEY, unknown);
            ship.load(normal);
            ship.tick();
            close(helper, ship.getAttrs().getAttrsRaw(ID.Attrs.HP), (float) ConfigHandler.scaleMobSmall[0],
                    "Unknown trait or variant applied an effect");
            helper.assertTrue(ship.individual().save().equals(unknown), "Unknown IDs were not retained");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual")
    public static void identityPacketSendsOnlyPresentationSnapshot(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = spawn(helper, entities);
            ship.selectVariant(definition().hostileVariants().get("flagship"));
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                S2CEntitySyncPacket.syncShipDefinition(ship).encode(wire);
                S2CEntitySyncPacket decoded = new S2CEntitySyncPacket(wire);
                wire.clear();
                decoded.encode(wire);
                helper.assertTrue(wire.readByte() == S2CEntitySyncPacket.SyncShip_Definition && wire.readInt() == ship.getId(),
                        "Identity envelope mismatch");
                FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(wire.readByteArray(wire.readVarInt())));
                try {
                    helper.assertTrue(data.readUtf(64).equals(SHIP.toString())
                            && data.readVarLong() == ShipDefinitionRegistry.server().generation() && !data.readBoolean(),
                            "Identity metadata mismatch");
                    helper.assertTrue(data.readUtf(128).equals("variant.test.flagship")
                            && data.readEnum(HostileVariants.Rarity.class) == HostileVariants.Rarity.EPIC
                            && data.readUtf(128).equals("addon:textures/entity/flagship.png") && data.readBoolean()
                            && !data.isReadable(), "Individual presentation missing, or private trait/variant IDs leaked");
                } finally {
                    data.release();
                }
            } finally {
                wire.release();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual")
    public static void manualNameOutranksTranslatableIndividualAndBossName(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = spawn(helper, entities);
            helper.assertTrue(ship.getName().getString().equals("Individual Test"), "Default type name changed");
            ship.initAttrs(2);
            ship.selectVariant(definition().hostileVariants().get("flagship"));
            helper.assertTrue(ship.getName().getContents() instanceof TranslatableContents translated
                    && translated.getKey().equals("variant.test.flagship"), "Individual name is not translatable");
            helper.assertTrue(ship.getName().getString().equals("Individual Test"), "Missing translation has no type fallback");
            helper.assertTrue(boss(ship).getName().equals(ship.getDisplayName()), "Boss name differs from individual name");
            ship.setCustomName(Component.literal("Manual"));
            helper.assertTrue(ship.getName().getString().equals("Manual") && boss(ship).getName().equals(ship.getDisplayName()),
                    "Manual name did not win for entity and boss");
            ship.setCustomName(null);
            helper.assertTrue(ship.getName().getContents() instanceof TranslatableContents
                    && boss(ship).getName().equals(ship.getDisplayName()), "Clearing custom name lost individual name");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_individual")
    public static void administratorVariantCommandSelectsOrRejectsWithoutSpawning(GameTestHelper helper) {
        fixture(helper, entities -> {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            ShipCmdSpawnDefinition.register(dispatcher);
            CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                    .withLevel(helper.getLevel()).withPosition(helper.absoluteVec(POSITION)).withPermission(4).withSuppressedOutput();
            String command = "shincolle ship spawn " + SHIP + " hostile ";
            AABB area = new AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(16);
            int before = helper.getLevel().getEntitiesOfClass(GenericHostileShipEntity.class, area).size();
            try {
                dispatcher.execute(command + "missing", source);
                helper.fail("Unknown variant did not raise a command error");
            } catch (CommandSyntaxException expected) {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(GenericHostileShipEntity.class, area).size() == before,
                        "Invalid variant spawned a ship");
            }
            try {
                helper.assertTrue(dispatcher.execute(command + "flagship", source) == 1, "Variant command failed");
                var ships = helper.getLevel().getEntitiesOfClass(GenericHostileShipEntity.class, area);
                helper.assertTrue(ships.size() == before + 1, "Command did not spawn exactly one ship");
                for (var ship : ships) {
                    if (!ship.getShipId().equals(SHIP.toString())) continue;
                    entities.add(ship);
                    helper.assertTrue(ship.individual().rolled() && ship.individual().variant().equals("flagship"),
                            "Command did not pin the variant");
                }
            } catch (CommandSyntaxException error) {
                helper.fail("Variant command failed: " + error);
            }
        });
    }

    private static void fixture(GameTestHelper helper, Consumer<GameTestEntities> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Map<ShipDefinitionId, ShipDefinition> before = ShipDefinitionRegistry.server().all();
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                publish(VARIANTS);
                verification.accept(entities);
                helper.succeed();
            } finally {
                ShipDefinitionRegistry.server().reload(() -> before);
            }
        }, POSITION);
    }

    private static void publish(String variants) {
        var json = JsonParser.parseString("""
                {"schema":1,"display_name":"Individual Test","ship_type":"shincolle_kai:destroyer",
                "visual":"shincolle_test:visual","base_stats":{}}
                """).getAsJsonObject();
        if (variants != null) json.add("hostile_variants", JsonParser.parseString(variants));
        ShipDefinition definition = new ShipDefinitionValidator().validate(SHIP, json, ignored -> { });
        ShipDefinitionRegistry.server().reload(() -> Map.of(SHIP, definition));
    }

    private static ShipDefinition definition() {
        return ShipDefinitionRegistry.server().get(SHIP);
    }

    private static GenericHostileShipEntity spawn(GameTestHelper helper, GameTestEntities entities) {
        GenericHostileShipEntity ship = entities.add((GenericHostileShipEntity)
                GenericShipSpawner.create(helper.getLevel(), SHIP, true, null));
        helper.assertTrue(ship != null, "Hostile fixture not created");
        ship.setInvulnerable(true);
        ship.setNoGravity(true);
        ship.moveTo(helper.absoluteVec(POSITION));
        GameTestEntities.removeAllGoalsEachTick(helper, ship);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Hostile fixture not added");
        return ship;
    }

    private static CountingRandom random(GenericHostileShipEntity ship) {
        CountingRandom random = new CountingRandom();
        try {
            Field field = Entity.class.getDeclaredField("random");
            field.setAccessible(true);
            field.set(ship, random);
            return random;
        } catch (ReflectiveOperationException error) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Random fixture failed: " + error);
        }
    }

    private static ServerBossEvent boss(GenericHostileShipEntity ship) {
        try {
            Field field = BasicEntityShipHostile.class.getDeclaredField("bossEvent");
            field.setAccessible(true);
            return (ServerBossEvent) field.get(ship);
        } catch (ReflectiveOperationException error) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Boss inspection failed: " + error);
        }
    }

    private static void restoreOnCompletion(GameTestHelper helper, Map<ShipDefinitionId, ShipDefinition> before) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) { }
                public void testPassed(GameTestInfo info) { ShipDefinitionRegistry.server().reload(() -> before); }
                public void testFailed(GameTestInfo info) { ShipDefinitionRegistry.server().reload(() -> before); }
            });
        } catch (ReflectiveOperationException error) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Cleanup registration failed: " + error);
        }
    }

    private static void close(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.001F, message + ": " + actual + " != " + expected);
    }

    private static final class CountingRandom extends LegacyRandomSource {
        private int variantDraws;

        private CountingRandom() { super(874); }

        @Override
        public int nextInt(int bound) {
            if (bound == 1) variantDraws++;
            return super.nextInt(bound);
        }
    }
}
