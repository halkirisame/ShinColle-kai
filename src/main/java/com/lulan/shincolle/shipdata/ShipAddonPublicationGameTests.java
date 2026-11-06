package com.lulan.shincolle.shipdata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.visualdata.ShipVisualValidator;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAddonPublicationGameTests {
    private static final Path ROOT = Path.of(System.getProperty("shincolle.publicationRoot", ".")).toAbsolutePath().normalize();
    private static final Path EXAMPLE = ROOT.resolve("examples/ship_addon");
    private static final ShipDefinitionId SHIP = ShipDefinitionId.parse("shincolle_example:training_ship");
    private static final Pattern JSON_BLOCK = Pattern.compile("(?m)^```json\\R(.*?)^```", Pattern.DOTALL);

    private ShipAddonPublicationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void readmeGiveCommandsCreateNamedFriendlyEggs(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ShipAddonReader"));
        try {
            for (String document : List.of("README.md", "README.en.md")) {
                String text = Files.readString(EXAMPLE.resolve(document));
                var commands = Pattern.compile("(?m)^/give .+$").matcher(text);
                int count = 0;
                while (commands.find()) {
                    player.getInventory().clearContent();
                    var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
                    int result = helper.getLevel().getServer().getCommands().performPrefixedCommand(source, commands.group());
                    var egg = player.getInventory().getItem(0);
                    helper.assertTrue(result == 1 && egg.is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()) && egg.getCount() == 1,
                            "README command must grant one generic egg: " + document);
                    helper.assertTrue(egg.getTag() != null && SHIP.toString().equals(egg.getTag().getString("ShipId"))
                            && !egg.getTag().getBoolean("Hostile"), "README command must carry the example's friendly identity");
                    count++;
                }
                helper.assertTrue(count == 1, "README must contain its executable give command: " + document);
            }
        } catch (IOException exception) {
            helper.fail("Cannot read example commands: " + exception.getMessage());
            return;
        } finally {
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void examplePackAndGuideDefinitionsPassRuntimeLoader(GameTestHelper helper) {
        ShipDefinitionRegistry isolated = new ShipDefinitionRegistry();
        ShipDefinitionValidator validator = new ShipDefinitionValidator();
        ShipDefinitionLoader loader = new ShipDefinitionLoader(validator, isolated);
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(new PathPackResources("ship_addon", EXAMPLE, false)))) {
            var prepared = loader.prepare(resources, InactiveProfiler.INSTANCE);
            helper.assertTrue(prepared.size() == 1 && prepared.containsKey(SHIP.location()), "Example pack path must derive its ID");
            loader.apply(prepared, resources, InactiveProfiler.INSTANCE);
            ShipDefinition definition = isolated.get(SHIP);
            helper.assertTrue(definition != null, "Runtime loader rejected the published ship example");
            helper.assertTrue(definition.visual().toString().equals(SHIP.toString()), "Example visual ID must match the resource pack");
            helper.assertTrue(definition.construction().enabled() && definition.construction().weight() == 500
                    && definition.construction().mode() == ShipDefinition.ConstructionMode.WEIGHTED
                    && definition.construction().matches(0) && definition.construction().matches(1), "Example must allow both construction tiers");
            helper.assertTrue(!definition.spawn().enabled(), "Example must not enable wild spawning");
            for (String document : List.of("docs/ship_addon_data.md", "docs/ship_addon_data.en.md")) {
                var blocks = JSON_BLOCK.matcher(Files.readString(ROOT.resolve(document)));
                int count = 0;
                int variantFragments = 0;
                while (blocks.find()) {
                    JsonElement json = ShipDefinitionJson.read(blocks.group(1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (json.isJsonObject() && json.getAsJsonObject().size() == 1 && json.getAsJsonObject().has("hostile_variants")) {
                        JsonObject merged = prepared.get(SHIP.location()).getAsJsonObject().deepCopy();
                        merged.add("hostile_variants", json.getAsJsonObject().get("hostile_variants"));
                        ShipDefinition withVariants = validator.validate(SHIP, merged, warning -> helper.fail(document + ": " + warning));
                        helper.assertTrue(withVariants.hostileVariants().get("flagship") != null,
                                "Guide fragment must load using the registered traits: " + document);
                        variantFragments++;
                        continue;
                    }
                    ShipDefinition parsed = validator.validate(SHIP, json,
                            warning -> helper.fail(document + ": " + warning));
                    helper.assertTrue(parsed.equals(definition), "Guide JSON must match the runtime example: " + document);
                    count++;
                }
                helper.assertTrue(count == 1, "Missing guide JSON: " + document);
                helper.assertTrue(variantFragments == 1, "Missing guide variant fragment: " + document);
            }
        } catch (IOException | IllegalArgumentException exception) {
            helper.fail("Cannot load published ship example: " + exception.getMessage());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void exampleVisualPassesValidatorAndReferencesBundledAssets(GameTestHelper helper) {
        try {
            Path file = EXAMPLE.resolve("assets/shincolle_example/shincolle_kai/visuals/training_ship.json");
            List<String> warnings = new ArrayList<>();
            var visual = new ShipVisualValidator().validate(read(file), warnings::add);
            helper.assertTrue(warnings.isEmpty(), "Example visual must not contain unknown keys: " + warnings);
            for (ResourceLocation asset : List.of(visual.model(), visual.texture(), visual.animation())) {
                helper.assertTrue(asset.getNamespace().equals(Reference.MOD_ID), "Example must reference bundled assets");
                helper.assertTrue(Files.isRegularFile(assetPath(asset)), "Missing bundled asset: " + asset);
            }
            JsonObject bundled = read(ROOT.resolve("src/main/resources/assets/shincolle_kai/shincolle_kai/visuals/kashima.json"));
            helper.assertTrue(read(file).equals(bundled), "Example visual must retain Kashima's mappings and presentation");
            Set<String> bones = new HashSet<>();
            var geometry = read(assetPath(visual.model())).getAsJsonArray("minecraft:geometry");
            for (JsonElement model : geometry) {
                for (JsonElement bone : model.getAsJsonObject().getAsJsonArray("bones")) {
                    bones.add(bone.getAsJsonObject().get("name").getAsString());
                }
            }
            helper.assertTrue(bones.containsAll(visual.rig().values()), "Example rig references missing bones");
            helper.assertTrue(bones.containsAll(visual.headFollow().bones()) && bones.containsAll(visual.glowBones())
                    && bones.containsAll(visual.lod().hideBones()), "Example optional features reference missing bones");
            for (var expression : visual.expressions().values()) {
                helper.assertTrue(bones.containsAll(expression), "Example expression references missing bones");
            }
            var clips = read(assetPath(visual.animation())).getAsJsonObject("animations");
            for (String clip : visual.animations().values()) {
                helper.assertTrue(clips.has(clip), "Missing animation clip " + clip);
            }
            for (String document : List.of("docs/ship_addon_data.md", "docs/ship_addon_data.en.md")) {
                String guide = Files.readString(ROOT.resolve(document));
                for (var role : visual.rig().entrySet()) {
                    helper.assertTrue(guide.contains("| `" + role.getKey() + "` | `" + role.getValue() + "` |"),
                            "Guide rig table differs for " + role.getKey() + ": " + document);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            helper.fail("Cannot validate published visual example: " + exception.getMessage());
            return;
        }
        helper.succeed();
    }

    private static Path assetPath(ResourceLocation asset) {
        return ROOT.resolve("src/main/resources/assets").resolve(asset.getNamespace()).resolve(asset.getPath());
    }

    private static JsonObject read(Path file) throws IOException {
        return ShipDefinitionJson.read(Files.readAllBytes(file)).getAsJsonObject();
    }
}
