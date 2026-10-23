package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.shipdata.ShipDefinitionJson;
import com.lulan.shincolle.visualdata.ShipVisualDefinition;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import com.lulan.shincolle.visualdata.ShipVisualPolicy;
import com.lulan.shincolle.visualdata.ShipVisualPresentation;
import com.lulan.shincolle.visualdata.ShipVisualValidator;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.loading.FileLoader;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Independently validated, atomically published client resource generations. */
@OnlyIn(Dist.CLIENT)
public final class ShipVisuals extends SimplePreparableReloadListener<ShipVisuals.Snapshot> {
    public record Loaded(ShipVisualDefinition definition, Model geometry, BakedAnimations animations,
                         Map<String, String> rig, Map<ShipVisualEvent, RawAnimation> clips, Map<String, List<String>> expressions,
                         Set<String> glowBones, Set<String> lodBones) {
        public Loaded {
            rig = Map.copyOf(rig);
            clips = Map.copyOf(clips);
            expressions = Map.copyOf(expressions);
            glowBones = Set.copyOf(glowBones);
            lodBones = Set.copyOf(lodBones);
        }
    }

    public record Snapshot(Map<ResourceLocation, Loaded> visuals, Loaded placeholder) {
        public Snapshot {
            visuals = Map.copyOf(visuals);
        }
    }

    private static final ResourceLocation PLACEHOLDER_ID = new ResourceLocation("shincolle_kai", "placeholder");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Snapshot snapshot;

    private ShipVisuals() { }

    public static final ShipVisuals LISTENER = new ShipVisuals();

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static Loaded resolve(ResourceLocation id) {
        Snapshot current = snapshot;
        if (current == null) throw new IllegalStateException("Ship visuals have not loaded");
        return ShipVisualPolicy.select(current.visuals(), id, current.placeholder(), missing ->
                warnOnce("visual:" + missing, "Missing/rejected ship visual " + missing + "; using placeholder"));
    }

    static boolean warned(String key) {
        return WARNED.contains(key);
    }

    static void warnOnce(String key, String message) {
        if (WARNED.add(key)) LogUtils.getLogger().warn(message);
    }

    @Override
    protected Snapshot prepare(ResourceManager resources, ProfilerFiller profiler) {
        try {
            ShipVisualDefinition placeholder = new ShipVisualDefinition(
                    new ResourceLocation("shincolle_kai", "ship_visual/placeholder.geo.json"),
                    new ResourceLocation("minecraft", "textures/block/magenta_concrete.png"),
                    new ResourceLocation("shincolle_kai", "ship_visual/placeholder.animation.json"),
                    1, 0.35F, Map.of(), Map.of(), Map.of());
            Loaded fallback = load(PLACEHOLDER_ID, placeholder, resources);
            Map<ResourceLocation, Loaded> accepted = new HashMap<>();
            ShipVisualValidator validator = new ShipVisualValidator();
            FileToIdConverter converter = FileToIdConverter.json(ShipVisualValidator.DIRECTORY);
            converter.listMatchingResources(resources).forEach((file, resource) -> {
                ResourceLocation id = converter.fileToId(file);
                try (var input = resource.open()) {
                    ShipVisualDefinition definition = validator.validate(ShipDefinitionJson.read(input.readNBytes(65537)),
                            message -> warnOnce("key:" + id + ":" + message, "Ship visual " + id + ": " + message));
                    accepted.put(id, load(id, definition, resources));
                } catch (Exception exception) {
                    warnOnce("visual:" + id, "Rejected ship visual " + id + "; using placeholder: " + exception);
                }
            });
            return new Snapshot(accepted, fallback);
        } catch (RuntimeException exception) {
            LogUtils.getLogger().error("Failed ship visual reload; keeping previous generation", exception);
            if (snapshot == null) throw exception;
            return null;
        }
    }

