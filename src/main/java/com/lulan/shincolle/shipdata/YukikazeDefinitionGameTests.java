package com.lulan.shincolle.shipdata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.crafting.ShipCalc;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.visualdata.ShipVisualValidator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YukikazeDefinitionGameTests {
    private static final ShipDefinitionId YUKIKAZE = ShipDefinitionId.parse("shincolle_kai:yukikaze");
    private static final Path ASSETS = Path.of(System.getProperty("shincolle.publicationRoot", "."))
            .resolve("src/main/resources/assets");

    private YukikazeDefinitionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void officialYukikazeSpawnsAlliedWithResolvedServerDefinition(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericShipEntity ship = (GenericShipEntity) GenericShipSpawner.create(helper.getLevel(), YUKIKAZE, false, null);
            helper.assertTrue(ship != null, "Yukikaze allied definition not spawnable");
            entities.add(ship);
            place(helper, ship);
            checkDefinition(helper, ship);
            helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "missing fuel");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void officialYukikazeSpawnsHostileWithoutLoadingClientClasses(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = (GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), YUKIKAZE, true, null);
            helper.assertTrue(ship != null, "Yukikaze hostile definition not spawnable");
            entities.add(ship);
            place(helper, ship);
            checkDefinition(helper, ship);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void yukikazeIsExcludedFromBothConstructionTiers(GameTestHelper helper) {
        ShipDefinition definition = ShipDefinitionRegistry.server().get(YUKIKAZE);
        helper.assertTrue(definition != null && !definition.construction().enabled(), "Yukikaze construction must remain disabled");
        for (int tier = 0; tier < 2; tier++) {
            RandomSource random = RandomSource.create(731);
            for (int amount : new int[]{16, 100, 500, 2600, 5000}) {
                for (int roll = 0; roll < 128; roll++) {
                    ShipChoice choice = ShipCalc.rollShipType(tier, new int[]{amount, amount, amount, amount}, random);
                    helper.assertTrue(!choice.equals(new ShipChoice.Defined(YUKIKAZE)), "disabled Yukikaze selected for construction");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void yukikazeOccupiesFifteenWildSpawnWeightSlotsAtBothNormalScales(GameTestHelper helper) {
        ShipDefinition definition = ShipDefinitionRegistry.server().get(YUKIKAZE);
        helper.assertTrue(definition != null && definition.spawn().enabled() && definition.spawn().weight() == 15,
                "Yukikaze wild spawn settings changed");
        long total = 700;
        for (ShipDefinition candidate : ShipDefinitionRegistry.server().all().values()) {
            if (candidate.spawn().enabled() && candidate.spawn().weight() > 0) total += candidate.spawn().weight();
        }
        try {
            var method = EntityHelper.class.getDeclaredMethod("pickRandomMobShipType", RandomSource.class, int.class);
            method.setAccessible(true);
            for (int scale = 0; scale < 2; scale++) {
                int selected = 0;
                for (long slot = 0; slot < total; slot++) {
                    double roll = (slot + 0.5) / total;
                    RandomSource random = new LegacyRandomSource(0) {
                        @Override
                        public int nextInt(int bound) {
                            return (int) (roll * bound);
                        }
                    };
                    if (method.invoke(null, random, scale).equals(new ShipChoice.Defined(YUKIKAZE))) selected++;
                }
                helper.assertTrue(selected == 15, "Yukikaze does not have exactly fifteen wild spawn slots at scale " + scale);
            }
        } catch (ReflectiveOperationException exception) {
            helper.fail("Cannot inspect wild spawn selection boundary: " + exception);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void yukikazeVisualResolvesBundledGeometryExpressionsAnimationsAndLod(GameTestHelper helper) {
        ShipDefinition definition = ShipDefinitionRegistry.server().get(YUKIKAZE);
        helper.assertTrue(definition != null, "Yukikaze definition missing");
        ResourceLocation id = definition.visual();
        try {
            List<String> warnings = new ArrayList<>();
            var visual = new ShipVisualValidator().validate(read(ASSETS.resolve(id.getNamespace())
                    .resolve("shincolle_kai/visuals/" + id.getPath() + ".json")), warnings::add);
            helper.assertTrue(warnings.isEmpty(), "unknown visual keys: " + warnings);
            for (ResourceLocation resource : List.of(visual.model(), visual.texture(), visual.animation())) {
                helper.assertTrue(Files.isRegularFile(asset(resource)), "missing visual asset: " + resource);
            }
            Set<String> bones = new HashSet<>();
            for (var geometry : read(asset(visual.model())).getAsJsonArray("minecraft:geometry")) {
                for (var bone : geometry.getAsJsonObject().getAsJsonArray("bones")) {
                    bones.add(bone.getAsJsonObject().get("name").getAsString());
                }
            }
            helper.assertTrue(bones.containsAll(visual.rig().values()) && bones.containsAll(visual.glowBones())
                    && bones.containsAll(visual.lod().hideBones()), "visual references missing bones");
            helper.assertTrue(visual.lod().distance() == 48 && !visual.lod().hideBones().isEmpty(), "distant detail LOD missing");
            for (var expression : visual.expressions().values()) {
                helper.assertTrue(bones.containsAll(expression), "expression references missing bones");
            }
            var clips = read(asset(visual.animation())).getAsJsonObject("animations");
            for (String clip : visual.animations().values()) helper.assertTrue(clips.has(clip), "missing clip: " + clip);
        } catch (IOException | IllegalArgumentException exception) {
            helper.fail("Cannot resolve Yukikaze visual: " + exception);
            return;
        }
        helper.succeed();
    }

    private static void checkDefinition(GameTestHelper helper, Mob entity) {
        DefinitionBackedShip ship = (DefinitionBackedShip) entity;
        ShipDefinition definition = ship.definitionState().definition();
        helper.assertTrue(definition != null && definition.id().equals(YUKIKAZE), "wrong definition identity");
        helper.assertTrue(definition.visual().equals(YUKIKAZE.location()), "wrong visual ID");
        helper.assertTrue(!definition.construction().enabled() && definition.spawn().enabled() && definition.spawn().weight() == 15,
                "Yukikaze acquisition settings changed");
        helper.assertTrue(ship.getShipId().equals(YUKIKAZE.toString()) && !ship.definitionState().dormant(), "entity not initialized");
        helper.assertTrue(!entity.level().isClientSide(), "test must run on dedicated server");
    }

    private static void fixture(GameTestHelper helper, java.util.function.Consumer<GameTestEntities> test) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                test.accept(entities);
                helper.succeed();
            }
        }, new Vec3(1.5, 2, 1.5));
    }

    private static void place(GameTestHelper helper, Mob entity) {
        entity.setNoAi(true);
        entity.setInvulnerable(true);
        entity.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "entity spawn rejected");
        GameTestEntities.assertRegistered(helper, entity);
    }

    private static Path asset(ResourceLocation resource) {
        return ASSETS.resolve(resource.getNamespace()).resolve(resource.getPath());
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