    private static Loaded load(ResourceLocation id, ShipVisualDefinition definition, ResourceManager resources) {
        resources.getResource(definition.texture()).orElseThrow(() -> new IllegalArgumentException("Missing texture"));
        Model raw = FileLoader.loadModelFile(definition.model(), resources);
        var baked = BakedModelFactory.getForNamespace(definition.model().getNamespace())
                .constructGeoModel(GeometryTree.fromModel(raw));
        Set<String> bones = new HashSet<>();
        int count = 0;
        for (GeoBone bone : baked.topLevelBones()) count += collectBones(bone, bones);
        ShipVisualPolicy.BoneBudget budget = ShipVisualPolicy.boneBudget(count);
        if (budget == ShipVisualPolicy.BoneBudget.REJECTED) throw new IllegalArgumentException("Bone count " + count + " exceeds 128");
        if (budget == ShipVisualPolicy.BoneBudget.WARNING) {
            warnOnce("bones:" + id, "Ship visual " + id + " has " + count + " bones; recommended maximum is 64");
        }
        Map<String, String> rig = ShipVisualPolicy.resolveRig(definition, bones, role -> {
            if (ShipVisualPolicy.warnsMissingRole(PLACEHOLDER_ID.equals(id), role)) {
                warnOnce("rig:" + id + ":" + role, "Ship visual " + id + " lacks role " + role + "; feature disabled");
            }
        });
        BakedAnimations animations = FileLoader.loadAnimationsFile(definition.animation(), resources);
        Map<ShipVisualEvent, RawAnimation> clips = new HashMap<>();
        for (ShipVisualEvent event : ShipVisualEvent.values()) {
            String clip = definition.clip(event);
            if (animations.getAnimation(clip) == null) {
                warnOnce("clip:" + id + ":" + event.key(), "Ship visual " + id + " lacks " + event.key()
                        + (event.isAttack() ? "; event ignored" : "; using next available posture"));
            } else {
                clips.put(event, event.isAttack() ? RawAnimation.begin().thenPlay(clip) : RawAnimation.begin().thenLoop(clip));
            }
        }
        Map<String, List<String>> expressions = new HashMap<>();
        for (String slot : ShipVisualPresentation.SLOT_SIZES.keySet()) {
            expressions.put(slot, ShipVisualPresentation.expressionBones(definition, slot, bones));
        }
        Set<String> glowRoots = ShipVisualPolicy.boneRoots(definition.glowBones(), bones, missing ->
                warnOnce("glow:" + id, "Ship visual " + id + " lacks Glow bone " + missing + "; Glow disabled"));
        Set<String> lodRoots = ShipVisualPolicy.boneRoots(definition.lod().hideBones(), bones, missing ->
                warnOnce("lod:" + id, "Ship visual " + id + " lacks LOD bone " + missing + "; LOD disabled"));
        Set<String> glow = new HashSet<>();
        Set<String> lod = new HashSet<>();
        for (GeoBone bone : baked.topLevelBones()) {
            collectGroup(bone, glowRoots, false, glow);
            collectGroup(bone, lodRoots, false, lod);
        }
        return new Loaded(definition, raw, animations, rig, clips, expressions, glow, lod);
    }

    private static void collectGroup(GeoBone bone, Set<String> roots, boolean parentIncluded, Set<String> result) {
        boolean included = parentIncluded || roots.contains(bone.getName());
        if (included) result.add(bone.getName());
        for (GeoBone child : bone.getChildBones()) collectGroup(child, roots, included, result);
    }

    private static int collectBones(GeoBone bone, Set<String> names) {
        names.add(bone.getName());
        int count = 1;
        for (GeoBone child : bone.getChildBones()) count += collectBones(child, names);
        return count;
    }

    @Override
    protected void apply(Snapshot candidate, ResourceManager resources, ProfilerFiller profiler) {
        if (candidate != null) {
            snapshot = candidate;
            LogUtils.getLogger().info("Loaded {} ship visuals", candidate.visuals().size());
        }
    }
}
